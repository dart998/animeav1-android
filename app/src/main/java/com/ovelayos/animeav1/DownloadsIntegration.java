package com.ovelayos.animeav1;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/** A local library rendered in the existing WebView; it never queries AnimeAV1. */
final class DownloadsIntegration {
    private DownloadsIntegration() {}

    static String render(List<EpisodeStore.DownloadRecord> records, boolean online) {
        JSONArray data = new JSONArray();
        for (EpisodeStore.DownloadRecord r : records) {
            JSONObject item = new JSONObject();
            try {
                item.put("slug", r.slug);
                item.put("episode", r.episode);
                item.put("title", r.title == null || r.title.isEmpty() ? r.slug : r.title);
                item.put("status", r.status);
                item.put("bytes", r.bytes);
                item.put("total", r.totalBytes);
                item.put("error", r.error == null ? "" : r.error);
                long size = r.path == null || r.path.isEmpty() ? 0 : new File(r.path).length();
                item.put("size", size > 0 ? size : r.bytes);
                data.put(item);
            } catch (Exception ignored) {}
        }

        return "(function(data,online){" + """
            var state=window.__animeav1DownloadsState;
            if(!state){
              state={filter:'all',htmlOverflow:document.documentElement.style.getPropertyValue('overflow'),
                htmlPriority:document.documentElement.style.getPropertyPriority('overflow'),
                bodyOverflow:document.body.style.getPropertyValue('overflow'),
                bodyPriority:document.body.style.getPropertyPriority('overflow')};
              window.__animeav1DownloadsState=state;
            }
            document.documentElement.style.setProperty('overflow','hidden','important');
            document.body.style.setProperty('overflow','hidden','important');
            var page=document.getElementById('animeav1-downloads-page');
            if(!page){
              page=document.createElement('section');page.id='animeav1-downloads-page';
              page.innerHTML=`<style>
                #animeav1-downloads-page{position:fixed;inset:0;z-index:2147483400;overflow-y:auto;
                  overscroll-behavior:contain;background:#100f14;color:#f4f4fa;box-sizing:border-box;
                  padding:22px 16px 32px;font-family:system-ui,sans-serif}
                #animeav1-downloads-page *{box-sizing:border-box}
                .av1d-head{display:flex;align-items:center;justify-content:space-between;gap:12px}
                .av1d-head h1{font-size:29px;margin:0 0 8px}
                .av1d-sub{color:#aeb1c9;margin:0 0 22px}
                .av1d-tabs{display:flex;gap:4px;overflow-x:auto;border-bottom:1px solid #333648;margin-bottom:20px}
                .av1d-tab{border:0;border-bottom:3px solid transparent;background:none;color:#b8bdd1;
                  font:inherit;padding:11px 10px;white-space:nowrap}
                .av1d-tab.on{color:#35dccd;border-color:#35dccd}
                .av1d-group h2{font-size:19px;margin:24px 0 10px}
                .av1d-card{border:1px solid #303448;border-radius:12px;background:#1b1d2a;padding:14px;margin-bottom:10px}
                .av1d-title{font-weight:700;overflow-wrap:anywhere}.av1d-meta{color:#b8bdd1;font-size:14px;margin-top:5px}
                .av1d-error{color:#ff7488}.av1d-track{height:7px;margin-top:12px;border-radius:6px;background:#34394b}
                .av1d-progress{height:100%;border-radius:6px;background:#35dccd}
                .av1d-actions{display:flex;gap:8px;margin-top:12px}
                .av1d-action{flex:1;border-radius:8px;border:1px solid #41465b;background:#252839;
                  color:#f4f4fa;font:inherit;padding:10px}
                .av1d-action.primary{color:#35dccd;border-color:#35dccd}
                .av1d-action.danger{color:#ff7488;border-color:#a74358}
                .av1d-back{border:0;background:transparent;color:#35dccd;font:inherit;padding:12px}
                .av1d-batch{border:1px solid #35dccd;border-radius:9px;background:#18332f;color:#35dccd;
                  font:inherit;padding:11px;margin-bottom:18px;width:100%}
                .av1d-batch:disabled{opacity:.45}
                .av1d-empty{color:#b8bdd1;text-align:center;padding:40px 12px}
              </style><div class="av1d-head"><h1>Descargas</h1><button class="av1d-back" type="button">← Volver</button></div>
              <p class="av1d-sub">Tus episodios, disponibles también sin conexión.</p>
              <button class="av1d-batch" type="button">Descargar todos los no vistos</button>
              <div class="av1d-tabs"></div><div class="av1d-list"></div>`;
              page.querySelector('.av1d-back').onclick=function(){
                if(window.AnimeAV1Android)window.AnimeAV1Android.closeDownloads();
                else if(window.__animeav1DownloadsCleanup)window.__animeav1DownloadsCleanup();
              };
              document.body.appendChild(page);
            }
            window.__animeav1DownloadsOpen=true;
            var batch=page.querySelector('.av1d-batch');batch.disabled=!online;
            batch.title=online?'Series que estás viendo':'Necesita conexión a AnimeAV1';
            batch.onclick=function(){location.href='animeav1://batch-download';};
            window.__animeav1DownloadsCleanup=function(){
              var p=document.getElementById('animeav1-downloads-page');if(p)p.remove();
              var s=window.__animeav1DownloadsState;
              if(s){document.documentElement.style.setProperty('overflow',s.htmlOverflow,s.htmlPriority);
                document.body.style.setProperty('overflow',s.bodyOverflow,s.bodyPriority);}
              window.__animeav1DownloadsState=null;window.__animeav1DownloadsOpen=false;
            };
            function human(n){n=Number(n||0);if(n>=1073741824)return (n/1073741824).toFixed(2)+' GB';
              if(n>=1048576)return (n/1048576).toFixed(1)+' MB';if(n>=1024)return (n/1024).toFixed(1)+' KB';return n+' B';}
            function action(name,kind,verb,r){var b=document.createElement('button');b.type='button';
              b.className='av1d-action '+(kind||'');b.textContent=name;b.onclick=function(){
                location.href='animeav1://downloads-action?action='+encodeURIComponent(verb)+
                  '&slug='+encodeURIComponent(r.slug)+'&episode='+encodeURIComponent(r.episode);};return b;}
            function card(r){var c=document.createElement('article');c.className='av1d-card';
              var title=document.createElement('div');title.className='av1d-title';title.textContent=r.title;c.appendChild(title);
              var meta=document.createElement('div');meta.className='av1d-meta';
              meta.textContent='Episodio '+r.episode+' · '+({pending:'En cola',resolving:'Resolviendo',
                downloading:'Descargando',completed:'Completado',cancelled:'Cancelado',error:'Error'}[r.status]||r.status);
              c.appendChild(meta);var actions=document.createElement('div');actions.className='av1d-actions';
              if(r.status==='resolving'||r.status==='downloading'){
                var pct=r.total>0?Math.min(100,Math.floor(r.bytes*100/r.total)):0;
                var track=document.createElement('div');track.className='av1d-track';
                var bar=document.createElement('div');bar.className='av1d-progress';bar.style.width=pct+'%';
                track.appendChild(bar);c.appendChild(track);
                var size=document.createElement('div');size.className='av1d-meta';
                size.textContent=r.total>0?human(r.bytes)+' / '+human(r.total)+' · '+pct+'%':'Preparando descarga…';
                c.appendChild(size);actions.appendChild(action('Cancelar','danger','cancel',r));
              }else if(r.status==='pending'){actions.appendChild(action('Quitar de la cola','danger','cancel',r));}
              else if(r.status==='completed'){
                var size=document.createElement('div');size.className='av1d-meta';size.textContent=human(r.size)+' · offline';
                c.appendChild(size);actions.appendChild(action('Ver','primary','play',r));
                actions.appendChild(action('Borrar','danger','delete',r));
              }else{
                var err=document.createElement('div');err.className='av1d-meta av1d-error';
                err.textContent=r.error||'Descarga cancelada';c.appendChild(err);
                actions.appendChild(action('Reintentar','primary','retry',r));
                actions.appendChild(action('Borrar','danger','delete-record',r));
              }
              c.appendChild(actions);return c;}
            function draw(){var tabs=page.querySelector('.av1d-tabs');tabs.replaceChildren();
              var active=data.filter(function(r){return r.status==='resolving'||r.status==='downloading'});
              var pending=data.filter(function(r){return r.status==='pending'});
              var complete=data.filter(function(r){return r.status==='completed'});
              [['all','Todas',data.length],['active','En curso',active.length],
                ['pending','En cola',pending.length],['completed','Completados',complete.length]].forEach(function(t){
                var b=document.createElement('button');b.className='av1d-tab'+(state.filter===t[0]?' on':'');
                b.textContent=t[1]+' ('+t[2]+')';b.onclick=function(){state.filter=t[0];draw();};tabs.appendChild(b);});
              var root=page.querySelector('.av1d-list');root.replaceChildren();
              function group(name,rows){if(!rows.length)return;var box=document.createElement('section');
                box.className='av1d-group';var h=document.createElement('h2');h.textContent=name+' ('+rows.length+')';
                box.appendChild(h);rows.forEach(function(r){box.appendChild(card(r));});root.appendChild(box);}
              if(state.filter==='all'||state.filter==='active')group('En curso',active);
              if(state.filter==='all'||state.filter==='pending')group('En cola',pending);
              if(state.filter==='all'||state.filter==='completed')group('Completados',complete);
              if(state.filter==='all')group('Errores y cancelados',data.filter(function(r){return r.status==='error'||r.status==='cancelled'}));
              if(!root.children.length){var empty=document.createElement('p');empty.className='av1d-empty';
                empty.textContent='Todavía no hay episodios en esta sección.';root.appendChild(empty);}
            }
            var scroll=page.scrollTop;draw();page.scrollTop=scroll;
            })(
            """ + data + "," + online + ");";
    }

    static String close() {
        return "(function(){if(window.__animeav1DownloadsCleanup)window.__animeav1DownloadsCleanup();return true})()";
    }
}
