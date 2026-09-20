package com.ovelayos.animeav1;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

final class DownloadsView extends LinearLayout {
    interface Actions { void play(DownloadEntry e); void cancel(DownloadEntry e); void delete(DownloadEntry e); void retry(DownloadEntry e); void batch(); }
    private final LinearLayout list;
    private final Actions actions;

    DownloadsView(Context context,Actions actions){
        super(context);this.actions=actions;setOrientation(VERTICAL);setBackgroundColor(AppUi.BG);setPadding(AppUi.dp(context,18),AppUi.dp(context,18),AppUi.dp(context,18),0);
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=AppUi.title(context,"Descargas",26);header.addView(title,new LayoutParams(0,-2,1));
        Button batch=AppUi.button(context,"Descargar pendientes");batch.setOnClickListener(v->actions.batch());header.addView(batch,new LayoutParams(-2,AppUi.dp(context,44)));addView(header,AppUi.match());
        TextView info=AppUi.text(context,"Tu biblioteca local funciona incluso sin conexión.",14,AppUi.MUTED);addView(info,AppUi.match());
        ScrollView scroll=new ScrollView(context);list=new LinearLayout(context);list.setOrientation(VERTICAL);scroll.addView(list,new ScrollView.LayoutParams(-1,-2));addView(scroll,new LayoutParams(-1,0,1));
    }

    void render(List<DownloadEntry> entries){
        list.removeAllViews();
        if(entries.isEmpty()){TextView empty=AppUi.text(getContext(),"Aún no hay episodios descargados ni en cola.",16,AppUi.MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,AppUi.dp(getContext(),72),0,0);list.addView(empty,new LayoutParams(-1,-2));return;}
        String previous="";
        for(DownloadEntry e:entries){
            String group=e.seriesTitle.isEmpty()?e.slug:e.seriesTitle;
            if(!group.equals(previous)){TextView h=AppUi.title(getContext(),group,18);h.setPadding(0,AppUi.dp(getContext(),22),0,AppUi.dp(getContext(),8));list.addView(h,AppUi.match());previous=group;}
            list.addView(card(e),AppUi.match());
        }
    }

    private View card(DownloadEntry e){
        Context c=getContext();LinearLayout card=new LinearLayout(c);card.setOrientation(VERTICAL);card.setPadding(AppUi.dp(c,14),AppUi.dp(c,12),AppUi.dp(c,14),AppUi.dp(c,12));card.setBackground(AppUi.round(AppUi.SURFACE,18));
        TextView name=AppUi.title(c,"Episodio "+e.episode,17);card.addView(name,AppUi.match());
        TextView state=AppUi.text(c,state(e),13,e.state.equals(DownloadEntry.ERROR)?AppUi.DANGER:AppUi.MUTED);card.addView(state,AppUi.match());
        if(e.totalBytes>0||e.state.equals(DownloadEntry.DOWNLOADING)){ProgressBar progress=new ProgressBar(c,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgress(e.totalBytes>0?(int)Math.min(100,e.bytes*100/e.totalBytes):0);progress.setIndeterminate(e.totalBytes<=0);card.addView(progress,AppUi.match());}
        LinearLayout buttons=new LinearLayout(c);buttons.setGravity(Gravity.END);buttons.setPadding(0,AppUi.dp(c,8),0,0);
        if(e.isPlayable())buttons.addView(action(c,"Ver",v->actions.play(e)));
        if(e.isActive())buttons.addView(action(c,e.state.equals(DownloadEntry.QUEUED)?"Quitar":"Cancelar",v->actions.cancel(e)));
        if(e.state.equals(DownloadEntry.ERROR)||e.state.equals(DownloadEntry.CANCELLED))buttons.addView(action(c,"Reintentar",v->actions.retry(e)));
        if(!e.isActive())buttons.addView(action(c,"Borrar",v->actions.delete(e)));
        card.addView(buttons,AppUi.match());LayoutParams p=(LayoutParams)card.getLayoutParams();return card;
    }
    private Button action(Context c,String text,OnClickListener click){Button b=new Button(c);b.setText(text);b.setAllCaps(false);b.setTextColor(AppUi.BRAND);b.setTextSize(13);b.setBackgroundColor(android.graphics.Color.TRANSPARENT);b.setOnClickListener(click);return b;}
    private String state(DownloadEntry e){
        String base;switch(e.state){case DownloadEntry.QUEUED:base="En cola";break;case DownloadEntry.RESOLVING:base="Resolviendo enlace";break;case DownloadEntry.DOWNLOADING:base="Descargando";break;case DownloadEntry.COMPLETED:base="Disponible offline";break;case DownloadEntry.CANCELLED:base="Cancelado";break;default:base="Error";}
        if(e.totalBytes>0)base+=" · "+human(e.bytes)+" / "+human(e.totalBytes);else if(e.bytes>0)base+=" · "+human(e.bytes);if(!e.error.isEmpty()&&e.state.equals(DownloadEntry.ERROR))base+=" · "+e.error;return base;
    }
    private String human(long b){if(b>=1073741824L)return String.format(Locale.getDefault(),"%.2f GB",b/1073741824d);if(b>=1048576)return String.format(Locale.getDefault(),"%.1f MB",b/1048576d);return String.format(Locale.getDefault(),"%.0f KB",b/1024d);}
}
