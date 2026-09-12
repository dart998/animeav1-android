package com.ovelayos.animeav1;

import org.json.JSONTokener;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AnimeAv1LibraryClient {
    private static final String LIBRARY_URL = "https://animeav1.com/cuenta/listas";
    private static final int MAX_HTML = 8 * 1024 * 1024;

    private AnimeAv1LibraryClient() {}

    static List<Item> fetch(String cookie) throws Exception {
        if (cookie == null || cookie.trim().isEmpty()) {
            throw new IllegalStateException("Inicia sesión en AnimeAV1 para consultar los capítulos no vistos");
        }

        HttpURLConnection c = (HttpURLConnection) new URL(LIBRARY_URL).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/142 Mobile Safari/537.36");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        c.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
        c.setRequestProperty("Cookie", cookie);
        int code = c.getResponseCode();
        if (code < 200 || code >= 400) {
            c.disconnect();
            throw new IllegalStateException("AnimeAV1 respondió HTTP " + code);
        }
        String body;
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            body = readUtf8(in, MAX_HTML);
        } finally {
            c.disconnect();
        }

        String low = body.toLowerCase();
        if (low.contains("verifique que es un ser humano")
                || (low.contains("iniciar sesión") && !body.contains("libraryEntries:"))) {
            throw new IllegalStateException("La sesión de AnimeAV1 ha caducado");
        }
        return parseLibraryEntries(body);
    }

    static List<Item> parseLibraryEntries(String body) throws Exception {
        int marker = body.indexOf("libraryEntries:");
        if (marker < 0) throw new IllegalStateException("AnimeAV1 no contiene libraryEntries");
        int start = body.indexOf('[', marker);
        if (start < 0) throw new IllegalStateException("libraryEntries sin array");
        String array = balanced(body, start, '[', ']');

        ArrayList<Item> out = new ArrayList<>();
        for (String obj : splitTopObjects(array)) {
            String media = extractObject(obj, "media");
            if (media.isEmpty()) continue;
            Item item = new Item();
            item.mediaId = fieldId(obj, "mediaId");
            item.status = fieldInt(obj, "status");
            item.seen = fieldInt(obj, "episode");
            item.title = fieldString(media, "title");
            item.total = fieldInt(media, "episodesCount");
            item.slug = fieldString(media, "slug");
            if (!item.title.trim().isEmpty() && !item.slug.trim().isEmpty()) out.add(item);
        }
        if (out.isEmpty()) throw new IllegalStateException("No se pudo leer la biblioteca de AnimeAV1");
        return out;
    }

    private static int fieldInt(String block, String name) {
        Pattern p = Pattern.compile("(?:\\\"?" + Pattern.quote(name) + "\\\"?)\\s*:\\s*(-?\\d+|null)");
        Matcher m = p.matcher(block);
        if (!m.find() || "null".equals(m.group(1))) return 0;
        try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) { return 0; }
    }

    private static String fieldId(String block, String name) {
        Pattern p = Pattern.compile("(?:\\\"?" + Pattern.quote(name) + "\\\"?)\\s*:\\s*(?:\\\"([^\\\"\\\\]*(?:\\\\.[^\\\"\\\\]*)*)\\\"|(-?[0-9]+))");
        Matcher m = p.matcher(block);
        if (!m.find()) return "";
        if (m.group(1) != null) return jsonString("\"" + m.group(1) + "\"");
        return m.group(2) == null ? "" : m.group(2);
    }

    private static String fieldString(String block, String name) {
        Pattern p = Pattern.compile("(?:\\\"?" + Pattern.quote(name) + "\\\"?)\\s*:\\s*(\\\"(?:\\\\.|[^\\\"\\\\])*\\\")");
        Matcher m = p.matcher(block);
        return m.find() ? jsonString(m.group(1)) : "";
    }

    private static String jsonString(String quoted) {
        try {
            Object value = new JSONTokener(quoted).nextValue();
            return value instanceof String ? (String) value : "";
        } catch (Exception ignored) {
            return quoted == null ? "" : quoted.replace("\"", "");
        }
    }

    private static String extractObject(String block, String key) {
        Pattern p = Pattern.compile("(?:\\\"?" + Pattern.quote(key) + "\\\"?)\\s*:\\s*\\{");
        Matcher m = p.matcher(block);
        if (!m.find()) return "";
        int brace = block.indexOf('{', m.start());
        if (brace < 0) return "";
        try { return balanced(block, brace, '{', '}'); } catch (Exception ignored) { return ""; }
    }

    private static String balanced(String src, int start, char open, char close) throws Exception {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return src.substring(start, i + 1);
            }
        }
        throw new IllegalStateException("Bloque de AnimeAV1 incompleto");
    }

    private static List<String> splitTopObjects(String array) {
        ArrayList<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < array.length(); i++) {
            char c = array.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(array.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out;
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

    static final class Item {
        String mediaId = "";
        String title = "";
        String slug = "";
        int seen;
        int total;
        int status;
    }
}
