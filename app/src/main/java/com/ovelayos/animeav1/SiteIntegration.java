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
                "function episodeDownloadElement(){if(!/^\\/media\\/[^/]+\\/\\d+\\/?$/.test(location.pathname))return null;var all=[].slice.call(document.querySelectorAll('a,button,[role=button]'));return all.find(function(e){if(e.closest('[data-animeav1-app-nav]'))return false;return n(e.textContent).indexOf('descargar')===0;})||null;}" +
                "function sourceFor(el){if(!el)return '';var a=el.matches('a[href]')?el:el.closest('a[href]');if(!a)a=el.querySelector&&el.querySelector('a[href]');var raw=a?a.getAttribute('href'):(el.getAttribute('data-url')||el.getAttribute('data-href')||el.getAttribute('data-link')||'');try{if(raw&&raw!=='#'&&!/^javascript:/i.test(raw))return new URL(raw,location.href).href;}catch(e){}var oc=el.getAttribute('onclick')||'';var m=oc.match(/https?:\\/\\/(?:www\\.)?mega\\.(?:nz|co\\.nz)\\/[^'\"\\s)]+/i);return m?m[0]:'';}" +
                "function nativeDownload(el){var p=location.pathname.split('/');var slug=p.length>2?p[2]:'';var ep=p.length>3?p[3]:'';var h=document.querySelector('h1');var title=h?(h.textContent||'').trim():document.title;var src=sourceFor(el);location.href='animeav1://download?slug='+encodeURIComponent(slug)+'&episode='+encodeURIComponent(ep)+'&page='+encodeURIComponent(location.href)+'&title='+encodeURIComponent(title)+'&source='+encodeURIComponent(src);}" +
                "function patchDownload(){var b=episodeDownloadElement();if(!b)return;b.setAttribute('data-animeav1-native-download','1');var t=tagText(b);if(t)t.textContent=window.__animeav1AndroidLabel||'Descargar';}" +
                "if(!window.__animeav1AndroidDownloadCapture){window.__animeav1AndroidDownloadCapture=true;document.addEventListener('click',function(e){if(!/^\\/media\\/[^/]+\\/\\d+\\/?$/.test(location.pathname))return;var el=e.target&&e.target.closest?e.target.closest('a,button,[role=button]'):null;if(!el||el.closest('[data-animeav1-app-nav]'))return;if(n(el.textContent).indexOf('descargar')!==0)return;e.preventDefault();e.stopImmediatePropagation();nativeDownload(el);},true);}" +
                "function notifyLibraryChanged(){clearTimeout(window.__animeav1LibraryChangedTimer);window.__animeav1LibraryChangedTimer=setTimeout(function(){location.href='animeav1://library-changed';},350);}" +
                "function libraryAction(u,m){u=String(u||'');m=String(m||'GET').toUpperCase();return m!=='GET'&&u.indexOf('/cuenta/listas')>=0&&(u.indexOf('?/library')>=0||u.indexOf('/library')>=0);}" +
                "if(!window.__animeav1FetchHook&&window.fetch){window.__animeav1FetchHook=true;var of=window.fetch;window.fetch=function(){var a=arguments,r=a[0],o=a[1]||{},u=typeof r==='string'?r:(r&&r.url)||'',m=o.method||(r&&r.method)||'GET';return of.apply(this,a).then(function(res){if(libraryAction(u,m))notifyLibraryChanged();return res;});};}" +
                "if(!window.__animeav1XhrHook&&window.XMLHttpRequest){window.__animeav1XhrHook=true;var oo=XMLHttpRequest.prototype.open,os=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(m,u){this.__av1m=m;this.__av1u=u;return oo.apply(this,arguments);};XMLHttpRequest.prototype.send=function(){if(libraryAction(this.__av1u,this.__av1m))this.addEventListener('load',notifyLibraryChanged,{once:true});return os.apply(this,arguments);};}" +
                "function refresh(){patchNav();patchDownload();}" +
                "window.__animeav1AndroidRefresh=refresh;refresh();if(!window.__animeav1AndroidObserver){window.__animeav1AndroidObserver=new MutationObserver(function(){requestAnimationFrame(refresh);});window.__animeav1AndroidObserver.observe(document.documentElement,{childList:true,subtree:true});}" +
                "})()";
    }

    static String updateDownloadLabel(String downloadLabel) {
        return "(function(){window.__animeav1AndroidLabel=" + JSONObject.quote(downloadLabel) + ";if(window.__animeav1AndroidRefresh)window.__animeav1AndroidRefresh();})()";
    }
}
