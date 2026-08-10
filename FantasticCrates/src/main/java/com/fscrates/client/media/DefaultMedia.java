package com.fscrates.client.media;

import com.fscrates.FSCrates;
import java.io.IOException;
import java.io.InputStream;
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
    /** Video por defecto: H.264/MP4 1600x900 a 24 fps. */
    private static final String VIDEO = "default_dungeons.mp4";
    /** Musica por defecto. */
    private static final String MUSIC = "default_music.mp3";
    /** Imagen mostrada mientras el primer fotograma del video aun no esta listo. */
    private static final String POSTER = "default_poster.png";

    private DefaultMedia() {
    }

    private static Path defaultsFolder() {
        return MediaCache.cacheRoot().resolve("defaults");
    }

    public static List<Path> videos() {
        List<Path> out = new ArrayList<>();
        Path video = extract(VIDEO);
        if (video != null) {
            out.add(video);
        }
        return out;
    }

    public static List<Path> music() {
        List<Path> out = new ArrayList<>();
        Path track = extract(MUSIC);
        if (track != null) {
            out.add(track);
        }
        return out;
    }

    public static Path poster() {
        return extract(POSTER);
    }

    /**
     * Extrae un recurso empaquetado al disco (si no esta ya) y devuelve su ruta.
     * Devuelve null si el recurso no existe en el JAR.
     */
    public static synchronized Path extract(String name) {
        Path target = defaultsFolder().resolve(name);
        try {
            if (MediaCache.isReady(target)) {
                return target;
            }

            Files.createDirectories(target.getParent());
            try (InputStream in = open(name)) {
                if (in == null) {
                    FSCrates.LOGGER.warn("[FSCrates] No se encontro la media por defecto '{}' dentro del JAR.", name);
                    return null;
                }
                Path part = target.resolveSibling(name + ".part");
                Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            }

            FSCrates.LOGGER.info("[FSCrates] Media por defecto extraida: {}", target.getFileName());
            return MediaCache.isReady(target) ? target : null;
        } catch (Exception e) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo extraer la media por defecto '{}': {}", name, e.toString());
            return null;
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
