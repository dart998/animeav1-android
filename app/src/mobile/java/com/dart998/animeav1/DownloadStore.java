package com.dart998.animeav1;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class DownloadStore extends SQLiteOpenHelper {
    private static final String DB = "animeav1-local.db";
    private static final int VERSION = 1;

    DownloadStore(Context context) { super(context.getApplicationContext(), DB, null, VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE downloads (slug TEXT NOT NULL, episode INTEGER NOT NULL," +
                "series_title TEXT NOT NULL DEFAULT '', page_url TEXT NOT NULL DEFAULT ''," +
                "source_url TEXT NOT NULL DEFAULT '', local_path TEXT NOT NULL DEFAULT ''," +
                "state TEXT NOT NULL, error TEXT NOT NULL DEFAULT '', origin TEXT NOT NULL DEFAULT 'single'," +
                "batch_id TEXT NOT NULL DEFAULT '', bytes INTEGER NOT NULL DEFAULT 0," +
                "total_bytes INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL," +
                "PRIMARY KEY(slug, episode))");
        db.execSQL("CREATE INDEX downloads_state_idx ON downloads(state, updated_at)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    synchronized boolean insertUnlessPresent(DownloadEntry e) {
        DownloadEntry current = get(e.slug, e.episode);
        if (current != null && (current.isActive() || current.isPlayable())) return false;
        save(e);
        return true;
    }

    synchronized void save(DownloadEntry e) {
        e.updatedAt = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("slug", e.slug); v.put("episode", e.episode); v.put("series_title", n(e.seriesTitle));
        v.put("page_url", n(e.pageUrl)); v.put("source_url", n(e.sourceUrl)); v.put("local_path", n(e.localPath));
        v.put("state", n(e.state)); v.put("error", n(e.error)); v.put("origin", n(e.origin));
        v.put("batch_id", n(e.batchId)); v.put("bytes", e.bytes); v.put("total_bytes", e.totalBytes);
        v.put("updated_at", e.updatedAt);
        getWritableDatabase().insertWithOnConflict("downloads", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    synchronized DownloadEntry get(String slug, int episode) {
        try (Cursor c = getReadableDatabase().query("downloads", null, "slug=? AND episode=?",
                new String[]{slug, String.valueOf(episode)}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    synchronized List<DownloadEntry> all() {
        ArrayList<DownloadEntry> result = new ArrayList<>();
        String order = "CASE state WHEN 'downloading' THEN 0 WHEN 'resolving' THEN 1 WHEN 'queued' THEN 2 " +
                "WHEN 'error' THEN 3 WHEN 'completed' THEN 4 ELSE 5 END, series_title COLLATE NOCASE, episode";
        try (Cursor c = getReadableDatabase().query("downloads", null, null, null, null, null, order)) {
            while (c.moveToNext()) result.add(read(c));
        }
        return result;
    }

    synchronized List<DownloadEntry> queued() {
        ArrayList<DownloadEntry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("downloads", null, "state=?", new String[]{DownloadEntry.QUEUED},
                null, null, "updated_at")) { while (c.moveToNext()) result.add(read(c)); }
        return result;
    }

    synchronized void remove(String slug, int episode, boolean deleteFile) {
        DownloadEntry e = get(slug, episode);
        if (deleteFile && e != null && !e.localPath.isEmpty()) {
            File f = new File(e.localPath);
            if (f.isFile()) f.delete();
            File part = new File(e.localPath + ".part");
            if (part.isFile()) part.delete();
        }
        getWritableDatabase().delete("downloads", "slug=? AND episode=?", new String[]{slug, String.valueOf(episode)});
    }

    synchronized void recoverInterrupted() {
        ContentValues v = new ContentValues();
        v.put("state", DownloadEntry.QUEUED); v.put("error", ""); v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("downloads", v, "state IN (?,?)",
                new String[]{DownloadEntry.RESOLVING, DownloadEntry.DOWNLOADING});
    }

    synchronized int deleteWatched(String slug, int watched) {
        int removed = 0;
        for (DownloadEntry e : all()) {
            if (e.slug.equals(slug) && e.episode <= watched && e.isPlayable()) { remove(e.slug, e.episode, true); removed++; }
        }
        return removed;
    }

    private static DownloadEntry read(Cursor c) {
        DownloadEntry e = new DownloadEntry();
        e.slug=s(c,"slug"); e.episode=i(c,"episode"); e.seriesTitle=s(c,"series_title"); e.pageUrl=s(c,"page_url");
        e.sourceUrl=s(c,"source_url"); e.localPath=s(c,"local_path"); e.state=s(c,"state"); e.error=s(c,"error");
        e.origin=s(c,"origin"); e.batchId=s(c,"batch_id"); e.bytes=l(c,"bytes"); e.totalBytes=l(c,"total_bytes"); e.updatedAt=l(c,"updated_at");
        return e;
    }
    private static String s(Cursor c,String name){return c.getString(c.getColumnIndexOrThrow(name));}
    private static int i(Cursor c,String name){return c.getInt(c.getColumnIndexOrThrow(name));}
    private static long l(Cursor c,String name){return c.getLong(c.getColumnIndexOrThrow(name));}
    private static String n(String value){return value == null ? "" : value;}
}
