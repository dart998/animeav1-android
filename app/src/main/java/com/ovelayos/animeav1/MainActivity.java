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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://animeav1.com/";
    private static final int DARK_FALLBACK = Color.rgb(16, 15, 20);
    private static final int REQ_NOTIFICATIONS = 41;
    private static final String PREFS = "animeav1_app";
    private static final String PREF_SITE_MENU = "site_menu_json";

    private WebView webView;
    private ProgressBar progressBar;
    private View rootContainer;
    private View bottomBar;
    private Button downloadButton;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private EpisodeStore store;
    private EpisodeRef currentEpisode;
    private boolean pendingDownloadAfterPermission;
    private float pullStartY;
    private boolean pullStartedAtTop;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String slug = intent.getStringExtra(DownloadService.EXTRA_SLUG);
            int episode = intent.getIntExtra(DownloadService.EXTRA_EPISODE, 0);
            String status = intent.getStringExtra(DownloadService.EXTRA_STATUS);
            String error = intent.getStringExtra(DownloadService.EXTRA_ERROR);

            if (currentEpisode != null && currentEpisode.slug.equals(slug) && currentEpisode.episode == episode) {
                updateDownloadButton();
                if (EpisodeStore.STATUS_COMPLETED.equals(status)) injectOfflinePlayerIfAvailable();
            }
            if (EpisodeStore.STATUS_COMPLETED.equals(status)) {
                Toast.makeText(MainActivity.this, "Episodio disponible offline", Toast.LENGTH_SHORT).show();
            } else if (EpisodeStore.STATUS_ERROR.equals(status) && error != null && !error.isEmpty()) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
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
        bottomBar = findViewById(R.id.bottomBar);
        downloadButton = findViewById(R.id.downloadButton);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);

        store = new EpisodeStore(this);
        store.markInterruptedDownloads();
        registerDownloadReceiver();
        setupBottomBar();
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
                bottomBar.setVisibility(View.GONE);
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
                updateDownloadButton();
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
                updateDownloadButton();
                injectOfflinePlayerIfAvailable();
                cacheSiteMenu();
                syncStatusBarWithWebTheme();
                enableImmersiveNavigation();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (AdBlocker.shouldBlock(uri)) return true;
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                return true;
            }
        });

        if (savedInstanceState == null) webView.loadUrl(HOME_URL);
        else webView.restoreState(savedInstanceState);
    }

    private void setupBottomBar() {
        findViewById(R.id.homeButton).setOnClickListener(v -> webView.loadUrl(HOME_URL));
        findViewById(R.id.menuButton).setOnClickListener(v -> showSiteMenu());
        downloadButton.setOnClickListener(v -> requestDownload());
        findViewById(R.id.offlineButton).setOnClickListener(v -> showOfflineLibrary());
        updateDownloadButton();
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
            if (!title.isEmpty() && currentEpisode != null && currentEpisode.same(ref)) {
                currentEpisode.title = title;
            }
        });
    }

    private void updateDownloadButton() {
        if (downloadButton == null) return;
        if (currentEpisode == null) {
            downloadButton.setEnabled(false);
            downloadButton.setText("Descargar");
            return;
        }

        downloadButton.setEnabled(true);
        EpisodeStore.DownloadRecord r = store.get(currentEpisode.slug, currentEpisode.episode);
        if (r == null) {
            downloadButton.setText("Descargar");
            return;
        }
        if (EpisodeStore.STATUS_COMPLETED.equals(r.status)) {
            if (!r.path.isEmpty() && new File(r.path).isFile()) downloadButton.setText("✓ Offline");
            else {
                store.delete(r.slug, r.episode);
                downloadButton.setText("Descargar");
            }
        } else if (EpisodeStore.STATUS_DOWNLOADING.equals(r.status) || EpisodeStore.STATUS_RESOLVING.equals(r.status)) {
            if (r.totalBytes > 0) {
                int pct = (int) Math.min(100, r.bytes * 100L / r.totalBytes);
                downloadButton.setText("↓ " + pct + "%");
            } else downloadButton.setText("Descargando…");
        } else if (EpisodeStore.STATUS_ERROR.equals(r.status)) {
            downloadButton.setText("Reintentar");
        } else downloadButton.setText("Descargar");
    }

    private void requestDownload() {
        if (currentEpisode == null) return;
        EpisodeStore.DownloadRecord existing = store.get(currentEpisode.slug, currentEpisode.episode);
        if (existing != null && EpisodeStore.STATUS_COMPLETED.equals(existing.status)
                && !existing.path.isEmpty() && new File(existing.path).isFile()) {
            Toast.makeText(this, "Este episodio ya está disponible offline", Toast.LENGTH_SHORT).show();
            return;
        }
        if (store.hasActiveDownload()) {
            Toast.makeText(this, "Ya hay una descarga en curso", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingDownloadAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        startEpisodeDownload();
    }

    private void startEpisodeDownload() {
        if (currentEpisode == null) return;
        Intent i = new Intent(this, DownloadService.class);
        i.putExtra(DownloadService.EXTRA_SLUG, currentEpisode.slug);
        i.putExtra(DownloadService.EXTRA_EPISODE, currentEpisode.episode);
        i.putExtra(DownloadService.EXTRA_PAGE_URL, currentEpisode.pageUrl);
        i.putExtra(DownloadService.EXTRA_TITLE, currentEpisode.title);
        String cookie = CookieManager.getInstance().getCookie(currentEpisode.pageUrl);
        i.putExtra(DownloadService.EXTRA_COOKIE, cookie == null ? "" : cookie);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        downloadButton.setText("Preparando…");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS && pendingDownloadAfterPermission) {
            pendingDownloadAfterPermission = false;
            startEpisodeDownload();
        }
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
        if (existing.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Descargas").setMessage("Todavía no hay episodios descargados.").setPositiveButton("Cerrar", null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Descargas")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> showOfflineActions(existing.get(which)))
                .setNegativeButton("Cerrar", null)
                .show();
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
                    updateDownloadButton();
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

    private void cacheSiteMenu() {
        if (webView.getUrl() == null || !webView.getUrl().contains("animeav1.com")) return;
        String js = "(function(){var wanted=['Inicio','Catálogo','Horario','Mis Listas','Mi cuenta'],links=[].slice.call(document.querySelectorAll('a[href]')),out=[];" +
                "wanted.forEach(function(w){var k=w.toLowerCase(),a=links.find(function(x){return (x.textContent||'').trim().toLowerCase()===k;});" +
                "if(a&&/^https?:/.test(a.href))out.push({label:w,url:a.href});});return JSON.stringify(out);})()";
        webView.evaluateJavascript(js, value -> {
            try {
                Object decoded = new JSONTokener(value).nextValue();
                if (decoded instanceof String) {
                    String json = (String) decoded;
                    if (new JSONArray(json).length() > 0) getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_SITE_MENU, json).apply();
                }
            } catch (Exception ignored) {}
        });
    }

    private void showSiteMenu() {
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SITE_MENU, "");
        try {
            JSONArray arr = new JSONArray(raw);
            if (arr.length() > 0) {
                String[] labels = new String[arr.length()];
                String[] urls = new String[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    labels[i] = o.optString("label", "Menú");
                    urls[i] = o.optString("url", HOME_URL);
                }
                new AlertDialog.Builder(this).setTitle("AnimeAV1").setItems(labels, (d, which) -> webView.loadUrl(urls[which])).setNegativeButton("Cerrar", null).show();
                return;
            }
        } catch (Exception ignored) {}

        new AlertDialog.Builder(this)
                .setTitle("AnimeAV1")
                .setItems(new String[]{"Inicio", "Ir al menú original"}, (d, which) -> {
                    if (which == 0) webView.loadUrl(HOME_URL);
                    else webView.evaluateJavascript("window.scrollTo({top:document.body.scrollHeight,behavior:'smooth'})", null);
                })
                .setNegativeButton("Cerrar", null)
                .show();
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
        bottomBar.setVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        applySystemBarInsets();
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

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enableImmersiveNavigation(); }
    @Override protected void onSaveInstanceState(Bundle outState) { webView.saveState(outState); super.onSaveInstanceState(outState); }
    @Override protected void onPause() { CookieManager.getInstance().flush(); webView.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); webView.onResume(); enableImmersiveNavigation(); updateDownloadButton(); if (customView == null) webView.postDelayed(this::syncStatusBarWithWebTheme, 250); }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
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
}
