package com.ovelayos.animeav1;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class EpisodeStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "offline.db";
    private static final int DB_VERSION = 1;

    public static final String STATUS_RESOLVING = "resolving";
    public static final String STATUS_DOWNLOADING = "downloading";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_ERROR = "error";

    public EpisodeStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE downloads (" +
                "slug TEXT NOT NULL," +
                "episode INTEGER NOT NULL," +
                "page_url TEXT NOT NULL DEFAULT ''," +
                "title TEXT NOT NULL DEFAULT ''," +
                "provider TEXT NOT NULL DEFAULT ''," +
                "source_url TEXT NOT NULL DEFAULT ''," +
                "path TEXT NOT NULL DEFAULT ''," +
                "bytes INTEGER NOT NULL DEFAULT 0," +
                "total_bytes INTEGER NOT NULL DEFAULT 0," +
                "status TEXT NOT NULL DEFAULT ''," +
                "error TEXT NOT NULL DEFAULT ''," +
                "updated_at INTEGER NOT NULL DEFAULT 0," +
                "PRIMARY KEY(slug, episode))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public synchronized void save(DownloadRecord r) {
        r.updatedAt = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("slug", r.slug);
        v.put("episode", r.episode);
        v.put("page_url", n(r.pageUrl));
        v.put("title", n(r.title));
        v.put("provider", n(r.provider));
        v.put("source_url", n(r.sourceUrl));
        v.put("path", n(r.path));
        v.put("bytes", r.bytes);
        v.put("total_bytes", r.totalBytes);
        v.put("status", n(r.status));
        v.put("error", n(r.error));
        v.put("updated_at", r.updatedAt);
        getWritableDatabase().insertWithOnConflict("downloads", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized DownloadRecord get(String slug, int episode) {
        try (Cursor c = getReadableDatabase().query(
                "downloads", null, "slug=? AND episode=?",
                new String[]{slug, Integer.toString(episode)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public synchronized List<DownloadRecord> listCompleted() {
        ArrayList<DownloadRecord> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "downloads", null, "status=?", new String[]{STATUS_COMPLETED},
                null, null, "updated_at DESC")) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    public synchronized boolean hasActiveDownload() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM downloads WHERE status IN (?,?) LIMIT 1",
                new String[]{STATUS_RESOLVING, STATUS_DOWNLOADING})) {
            return c.moveToFirst();
        }
    }

    public synchronized void markInterruptedDownloads() {
        ContentValues v = new ContentValues();
        v.put("status", STATUS_ERROR);
        v.put("error", "Descarga interrumpida");
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("downloads", v, "status IN (?,?)",
                new String[]{STATUS_RESOLVING, STATUS_DOWNLOADING});
    }

    public synchronized void delete(String slug, int episode) {
        getWritableDatabase().delete("downloads", "slug=? AND episode=?",
                new String[]{slug, Integer.toString(episode)});
    }

    private DownloadRecord fromCursor(Cursor c) {
        DownloadRecord r = new DownloadRecord();
        r.slug = s(c, "slug");
        r.episode = i(c, "episode");
        r.pageUrl = s(c, "page_url");
        r.title = s(c, "title");
        r.provider = s(c, "provider");
        r.sourceUrl = s(c, "source_url");
        r.path = s(c, "path");
        r.bytes = l(c, "bytes");
        r.totalBytes = l(c, "total_bytes");
        r.status = s(c, "status");
        r.error = s(c, "error");
        r.updatedAt = l(c, "updated_at");
        return r;
    }

    private static String s(Cursor c, String name) { return c.getString(c.getColumnIndexOrThrow(name)); }
    private static int i(Cursor c, String name) { return c.getInt(c.getColumnIndexOrThrow(name)); }
    private static long l(Cursor c, String name) { return c.getLong(c.getColumnIndexOrThrow(name)); }
    private static String n(String value) { return value == null ? "" : value; }

    public static class DownloadRecord {
        public String slug = "";
        public int episode;
        public String pageUrl = "";
        public String title = "";
        public String provider = "";
        public String sourceUrl = "";
        public String path = "";
        public long bytes;
        public long totalBytes;
        public String status = "";
        public String error = "";
        public long updatedAt;
    }
}
