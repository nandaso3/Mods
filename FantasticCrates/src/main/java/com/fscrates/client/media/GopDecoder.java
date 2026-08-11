package com.fscrates.client.media;

import com.fscrates.FSCrates;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;

/**
 * Decodificador de video con varios hilos, repartiendo el trabajo por GOPs.
 *
 * JCodec decodifica con un solo hilo, y eso pone un techo bajo: en pruebas, unos
 * 27 fps a 1080p y 15 a 1440p. Como cada GOP (grupo que empieza en un keyframe)
 * se puede decodificar de forma independiente, aqui se reparten entre varios
 * hilos, cada uno con su propio FrameGrab sobre el mismo archivo.
 *
 * El orden de salida se mantiene: los fotogramas se guardan por su numero
 * absoluto y se van entregando en secuencia. Un hilo no puede adelantarse mas de
 * lo que marca la ventana, para que la memoria quede acotada.
 *
 * Si el archivo no tiene informacion de keyframes utilizable se cae a un solo
 * hilo, que es el comportamiento de siempre.
 */
final class GopDecoder implements AutoCloseable {
    /** Fotograma YUV con planos compactos (stride == ancho). */
    static final class YuvFrame {
        byte[] luma;
        byte[] cb;
        byte[] cr;
        int width;
        int height;
        int chromaWidth;
        double timestamp;
        double duration;
        int index;
    }

    private final Path file;
    private final int workerCount;
    private final int[] keyFrames;
    private final int totalFrames;
    /** Fotogramas que se pueden tener decodificados por delante. */
    private volatile int window;
    /** Techo de memoria para los fotogramas en vuelo. */
    private static final long FRAME_BUDGET_BYTES = 72L * 1024L * 1024L;
    private boolean windowAdjusted;

    /** Siguiente GOP por repartir. */
    private final AtomicInteger nextGop = new AtomicInteger();
    /** Siguiente fotograma que toca entregar. */
    private int nextToEmit;
    /** Fotogramas decodificados esperando su turno, por numero absoluto. */
    private final Map<Integer, YuvFrame> pending = new HashMap<>();
    /** Objetos reutilizables para no asignar memoria por fotograma. */
    private final Deque<YuvFrame> pool = new ArrayDeque<>();

    private final Object lock = new Object();
    private volatile boolean stopped;
    private volatile Throwable failure;
    private Thread[] workers;

    private GopDecoder(Path file, int workerCount, int[] keyFrames, int totalFrames) {
        this.file = file;
        this.workerCount = workerCount;
        this.keyFrames = keyFrames;
        this.totalFrames = totalFrames;
        // Para que varios hilos avancen a la vez hace falta poder tener en vuelo
        // al menos un GOP por hilo. Se ajusta luego segun el tamano real del
        // fotograma para no comerse la RAM con videos grandes.
        int gopSize = keyFrames.length > 1 ? Math.max(1, keyFrames[1] - keyFrames[0]) : 12;
        this.window = Math.max(12, Math.min(28, gopSize * workerCount + 4));
    }

    /**
     * Intenta crear un decodificador multihilo. Devuelve null si no se puede
     * (pocos nucleos, sin keyframes conocidos o un solo GOP).
     */
    static GopDecoder tryCreate(Path file) {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores < 4) {
            return null;
        }

