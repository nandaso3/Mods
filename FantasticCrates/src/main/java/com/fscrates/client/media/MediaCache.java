package com.fscrates.client.media;

import com.fscrates.FSCrates;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.stream.Stream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

/**
 * Descarga y cachea en disco los archivos de media (videos y musica) que usa la
 * pantalla de pre-apertura.
 *
 * Replica el patron de red de Fantastic Pass (redirecciones seguidas a mano,
 * User-Agent de navegador y timeouts generosos) y le anade una cache persistente:
 *
 *   .minecraft/fscrates/cache/videos/&lt;hash_del_url&gt;.mp4
 *   .minecraft/fscrates/cache/music/&lt;hash_del_url&gt;.mp3
 *
 * Toda descarga ocurre en un hilo de fondo: nunca se bloquea el hilo de render.
 */
public final class MediaCache {
    private static final String USER_AGENT = "Mozilla/5.0 (FSCrates)";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int MAX_REDIRECTS = 6;

    /** Token de confirmacion que Google Drive pide para archivos grandes. */
    private static final Pattern DRIVE_CONFIRM = Pattern.compile("name=\"confirm\"\\s+value=\"([^\"]+)\"");
    private static final Pattern DRIVE_FORM_ACTION = Pattern.compile("action=\"([^\"]+)\"");
    private static final Pattern DRIVE_HIDDEN_INPUT = Pattern.compile("name=\"([^\"]+)\"\\s+value=\"([^\"]*)\"");

