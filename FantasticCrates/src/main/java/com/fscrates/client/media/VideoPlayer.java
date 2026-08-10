package com.fscrates.client.media;

import com.fscrates.FSCrates;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
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
 * El decodificado corre en un hilo propio y entrega fotogramas RGBA ya
 * convertidos a traves de una cola pequena; el hilo de render solo sube el
 * buffer a la GPU. Los buffers se reciclan para no generar basura.
 */
public final class VideoPlayer implements AutoCloseable {
    private static final int QUEUE_SIZE = 3;
    private static final double DEFAULT_FRAME_SECONDS = 1.0 / 24.0;

    private static int textureSequence;

    private final Path file;
    private final boolean staticImage;
    private final boolean unsupported;

    private final BlockingQueue<Frame> ready = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private final BlockingQueue<ByteBuffer> recycled = new ArrayBlockingQueue<>(QUEUE_SIZE + 1);

    private Thread decoderThread;
    private volatile boolean stopped;
    private volatile boolean failed;
    private volatile int bufferBytes = -1;

    private VideoTexture texture;
    private ResourceLocation textureId;
    private Frame current;
    private long nextFrameAtNanos;

    private record Frame(ByteBuffer rgba, int width, int height, double seconds) {
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

    /** Arranca el hilo de decodificado. Idempotente. */
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

    /** true cuando ya hay al menos un fotograma listo para mostrar. */
    public boolean hasPicture() {
        return this.current != null || !this.ready.isEmpty();
    }

    // ------------------------------------------------------------------ render

    /**
     * Dibuja el fotograma actual cubriendo toda el area indicada (estilo "cover":
     * mantiene la proporcion y recorta el excedente).
     */
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

    /** Toma el siguiente fotograma de la cola si ya toca mostrarlo. */
    private void advanceFrame() {
        long now = System.nanoTime();

        if (this.current == null) {
            Frame first = this.ready.poll();
            if (first == null) {
                return;
            }
            this.swapTo(first, now);
            return;
        }

        // Una imagen estatica no necesita avanzar nunca.
        if (this.staticImage) {
            return;
        }

        if (now < this.nextFrameAtNanos) {
            return;
        }

        Frame next = this.ready.poll();
        if (next == null) {
            // El decodificador va por detras: mantenemos el fotograma actual y
            // reprogramamos para no acumular retraso.
            this.nextFrameAtNanos = now + (long) (DEFAULT_FRAME_SECONDS * 1_000_000_000.0);
            return;
        }

        this.swapTo(next, now);
    }

    private void swapTo(Frame frame, long now) {
        if (this.texture == null) {
            this.texture = new VideoTexture();
            this.textureId = new ResourceLocation("fscrates", "video/" + (textureSequence++));
            Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
        }

        try {
            this.texture.upload(frame.rgba(), frame.width(), frame.height());
        } catch (Throwable t) {
            FSCrates.LOGGER.error("[FSCrates] Error subiendo el fotograma de video a la GPU: {}", t.toString());
            this.failed = true;
            return;
        }

        Frame previous = this.current;
        this.current = frame;

        double seconds = frame.seconds() > 0.0005 && frame.seconds() < 1.0 ? frame.seconds() : DEFAULT_FRAME_SECONDS;
        long due = this.nextFrameAtNanos == 0L ? now : this.nextFrameAtNanos;
        this.nextFrameAtNanos = due + (long) (seconds * 1_000_000_000.0);
        // Si nos hemos quedado muy atras, resincronizamos con el reloj actual.
        if (this.nextFrameAtNanos < now) {
            this.nextFrameAtNanos = now + (long) (seconds * 1_000_000_000.0);
        }

        if (previous != null) {
            this.recycled.offer(previous.rgba());
        }
    }

    // ----------------------------------------------------------- decodificado

