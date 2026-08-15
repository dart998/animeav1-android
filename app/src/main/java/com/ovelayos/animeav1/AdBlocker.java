package com.ovelayos.animeav1;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public final class AdBlocker {
    private static final String PREFS = "adblock";
    private static final String PREF_HOSTS = "dynamic_hosts";
    private static final String PREF_LAST_UPDATE = "last_update";
    private static final long UPDATE_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final String HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";

    private static final Set<String> BLOCKED_HOSTS = ConcurrentHashMap.newKeySet();

    static {
        BLOCKED_HOSTS.addAll(Arrays.asList(
                "doubleclick.net", "googleadservices.com", "googlesyndication.com",
                "adservice.google.com", "adservice.google.es", "google-analytics.com",
                "googletagmanager.com", "googletagservices.com", "amazon-adsystem.com",
                "scorecardresearch.com", "taboola.com", "outbrain.com", "criteo.com",
                "criteo.net", "adsrvr.org", "adnxs.com", "rubiconproject.com",
                "pubmatic.com", "openx.net", "smartadserver.com", "yieldmo.com",
                "casalemedia.com", "media.net", "zedo.com", "popads.net", "popcash.net",
                "propellerads.com", "propellerpops.com", "exoclick.com", "exosrv.com",
                "trafficjunky.net", "onclicka.com", "onclickperformance.com", "hilltopads.net",
                "hilltopads.com", "clickadu.com", "adsterra.com", "adsterra.network",
                "monetag.com", "highperformanceformat.com", "highperformancecpm.com",
                "pushground.com", "richads.com", "evadav.com", "galaksion.com"
        ));
    }

    private static final String[] URL_MARKERS = {
            "/ads/", "/adserver/", "/advert/", "/advertising/", "/banner/",
            "?ad=", "&ad=", "?ads=", "&ads=", "doubleclick", "googlesyndication",
            "popunder", "popup_ad", "adservice", "adserver", "interstitial", "popunder"
    };

    private AdBlocker() {}

    public static void initialize(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        String cached = prefs.getString(PREF_HOSTS, "");
        if (!cached.isEmpty()) {
            for (String host : cached.split("\\n")) {
                if (!host.isEmpty()) BLOCKED_HOSTS.add(host);
            }
        }

        long lastUpdate = prefs.getLong(PREF_LAST_UPDATE, 0L);
        if (System.currentTimeMillis() - lastUpdate < UPDATE_INTERVAL_MS) return;

        Executors.newSingleThreadExecutor().execute(() -> refreshHosts(prefs));
    }

    private static void refreshHosts(SharedPreferences prefs) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(HOSTS_URL).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "AnimeAV1-Android-AdBlock/1.0");

            StringBuilder saved = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    String[] parts = line.split("\\s+");
                    if (parts.length < 2) continue;

                    String host = parts[1].toLowerCase(Locale.ROOT);
                    if (host.equals("localhost") || host.equals("localhost.localdomain") || host.endsWith(".local")) continue;
                    if (!host.contains(".")) continue;

                    BLOCKED_HOSTS.add(host);
                    saved.append(host).append('\n');
                }
            }

            prefs.edit()
                    .putString(PREF_HOSTS, saved.toString())
                    .putLong(PREF_LAST_UPDATE, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
            // Keep the bundled and previously cached rules if the list cannot be refreshed.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static boolean shouldBlock(Uri uri) {
        if (uri == null) return false;

        String host = uri.getHost();
        if (host != null) {
            host = host.toLowerCase(Locale.ROOT);
            String candidate = host;
            while (candidate.contains(".")) {
                if (BLOCKED_HOSTS.contains(candidate)) return true;
                int dot = candidate.indexOf('.');
                if (dot < 0 || dot + 1 >= candidate.length()) break;
                candidate = candidate.substring(dot + 1);
            }
        }

        String url = uri.toString().toLowerCase(Locale.ROOT);
        for (String marker : URL_MARKERS) {
            if (url.contains(marker)) return true;
        }
        return false;
    }

    public static WebResourceResponse emptyResponse() {
        return new WebResourceResponse(
                "text/plain",
                StandardCharsets.UTF_8.name(),
                new ByteArrayInputStream(new byte[0])
        );
    }

    public static String cosmeticCleanupScript() {
        return "(function(){" +
                "if(window.__animeav1AdCleaner)return;window.__animeav1AdCleaner=true;" +
                "const selectors=[" +
                "'iframe[src*=\\\"ads\\\" i]','iframe[src*=\\\"doubleclick\\\" i]'," +
                "'iframe[src*=\\\"adsterra\\\" i]','iframe[src*=\\\"onclick\\\" i]'," +
                "'[id^=\\\"ad-\\\" i]','[id*=\\\"-ad-\\\" i]','[class^=\\\"ad-\\\" i]','[class*=\\\" ad-\\\" i]'," +
                "'[class*=\\\"advert\\\" i]','[id*=\\\"advert\\\" i]','[class*=\\\"banner-ad\\\" i]'," +
                "'[class*=\\\"popup\\\" i]','[class*=\\\"popunder\\\" i]','[id*=\\\"popup\\\" i]'," +
                "'[aria-label*=\\\"advertisement\\\" i]','[data-ad-slot]','[data-ad-client]'" +
                "];" +
                "const clean=()=>{selectors.forEach(s=>{try{document.querySelectorAll(s).forEach(e=>e.remove())}catch(e){}});" +
                "document.documentElement.style.overflow='auto';document.body&& (document.body.style.overflow='auto');};" +
                "clean();setInterval(clean,1500);" +
                "new MutationObserver(clean).observe(document.documentElement,{childList:true,subtree:true,attributes:true});" +
                "window.open=function(){return null;};" +
                "})();";
    }
}
