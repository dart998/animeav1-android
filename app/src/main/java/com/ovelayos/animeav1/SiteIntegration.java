package com.ovelayos.animeav1;

import org.json.JSONObject;

final class SiteIntegration {
    private SiteIntegration() {}

    static String install(String downloadLabel) {
        return "(function(){" +
                "window.__animeav1AndroidLabel=" + JSONObject.quote(downloadLabel) + ";" +
                "function n(s){return (s||'').replace(/\\s+/g,' ').trim().toLowerCase();}" +
                "function tagText(el){if(!el)return null;var x=el.querySelector('[data-animeav1-download-text]');if(x)return x;var w=document.createTreeWalker(el,NodeFilter.SHOW_TEXT),t;while((t=w.nextNode())){if(n(t.nodeValue).indexOf('descargar')===0){var s=document.createElement('span');s.setAttribute('data-animeav1-download-text','1');s.textContent=t.nodeValue;t.parentNode.replaceChild(s,t);return s;}}return null;}" +
                "function replaceCatalog(el){if(!el)return;var w=document.createTreeWalker(el,NodeFilter.SHOW_TEXT),t;while((t=w.nextNode())){if(n(t.nodeValue)==='catálogo'||n(t.nodeValue)==='catalogo'){t.nodeValue=t.nodeValue.replace(/catálogo|catalogo/i,'Descargas');break;}}}" +
                "function patchNav(){var cat=document.querySelector('[data-animeav1-downloads-link]');if(!cat){var all=[].slice.call(document.querySelectorAll('a[href],button'));cat=all.find(function(e){var z=n(e.textContent);return z==='catálogo'||z==='catalogo';});}" +
                "if(!cat)return;cat.setAttribute('data-animeav1-downloads-link','1');replaceCatalog(cat);" +
                "var box=cat.closest('[data-animeav1-app-nav]');if(!box){var p=cat;for(var i=0;i<8&&p;i++,p=p.parentElement){var tx=n(p.innerText);if(tx.indexOf('inicio')>=0&&tx.indexOf('horario')>=0&&tx.indexOf('mis listas')>=0&&tx.indexOf('mi cuenta')>=0){box=p;break;}}}" +
                "if(box&&box!==document.body&&box!==document.documentElement){box.setAttribute('data-animeav1-app-nav','1');box.style.setProperty('position','fixed','important');box.style.setProperty('left','0','important');box.style.setProperty('right','0','important');box.style.setProperty('bottom','0','important');box.style.setProperty('z-index','2147483000','important');box.style.setProperty('margin','0','important');box.style.setProperty('width','100%','important');box.style.setProperty('padding-bottom','env(safe-area-inset-bottom)','important');var h=Math.max(56,Math.ceil(box.getBoundingClientRect().height));document.body.style.setProperty('padding-bottom','calc('+h+'px + env(safe-area-inset-bottom))','important');}" +
                "if(cat.tagName==='A')cat.setAttribute('href','animeav1://downloads');if(!cat.dataset.animeav1Bound){cat.dataset.animeav1Bound='1';cat.addEventListener('click',function(e){e.preventDefault();e.stopImmediatePropagation();location.href='animeav1://downloads';},true);}}" +
                "function patchDownload(){if(!/^\\/media\\/[^/]+\\/\\d+\\/?$/.test(location.pathname))return;var b=document.querySelector('[data-animeav1-native-download]');if(!b){var all=[].slice.call(document.querySelectorAll('a,button,[role=button]'));b=all.find(function(e){return n(e.textContent).indexOf('descargar')===0;});}" +
                "if(!b)return;if(!b.dataset.animeav1NativeDownload){b.dataset.animeav1NativeDownload='1';var a=b.matches('a[href]')?b:b.closest('a[href]');var raw=a?a.getAttribute('href'):(b.getAttribute('data-url')||b.getAttribute('data-href')||'');var src='';try{if(raw&&raw!=='#'&&!/^javascript:/i.test(raw))src=new URL(raw,location.href).href;}catch(e){}b.dataset.animeav1Source=src;tagText(b);b.addEventListener('click',function(e){e.preventDefault();e.stopImmediatePropagation();var p=location.pathname.split('/');var slug=p.length>2?p[2]:'';var ep=p.length>3?p[3]:'';var h=document.querySelector('h1');var title=h?(h.textContent||'').trim():document.title;var u='animeav1://download?slug='+encodeURIComponent(slug)+'&episode='+encodeURIComponent(ep)+'&page='+encodeURIComponent(location.href)+'&title='+encodeURIComponent(title)+'&source='+encodeURIComponent(b.dataset.animeav1Source||'');location.href=u;},true);}" +
                "var t=tagText(b);if(t)t.textContent=window.__animeav1AndroidLabel||'Descargar';}" +
                "function refresh(){patchNav();patchDownload();}" +
                "window.__animeav1AndroidRefresh=refresh;refresh();if(!window.__animeav1AndroidObserver){window.__animeav1AndroidObserver=new MutationObserver(function(){requestAnimationFrame(refresh);});window.__animeav1AndroidObserver.observe(document.documentElement,{childList:true,subtree:true});}" +
                "})()";
    }

    static String updateDownloadLabel(String downloadLabel) {
        return "(function(){window.__animeav1AndroidLabel=" + JSONObject.quote(downloadLabel) + ";if(window.__animeav1AndroidRefresh)window.__animeav1AndroidRefresh();})()";
    }
}
