package com.fscrates.client.media;

import com.fscrates.FSCrates;
import com.fscrates.config.CrateConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Sesion de media de la pantalla de pre-apertura: elige que video y que cancion
 * toca, se encarga de la descarga/cache y mantiene vivos los reproductores.
 *
 * Es estatica a proposito: solo puede haber una pre-apertura abierta a la vez y
 * asi la musica no se corta al abrir el pool de recompensas encima, ni al
 * redimensionar la ventana (que vuelve a llamar a init()).
 *
 * Regla de oro: si la crate tiene media propia se usa SOLO la suya. La media por
 * defecto del mod nunca sustituye a una personalizada que haya fallado.
 */
public final class CrateMedia {
    private static boolean active;
    private static boolean usingDefaults;

    private static CompletableFuture<Path> videoFuture;
    private static CompletableFuture<Path> musicFuture;

    private static VideoPlayer video;
    private static MusicPlayer music;
    private static VideoPlayer poster;

    private static float volume = 1.0F;

    private CrateMedia() {
    }

    // ------------------------------------------------------------------ arranque

    /** Empieza una sesion nueva para la crate indicada. */
    public static synchronized void begin(CrateConfig config) {
        stop();
        active = true;

        List<String> videoUrls = validUrls(config == null ? null : config.videos);
        List<String> musicUrls = validUrls(config == null ? null : config.music);

        // El video y la musica se deciden por separado. Antes bastaba con
        // configurar UNA de las dos para que la otra se quedara en nada: si
        // ponias tu video y dejabas la musica vacia, la escena iba en silencio.
        // Lo vacio significa "usa la del mod", no "que no suene"; para el
        // silencio esta el boton de volumen.
        boolean customVideo = !videoUrls.isEmpty();
        boolean customMusic = !musicUrls.isEmpty();

        // El poster es la imagen fija del video del mod, y solo vale mientras
        // carga ese video. Con un video propio no se pinta nada de fondo hasta
        // que llega el suyo, para no colar el del mod ni un instante.
        usingDefaults = !customVideo;

        FSCrates.LOGGER.info(
            "[FSCrates] Media de la escena: video {}, musica {}.",
            customVideo ? videoUrls.size() + " propio(s)" : "el del mod",
            customMusic ? musicUrls.size() + " propia(s)" : "la del mod"
        );

        videoFuture = customVideo
            ? MediaCache.obtain(pick(videoUrls), MediaCache.Kind.VIDEO)
            : CompletableFuture.supplyAsync(() -> pickPath(DefaultMedia.videos()));
        musicFuture = customMusic
            ? MediaCache.obtain(pick(musicUrls), MediaCache.Kind.MUSIC)
            : CompletableFuture.supplyAsync(() -> pickPath(DefaultMedia.music()));
    }

    /** Crea los reproductores en cuanto sus archivos estan listos. */
    public static synchronized void ensurePlayers() {
        if (!active) {
            return;
        }

        if (video == null && videoFuture != null && videoFuture.isDone()) {
            Path file = resolve(videoFuture, "video");
            videoFuture = null;
            if (file != null) {
                video = new VideoPlayer(file);
                video.start();
            }
        }

        if (music == null && musicFuture != null && musicFuture.isDone()) {
            Path file = resolve(musicFuture, "musica");
            musicFuture = null;
            if (file != null) {
                music = new MusicPlayer(file);
                music.setVolume(volume);
                music.start();
            }
        }
    }

    private static Path resolve(CompletableFuture<Path> future, String what) {
        try {
            Path file = future.getNow(null);
            if (file == null) {
                FSCrates.LOGGER.warn("[FSCrates] No hay {} disponible para la pantalla de pre-apertura.", what);
            }
            return file;
        } catch (Exception e) {
            FSCrates.LOGGER.error("[FSCrates] Fallo al preparar la {}: {}", what, e.toString());
            return null;
        }
    }

    // -------------------------------------------------------------------- estado

    /** true mientras se este descargando/extrayendo algo (muestra la pantallita de carga). */
    public static boolean isLoading() {
        return active
            && ((videoFuture != null && !videoFuture.isDone()) || (musicFuture != null && !musicFuture.isDone()));
    }

    public static boolean isActive() {
        return active;
    }

    /** true si esta sesion usa la media del mod (la crate no tiene media propia). */
    public static boolean isUsingDefaults() {
        return usingDefaults;
    }

    /** Volumen lineal ya calculado (0 = mute). */
    public static synchronized void applyVolume(float linear) {
        volume = Math.max(0.0F, linear);
        if (music != null) {
            music.setVolume(volume);
        }
    }

    // -------------------------------------------------------------------- render

    /**
     * Dibuja el fondo. Si no hay video (o fallo) deja el fondo negro, tal y como
     * pide la especificacion.
     */
    public static void renderBackground(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, -16777216);

        ensurePlayers();

        if (video != null && !video.hasFailed()) {
            if (video.hasPicture()) {
                video.render(g, width, height, 1.0F);
                return;
            }
        }

        // Solo con la media por defecto usamos el poster mientras carga el video.
        if (usingDefaults) {
            renderPoster(g, width, height);
        }
    }

    private static void renderPoster(GuiGraphics g, int width, int height) {
        if (poster == null) {
            Path file = DefaultMedia.poster();
            if (file == null) {
                return;
            }
            poster = new VideoPlayer(file);
            poster.start();
        }
        if (!poster.hasFailed()) {
            poster.render(g, width, height, 1.0F);
        }
    }

    // -------------------------------------------------------------------- cierre

    /** Para todo y libera texturas, hilos y memoria nativa. */
    public static synchronized void stop() {
        active = false;
        usingDefaults = false;
        videoFuture = null;
        musicFuture = null;

        if (video != null) {
            video.close();
            video = null;
        }
        if (poster != null) {
            poster.close();
            poster = null;
        }
        if (music != null) {
            music.close();
            music = null;
        }
    }

    // -------------------------------------------------------------------- utiles

    private static List<String> validUrls(List<String> source) {
        List<String> out = new ArrayList<>();
        if (source != null) {
            for (String url : source) {
                if (MediaCache.isValidUrl(url)) {
                    out.add(url.trim());
                }
            }
        }
        return out;
    }

    /** Elige un elemento al azar: cada apertura puede mostrar uno distinto. */
    private static String pick(List<String> options) {
        if (options.size() == 1) {
            return options.get(0);
        }
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

    private static Path pickPath(List<Path> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        if (options.size() == 1) {
            return options.get(0);
        }
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }
}
