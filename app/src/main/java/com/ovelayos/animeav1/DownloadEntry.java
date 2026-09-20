package com.ovelayos.animeav1;

import java.io.File;

final class DownloadEntry {
    static final String QUEUED = "queued";
    static final String RESOLVING = "resolving";
    static final String DOWNLOADING = "downloading";
    static final String COMPLETED = "completed";
    static final String CANCELLED = "cancelled";
    static final String ERROR = "error";

    String slug = "";
    int episode;
    String seriesTitle = "";
    String pageUrl = "";
    String sourceUrl = "";
    String localPath = "";
    String state = QUEUED;
    String error = "";
    String origin = "single";
    String batchId = "";
    long bytes;
    long totalBytes;
    long updatedAt;

    String key() { return slug + "#" + episode; }
    boolean isActive() { return QUEUED.equals(state) || RESOLVING.equals(state) || DOWNLOADING.equals(state); }
    boolean isPlayable() { return COMPLETED.equals(state) && !localPath.isEmpty() && new File(localPath).isFile(); }
}
