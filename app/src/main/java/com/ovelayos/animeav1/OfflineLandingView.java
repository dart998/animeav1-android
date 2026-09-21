package com.ovelayos.animeav1;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class OfflineLandingView extends LinearLayout {
    private final Button openButton;

    OfflineLandingView(Context context,Runnable downloads,Runnable retry){
        super(context);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(AppUi.dp(context,28),AppUi.dp(context,28),AppUi.dp(context,28),AppUi.dp(context,28));setBackgroundColor(AppUi.BG);
        ImageView logo=new ImageView(context);logo.setImageResource(R.drawable.animeav1_logo);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);addView(logo,new LayoutParams(AppUi.dp(context,104),AppUi.dp(context,104)));
        TextView title=AppUi.title(context,"Estás sin conexión",27);title.setGravity(Gravity.CENTER);addView(title,new LayoutParams(-1,-2));
        TextView message=AppUi.text(context,"AnimeAV1 no está disponible en este momento. Puedes seguir viendo los episodios que tienes descargados.",16,AppUi.MUTED);message.setGravity(Gravity.CENTER);message.setPadding(0,AppUi.dp(context,12),0,AppUi.dp(context,24));addView(message,new LayoutParams(-1,-2));
        openButton=AppUi.button(context,"Abrir Descargas");openButton.setId(View.generateViewId());openButton.setOnClickListener(v->downloads.run());addView(openButton,new LayoutParams(-1,AppUi.dp(context,50)));
        Button again=AppUi.button(context,"Reintentar conexión");again.setOnClickListener(v->retry.run());LayoutParams p=new LayoutParams(-1,AppUi.dp(context,50));p.topMargin=AppUi.dp(context,12);addView(again,p);
        if(AppUi.isTelevision(context))post(openButton::requestFocus);
    }

    int defaultFocusId(){return openButton.getId();}
}
