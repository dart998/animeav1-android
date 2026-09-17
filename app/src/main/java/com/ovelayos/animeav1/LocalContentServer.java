package com.ovelayos.animeav1;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

final class LocalContentServer {
    static final String HOST = "app.animeav1.local";
    static final String DOWNLOADS_URL = "https://" + HOST + "/downloads";
    static final String OFFLINE_URL = "https://" + HOST + "/offline";

    private LocalContentServer() {}

    static String playerUrl(String slug, int episode) {
        return "https://" + HOST + "/player?slug=" + Uri.encode(slug) + "&episode=" + episode;
    }

    static boolean isLocal(String url) {
        try { return HOST.equalsIgnoreCase(Uri.parse(url).getHost()); }
        catch (Exception e) { return false; }
    }

    static boolean isDownloads(String url) {
        try { Uri u = Uri.parse(url); return HOST.equalsIgnoreCase(u.getHost()) && "/downloads".equals(u.getPath()); }
        catch (Exception e) { return false; }
    }

    static boolean isOffline(String url) {
        try { Uri u = Uri.parse(url); return HOST.equalsIgnoreCase(u.getHost()) && "/offline".equals(u.getPath()); }
        catch (Exception e) { return false; }
    }

    static WebResourceResponse open(WebResourceRequest request, EpisodeStore store, boolean online) {
        Uri u = request.getUrl();
        if (!HOST.equalsIgnoreCase(u.getHost())) return null;
        String path = u.getPath() == null ? "/" : u.getPath();
        String html;
        if ("/downloads".equals(path) || "/downloads/".equals(path)) html = downloadsPage(store, online);
        else if ("/offline".equals(path) || "/offline/".equals(path)) html = offlinePage(store);
        else if ("/player".equals(path) || "/player/".equals(path)) html = playerPage(store, u);
        else html = errorPage("Página local no encontrada");
        return html(html);
    }

    private static WebResourceResponse html(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Content-Length", Integer.toString(bytes.length));
        return new WebResourceResponse("text/html", "utf-8", 200, "OK", headers, new ByteArrayInputStream(bytes));
    }

    private static String shell(String title, String body, boolean swipe) {
        return "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\"><title>" + esc(title) + "</title>" +
                "<style>:root{color-scheme:dark}*{box-sizing:border-box}html,body{margin:0;min-height:100%;background:#100f14;color:#f5f5f7;font-family:system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif}body{padding:20px 16px 30px}main{max-width:850px;margin:0 auto}.brand{font-weight:800;font-size:18px;color:#21ead8;letter-spacing:.2px}.title{font-size:28px;margin:14px 0 8px}.muted{color:#9b9cab}.notice{padding:13px 14px;background:#18171f;border:1px solid #2d2b38;border-radius:14px;margin:14px 0}.offline{border-color:#665b2d}.toolbar{display:flex;gap:9px;flex-wrap:wrap;margin:16px 0}.btn{appearance:none;border:1px solid #3a3945;background:#201f28;color:#f5f5f7;border-radius:11px;padding:10px 13px;font-weight:650;text-decoration:none;display:inline-flex;align-items:center;justify-content:center}.btn.primary{background:#21ead8;border-color:#21ead8;color:#071311}.btn.danger{border-color:#673b43;color:#ffb7bf}.btn:active{transform:scale(.98)}.list{display:grid;gap:11px}.card{background:#18171f;border:1px solid #2a2933;border-radius:15px;padding:14px}.card h3{font-size:16px;margin:0 0 5px}.meta{font-size:13px;color:#9b9cab;display:flex;gap:8px;flex-wrap:wrap}.state{font-weight:700;color:#d6d6dc}.state.completed{color:#21ead8}.state.error{color:#ff909b}.progress{height:6px;background:#2c2b35;border-radius:9px;overflow:hidden;margin:10px 0}.progress>i{display:block;height:100%;background:#21ead8}.actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:11px}.empty{text-align:center;padding:42px 16px;color:#9b9cab}.big-icon{font-size:46px;margin:30px 0 8px}.player{width:100%;background:#000;border-radius:14px;max-height:75vh}.back{margin-bottom:15px}</style></head><body><main>" + body + "</main>" +
                (swipe ? swipeScript() : "") + "</body></html>";
    }

