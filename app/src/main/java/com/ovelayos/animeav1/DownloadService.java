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
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DownloadService extends Service {
    public static final String ACTION_DOWNLOAD_UPDATED = "com.ovelayos.animeav1.DOWNLOAD_UPDATED";
    public static final String ACTION_CANCEL = "com.ovelayos.animeav1.CANCEL_DOWNLOAD";
    public static final String ACTION_CANCEL_ALL = "com.ovelayos.animeav1.CANCEL_ALL_DOWNLOADS";
    public static final String EXTRA_SLUG = "slug";
    public static final String EXTRA_EPISODE = "episode";
    public static final String EXTRA_PAGE_URL = "page_url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_COOKIE = "cookie";
    public static final String EXTRA_SOURCE_URL = "source_url";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_BYTES = "bytes";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_BULK = "bulk";

    private static final String CHANNEL_ID = "animeav1_downloads";
    private static final int FOREGROUND_ID = 1101;
    private static final int FINAL_BASE_ID = 2100;
    private static final Pattern MEGA_PROVIDER_RE = Pattern.compile("[\\\"']?server[\\\"']?\\s*:\\s*[\\\"']Mega[\\\"']\\s*,\\s*[\\\"']?url[\\\"']?\\s*:\\s*[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEGA_ANY_RE = Pattern.compile("https?://(?:www\\.)?mega\\.(?:nz|co\\.nz)/[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private final Object queueLock = new Object();
    private final ArrayDeque<Intent> queue = new ArrayDeque<>();
    private final Set<String> queuedKeys = new HashSet<>();
    private final Set<String> cancelledKeys = new HashSet<>();
    private volatile String currentKey = "";
    private volatile HttpURLConnection currentConnection;
    private volatile String currentPage = "";
    private volatile String currentSlug = "";
    private volatile int currentEpisode;
    private volatile boolean currentBulk;
    private int latestStartId;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Descargas AnimeAV1", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Progreso de episodios descargados para verlos offline");
            nm.createNotificationChannel(c);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        latestStartId = Math.max(latestStartId, startId);
        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            cancelOne(value(intent.getStringExtra(EXTRA_SLUG)), intent.getIntExtra(EXTRA_EPISODE, 0));
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL_ALL.equals(action)) {
            cancelAll();
            return START_NOT_STICKY;
        }

        String key = taskKey(intent);
        if (key.isEmpty()) {
            broadcast(value(intent.getStringExtra(EXTRA_SLUG)), intent.getIntExtra(EXTRA_EPISODE, 0), EpisodeStore.STATUS_ERROR, 0, 0, "Episodio no válido");
            return START_NOT_STICKY;
        }
        if (alreadyAvailableOrQueued(intent)) return START_NOT_STICKY;

        String slug = value(intent.getStringExtra(EXTRA_SLUG));
        int ep = intent.getIntExtra(EXTRA_EPISODE, 0);
        String page = value(intent.getStringExtra(EXTRA_PAGE_URL));
        boolean bulk = intent.getBooleanExtra(EXTRA_BULK, false);
        startForeground(FOREGROUND_ID, buildProgressNotification("Preparando cola de descargas…", 0, 0, slug, ep, page, bulk));

        synchronized (queueLock) {
            if (queuedKeys.contains(key)) return START_NOT_STICKY;
            queue.addLast(new Intent(intent));
            queuedKeys.add(key);
        }
        markPending(intent);
        if (workerRunning.compareAndSet(false, true)) executor.execute(this::drainQueue);
        return START_NOT_STICKY;
    }

    private void cancelOne(String slug, int ep) {
        String key = slug + "#" + ep;
        boolean current = key.equals(currentKey);
        synchronized (queueLock) {
            cancelledKeys.add(key);
            for (Iterator<Intent> it = queue.iterator(); it.hasNext();) {
                Intent x = it.next();
                if (key.equals(taskKey(x))) {
                    it.remove();
                    queuedKeys.remove(key);
                    markCancelled(slug, ep);
                    break;
                }
            }
        }
        if (current) {
            HttpURLConnection c = currentConnection;
            if (c != null) c.disconnect();
        }
    }

    private void cancelAll() {
        synchronized (queueLock) {
            for (Intent x : queue) {
                String k = taskKey(x);
                cancelledKeys.add(k);
                markCancelled(value(x.getStringExtra(EXTRA_SLUG)), x.getIntExtra(EXTRA_EPISODE, 0));
            }
            queue.clear();
            queuedKeys.clear();
            if (!currentKey.isEmpty()) cancelledKeys.add(currentKey);
        }
        HttpURLConnection c = currentConnection;
        if (c != null) c.disconnect();
    }

    private void markCancelled(String slug, int ep) {
        EpisodeStore s = new EpisodeStore(this);
        try {
            EpisodeStore.DownloadRecord r = s.get(slug, ep);
            if (r != null) {
                r.status = EpisodeStore.STATUS_CANCELLED;
                r.error = "Cancelada";
                s.save(r);
                broadcastRecord(r);
            }
        } finally {
            s.close();
        }
    }

    private boolean isCancelled(String key) {
        synchronized (queueLock) {
            return cancelledKeys.contains(key);
        }
    }

    private boolean alreadyAvailableOrQueued(Intent i) {
        String slug = value(i.getStringExtra(EXTRA_SLUG));
        int ep = i.getIntExtra(EXTRA_EPISODE, 0);
        EpisodeStore s = new EpisodeStore(this);
        try {
            EpisodeStore.DownloadRecord r = s.get(slug, ep);
            if (r == null) return false;
            if (EpisodeStore.STATUS_COMPLETED.equals(r.status) && !r.path.isEmpty() && new File(r.path).isFile()) return true;
            return EpisodeStore.STATUS_PENDING.equals(r.status)
                    || EpisodeStore.STATUS_RESOLVING.equals(r.status)
                    || EpisodeStore.STATUS_DOWNLOADING.equals(r.status);
        } finally {
            s.close();
        }
    }

    private void markPending(Intent i) {
        EpisodeStore s = new EpisodeStore(this);
        try {
            EpisodeStore.DownloadRecord r = new EpisodeStore.DownloadRecord();
            r.slug = value(i.getStringExtra(EXTRA_SLUG));
            r.episode = i.getIntExtra(EXTRA_EPISODE, 0);
            r.pageUrl = value(i.getStringExtra(EXTRA_PAGE_URL));
            r.title = value(i.getStringExtra(EXTRA_TITLE));
            r.provider = "Mega";
            r.sourceUrl = value(i.getStringExtra(EXTRA_SOURCE_URL));
            r.status = EpisodeStore.STATUS_PENDING;
            s.save(r);
            broadcastRecord(r);
        } finally {
            s.close();
        }
    }

    private void drainQueue() {
        for (;;) {
            Intent task;
            synchronized (queueLock) {
                task = queue.pollFirst();
                if (task == null) {
                    workerRunning.set(false);
                    break;
                }
            }
            String key = taskKey(task);
            currentKey = key;
            currentSlug = value(task.getStringExtra(EXTRA_SLUG));
            currentEpisode = task.getIntExtra(EXTRA_EPISODE, 0);
            currentPage = value(task.getStringExtra(EXTRA_PAGE_URL));
            currentBulk = task.getBooleanExtra(EXTRA_BULK, false);
            if (isCancelled(key)) {
                markCancelled(currentSlug, currentEpisode);
            } else {
                Result r = performDownload(task, key);
                if (!r.cancelled) postFinalNotification(r, task);
            }
            synchronized (queueLock) {
                queuedKeys.remove(key);
                cancelledKeys.remove(key);
            }
            currentKey = "";
            currentSlug = "";
            currentEpisode = 0;
            currentPage = "";
            currentBulk = false;
        }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelfResult(latestStartId);
    }

    private Result performDownload(Intent i, String key) {
        String slug = value(i.getStringExtra(EXTRA_SLUG));
        int ep = i.getIntExtra(EXTRA_EPISODE, 0);
        String page = value(i.getStringExtra(EXTRA_PAGE_URL));
        String title = value(i.getStringExtra(EXTRA_TITLE));
        String cookie = value(i.getStringExtra(EXTRA_COOKIE));
        String hint = value(i.getStringExtra(EXTRA_SOURCE_URL));
        EpisodeStore s = new EpisodeStore(this);
        EpisodeStore.DownloadRecord r = new EpisodeStore.DownloadRecord();
        r.slug = slug;
        r.episode = ep;
        r.pageUrl = page;
        r.title = title;
        r.provider = "Mega";
        r.status = EpisodeStore.STATUS_RESOLVING;
        s.save(r);
        broadcastRecord(r);
        try {
            if (isCancelled(key)) throw new Cancelled();
            MegaLink mega = parseMegaLink(hint);
            if (mega == null) mega = findMegaLink(fetchEpisodeHtml(page, cookie));
            if (mega == null) throw new IllegalStateException("No se ha podido resolver el enlace de descarga de Mega");
            if (isCancelled(key)) throw new Cancelled();
            r.sourceUrl = mega.original;
            r.status = EpisodeStore.STATUS_DOWNLOADING;
            s.save(r);
            broadcastRecord(r);
            File f = episodeFile(slug, ep);
            downloadMega(mega, f, r, s, key);
            if (isCancelled(key)) throw new Cancelled();
            r.path = f.getAbsolutePath();
            r.bytes = f.length();
            if (r.totalBytes <= 0) r.totalBytes = r.bytes;
            r.status = EpisodeStore.STATUS_COMPLETED;
            r.error = "";
            s.save(r);
            broadcastRecord(r);
            return Result.success(titleFor(r), r.bytes);
        } catch (Cancelled e) {
            File p = new File(episodeFile(slug, ep).getAbsolutePath() + ".part");
            if (p.exists()) p.delete();
            r.status = EpisodeStore.STATUS_CANCELLED;
            r.error = "Cancelada";
            s.save(r);
            broadcastRecord(r);
            return Result.cancelled();
        } catch (Exception e) {
            if (isCancelled(key)) {
                r.status = EpisodeStore.STATUS_CANCELLED;
                r.error = "Cancelada";
                s.save(r);
                broadcastRecord(r);
                return Result.cancelled();
            }
            r.status = EpisodeStore.STATUS_ERROR;
            r.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            s.save(r);
            broadcastRecord(r);
            return Result.error(titleFor(r), r.error);
        } finally {
            s.close();
            currentConnection = null;
        }
    }

    private String fetchEpisodeHtml(String page, String cookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(page).openConnection();
        currentConnection = c;
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/142 Mobile Safari/537.36");
        c.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
        if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        int code = c.getResponseCode();
        if (code < 200 || code >= 400) throw new IllegalStateException("AnimeAV1 respondió HTTP " + code);
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            return readUtf8(in, 8 * 1024 * 1024);
        } finally {
            c.disconnect();
            currentConnection = null;
        }
    }

    private MegaLink findMegaLink(String html) {
        String d = decodeJsString(html);
        Matcher p = MEGA_PROVIDER_RE.matcher(d);
        while (p.find()) {
            MegaLink l = parseMegaLink(p.group(1));
            if (l != null) return l;
        }
        Matcher a = MEGA_ANY_RE.matcher(d);
        while (a.find()) {
            MegaLink l = parseMegaLink(a.group());
            if (l != null) return l;
        }
        return null;
    }

    private MegaLink parseMegaLink(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String s = stripQuotes(decodeJsString(raw).trim());
        for (int i = 0; i < 2 && looksPercentEncoded(s); i++) {
            try { s = URLDecoder.decode(s, StandardCharsets.UTF_8.name()); }
            catch (Exception e) { break; }
        }
        Matcher m = MEGA_ANY_RE.matcher(s);
        if (m.find()) s = m.group();
        s = trimTrailingPunctuation(s);
        try {
            URI u = new URI(s);
            String h = value(u.getHost()).toLowerCase(Locale.US);
            if (!(h.equals("mega.nz") || h.endsWith(".mega.nz") || h.equals("mega.co.nz") || h.endsWith(".mega.co.nz"))) return null;
            String f = value(u.getRawFragment());
            try { f = URLDecoder.decode(f, StandardCharsets.UTF_8.name()); } catch (Exception ignored) {}
            String handle = "", key = "", path = value(u.getPath()).replaceAll("^/+|/+$", "");
            String[] b = path.isEmpty() ? new String[0] : path.split("/");
            if (b.length >= 2 && ("file".equalsIgnoreCase(b[0]) || "embed".equalsIgnoreCase(b[0]))) {
                handle = b[1];
                key = f;
            } else if (f.startsWith("!")) {
                String[] o = f.substring(1).split("!");
                if (o.length >= 2) { handle = o[0]; key = o[1]; }
            }
            if (key.contains("/")) key = key.substring(0, key.indexOf('/'));
            if (key.contains("?")) key = key.substring(0, key.indexOf('?'));
            byte[] test = Base64.decode(key.trim(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            if (handle.isEmpty() || test.length != 32) return null;
            return new MegaLink(s, handle, key.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean looksPercentEncoded(String s) {
        String x = s.toLowerCase(Locale.US);
        return x.contains("%2f") || x.contains("%3a") || x.contains("%23") || x.contains("%21");
    }

    private String stripQuotes(String s) {
        while (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) s = s.substring(1, s.length() - 1).trim();
        return s;
    }

    private String trimTrailingPunctuation(String s) {
        while (!s.isEmpty() && ")]}.,;".indexOf(s.charAt(s.length() - 1)) >= 0) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String decodeJsString(String r) {
        return value(r).replace("\\/", "/").replace("\\u002F", "/").replace("\\u002f", "/")
                .replace("\\u003A", ":").replace("\\u003a", ":").replace("\\u0023", "#")
                .replace("\\u0021", "!").replace("\\u0026", "&").replace("&amp;", "&")
                .replace("&#35;", "#").replace("&#x23;", "#");
    }

    private void downloadMega(MegaLink mega, File finalFile, EpisodeStore.DownloadRecord r, EpisodeStore s, String key) throws Exception {
        byte[] rawKey = Base64.decode(mega.key, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        if (rawKey.length != 32) throw new IllegalStateException("Clave Mega inválida");
        byte[] aes = new byte[16], iv = new byte[16];
        for (int x = 0; x < 16; x++) aes[x] = (byte) (rawKey[x] ^ rawKey[x + 16]);
        System.arraycopy(rawKey, 16, iv, 0, 8);
        JSONObject meta = requestMegaDownload(mega.handle);
        String dl = meta.optString("g", "");
        long expected = meta.optLong("s", 0);
        if (dl.isEmpty()) throw new IllegalStateException("Mega no devolvió una URL de descarga");
        File dir = finalFile.getParentFile();
        if (dir == null || (!dir.exists() && !dir.mkdirs())) throw new IllegalStateException("No se puede crear la carpeta de descarga");
        File part = new File(finalFile.getAbsolutePath() + ".part");
        if (part.exists()) part.delete();
        HttpURLConnection c = (HttpURLConnection) new URL(dl).openConnection();
        currentConnection = c;
        c.setConnectTimeout(25000);
        c.setReadTimeout(45000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AnimeAV1/1.4.3");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("Mega respondió HTTP " + code);
        if (expected <= 0) expected = c.getContentLengthLong();
        r.totalBytes = Math.max(0, expected);
        r.bytes = 0;
        s.save(r);
        broadcastRecord(r);
        updateForeground(r);
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aes, "AES"), new IvParameterSpec(iv));
        long done = 0, last = 0;
        byte[] buf = new byte[128 * 1024];
        try (InputStream raw = new BufferedInputStream(c.getInputStream());
             CipherInputStream dec = new CipherInputStream(raw, cipher);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(part))) {
            int n;
            while ((n = dec.read(buf)) != -1) {
                if (isCancelled(key)) throw new Cancelled();
                out.write(buf, 0, n);
                done += n;
                long now = System.currentTimeMillis();
                if (now - last >= 750) {
                    last = now;
                    r.bytes = done;
                    s.save(r);
                    broadcastRecord(r);
                    updateForeground(r);
                }
            }
        } finally {
            c.disconnect();
            currentConnection = null;
        }
        if (expected > 0 && done != expected) throw new IllegalStateException(String.format(Locale.US, "Descarga Mega incompleta: %d/%d bytes", done, expected));
        if (finalFile.exists() && !finalFile.delete()) throw new IllegalStateException("No se puede reemplazar el episodio existente");
        if (!part.renameTo(finalFile)) throw new IllegalStateException("No se puede finalizar el archivo descargado");
        r.bytes = done;
        r.totalBytes = expected > 0 ? expected : done;
        updateForeground(r);
    }

    private JSONObject requestMegaDownload(String handle) throws Exception {
        URL api = new URL("https://g.api.mega.co.nz/cs?id=" + System.nanoTime());
        HttpURLConnection c = (HttpURLConnection) api.openConnection();
        currentConnection = c;
        c.setConnectTimeout(20000);
        c.setReadTimeout(25000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        byte[] body = ("[{\"a\":\"g\",\"g\":1,\"p\":\"" + handle + "\"}]").getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(body.length);
        try (BufferedOutputStream out = new BufferedOutputStream(c.getOutputStream())) { out.write(body); }
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("Mega API respondió HTTP " + code);
        String json;
        try (InputStream in = c.getInputStream()) { json = readUtf8(in, 2 * 1024 * 1024); }
        finally { c.disconnect(); currentConnection = null; }
        JSONArray a = new JSONArray(json);
        if (a.length() == 0 || !(a.get(0) instanceof JSONObject)) throw new IllegalStateException("Respuesta Mega inválida");
        JSONObject o = a.getJSONObject(0);
        if (o.has("e")) throw new IllegalStateException("Mega API error " + o.optInt("e"));
        return o;
    }

    private File episodeFile(String slug, int ep) {
        File base = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (base == null) base = new File(getFilesDir(), "movies");
        return new File(new File(new File(base, "AnimeAV1"), safe(slug)), String.format(Locale.US, "%03d.mp4", ep));
    }

    private String safe(String v) {
        String s = v.replaceAll("[^A-Za-z0-9._-]+", "_");
        return s.isEmpty() ? "anime" : s;
    }

    private void updateForeground(EpisodeStore.DownloadRecord r) {
        String text;
        if (r.totalBytes > 0) {
            int pct = (int) Math.min(100, r.bytes * 100L / r.totalBytes);
            text = titleFor(r) + " · " + pct + "%";
        } else text = titleFor(r) + " · descargando";
        int waiting;
        synchronized (queueLock) { waiting = queue.size(); }
        if (waiting > 0) text += " · " + waiting + " en cola";
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(
                FOREGROUND_ID,
                buildProgressNotification(text, r.bytes, r.totalBytes, currentSlug, currentEpisode, currentPage, currentBulk));
    }

    private Notification buildProgressNotification(String text, long done, long total, String slug, int ep, String page, boolean bulk) {
        PendingIntent pi = viewPendingIntent(slug, ep, page, bulk, 1000);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("AnimeAV1")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS);
        if (total > 0) b.setProgress(100, (int) Math.min(100, done * 100L / total), false);
        else b.setProgress(0, 0, true);
        if (!slug.isEmpty() && ep > 0) {
            Intent c = new Intent(this, DownloadService.class).setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_SLUG, slug).putExtra(EXTRA_EPISODE, ep);
            PendingIntent cp = PendingIntent.getService(this, pendingCode(slug, ep, 31), c, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cp);
        }
        return b.build();
    }

    private void postFinalNotification(Result r, Intent task) {
        String slug = value(task.getStringExtra(EXTRA_SLUG));
        int ep = task.getIntExtra(EXTRA_EPISODE, 0);
        String page = value(task.getStringExtra(EXTRA_PAGE_URL));
        boolean bulk = task.getBooleanExtra(EXTRA_BULK, false);
        PendingIntent pi = viewPendingIntent(slug, ep, page, bulk, 2000);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(r.ok ? "Descarga completada" : "Error de descarga")
                .setContentText(r.message)
                .setContentIntent(pi)
                .setAutoCancel(true);
        if (r.ok) b.addAction(android.R.drawable.ic_media_play, "Ver", pi);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(finalNotificationId(slug, ep), b.build());
    }

    private PendingIntent viewPendingIntent(String slug, int ep, String page, boolean bulk, int salt) {
        String target = targetUrl(slug, ep, page, bulk);
        Intent open = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_URL, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, pendingCode(slug, ep, salt), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private String targetUrl(String slug, int ep, String page, boolean bulk) {
        if (bulk && !slug.isEmpty()) return "https://animeav1.com/media/" + slug;
        if (!page.isEmpty()) return page;
        if (!slug.isEmpty() && ep > 0) return "https://animeav1.com/media/" + slug + "/" + ep;
        return "https://animeav1.com/";
    }

    private int pendingCode(String slug, int ep, int salt) {
        return 10000 + Math.abs((slug + "#" + ep + "#" + salt).hashCode() % 50000);
    }

    private int finalNotificationId(String slug, int ep) {
        return FINAL_BASE_ID + Math.abs((slug + "#" + ep).hashCode() % 50000);
    }

    private void broadcastRecord(EpisodeStore.DownloadRecord r) {
        broadcast(r.slug, r.episode, r.status, r.bytes, r.totalBytes, r.error);
    }

    private void broadcast(String slug, int ep, String status, long bytes, long total, String error) {
        Intent x = new Intent(ACTION_DOWNLOAD_UPDATED);
        x.setPackage(getPackageName());
        x.putExtra(EXTRA_SLUG, value(slug));
        x.putExtra(EXTRA_EPISODE, ep);
        x.putExtra(EXTRA_STATUS, value(status));
        x.putExtra(EXTRA_BYTES, bytes);
        x.putExtra(EXTRA_TOTAL, total);
        x.putExtra(EXTRA_ERROR, value(error));
        sendBroadcast(x);
    }

    private String titleFor(EpisodeStore.DownloadRecord r) {
        String b = r.title == null || r.title.trim().isEmpty() ? r.slug : r.title.trim();
        return b + " · Ep. " + r.episode;
    }

    private String taskKey(Intent i) {
        String s = value(i.getStringExtra(EXTRA_SLUG));
        int e = i.getIntExtra(EXTRA_EPISODE, 0);
        return s.isEmpty() || e <= 0 ? "" : s + "#" + e;
    }

    private static String readUtf8(InputStream in, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[32768];
        int total = 0, n;
        while ((n = in.read(b)) != -1) {
            total += n;
            if (total > max) throw new IllegalStateException("Respuesta demasiado grande");
            out.write(b, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static String value(String s) { return s == null ? "" : s; }

    @Override
    public void onDestroy() {
        HttpURLConnection c = currentConnection;
        if (c != null) c.disconnect();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static final class MegaLink {
        final String original, handle, key;
        MegaLink(String o, String h, String k) { original = o; handle = h; key = k; }
    }

    private static final class Cancelled extends Exception {}

    private static final class Result {
        final boolean ok, cancelled;
        final String message;
        private Result(boolean o, boolean c, String m) { ok = o; cancelled = c; message = m; }
        static Result success(String t, long b) { return new Result(true, false, t + " · " + humanBytes(b)); }
        static Result error(String t, String e) { return new Result(false, false, t + " · " + e); }
        static Result cancelled() { return new Result(false, true, ""); }
        static String humanBytes(long b) {
            if (b >= 1073741824L) return String.format(Locale.US, "%.2f GB", b / 1073741824d);
            if (b >= 1048576L) return String.format(Locale.US, "%.1f MB", b / 1048576d);
            if (b >= 1024L) return String.format(Locale.US, "%.1f KB", b / 1024d);
            return b + " B";
        }
    }
}
