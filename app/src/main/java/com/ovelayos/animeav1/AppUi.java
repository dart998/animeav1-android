package com.ovelayos.animeav1;

import android.content.Context;
import android.app.UiModeManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AppUi {
    static final int BG=Color.rgb(16,15,20), SURFACE=Color.rgb(26,25,34), ALT=Color.rgb(37,35,48);
    static final int BRAND=Color.rgb(32,214,199), TEXT=Color.rgb(247,247,251), MUTED=Color.rgb(167,165,180), DANGER=Color.rgb(255,100,124);
    static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static boolean isTelevision(Context c){UiModeManager mode=(UiModeManager)c.getSystemService(Context.UI_MODE_SERVICE);PackageManager packages=c.getPackageManager();boolean fireTv=packages.hasSystemFeature("amazon.hardware.fire_tv")||("Amazon".equalsIgnoreCase(Build.MANUFACTURER)&&Build.MODEL!=null&&Build.MODEL.startsWith("AFT"));return (mode!=null&&mode.getCurrentModeType()==Configuration.UI_MODE_TYPE_TELEVISION)||packages.hasSystemFeature(PackageManager.FEATURE_LEANBACK)||fireTv;}
    static TextView text(Context c,String value,int sp,int color){TextView v=new TextView(c);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);return v;}
    static TextView title(Context c,String value,int sp){TextView v=text(c,value,sp,TEXT);v.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));return v;}
    static Button button(Context c,String value){Button b=new Button(c);b.setText(value);b.setTextColor(BG);b.setTextSize(isTelevision(c)?16:13);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setAllCaps(false);int[][] states={{android.R.attr.state_focused},{android.R.attr.state_pressed},{}};int[] colors={Color.rgb(102,244,231),Color.rgb(102,244,231),BRAND};b.setBackground(round(c,BRAND,14));b.setBackgroundTintList(new ColorStateList(states,colors));b.setMinHeight(dp(c,44));b.setFocusable(true);return b;}
    static GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static GradientDrawable round(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static GradientDrawable outlined(Context c,int color,int strokeColor,int radius){GradientDrawable d=round(c,color,radius);d.setStroke(dp(c,1),strokeColor);return d;}
    static void focusable(View view,Drawable normal,int radius){StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},outlined(view.getContext(),ALT,BRAND,radius));states.addState(new int[]{},normal);view.setBackground(states);view.setFocusable(true);view.setFocusableInTouchMode(false);}
    static LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(-1,-2);}
    static void margins(View v,int l,int t,int r,int b){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)v.getLayoutParams();p.setMargins(dp(v.getContext(),l),dp(v.getContext(),t),dp(v.getContext(),r),dp(v.getContext(),b));v.setLayoutParams(p);}
    private AppUi(){}
}