    private static String downloadsPage(EpisodeStore store, boolean online) {
        List<EpisodeStore.DownloadRecord> rows = store.listAll();
        StringBuilder b = new StringBuilder();
        b.append("<div class=\"brand\">AnimeAV1</div><h1 class=\"title\">Descargas</h1>");
        if (!online) b.append("<div class=\"notice offline\">Estás sin conexión. Puedes reproducir y gestionar los episodios ya descargados. Las nuevas descargas y la sincronización requieren Internet.</div>");
        else b.append("<div class=\"notice\">Biblioteca local y cola de descargas.</div>");
        b.append("<div class=\"toolbar\">");
        if (online) b.append("<button class=\"btn primary\" onclick=\"AnimeAV1App.downloadAllUnwatched()\">Descargar no vistos</button>");
        b.append("<button class=\"btn\" onclick=\"location.reload()\">Actualizar</button></div>");
        if (rows.isEmpty()) {
            b.append("<div class=\"empty\"><div class=\"big-icon\">↓</div>No hay episodios en la biblioteca local.</div>");
        } else {
            b.append("<div class=\"list\">");
            for (EpisodeStore.DownloadRecord r : rows) b.append(downloadCard(r, online));
            b.append("</div>");
        }
        return shell("Descargas", b.toString(), true);
    }

    private static String downloadCard(EpisodeStore.DownloadRecord r, boolean online) {
        boolean exists = r.path != null && !r.path.isEmpty() && new File(r.path).isFile();
        String status = r.status == null ? "" : r.status;
        String stateText = statusText(status, exists);
        String stateClass = EpisodeStore.STATUS_COMPLETED.equals(status) && exists ? "completed" :
                (EpisodeStore.STATUS_ERROR.equals(status) || EpisodeStore.STATUS_CANCELLED.equals(status) ? "error" : "");
        long total = Math.max(0, r.totalBytes);
        long done = Math.max(0, r.bytes);
        int pct = total > 0 ? (int) Math.min(100, done * 100L / total) : 0;
        StringBuilder c = new StringBuilder();
        c.append("<article class=\"card\"><h3>").append(esc(displayTitle(r))).append(" · Episodio ").append(r.episode).append("</h3>");
        c.append("<div class=\"meta\"><span class=\"state ").append(stateClass).append("\">").append(esc(stateText)).append("</span>");
        if (done > 0 || total > 0) c.append("<span>").append(esc(size(done))).append(total > 0 ? " / " + esc(size(total)) : "").append("</span>");
        c.append("</div>");
        if (EpisodeStore.STATUS_DOWNLOADING.equals(status) || EpisodeStore.STATUS_RESOLVING.equals(status) || EpisodeStore.STATUS_PENDING.equals(status)) {
            c.append("<div class=\"progress\"><i style=\"width:").append(total > 0 ? pct : 8).append("%\"></i></div>");
        }
        if (r.error != null && !r.error.isEmpty() && (EpisodeStore.STATUS_ERROR.equals(status) || EpisodeStore.STATUS_CANCELLED.equals(status))) {
            c.append("<div class=\"meta\">").append(esc(r.error)).append("</div>");
        }
        c.append("<div class=\"actions\">");
        String args = js(r.slug) + "," + r.episode;
        if (EpisodeStore.STATUS_COMPLETED.equals(status) && exists) {
            c.append("<button class=\"btn primary\" onclick=\"AnimeAV1App.downloadAction('play',").append(args).append(")\">Ver</button>");
            c.append("<button class=\"btn danger\" onclick=\"AnimeAV1App.downloadAction('delete',").append(args).append(")\">Borrar</button>");
        } else if (EpisodeStore.STATUS_PENDING.equals(status) || EpisodeStore.STATUS_RESOLVING.equals(status) || EpisodeStore.STATUS_DOWNLOADING.equals(status)) {
            c.append("<button class=\"btn danger\" onclick=\"AnimeAV1App.downloadAction('cancel',").append(args).append(")\">Cancelar</button>");
        } else {
            if (online) c.append("<button class=\"btn primary\" onclick=\"AnimeAV1App.downloadAction('retry',").append(args).append(")\">Reintentar</button>");
            c.append("<button class=\"btn danger\" onclick=\"AnimeAV1App.downloadAction('delete-record',").append(args).append(")\">Eliminar</button>");
        }
        c.append("</div></article>");
        return c.toString();
    }

