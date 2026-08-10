package com.fscrates.client.media;

import com.fscrates.FSCrates;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;
import org.lwjgl.system.MemoryUtil;

/**
 * Reproductor de video en bucle para el fondo de la pantalla de pre-apertura.
 *
 * Formatos soportados:
 *  - .mp4 / .mov / .m4v con video H.264  -> video real, decodificado con JCodec (Java puro)
 *  - .png / .jpg / .jpeg                 -> imagen estatica
 *  - .webm                               -> NO soportado (VP8/VP9 no se puede decodificar en Java puro)
 *
 * Detalles que importan:
 *
 * 1) REORDEN POR TIMESTAMP. JCodec entrega los fotogramas en orden de
 *    DECODIFICACION, no de reproduccion. Con B-frames los timestamps salen
 *    desordenados (0.000, 0.125, 0.042, 0.083, ...) y el video parece
 *    "teletransportarse" hacia atras. Aqui se meten en una ventana ordenada por
 *    timestamp y se van sacando siempre en orden correcto.
 *
 * 2) SIN BASURA POR FOTOGRAMA. En vez de Picture.cloneCropped() (que asigna
 *    memoria nueva en cada fotograma, unos 50 MB/s) se copian los planos a
 *    objetos YuvFrame reutilizados de un pool.
 *
 * 3) CONVERSION RAPIDA. YUV -> RGBA se escribe en un int[] y se vuelca al
 *    ByteBuffer de una sola vez (asIntBuffer().put), con las filas repartidas
 *    entre varios hilos. Escribir byte a byte serian 8 millones de llamadas por
 *    fotograma a 1080p.
 */
public final class VideoPlayer implements AutoCloseable {
    /** Fotogramas RGBA listos para subir a la GPU. */
    private static final int READY_CAPACITY = 3;
    /**
     * Ventana de reordenado. Tiene que ser mayor que la distancia maxima de
     * reordenado del H.264 (con B-frames suele ser 2-4).
     */
    private static final int REORDER_WINDOW = 8;
    private static final double DEFAULT_FRAME_SECONDS = 1.0 / 24.0;
    /** Si nos retrasamos mas que esto, resincronizamos en vez de acelerar. */
    private static final double MAX_DRIFT_SECONDS = 0.4;

    private static int textureSequence;
    private static ForkJoinPool convertPool;

    private final Path file;
    private final boolean staticImage;
    private final boolean unsupported;

    private final BlockingQueue<RgbaFrame> ready = new ArrayBlockingQueue<>(READY_CAPACITY);
    private final BlockingQueue<RgbaFrame> recycled = new ArrayBlockingQueue<>(READY_CAPACITY + 2);
    /** Pool de fotogramas YUV para no asignar memoria en cada vuelta. */
    private final Deque<YuvFrame> yuvPool = new ArrayDeque<>();

    private Thread decoderThread;
    private volatile boolean stopped;
    private volatile boolean failed;
    /** Buffer de trabajo de la conversion. Solo lo toca el hilo decodificador. */
    private int[] scratch;

    private VideoTexture texture;
    private ResourceLocation textureId;
    private RgbaFrame current;

    // Reloj de reproduccion: el fotograma con timestamp ts se muestra en
    // baseNanos + (ts - baseTimestamp).
    private long baseNanos;
    private double baseTimestamp;
    private boolean clockStarted;

    /** Fotograma YUV con planos compactos (stride == ancho). */
    private static final class YuvFrame {
        byte[] luma;
        byte[] cb;
        byte[] cr;
        int width;
        int height;
        int chromaWidth;
        int chromaHeight;
        double timestamp;
        double duration;
    }

    /** Fotograma ya convertido a RGBA en memoria nativa. */
    private record RgbaFrame(ByteBuffer pixels, int width, int height, double timestamp, double duration) {
    }

    public VideoPlayer(Path file) {
        this.file = file;
        String name = file == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        this.staticImage = name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
        this.unsupported = name.endsWith(".webm") || name.endsWith(".mkv");

        if (this.unsupported) {
            this.failed = true;
            FSCrates.LOGGER.warn(
                "[FSCrates] El formato WEBM no se puede reproducir (VP8/VP9 no tiene decodificador en Java puro). "
                    + "Convierte '{}' a MP4 (H.264) para usarlo como video de crate.",
                name
            );
        }
    }