    private void runVideo() {
        while (!this.stopped) {
            FrameGrab grab = null;
            try {
                grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(this.file.toFile()));
                int decoded = 0;

                while (!this.stopped) {
                    PictureWithMetadata meta = grab.getNativeFrameWithMetadata();
                    if (meta == null || meta.getPicture() == null) {
                        break;
                    }

                    Frame frame = toFrame(meta.getPicture(), meta.getDuration());
                    if (frame == null) {
                        break;
                    }
                    decoded++;

                    // Espera con hueco: si la cola esta llena, el render aun no
                    // consumio; no tiene sentido decodificar mas rapido.
                    while (!this.stopped && !this.ready.offer(frame, 100, TimeUnit.MILLISECONDS)) {
                        // reintenta
                    }
                }

                if (decoded == 0) {
                    throw new IllegalStateException("no se pudo decodificar ningun fotograma");
                }
            } catch (Throwable t) {
                FSCrates.LOGGER.error(
                    "[FSCrates] No se pudo reproducir el video '{}': {}. "
                        + "Asegurate de que sea MP4 con video H.264.",
                    this.file == null ? "?" : this.file.getFileName(),
                    t.toString()
                );
                this.failed = true;
                return;
            } finally {
                closeQuietly(grab);
            }
            // Fin del archivo -> vuelve a empezar (bucle continuo).
        }
    }

    private void runImage() {
        try {
            BufferedImage img = ImageIO.read(new File(this.file.toString()));
            if (img == null) {
                throw new IllegalStateException("formato de imagen no reconocido");
            }

            int w = img.getWidth();
            int h = img.getHeight();
            ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    buf.put((byte) (argb >> 16 & 0xFF));
                    buf.put((byte) (argb >> 8 & 0xFF));
                    buf.put((byte) (argb & 0xFF));
                    buf.put((byte) (argb >>> 24 & 0xFF));
                }
            }
            buf.flip();
            this.bufferBytes = w * h * 4;
            this.ready.offer(new Frame(buf, w, h, 0.0));
        } catch (Throwable t) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo cargar la imagen '{}': {}", this.file.getFileName(), t.toString());
            this.failed = true;
        }
    }

    /** Convierte un Picture YUV 4:2:0 de JCodec a un buffer RGBA reciclado. */
    private Frame toFrame(Picture picture, double duration) throws InterruptedException {
        Picture pic = picture.getCrop() != null ? picture.cloneCropped() : picture;

        int w = pic.getWidth();
        int h = pic.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }

        int needed = w * h * 4;
        if (this.bufferBytes != needed) {
            // El tamano cambio (primer fotograma o cambio de resolucion):
            // descartamos los buffers viejos.
            ByteBuffer old;
            while ((old = this.recycled.poll()) != null) {
                MemoryUtil.memFree(old);
            }
            this.bufferBytes = needed;
        }

        ByteBuffer buf = this.recycled.poll();
        if (buf == null || buf.capacity() != needed) {
            if (buf != null) {
                MemoryUtil.memFree(buf);
            }
            buf = MemoryUtil.memAlloc(needed);
        }
        buf.clear();

        byte[] luma = pic.getPlaneData(0);
        byte[] cb = pic.getPlaneData(1);
        byte[] cr = pic.getPlaneData(2);
        int lumaStride = pic.getPlaneWidth(0);
        int chromaStride = pic.getPlaneWidth(1);

        for (int y = 0; y < h; y++) {
            int lumaRow = y * lumaStride;
            int chromaRow = (y >> 1) * chromaStride;
            for (int x = 0; x < w; x++) {
                int chromaCol = x >> 1;
                int yy = luma[lumaRow + x] + 128;
                int u = cb[chromaRow + chromaCol];
                int v = cr[chromaRow + chromaCol];

                int r = yy + ((91881 * v) >> 16);
                int g = yy - ((22554 * u + 46802 * v) >> 16);
                int b = yy + ((116130 * u) >> 16);

                buf.put((byte) clamp(r));
                buf.put((byte) clamp(g));
                buf.put((byte) clamp(b));
                buf.put((byte) 0xFF);
            }
        }
        buf.flip();

        return new Frame(buf, w, h, duration);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
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

        // Libera la memoria nativa de todos los buffers.
        Frame cur = this.current;
        this.current = null;
        if (cur != null) {
            MemoryUtil.memFree(cur.rgba());
        }
        Frame pending;
        while ((pending = this.ready.poll()) != null) {
            MemoryUtil.memFree(pending.rgba());
        }
        ByteBuffer spare;
        while ((spare = this.recycled.poll()) != null) {
            MemoryUtil.memFree(spare);
        }
    }
}
