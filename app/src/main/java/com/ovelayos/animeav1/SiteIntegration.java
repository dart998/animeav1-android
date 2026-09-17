package com.ovelayos.animeav1;

import org.json.JSONObject;

final class SiteIntegration {
    private SiteIntegration() {}

    static String install(String downloadLabel) {
        String version = JSONObject.quote("AnimeAV1 Android v" + BuildConfig.VERSION_NAME);
        String label = JSONObject.quote(downloadLabel == null ? "Descargar" : downloadLabel);
        return "(function(){window.__animeav1AndroidVersion=" + version + ";window.__animeav1DownloadLabel=" + label + ";" + """
                function n(s){return String(s||'').replace(/\s+/g,' ').trim().toLowerCase();}
                function text(el){return n((el&&el.textContent)||'');}
                function visible(el){if(!el||!el.isConnected)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0;}
                function isTopLevel(){var p=location.pathname.replace(/\/+$/,'')||'/';return p==='/'||p==='/horario'||p==='/cuenta/listas'||p==='/cuenta';}
                function sectionIndex(){var p=location.pathname.replace(/\/+$/,'')||'/';if(p==='/')return 0;if(p==='/horario')return 2;if(p==='/cuenta/listas')return 3;if(p==='/cuenta')return 4;return -1;}
                function hideSiteBottomNav(){
                  var labels=['inicio','catálogo','catalogo','descargas','horario','mis listas','mi cuenta'];
                  var candidates=[].slice.call(document.querySelectorAll('nav,footer,div')).filter(function(el){
                    if(!visible(el))return false;
                    var r=el.getBoundingClientRect();if(r.height>180||r.bottom<innerHeight-220)return false;
                    var hits={};[].slice.call(el.querySelectorAll('a,button,[role=button]')).forEach(function(x){var z=text(x);if(labels.indexOf(z)>=0)hits[z]=1;});
                    var hasInicio=hits.inicio,hasHorario=hits.horario,hasListas=hits['mis listas'],hasCuenta=hits['mi cuenta'];
                    var hasMiddle=hits['catálogo']||hits.catalogo||hits.descargas;return hasInicio&&hasMiddle&&hasHorario&&hasListas&&hasCuenta;
                  });
                  if(!candidates.length)return;
                  candidates.sort(function(a,b){return a.getBoundingClientRect().height-b.getBoundingClientRect().height;});
                  var el=candidates[0];el.setAttribute('data-animeav1-site-bottom-nav','1');el.style.setProperty('display','none','important');
                }
                function footerVersion(){
                  var old=document.getElementById('animeav1-android-version');if(old){old.textContent=window.__animeav1AndroidVersion;return;}
                  var all=[].slice.call(document.querySelectorAll('footer p,footer span,footer div,p,span'));
                  var fan=all.find(function(x){return text(x)==='by fans for fans';});if(!fan)return;
                  var v=document.createElement('div');v.id='animeav1-android-version';v.textContent=window.__animeav1AndroidVersion;
                  v.style.cssText='margin-top:6px;text-align:center;font-size:12px;line-height:1.4;color:#8f92ad;opacity:.9;font-family:inherit;';
                  fan.insertAdjacentElement('afterend',v);
                }
                function episodeParts(){var p=location.pathname.split('/').filter(Boolean);if(p.length===3&&p[0]==='media'&&/^\d+$/.test(p[2]))return {slug:p[1],episode:parseInt(p[2],10)};return null;}
                function downloadElement(){if(!episodeParts())return null;return [].slice.call(document.querySelectorAll('a,button,[role=button]')).find(function(el){if(el.closest('[data-animeav1-site-bottom-nav]'))return false;var z=n((el.textContent||'')+' '+(el.getAttribute('aria-label')||'')+' '+(el.getAttribute('title')||''));return z.indexOf('descarg')===0||el.hasAttribute('data-animeav1-native-download');})||null;}
                function sourceFor(el){if(!el)return '';var a=el.matches&&el.matches('a[href]')?el:(el.closest&&el.closest('a[href]'));if(!a&&el.querySelector)a=el.querySelector('a[href]');var raw=a?a.getAttribute('href'):(el.getAttribute('data-url')||el.getAttribute('data-href')||el.getAttribute('data-link')||'');try{if(raw&&raw!=='#'&&!/^javascript:/i.test(raw))return new URL(raw,location.href).href;}catch(e){}return '';}
                function setDownloadLabel(){var el=downloadElement();if(!el)return;el.setAttribute('data-animeav1-native-download','1');var nodes=[];var w=document.createTreeWalker(el,NodeFilter.SHOW_TEXT);var t;while((t=w.nextNode()))nodes.push(t);var target=nodes.find(function(x){return n(x.nodeValue).indexOf('descarg')===0||n(x.nodeValue).indexOf('offline')>=0||n(x.nodeValue).indexOf('cola')>=0||n(x.nodeValue).indexOf('reintentar')>=0;});if(target)target.nodeValue=window.__animeav1DownloadLabel;}
                function publishedCount(slug){var max=0;[].slice.call(document.querySelectorAll('a[href]')).forEach(function(a){try{var u=new URL(a.getAttribute('href'),location.href),p=u.pathname.split('/').filter(Boolean);if(p.length===3&&p[0]==='media'&&p[1]===slug&&/^\d+$/.test(p[2]))max=Math.max(max,parseInt(p[2],10)||0);}catch(e){}});return max;}
                function ensureSeriesDownload(){var p=location.pathname.split('/').filter(Boolean);if(p.length!==2||p[0]!=='media')return;var slug=p[1];var share=[].slice.call(document.querySelectorAll('button,a,[role=button]')).find(function(e){var z=n((e.textContent||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||''));return z.indexOf('compartir')>=0||z.indexOf('share')>=0;});if(!share)return;var b=document.getElementById('animeav1-download-unwatched');if(b)return;b=document.createElement('button');b.id='animeav1-download-unwatched';b.type='button';b.className=share.className;b.setAttribute('title','Descargar episodios no vistos');b.setAttribute('aria-label','Descargar episodios no vistos');b.innerHTML='<span class="animeav1-series-arrow"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3v11"/><path d="m7 10 5 5 5-5"/><path d="M5 20h14"/></svg></span><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3l18 18"/><path d="M10.6 10.6a2 2 0 0 0 2.8 2.8"/><path d="M9.9 4.2A10.5 10.5 0 0 1 21 12a15.7 15.7 0 0 1-2.1 3"/><path d="M6.6 6.6A15.5 15.5 0 0 0 3 12a15.7 15.7 0 0 0 8.7 6.7"/></svg>';
                  b.style.display='inline-flex';b.style.alignItems='center';b.style.justifyContent='center';b.style.gap='2px';
                  b.addEventListener('click',function(e){e.preventDefault();e.stopImmediatePropagation();var published=publishedCount(slug);if(!published){alert('No se pudo determinar cuántos episodios están publicados');return;}b.classList.add('animeav1-series-downloading');try{AnimeAV1App.downloadSeries(slug,published);}catch(x){}setTimeout(function(){b.classList.remove('animeav1-series-downloading');},1200);},true);
                  share.parentNode.insertBefore(b,share);
                }
                function ensureStyle(){if(document.getElementById('animeav1-android-style'))return;var s=document.createElement('style');s.id='animeav1-android-style';s.textContent='@keyframes animeav1DownloadBounce{0%,100%{transform:translateY(-3px);opacity:.45}55%{transform:translateY(3px);opacity:1}}.animeav1-series-downloading .animeav1-series-arrow{display:inline-flex;animation:animeav1DownloadBounce .9s ease-in-out infinite}';document.head.appendChild(s);}
                function patchNewWindows(){document.querySelectorAll('a[target="_blank"]').forEach(function(a){a.removeAttribute('target');});if(!window.__animeav1OpenPatched){window.__animeav1OpenPatched=true;window.open=function(url){if(url){try{AnimeAV1App.openUrl(new URL(String(url),location.href).href);}catch(e){location.href=String(url);}}return null;};}}
                if(!window.__animeav1DownloadCapture){window.__animeav1DownloadCapture=true;document.addEventListener('click',function(e){var p=episodeParts();if(!p)return;var el=e.target&&e.target.closest?e.target.closest('a,button,[role=button]'):null;if(!el)return;var z=n((el.textContent||'')+' '+(el.getAttribute('aria-label')||'')+' '+(el.getAttribute('title')||''));if(z.indexOf('descarg')!==0&&!el.hasAttribute('data-animeav1-native-download'))return;e.preventDefault();e.stopImmediatePropagation();var h=document.querySelector('h1');var title=h?String(h.textContent||'').trim():document.title;try{AnimeAV1App.downloadEpisode(p.slug,p.episode,location.href,title,sourceFor(el));}catch(x){}},true);}
                if(!window.__animeav1SwipeInstalled){window.__animeav1SwipeInstalled=true;var sx=0,sy=0,ok=false;document.addEventListener('touchstart',function(e){ok=false;if(!isTopLevel()||!e.touches||e.touches.length!==1)return;var t=e.target;if(t&&t.closest&&t.closest('video,iframe,input,textarea,select,button,a,[contenteditable],[class*="swiper"],[class*="carousel"],[data-carousel]'))return;sx=e.touches[0].clientX;sy=e.touches[0].clientY;ok=true;},{passive:true});document.addEventListener('touchend',function(e){if(!ok||!e.changedTouches||e.changedTouches.length!==1)return;ok=false;var dx=e.changedTouches[0].clientX-sx,dy=e.changedTouches[0].clientY-sy;if(Math.abs(dx)<90||Math.abs(dx)<Math.abs(dy)*1.5||Math.abs(dy)>90)return;try{AnimeAV1App.swipe(dx<0?1:-1);}catch(x){}},{passive:true});}
                function libraryAction(u,m){u=String(u||'');m=String(m||'GET').toUpperCase();return m!=='GET'&&(u.indexOf('?/library')>=0||(u.indexOf('/cuenta/listas')>=0&&u.indexOf('library')>=0));}
                function notifyLibrary(){clearTimeout(window.__animeav1LibraryTimer);window.__animeav1LibraryTimer=setTimeout(function(){try{AnimeAV1App.libraryChanged();}catch(x){}},400);}
                if(!window.__animeav1FetchHook&&window.fetch){window.__animeav1FetchHook=true;var of=window.fetch;window.fetch=function(){var a=arguments,r=a[0],o=a[1]||{},u=typeof r==='string'?r:(r&&r.url)||'',m=o.method||(r&&r.method)||'GET';return of.apply(this,a).then(function(res){if(libraryAction(u,m))notifyLibrary();return res;});};}
                if(!window.__animeav1XhrHook&&window.XMLHttpRequest){window.__animeav1XhrHook=true;var oo=XMLHttpRequest.prototype.open,os=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(m,u){this.__av1m=m;this.__av1u=u;return oo.apply(this,arguments);};XMLHttpRequest.prototype.send=function(){if(libraryAction(this.__av1u,this.__av1m))this.addEventListener('load',notifyLibrary,{once:true});return os.apply(this,arguments);};}
                function refresh(){ensureStyle();hideSiteBottomNav();footerVersion();patchNewWindows();setDownloadLabel();ensureSeriesDownload();}
                window.__animeav1AndroidRefresh=refresh;refresh();if(!window.__animeav1Observer){window.__animeav1Observer=new MutationObserver(function(){clearTimeout(window.__animeav1RefreshTimer);window.__animeav1RefreshTimer=setTimeout(refresh,120);});window.__animeav1Observer.observe(document.documentElement,{childList:true,subtree:true});}
                })();
                """;
    }

    static String updateDownloadLabel(String value) {
        return "(function(){window.__animeav1DownloadLabel=" + JSONObject.quote(value == null ? "Descargar" : value) + ";if(window.__animeav1AndroidRefresh)window.__animeav1AndroidRefresh();})()";
    }

    static String useLocalPlayer(String videoUrl) {
        return "(function(){var src=" + JSONObject.quote(videoUrl) + ";var old=document.querySelector('video,iframe');if(!old)return false;var v=document.createElement('video');v.controls=true;v.playsInline=true;v.preload='metadata';v.src=src;v.style.cssText='width:100%;height:auto;max-height:75vh;background:#000;display:block;';var box=old.parentNode;if(box){box.replaceChild(v,old);return true;}return false;})()";
    }
}