    public void start() {
        if (this.decoderThread != null || this.failed || this.file == null) {
            return;
        }

        this.decoderThread = new Thread(this.staticImage ? this::runImage : this::runVideo, "FSCrates-VideoDecode");
        this.decoderThread.setDaemon(true);
        this.decoderThread.setPriority(Thread.NORM_PRIORITY - 1);
        this.decoderThread.start();
    }

    public boolean hasFailed() {
        return this.failed;
    }

    public boolean hasPicture() {
        return this.current != null || !this.ready.isEmpty();
    }

    // ------------------------------------------------------------------ render

    /** Dibuja el fotograma actual cubriendo el area (estilo "cover"). */
    public void render(GuiGraphics g, int areaWidth, int areaHeight, float alpha) {
        if (this.failed) {
            return;
        }

        this.advanceFrame();
        if (this.current == null || this.texture == null || !this.texture.hasFrame()) {
            return;
        }

        int fw = this.current.width();
        int fh = this.current.height();
        float scale = Math.max((float) areaWidth / fw, (float) areaHeight / fh);
        int dw = Math.max(1, Math.round(fw * scale));
        int dh = Math.max(1, Math.round(fh * scale));
        int x = (areaWidth - dw) / 2;
        int y = (areaHeight - dh) / 2;

        g.setColor(1.0F, 1.0F, 1.0F, Math.max(0.0F, Math.min(1.0F, alpha)));
        g.blit(this.textureId, x, y, dw, dh, 0.0F, 0.0F, fw, fh, fw, fh);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Muestra el siguiente fotograma cuando toca segun su timestamp.
     *
     * Nunca retrocede: si el decodificador no da abasto simplemente se mantiene
     * el fotograma actual y el reloj se reajusta, asi la degradacion es suave.
     */
    private void advanceFrame() {
        if (this.staticImage) {
            if (this.current == null) {
                RgbaFrame first = this.ready.poll();
                if (first != null) {
                    this.show(first);
                }
            }
            return;
        }

        long now = System.nanoTime();

        while (true) {
            RgbaFrame next = this.ready.peek();
            if (next == null) {
                return;
            }

            if (!this.clockStarted) {
                this.clockStarted = true;
                this.baseNanos = now;
                this.baseTimestamp = next.timestamp();
            }

            // Al volver a empezar el bucle el timestamp baja: reanclamos el reloj.
            if (next.timestamp() < this.baseTimestamp) {
                this.baseNanos = now;
                this.baseTimestamp = next.timestamp();
            }

            long dueAt = this.baseNanos + (long) ((next.timestamp() - this.baseTimestamp) * 1_000_000_000.0);
            if (now < dueAt) {
                return;
            }

            // Si vamos muy retrasados, reancla para no encadenar saltos.
            if (now - dueAt > (long) (MAX_DRIFT_SECONDS * 1_000_000_000.0)) {
                this.baseNanos = now;
                this.baseTimestamp = next.timestamp();
            }

            this.ready.poll();
            this.show(next);

            // Si ya hay otro fotograma cuyo momento tambien paso, se descarta
            // este y se sigue: asi se recupera el retraso sin retroceder.
            RgbaFrame following = this.ready.peek();
            if (following == null) {
                return;
            }
            long followingDue = this.baseNanos + (long) ((following.timestamp() - this.baseTimestamp) * 1_000_000_000.0);
            if (System.nanoTime() < followingDue) {
                return;
            }
        }
    }

    private void show(RgbaFrame frame) {
        if (this.texture == null) {
            this.texture = new VideoTexture();
            this.textureId = new ResourceLocation("fscrates", "video/" + (textureSequence++));
            Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
        }

        try {
            this.texture.upload(frame.pixels(), frame.width(), frame.height());
        } catch (Throwable t) {
            FSCrates.LOGGER.error("[FSCrates] Error subiendo el fotograma de video a la GPU: {}", t.toString());
            this.failed = true;
            return;
        }

        RgbaFrame previous = this.current;
        this.current = frame;
        if (previous != null && !this.recycled.offer(previous)) {
            MemoryUtil.memFree(previous.pixels());
        }
    }

    // ----------------------------------------------------------- decodificado

    private void runVideo() {
        while (!this.stopped) {
            FrameGrab grab = null;
            PriorityQueue<YuvFrame> window = new PriorityQueue<>(
                REORDER_WINDOW + 1,
                Comparator.comparingDouble(f -> f.timestamp)
            );

            try {
                grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(this.file.toFile()));
                int emitted = 0;

                while (!this.stopped) {
                    PictureWithMetadata meta = grab.getNativeFrameWithMetadata();
                    if (meta == null || meta.getPicture() == null) {
                        break;
                    }

                    window.add(this.capture(meta));

                    // La ventana llena garantiza que el minimo ya es el correcto.
                    if (window.size() > REORDER_WINDOW) {
                        if (!this.emit(window.poll())) {
                            return;
                        }
                        emitted++;
                    }
                }

                // Fin del archivo: se vacia la ventana en orden.
                while (!this.stopped && !window.isEmpty()) {
                    if (!this.emit(window.poll())) {
                        return;
                    }
                    emitted++;
                }

                if (emitted == 0) {
                    throw new IllegalStateException("no se pudo decodificar ningun fotograma");
                }
            } catch (Throwable t) {
                FSCrates.LOGGER.error(
                    "[FSCrates] No se pudo reproducir el video '{}': {}. Asegurate de que sea MP4 con video H.264.",
                    this.file == null ? "?" : this.file.getFileName(),
                    t.toString()
                );
                this.failed = true;
                return;
            } finally {
                window.forEach(this::release);
                window.clear();
                closeQuietly(grab);
            }
            // Vuelve a empezar: bucle continuo.
        }
    }

