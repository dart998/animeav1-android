package com.dart998.animeav1;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private final TextView summary;
    private final Actions actions;

    DownloadsView(Context context,Actions actions){
        super(context);this.actions=actions;setOrientation(VERTICAL);setBackgroundColor(AppUi.BG);setPadding(AppUi.dp(context,20),AppUi.dp(context,22),AppUi.dp(context,20),0);

        LinearLayout heading=new LinearLayout(context);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=AppUi.title(context,"Descargas",29);heading.addView(title,new LayoutParams(0,AppUi.dp(context,44),1));
        TextView offline=AppUi.text(context,"OFFLINE",11,AppUi.BRAND);offline.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));offline.setGravity(Gravity.CENTER);offline.setBackground(AppUi.outlined(context,Color.argb(22,32,214,199),Color.argb(115,32,214,199),20));heading.addView(offline,new LayoutParams(AppUi.dp(context,76),AppUi.dp(context,30)));addView(heading,AppUi.match());

        TextView info=AppUi.text(context,"Tu biblioteca local, disponible incluso sin conexión.",14,AppUi.MUTED);LayoutParams infoParams=AppUi.match();infoParams.bottomMargin=AppUi.dp(context,16);addView(info,infoParams);

        Button batch=AppUi.button(context,"Descargar episodios pendientes");batch.setOnClickListener(v->actions.batch());LayoutParams batchParams=new LayoutParams(-1,AppUi.dp(context,50));batchParams.bottomMargin=AppUi.dp(context,16);addView(batch,batchParams);

        summary=AppUi.text(context,"",13,AppUi.MUTED);summary.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));summary.setPadding(AppUi.dp(context,2),0,0,AppUi.dp(context,10));addView(summary,AppUi.match());

        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);scroll.setPadding(0,0,0,AppUi.dp(context,22));
        list=new LinearLayout(context);list.setOrientation(VERTICAL);scroll.addView(list,new ScrollView.LayoutParams(-1,-2));addView(scroll,new LayoutParams(-1,0,1));
    }

    void render(List<DownloadEntry> entries){
        list.removeAllViews();int completed=0,active=0;
        for(DownloadEntry e:entries){if(e.isPlayable())completed++;if(e.isActive())active++;}
        summary.setText(entries.size()+" episodios  ·  "+completed+" offline"+(active>0?"  ·  "+active+" en curso":""));
        if(entries.isEmpty()){list.addView(empty(),new LayoutParams(-1,-1));return;}

        String previous="";
        for(DownloadEntry e:entries){
            String group=e.seriesTitle.isEmpty()?e.slug:e.seriesTitle;
            if(!group.equals(previous)){
                TextView h=AppUi.title(getContext(),group,18);h.setMaxLines(2);h.setPadding(AppUi.dp(getContext(),2),AppUi.dp(getContext(),15),AppUi.dp(getContext(),2),AppUi.dp(getContext(),9));list.addView(h,AppUi.match());previous=group;
            }
            LayoutParams cardParams=new LayoutParams(-1,-2);cardParams.bottomMargin=AppUi.dp(getContext(),11);list.addView(card(e),cardParams);
        }
    }

    private View empty(){
        Context c=getContext();LinearLayout box=new LinearLayout(c);box.setOrientation(VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(AppUi.dp(c,22),AppUi.dp(c,54),AppUi.dp(c,22),AppUi.dp(c,38));box.setBackground(AppUi.outlined(c,AppUi.SURFACE,AppUi.ALT,18));
        TextView icon=AppUi.text(c,"↓",38,AppUi.BRAND);icon.setGravity(Gravity.CENTER);box.addView(icon,new LayoutParams(-1,AppUi.dp(c,56)));
        TextView title=AppUi.title(c,"Todavía no hay descargas",19);title.setGravity(Gravity.CENTER);box.addView(title,AppUi.match());
        TextView text=AppUi.text(c,"Los episodios descargados y los elementos de la cola aparecerán aquí.",14,AppUi.MUTED);text.setGravity(Gravity.CENTER);text.setPadding(0,AppUi.dp(c,8),0,0);box.addView(text,AppUi.match());return box;
    }

    private View card(DownloadEntry e){
        Context c=getContext();LinearLayout card=new LinearLayout(c);card.setOrientation(VERTICAL);card.setPadding(AppUi.dp(c,14),AppUi.dp(c,14),AppUi.dp(c,14),AppUi.dp(c,12));card.setBackground(AppUi.outlined(c,AppUi.SURFACE,AppUi.ALT,16));

        LinearLayout top=new LinearLayout(c);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView episode=AppUi.text(c,"EP\n"+e.episode,13,AppUi.BRAND);episode.setGravity(Gravity.CENTER);episode.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));episode.setBackground(AppUi.round(c,Color.argb(24,32,214,199),13));top.addView(episode,new LayoutParams(AppUi.dp(c,54),AppUi.dp(c,54)));

        LinearLayout detail=new LinearLayout(c);detail.setOrientation(VERTICAL);detail.setGravity(Gravity.CENTER_VERTICAL);detail.setPadding(AppUi.dp(c,13),0,0,0);
        TextView name=AppUi.title(c,"Episodio "+e.episode,17);detail.addView(name,AppUi.match());
        TextView state=AppUi.text(c,state(e),13,stateColor(e));state.setMaxLines(2);detail.addView(state,AppUi.match());top.addView(detail,new LayoutParams(0,-2,1));card.addView(top,AppUi.match());

        if(e.totalBytes>0||DownloadEntry.DOWNLOADING.equals(e.state)||DownloadEntry.RESOLVING.equals(e.state)){
            ProgressBar progress=new ProgressBar(c,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgress(e.totalBytes>0?(int)Math.min(100,e.bytes*100/e.totalBytes):0);progress.setIndeterminate(e.totalBytes<=0);progress.setProgressTintList(ColorStateList.valueOf(AppUi.BRAND));progress.setIndeterminateTintList(ColorStateList.valueOf(AppUi.BRAND));progress.setProgressBackgroundTintList(ColorStateList.valueOf(AppUi.ALT));LayoutParams progressParams=new LayoutParams(-1,AppUi.dp(c,6));progressParams.topMargin=AppUi.dp(c,13);card.addView(progress,progressParams);
        }

        LinearLayout buttons=new LinearLayout(c);buttons.setGravity(Gravity.END);buttons.setPadding(0,AppUi.dp(c,11),0,0);
        if(e.isPlayable())buttons.addView(action(c,"Ver offline",true,false,v->actions.play(e)),actionParams(c));
        if(e.isActive())buttons.addView(action(c,DownloadEntry.QUEUED.equals(e.state)?"Quitar de la cola":"Cancelar",false,true,v->actions.cancel(e)),actionParams(c));
        if(DownloadEntry.ERROR.equals(e.state)||DownloadEntry.CANCELLED.equals(e.state))buttons.addView(action(c,"Reintentar",true,false,v->actions.retry(e)),actionParams(c));
        if(!e.isActive())buttons.addView(action(c,"Borrar",false,true,v->actions.delete(e)),actionParams(c));
        card.addView(buttons,AppUi.match());return card;
    }

    private LayoutParams actionParams(Context c){LayoutParams p=new LayoutParams(0,AppUi.dp(c,42),1);p.leftMargin=AppUi.dp(c,5);p.rightMargin=AppUi.dp(c,5);return p;}
    private TextView action(Context c,String text,boolean primary,boolean danger,OnClickListener click){int color=danger?AppUi.DANGER:(primary?AppUi.BRAND:AppUi.TEXT);int stroke=danger?Color.argb(150,255,100,124):(primary?Color.argb(160,32,214,199):AppUi.ALT);int fill=danger?Color.argb(16,255,100,124):(primary?Color.argb(16,32,214,199):Color.TRANSPARENT);TextView b=AppUi.text(c,text,13,color);b.setGravity(Gravity.CENTER);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setBackground(AppUi.outlined(c,fill,stroke,11));b.setOnClickListener(click);return b;}

    private int stateColor(DownloadEntry e){if(DownloadEntry.ERROR.equals(e.state)||DownloadEntry.CANCELLED.equals(e.state))return AppUi.DANGER;if(e.isPlayable()||DownloadEntry.DOWNLOADING.equals(e.state))return AppUi.BRAND;return AppUi.MUTED;}
    private String state(DownloadEntry e){
        String base;switch(e.state){case DownloadEntry.QUEUED:base="En cola";break;case DownloadEntry.RESOLVING:base="Resolviendo enlace";break;case DownloadEntry.DOWNLOADING:base="Descargando";break;case DownloadEntry.COMPLETED:base="Disponible offline";break;case DownloadEntry.CANCELLED:base="Cancelado";break;default:base="Error";}
        if(e.totalBytes>0)base+="  ·  "+human(e.bytes)+" / "+human(e.totalBytes);else if(e.bytes>0)base+="  ·  "+human(e.bytes);if(!e.error.isEmpty()&&DownloadEntry.ERROR.equals(e.state))base+="  ·  "+e.error;return base;
    }
    private String human(long b){if(b>=1073741824L)return String.format(Locale.getDefault(),"%.2f GB",b/1073741824d);if(b>=1048576)return String.format(Locale.getDefault(),"%.1f MB",b/1048576d);return String.format(Locale.getDefault(),"%.0f KB",b/1024d);}
}
