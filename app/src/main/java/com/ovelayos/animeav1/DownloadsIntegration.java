package com.ovelayos.animeav1;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

final class DownloadsIntegration {
    private DownloadsIntegration() {}

    static String render(List<EpisodeStore.DownloadRecord> records) {
        JSONArray items = new JSONArray();
        for (EpisodeStore.DownloadRecord r : records) {
            try {
                JSONObject o = new JSONObject();
                o.put("slug", r.slug);
                o.put("episode", r.episode);
                o.put("title", r.title == null || r.title.isEmpty() ? r.slug : r.title);
                o.put("status", r.status);
                o.put("bytes", r.bytes);
                o.put("total", r.totalBytes);
                o.put("error", r.error == null ? "" : r.error);
                o.put("path", r.path == null ? "" : r.path);
                long size = 0;
                if (r.path != null && !r.path.isEmpty()) {
                    File f = new File(r.path);
                    if (f.isFile()) size = f.length();
                }
                o.put("size", size > 0 ? size : r.bytes);
                items.put(o);
            } catch (Exception ignored) {}
        }

        return "(function(){" +
                "var data=" + items.toString() + ";" +
                "function esc(s){return String(s==null?'':s).replace(/[&<>\\\"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','\\\"':'&quot;'}[c]||c;});}" +
                "function human(n){n=Number(n||0);if(n>=1073741824)return (n/1073741824).toFixed(2)+' GB';if(n>=1048576)return (n/1048576).toFixed(1)+' MB';if(n>=1024)return (n/1024).toFixed(1)+' KB';return n+' B';}" +
                "function q(v){return encodeURIComponent(String(v==null?'':v));}" +
                "function go(a,r){location.href='animeav1://downloads-action?action='+q(a)+'&slug='+q(r.slug)+'&episode='+q(r.episode);}" +
                "var nav=document.querySelector('[data-animeav1-app-nav]');" +
                "if(!nav){var all=[].slice.call(document.querySelectorAll('nav,footer,div'));nav=all.find(function(e){var t=(e.innerText||'').toLowerCase();return t.indexOf('inicio')>=0&&t.indexOf('descargas')>=0&&t.indexOf('horario')>=0&&t.indexOf('mi cuenta')>=0;})||null;}" +
                "var logo=[].slice.call(document.querySelectorAll('a,div,header')).find(function(e){var t=(e.innerText||'').trim();return /^anime/i.test(t)&&e.getBoundingClientRect().top<180;});" +
                "var header=logo?logo.closest('header'):document.querySelector('header');" +
                "if(!window.__animeav1DownloadsState){window.__animeav1DownloadsState={scrollY:window.scrollY||0,htmlOverflow:document.documentElement.style.overflow||'',bodyOverflow:document.body.style.overflow||'',bodyOverscroll:document.body.style.overscrollBehavior||''};}" +
                "document.documentElement.style.setProperty('overflow','hidden','important');document.body.style.setProperty('overflow','hidden','important');document.body.style.setProperty('overscroll-behavior','none','important');document.body.setAttribute('data-av1d-open','1');" +
                "if(header){header.style.setProperty('position','relative','important');header.style.setProperty('z-index','2147483400','important');}" +
                "if(nav){nav.style.setProperty('z-index','2147483500','important');}" +
                "var top=header?Math.max(0,Math.ceil(header.getBoundingClientRect().bottom)):0;" +
                "var bottom=nav?Math.max(58,Math.ceil(nav.getBoundingClientRect().height)):64;" +
                "var old=document.getElementById('animeav1-downloads-page');if(old)old.remove();" +
                "var page=document.createElement('section');page.id='animeav1-downloads-page';" +
                "page.style.cssText='position:fixed;inset:0;z-index:2147483300;overflow:auto;overscroll-behavior:contain;background:#100f14;color:#f4f4fa;padding:'+Math.max(top+18,18)+'px 18px '+(bottom+26)+'px;box-sizing:border-box;font-family:inherit;';" +
                "var css=document.createElement('style');css.textContent='body[data-av1d-open=\\\"1\\\"] [data-animeav1-app-nav] a,body[data-av1d-open=\\\"1\\\"] [data-animeav1-app-nav] button,body[data-av1d-open=\\\"1\\\"] [data-animeav1-app-nav] [role=button]{color:#aeb1cd!important;opacity:.72!important;background:transparent!important;box-shadow:none!important;border-color:transparent!important}body[data-av1d-open=\\\"1\\\"] [data-animeav1-downloads-link]{color:#35dccd!important;opacity:1!important}.av1d-title{font-size:32px;font-weight:800;margin:2px 0 6px}.av1d-sub{color:#a7aac5;font-size:15px;margin:0 0 22px}.av1d-tabs{display:flex;gap:4px;border-bottom:1px solid #303244;margin-bottom:22px;overflow-x:auto}.av1d-tab{appearance:none;border:0;background:transparent;color:#c7c9df;padding:12px 13px;font:inherit;white-space:nowrap;border-bottom:3px solid transparent}.av1d-tab.on{color:#35dccd;border-color:#35dccd}.av1d-section{margin:0 0 24px}.av1d-section h2{font-size:21px;margin:0 0 10px}.av1d-card{background:#1b1c27;border:1px solid #2e3040;border-radius:12px;padding:14px;margin:0 0 10px}.av1d-name{font-weight:750;font-size:16px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.av1d-ep,.av1d-meta{color:#aeb1cd;font-size:14px;margin-top:4px}.av1d-progress{height:7px;background:#303446;border-radius:20px;overflow:hidden;margin:12px 0 7px}.av1d-progress i{display:block;height:100%;background:#35dccd;border-radius:20px}.av1d-row{display:flex;gap:9px;margin-top:12px}.av1d-btn{flex:1;border:1px solid #40445a;background:#232532;color:#e8e9f5;border-radius:9px;padding:12px 10px;font:inherit}.av1d-btn.primary{border-color:#35dccd;color:#35dccd;background:rgba(53,220,205,.07)}.av1d-btn.danger{border-color:#be334b;color:#ff536d;background:rgba(255,83,109,.06)}.av1d-empty{text-align:center;padding:56px 14px;color:#aeb1cd}.av1d-empty b{display:block;color:#fff;font-size:20px;margin-bottom:8px}.av1d-error{color:#ff6c80;margin-top:7px;font-size:13px}';page.appendChild(css);" +
                "page.insertAdjacentHTML('beforeend','<div class=\\\"av1d-title\\\">Descargas</div><p class=\\\"av1d-sub\\\">Gestiona tus episodios descargados, en cola y en curso.</p><div class=\\\"av1d-tabs\\\"><button class=\\\"av1d-tab on\\\" data-f=\\\"all\\\">Todas ('+data.length+')</button><button class=\\\"av1d-tab\\\" data-f=\\\"active\\\">En curso ('+data.filter(function(x){return x.status===\\\"downloading\\\"||x.status===\\\"resolving\\\"}).length+')</button><button class=\\\"av1d-tab\\\" data-f=\\\"pending\\\">En cola ('+data.filter(function(x){return x.status===\\\"pending\\\"}).length+')</button><button class=\\\"av1d-tab\\\" data-f=\\\"completed\\\">Completados ('+data.filter(function(x){return x.status===\\\"completed\\\"}).length+')</button></div><div id=\\\"av1d-list\\\"></div>');" +
                "function actionBtn(text,cls,fn){var b=document.createElement('button');b.className='av1d-btn '+(cls||'');b.textContent=text;b.onclick=fn;return b;}" +
                "function card(r){var c=document.createElement('article');c.className='av1d-card';c.innerHTML='<div class=\\\"av1d-name\\\">'+esc(r.title)+'</div><div class=\\\"av1d-ep\\\">Episodio '+r.episode+'</div>';" +
                "if(r.status==='downloading'||r.status==='resolving'){var pct=r.total>0?Math.min(100,Math.floor(r.bytes*100/r.total)):0;c.insertAdjacentHTML('beforeend','<div class=\\\"av1d-progress\\\"><i style=\\\"width:'+pct+'%\\\"></i></div><div class=\\\"av1d-meta\\\">'+(r.total>0?human(r.bytes)+' / '+human(r.total)+' · '+pct+'%':'Preparando descarga…')+'</div>');var row=document.createElement('div');row.className='av1d-row';row.appendChild(actionBtn('✕  Cancelar','danger',function(){go('cancel',r)}));c.appendChild(row);}" +
                "else if(r.status==='pending'){c.insertAdjacentHTML('beforeend','<div class=\\\"av1d-meta\\\">En cola</div>');var row=document.createElement('div');row.className='av1d-row';row.appendChild(actionBtn('Quitar','',function(){go('cancel',r)}));c.appendChild(row);}" +
                "else if(r.status==='completed'){c.insertAdjacentHTML('beforeend','<div class=\\\"av1d-meta\\\">'+human(r.size)+' · disponible offline</div>');var row=document.createElement('div');row.className='av1d-row';row.appendChild(actionBtn('▶  Ver offline','primary',function(){go('play',r)}));row.appendChild(actionBtn('🗑  Borrar','danger',function(){go('delete',r)}));c.appendChild(row);}" +
                "else{c.insertAdjacentHTML('beforeend','<div class=\\\"av1d-error\\\">'+esc(r.status==='cancelled'?'Descarga cancelada':(r.error||'Error de descarga'))+'</div>');var row=document.createElement('div');row.className='av1d-row';row.appendChild(actionBtn('↻  Reintentar','primary',function(){go('retry',r)}));row.appendChild(actionBtn('🗑  Borrar','danger',function(){go('delete-record',r)}));c.appendChild(row);}return c;}" +
                "function section(root,title,list){if(!list.length)return;var s=document.createElement('div');s.className='av1d-section';var h=document.createElement('h2');h.textContent=title+' ('+list.length+')';s.appendChild(h);list.forEach(function(r){s.appendChild(card(r))});root.appendChild(s);}" +
                "function draw(f){var root=page.querySelector('#av1d-list');root.innerHTML='';var a=data.filter(function(x){return x.status==='downloading'||x.status==='resolving'}),p=data.filter(function(x){return x.status==='pending'}),d=data.filter(function(x){return x.status==='completed'}),e=data.filter(function(x){return x.status==='error'||x.status==='cancelled'});if(f==='all'||f==='active')section(root,'En curso',a);if(f==='all'||f==='pending')section(root,'En cola',p);if(f==='all'||f==='completed')section(root,'Completados',d);if(f==='all')section(root,'Errores',e);if(!root.children.length)root.innerHTML='<div class=\\\"av1d-empty\\\"><b>Todavía no hay episodios aquí</b>Las descargas aparecerán en esta sección automáticamente.</div>';page.querySelectorAll('.av1d-tab').forEach(function(b){b.classList.toggle('on',b.dataset.f===f)});}" +
                "page.querySelectorAll('.av1d-tab').forEach(function(b){b.onclick=function(){draw(b.dataset.f)}});draw('all');document.body.appendChild(page);" +
                "window.__animeav1DownloadsOpen=true;" +
                "window.__animeav1DownloadsCleanup=function(){var s=window.__animeav1DownloadsState;if(!s)return;document.documentElement.style.overflow=s.htmlOverflow;document.body.style.overflow=s.bodyOverflow;document.body.style.overscrollBehavior=s.bodyOverscroll;document.body.removeAttribute('data-av1d-open');window.scrollTo(0,s.scrollY||0);window.__animeav1DownloadsState=null;window.__animeav1DownloadsOpen=false;};" +
                "if(!window.__animeav1DownloadsObserver){window.__animeav1DownloadsObserver=new MutationObserver(function(){if(window.__animeav1DownloadsOpen&&!document.getElementById('animeav1-downloads-page')&&window.__animeav1DownloadsCleanup)window.__animeav1DownloadsCleanup();});window.__animeav1DownloadsObserver.observe(document.body,{childList:true});}" +
                "})()";
    }

    static String close() {
        return "(function(){var p=document.getElementById('animeav1-downloads-page');if(p)p.remove();if(window.__animeav1DownloadsCleanup)window.__animeav1DownloadsCleanup();return true})()";
    }
}
