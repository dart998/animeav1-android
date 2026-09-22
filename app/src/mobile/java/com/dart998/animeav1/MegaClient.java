package com.dart998.animeav1;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class MegaClient {
    private static final Pattern PROVIDER = Pattern.compile("[\\\"']?server[\\\"']?\\s*:\\s*[\\\"']Mega[\\\"']\\s*,\\s*[\\\"']?url[\\\"']?\\s*:\\s*[\\\"']([^\\\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile("https?://(?:www\\.)?mega\\.(?:nz|co\\.nz)/[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUDIO_LANGUAGE = Pattern.compile("(?:[\\\"']?(?:language|lang|audio|version|type|label|name)[\\\"']?\\s*[:=]\\s*[\\\"']?(SUB|DUB)[\\\"']?|(?:^|[,{])\\s*[\\\"']?(SUB|DUB)[\\\"']?\\s*:|>\\s*(SUB|DUB)\\s*<)", Pattern.CASE_INSENSITIVE);

    interface Control {
        boolean cancelled();
        void progress(long bytes, long total);
        void connection(HttpURLConnection connection);
    }

    static Link fromHtml(String html) {
        for(String url:orderedUrlsFromHtml(html)){Link link=parse(url);if(link!=null)return link;}
        return null;
    }

    static List<String> orderedUrlsFromHtml(String html){
        String decoded=decode(html);List<Marker> markers=new ArrayList<>();Matcher audio=AUDIO_LANGUAGE.matcher(decoded);
        while(audio.find()){String language="";for(int i=1;i<=audio.groupCount();i++)if(audio.group(i)!=null){language=audio.group(i).toUpperCase(Locale.US);break;}if(!language.isEmpty())markers.add(new Marker(audio.start(),language));}
        List<Candidate> candidates=new ArrayList<>();Matcher provider=PROVIDER.matcher(decoded);while(provider.find())candidates.add(new Candidate(provider.start(),provider.group(1)));
        if(candidates.isEmpty()){Matcher any=LINK.matcher(decoded);while(any.find())candidates.add(new Candidate(any.start(),any.group()));}
        List<String> sub=new ArrayList<>(),unknown=new ArrayList<>(),dub=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Candidate candidate:candidates){String raw=decode(candidate.url);if(!seen.add(raw))continue;String language="";for(Marker marker:markers){if(marker.position>candidate.position)break;language=marker.language;}if("SUB".equals(language))sub.add(raw);else if("DUB".equals(language))dub.add(raw);else unknown.add(raw);}
        ArrayList<String> ordered=new ArrayList<>(sub.size()+unknown.size()+dub.size());ordered.addAll(sub);ordered.addAll(unknown);ordered.addAll(dub);return ordered;
    }

    static Link parse(String value) {
        if (value == null) return null;
        String raw = decode(value).trim();
        for (int i = 0; i < 2 && raw.contains("%2"); i++) {
            try { raw = URLDecoder.decode(raw, StandardCharsets.UTF_8.name()); } catch (Exception ignored) { break; }
        }
        Matcher found = LINK.matcher(raw);
        if (found.find()) raw = found.group();
        while (!raw.isEmpty() && ")]},.;".indexOf(raw.charAt(raw.length()-1)) >= 0) raw = raw.substring(0, raw.length()-1);
        try {
            URI uri = new URI(raw);
            String host = text(uri.getHost()).toLowerCase(Locale.US);
            if (!(host.equals("mega.nz") || host.endsWith(".mega.nz") || host.equals("mega.co.nz") || host.endsWith(".mega.co.nz"))) return null;
            String path = text(uri.getPath()).replaceAll("^/+|/+$", "");
            String fragment = text(uri.getRawFragment());
            String handle = "", key = "";
            String[] parts = path.isEmpty() ? new String[0] : path.split("/");
            if (parts.length >= 2 && (parts[0].equalsIgnoreCase("file") || parts[0].equalsIgnoreCase("embed"))) {
                handle = parts[1]; key = fragment;
            } else if (fragment.startsWith("!")) {
                String[] legacy = fragment.substring(1).split("!");
                if (legacy.length >= 2) { handle = legacy[0]; key = legacy[1]; }
            }
            if (key.contains("?")) key = key.substring(0, key.indexOf('?'));
            byte[] decoded = Base64.decode(key, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            return handle.isEmpty() || decoded.length != 32 ? null : new Link(raw, handle, key);
        } catch (Exception ignored) { return null; }
    }

    static void download(Link link, File destination, Control control) throws Exception {
        byte[] raw = Base64.decode(link.key, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        byte[] aes = new byte[16], iv = new byte[16];
        for (int i = 0; i < 16; i++) aes[i] = (byte) (raw[i] ^ raw[i + 16]);
        System.arraycopy(raw, 16, iv, 0, 8);
        JSONObject info = request(link.handle, control);
        String downloadUrl = info.optString("g");
        long total = info.optLong("s");
        if (downloadUrl.isEmpty()) throw new IllegalStateException("Mega no devolvió el archivo");
        File parent = destination.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) throw new IllegalStateException("No se pudo crear la carpeta local");
        File part = new File(destination.getAbsolutePath() + ".part");
        if (part.exists() && !part.delete()) throw new IllegalStateException("No se pudo reiniciar el archivo parcial");

        HttpURLConnection c = (HttpURLConnection) new URL(downloadUrl).openConnection();
        control.connection(c); c.setConnectTimeout(25_000); c.setReadTimeout(45_000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "AnimeAV1-Android/" + BuildConfig.VERSION_NAME);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("Mega respondió HTTP " + code);
        if (total <= 0) total = c.getContentLengthLong();
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aes, "AES"), new IvParameterSpec(iv));
        long done = 0, lastUpdate = 0;
        byte[] buffer = new byte[128 * 1024];
        try (InputStream input = new CipherInputStream(new BufferedInputStream(c.getInputStream()), cipher);
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(part))) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (control.cancelled()) throw new Cancelled();
                output.write(buffer, 0, count); done += count;
                long now = System.currentTimeMillis();
                if (now - lastUpdate > 650) { lastUpdate = now; control.progress(done, total); }
            }
        } finally { c.disconnect(); control.connection(null); }
        if (total > 0 && done != total) { part.delete(); throw new IllegalStateException("Descarga incompleta"); }
        if (destination.exists() && !destination.delete()) throw new IllegalStateException("No se pudo reemplazar el archivo");
        if (!part.renameTo(destination)) throw new IllegalStateException("No se pudo finalizar el archivo");
        control.progress(done, total > 0 ? total : done);
    }

    private static JSONObject request(String handle, Control control) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://g.api.mega.co.nz/cs?id=" + System.nanoTime()).openConnection();
        control.connection(c); c.setDoOutput(true); c.setRequestMethod("POST"); c.setConnectTimeout(20_000); c.setReadTimeout(25_000);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] body = ("[{\"a\":\"g\",\"g\":1,\"p\":\"" + handle + "\"}]").getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(body.length);
        try (BufferedOutputStream out = new BufferedOutputStream(c.getOutputStream())) { out.write(body); }
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("Mega API respondió HTTP " + code);
        String json;
        try (InputStream in = c.getInputStream()) { json = read(in, 2 * 1024 * 1024); }
        finally { c.disconnect(); control.connection(null); }
        JSONArray array = new JSONArray(json);
        if (array.length() == 0 || !(array.get(0) instanceof JSONObject)) throw new IllegalStateException("Respuesta Mega no válida");
        JSONObject result = array.getJSONObject(0);
        if (result.has("e")) throw new IllegalStateException("Mega API error " + result.optInt("e"));
        return result;
    }

    static String read(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[32 * 1024]; int total = 0, count;
        while ((count = input.read(buffer)) != -1) { total += count; if (total > limit) throw new IllegalStateException("Respuesta demasiado grande"); out.write(buffer, 0, count); }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static String decode(String value) { return text(value).replace("\\/", "/").replace("\\u002F", "/").replace("\\u003A", ":").replace("\\u0023", "#").replace("&amp;", "&"); }
    private static String text(String value) { return value == null ? "" : value; }
    private static final class Marker { final int position; final String language; Marker(int position,String language){this.position=position;this.language=language;} }
    private static final class Candidate { final int position; final String url; Candidate(int position,String url){this.position=position;this.url=url;} }
    static final class Link { final String original, handle, key; Link(String original, String handle, String key){this.original=original;this.handle=handle;this.key=key;} }
    static final class Cancelled extends Exception { }
}
