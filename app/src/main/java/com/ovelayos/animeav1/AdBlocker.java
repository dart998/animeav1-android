package com.ovelayos.animeav1;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class AdBlocker {
    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
            "doubleclick.net",
            "googleadservices.com",
            "googlesyndication.com",
            "adservice.google.com",
            "adservice.google.es",
            "google-analytics.com",
            "googletagmanager.com",
            "googletagservices.com",
            "amazon-adsystem.com",
            "scorecardresearch.com",
            "taboola.com",
            "outbrain.com",
            "criteo.com",
            "criteo.net",
            "adsrvr.org",
            "adnxs.com",
            "rubiconproject.com",
            "pubmatic.com",
            "openx.net",
            "smartadserver.com",
            "yieldmo.com",
            "casalemedia.com",
            "media.net",
            "zedo.com",
            "popads.net",
            "popcash.net",
            "propellerads.com",
            "propellerpops.com",
            "exoclick.com",
            "exosrv.com",
            "trafficjunky.net",
            "onclicka.com",
            "onclickperformance.com",
            "hilltopads.net"
    ));

    private static final String[] URL_MARKERS = {
            "/ads/", "/adserver/", "/advert/", "/advertising/", "/banner/",
            "?ad=", "&ad=", "?ads=", "&ads=", "doubleclick", "googlesyndication",
            "popunder", "popup_ad", "adservice", "adserver"
    };

    private AdBlocker() {}

    public static boolean shouldBlock(Uri uri) {
        if (uri == null) return false;

        String host = uri.getHost();
        if (host != null) {
            host = host.toLowerCase(Locale.ROOT);
            for (String blocked : BLOCKED_HOSTS) {
                if (host.equals(blocked) || host.endsWith("." + blocked)) {
                    return true;
                }
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
                "const selectors=[" +
                "'iframe[src*=\"ads\" i]','iframe[src*=\"doubleclick\" i]'," +
                "'[id^=\"ad-\" i]','[id*=\"-ad-\" i]','[class^=\"ad-\" i]','[class*=\" ad-\" i]'," +
                "'[class*=\"advert\" i]','[id*=\"advert\" i]','[class*=\"banner-ad\" i]'," +
                "'[aria-label*=\"advertisement\" i]','[data-ad-slot]'" +
                "];" +
                "const clean=()=>selectors.forEach(s=>document.querySelectorAll(s).forEach(e=>e.remove()));" +
                "clean();" +
                "new MutationObserver(clean).observe(document.documentElement,{childList:true,subtree:true});" +
                "})();";
    }
}