    private static String offlinePage(EpisodeStore store) {
        int completed = 0;
        for (EpisodeStore.DownloadRecord r : store.listCompleted()) if (r.path != null && !r.path.isEmpty() && new File(r.path).isFile()) completed++;
        String body = "<div class=\"brand\">AnimeAV1</div><div class=\"big-icon\">⌁</div><h1 class=\"title\">Estás sin conexión</h1>" +
                "<div class=\"notice offline\">AnimeAV1 no está disponible ahora. Solo puedes acceder a los episodios que tienes descargados.</div>" +
                "<p class=\"muted\">Tienes " + completed + " episodio" + (completed == 1 ? "" : "s") + " disponible" + (completed == 1 ? "" : "s") + " offline.</p>" +
                "<div class=\"toolbar\"><button class=\"btn primary\" onclick=\"AnimeAV1App.openDownloads()\">Abrir Descargas</button>" +
                "<button class=\"btn\" onclick=\"AnimeAV1App.retryConnection()\">Reintentar conexión</button></div>";
        return shell("Sin conexión", body, false);
    }

    private static String playerPage(EpisodeStore store, Uri uri) {
        String slug = uri.getQueryParameter("slug");
        int ep;
        try { ep = Integer.parseInt(String.valueOf(uri.getQueryParameter("episode"))); }
        catch (Exception e) { return errorPage("Episodio no válido"); }
        EpisodeStore.DownloadRecord r = slug == null ? null : store.get(slug, ep);
        if (r == null || !EpisodeStore.STATUS_COMPLETED.equals(r.status) || r.path == null || !new File(r.path).isFile()) return errorPage("El fichero local ya no está disponible");
        String video = OfflineVideoServer.urlFor(slug, ep);
        String body = "<button class=\"btn back\" onclick=\"history.back()\">← Volver</button><div class=\"brand\">AnimeAV1 offline</div>" +
                "<h1 class=\"title\">" + esc(displayTitle(r)) + " · Episodio " + ep + "</h1>" +
                "<video class=\"player\" controls autoplay playsinline preload=\"metadata\" src=\"" + escAttr(video) + "\"></video>" +
                "<p class=\"muted\">Reproduciendo el fichero almacenado en el dispositivo.</p>";
        return shell("Episodio " + ep, body, false);
    }

    private static String errorPage(String message) {
        return shell("AnimeAV1", "<div class=\"brand\">AnimeAV1</div><h1 class=\"title\">No disponible</h1><div class=\"notice\">" + esc(message) + "</div><button class=\"btn\" onclick=\"history.back()\">Volver</button>", false);
    }

    private static String statusText(String status, boolean fileExists) {
        if (EpisodeStore.STATUS_PENDING.equals(status)) return "En cola";
        if (EpisodeStore.STATUS_RESOLVING.equals(status)) return "Resolviendo";
        if (EpisodeStore.STATUS_DOWNLOADING.equals(status)) return "Descargando";
        if (EpisodeStore.STATUS_COMPLETED.equals(status)) return fileExists ? "Disponible offline" : "Fichero ausente";
        if (EpisodeStore.STATUS_CANCELLED.equals(status)) return "Cancelada";
        if (EpisodeStore.STATUS_ERROR.equals(status)) return "Error";
        return status.isEmpty() ? "Desconocido" : status;
    }

    private static String displayTitle(EpisodeStore.DownloadRecord r) {
        String t = r.title == null ? "" : r.title.trim();
        if (!t.isEmpty()) return t;
        String s = r.slug == null ? "Anime" : r.slug.replace('-', ' ').replace('_', ' ');
        if (s.isEmpty()) return "Anime";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes / 1024.0;
        if (v < 1024) return String.format(Locale.US, "%.1f KB", v);
        v /= 1024.0;
        if (v < 1024) return String.format(Locale.US, "%.1f MB", v);
        return String.format(Locale.US, "%.2f GB", v / 1024.0);
    }

    private static String swipeScript() {
        return "<script>(function(){var sx=0,sy=0,ok=false;document.addEventListener('touchstart',function(e){ok=false;if(!e.touches||e.touches.length!==1)return;var t=e.target;if(t&&t.closest&&t.closest('video,iframe,input,textarea,select,button,a,[contenteditable]'))return;sx=e.touches[0].clientX;sy=e.touches[0].clientY;ok=true;},{passive:true});document.addEventListener('touchend',function(e){if(!ok||!e.changedTouches||e.changedTouches.length!==1)return;ok=false;var dx=e.changedTouches[0].clientX-sx,dy=e.changedTouches[0].clientY-sy;if(Math.abs(dx)<90||Math.abs(dx)<Math.abs(dy)*1.5||Math.abs(dy)>90)return;AnimeAV1App.swipe(dx<0?1:-1);},{passive:true});})();</script>";
    }

    private static String js(String value) {
        if (value == null) value = "";
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "\\n") + "'";
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escAttr(String value) { return esc(value); }
}
