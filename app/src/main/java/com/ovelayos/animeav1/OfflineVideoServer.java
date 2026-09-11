package com.ovelayos.animeav1;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OfflineVideoServer {
    private static final String HOST = "offline.animeav1.local";

    private OfflineVideoServer() {}

    public static String urlFor(String slug, int episode) {
        return "https://" + HOST + "/video/" + Uri.encode(slug) + "/" + episode + ".mp4";
    }

    public static WebResourceResponse open(WebResourceRequest request, EpisodeStore store) {
        Uri uri = request.getUrl();
        if (!HOST.equalsIgnoreCase(uri.getHost())) return null;

        List<String> parts = uri.getPathSegments();
        if (parts.size() != 3 || !"video".equals(parts.get(0))) return response(404, "Not Found", 0, null);

        String slug = parts.get(1);
        String fileName = parts.get(2);
        int dot = fileName.indexOf('.');
        if (dot > 0) fileName = fileName.substring(0, dot);

        int episode;
        try { episode = Integer.parseInt(fileName); }
        catch (NumberFormatException e) { return response(404, "Not Found", 0, null); }

        EpisodeStore.DownloadRecord record = store.get(slug, episode);
        if (record == null || !EpisodeStore.STATUS_COMPLETED.equals(record.status) || record.path.isEmpty()) {
            return response(404, "Not Found", 0, null);
        }

        File file = new File(record.path);
        if (!file.isFile() || file.length() <= 0) return response(404, "Not Found", 0, null);

        long size = file.length();
        String range = request.getRequestHeaders().get("Range");
        if (range == null || !range.startsWith("bytes=")) {
            try {
                LimitedRandomAccessInputStream in = new LimitedRandomAccessInputStream(file, 0, size);
                Map<String, String> headers = baseHeaders(size);
                return new WebResourceResponse("video/mp4", null, 200, "OK", headers, in);
            } catch (IOException e) {
                return response(500, "Internal Server Error", 0, null);
            }
        }

        long start;
        long end;
        try {
            String spec = range.substring(6).split(",", 2)[0].trim();
            int dash = spec.indexOf('-');
            if (dash < 0) throw new IllegalArgumentException();
            String left = spec.substring(0, dash).trim();
            String right = spec.substring(dash + 1).trim();
            if (left.isEmpty()) {
                long suffix = Long.parseLong(right);
                if (suffix <= 0) throw new IllegalArgumentException();
                start = Math.max(0, size - suffix);
                end = size - 1;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? size - 1 : Math.min(size - 1, Long.parseLong(right));
            }
            if (start < 0 || start >= size || end < start) throw new IllegalArgumentException();
        } catch (Exception e) {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Content-Range", "bytes */" + size);
            headers.put("Accept-Ranges", "bytes");
            return new WebResourceResponse("video/mp4", null, 416, "Range Not Satisfiable", headers,
                    new ByteArrayInputStream(new byte[0]));
        }

        long length = end - start + 1;
        try {
            LimitedRandomAccessInputStream in = new LimitedRandomAccessInputStream(file, start, length);
            Map<String, String> headers = baseHeaders(length);
            headers.put("Content-Range", String.format(Locale.US, "bytes %d-%d/%d", start, end, size));
            return new WebResourceResponse("video/mp4", null, 206, "Partial Content", headers, in);
        } catch (IOException e) {
            return response(500, "Internal Server Error", 0, null);
        }
    }

    private static Map<String, String> baseHeaders(long length) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Length", Long.toString(length));
        headers.put("Accept-Ranges", "bytes");
        headers.put("Cache-Control", "no-store");
        headers.put("Access-Control-Allow-Origin", "*");
        return headers;
    }

    private static WebResourceResponse response(int status, String reason, long length, InputStream input) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Length", Long.toString(length));
        return new WebResourceResponse("text/plain", "utf-8", status, reason, headers,
                input != null ? input : new ByteArrayInputStream(new byte[0]));
    }

    private static final class LimitedRandomAccessInputStream extends InputStream {
        private final RandomAccessFile file;
        private long remaining;

        LimitedRandomAccessInputStream(File source, long offset, long length) throws IOException {
            file = new RandomAccessFile(source, "r");
            file.seek(offset);
            remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int value = file.read();
            if (value >= 0) remaining--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) return -1;
            int wanted = (int) Math.min(length, remaining);
            int count = file.read(buffer, offset, wanted);
            if (count > 0) remaining -= count;
            return count;
        }

        @Override
        public long skip(long count) throws IOException {
            long n = Math.min(count, remaining);
            file.seek(file.getFilePointer() + n);
            remaining -= n;
            return n;
        }

        @Override
        public int available() {
            return (int) Math.min(Integer.MAX_VALUE, remaining);
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }
}
