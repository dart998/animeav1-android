package com.ovelayos.animeav1;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AnimeAv1SeriesClient {
    private static final int MAX_HTML = 8 * 1024 * 1024;

    private AnimeAv1SeriesClient() {}

    static int fetchPublished(String slug, String cookie) throws Exception {
        if (slug == null || !slug.matches("[A-Za-z0-9._~-]+")) throw new IllegalArgumentException("Serie no válida");
        String page = "https://animeav1.com/media/" + slug;
        HttpURLConnection c = (HttpURLConnection) new URL(page).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/142 Mobile Safari/537.36");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        c.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
        if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        int code = c.getResponseCode();
        if (code < 200 || code >= 400) {
            c.disconnect();
            throw new IllegalStateException("AnimeAV1 respondió HTTP " + code + " al consultar " + slug);
        }
        String html;
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            html = readUtf8(in, MAX_HTML);
        } finally {
            c.disconnect();
        }
        Pattern p = Pattern.compile("/media/" + Pattern.quote(slug) + "/(\\d+)(?:[\\\"'/?#<]|$)");
        Matcher m = p.matcher(html);
        int max = 0;
        while (m.find()) {
            try { max = Math.max(max, Integer.parseInt(m.group(1))); } catch (Exception ignored) {}
        }
        if (max <= 0) throw new IllegalStateException("No se pudo determinar el último episodio publicado de " + slug);
        return max;
    }

    private static String readUtf8(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[32 * 1024];
        int total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > maxBytes) throw new IllegalStateException("Respuesta de AnimeAV1 demasiado grande");
            out.write(buffer, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
