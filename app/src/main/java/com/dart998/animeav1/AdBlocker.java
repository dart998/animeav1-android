package com.dart998.animeav1;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class AdBlocker {
    private static final Set<String> BLOCKED_HOSTS=new HashSet<>(Arrays.asList(
            "runative-syndicate.com","runative.com","doubleclick.net","googleadservices.com",
            "googlesyndication.com","adservice.google.com","googletagservices.com",
            "amazon-adsystem.com","taboola.com","outbrain.com","criteo.com","criteo.net",
            "adsrvr.org","adnxs.com","rubiconproject.com","pubmatic.com","openx.net",
            "popads.net","popcash.net","propellerads.com","propellerpops.com","exoclick.com",
            "exosrv.com","trafficjunky.net","onclicka.com","onclickperformance.com",
            "hilltopads.net","hilltopads.com","clickadu.com","adsterra.com","adsterra.network",
            "monetag.com","highperformanceformat.com","highperformancecpm.com","pushground.com"
    ));
    private static final String[] URL_MARKERS={"/ads/","/adserver/","/advert/","/advertising/","popunder","popup_ad","interstitial"};

    static boolean shouldBlock(Uri uri){
        if(uri==null)return false;String host=uri.getHost();
        if(host!=null){String candidate=host.toLowerCase(Locale.ROOT);while(true){if(BLOCKED_HOSTS.contains(candidate))return true;int dot=candidate.indexOf('.');if(dot<0)break;candidate=candidate.substring(dot+1);}}
        String url=uri.toString().toLowerCase(Locale.ROOT);for(String marker:URL_MARKERS)if(url.contains(marker))return true;return false;
    }

    static WebResourceResponse emptyResponse(){return new WebResourceResponse("text/plain",StandardCharsets.UTF_8.name(),new ByteArrayInputStream(new byte[0]));}

    static String cleanupScript(){
        return "(function(){if(window.__av1AdClean)return;window.__av1AdClean=1;var selectors=['iframe[src*=\\\"runative\\\" i]','iframe[src*=\\\"ads\\\" i]','iframe[src*=\\\"doubleclick\\\" i]','[class*=\\\"popunder\\\" i]','[data-ad-slot]'];function clean(){selectors.forEach(function(s){try{document.querySelectorAll(s).forEach(function(e){e.remove();});}catch(_){} });}clean();new MutationObserver(clean).observe(document.documentElement,{childList:true,subtree:true});})()";
    }

    private AdBlocker(){}
}
