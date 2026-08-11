package com.fscrates.client.media;

import com.fscrates.FSCrates;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.stream.Stream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Media por defecto que viene empaquetada dentro del JAR en
 * assets/fscrates/media/.
 *
 * Se extrae una sola vez a .minecraft/fscrates/cache/defaults/ porque el
 * decodificador de video necesita un archivo con acceso aleatorio (no un stream).
 *
 * OJO: estos archivos solo se usan cuando la crate NO tiene media propia.
 * Nunca sirven de sustituto de una media personalizada que fallo.
 */
public final class DefaultMedia {
    /**
     * Musica por defecto. Es lo UNICO que trae el mod.
     *
     * Antes venia tambien un video de ejemplo, y con el una imagen fija para
     * tapar el rato que tardaba en arrancar. Se han quitado los dos: eran 76 MB
     * de los 84 que pesaba el mod, para un video que cualquiera va a sustituir
     * por el suyo. Sin video propio la escena queda con fondo negro, que es lo
     * mismo que ya pasaba mientras cargaba uno personalizado.
     */
    private static final String MUSIC = "default_music.mp3";

    private DefaultMedia() {
    }

    private static Path defaultsFolder() {
        return MediaCache.cacheRoot().resolve("defaults");
    }

    /**
     * Deja la musica ya extraida en disco, en segundo plano.
     *
     * Se llama al arrancar el cliente para que la PRIMERA apertura de una caja no
     * tenga que descomprimirla justo en ese momento.
     */
    public static void warmUp() {
        Thread thread = new Thread(() -> {
            try {
                music();
                FSCrates.LOGGER.info("[FSCrates] Musica por defecto lista en cache.");
            } catch (Throwable t) {
                FSCrates.LOGGER.warn("[FSCrates] No se pudo precargar la musica por defecto: {}", t.toString());
            }
        }, "FSCrates-MediaWarmup");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    public static List<Path> music() {
        List<Path> out = new ArrayList<>();
        Path track = extract(MUSIC);
        if (track != null) {
            out.add(track);
        }
        return out;
    }

    /**
     * Extrae un recurso empaquetado al disco (si no esta ya) y devuelve su ruta.
     * Devuelve null si el recurso no existe en el JAR.
     */
    public static synchronized Path extract(String name) {
        try {
            // El nombre en disco lleva el tamano del recurso empaquetado.
            //
            // Antes se guardaba con el nombre pelado y se salia antes de tiempo si
            // el archivo ya existia, asi que al actualizar el mod se seguia usando
            // el video viejo del cache para siempre: los cambios de calidad no
            // llegaban nunca. Con el tamano en el nombre, un video nuevo es un
            // archivo nuevo, y las versiones viejas se borran solas.
            long size = resourceSize(name);
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String extension = dot > 0 ? name.substring(dot) : "";
            String versioned = base + "_" + (size > 0 ? Long.toString(size) : "x") + extension;

            Path target = defaultsFolder().resolve(versioned);
            if (MediaCache.isReady(target)) {
                return target;
            }

            Files.createDirectories(target.getParent());
            cleanOldVersions(base, extension, versioned);

            try (InputStream in = open(name)) {
                if (in == null) {
                    FSCrates.LOGGER.warn("[FSCrates] No se encontro la media por defecto '{}' dentro del JAR.", name);
                    return null;
                }
                Path part = target.resolveSibling(versioned + ".part");
                Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            }

            FSCrates.LOGGER.info("[FSCrates] Media por defecto extraida: {} ({} bytes)", target.getFileName(), size);
            return MediaCache.isReady(target) ? target : null;
        } catch (Exception e) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo extraer la media por defecto '{}': {}", name, e.toString());
            return null;
        }
    }

    /** Tamano del recurso dentro del JAR, o -1 si no se puede saber. */
    private static long resourceSize(String name) {
        try {
            URL url = DefaultMedia.class.getResource("/assets/fscrates/media/" + name);
            if (url != null) {
                URLConnection connection = url.openConnection();
                long length = connection.getContentLengthLong();
                try {
                    connection.getInputStream().close();
                } catch (IOException ignored) {
                }
                if (length > 0L) {
                    return length;
                }
            }
        } catch (Exception ignored) {
        }

        // Sin cabecera de tamano: se cuenta leyendo, que sigue siendo fiable.
        try (InputStream in = open(name)) {
            if (in == null) {
                return -1L;
            }
            long total = 0L;
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
            }
            return total;
        } catch (Exception e) {
            return -1L;
        }
    }

    /** Borra versiones anteriores del mismo recurso para no dejar basura. */
    private static void cleanOldVersions(String base, String extension, String keep) {
        // Primero se recoge la lista y despues se borra: borrar mientras se recorre
        // el directorio puede saltarse entradas.
        List<Path> stale = new ArrayList<>();
        try (Stream<Path> files = Files.list(defaultsFolder())) {
            files.filter(p -> {
                    String fileName = p.getFileName().toString();
                    // Versiones anteriores y tambien el archivo sin version de las
                    // primeras builds, que es el que se quedaba pegado para siempre.
                    boolean versioned = fileName.startsWith(base + "_") && fileName.endsWith(extension);
                    boolean legacy = fileName.equals(base + extension);
                    return (versioned || legacy) && !fileName.equals(keep);
                })
                .forEach(stale::add);
        } catch (IOException ignored) {
            return;
        }

        for (Path path : stale) {
            try {
                Files.deleteIfExists(path);
                FSCrates.LOGGER.info("[FSCrates] Borrada media por defecto obsoleta: {}", path.getFileName());
            } catch (IOException ignored) {
            }
        }
    }

    /** Busca el recurso primero en el classpath del mod y si no, via ResourceManager. */
    private static InputStream open(String name) throws IOException {
        InputStream in = DefaultMedia.class.getResourceAsStream("/assets/fscrates/media/" + name);
        if (in != null) {
            return in;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getResourceManager() != null) {
            Optional<Resource> res = mc.getResourceManager().getResource(new ResourceLocation("fscrates", "media/" + name));
            if (res.isPresent()) {
                return res.get().open();
            }
        }
        return null;
    }
}
