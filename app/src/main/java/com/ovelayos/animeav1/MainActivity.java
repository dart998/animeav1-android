package com.ovelayos.animeav1;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements DownloadsView.Actions {
    static final String EXTRA_URL="open_url";
    private static final int NOTIFICATION_PERMISSION=44;
    private static final String[] URLS={AnimeAv1Client.ORIGIN+"/", "", AnimeAv1Client.ORIGIN+"/horario", AnimeAv1Client.ORIGIN+"/cuenta/listas", AnimeAv1Client.ORIGIN+"/cuenta"};
    private static final String[] LABELS={"Inicio","Descargas","Horario","Mis Listas","Mi cuenta"};
    private static final int[] NAV_ICONS={R.drawable.ic_nav_home,R.drawable.ic_nav_downloads,R.drawable.ic_nav_schedule,R.drawable.ic_nav_lists,R.drawable.ic_nav_account};

    private WebView web;
    private FrameLayout content,nativeContent,fullscreen;
    private ProgressBar progress;
    private LinearLayout root,navigation;
    private final ImageView[] navIcons=new ImageView[5];
    private final TextView[] navLabels=new TextView[5];
    private DownloadsView downloadsView;
    private DownloadStore store;
    private final ExecutorService background=Executors.newSingleThreadExecutor();
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean online;
    private boolean offlineLanding;
    private int selected=0;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private long lastLibrarySync;
    private boolean watchingListRequested;

    private final BroadcastReceiver updates=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){refreshDownloads();updatePageIntegration();}};

    @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);setContentView(R.layout.activity_main);getWindow().setStatusBarColor(AppUi.BG);getWindow().setNavigationBarColor(AppUi.BG);
        root=findViewById(R.id.root);content=findViewById(R.id.content);web=findViewById(R.id.web_view);nativeContent=findViewById(R.id.native_content);progress=findViewById(R.id.page_progress);fullscreen=findViewById(R.id.fullscreen_video);navigation=findViewById(R.id.bottom_navigation);
        configureNavigation(getResources().getConfiguration());applySystemBarInsets();hideSystemNavigation();
        store=new DownloadStore(this);store.recoverInterrupted();setupNavigation();setupWebView();registerUpdates();observeNetwork();
        String requested=getIntent().getStringExtra(EXTRA_URL);
        if(state!=null)web.restoreState(state);else if(isOnline())web.loadUrl(requested==null?URLS[0]:requested);else showOffline();
        if(!store.queued().isEmpty())DownloadService.wake(this);
    }

    private void setupNavigation(){
        for(int i=0;i<LABELS.length;i++){
            final int index=i;
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(0,AppUi.dp(this,4),0,AppUi.dp(this,3));item.setBackgroundColor(android.graphics.Color.TRANSPARENT);item.setContentDescription(LABELS[i]);item.setOnClickListener(v->select(index));
            ImageView icon=new ImageView(this);icon.setImageResource(NAV_ICONS[i]);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);item.addView(icon,new LinearLayout.LayoutParams(AppUi.dp(this,25),AppUi.dp(this,25)));
            TextView label=AppUi.text(this,LABELS[i],11,AppUi.MUTED);label.setGravity(Gravity.CENTER);label.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(-1,0,1);labelParams.topMargin=AppUi.dp(this,2);item.addView(label,labelParams);
            LinearLayout.LayoutParams itemParams=navigation.getOrientation()==LinearLayout.VERTICAL?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,1);
            navigation.addView(item,itemParams);navIcons[i]=icon;navLabels[i]=label;
        }
        markSelected(0);
    }

    private void applySystemBarInsets(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
            root.setOnApplyWindowInsetsListener((view,insets)->{
                if(customView!=null){view.setPadding(0,0,0,0);return insets;}
                Insets safe=insets.getInsets(WindowInsets.Type.statusBars()|WindowInsets.Type.displayCutout());
                view.setPadding(safe.left,safe.top,safe.right,0);
                return insets;
            });
            root.requestApplyInsets();
        }
    }

    private void configureNavigation(Configuration configuration){
        boolean landscape=configuration.orientation==Configuration.ORIENTATION_LANDSCAPE;
        root.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        navigation.setOrientation(landscape?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        content.setLayoutParams(landscape?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));
        navigation.setLayoutParams(landscape?new LinearLayout.LayoutParams(AppUi.dp(this,68),-1):new LinearLayout.LayoutParams(-1,AppUi.dp(this,68)));
        for(int i=0;i<navigation.getChildCount();i++)navigation.getChildAt(i).setLayoutParams(landscape?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,1));
        navigation.requestLayout();
    }

    /**
     * Keeps Android's navigation bar out of the way while preserving the
     * platform gesture that reveals it temporarily from the bottom edge.
     */
    @SuppressWarnings("deprecation")
    private void hideSystemNavigation(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.navigationBars());
            }
        }else{
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            |View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @SuppressWarnings("deprecation")
    private void hideAllSystemBars(){
        root.setPadding(0,0,0,0);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
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

    private void restoreSystemBars(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){controller.show(WindowInsets.Type.statusBars());controller.hide(WindowInsets.Type.navigationBars());}
        }else hideSystemNavigation();
        root.requestApplyInsets();
    }

    @SuppressLint("SetJavaScriptEnabled") private void setupWebView(){
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setUserAgentString(s.getUserAgentString()+" AnimeAV1Android/"+BuildConfig.VERSION_NAME);
        CookieManager cookies=CookieManager.getInstance();cookies.setAcceptCookie(true);cookies.setAcceptThirdPartyCookies(web,true);web.addJavascriptInterface(new Bridge(),"AnimeAV1Android");
        web.setDownloadListener((url,userAgent,contentDisposition,mimeType,length)->{Episode episode=parseEpisode(web.getUrl());if(episode!=null)enqueue(episode.slug,episode.number,web.getTitle(),web.getUrl(),url,"single","");});
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){progress.setProgress(value);progress.setVisibility(value<100?View.VISIBLE:View.GONE);}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){if(customView!=null){callback.onCustomViewHidden();return;}customView=view;customCallback=callback;web.setVisibility(View.GONE);navigation.setVisibility(View.GONE);fullscreen.setVisibility(View.VISIBLE);fullscreen.addView(view,new FrameLayout.LayoutParams(-1,-1));getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);hideAllSystemBars();}
            @Override public void onHideCustomView(){exitFullscreen();}
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean gesture,android.os.Message result){return false;}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){if(AdBlocker.shouldBlock(Uri.parse(url))){view.stopLoading();if(view.canGoBack())view.goBack();else view.loadUrl(URLS[0]);return;}progress.setVisibility(View.VISIBLE);showWeb();markForUrl(url);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return AdBlocker.shouldBlock(request.getUrl())?AdBlocker.emptyResponse():super.shouldInterceptRequest(view,request);}
            @Override public void onPageFinished(WebView view,String url){CookieManager.getInstance().flush();view.evaluateJavascript(AdBlocker.cleanupScript(),null);inject();if(watchingListRequested&&url!=null&&url.contains("/cuenta/listas"))openWatchingList();syncLibrary(false);}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()&&!isOnline())showOffline();}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){Uri uri=request.getUrl();if(AdBlocker.shouldBlock(uri))return true;if(isDiscord(uri)){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}return true;}String scheme=uri.getScheme();if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}return true;}
        });
    }

    private static boolean isDiscord(Uri uri){String host=uri==null?null:uri.getHost();if(host==null)return false;host=host.toLowerCase(java.util.Locale.US);return host.equals("discord.gg")||host.equals("discord.com")||host.endsWith(".discord.com")||host.equals("discordapp.com")||host.endsWith(".discordapp.com");}

    private void select(int index){
        if(index==1){showDownloads();return;}
        if(!isOnline()){showOffline();Toast.makeText(this,"Esta sección necesita conexión",Toast.LENGTH_SHORT).show();return;}
        if(index==3)watchingListRequested=true;
        selected=index;markSelected(index);showWeb();String target=URLS[index];if(!target.equals(web.getUrl()))web.loadUrl(target);else if(index==3)openWatchingList();
    }
    private void openWatchingList(){watchingListRequested=false;web.evaluateJavascript(SiteScripts.openWatchingList(),null);}
    private void showWeb(){offlineLanding=false;nativeContent.setVisibility(View.GONE);web.setVisibility(View.VISIBLE);navigation.setVisibility(View.VISIBLE);}
    private void showDownloads(){selected=1;markSelected(1);offlineLanding=false;web.setVisibility(View.GONE);nativeContent.setVisibility(View.VISIBLE);navigation.setVisibility(View.VISIBLE);nativeContent.removeAllViews();downloadsView=new DownloadsView(this,this);nativeContent.addView(downloadsView,new FrameLayout.LayoutParams(-1,-1));refreshDownloads();}
    private void showOffline(){selected=0;markSelected(0);offlineLanding=true;web.setVisibility(View.GONE);nativeContent.setVisibility(View.VISIBLE);navigation.setVisibility(View.VISIBLE);nativeContent.removeAllViews();nativeContent.addView(new OfflineLandingView(this,this::showDownloads,()->{if(isOnline()){offlineLanding=false;select(0);}else Toast.makeText(this,"Sigue sin haber conexión",Toast.LENGTH_SHORT).show();}),new FrameLayout.LayoutParams(-1,-1));}
    private void markSelected(int index){for(int i=0;i<navLabels.length;i++){int color=i==index?AppUi.BRAND:AppUi.MUTED;navLabels[i].setTextColor(color);navIcons[i].setColorFilter(color);}}
    private void markForUrl(String url){if(url==null)return;if(url.equals(URLS[0])){selected=0;markSelected(0);}else if(url.contains("/horario")){selected=2;markSelected(2);}else if(url.contains("/cuenta/listas")){selected=3;markSelected(3);}else if(url.matches("https://animeav1\\.com/cuenta/?(?:\\?.*)?")){selected=4;markSelected(4);}}

    private void inject(){Episode e=parseEpisode(web.getUrl());DownloadEntry local=e==null?null:store.get(e.slug,e.number);web.evaluateJavascript(SiteScripts.install(local!=null&&local.isPlayable()?DownloadEntry.COMPLETED:""),null);}
    private void updatePageIntegration(){if(web.getVisibility()==View.VISIBLE)inject();}

    private void enqueue(String slug,int episode,String title,String page,String source,String origin,String batchId){
        if(slug==null||slug.isEmpty()||episode<=0)return;DownloadEntry e=new DownloadEntry();e.slug=slug;e.episode=episode;e.seriesTitle=cleanTitle(title,slug);e.pageUrl=page==null||page.isEmpty()?AnimeAv1Client.episodeUrl(slug,episode):page;e.sourceUrl=source==null?"":source;e.origin=origin;e.batchId=batchId;e.state=DownloadEntry.QUEUED;
        if(!store.insertUnlessPresent(e)){Toast.makeText(this,"El episodio ya está descargado o en cola",Toast.LENGTH_SHORT).show();return;}requestNotifications();DownloadService.wake(this);refreshDownloads();
    }

    private void requestNotifications(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION);}
    private void refreshDownloads(){if(downloadsView!=null&&selected==1)downloadsView.render(store.all());}
    @Override public void play(DownloadEntry e){if(!e.isPlayable()){Toast.makeText(this,"El archivo ya no existe",Toast.LENGTH_SHORT).show();return;}startActivity(new Intent(this,OfflinePlayerActivity.class).putExtra(OfflinePlayerActivity.EXTRA_PATH,e.localPath).putExtra(OfflinePlayerActivity.EXTRA_TITLE,cleanTitle(e.seriesTitle,e.slug)+" · Episodio "+e.episode));}
    @Override public void cancel(DownloadEntry e){if(DownloadEntry.QUEUED.equals(e.state)){e.state=DownloadEntry.CANCELLED;store.save(e);refreshDownloads();}else DownloadService.cancel(this,e);}
    @Override public void delete(DownloadEntry e){new AlertDialog.Builder(this).setTitle("Borrar episodio").setMessage("Se eliminarán el archivo y su registro local.").setNegativeButton("Cancelar",null).setPositiveButton("Borrar",(d,w)->{store.remove(e.slug,e.episode,true);refreshDownloads();updatePageIntegration();}).show();}
    @Override public void retry(DownloadEntry e){e.state=DownloadEntry.QUEUED;e.error="";e.bytes=0;e.totalBytes=0;store.save(e);DownloadService.wake(this);refreshDownloads();}
    @Override public void batch(){if(!isOnline()){Toast.makeText(this,"Necesitas conexión para consultar Mis Listas",Toast.LENGTH_SHORT).show();return;}loadBatch();}

    private void loadBatch(){Toast.makeText(this,"Consultando episodios pendientes…",Toast.LENGTH_SHORT).show();String cookie=CookieManager.getInstance().getCookie(AnimeAv1Client.ORIGIN);background.execute(()->{
        try{List<AnimeAv1Client.LibraryItem> library=AnimeAv1Client.library(cookie);ArrayList<Pending> pending=new ArrayList<>();int series=0;
            for(AnimeAv1Client.LibraryItem item:library){if(item.status!=0)continue;int published=AnimeAv1Client.publishedEpisodes(item.slug,cookie);boolean added=false;for(int ep=item.watched+1;ep<=published;ep++){DownloadEntry old=store.get(item.slug,ep);if(old==null||(!old.isActive()&&!old.isPlayable())){pending.add(new Pending(item.slug,item.title,ep));added=true;}}if(added)series++;}
            final int countSeries=series;runOnUiThread(()->confirmBatch(pending,countSeries));
        }catch(Exception e){runOnUiThread(()->Toast.makeText(this,value(e.getMessage()),Toast.LENGTH_LONG).show());}
    });}
    private void confirmBatch(List<Pending> pending,int series){if(pending.isEmpty()){Toast.makeText(this,"No hay episodios pendientes nuevos",Toast.LENGTH_LONG).show();return;}new AlertDialog.Builder(this).setTitle("Descargar pendientes").setMessage("Se añadirán "+pending.size()+" episodios de "+series+" series. Se descargarán uno a uno.").setNegativeButton("Cancelar",null).setPositiveButton("Añadir",(d,w)->{String batch=UUID.randomUUID().toString();for(Pending p:pending)enqueue(p.slug,p.episode,p.title,AnimeAv1Client.episodeUrl(p.slug,p.episode),"","bulk",batch);showDownloads();}).show();}

    private void downloadUnwatched(String slug,String title,int published){if(!isOnline()){Toast.makeText(this,"Necesitas conexión",Toast.LENGTH_SHORT).show();return;}String cookie=CookieManager.getInstance().getCookie(AnimeAv1Client.ORIGIN);background.execute(()->{try{int last=0;String resolvedTitle=title;for(AnimeAv1Client.LibraryItem i:AnimeAv1Client.library(cookie))if(i.slug.equals(slug)){last=i.watched;if(resolvedTitle==null||resolvedTitle.isEmpty())resolvedTitle=i.title;break;}int real=published>0?published:AnimeAv1Client.publishedEpisodes(slug,cookie);ArrayList<Pending> result=new ArrayList<>();for(int ep=last+1;ep<=real;ep++){DownloadEntry old=store.get(slug,ep);if(old==null||(!old.isActive()&&!old.isPlayable()))result.add(new Pending(slug,resolvedTitle,ep));}runOnUiThread(()->confirmBatch(result,result.isEmpty()?0:1));}catch(Exception error){runOnUiThread(()->Toast.makeText(this,value(error.getMessage()),Toast.LENGTH_LONG).show());}});}

    private void syncLibrary(boolean force){long now=System.currentTimeMillis();if(!isOnline()||(!force&&now-lastLibrarySync<5*60_000))return;lastLibrarySync=now;String cookie=CookieManager.getInstance().getCookie(AnimeAv1Client.ORIGIN);background.execute(()->{try{int removed=0;for(AnimeAv1Client.LibraryItem item:AnimeAv1Client.library(cookie))removed+=store.deleteWatched(item.slug,item.watched);if(removed>0)runOnUiThread(()->{refreshDownloads();updatePageIntegration();});}catch(Exception ignored){}});}

    @Override public void swipe(int direction){select((selected+direction+5)%5);}

    private void observeNetwork(){connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);online=isOnline();networkCallback=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network network){refreshConnectivity();}@Override public void onCapabilitiesChanged(Network network,NetworkCapabilities capabilities){refreshConnectivity();}@Override public void onLost(Network network){refreshConnectivity();}};try{connectivity.registerDefaultNetworkCallback(networkCallback);}catch(Exception ignored){}}
    private void refreshConnectivity(){runOnUiThread(()->{boolean was=online;online=isOnline();if(!was&&online&&offlineLanding)select(0);});}
    private boolean isOnline(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);Network n=cm.getActiveNetwork();NetworkCapabilities c=n==null?null:cm.getNetworkCapabilities(n);return c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}
    private void registerUpdates(){IntentFilter f=new IntentFilter(DownloadService.ACTION_UPDATED);if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(updates,f);}

    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String url=intent.getStringExtra(EXTRA_URL);if(url!=null){if(isOnline()){showWeb();web.loadUrl(url);}else showDownloads();}}
    @Override public void onConfigurationChanged(Configuration configuration){super.onConfigurationChanged(configuration);configureNavigation(configuration);if(customView!=null)hideAllSystemBars();else{root.requestApplyInsets();hideSystemNavigation();}}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus){if(customView!=null)hideAllSystemBars();else hideSystemNavigation();}}
    @Override protected void onSaveInstanceState(Bundle out){web.saveState(out);super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){if(customView!=null){exitFullscreen();return;}if(selected==1){if(isOnline())select(0);else showOffline();return;}if(offlineLanding){super.onBackPressed();return;}if(web.canGoBack())web.goBack();else super.onBackPressed();}
    private void exitFullscreen(){if(customView==null)return;fullscreen.removeView(customView);fullscreen.setVisibility(View.GONE);customView=null;web.setVisibility(View.VISIBLE);navigation.setVisibility(View.VISIBLE);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);restoreSystemBars();if(customCallback!=null)customCallback.onCustomViewHidden();customCallback=null;}
    @Override protected void onDestroy(){try{unregisterReceiver(updates);}catch(Exception ignored){}try{connectivity.unregisterNetworkCallback(networkCallback);}catch(Exception ignored){}background.shutdownNow();store.close();web.destroy();super.onDestroy();}

    private static Episode parseEpisode(String url){if(url==null)return null;try{List<String> p=Uri.parse(url).getPathSegments();if(p.size()==3&&p.get(0).equals("media"))return new Episode(p.get(1),Integer.parseInt(p.get(2)));}catch(Exception ignored){}return null;}
    private static String cleanTitle(String title,String fallback){String value=title==null?"":title.trim();value=value.replaceAll("(?i)\\s*[-|·]\\s*AnimeAV1.*$","");return value.isEmpty()?fallback:value;}
    private static String value(String v){return v==null||v.trim().isEmpty()?"No se pudo completar la operación":v;}
    private static final class Episode{final String slug;final int number;Episode(String s,int n){slug=s;number=n;}}
    private static final class Pending{final String slug,title;final int episode;Pending(String s,String t,int e){slug=s;title=t;episode=e;}}

    private final class Bridge {
        @JavascriptInterface public void download(String slug,int episode,String title,String page,String source){runOnUiThread(()->enqueue(slug,episode,title,page,source,"single",""));}
        @JavascriptInterface public void downloadUnwatched(String slug,String title,int published){runOnUiThread(()->MainActivity.this.downloadUnwatched(slug,title,published));}
        @JavascriptInterface public void playLocal(String slug,int episode){runOnUiThread(()->{DownloadEntry e=store.get(slug,episode);if(e!=null)play(e);});}
        @JavascriptInterface public void swipe(int direction){runOnUiThread(()->MainActivity.this.swipe(direction));}
    }
}