    private static final ExecutorService DOWNLOADER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "FSCrates-MediaDownload");
        t.setDaemon(true);
        return t;
    });

    /** Evita descargar dos veces el mismo url a la vez. */
    private static final Map<String, CompletableFuture<Path>> IN_FLIGHT = new ConcurrentHashMap<>();

    private MediaCache() {
    }

    public enum Kind {
        VIDEO("videos", ".mp4"),
        MUSIC("music", ".mp3");

        private final String folder;
        private final String defaultExtension;

        Kind(String folder, String defaultExtension) {
            this.folder = folder;
            this.defaultExtension = defaultExtension;
        }

        public String folder() {
            return this.folder;
        }

        public String defaultExtension() {
            return this.defaultExtension;
        }
    }

    /** Raiz de la cache: .minecraft/fscrates/cache */
    public static Path cacheRoot() {
        Minecraft mc = Minecraft.getInstance();
        Path base = (mc != null && mc.gameDirectory != null ? mc.gameDirectory.toPath() : Path.of("."));
        return base.resolve("fscrates").resolve("cache");
    }

    public static Path folderFor(Kind kind) {
        return cacheRoot().resolve(kind.folder());
    }

    /** Ruta en cache que le corresponde a un url (hash + extension deducida). */
    public static Path cacheFileFor(String url, Kind kind) {
        return folderFor(kind).resolve(hash(url) + extensionOf(url, kind));
    }

    /**
     * Busca en la cache cualquier archivo de este url, sea cual sea su extension.
     *
     * Se busca por hash y no por nombre completo a proposito: la extension se
     * decide al descargar segun el contenido real, asi que no se puede adivinar
     * desde el url. Si solo se mirase el nombre exacto, un PNG guardado como
     * .png no se encontraria y se volveria a descargar cada vez.
     */
    public static Path findCached(String url, Kind kind) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String prefix = hash(url.trim());
        Path exact = cacheFileFor(url.trim(), kind);
        if (isReady(exact)) {
            return exact;
        }

        Path folder = folderFor(kind);
        if (!Files.isDirectory(folder)) {
            return null;
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files
                .filter(p -> p.getFileName().toString().startsWith(prefix + "."))
                .filter(p -> !p.getFileName().toString().endsWith(".part"))
                .filter(MediaCache::isReady)
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** Un archivo en cache es valido si existe y tiene tamano &gt; 0. */
    public static boolean isReady(Path file) {
        try {
            return file != null && Files.isRegularFile(file) && Files.size(file) > 0L;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isCached(String url, Kind kind) {
        return findCached(url, kind) != null;
    }

    /**
     * Devuelve el archivo local para un url. Si ya esta en cache el future
     * viene ya completado (arranque instantaneo, sin pantalla de carga).
     * Si no, la descarga corre en un hilo de fondo.
     */
    public static CompletableFuture<Path> obtain(String url, Kind kind) {
        if (url == null || url.isBlank()) {
            return CompletableFuture.failedFuture(new IOException("url vacio"));
        }

        final String clean = url.trim();

        // Ya esta en disco: se usa tal cual, sin volver a descargar nunca.
        Path cached = findCached(clean, kind);
        if (cached != null) {
            FSCrates.LOGGER.debug("[FSCrates] Media ya en cache, no se descarga: {}", cached.getFileName());
            return CompletableFuture.completedFuture(cached);
        }

        String key = kind.name() + "|" + clean;
        return IN_FLIGHT.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
            try {
                Path existing = findCached(clean, kind);
                if (existing != null) {
                    return existing;
                }
                Path target = downloadTo(clean, cacheFileFor(clean, kind));
                FSCrates.LOGGER.info(
                    "[FSCrates] Media descargada y guardada en cache: {} ({})",
                    target.getFileName(),
                    sniff(target)
                );
                return target;
            } catch (Exception e) {
                FSCrates.LOGGER.error("[FSCrates] No se pudo descargar la media '{}': {}", clean, e.toString());
                throw new java.util.concurrent.CompletionException(e);
            } finally {
                IN_FLIGHT.remove(k);
            }
        }, DOWNLOADER));
    }

    /**
     * Descarga atomica: escribe a un .part, mira que tipo de archivo es de
     * verdad y lo renombra con la extension correcta.
     *
     * @return la ruta definitiva en cache
     */
    private static Path downloadTo(String url, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");

        try (InputStream in = openStream(url); OutputStream out = Files.newOutputStream(part)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }

        if (Files.size(part) <= 0L) {
            Files.deleteIfExists(part);
            throw new IOException("la descarga quedo vacia");
        }

        // La extension real se decide por el contenido, no por el url.
        MediaType type = sniff(part);
        Path finalTarget = target;
        if (type != MediaType.UNKNOWN && !target.getFileName().toString().endsWith(type.extension())) {
            String name = target.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            finalTarget = target.resolveSibling(base + type.extension());
        }

        Files.move(part, finalTarget, StandardCopyOption.REPLACE_EXISTING);
        return finalTarget;
    }

    /**
     * Abre un stream siguiendo redirecciones a mano (igual que Fantastic Pass) y
     * resolviendo la pagina intermedia de confirmacion de Google Drive.
     */
    static InputStream openStream(String url) throws IOException {
        String current = url.trim();
        String cookies = "";

        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection conn = null;

            for (int i = 0; i < MAX_REDIRECTS; i++) {
                URL u = new URL(current);
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                if (!cookies.isEmpty()) {
                    conn.setRequestProperty("Cookie", cookies);
                }
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);

                int code = conn.getResponseCode();
                cookies = mergeCookies(cookies, conn.getHeaderFields());

                if (code / 100 == 3) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (location == null) {
                        throw new IOException("redireccion sin cabecera Location");
                    }
                    current = new URL(u, location).toString();
                    continue;
                }

                if (code / 100 != 2) {
                    conn.disconnect();
                    throw new IOException("HTTP " + code + " al descargar " + current);
                }

                break;
            }

            if (conn == null) {
                throw new IOException("demasiadas redirecciones");
            }

            String type = conn.getContentType();
            boolean html = type != null && type.toLowerCase(Locale.ROOT).contains("text/html");
            if (!html) {
                return conn.getInputStream();
            }

            // Google Drive devolvio la pagina de "el archivo es muy grande":
            // buscamos el formulario de confirmacion y reintentamos una vez.
            String body = readLimited(conn.getInputStream(), 512 * 1024);
            conn.disconnect();
            String next = resolveDriveConfirm(current, body);
            if (next == null) {
                throw new IOException("el servidor devolvio HTML en vez del archivo (revisa que el link sea de descarga directa)");
            }
            current = next;
        }

        throw new IOException("no se pudo obtener el archivo tras la confirmacion de Google Drive");
    }

    /** Construye el url de descarga real a partir del formulario de confirmacion de Drive. */
    private static String resolveDriveConfirm(String pageUrl, String html) {
        try {
            Matcher form = DRIVE_FORM_ACTION.matcher(html);
            if (form.find()) {
                String action = unescapeHtml(form.group(1));
                StringBuilder sb = new StringBuilder(new URL(new URL(pageUrl), action).toString());
                boolean first = !sb.toString().contains("?");
                Matcher hidden = DRIVE_HIDDEN_INPUT.matcher(html);
                while (hidden.find()) {
                    sb.append(first ? '?' : '&').append(hidden.group(1)).append('=').append(unescapeHtml(hidden.group(2)));
                    first = false;
                }
                return sb.toString();
            }

            Matcher confirm = DRIVE_CONFIRM.matcher(html);
            if (confirm.find()) {
                String sep = pageUrl.contains("?") ? "&" : "?";
                return pageUrl + sep + "confirm=" + confirm.group(1);
            }

            if (pageUrl.contains("drive.google.com") && !pageUrl.contains("confirm=")) {
                String sep = pageUrl.contains("?") ? "&" : "?";
                return pageUrl + sep + "confirm=t";
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String unescapeHtml(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'");
    }

    private static String mergeCookies(String existing, Map<String, List<String>> headers) {
        StringBuilder sb = new StringBuilder(existing);
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() != null && "set-cookie".equalsIgnoreCase(e.getKey())) {
                for (String raw : e.getValue()) {
                    String pair = raw.split(";", 2)[0];
                    if (!pair.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append("; ");
                        }
                        sb.append(pair.trim());
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String readLimited(InputStream in, int max) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            int n;
            while (out.size() < max && (n = stream.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /** Tipo real de un archivo, deducido de su contenido. */
    public enum MediaType {
        MP4(".mp4", true),
        WEBM(".webm", true),
        PNG(".png", true),
        JPEG(".jpg", true),
        GIF(".gif", true),
        MP3(".mp3", false),
        OGG(".ogg", false),
        WAV(".wav", false),
        UNKNOWN("", false);

        private final String extension;
        private final boolean visual;

        MediaType(String extension, boolean visual) {
            this.extension = extension;
            this.visual = visual;
        }

        public String extension() {
            return this.extension;
        }

        /** true si es video o imagen (va al fondo de la pantalla). */
        public boolean isVisual() {
            return this.visual;
        }

        public boolean isImage() {
            return this == PNG || this == JPEG || this == GIF;
        }
    }

    /**
     * Detecta el tipo por los bytes de cabecera (numeros magicos).
     *
     * Hace falta porque los links directos de Google Drive no llevan extension:
     * por el url es imposible saber si lo que viene es un MP4 o un PNG.
     */
    public static MediaType sniff(Path file) {
        if (file == null) {
            return MediaType.UNKNOWN;
        }

        byte[] head = new byte[32];
        int read;
        try (InputStream in = Files.newInputStream(file)) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return MediaType.UNKNOWN;
        }
        if (read < 12) {
            return MediaType.UNKNOWN;
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (head[0] == (byte) 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return MediaType.PNG;
        }
        // JPEG: FF D8 FF
        if (head[0] == (byte) 0xFF && head[1] == (byte) 0xD8 && head[2] == (byte) 0xFF) {
            return MediaType.JPEG;
        }
        // GIF87a / GIF89a
        if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F') {
            return MediaType.GIF;
        }
        // Matroska / WebM: 1A 45 DF A3
        if (head[0] == (byte) 0x1A && head[1] == (byte) 0x45 && head[2] == (byte) 0xDF && head[3] == (byte) 0xA3) {
            return MediaType.WEBM;
        }
        // MP4 / MOV: "....ftyp"
        if (head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
            return MediaType.MP4;
        }
        // OGG: "OggS"
        if (head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') {
            return MediaType.OGG;
        }
        // WAV: "RIFF....WAVE"
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
            && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E') {
            return MediaType.WAV;
        }
        // MP3: "ID3" o sincronismo de frame FF Ex/Fx
        if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') {
            return MediaType.MP3;
        }
        if (head[0] == (byte) 0xFF && (head[1] & 0xE0) == 0xE0) {
            return MediaType.MP3;
        }

        return MediaType.UNKNOWN;
    }

    /** Extension deducida del url; si no se reconoce se usa la del tipo de media. */
    public static String extensionOf(String url, Kind kind) {
        try {
            String path = new URI(url).getPath();
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot >= 0 && dot < path.length() - 1) {
                    String ext = path.substring(dot).toLowerCase(Locale.ROOT);
                    if (ext.length() <= 6 && ext.matches("\\.[a-z0-9]+")) {
                        return ext;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Muchos links directos (Drive) no llevan extension: la deducimos del
        // parametro del propio url si se puede, o caemos al valor por defecto.
        String lower = url.toLowerCase(Locale.ROOT);
        for (String ext : kind == Kind.VIDEO
                ? new String[]{".mp4", ".webm", ".mov", ".m4v", ".png", ".jpg", ".jpeg"}
                : new String[]{".mp3", ".ogg", ".wav"}) {
            if (lower.contains(ext)) {
                return ext;
            }
        }
        return kind.defaultExtension();
    }

    public static String hash(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(url.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.trim().hashCode());
        }
    }

    public static boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String scheme = new URI(url.trim()).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (Exception e) {
            return false;
        }
    }
}
