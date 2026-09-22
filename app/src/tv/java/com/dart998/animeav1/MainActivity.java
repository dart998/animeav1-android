package com.dart998.animeav1;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String HOME="https://animeav1.com/";
    private static final int BRAND=Color.rgb(32,214,199);

    private FrameLayout root,fullscreen;
    private WebView web;
    private ProgressBar progress;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        configureWindow();

        root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web=new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(true);
        web.setFocusableInTouchMode(false);
        root.addView(web,new FrameLayout.LayoutParams(-1,-1));

        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(BRAND));
        FrameLayout.LayoutParams progressParams=new FrameLayout.LayoutParams(-1,dp(3));
        root.addView(progress,progressParams);

        fullscreen=new FrameLayout(this);
        fullscreen.setBackgroundColor(Color.BLACK);
        fullscreen.setVisibility(View.GONE);
        root.addView(fullscreen,new FrameLayout.LayoutParams(-1,-1));

        setContentView(root);
        setupWebView();

        if(state!=null)web.restoreState(state);else web.loadUrl(HOME);
        web.requestFocus();
    }

    private void configureWindow(){
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=28){
            WindowManager.LayoutParams params=getWindow().getAttributes();
            params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(params);
        }
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars(){
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());
            }
        }else{
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            |View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            |View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(){
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUserAgentString(settings.getUserAgentString()+" AnimeAV1TV/"+BuildConfig.VERSION_NAME);

        CookieManager cookies=CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web,true);

        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){
                progress.setProgress(value);
                progress.setVisibility(value<100?View.VISIBLE:View.GONE);
            }
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(customView!=null){callback.onCustomViewHidden();return;}
                customView=view;
                customCallback=callback;
                web.setVisibility(View.GONE);
                progress.setVisibility(View.GONE);
                fullscreen.setVisibility(View.VISIBLE);
                fullscreen.addView(view,new FrameLayout.LayoutParams(-1,-1));
                hideSystemBars();
            }
            @Override public void onHideCustomView(){exitFullscreen();}
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean gesture,android.os.Message result){return false;}
        });

        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){
                if(AdBlocker.shouldBlock(Uri.parse(url))){
                    view.stopLoading();
                    if(view.canGoBack())view.goBack();else view.loadUrl(HOME);
                    return;
                }
                progress.setVisibility(View.VISIBLE);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                return AdBlocker.shouldBlock(request.getUrl())?AdBlocker.emptyResponse():super.shouldInterceptRequest(view,request);
            }
            @Override public void onPageFinished(WebView view,String url){
                CookieManager.getInstance().flush();
                view.evaluateJavascript(AdBlocker.cleanupScript(),null);
                view.evaluateJavascript(TvSiteScripts.install(),null);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();
                if(AdBlocker.shouldBlock(uri))return true;
                if(isDiscord(uri)){
                    try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}
                    return true;
                }
                String scheme=uri.getScheme();
                if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))return false;
                try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}
                return true;
            }
        });
    }

    private static boolean isDiscord(Uri uri){
        String host=uri==null?null:uri.getHost();
        if(host==null)return false;
        host=host.toLowerCase(Locale.US);
        return host.equals("discord.gg")||host.equals("discord.com")||host.endsWith(".discord.com")
                ||host.equals("discordapp.com")||host.endsWith(".discordapp.com");
    }

    private int dp(int value){
        return Math.round(value*getResources().getDisplayMetrics().density);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(web!=null&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
            String command=null;
            switch(event.getKeyCode()){
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: command="v.paused?v.play():v.pause()";break;
                case KeyEvent.KEYCODE_MEDIA_PLAY: command="v.play()";break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE: command="v.pause()";break;
                case KeyEvent.KEYCODE_MEDIA_REWIND: command="v.currentTime=Math.max(0,v.currentTime-10)";break;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: command="v.currentTime=Math.min(v.duration||v.currentTime+10,v.currentTime+10)";break;
            }
            if(command!=null){
                web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){"+command+";}})()",null);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed(){
        if(customView!=null){exitFullscreen();return;}
        if(web.canGoBack())web.goBack();else super.onBackPressed();
    }

    private void exitFullscreen(){
        if(customView==null)return;
        fullscreen.removeView(customView);
        fullscreen.setVisibility(View.GONE);
        customView=null;
        web.setVisibility(View.VISIBLE);
        if(customCallback!=null)customCallback.onCustomViewHidden();
        customCallback=null;
        hideSystemBars();
        web.requestFocus();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)hideSystemBars();
    }

    @Override protected void onSaveInstanceState(Bundle out){
        web.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy(){
        web.destroy();
        super.onDestroy();
    }
}