        try {
            FrameGrab probe = FrameGrab.createFrameGrab(NIOUtils.readableChannel(file.toFile()));
            DemuxerTrackMeta meta = probe.getVideoTrack().getMeta();
            int[] seekFrames = meta == null ? null : meta.getSeekFrames();
            int total = meta == null ? 0 : meta.getTotalFrames();

            if (seekFrames == null || seekFrames.length < 3 || total <= 0) {
                return null;
            }

            // Un solo hilo por cada 4 nucleos, maximo 3: decodificar es intenso y
            // no queremos ahogar al resto del juego.
            int workers = Math.max(2, Math.min(3, cores / 4));
            GopDecoder decoder = new GopDecoder(file, workers, seekFrames, total);
            FSCrates.LOGGER.info(
                "[FSCrates] Video '{}': {} fotogramas, {} GOPs, decodificando con {} hilos.",
                file.getFileName(),
                total,
                seekFrames.length,
                workers
            );
            return decoder;
        } catch (Throwable t) {
            return null;
        }
    }

    void start() {
        this.workers = new Thread[this.workerCount];
        for (int i = 0; i < this.workerCount; i++) {
            Thread t = new Thread(this::runWorker, "FSCrates-VideoDecode-" + i);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            this.workers[i] = t;
            t.start();
        }
    }

    /** Primer fotograma de un GOP. */
    private int gopStart(int gop) {
        return this.keyFrames[gop];
    }

    /** Primer fotograma del GOP siguiente (o el total si es el ultimo). */
    private int gopEnd(int gop) {
        return gop + 1 < this.keyFrames.length ? this.keyFrames[gop + 1] : this.totalFrames;
    }

    private void runWorker() {
        FrameGrab grab = null;
        try {
            grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(this.file.toFile()));

            while (!this.stopped) {
                int gop = this.nextGop.getAndIncrement();
                if (gop >= this.keyFrames.length) {
                    // Fin del video: se reparten otra vez desde el principio para
                    // el bucle. Solo el hilo que llega justo al limite reinicia.
                    synchronized (this.lock) {
                        if (this.nextGop.get() >= this.keyFrames.length) {
                            this.nextGop.set(0);
                        }
                    }
                    continue;
                }

                int from = this.gopStart(gop);
                int to = this.gopEnd(gop);

                // No adelantarse mas de la ventana: acota la memoria.
                synchronized (this.lock) {
                    while (!this.stopped && from > this.nextToEmit + this.window) {
                        this.lock.wait(50L);
                    }
                    if (this.stopped) {
                        return;
                    }
                }

                // 'from' es un keyframe, asi que el salto es directo y barato.
                grab.seekToFrameSloppy(from);

                // El numero de fotograma NO se puede ir contando 1, 2, 3...: dentro
                // de un GOP con B-frames el orden de decodificacion tampoco es el de
                // reproduccion. Se calcula desde el timestamp, tomando como base el
                // primer fotograma del GOP (el keyframe, que siempre va primero).
                double baseTimestamp = -1.0;

                for (int decoded = 0; decoded < to - from && !this.stopped; decoded++) {
                    PictureWithMetadata meta = grab.getNativeFrameWithMetadata();
                    if (meta == null || meta.getPicture() == null) {
                        break;
                    }

                    if (baseTimestamp < 0.0) {
                        baseTimestamp = meta.getTimestamp();
                    }

                    double duration = meta.getDuration() > 0.0001 ? meta.getDuration() : 1.0 / 30.0;
                    int offset = (int) Math.round((meta.getTimestamp() - baseTimestamp) / duration);
                    int index = Math.max(from, Math.min(to - 1, from + offset));

                    this.publish(index, meta);
                }
            }
        } catch (Throwable t) {
            if (!this.stopped) {
                this.failure = t;
                synchronized (this.lock) {
                    this.lock.notifyAll();
                }
            }
        } finally {
            closeQuietly(grab);
        }
    }

    /** Copia el fotograma y lo deja en la cola ordenada. */
    private void publish(int index, PictureWithMetadata meta) throws InterruptedException {
        Picture picture = meta.getPicture();
        int w = picture.getCroppedWidth();
        int h = picture.getCroppedHeight();
        int cw = (w + 1) / 2;
        int ch = (h + 1) / 2;

        YuvFrame frame;
        synchronized (this.lock) {
            // Con el tamano real ya conocido, se recorta la ventana si hace falta
            // para no pasarse del techo de memoria (un fotograma 1440p en YUV son
            // unos 5,5 MB, asi que 24 en vuelo serian 132 MB).
            if (!this.windowAdjusted) {
                this.windowAdjusted = true;
                long frameBytes = (long) w * h * 3L / 2L;
                int allowed = (int) Math.max(12L, Math.min(this.window, FRAME_BUDGET_BYTES / Math.max(1L, frameBytes)));
                if (allowed < this.window) {
                    FSCrates.LOGGER.info(
                        "[FSCrates] Video {}x{}: ventana de decodificado reducida a {} fotogramas por memoria.",
                        w, h, allowed
                    );
                    this.window = allowed;
                }
            }

            while (!this.stopped && (this.pending.size() >= this.window || index > this.nextToEmit + this.window)) {
                this.lock.wait(50L);
            }
            if (this.stopped) {
                return;
            }
            frame = this.pool.poll();
        }

        if (frame == null) {
            frame = new YuvFrame();
        }
        if (frame.luma == null || frame.width != w || frame.height != h) {
            frame.luma = new byte[w * h];
            frame.cb = new byte[cw * ch];
            frame.cr = new byte[cw * ch];
            frame.width = w;
            frame.height = h;
            frame.chromaWidth = cw;
        }
        frame.timestamp = meta.getTimestamp();
        frame.duration = meta.getDuration();
        frame.index = index;

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

        synchronized (this.lock) {
            this.pending.put(index, frame);
            this.lock.notifyAll();
        }
    }

    /**
     * Devuelve el siguiente fotograma en ORDEN de reproduccion, esperando si
     * hace falta. Devuelve null si se paro o si fallo la decodificacion.
     */
    YuvFrame take() throws InterruptedException {
        synchronized (this.lock) {
            while (!this.stopped) {
                if (this.failure != null) {
                    return null;
                }

                YuvFrame frame = this.pending.remove(this.nextToEmit);
                if (frame != null) {
                    this.nextToEmit++;
                    // Al terminar la vuelta se vuelve a empezar (bucle continuo).
                    if (this.nextToEmit >= this.totalFrames) {
                        this.nextToEmit = 0;
                        this.pending.clear();
                    }
                    this.lock.notifyAll();
                    return frame;
                }

                this.lock.wait(100L);
            }
        }
        return null;
    }

    /** Devuelve el objeto al pool para reutilizarlo. */
    void recycle(YuvFrame frame) {
        if (frame == null) {
            return;
        }
        synchronized (this.lock) {
            if (this.pool.size() <= this.window) {
                this.pool.offer(frame);
            }
            this.lock.notifyAll();
        }
    }

    Throwable failure() {
        return this.failure;
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

    @Override
    public void close() {
        this.stopped = true;
        synchronized (this.lock) {
            this.lock.notifyAll();
        }
        if (this.workers != null) {
            for (Thread t : this.workers) {
                if (t != null) {
                    t.interrupt();
                }
            }
            this.workers = null;
        }
        synchronized (this.lock) {
            this.pending.clear();
            this.pool.clear();
        }
    }
}
