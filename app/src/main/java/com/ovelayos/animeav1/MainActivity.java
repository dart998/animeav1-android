package com.ovelayos.animeav1;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://animeav1.com/";
    private static final int DARK_FALLBACK = Color.rgb(16, 15, 20);
    private static final int REQ_NOTIFICATIONS = 41;
    private static final long LIBRARY_SYNC_INTERVAL_MS = 60_000L;

    private WebView webView;
    private ProgressBar progressBar;
    private View rootContainer;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private EpisodeStore store;
    private EpisodeRef currentEpisode;
    private boolean pendingDownloadAfterPermission;
    private boolean pendingBatchAfterPermission;
    private String pendingDownloadSource = "";
    private float pullStartY;
    private boolean pullStartedAtTop;
    private final ExecutorService libraryExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean librarySyncRunning;
    private long lastLibrarySyncMs;
    private int batchPendingCount;
    private int batchSuccessCount;
    private int batchErrorCount;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String slug = intent.getStringExtra(DownloadService.EXTRA_SLUG);
            int episode = intent.getIntExtra(DownloadService.EXTRA_EPISODE, 0);
            String status = intent.getStringExtra(DownloadService.EXTRA_STATUS);
            String error = intent.getStringExtra(DownloadService.EXTRA_ERROR);

            EpisodeRef fromUrl = parseEpisode(webView.getUrl());
            if (fromUrl != null) currentEpisode = fromUrl;
            updateSiteDownloadButton();
            if (currentEpisode != null && currentEpisode.slug.equals(slug) && currentEpisode.episode == episode
                    && EpisodeStore.STATUS_COMPLETED.equals(status)) {
                injectOfflinePlayerIfAvailable();
            }

            boolean terminal = EpisodeStore.STATUS_COMPLETED.equals(status) || EpisodeStore.STATUS_ERROR.equals(status);
            if (batchPendingCount > 0 && terminal) {
                if (EpisodeStore.STATUS_COMPLETED.equals(status)) batchSuccessCount++;
                else batchErrorCount++;
                batchPendingCount--;
                if (batchPendingCount == 0) {
                    String summary = batchSuccessCount + " descargados";
                    if (batchErrorCount > 0) summary += " · " + batchErrorCount + " con error";
                    Toast.makeText(MainActivity.this, summary, Toast.LENGTH_LONG).show();
                    batchSuccessCount = 0;
                    batchErrorCount = 0;
                }
            } else if (batchPendingCount == 0) {
                if (EpisodeStore.STATUS_COMPLETED.equals(status)) {
                    Toast.makeText(MainActivity.this, "Episodio disponible offline", Toast.LENGTH_SHORT).show();
                } else if (EpisodeStore.STATUS_ERROR.equals(status) && error != null && !error.isEmpty()) {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootContainer = findViewById(R.id.rootContainer);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);

        store = new EpisodeStore(this);
        store.markInterruptedDownloads();
        registerDownloadReceiver();
        setupPullToRefresh();

        applySystemBarInsets();
        setStatusBarAppearance(DARK_FALLBACK, false);
        enableImmersiveNavigation();
        AdBlocker.initialize(getApplicationContext());

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                webView.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                rootContainer.setPadding(0, 0, 0, 0);
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                hideAllSystemBarsForVideo();
            }

            @Override public void onHideCustomView() { exitVideoFullscreen(); }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) { return false; }
            @Override public boolean onJsAlert(WebView view, String url, String message, JsResult result) { return super.onJsAlert(view, url, message, result); }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                currentEpisode = parseEpisode(url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse local = OfflineVideoServer.open(request, store);
                if (local != null) return local;
                if (AdBlocker.shouldBlock(request.getUrl())) return AdBlocker.emptyResponse();
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                view.evaluateJavascript(AdBlocker.cosmeticCleanupScript(), null);
                currentEpisode = parseEpisode(url);
                if (currentEpisode != null) {
                    currentEpisode.title = friendlyPageTitle(view.getTitle(), currentEpisode.slug);
                    resolveH1Title();
                }
                injectSiteControls();
                updateSiteDownloadButton();
                injectOfflinePlayerIfAvailable();
                syncWatchedAndCleanup(false);
                syncStatusBarWithWebTheme();
                enableImmersiveNavigation();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("animeav1".equalsIgnoreCase(scheme)) {
                    handleAppUri(uri);
                    return true;
                }
                if (AdBlocker.shouldBlock(uri)) return true;
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                return true;
            }
        });

        if (savedInstanceState == null) webView.loadUrl(HOME_URL);
        else webView.restoreState(savedInstanceState);
    }

    private void handleAppUri(Uri uri) {
        String host = uri.getHost() == null ? "" : uri.getHost();
        if ("downloads".equalsIgnoreCase(host)) {
            showOfflineLibrary();
            return;
        }
        if ("library-changed".equalsIgnoreCase(host)) {
            syncWatchedAndCleanup(true);
            return;
        }
        if (!"download".equalsIgnoreCase(host)) return;

        String slug = value(uri.getQueryParameter("slug"));
        int episode = 0;
        try { episode = Integer.parseInt(value(uri.getQueryParameter("episode"))); } catch (Exception ignored) {}
        String page = value(uri.getQueryParameter("page"));
        String title = value(uri.getQueryParameter("title"));
        String source = value(uri.getQueryParameter("source"));
        if (!slug.isEmpty() && episode > 0 && !page.isEmpty()) {
            currentEpisode = new EpisodeRef(slug, episode, page);
            currentEpisode.title = title.isEmpty() ? slug : title;
        } else {
            EpisodeRef fromUrl = parseEpisode(webView.getUrl());
            if (fromUrl != null) currentEpisode = fromUrl;
        }
        requestDownload(source);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPullToRefresh() {
        final float threshold = 110f * getResources().getDisplayMetrics().density;
        webView.setOnTouchListener((v, event) -> {
            if (customView != null) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                pullStartY = event.getY();
                pullStartedAtTop = !webView.canScrollVertically(-1);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dy = event.getY() - pullStartY;
                if (pullStartedAtTop && !webView.canScrollVertically(-1) && dy >= threshold) {
                    Toast.makeText(this, "Actualizando…", Toast.LENGTH_SHORT).show();
                    webView.reload();
                }
                pullStartedAtTop = false;
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                pullStartedAtTop = false;
            }
            return false;
        });
    }

    private EpisodeRef parseEpisode(String rawUrl) {
        if (rawUrl == null) return null;
        try {
            Uri uri = Uri.parse(rawUrl);
            String host = uri.getHost();
            if (host == null || !(host.equals("animeav1.com") || host.endsWith(".animeav1.com"))) return null;
            List<String> p = uri.getPathSegments();
            if (p.size() < 3 || !"media".equals(p.get(0))) return null;
            int episode = Integer.parseInt(p.get(2));
            if (episode <= 0) return null;
            return new EpisodeRef(p.get(1), episode, rawUrl);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void resolveH1Title() {
        EpisodeRef ref = currentEpisode;
        if (ref == null) return;
        webView.evaluateJavascript("(function(){var h=document.querySelector('h1');return h?h.textContent.trim():''})()", value -> {
            String title = decodeJsString(value);
            if (!title.isEmpty() && currentEpisode != null && currentEpisode.same(ref)) currentEpisode.title = title;
        });
    }

    private void injectSiteControls() {
        String url = webView.getUrl();
        if (url == null || !url.contains("animeav1.com")) return;
        webView.evaluateJavascript(SiteIntegration.install(downloadLabelForCurrent()), null);
    }

    private void updateSiteDownloadButton() {
        String url = webView.getUrl();
        if (url == null || !url.contains("animeav1.com")) return;
        webView.evaluateJavascript(SiteIntegration.updateDownloadLabel(downloadLabelForCurrent()), null);
    }

    private String downloadLabelForCurrent() {
        if (currentEpisode == null) return "Descargar";
        EpisodeStore.DownloadRecord r = store.get(currentEpisode.slug, currentEpisode.episode);
        if (r == null) return "Descargar";
        if (EpisodeStore.STATUS_COMPLETED.equals(r.status)) {
            if (!r.path.isEmpty() && new File(r.path).isFile()) return "✓ Offline";
            store.delete(r.slug, r.episode);
            return "Descargar";
        }
        if (EpisodeStore.STATUS_PENDING.equals(r.status)) return "En cola…";
        if (EpisodeStore.STATUS_DOWNLOADING.equals(r.status) || EpisodeStore.STATUS_RESOLVING.equals(r.status)) {
            if (r.totalBytes > 0) {
                int pct = (int) Math.min(100, r.bytes * 100L / r.totalBytes);
                return "↓ " + pct + "%";
            }
            return "Descargando…";
        }
        if (EpisodeStore.STATUS_ERROR.equals(r.status)) return "Reintentar";
        return "Descargar";
    }

    private void requestDownload(String sourceUrl) {
        if (currentEpisode == null) {
            Toast.makeText(this, "No se ha podido identificar el episodio", Toast.LENGTH_LONG).show();
            return;
        }
        EpisodeStore.DownloadRecord existing = store.get(currentEpisode.slug, currentEpisode.episode);
        if (existing != null) {
            if (EpisodeStore.STATUS_COMPLETED.equals(existing.status)
                    && !existing.path.isEmpty() && new File(existing.path).isFile()) {
                Toast.makeText(this, "Este episodio ya está disponible offline", Toast.LENGTH_SHORT).show();
                return;
            }
            if (EpisodeStore.STATUS_PENDING.equals(existing.status)
                    || EpisodeStore.STATUS_RESOLVING.equals(existing.status)
                    || EpisodeStore.STATUS_DOWNLOADING.equals(existing.status)) {
                Toast.makeText(this, "Este episodio ya está en la cola de descarga", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        pendingDownloadSource = value(sourceUrl);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingDownloadAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        startEpisodeDownload(pendingDownloadSource);
        pendingDownloadSource = "";
    }

    private void startEpisodeDownload(String sourceUrl) {
        if (currentEpisode == null) return;
        enqueueEpisode(currentEpisode.slug, currentEpisode.episode, currentEpisode.pageUrl,
                currentEpisode.title, value(sourceUrl), cookieForAnimeAv1());
        webView.evaluateJavascript(SiteIntegration.updateDownloadLabel("Preparando…"), null);
    }

    private void enqueueEpisode(String slug, int episode, String pageUrl, String title, String sourceUrl, String cookie) {
        Intent i = new Intent(this, DownloadService.class);
        i.putExtra(DownloadService.EXTRA_SLUG, slug);
        i.putExtra(DownloadService.EXTRA_EPISODE, episode);
        i.putExtra(DownloadService.EXTRA_PAGE_URL, pageUrl);
        i.putExtra(DownloadService.EXTRA_TITLE, title);
        i.putExtra(DownloadService.EXTRA_SOURCE_URL, sourceUrl);
        i.putExtra(DownloadService.EXTRA_COOKIE, cookie);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NOTIFICATIONS) return;
        if (pendingDownloadAfterPermission) {
            pendingDownloadAfterPermission = false;
            startEpisodeDownload(pendingDownloadSource);
            pendingDownloadSource = "";
        } else if (pendingBatchAfterPermission) {
            pendingBatchAfterPermission = false;
            downloadUnwatchedEpisodes();
        }
    }

    private void downloadUnwatchedEpisodes() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingBatchAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        String cookie = cookieForAnimeAv1();
        if (cookie.isEmpty()) {
            Toast.makeText(this, "Inicia sesión en AnimeAV1 para consultar los capítulos no vistos", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Buscando capítulos no vistos…", Toast.LENGTH_SHORT).show();
        libraryExecutor.execute(() -> {
            try {
                List<AnimeAv1LibraryClient.Item> items = AnimeAv1LibraryClient.fetch(cookie);
                cleanupWatchedFiles(items, false);
                ArrayList<BatchEpisode> pending = new ArrayList<>();
                HashSet<String> series = new HashSet<>();
                for (AnimeAv1LibraryClient.Item item : items) {
                    if (item.status != 0 || item.slug.isEmpty() || item.total <= item.seen) continue;
                    for (int ep = Math.max(1, item.seen + 1); ep <= item.total; ep++) {
                        EpisodeStore.DownloadRecord r = store.get(item.slug, ep);
                        if (r != null) {
                            if (EpisodeStore.STATUS_COMPLETED.equals(r.status) && !r.path.isEmpty() && new File(r.path).isFile()) continue;
                            if (EpisodeStore.STATUS_PENDING.equals(r.status)
                                    || EpisodeStore.STATUS_RESOLVING.equals(r.status)
                                    || EpisodeStore.STATUS_DOWNLOADING.equals(r.status)) continue;
                        }
                        pending.add(new BatchEpisode(item.slug, item.title, ep));
                        series.add(item.slug);
                    }
                }
                runOnUiThread(() -> confirmBatchDownload(pending, series.size(), cookie));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, messageOf(e), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmBatchDownload(List<BatchEpisode> episodes, int seriesCount, String cookie) {
        if (episodes.isEmpty()) {
            Toast.makeText(this, "No hay capítulos no vistos pendientes de descargar", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Descargar no vistos")
                .setMessage("Se añadirán " + episodes.size() + " episodios de " + seriesCount + " series a la cola. Las descargas se harán una a una.")
                .setPositiveButton("Descargar", (dialog, which) -> {
                    batchPendingCount = episodes.size();
                    batchSuccessCount = 0;
                    batchErrorCount = 0;
                    for (BatchEpisode ep : episodes) {
                        String page = "https://animeav1.com/media/" + ep.slug + "/" + ep.episode;
                        enqueueEpisode(ep.slug, ep.episode, page, ep.title, "", cookie);
                    }
                    Toast.makeText(this, episodes.size() + " episodios añadidos a la cola", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void syncWatchedAndCleanup(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastLibrarySyncMs < LIBRARY_SYNC_INTERVAL_MS) return;
        if (librarySyncRunning) return;
        String cookie = cookieForAnimeAv1();
        if (cookie.isEmpty()) return;
        librarySyncRunning = true;
        libraryExecutor.execute(() -> {
            try {
                List<AnimeAv1LibraryClient.Item> items = AnimeAv1LibraryClient.fetch(cookie);
                int deleted = cleanupWatchedFiles(items, true);
                lastLibrarySyncMs = System.currentTimeMillis();
                if (deleted > 0) {
                    runOnUiThread(() -> {
                        updateSiteDownloadButton();
                        Toast.makeText(this, deleted + (deleted == 1 ? " episodio visto eliminado" : " episodios vistos eliminados"), Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception ignored) {
            } finally {
                librarySyncRunning = false;
            }
        });
    }

    private int cleanupWatchedFiles(List<AnimeAv1LibraryClient.Item> items, boolean deleteFiles) {
        Map<String, Integer> seenBySlug = new HashMap<>();
        for (AnimeAv1LibraryClient.Item item : items) {
            if (!item.slug.isEmpty() && item.seen > 0) seenBySlug.put(item.slug, item.seen);
        }
        int deleted = 0;
        if (!deleteFiles) return 0;
        for (EpisodeStore.DownloadRecord r : store.listCompleted()) {
            Integer seen = seenBySlug.get(r.slug);
            if (seen == null || r.episode > seen) continue;
            if (!r.path.isEmpty()) {
                File f = new File(r.path);
                if (f.exists() && !f.delete()) continue;
            }
            store.delete(r.slug, r.episode);
            deleted++;
        }
        return deleted;
    }

    private String cookieForAnimeAv1() {
        String cookie = CookieManager.getInstance().getCookie("https://animeav1.com/");
        return cookie == null ? "" : cookie;
    }

    private void injectOfflinePlayerIfAvailable() {
        if (currentEpisode == null || customView != null) return;
        EpisodeStore.DownloadRecord r = store.get(currentEpisode.slug, currentEpisode.episode);
        if (r == null || !EpisodeStore.STATUS_COMPLETED.equals(r.status) || r.path.isEmpty() || !new File(r.path).isFile()) return;

        String localUrl = OfflineVideoServer.urlFor(r.slug, r.episode);
        String js = "(function(){" +
                "if(document.querySelector('[data-animeav1-offline-player]'))return;" +
                "var c=[].slice.call(document.querySelectorAll('iframe,video'));" +
                "if(!c.length)return;" +
                "var t=c[0],a=-1;c.forEach(function(e){var r=e.getBoundingClientRect(),x=r.width*r.height;if(x>a){a=x;t=e;}});" +
                "var box=document.createElement('div');box.setAttribute('data-animeav1-offline-player','1');box.style.cssText='position:relative;width:100%;background:#000;';" +
                "var v=document.createElement('video');v.controls=true;v.playsInline=true;v.preload='metadata';v.src=" + JSONObject.quote(localUrl) + ";v.style.cssText='display:block;width:100%;aspect-ratio:16/9;background:#000;object-fit:contain;';" +
                "var b=document.createElement('div');b.textContent='OFFLINE';b.style.cssText='position:absolute;top:8px;left:8px;z-index:5;background:rgba(0,0,0,.65);color:white;padding:4px 7px;border-radius:4px;font:12px sans-serif;pointer-events:none;';" +
                "box.appendChild(v);box.appendChild(b);if(t.parentNode)t.parentNode.replaceChild(box,t);" +
                "})()";
        webView.evaluateJavascript(js, null);
    }

    private void showOfflineLibrary() {
        syncWatchedAndCleanup(false);
        List<EpisodeStore.DownloadRecord> records = store.listCompleted();
        ArrayList<EpisodeStore.DownloadRecord> existing = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (EpisodeStore.DownloadRecord r : records) {
            File f = new File(r.path);
            if (!f.isFile()) continue;
            existing.add(r);
            String title = r.title.isEmpty() ? r.slug : r.title;
            labels.add(title + " · Ep. " + r.episode + " · " + humanBytes(f.length()));
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Descargas");
        if (existing.isEmpty()) b.setMessage("Todavía no hay episodios descargados.");
        else b.setItems(labels.toArray(new String[0]), (dialog, which) -> showOfflineActions(existing.get(which)));
        b.setPositiveButton("Descargar no vistos", (dialog, which) -> downloadUnwatchedEpisodes());
        b.setNegativeButton("Cerrar", null);
        b.show();
    }

    private void showOfflineActions(EpisodeStore.DownloadRecord r) {
        new AlertDialog.Builder(this)
                .setTitle((r.title.isEmpty() ? r.slug : r.title) + " · Ep. " + r.episode)
                .setItems(new String[]{"Ver offline", "Eliminar descarga"}, (dialog, which) -> {
                    if (which == 0) loadOfflinePage(r);
                    else confirmDelete(r);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmDelete(EpisodeStore.DownloadRecord r) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar episodio")
                .setMessage("Se borrará la copia local del episodio " + r.episode + ".")
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    if (!r.path.isEmpty()) new File(r.path).delete();
                    store.delete(r.slug, r.episode);
                    if (currentEpisode != null && currentEpisode.slug.equals(r.slug) && currentEpisode.episode == r.episode) webView.reload();
                    updateSiteDownloadButton();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void loadOfflinePage(EpisodeStore.DownloadRecord r) {
        String title = TextUtils.htmlEncode(r.title.isEmpty() ? r.slug : r.title);
        String src = TextUtils.htmlEncode(OfflineVideoServer.urlFor(r.slug, r.episode));
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>html,body{margin:0;background:#100f14;color:#eee;font-family:sans-serif}main{padding:16px}video{width:100%;background:#000;aspect-ratio:16/9;object-fit:contain}h2{font-size:18px;margin:12px 0 4px}p{opacity:.7;margin-top:4px}</style></head>" +
                "<body><main><video controls autoplay playsinline src='" + src + "'></video><h2>" + title + "</h2><p>Episodio " + r.episode + " · reproducción offline</p></main></body></html>";
        webView.loadDataWithBaseURL("https://offline.animeav1.local/library/", html, "text/html", "UTF-8", null);
    }

    private void registerDownloadReceiver() {
        IntentFilter filter = new IntentFilter(DownloadService.ACTION_DOWNLOAD_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(downloadReceiver, filter);
    }

    private void exitVideoFullscreen() {
        if (customView == null) return;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        customView = null;
        webView.setVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        applySystemBarInsets();
        injectSiteControls();
        syncStatusBarWithWebTheme();
        enableImmersiveNavigation();
        if (customViewCallback != null) { customViewCallback.onCustomViewHidden(); customViewCallback = null; }
    }

    private void hideAllSystemBarsForVideo() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void syncStatusBarWithWebTheme() {
        if (customView != null) return;
        String js = "(function(){var e=document.elementFromPoint(2,2)||document.body||document.documentElement;var c='';while(e){c=getComputedStyle(e).backgroundColor;if(c&&c!=='rgba(0, 0, 0, 0)'&&c!=='transparent')break;e=e.parentElement;}if(!c)c=getComputedStyle(document.body).backgroundColor;return c;})()";
        webView.evaluateJavascript(js, value -> {
            if (value == null) return;
            int color = parseCssColor(decodeJsString(value));
            setStatusBarAppearance(color, luminance(color) > 0.55);
        });
    }

    private int parseCssColor(String css) {
        try {
            if (css.startsWith("rgb")) {
                int a = css.indexOf('('), b = css.indexOf(')');
                String[] p = css.substring(a + 1, b).split(",");
                return Color.rgb(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
            }
            return Color.parseColor(css);
        } catch (Exception e) { return DARK_FALLBACK; }
    }

    private double luminance(int color) {
        return (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0;
    }

    private void setStatusBarAppearance(int color, boolean lightBackground) {
        Window window = getWindow();
        window.setStatusBarColor(color);
        rootContainer.setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(lightBackground ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility();
            if (lightBackground) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void applySystemBarInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rootContainer.setOnApplyWindowInsetsListener((v, insets) -> {
                if (customView == null) {
                    android.graphics.Insets statusInsets = insets.getInsets(WindowInsets.Type.statusBars());
                    v.setPadding(0, statusInsets.top, 0, 0);
                } else v.setPadding(0, 0, 0, 0);
                return insets;
            });
            rootContainer.requestApplyInsets();
        }
    }

    private void enableImmersiveNavigation() {
        if (customView != null) { hideAllSystemBarsForVideo(); return; }
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars());
                controller.hide(WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private String decodeJsString(String value) {
        try {
            Object decoded = new JSONTokener(value).nextValue();
            return decoded instanceof String ? (String) decoded : value.replace("\"", "");
        } catch (Exception e) {
            return value == null ? "" : value.replace("\"", "");
        }
    }

    private String friendlyPageTitle(String title, String fallback) {
        if (title == null || title.trim().isEmpty()) return fallback;
        String clean = title.replace("AnimeAV1", "").replace("|", "").trim();
        return clean.isEmpty() ? fallback : clean;
    }

    private String humanBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.US, "%.2f GB", bytes / (1024d * 1024d * 1024d));
        if (bytes >= 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024d * 1024d));
        if (bytes >= 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024d);
        return bytes + " B";
    }

    private String messageOf(Exception e) {
        return e.getMessage() == null || e.getMessage().trim().isEmpty() ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static String value(String s) { return s == null ? "" : s; }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enableImmersiveNavigation(); }
    @Override protected void onSaveInstanceState(Bundle outState) { webView.saveState(outState); super.onSaveInstanceState(outState); }
    @Override protected void onPause() { CookieManager.getInstance().flush(); webView.onPause(); super.onPause(); }
    @Override protected void onResume() {
        super.onResume();
        webView.onResume();
        enableImmersiveNavigation();
        injectSiteControls();
        updateSiteDownloadButton();
        syncWatchedAndCleanup(false);
        if (customView == null) webView.postDelayed(this::syncStatusBarWithWebTheme, 250);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
        libraryExecutor.shutdownNow();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        CookieManager.getInstance().flush();
        webView.destroy();
        store.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) exitVideoFullscreen();
        else if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private static final class EpisodeRef {
        final String slug;
        final int episode;
        final String pageUrl;
        String title;

        EpisodeRef(String slug, int episode, String pageUrl) {
            this.slug = slug;
            this.episode = episode;
            this.pageUrl = pageUrl;
            this.title = slug;
        }

        boolean same(EpisodeRef other) {
            return other != null && episode == other.episode && slug.equals(other.slug);
        }
    }

    private static final class BatchEpisode {
        final String slug;
        final String title;
        final int episode;
        BatchEpisode(String slug, String title, int episode) {
            this.slug = slug;
            this.title = title;
            this.episode = episode;
        }
    }
}
