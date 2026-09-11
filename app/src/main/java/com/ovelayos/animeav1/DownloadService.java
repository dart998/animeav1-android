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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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
    public static final String EXTRA_SLUG = "slug";
    public static final String EXTRA_EPISODE = "episode";
    public static final String EXTRA_PAGE_URL = "page_url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_COOKIE = "cookie";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_BYTES = "bytes";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_ERROR = "error";

    private static final String CHANNEL_ID = "animeav1_downloads";
    private static final int FOREGROUND_ID = 1101;
    private static final int FINAL_ID = 1102;

    private static final Pattern MEGA_RE = Pattern.compile(
            "[\\\"']?server[\\\"']?\\s*:\\s*[\\\"']Mega[\\\"']\\s*,\\s*[\\\"']?url[\\\"']?\\s*:\\s*[\\\"']([^\\\"']+)[\\\"']",
            Pattern.CASE_INSENSITIVE);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Descargas AnimeAV1", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Progreso de episodios descargados para verlos offline");
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (!busy.compareAndSet(false, true)) {
            broadcast(intent.getStringExtra(EXTRA_SLUG), intent.getIntExtra(EXTRA_EPISODE, 0),
                    EpisodeStore.STATUS_ERROR, 0, 0, "Ya hay otra descarga en curso");
            return START_NOT_STICKY;
        }

        startForeground(FOREGROUND_ID, buildProgressNotification("Preparando descarga…", 0, 0));
        executor.execute(() -> {
            Result result = performDownload(intent);
            busy.set(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            postFinalNotification(result);
            stopSelf(startId);
        });
        return START_NOT_STICKY;
    }

    private Result performDownload(Intent intent) {
        String slug = value(intent.getStringExtra(EXTRA_SLUG));
        int episode = intent.getIntExtra(EXTRA_EPISODE, 0);
        String pageUrl = value(intent.getStringExtra(EXTRA_PAGE_URL));
        String title = value(intent.getStringExtra(EXTRA_TITLE));
        String cookie = value(intent.getStringExtra(EXTRA_COOKIE));

        EpisodeStore store = new EpisodeStore(this);
        EpisodeStore.DownloadRecord record = new EpisodeStore.DownloadRecord();
        record.slug = slug;
        record.episode = episode;
        record.pageUrl = pageUrl;
        record.title = title;
        record.provider = "Mega";
        record.status = EpisodeStore.STATUS_RESOLVING;
        store.save(record);
        broadcastRecord(record);

        try {
            if (slug.isEmpty() || episode <= 0 || pageUrl.isEmpty()) {
                throw new IllegalArgumentException("Episodio no válido");
            }

            String html = fetchEpisodeHtml(pageUrl, cookie);
            String megaUrl = findMegaUrl(html);
            if (megaUrl.isEmpty()) throw new IllegalStateException("Este episodio no expone una fuente Mega descargable");

            record.sourceUrl = megaUrl;
            record.status = EpisodeStore.STATUS_DOWNLOADING;
            store.save(record);
            broadcastRecord(record);

            File finalFile = episodeFile(slug, episode);
            downloadMega(megaUrl, finalFile, record, store);

            record.path = finalFile.getAbsolutePath();
            record.bytes = finalFile.length();
            if (record.totalBytes <= 0) record.totalBytes = record.bytes;
            record.status = EpisodeStore.STATUS_COMPLETED;
            record.error = "";
            store.save(record);
            broadcastRecord(record);
            return Result.success(titleFor(record), record.bytes);
        } catch (Exception e) {
            record.status = EpisodeStore.STATUS_ERROR;
            record.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            store.save(record);
            broadcastRecord(record);
            return Result.error(titleFor(record), record.error);
        } finally {
            store.close();
        }
    }

    private String fetchEpisodeHtml(String pageUrl, String cookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(pageUrl).openConnection();
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
        }
    }

    private String findMegaUrl(String html) {
        Matcher m = MEGA_RE.matcher(html);
        while (m.find()) {
            String raw = decodeJsString(m.group(1));
            if (raw.contains("mega.nz") || raw.contains("mega.co.nz")) return raw;
        }
        return "";
    }

    private String decodeJsString(String raw) {
        return raw
                .replace("\\/", "/")
                .replace("\\u002F", "/")
                .replace("\\u002f", "/")
                .replace("\\u003A", ":")
                .replace("\\u003a", ":")
                .replace("\\u0023", "#")
                .replace("\\u0026", "&")
                .replace("&amp;", "&");
    }

    private void downloadMega(String rawUrl, File finalFile,
                              EpisodeStore.DownloadRecord record, EpisodeStore store) throws Exception {
        URL parsed = new URL(rawUrl);
        String path = parsed.getPath() == null ? "" : parsed.getPath();
        String fragment = parsed.getRef() == null ? "" : parsed.getRef();
        String handle = "";
        String keyPart = "";

        String[] pathBits = path.replaceAll("^/+|/+$", "").split("/");
        if (pathBits.length >= 2 && "file".equalsIgnoreCase(pathBits[0])) {
            handle = pathBits[1];
            keyPart = fragment;
        } else if (fragment.startsWith("!")) {
            String[] oldBits = fragment.substring(1).split("!");
            if (oldBits.length >= 2) {
                handle = oldBits[0];
                keyPart = oldBits[1];
            }
        }
        if (handle.isEmpty() || keyPart.isEmpty()) throw new IllegalStateException("URL Mega inválida");

        byte[] rawKey = Base64.decode(keyPart, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        if (rawKey.length != 32) throw new IllegalStateException("Clave Mega inválida");

        byte[] aesKey = new byte[16];
        for (int i = 0; i < 16; i++) aesKey[i] = (byte) (rawKey[i] ^ rawKey[i + 16]);
        byte[] iv = new byte[16];
        System.arraycopy(rawKey, 16, iv, 0, 8);

        JSONObject meta = requestMegaDownload(handle);
        String downloadUrl = meta.optString("g", "");
        long expected = meta.optLong("s", 0);
        if (downloadUrl.isEmpty()) throw new IllegalStateException("Mega no devolvió una URL de descarga");

        File dir = finalFile.getParentFile();
        if (dir == null || (!dir.exists() && !dir.mkdirs())) throw new IllegalStateException("No se puede crear la carpeta de descarga");
        File part = new File(finalFile.getAbsolutePath() + ".part");
        if (part.exists() && !part.delete()) throw new IllegalStateException("No se puede reiniciar la descarga parcial");

        HttpURLConnection c = (HttpURLConnection) new URL(downloadUrl).openConnection();
        c.setConnectTimeout(25000);
        c.setReadTimeout(45000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AnimeAV1/1.1");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IllegalStateException("Mega respondió HTTP " + code);
        }

        if (expected <= 0) expected = c.getContentLengthLong();
        record.totalBytes = Math.max(0, expected);
        record.bytes = 0;
        store.save(record);
        broadcastRecord(record);
        updateForeground(record);

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));

        long done = 0;
        long lastUpdate = 0;
        byte[] buffer = new byte[128 * 1024];
        try (InputStream raw = new BufferedInputStream(c.getInputStream());
             CipherInputStream decrypted = new CipherInputStream(raw, cipher);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(part))) {
            int n;
            while ((n = decrypted.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                done += n;
                long now = System.currentTimeMillis();
                if (now - lastUpdate >= 750) {
                    lastUpdate = now;
                    record.bytes = done;
                    store.save(record);
                    broadcastRecord(record);
                    updateForeground(record);
                }
            }
        } finally {
            c.disconnect();
        }

        if (expected > 0 && done != expected) {
            throw new IllegalStateException(String.format(Locale.US,
                    "Descarga Mega incompleta: %d/%d bytes", done, expected));
        }

        if (finalFile.exists() && !finalFile.delete()) throw new IllegalStateException("No se puede reemplazar el episodio existente");
        if (!part.renameTo(finalFile)) throw new IllegalStateException("No se puede finalizar el archivo descargado");

        record.bytes = done;
        record.totalBytes = expected > 0 ? expected : done;
        updateForeground(record);
    }

    private JSONObject requestMegaDownload(String handle) throws Exception {
        URL api = new URL("https://g.api.mega.co.nz/cs?id=" + System.nanoTime());
        HttpURLConnection c = (HttpURLConnection) api.openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(25000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        byte[] body = ("[{\"a\":\"g\",\"g\":1,\"p\":\"" + handle + "\"}]").getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(body.length);
        try (BufferedOutputStream out = new BufferedOutputStream(c.getOutputStream())) {
            out.write(body);
        }
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IllegalStateException("Mega API respondió HTTP " + code);
        }
        String json;
        try (InputStream in = c.getInputStream()) {
            json = readUtf8(in, 2 * 1024 * 1024);
        } finally {
            c.disconnect();
        }
        JSONArray array = new JSONArray(json);
        if (array.length() == 0 || !(array.get(0) instanceof JSONObject)) {
            throw new IllegalStateException("Respuesta Mega inválida");
        }
        JSONObject obj = array.getJSONObject(0);
        if (obj.has("e")) throw new IllegalStateException("Mega API error " + obj.optInt("e"));
        return obj;
    }

    private File episodeFile(String slug, int episode) {
        File base = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (base == null) base = new File(getFilesDir(), "movies");
        File dir = new File(new File(base, "AnimeAV1"), safe(slug));
        return new File(dir, String.format(Locale.US, "%03d.mp4", episode));
    }

    private String safe(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.isEmpty() ? "anime" : safe;
    }

    private void updateForeground(EpisodeStore.DownloadRecord r) {
        String text;
        if (r.totalBytes > 0) {
            int pct = (int) Math.min(100, (r.bytes * 100L) / r.totalBytes);
            text = titleFor(r) + " · " + pct + "%";
        } else {
            text = titleFor(r) + " · descargando";
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(FOREGROUND_ID, buildProgressNotification(text, r.bytes, r.totalBytes));
    }

    private Notification buildProgressNotification(String text, long done, long total) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("AnimeAV1")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS);
        if (total > 0) {
            int pct = (int) Math.min(100, (done * 100L) / total);
            b.setProgress(100, pct, false);
        } else {
            b.setProgress(0, 0, true);
        }
        return b.build();
    }

    private void postFinalNotification(Result result) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(result.ok ? "Descarga completada" : "Error de descarga")
                .setContentText(result.message)
                .setContentIntent(pi)
                .setAutoCancel(true);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(FINAL_ID, b.build());
    }

    private void broadcastRecord(EpisodeStore.DownloadRecord r) {
        broadcast(r.slug, r.episode, r.status, r.bytes, r.totalBytes, r.error);
    }

    private void broadcast(String slug, int episode, String status, long bytes, long total, String error) {
        Intent i = new Intent(ACTION_DOWNLOAD_UPDATED);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_SLUG, value(slug));
        i.putExtra(EXTRA_EPISODE, episode);
        i.putExtra(EXTRA_STATUS, value(status));
        i.putExtra(EXTRA_BYTES, bytes);
        i.putExtra(EXTRA_TOTAL, total);
        i.putExtra(EXTRA_ERROR, value(error));
        sendBroadcast(i);
    }

    private String titleFor(EpisodeStore.DownloadRecord r) {
        String base = r.title == null || r.title.trim().isEmpty() ? r.slug : r.title.trim();
        return base + " · Ep. " + r.episode;
    }

    private static String readUtf8(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[32 * 1024];
        int total = 0;
        int n;
        while ((n = in.read(b)) != -1) {
            total += n;
            if (total > maxBytes) throw new IllegalStateException("Respuesta demasiado grande");
            out.write(b, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static String value(String s) { return s == null ? "" : s; }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final class Result {
        final boolean ok;
        final String message;
        private Result(boolean ok, String message) { this.ok = ok; this.message = message; }
        static Result success(String title, long bytes) {
            return new Result(true, title + " · " + humanBytes(bytes));
        }
        static Result error(String title, String error) {
            return new Result(false, title + " · " + error);
        }
        static String humanBytes(long bytes) {
            if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.US, "%.2f GB", bytes / (1024d * 1024d * 1024d));
            if (bytes >= 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024d * 1024d));
            if (bytes >= 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024d);
            return bytes + " B";
        }
    }
}
