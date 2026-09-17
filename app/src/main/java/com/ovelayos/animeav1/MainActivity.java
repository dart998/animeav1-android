package com.ovelayos.animeav1;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
    public static final String EXTRA_OPEN_DOWNLOADS = "open_downloads";
    public static final String EXTRA_OPEN_URL = "open_url";

    private static final String HOME_URL = "https://animeav1.com/";
    private static final String SCHEDULE_URL = "https://animeav1.com/horario";
    private static final String LISTS_URL = "https://animeav1.com/cuenta/listas";
    private static final String ACCOUNT_URL = "https://animeav1.com/cuenta";
    private static final int REQ_NOTIFICATIONS = 41;
    private static final long LIBRARY_SYNC_INTERVAL_MS = 60_000L;
    private static final int ACTIVE_COLOR = Color.rgb(33, 234, 216);
    private static final int INACTIVE_COLOR = Color.rgb(154, 156, 171);

    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout fullscreenContainer;
    private View appContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private TextView[] navViews;
    private EpisodeStore store;
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final Set<String> batchKeys = new HashSet<>();
    private int batchSuccess;
    private int batchFailure;
    private boolean librarySyncRunning;
    private long lastLibrarySyncMs;
    private String lastOnlineUrl = HOME_URL;
    private Runnable pendingNotificationAction;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver legacyNetworkReceiver;

    private enum Section {
        HOME(HOME_URL), DOWNLOADS(LocalContentServer.DOWNLOADS_URL), SCHEDULE(SCHEDULE_URL), LISTS(LISTS_URL), ACCOUNT(ACCOUNT_URL);
        final String url;
        Section(String url) { this.url = url; }
    }

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String slug = value(intent.getStringExtra(DownloadService.EXTRA_SLUG));
            int episode = intent.getIntExtra(DownloadService.EXTRA_EPISODE, 0);
            String status = value(intent.getStringExtra(DownloadService.EXTRA_STATUS));
            String error = value(intent.getStringExtra(DownloadService.EXTRA_ERROR));
            boolean terminal = EpisodeStore.STATUS_COMPLETED.equals(status)
                    || EpisodeStore.STATUS_ERROR.equals(status)
                    || EpisodeStore.STATUS_CANCELLED.equals(status);

            String key = slug + "#" + episode;
            if (terminal && batchKeys.remove(key)) {
                if (EpisodeStore.STATUS_COMPLETED.equals(status)) batchSuccess++;
                else batchFailure++;
                if (batchKeys.isEmpty()) {
                    String msg = batchSuccess + " descargados" + (batchFailure > 0 ? " · " + batchFailure + " con error/cancelados" : "");
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    batchSuccess = 0;
                    batchFailure = 0;
                }
            } else if (terminal && batchKeys.isEmpty()) {
                if (EpisodeStore.STATUS_COMPLETED.equals(status)) Toast.makeText(MainActivity.this, "Episodio disponible offline", Toast.LENGTH_SHORT).show();
                else if (EpisodeStore.STATUS_ERROR.equals(status) && !error.isEmpty()) Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }

            String current = webView == null ? "" : value(webView.getUrl());
            if (LocalContentServer.isDownloads(current)) webView.reload();
            if (isAnimeAv1(current)) {
                webView.evaluateJavascript(SiteIntegration.updateDownloadLabel(downloadLabelFor(current)), null);
                EpisodeRef ref = parseEpisode(current);
                if (ref != null && ref.slug.equals(slug) && ref.episode == episode && EpisodeStore.STATUS_COMPLETED.equals(status)) replaceOnlinePlayerIfDownloaded(ref);
            }
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appContainer = findViewById(R.id.appContainer);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);
        navViews = new TextView[]{findViewById(R.id.navHome), findViewById(R.id.navDownloads), findViewById(R.id.navSchedule), findViewById(R.id.navLists), findViewById(R.id.navAccount)};

        store = new EpisodeStore(this);
        store.markInterruptedDownloads();
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        setupNavigation();
        setupWebView();
        registerDownloadReceiver();
        registerConnectivityMonitor();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) {
            openDownloads();
        } else {
            openIntentTarget(getIntent());
        }
    }

    private void setupNavigation() {
        for (int i = 0; i < navViews.length; i++) {
            final int index = i;
            navViews[i].setOnClickListener(v -> navigateTo(Section.values()[index]));
        }
        updateNavigation(null);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new AppBridge(), "AnimeAV1App");
        AdBlocker.initialize(getApplicationContext());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
            }

            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customViewCallback = callback;
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                appContainer.setVisibility(View.GONE);
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                hideSystemBars();
            }

            @Override public void onHideCustomView() { exitFullscreenVideo(); }

            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView popup = new WebView(MainActivity.this);
                popup.getSettings().setJavaScriptEnabled(true);
                popup.setWebViewClient(new WebViewClient() {
                    private boolean handled;
                    private void capture(String url) {
                        if (handled || url == null || url.isEmpty() || "about:blank".equals(url)) return;
                        handled = true;
                        webView.post(() -> openInSameView(url));
                        popup.destroy();
                    }
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) { capture(request.getUrl().toString()); return true; }
                    @Override public void onPageStarted(WebView v, String url, Bitmap favicon) { capture(url); }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                updateNavigation(url);
                if (isAnimeAv1(url) && !isOnline()) {
                    view.stopLoading();
                    view.post(MainActivity.this::showOfflineLanding);
                }
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse localVideo = OfflineVideoServer.open(request, store);
                if (localVideo != null) return localVideo;
                WebResourceResponse localPage = LocalContentServer.open(request, store, isOnline());
                if (localPage != null) return localPage;
                if (AdBlocker.shouldBlock(request.getUrl())) return AdBlocker.emptyResponse();
                return super.shouldInterceptRequest(view, request);
            }

            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                updateNavigation(url);
                if (isAnimeAv1(url)) {
                    lastOnlineUrl = url;
                    view.evaluateJavascript(AdBlocker.cosmeticCleanupScript(), null);
                    view.evaluateJavascript(SiteIntegration.install(downloadLabelFor(url)), null);
                    EpisodeRef ref = parseEpisode(url);
                    if (ref != null) replaceOnlinePlayerIfDownloaded(ref);
                    syncWatchedAndCleanup(false);
                }
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && isAnimeAv1(request.getUrl().toString()) && !isOnline()) view.post(MainActivity.this::showOfflineLanding);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigationRequest(request.getUrl());
            }
        });
    }

    private boolean handleNavigationRequest(Uri uri) {
        String scheme = value(uri.getScheme()).toLowerCase(Locale.US);
        if ("animeav1".equals(scheme)) {
            if ("downloads".equalsIgnoreCase(uri.getHost())) openDownloads();
            return true;
        }
        if ("http".equals(scheme) || "https".equals(scheme)) {
            if (isAnimeAv1(uri.toString()) && !isOnline()) {
                showOfflineLanding();
                return true;
            }
            return false;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
        return true;
    }

    private void navigateTo(Section section) {
        if (section == Section.DOWNLOADS) { openDownloads(); return; }
        if (!isOnline()) { showOfflineLanding(); return; }
        webView.loadUrl(section.url);
    }

    private void openDownloads() {
        webView.loadUrl(LocalContentServer.DOWNLOADS_URL);
    }

    private void showOfflineLanding() {
        if (!LocalContentServer.isOffline(value(webView.getUrl()))) webView.loadUrl(LocalContentServer.OFFLINE_URL);
    }

    private void openInSameView(String raw) {
        try {
            Uri uri = Uri.parse(raw);
            String scheme = value(uri.getScheme()).toLowerCase(Locale.US);
            if ("http".equals(scheme) || "https".equals(scheme)) {
                if (isAnimeAv1(raw) && !isOnline()) showOfflineLanding();
                else webView.loadUrl(raw);
                return;
            }
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {}
    }

    private void openIntentTarget(Intent intent) {
        String raw = intent == null ? "" : value(intent.getStringExtra(EXTRA_OPEN_URL));
        if (raw.isEmpty() && intent != null && intent.getData() != null) raw = intent.getData().toString();
        if (!raw.isEmpty() && isAnimeAv1(raw)) {
            EpisodeRef ref = parseEpisode(raw);
            if (!isOnline() && ref != null) {
                EpisodeStore.DownloadRecord record = store.get(ref.slug, ref.episode);
                if (isPlayable(record)) { webView.loadUrl(LocalContentServer.playerUrl(ref.slug, ref.episode)); return; }
            }
            if (isOnline()) { webView.loadUrl(raw); return; }
        }
        if (isOnline()) webView.loadUrl(HOME_URL); else showOfflineLanding();
    }

    private void updateNavigation(String url) {
        Section active = sectionFor(url == null ? value(webView.getUrl()) : url);
        for (int i = 0; i < navViews.length; i++) {
            boolean selected = active != null && active.ordinal() == i;
            int color = selected ? ACTIVE_COLOR : INACTIVE_COLOR;
            navViews[i].setTextColor(color);
            navViews[i].setCompoundDrawableTintList(ColorStateList.valueOf(color));
            navViews[i].setSelected(selected);
        }
    }

    private Section sectionFor(String raw) {
        if (LocalContentServer.isDownloads(raw)) return Section.DOWNLOADS;
        if (!isAnimeAv1(raw)) return null;
        try {
            Uri u = Uri.parse(raw);
            String p = value(u.getPath()).replaceAll("/+$", "");
            if (p.isEmpty()) p = "/";
            if ("/".equals(p)) return Section.HOME;
            if ("/horario".equals(p)) return Section.SCHEDULE;
            if ("/cuenta/listas".equals(p)) return Section.LISTS;
            if ("/cuenta".equals(p)) return Section.ACCOUNT;
        } catch (Exception ignored) {}
        return null;
    }

    private void swipe(int direction) {
        Section current = sectionFor(value(webView.getUrl()));
        if (current == null) return;
        int next = current.ordinal() + (direction > 0 ? 1 : -1);
        if (next < 0 || next >= Section.values().length) return;
        navigateTo(Section.values()[next]);
    }

    private final class AppBridge {
        @JavascriptInterface public void openDownloads() { runOnUiThread(MainActivity.this::openDownloads); }
        @JavascriptInterface public void retryConnection() { runOnUiThread(() -> { if (isOnline()) webView.loadUrl(lastOnlineUrl); else Toast.makeText(MainActivity.this, "Sigue sin haber conexión", Toast.LENGTH_SHORT).show(); }); }
        @JavascriptInterface public void swipe(int direction) { runOnUiThread(() -> MainActivity.this.swipe(direction)); }
        @JavascriptInterface public void openUrl(String url) { runOnUiThread(() -> openInSameView(url)); }
        @JavascriptInterface public void libraryChanged() { runOnUiThread(() -> syncWatchedAndCleanup(true)); }
        @JavascriptInterface public void downloadAllUnwatched() { runOnUiThread(MainActivity.this::downloadAllUnwatched); }

        @JavascriptInterface public void downloadEpisode(String slug, int episode, String pageUrl, String title, String sourceUrl) {
            runOnUiThread(() -> requestEpisodeDownload(slug, episode, pageUrl, title, sourceUrl));
        }

        @JavascriptInterface public void downloadSeries(String slug, int published) {
            runOnUiThread(() -> prepareSeriesDownload(slug, published));
        }

        @JavascriptInterface public void downloadAction(String action, String slug, int episode) {
            runOnUiThread(() -> handleDownloadAction(action, slug, episode));
        }
    }

    private void requestEpisodeDownload(String slug, int episode, String pageUrl, String title, String sourceUrl) {
        if (!validSlug(slug) || episode <= 0 || !isAnimeAv1(pageUrl)) {
            Toast.makeText(this, "No se ha podido identificar el episodio", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isOnline()) { Toast.makeText(this, "Necesitas conexión para descargar nuevos episodios", Toast.LENGTH_LONG).show(); return; }
        EpisodeStore.DownloadRecord r = store.get(slug, episode);
        if (isPlayable(r)) { Toast.makeText(this, "Este episodio ya está disponible offline", Toast.LENGTH_SHORT).show(); return; }
        if (isActive(r)) { Toast.makeText(this, "Este episodio ya está en la cola", Toast.LENGTH_SHORT).show(); return; }
        withNotificationPermission(() -> enqueueEpisode(slug, episode, pageUrl, cleanTitle(title, slug), value(sourceUrl), false));
    }

    private void prepareSeriesDownload(String slug, int published) {
        if (!validSlug(slug) || published <= 0) { Toast.makeText(this, "No se pudieron determinar los episodios publicados", Toast.LENGTH_LONG).show(); return; }
        if (!isOnline()) { Toast.makeText(this, "Necesitas conexión para consultar tu progreso", Toast.LENGTH_LONG).show(); return; }
        String cookie = cookieForAnimeAv1();
        if (cookie.isEmpty()) { Toast.makeText(this, "Inicia sesión en AnimeAV1 para consultar tu progreso", Toast.LENGTH_LONG).show(); return; }
        background.execute(() -> {
            try {
                AnimeAv1LibraryClient.Item found = null;
                for (AnimeAv1LibraryClient.Item item : AnimeAv1LibraryClient.fetch(cookie)) if (slug.equals(item.slug)) { found = item; break; }
                if (found == null) throw new IllegalStateException("La serie no está en tu biblioteca de AnimeAV1");
                ArrayList<BatchEpisode> episodes = pendingEpisodes(found.slug, found.title, found.seen, published);
                AnimeAv1LibraryClient.Item finalFound = found;
                runOnUiThread(() -> confirmBatch("Descargar no vistos", "Vistos: " + finalFound.seen + " · Publicados: " + published, episodes));
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, messageOf(e), Toast.LENGTH_LONG).show()); }
        });
    }

    private void downloadAllUnwatched() {
        if (!isOnline()) { Toast.makeText(this, "Necesitas conexión para buscar episodios no vistos", Toast.LENGTH_LONG).show(); return; }
        String cookie = cookieForAnimeAv1();
        if (cookie.isEmpty()) { Toast.makeText(this, "Inicia sesión en AnimeAV1 para consultar tu biblioteca", Toast.LENGTH_LONG).show(); return; }
        Toast.makeText(this, "Buscando episodios no vistos publicados…", Toast.LENGTH_SHORT).show();
        background.execute(() -> {
            try {
                List<AnimeAv1LibraryClient.Item> items = AnimeAv1LibraryClient.fetch(cookie);
                cleanupWatchedFiles(items);
                ArrayList<BatchEpisode> episodes = new ArrayList<>();
                int series = 0;
                for (AnimeAv1LibraryClient.Item item : items) {
                    if (item.status != 0 || !validSlug(item.slug)) continue;
                    int published;
                    try { published = AnimeAv1SeriesClient.fetchPublished(item.slug, cookie); }
                    catch (Exception ignored) { continue; }
                    ArrayList<BatchEpisode> one = pendingEpisodes(item.slug, item.title, item.seen, published);
                    if (!one.isEmpty()) { series++; episodes.addAll(one); }
                }
                int finalSeries = series;
                runOnUiThread(() -> confirmBatch("Descargar no vistos", episodes.size() + " episodios de " + finalSeries + " series", episodes));
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, messageOf(e), Toast.LENGTH_LONG).show()); }
        });
    }

    private ArrayList<BatchEpisode> pendingEpisodes(String slug, String title, int seen, int published) {
        ArrayList<BatchEpisode> out = new ArrayList<>();
        for (int ep = Math.max(1, seen + 1); ep <= published; ep++) {
            EpisodeStore.DownloadRecord r = store.get(slug, ep);
            if (isPlayable(r) || isActive(r)) continue;
            out.add(new BatchEpisode(slug, title, ep));
        }
        return out;
    }

    private void confirmBatch(String title, String detail, List<BatchEpisode> episodes) {
        if (episodes.isEmpty()) { Toast.makeText(this, "No hay episodios no vistos publicados pendientes", Toast.LENGTH_LONG).show(); return; }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(detail + "\nSe añadirán " + episodes.size() + " episodio" + (episodes.size() == 1 ? "" : "s") + " a una cola secuencial.")
                .setPositiveButton("Descargar", (d, w) -> withNotificationPermission(() -> {
                    batchKeys.clear(); batchSuccess = 0; batchFailure = 0;
                    for (BatchEpisode ep : episodes) {
                        batchKeys.add(ep.slug + "#" + ep.episode);
                        enqueueEpisode(ep.slug, ep.episode, "https://animeav1.com/media/" + ep.slug + "/" + ep.episode, ep.title, "", true);
                    }
                    Toast.makeText(this, episodes.size() + " episodios añadidos a la cola", Toast.LENGTH_LONG).show();
                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void enqueueEpisode(String slug, int episode, String page, String title, String source, boolean bulk) {
        Intent i = new Intent(this, DownloadService.class);
        i.putExtra(DownloadService.EXTRA_SLUG, slug);
        i.putExtra(DownloadService.EXTRA_EPISODE, episode);
        i.putExtra(DownloadService.EXTRA_PAGE_URL, page);
        i.putExtra(DownloadService.EXTRA_TITLE, cleanTitle(title, slug));
        i.putExtra(DownloadService.EXTRA_SOURCE_URL, value(source));
        i.putExtra(DownloadService.EXTRA_COOKIE, cookieForAnimeAv1());
        i.putExtra(DownloadService.EXTRA_BULK, bulk);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
        String current = value(webView.getUrl());
        if (isAnimeAv1(current)) webView.evaluateJavascript(SiteIntegration.updateDownloadLabel("Preparando…"), null);
    }

    private void handleDownloadAction(String action, String slug, int episode) {
        if (!validSlug(slug) || episode <= 0) return;
        EpisodeStore.DownloadRecord r = store.get(slug, episode);
        if (r == null) return;
        switch (value(action)) {
            case "cancel": {
                Intent i = new Intent(this, DownloadService.class).setAction(DownloadService.ACTION_CANCEL)
                        .putExtra(DownloadService.EXTRA_SLUG, slug).putExtra(DownloadService.EXTRA_EPISODE, episode);
                startService(i);
                break;
            }
            case "delete":
                if (!value(r.path).isEmpty()) new File(r.path).delete();
                store.delete(slug, episode);
                refreshLocalDownloads();
                break;
            case "delete-record":
                store.delete(slug, episode);
                refreshLocalDownloads();
                break;
            case "retry":
                if (!isOnline()) { Toast.makeText(this, "Necesitas conexión para reintentar", Toast.LENGTH_LONG).show(); break; }
                withNotificationPermission(() -> enqueueEpisode(slug, episode, value(r.pageUrl).isEmpty() ? "https://animeav1.com/media/" + slug + "/" + episode : r.pageUrl, r.title, r.sourceUrl, false));
                break;
            case "play":
                if (isPlayable(r)) webView.loadUrl(LocalContentServer.playerUrl(slug, episode));
                else Toast.makeText(this, "El fichero local ya no está disponible", Toast.LENGTH_LONG).show();
                break;
        }
    }

    private void refreshLocalDownloads() {
        if (LocalContentServer.isDownloads(value(webView.getUrl()))) webView.reload();
    }

    private void replaceOnlinePlayerIfDownloaded(EpisodeRef ref) {
        EpisodeStore.DownloadRecord r = store.get(ref.slug, ref.episode);
        if (!isPlayable(r)) return;
        webView.evaluateJavascript(SiteIntegration.useLocalPlayer(OfflineVideoServer.urlFor(ref.slug, ref.episode)), null);
    }

    private String downloadLabelFor(String url) {
        EpisodeRef ref = parseEpisode(url);
        if (ref == null) return "Descargar";
        EpisodeStore.DownloadRecord r = store.get(ref.slug, ref.episode);
        if (r == null) return "Descargar";
        if (isPlayable(r)) return "✓ Offline";
        if (EpisodeStore.STATUS_PENDING.equals(r.status)) return "En cola…";
        if (EpisodeStore.STATUS_RESOLVING.equals(r.status)) return "Resolviendo…";
        if (EpisodeStore.STATUS_DOWNLOADING.equals(r.status)) {
            if (r.totalBytes > 0) return "↓ " + Math.min(100, r.bytes * 100L / r.totalBytes) + "%";
            return "Descargando…";
        }
        if (EpisodeStore.STATUS_ERROR.equals(r.status) || EpisodeStore.STATUS_CANCELLED.equals(r.status)) return "Reintentar";
        return "Descargar";
    }

    private void syncWatchedAndCleanup(boolean force) {
        if (!isOnline() || librarySyncRunning) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastLibrarySyncMs < LIBRARY_SYNC_INTERVAL_MS) return;
        String cookie = cookieForAnimeAv1();
        if (cookie.isEmpty()) return;
        librarySyncRunning = true;
        background.execute(() -> {
            try {
                List<AnimeAv1LibraryClient.Item> items = AnimeAv1LibraryClient.fetch(cookie);
                int deleted = cleanupWatchedFiles(items);
                lastLibrarySyncMs = System.currentTimeMillis();
                if (deleted > 0) runOnUiThread(() -> { refreshLocalDownloads(); Toast.makeText(this, deleted + " episodio" + (deleted == 1 ? "" : "s") + " visto" + (deleted == 1 ? " eliminado" : "s eliminados") + " del almacenamiento local", Toast.LENGTH_LONG).show(); });
            } catch (Exception ignored) {
            } finally { librarySyncRunning = false; }
        });
    }

    private int cleanupWatchedFiles(List<AnimeAv1LibraryClient.Item> items) {
        Map<String, Integer> seen = new HashMap<>();
        for (AnimeAv1LibraryClient.Item item : items) seen.put(item.slug, Math.max(0, item.seen));
        int deleted = 0;
        for (EpisodeStore.DownloadRecord r : store.listCompleted()) {
            Integer max = seen.get(r.slug);
            if (max == null || r.episode > max) continue;
            if (!value(r.path).isEmpty()) new File(r.path).delete();
            store.delete(r.slug, r.episode);
            deleted++;
        }
        return deleted;
    }

    private void withNotificationPermission(Runnable action) {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) { action.run(); return; }
        pendingNotificationAction = action;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NOTIFICATIONS) return;
        Runnable action = pendingNotificationAction;
        pendingNotificationAction = null;
        if (action != null) action.run();
    }

    private void registerDownloadReceiver() {
        IntentFilter f = new IntentFilter(DownloadService.ACTION_DOWNLOAD_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(downloadReceiver, f);
    }

    private void registerConnectivityMonitor() {
        if (Build.VERSION.SDK_INT >= 24) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) { runOnUiThread(MainActivity.this::networkStateChanged); }
                @Override public void onLost(Network network) { runOnUiThread(MainActivity.this::networkStateChanged); }
                @Override public void onAvailable(Network network) { runOnUiThread(MainActivity.this::networkStateChanged); }
            };
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } else {
            legacyNetworkReceiver = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) { networkStateChanged(); } };
            registerReceiver(legacyNetworkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }
    }

    private void networkStateChanged() {
        String current = value(webView.getUrl());
        if (isOnline() && LocalContentServer.isOffline(current)) webView.loadUrl(lastOnlineUrl);
        else if (LocalContentServer.isDownloads(current)) webView.reload();
    }

    private boolean isOnline() {
        try {
            Network n = connectivityManager.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities c = connectivityManager.getNetworkCapabilities(n);
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Exception e) { return false; }
    }

    private String cookieForAnimeAv1() {
        return value(CookieManager.getInstance().getCookie(HOME_URL));
    }

    private EpisodeRef parseEpisode(String raw) {
        try {
            Uri u = Uri.parse(raw);
            if (!isAnimeAv1(raw)) return null;
            List<String> p = u.getPathSegments();
            if (p.size() != 3 || !"media".equals(p.get(0))) return null;
            int ep = Integer.parseInt(p.get(2));
            return ep > 0 && validSlug(p.get(1)) ? new EpisodeRef(p.get(1), ep) : null;
        } catch (Exception e) { return null; }
    }

    private boolean isAnimeAv1(String raw) {
        try {
            String host = value(Uri.parse(raw).getHost()).toLowerCase(Locale.US);
            return "animeav1.com".equals(host) || host.endsWith(".animeav1.com");
        } catch (Exception e) { return false; }
    }

    private boolean isPlayable(EpisodeStore.DownloadRecord r) {
        return r != null && EpisodeStore.STATUS_COMPLETED.equals(r.status) && !value(r.path).isEmpty() && new File(r.path).isFile();
    }

    private boolean isActive(EpisodeStore.DownloadRecord r) {
        return r != null && (EpisodeStore.STATUS_PENDING.equals(r.status) || EpisodeStore.STATUS_RESOLVING.equals(r.status) || EpisodeStore.STATUS_DOWNLOADING.equals(r.status));
    }

    private boolean validSlug(String s) { return s != null && s.matches("[A-Za-z0-9._~-]+"); }

    private String cleanTitle(String title, String slug) {
        String t = value(title).replaceAll("\\s+", " ").trim();
        if (!t.isEmpty()) return t;
        return slug.replace('-', ' ');
    }

    private String messageOf(Throwable e) {
        String m = e == null ? "" : value(e.getMessage()).trim();
        return m.isEmpty() ? "No se pudo completar la operación" : m;
    }

    private static String value(String s) { return s == null ? "" : s; }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void exitFullscreenVideo() {
        if (customView == null) return;
        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        appContainer.setVisibility(View.VISIBLE);
        customView = null;
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) openDownloads();
        else openIntentTarget(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        syncWatchedAndCleanup(false);
    }

    @Override public void onBackPressed() {
        if (customView != null) { exitFullscreenVideo(); return; }
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
        try { if (Build.VERSION.SDK_INT >= 24 && networkCallback != null) connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        try { if (legacyNetworkReceiver != null) unregisterReceiver(legacyNetworkReceiver); } catch (Exception ignored) {}
        background.shutdownNow();
        if (store != null) store.close();
        super.onDestroy();
    }

    private static final class EpisodeRef {
        final String slug; final int episode;
        EpisodeRef(String slug, int episode) { this.slug = slug; this.episode = episode; }
    }

    private static final class BatchEpisode {
        final String slug; final String title; final int episode;
        BatchEpisode(String slug, String title, int episode) { this.slug = slug; this.title = title; this.episode = episode; }
    }
}
