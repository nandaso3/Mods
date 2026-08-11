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
import java.util.function.IntConsumer;
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
import org.jcodec.common.io.SeekableByteChannel;
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
    /**
     * A partir de este tamano de fotograma se reparte la conversion entre hilos.
     *
     * Esta alto a proposito: convertir un fotograma 1080p cuesta unos 7 ms, y
     * repartirlo no ahorra CPU (solo la mueve de sitio) mientras que si anade
     * hilos despertandose 30 veces por segundo. Solo compensa por encima de
     * 1440p, donde ya son mas de 12 ms.
     */
    private static final int PARALLEL_CONVERT_PIXELS = 2_500_000;

    private static int textureSequence;
    private static ForkJoinPool convertPool;

    /**
     * El archivo que se reproduce.
     *
     * No es final porque al arrancar puede cambiarse por una copia sin las pistas
     * de audio (ver Mp4Sanitizer). Volatil porque lo escribe el hilo que arranca
     * la reproduccion y lo leen el de decodificado y el de render.
     */
    private volatile Path file;
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
    /** Decodificador multihilo, si el archivo lo permite. */
    private volatile GopDecoder gopDecoder;

    /**
     * Conversion de color de este archivo, sacada de sus metadatos.
     *
     * Se resuelve con el primer fotograma y no antes porque hasta entonces no se
     * sabe la resolucion real (la del crop, no la del relleno de macrobloque),
     * que es lo que se usa si el archivo no declara nada.
     */
    private YuvToRgb converter;

    /**
     * Cuanto se esta ampliando el video en pantalla. Lo escribe el hilo de render
     * y lo lee el de decodificado para decidir si hace falta enfocar.
     */
    private volatile float drawScale = 1.0F;

    /** Brillo ya enfocado. Aparte del original porque el enfoque lee los vecinos. */
    private byte[] sharpened;

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

        // El tipo se decide por el CONTENIDO del archivo, no por su nombre: los
        // links directos de Google Drive no llevan extension, asi que por el url
        // es imposible saber si viene un MP4 o un PNG.
        MediaCache.MediaType type = MediaCache.sniff(file);
        if (type == MediaCache.MediaType.UNKNOWN) {
            type = typeFromName(file);
        }

        this.staticImage = type.isImage();
        this.unsupported = type == MediaCache.MediaType.WEBM;

        if (this.unsupported) {
            this.failed = true;
            FSCrates.LOGGER.warn(
                "[FSCrates] '{}' es un WEBM y no se puede reproducir (VP8/VP9 no tiene decodificador en Java puro). "
                    + "Convertelo a MP4 (H.264) o usa una imagen PNG/JPG.",
                file == null ? "?" : file.getFileName()
            );
        }
    }

    /**
     * Explica en el log QUE tiene de malo el video, no un "no se pudo" a secas.
     *
     * Los dos fallos tipicos son exportar en 10 bits o en HEVC (H.265), que es lo
     * que hacen por defecto muchos editores y moviles. En los dos casos el archivo
     * parece un MP4 normal, asi que sin un mensaje concreto es imposible adivinarlo.
     */
    private void reportFailure(Throwable t) {
        String name = this.file == null ? "?" : this.file.getFileName().toString();
        String message = t.getMessage() == null ? "" : t.getMessage();

        if (message.contains("High bit depth")) {
            FSCrates.LOGGER.error(
                "[FSCrates] '{}' esta en 10 bits y no se puede reproducir. Expórtalo en 8 bits "
                    + "(en ffmpeg: -pix_fmt yuv420p). Mira config/fscrates/_LEEME_VIDEOS.txt.",
                name
            );
            return;
        }

        if (message.contains("Not a video track") || message.contains("hvc1") || message.contains("hev1")) {
            FSCrates.LOGGER.error(
                "[FSCrates] '{}' parece HEVC (H.265) y solo se soporta H.264. Reconviertelo "
                    + "(en ffmpeg: -c:v libx264). Mira config/fscrates/_LEEME_VIDEOS.txt.",
                name
            );
            return;
        }

        if (message.contains("Unsupported h264 feature")) {
            FSCrates.LOGGER.error(
                "[FSCrates] '{}' usa una opcion de H.264 que no se soporta ({}). Reconviertelo con "
                    + "los ajustes de config/fscrates/_LEEME_VIDEOS.txt.",
                name,
                message
            );
            return;
        }

        FSCrates.LOGGER.error(
            "[FSCrates] No se pudo reproducir '{}': {}. Tiene que ser MP4 con video H.264 de 8 bits. "
                + "Mira config/fscrates/_LEEME_VIDEOS.txt.",
            name,
            t.toString()
        );
    }

    /** Ultimo recurso si el contenido no se reconoce: mirar la extension. */
    private static MediaCache.MediaType typeFromName(Path file) {
        String name = file == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return MediaCache.MediaType.PNG;
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return MediaCache.MediaType.JPEG;
        }
        if (name.endsWith(".gif")) {
            return MediaCache.MediaType.GIF;
        }
        if (name.endsWith(".webm") || name.endsWith(".mkv")) {
            return MediaCache.MediaType.WEBM;
        }
        return MediaCache.MediaType.MP4;
    }

    public void start() {
        if (this.decoderThread != null || this.failed || this.file == null) {
            return;
        }

        Runnable task;
        if (this.staticImage) {
            task = this::runImage;
        } else {
            // Antes de abrir nada se apartan las pistas que no son de video: si
            // el decodificador tropieza con la de audio no abre el archivo ni
            // aunque el video este perfecto. Tiene que ir aqui, que es por donde
            // pasan las dos rutas (un hilo y varios).
            this.file = Mp4Sanitizer.prepare(this.file);

            // Se intenta el decodificado en paralelo; si el archivo no lo permite
            // se usa un solo hilo, como siempre.
            GopDecoder parallel = GopDecoder.tryCreate(this.file);
            task = parallel != null ? () -> this.runParallelVideo(parallel) : this::runVideo;
        }

        this.decoderThread = new Thread(task, "FSCrates-VideoDecode");
        this.decoderThread.setDaemon(true);
        // Prioridad minima: si hay que elegir, manda el juego.
        this.decoderThread.setPriority(Thread.MIN_PRIORITY);
        this.decoderThread.start();
    }

    public boolean hasFailed() {
        return this.failed;
    }

    public boolean hasPicture() {
        return this.current != null || !this.ready.isEmpty();
    }

    // ------------------------------------------------------------------ render

    /**
     * Dibuja el fotograma actual cubriendo el area (estilo "cover").
     *
     * IMPORTANTE: se dibuja a resolucion REAL de pantalla, no en coordenadas de
     * GUI. Las coordenadas de GUI estan divididas por el guiScale (con escala 3
     * una pantalla de 1920 son 640 unidades), asi que dibujar ahi metia el video
     * en 640 px de ancho y luego Minecraft lo estiraba: se veia borroso.
     * Escalando el PoseStack por 1/guiScale se dibuja pixel a pixel.
     */
    public void render(GuiGraphics g, int areaWidth, int areaHeight, float alpha) {
        if (this.failed) {
            return;
        }

        this.advanceFrame();
        if (this.current == null || this.texture == null || !this.texture.hasFrame()) {
            return;
        }

        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        if (guiScale <= 0.0) {
            guiScale = 1.0;
        }

        // Area destino en pixeles reales de pantalla.
        int targetWidth = (int) Math.ceil(areaWidth * guiScale);
        int targetHeight = (int) Math.ceil(areaHeight * guiScale);

        int fw = this.current.width();
        int fh = this.current.height();
        float scale = Math.max((float) targetWidth / fw, (float) targetHeight / fh);
        int dw = Math.max(1, Math.round(fw * scale));
        int dh = Math.max(1, Math.round(fh * scale));
        int x = (targetWidth - dw) / 2;
        int y = (targetHeight - dh) / 2;

        // Si el video cae 1:1 con la pantalla se dibuja sin suavizado, para que
        // salga exactamente igual de nitido que el archivo original.
        this.texture.setSharpFiltering(Math.abs(scale - 1.0F) < 0.01F);

        // El decodificador necesita saber cuanto se va a ampliar para decidir si
        // enfoca. Se apunta aqui, que es el unico sitio donde se conoce el hueco
        // real en pixeles de pantalla.
        this.drawScale = scale;

        g.setColor(1.0F, 1.0F, 1.0F, Math.max(0.0F, Math.min(1.0F, alpha)));
        g.pose().pushPose();
        g.pose().scale((float) (1.0 / guiScale), (float) (1.0 / guiScale), 1.0F);
        g.blit(this.textureId, x, y, dw, dh, 0.0F, 0.0F, fw, fh, fw, fh);
        g.pose().popPose();
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
        RgbaFrame chosen = null;

        // Se busca el fotograma MAS NUEVO cuyo momento ya paso y se descartan los
        // atrasados SIN subirlos a la GPU. Antes se subian todos uno detras de
        // otro, y despues de un tiron eso amplificaba el paron: cada subida de un
        // fotograma 1080p mueve 8 MB por el bus, en el hilo de render.
        while (true) {
            RgbaFrame next = this.ready.peek();
            if (next == null) {
                break;
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
                break;
            }

            // Si vamos muy retrasados, reancla para no encadenar saltos.
            if (now - dueAt > (long) (MAX_DRIFT_SECONDS * 1_000_000_000.0)) {
                this.baseNanos = now;
                this.baseTimestamp = next.timestamp();
            }

            this.ready.poll();
            if (chosen != null) {
                // Este ya no se ve: se recicla sin gastar nada en subirlo.
                this.discard(chosen);
            }
            chosen = next;
        }

        if (chosen != null) {
            this.show(chosen);
        }
    }

    /** Devuelve el buffer al pool sin dibujarlo. */
    private void discard(RgbaFrame frame) {
        if (frame != null && !this.recycled.offer(frame)) {
            MemoryUtil.memFree(frame.pixels());
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

    /**
     * Decodificado en paralelo por GOPs. Es bastante mas rapido que un solo hilo
     * (JCodec decodifica con uno solo), que es lo que permite 1080p a 30 fps.
     */
    private void runParallelVideo(GopDecoder first) {
        GopDecoder decoder = first;
        this.gopDecoder = decoder;
        try {
            decoder.start();

            while (!this.stopped) {
                GopDecoder.YuvFrame frame = decoder.take();
                if (frame == null) {
                    Throwable failure = decoder.failure();
                    if (failure != null) {
                        throw failure;
                    }

                    // Fin del archivo: se cierra y se abre otro para dar la vuelta.
                    // Es mas simple y robusto que reiniciar el reparto por dentro.
                    boolean finished = decoder.reachedEnd();
                    decoder.close();
                    this.gopDecoder = null;
                    if (this.stopped || !finished) {
                        return;
                    }

                    GopDecoder next = GopDecoder.tryCreate(this.file);
                    if (next == null) {
                        // Si ya no se puede en paralelo, se sigue con un solo hilo.
                        this.runVideo();
                        return;
                    }
                    decoder = next;
                    this.gopDecoder = decoder;
                    decoder.start();
                    continue;
                }

                RgbaFrame out = this.convertShared(
                    frame.luma, frame.cb, frame.cr, frame.width, frame.height,
                    frame.chromaWidth, frame.chromaHeight,
                    frame.timestamp, frame.duration
                );
                decoder.recycle(frame);

                boolean queued = false;
                while (!this.stopped && !queued) {
                    queued = this.ready.offer(out, 100, TimeUnit.MILLISECONDS);
                }
                if (!queued) {
                    MemoryUtil.memFree(out.pixels());
                    return;
                }
            }
        } catch (InterruptedException e) {
            // cierre normal
        } catch (Throwable t) {
            FSCrates.LOGGER.error(
                "[FSCrates] Fallo el decodificado en paralelo de '{}': {}. Se reintenta con un solo hilo.",
                this.file.getFileName(),
                t.toString()
            );
            decoder.close();
            this.gopDecoder = null;
            if (!this.stopped) {
                this.runVideo();
            }
        }
    }

    /**
     * Decodificado con un solo hilo.
     *
     * El archivo se abre UNA vez y para repetir se rebobina con seek. Antes se
     * volvia a crear el FrameGrab en cada vuelta y el canal anterior no se
     * cerraba nunca (SeekableByteChannel es Closeable): se acumulaban descriptores
     * abiertos y al fallar la reapertura el video se quedaba congelado.
     */
    private void runVideo() {
        SeekableByteChannel channel = null;
        PriorityQueue<YuvFrame> window = new PriorityQueue<>(
            REORDER_WINDOW + 1,
            Comparator.comparingDouble(f -> f.timestamp)
        );

        try {
            channel = NIOUtils.readableChannel(this.file.toFile());
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            long totalEmitted = 0L;
            int emptyPasses = 0;

            while (!this.stopped) {
                PictureWithMetadata meta = grab.getNativeFrameWithMetadata();

                if (meta == null || meta.getPicture() == null) {
                    // Fin del archivo: se vacia la ventana en orden...
                    int flushed = 0;
                    while (!this.stopped && !window.isEmpty()) {
                        if (!this.emit(window.poll())) {
                            return;
                        }
                        flushed++;
                        totalEmitted++;
                    }

                    if (totalEmitted == 0L) {
                        throw new IllegalStateException("no se pudo decodificar ningun fotograma");
                    }

                    // Si dos vueltas seguidas no dan nada, algo va mal: mejor parar
                    // que quedarse girando en vacio quemando CPU.
                    emptyPasses = flushed == 0 ? emptyPasses + 1 : 0;
                    if (emptyPasses >= 2) {
                        FSCrates.LOGGER.warn(
                            "[FSCrates] El video '{}' dejo de dar fotogramas; se detiene la reproduccion.",
                            this.file.getFileName()
                        );
                        return;
                    }

                    // ...y se vuelve al principio SIN reabrir el archivo.
                    grab.seekToFrameSloppy(0);
                    continue;
                }

                window.add(this.capture(meta));

                // La ventana llena garantiza que el minimo ya es el correcto.
                if (window.size() > REORDER_WINDOW) {
                    if (!this.emit(window.poll())) {
                        return;
                    }
                    totalEmitted++;
                }
            }
        } catch (Throwable t) {
            this.reportFailure(t);
            this.failed = true;
        } finally {
            window.forEach(this::release);
            window.clear();
            NIOUtils.closeQuietly(channel);
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
        return this.convertShared(
            frame.luma, frame.cb, frame.cr, frame.width, frame.height,
            frame.chromaWidth, frame.chromaHeight,
            frame.timestamp, frame.duration
        );
    }

    /** Conversion YUV 4:2:0 -> RGBA, compartida por los dos caminos de decodificado. */
    private RgbaFrame convertShared(
        byte[] luma,
        byte[] cb,
        byte[] cr,
        int w,
        int h,
        int cw,
        int ch,
        double timestamp,
        double duration
    ) {
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

        YuvToRgb rgb = this.converter;
        if (rgb == null) {
            rgb = this.file == null ? YuvToRgb.byResolution(w, h) : YuvToRgb.forFile(this.file, w, h);
            this.converter = rgb;
            FSCrates.LOGGER.info(
                "[FSCrates] '{}' {}x{}: color {}.",
                this.file == null ? "?" : this.file.getFileName(),
                w,
                h,
                rgb.describe()
            );
        }
        YuvToRgb use = rgb;

        // Si el video se va a ver ampliado se enfoca el brillo antes de convertir.
        // A escala 1:1 esto no entra y el fotograma va tal cual viene del archivo.
        float amount = LumaSharpen.amountFor(this.drawScale);
        int amount8 = Math.round(amount * 256.0F);
        byte[] target;
        if (amount > 0.0F) {
            if (this.sharpened == null || this.sharpened.length != w * h) {
                this.sharpened = new byte[w * h];
            }
            target = this.sharpened;
        } else {
            target = null;
        }

        // Enfoque y conversion van juntos en la misma pasada por fila. Se puede
        // porque el enfoque de una fila solo lee el brillo ORIGINAL (que nadie
        // modifica) y la conversion de esa fila solo necesita esa misma fila ya
        // enfocada. Asi el reparto entre hilos vale para los dos pasos y se
        // recorre la memoria una vez en lugar de dos.
        IntConsumer rowJob = y -> {
            byte[] src = luma;
            if (target != null) {
                LumaSharpen.row(luma, target, w, h, y, amount8);
                src = target;
            }
            use.row(y, src, cb, cr, w, h, cw, ch, scratch);
        };

        // La conversion solo se reparte entre hilos con fotogramas grandes. Con
        // 720p un hilo tarda pocos milisegundos y no compensa despertar a mas.
        ForkJoinPool pool = w * h >= PARALLEL_CONVERT_PIXELS ? convertPool() : null;
        if (pool == null) {
            IntStream.range(0, h).forEach(rowJob);
        } else {
            pool.submit(() -> IntStream.range(0, h).parallel().forEach(rowJob)).join();
        }

        buffer.clear();
        buffer.asIntBuffer().put(scratch, 0, w * h);

        return new RgbaFrame(buffer, w, h, timestamp, duration);
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

        GopDecoder decoder = this.gopDecoder;
        if (decoder != null) {
            decoder.close();
            this.gopDecoder = null;
        }

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
