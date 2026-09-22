package com.dart998.animeav1;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AnimeAv1Client {
    static final String ORIGIN = "https://animeav1.com";
    private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/142 Mobile Safari/537.36 AnimeAV1/" + BuildConfig.VERSION_NAME;

    static String get(String url, String cookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20_000); c.setReadTimeout(30_000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
        if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        int code = c.getResponseCode();
        if (code < 200 || code >= 400) { c.disconnect(); throw new IllegalStateException("AnimeAV1 respondió HTTP " + code); }
        try (InputStream in = new BufferedInputStream(c.getInputStream())) { return MegaClient.read(in, 8 * 1024 * 1024); }
        finally { c.disconnect(); }
    }

    static List<LibraryItem> library(String cookie) throws Exception {
        if (cookie == null || cookie.trim().isEmpty()) throw new IllegalStateException("Inicia sesión para consultar Mis Listas");
        return parseLibrary(get(ORIGIN + "/cuenta/listas", cookie));
    }

    static List<LibraryItem> parseLibrary(String html) {
        int marker = html.indexOf("libraryEntries:");
        if (marker < 0) throw new IllegalStateException("La sesión no contiene la biblioteca de AnimeAV1");
        int start = html.indexOf('[', marker);
        if (start < 0) throw new IllegalStateException("Biblioteca incompleta");
        String array = balanced(html, start, '[', ']');
        ArrayList<LibraryItem> result = new ArrayList<>();
        for (String object : objects(array)) {
            String media = objectValue(object, "media");
            LibraryItem item = new LibraryItem();
            item.status = integer(object, "status"); item.watched = integer(object, "episode");
            item.slug = string(media, "slug"); item.title = string(media, "title");
            if (!item.slug.isEmpty()) result.add(item);
        }
        return result;
    }

    static int publishedEpisodes(String slug, String cookie) throws Exception {
        String html = get(ORIGIN + "/media/" + slug, cookie);
        Pattern pattern = Pattern.compile("/media/" + Pattern.quote(slug) + "/(\\d+)(?:[\\\"'/?#]|$)");
        Matcher matcher = pattern.matcher(html); int max = 0;
        while (matcher.find()) { try { max = Math.max(max, Integer.parseInt(matcher.group(1))); } catch (Exception ignored) { } }
        return max;
    }

    static String episodeUrl(String slug, int episode) { return ORIGIN + "/media/" + slug + "/" + episode; }

    private static int integer(String block, String name) {
        Matcher m = Pattern.compile("(?:\\\"?" + Pattern.quote(name) + "\\\"?)\\s*:\\s*(-?\\d+|null)").matcher(block);
        if (!m.find() || "null".equals(m.group(1))) return 0;
        try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) { return 0; }
    }
    private static String string(String block, String name) {
        Matcher m = Pattern.compile("(?:\\\"?" + Pattern.quote(name) + "\\\"?)\\s*:\\s*(\\\"(?:\\\\.|[^\\\"\\\\])*\\\")").matcher(block);
        if (!m.find()) return "";
        return unescapeJsonString(m.group(1));
    }
    private static String unescapeJsonString(String quoted) {
        if (quoted == null || quoted.length() < 2) return "";
        StringBuilder out = new StringBuilder(quoted.length() - 2);
        for (int i = 1; i < quoted.length() - 1; i++) {
            char c = quoted.charAt(i);
            if (c != '\\' || i + 1 >= quoted.length() - 1) { out.append(c); continue; }
            char escaped = quoted.charAt(++i);
            switch (escaped) {
                case '\"': out.append('\"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (i + 4 >= quoted.length()) return "";
                    try { out.append((char) Integer.parseInt(quoted.substring(i + 1, i + 5), 16)); i += 4; }
                    catch (NumberFormatException invalid) { return ""; }
                    break;
                default: out.append(escaped);
            }
        }
        return out.toString();
    }
    private static String objectValue(String block, String name) {
        Matcher m = Pattern.compile("(?:\\\"?" + Pattern.quote(name) + "\\\"?)\\s*:\\s*\\{").matcher(block);
        if (!m.find()) return "";
        try { return balanced(block, block.indexOf('{', m.start()), '{', '}'); } catch (Exception ignored) { return ""; }
    }
    private static String balanced(String source, int start, char open, char close) {
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i=start; i<source.length(); i++) { char c=source.charAt(i); if (quoted) { if (escaped) escaped=false; else if(c=='\\') escaped=true; else if(c=='\"') quoted=false; continue; } if(c=='\"'){quoted=true;continue;} if(c==open)depth++; else if(c==close&&--depth==0)return source.substring(start,i+1); }
        throw new IllegalStateException("Bloque incompleto");
    }
    private static List<String> objects(String array) {
        ArrayList<String> result=new ArrayList<>(); int depth=0,start=-1; boolean quoted=false,escaped=false;
        for(int i=0;i<array.length();i++){char c=array.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='\"')quoted=false;continue;}if(c=='\"'){quoted=true;continue;}if(c=='{'){if(depth==0)start=i;depth++;}else if(c=='}'&&--depth==0&&start>=0){result.add(array.substring(start,i+1));start=-1;}}
        return result;
    }
    static final class LibraryItem { String slug="", title=""; int status, watched; }
}
