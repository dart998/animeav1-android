package com.ovelayos.animeav1;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.CookieManager;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class DownloadsActivity extends Activity {
    private static final int BG=Color.rgb(16,15,20),CARD=Color.rgb(27,28,39),CARD2=Color.rgb(31,33,48),MUTED=Color.rgb(167,170,197),ACCENT=Color.rgb(53,220,205),DANGER=Color.rgb(255,79,99),NAV=Color.rgb(20,20,28);
    private EpisodeStore store;
    private LinearLayout content,tabs;
    private String filter="all";
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){render();}};

    @Override protected void onCreate(Bundle b){super.onCreate(b);overridePendingTransition(0,0);store=new EpisodeStore(this);buildUi();register();render();}
    @Override protected void onResume(){super.onResume();render();overridePendingTransition(0,0);}
    @Override public void finish(){super.finish();overridePendingTransition(0,0);}
    @Override protected void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception ignored){}store.close();super.onDestroy();}
    private void register(){IntentFilter f=new IntentFilter(DownloadService.ACTION_DOWNLOAD_UPDATED);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);}

    private void buildUi(){
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.addView(header(),new LinearLayout.LayoutParams(-1,dp(78)));

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(8),dp(18),0);
        body.addView(text("Descargas",28,Color.WHITE,true));
        TextView sub=text("Gestiona tus episodios descargados, en cola y en curso.",14,MUTED,false);sub.setPadding(0,dp(4),0,0);body.addView(sub);
        tabs=new LinearLayout(this);tabs.setPadding(0,dp(16),0,dp(8));body.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,dp(4),0,dp(24));scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(bottomNav(),new LinearLayout.LayoutParams(-1,dp(74)));
        setContentView(root);
    }

    private View header(){
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);h.setPadding(dp(18),dp(4),dp(14),0);h.setBackgroundColor(BG);
        TextView logo=text("Anime▽",30,Color.WHITE,true);h.addView(logo,new LinearLayout.LayoutParams(0,-1,1));
        h.addView(iconButton("◉"),new LinearLayout.LayoutParams(dp(48),dp(48)));
        h.addView(iconButton("♢"),new LinearLayout.LayoutParams(dp(48),dp(48)));
        h.addView(iconButton("☼"),new LinearLayout.LayoutParams(dp(48),dp(48)));
        h.addView(iconButton("⌕"),new LinearLayout.LayoutParams(dp(48),dp(48)));
        return h;
    }

    private View bottomNav(){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setBackgroundColor(NAV);nav.setPadding(dp(6),0,dp(6),0);
        nav.addView(navButton("⌂\nInicio",false,v->finish()),new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navButton("◇\nDescargas",true,v->{}),new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navButton("□\nHorario",false,v->finish()),new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navButton("☷\nMis listas",false,v->finish()),new LinearLayout.LayoutParams(0,-1,1));
        nav.addView(navButton("♙\nMi cuenta",false,v->finish()),new LinearLayout.LayoutParams(0,-1,1));
        return nav;
    }

    private void render(){if(content==null)return;List<EpisodeStore.DownloadRecord> all=store.listAll();renderTabs(all);content.removeAllViews();boolean any=false;if("all".equals(filter)||"active".equals(filter))any|=section(all,"En curso",EpisodeStore.STATUS_DOWNLOADING,EpisodeStore.STATUS_RESOLVING);if("all".equals(filter)||"queue".equals(filter))any|=section(all,"En cola",EpisodeStore.STATUS_PENDING);if("all".equals(filter)||"done".equals(filter))any|=section(all,"Completados",EpisodeStore.STATUS_COMPLETED);if("all".equals(filter))any|=section(all,"Errores",EpisodeStore.STATUS_ERROR,EpisodeStore.STATUS_CANCELLED);if(!any)emptyState();}
    private void renderTabs(List<EpisodeStore.DownloadRecord> all){tabs.removeAllViews();int active=0,queue=0,done=0;for(EpisodeStore.DownloadRecord r:all){if(EpisodeStore.STATUS_DOWNLOADING.equals(r.status)||EpisodeStore.STATUS_RESOLVING.equals(r.status))active++;else if(EpisodeStore.STATUS_PENDING.equals(r.status))queue++;else if(EpisodeStore.STATUS_COMPLETED.equals(r.status))done++;}addTab("Todas ("+all.size()+")","all");addTab("En curso ("+active+")","active");addTab("En cola ("+queue+")","queue");addTab("Completados ("+done+")","done");}
    private void addTab(String label,String id){Button b=button(label,id.equals(filter));b.setTextSize(12);b.setOnClickListener(v->{filter=id;render();});tabs.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));}
    private boolean section(List<EpisodeStore.DownloadRecord> all,String name,String... statuses){LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);int count=0;for(EpisodeStore.DownloadRecord r:all){boolean ok=false;for(String s:statuses)if(s.equals(r.status))ok=true;if(!ok)continue;list.addView(card(r));count++;}if(count==0)return false;TextView h=text(name+" ("+count+")",20,Color.WHITE,true);h.setPadding(0,dp(14),0,dp(8));content.addView(h);content.addView(list);return true;}

    private View card(EpisodeStore.DownloadRecord r){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(12),dp(14),dp(12));c.setBackgroundColor(CARD2);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(10));c.setLayoutParams(cp);
        String name=(r.title==null||r.title.isEmpty())?r.slug:r.title;TextView t=text(name,16,Color.WHITE,true);t.setSingleLine(true);c.addView(t);c.addView(text("Episodio "+r.episode,14,MUTED,false));
        if(EpisodeStore.STATUS_DOWNLOADING.equals(r.status)||EpisodeStore.STATUS_RESOLVING.equals(r.status)){
            ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setMax(100);p.setProgress(r.totalBytes>0?(int)Math.min(100,r.bytes*100L/r.totalBytes):0);c.addView(p,new LinearLayout.LayoutParams(-1,dp(8)));
            String detail=r.totalBytes>0?human(r.bytes)+" / "+human(r.totalBytes)+" · "+(r.bytes*100L/r.totalBytes)+"%":"Preparando descarga…";TextView d=text(detail,13,MUTED,false);d.setPadding(0,dp(6),0,dp(6));c.addView(d);
            Button cancel=button("✕  Cancelar",false);cancel.setTextColor(DANGER);cancel.setOnClickListener(v->cancel(r));c.addView(cancel,new LinearLayout.LayoutParams(-1,dp(48)));
        }else if(EpisodeStore.STATUS_PENDING.equals(r.status)){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text("En cola",13,MUTED,false),new LinearLayout.LayoutParams(0,dp(46),1));Button q=button("Quitar",false);q.setOnClickListener(v->cancel(r));row.addView(q,new LinearLayout.LayoutParams(dp(100),dp(44)));c.addView(row);
        }else if(EpisodeStore.STATUS_COMPLETED.equals(r.status)){
            File f=new File(r.path);TextView d=text(human(f.isFile()?f.length():r.bytes)+" · disponible offline",13,MUTED,false);d.setPadding(0,dp(4),0,dp(8));c.addView(d);
            LinearLayout row=new LinearLayout(this);Button play=button("▶  Ver offline",true);play.setOnClickListener(v->play(r));Button del=button("🗑  Borrar",false);del.setTextColor(DANGER);del.setOnClickListener(v->confirmDelete(r));row.addView(play,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(del,new LinearLayout.LayoutParams(0,dp(50),1));c.addView(row);
        }else{
            String msg=EpisodeStore.STATUS_CANCELLED.equals(r.status)?"Descarga cancelada":(r.error==null?"Error de descarga":r.error);TextView e=text(msg,13,DANGER,false);e.setPadding(0,dp(5),0,dp(8));c.addView(e);
            LinearLayout row=new LinearLayout(this);Button retry=button("↻  Reintentar",true);retry.setOnClickListener(v->retry(r));Button del=button("🗑  Borrar",false);del.setTextColor(DANGER);del.setOnClickListener(v->{store.delete(r.slug,r.episode);render();});row.addView(retry,new LinearLayout.LayoutParams(0,dp(50),1));row.addView(del,new LinearLayout.LayoutParams(0,dp(50),1));c.addView(row);
        }
        return c;
    }

    private void emptyState(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(dp(18),dp(64),dp(18),0);TextView icon=text("⇩",72,ACCENT,true);icon.setGravity(Gravity.CENTER);box.addView(icon);TextView h=text("Todavía no hay episodios descargados",20,Color.WHITE,true);h.setGravity(Gravity.CENTER);box.addView(h);TextView p=text("Aquí aparecerán tus descargas en curso, en cola y los episodios disponibles para ver sin conexión.",14,MUTED,false);p.setGravity(Gravity.CENTER);p.setPadding(dp(10),dp(12),dp(10),dp(20));box.addView(p);Button go=button("Buscar una serie",true);go.setOnClickListener(v->finish());box.addView(go,new LinearLayout.LayoutParams(-1,dp(54)));content.addView(box);}

    private void cancel(EpisodeStore.DownloadRecord r){Intent i=new Intent(this,DownloadService.class).setAction(DownloadService.ACTION_CANCEL).putExtra(DownloadService.EXTRA_SLUG,r.slug).putExtra(DownloadService.EXTRA_EPISODE,r.episode);startService(i);}
    private void retry(EpisodeStore.DownloadRecord r){Intent i=new Intent(this,DownloadService.class);i.putExtra(DownloadService.EXTRA_SLUG,r.slug);i.putExtra(DownloadService.EXTRA_EPISODE,r.episode);i.putExtra(DownloadService.EXTRA_PAGE_URL,r.pageUrl.isEmpty()?"https://animeav1.com/media/"+r.slug+"/"+r.episode:r.pageUrl);i.putExtra(DownloadService.EXTRA_TITLE,r.title);i.putExtra(DownloadService.EXTRA_SOURCE_URL,r.sourceUrl);String cookie=CookieManager.getInstance().getCookie("https://animeav1.com/");i.putExtra(DownloadService.EXTRA_COOKIE,cookie==null?"":cookie);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    private void play(EpisodeStore.DownloadRecord r){Intent i=new Intent(this,OfflinePlayerActivity.class);i.putExtra(OfflinePlayerActivity.EXTRA_PATH,r.path);i.putExtra(OfflinePlayerActivity.EXTRA_TITLE,r.title.isEmpty()?r.slug:r.title);i.putExtra(OfflinePlayerActivity.EXTRA_EPISODE,r.episode);startActivity(i);overridePendingTransition(0,0);}
    private void confirmDelete(EpisodeStore.DownloadRecord r){new AlertDialog.Builder(this).setTitle("Eliminar episodio").setMessage("¿Borrar la copia local del episodio "+r.episode+"?").setPositiveButton("Eliminar",(d,w)->{if(!r.path.isEmpty())new File(r.path).delete();store.delete(r.slug,r.episode);render();}).setNegativeButton("Cancelar",null).show();}
    private void showGlobalOptions(){new AlertDialog.Builder(this).setTitle("Opciones").setItems(new String[]{"Cancelar todas las descargas","Borrar todos los completados","Borrar errores"},(d,w)->{if(w==0)startService(new Intent(this,DownloadService.class).setAction(DownloadService.ACTION_CANCEL_ALL));else if(w==1){for(EpisodeStore.DownloadRecord r:store.listCompleted()){if(!r.path.isEmpty())new File(r.path).delete();store.delete(r.slug,r.episode);}render();}else{store.deleteByStatus(EpisodeStore.STATUS_ERROR);store.deleteByStatus(EpisodeStore.STATUS_CANCELLED);render();}}).show();}

    private Button iconButton(String s){Button b=button(s,false);b.setTextSize(19);b.setPadding(0,0,0,0);return b;}
    private Button navButton(String s,boolean active,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);b.setGravity(Gravity.CENTER);b.setTextColor(active?ACCENT:Color.rgb(190,193,220));b.setBackgroundColor(NAV);b.setPadding(0,0,0,0);b.setOnClickListener(l);return b;}
    private Button button(String s,boolean accent){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(accent?Color.rgb(8,40,40):Color.rgb(220,222,238));b.setBackgroundColor(accent?ACCENT:CARD);b.setPadding(dp(8),0,dp(8),0);return b;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}private String human(long b){if(b>=1073741824L)return String.format(Locale.US,"%.2f GB",b/1073741824d);if(b>=1048576L)return String.format(Locale.US,"%.1f MB",b/1048576d);if(b>=1024L)return String.format(Locale.US,"%.1f KB",b/1024d);return b+" B";}
}