    /** Copia el fotograma decodificado a un YuvFrame del pool (planos compactos). */
    private YuvFrame capture(PictureWithMetadata meta) {
        Picture picture = meta.getPicture();
        // Ojo: getWidth()/getHeight() incluyen el relleno de macrobloque (por
        // ejemplo 1600x912 para un video de 1600x900); el tamano real es el crop.
        int w = picture.getCroppedWidth();
        int h = picture.getCroppedHeight();
        int cw = (w + 1) / 2;
        int ch = (h + 1) / 2;

        YuvFrame frame = this.borrowYuv();
        if (frame.luma == null || frame.width != w || frame.height != h) {
            frame.luma = new byte[w * h];
            frame.cb = new byte[cw * ch];
            frame.cr = new byte[cw * ch];
            frame.width = w;
            frame.height = h;
            frame.chromaWidth = cw;
            frame.chromaHeight = ch;
        }
        frame.timestamp = meta.getTimestamp();
        frame.duration = meta.getDuration();

        byte[] srcY = picture.getPlaneData(0);
        byte[] srcU = picture.getPlaneData(1);
        byte[] srcV = picture.getPlaneData(2);
        int lumaStride = picture.getPlaneWidth(0);
        int chromaStride = picture.getPlaneWidth(1);

        for (int y = 0; y < h; y++) {
            System.arraycopy(srcY, y * lumaStride, frame.luma, y * w, w);
        }
        for (int y = 0; y < ch; y++) {
            System.arraycopy(srcU, y * chromaStride, frame.cb, y * cw, cw);
            System.arraycopy(srcV, y * chromaStride, frame.cr, y * cw, cw);
        }

        return frame;
    }

