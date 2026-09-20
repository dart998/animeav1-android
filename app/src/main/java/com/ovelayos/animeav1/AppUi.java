package com.ovelayos.animeav1;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AppUi {
    static final int BG=Color.rgb(16,15,20), SURFACE=Color.rgb(26,25,34), ALT=Color.rgb(37,35,48);
    static final int BRAND=Color.rgb(32,214,199), TEXT=Color.rgb(247,247,251), MUTED=Color.rgb(167,165,180), DANGER=Color.rgb(255,100,124);
    static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static TextView text(Context c,String value,int sp,int color){TextView v=new TextView(c);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);return v;}
    static TextView title(Context c,String value,int sp){TextView v=text(c,value,sp,TEXT);v.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));return v;}
    static Button button(Context c,String value){Button b=new Button(c);b.setText(value);b.setTextColor(BG);b.setTextSize(13);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setAllCaps(false);b.setBackground(round(c,BRAND,14));b.setBackgroundTintList(ColorStateList.valueOf(BRAND));b.setMinHeight(dp(c,44));return b;}
    static GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static GradientDrawable round(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static GradientDrawable outlined(Context c,int color,int strokeColor,int radius){GradientDrawable d=round(c,color,radius);d.setStroke(dp(c,1),strokeColor);return d;}
    static LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(-1,-2);}
    static void margins(View v,int l,int t,int r,int b){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)v.getLayoutParams();p.setMargins(dp(v.getContext(),l),dp(v.getContext(),t),dp(v.getContext(),r),dp(v.getContext(),b));v.setLayoutParams(p);}
    private AppUi(){}
}
