package com.ovelayos.animeav1;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DownloadService extends Service {
    static final String ACTION_WAKE = "com.ovelayos.animeav1.action.WAKE_DOWNLOADS";
    static final String ACTION_CANCEL = "com.ovelayos.animeav1.action.CANCEL_DOWNLOAD";
    static final String ACTION_UPDATED = "com.ovelayos.animeav1.action.DOWNLOAD_UPDATED";
    static final String EXTRA_SLUG = "slug";
    static final String EXTRA_EPISODE = "episode";
    private static final String CHANNEL = "animeav1_downloads_v2";
    private static final int ACTIVE_NOTIFICATION = 1200;
    private static final int COMPLETE_NOTIFICATION = 2200;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean cancelCurrent = new AtomicBoolean();
    private volatile String activeKey = "";
    private volatile HttpURLConnection connection;

    static void wake(Context context) {
        Intent intent = new Intent(context, DownloadService.class).setAction(ACTION_WAKE);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent); else context.startService(intent);
    }

    static void cancel(Context context, DownloadEntry entry) {
        Intent intent = new Intent(context, DownloadService.class).setAction(ACTION_CANCEL)
                .putExtra(EXTRA_SLUG, entry.slug).putExtra(EXTRA_EPISODE, entry.episode);
        context.startService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Descargas AnimeAV1", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Progreso de la cola de episodios");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            String slug = value(intent.getStringExtra(EXTRA_SLUG)); int episode = intent.getIntExtra(EXTRA_EPISODE, 0);
            DownloadStore store = new DownloadStore(this); DownloadEntry entry = store.get(slug, episode);
            if (entry != null) { if (entry.key().equals(activeKey)) { cancelCurrent.set(true); disconnect(); } else if (DownloadEntry.QUEUED.equals(entry.state)) { entry.state=DownloadEntry.CANCELLED; store.save(entry); broadcast(entry); } }
            store.close(); return START_STICKY;
        }
        startForeground(ACTIVE_NOTIFICATION, notification("Preparando la cola…", 0, 0, null, true));
        if (running.compareAndSet(false, true)) executor.execute(this::drain);
        return START_STICKY;
    }

    private void drain() {
        int bulkOk=0, bulkErrors=0; String lastBulkSlug="";
        try {
            while (true) {
                DownloadStore store = new DownloadStore(this); List<DownloadEntry> queued = store.queued(); store.close();
                if (queued.isEmpty()) break;
                DownloadEntry entry = queued.get(0); activeKey=entry.key(); cancelCurrent.set(false);
                boolean ok = perform(entry);
                if ("bulk".equals(entry.origin)) { if(ok)bulkOk++;else bulkErrors++; lastBulkSlug=entry.slug; }
                else if (ok) completedNotification(entry);
                activeKey="";
            }
            if (bulkOk + bulkErrors > 0) bulkNotification(bulkOk, bulkErrors, lastBulkSlug);
        } finally {
            running.set(false); activeKey="";
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
            stopSelf();
        }
    }

    private boolean perform(DownloadEntry entry) {
        DownloadStore store = new DownloadStore(this);
        try {
            entry.state=DownloadEntry.RESOLVING; entry.error=""; store.save(entry); broadcast(entry); update(entry, "Resolviendo enlace…");
            String html = AnimeAv1Client.get(entry.pageUrl, android.webkit.CookieManager.getInstance().getCookie(AnimeAv1Client.ORIGIN));
            MegaClient.Link link = MegaClient.parse(entry.sourceUrl);
            if (link == null) link = MegaClient.fromHtml(html);
            if (link == null) throw new IllegalStateException("No se encontró un enlace público de Mega");
            entry.sourceUrl=link.original; entry.state=DownloadEntry.DOWNLOADING; entry.localPath=fileFor(entry).getAbsolutePath(); store.save(entry); broadcast(entry);
            final DownloadEntry current=entry;
            MegaClient.download(link, new File(entry.localPath), new MegaClient.Control() {
                private long lastSave;
                @Override public boolean cancelled(){return cancelCurrent.get();}
                @Override public void connection(HttpURLConnection c){connection=c;}
                @Override public void progress(long bytes,long total){
                    current.bytes=bytes; current.totalBytes=total; long now=System.currentTimeMillis();
                    if(now-lastSave>600||bytes==total){lastSave=now;store.save(current);broadcast(current);update(current,label(current));}
                }
            });
            entry.state=DownloadEntry.COMPLETED; entry.error=""; store.save(entry); broadcast(entry); return true;
        } catch (MegaClient.Cancelled cancelled) {
            cleanupPart(entry); entry.state=DownloadEntry.CANCELLED; entry.error="Cancelada"; store.save(entry); broadcast(entry); return false;
        } catch (Exception failure) {
            if (cancelCurrent.get()) { cleanupPart(entry); entry.state=DownloadEntry.CANCELLED; entry.error="Cancelada"; }
            else { cleanupPart(entry); entry.state=DownloadEntry.ERROR; entry.error=value(failure.getMessage()); }
            store.save(entry); broadcast(entry); return false;
        } finally { disconnect(); store.close(); }
    }

    private File fileFor(DownloadEntry e) {
        File root=getExternalFilesDir(Environment.DIRECTORY_MOVIES); if(root==null)root=new File(getFilesDir(),"movies");
        String safe=e.slug.replaceAll("[^A-Za-z0-9._-]+","_");
        return new File(new File(new File(root,"AnimeAV1"),safe),String.format(Locale.US,"%03d.mp4",e.episode));
    }
    private void cleanupPart(DownloadEntry e){if(!e.localPath.isEmpty())new File(e.localPath+".part").delete();}
    private void disconnect(){HttpURLConnection c=connection;connection=null;if(c!=null)c.disconnect();}

    private void update(DownloadEntry e,String text){
        int queued=0; DownloadStore store=new DownloadStore(this); for(DownloadEntry x:store.all())if(DownloadEntry.QUEUED.equals(x.state))queued++; store.close();
        if(queued>0)text += " · " + queued + " en cola";
        getSystemService(NotificationManager.class).notify(ACTIVE_NOTIFICATION,notification(text,e.bytes,e.totalBytes,e,false));
    }
    private Notification notification(String text,long done,long total,DownloadEntry e,boolean indeterminate){
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification).setContentTitle("AnimeAV1").setContentText(text).setOnlyAlertOnce(true).setOngoing(true).setCategory(Notification.CATEGORY_PROGRESS);
        if(total>0)b.setProgress(100,(int)Math.min(100,done*100/total),false);else b.setProgress(0,0,indeterminate);
        if(e!=null){Intent cancel=new Intent(this,DownloadService.class).setAction(ACTION_CANCEL).putExtra(EXTRA_SLUG,e.slug).putExtra(EXTRA_EPISODE,e.episode);
            PendingIntent action=PendingIntent.getService(this,code(e,7),cancel,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);b.addAction(android.R.drawable.ic_menu_close_clear_cancel,"Cancelar",action);}
        return b.build();
    }
    private void completedNotification(DownloadEntry e){
        PendingIntent open=openIntent(e,false); Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification).setContentTitle("Descarga completada").setContentText(title(e)).setAutoCancel(true).setContentIntent(open).addAction(android.R.drawable.ic_media_play,"Ver",open);
        getSystemService(NotificationManager.class).notify(COMPLETE_NOTIFICATION+code(e,0),b.build());
    }
    private void bulkNotification(int ok,int errors,String series){
        Intent intent=new Intent(this,MainActivity.class).putExtra(MainActivity.EXTRA_URL,AnimeAv1Client.ORIGIN+"/media/"+series).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(this,9101,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String text=ok+" completados"+(errors>0?" · "+errors+" con error/cancelados":"");
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification).setContentTitle("Lote de descargas finalizado").setContentText(text).setAutoCancel(true).setContentIntent(open);
        getSystemService(NotificationManager.class).notify(COMPLETE_NOTIFICATION-1,b.build());
    }
    private PendingIntent openIntent(DownloadEntry e,boolean local){
        Intent i=new Intent(this,MainActivity.class).putExtra(MainActivity.EXTRA_URL,e.pageUrl).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this,code(e,11),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private int code(DownloadEntry e,int salt){return Math.abs((e.key()+salt).hashCode()%50000)+1000;}
    private String title(DownloadEntry e){return (e.seriesTitle.isEmpty()?e.slug:e.seriesTitle)+" · Episodio "+e.episode;}
    private String label(DownloadEntry e){return title(e)+(e.totalBytes>0?" · "+(e.bytes*100/e.totalBytes)+"%":" · descargando");}
    private void broadcast(DownloadEntry e){Intent i=new Intent(ACTION_UPDATED).setPackage(getPackageName()).putExtra(EXTRA_SLUG,e.slug).putExtra(EXTRA_EPISODE,e.episode);sendBroadcast(i);}
    private static String value(String s){return s==null?"":s;}
    @Override public void onDestroy(){disconnect();executor.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