    /** Convierte a RGBA y lo deja en la cola. false si hay que parar. */
    private boolean emit(YuvFrame frame) {
        try {
            RgbaFrame out = this.convert(frame);
            while (!this.stopped) {
                if (this.ready.offer(out, 100, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            }
            MemoryUtil.memFree(out.pixels());
            return false;
        } catch (InterruptedException e) {
            return false;
        } finally {
            this.release(frame);
        }
    }

    private RgbaFrame convert(YuvFrame frame) {
        int w = frame.width;
        int h = frame.height;
        int needed = w * h * 4;

        RgbaFrame reuse = this.recycled.poll();
        ByteBuffer buffer;
        if (reuse != null && reuse.pixels().capacity() == needed) {
            buffer = reuse.pixels();
        } else {
            if (reuse != null) {
                MemoryUtil.memFree(reuse.pixels());
            }
            buffer = MemoryUtil.memAlloc(needed);
        }

        if (this.scratch == null || this.scratch.length != w * h) {
            this.scratch = new int[w * h];
        }
        int[] scratch = this.scratch;
        byte[] luma = frame.luma;
        byte[] cb = frame.cb;
        byte[] cr = frame.cr;
        int cw = frame.chromaWidth;

        Runnable job = () -> IntStream.range(0, h).parallel().forEach(y -> {
            int lumaRow = y * w;
            int chromaRow = (y >> 1) * cw;
            for (int x = 0; x < w; x++) {
                int chromaCol = x >> 1;
                int yy = luma[lumaRow + x] + 128;
                int u = cb[chromaRow + chromaCol];
                int v = cr[chromaRow + chromaCol];

                int r = yy + ((91881 * v) >> 16);
                int g = yy - ((22554 * u + 46802 * v) >> 16);
                int b = yy + ((116130 * u) >> 16);

                // El buffer va en orden nativo (little endian), asi que este int
                // aterriza en memoria como R, G, B, A: justo lo que espera GL_RGBA.
                scratch[lumaRow + x] = 0xFF000000 | (clamp(b) << 16) | (clamp(g) << 8) | clamp(r);
            }
        });

        ForkJoinPool pool = convertPool();
        if (pool == null) {
            job.run();
        } else {
            pool.submit(job).join();
        }

        buffer.clear();
        buffer.asIntBuffer().put(scratch, 0, w * h);

        return new RgbaFrame(buffer, w, h, frame.timestamp, frame.duration);
    }

    private void runImage() {
        try {
            BufferedImage img = ImageIO.read(new File(this.file.toString()));
            if (img == null) {
                throw new IllegalStateException("formato de imagen no reconocido");
            }

            int w = img.getWidth();
            int h = img.getHeight();
            int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
            int[] rgba = new int[w * h];
            for (int i = 0; i < rgba.length; i++) {
                int c = argb[i];
                rgba[i] = (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
            }

            ByteBuffer buffer = MemoryUtil.memAlloc(w * h * 4);
            buffer.asIntBuffer().put(rgba);
            this.ready.offer(new RgbaFrame(buffer, w, h, 0.0, 0.0));
        } catch (Throwable t) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo cargar la imagen '{}': {}", this.file.getFileName(), t.toString());
            this.failed = true;
        }
    }

    // ------------------------------------------------------------------ utiles

    private YuvFrame borrowYuv() {
        YuvFrame frame = this.yuvPool.poll();
        return frame == null ? new YuvFrame() : frame;
    }

    private void release(YuvFrame frame) {
        if (frame != null && this.yuvPool.size() <= REORDER_WINDOW + 2) {
            this.yuvPool.offer(frame);
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /**
     * Pool propio para la conversion. No usamos el common pool de ForkJoin para
     * no competir con las tareas de Minecraft ni de otros mods.
     */
    private static synchronized ForkJoinPool convertPool() {
        if (convertPool == null) {
            int parallelism = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
            if (parallelism <= 1) {
                return null;
            }
            AtomicInteger counter = new AtomicInteger();
            convertPool = new ForkJoinPool(parallelism, pool -> {
                ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                thread.setName("FSCrates-YUV-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }, null, false);
        }
        return convertPool;
    }

    private static void closeQuietly(FrameGrab grab) {
        if (grab == null) {
            return;
        }
        try {
            if (grab.getVideoTrack() instanceof AutoCloseable closeable) {
                closeable.close();
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ cierre

    @Override
    public void close() {
        this.stopped = true;

        Thread thread = this.decoderThread;
        if (thread != null) {
            thread.interrupt();
            this.decoderThread = null;
        }

        if (this.textureId != null) {
            ResourceLocation id = this.textureId;
            this.textureId = null;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    try {
                        mc.getTextureManager().release(id);
                    } catch (Throwable ignored) {
                    }
                });
            }
        }
        this.texture = null;

        RgbaFrame cur = this.current;
        this.current = null;
        if (cur != null) {
            MemoryUtil.memFree(cur.pixels());
        }
        RgbaFrame pending;
        while ((pending = this.ready.poll()) != null) {
            MemoryUtil.memFree(pending.pixels());
        }
        RgbaFrame spare;
        while ((spare = this.recycled.poll()) != null) {
            MemoryUtil.memFree(spare.pixels());
        }
        this.yuvPool.clear();
    }
}
