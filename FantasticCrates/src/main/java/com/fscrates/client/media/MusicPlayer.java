package com.fscrates.client.media;

import com.fscrates.FSCrates;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Reproductor de musica en bucle para la pantalla de pre-apertura.
 *
 * Formatos soportados:
 *  - .mp3          -> JLayer (la misma libreria que usa Fantastic Pass)
 *  - .ogg          -> STB Vorbis (ya viene con LWJGL/Minecraft)
 *  - .wav / .aiff  -> javax.sound.sampled
 *
 * Se decodifica en su propio hilo demonio y se escribe a un SourceDataLine, con
 * el volumen aplicado en dB sobre el control MASTER_GAIN (patron de Fantastic Pass).
 */
public final class MusicPlayer implements AutoCloseable {
    private static final int OGG_CHUNK_SAMPLES = 4096;

    private final Path file;

    private Thread thread;
    private volatile boolean stopped;
    private volatile SourceDataLine activeLine;
    private volatile float volume = 1.0F;
    private volatile int volumeGeneration;
    private int appliedGeneration = -1;

    public MusicPlayer(Path file) {
        this.file = file;
    }

    /** Volumen lineal: 1.0 = 100%. 0 = silencio. Puede pasar de 1.0 (amplifica). */
    public void setVolume(float value) {
        this.volume = Math.max(0.0F, value);
        this.volumeGeneration++;
    }

    public void start() {
        if (this.thread != null || this.file == null) {
            return;
        }

        this.thread = new Thread(this::runLoop, "FSCrates-Music");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** Reproduce el archivo una y otra vez hasta que se cierre la pantalla. */
    private void runLoop() {
        String name = this.file.getFileName().toString().toLowerCase(Locale.ROOT);

        // El formato se detecta por el contenido: los links de Drive no traen extension.
        MediaCache.MediaType type = MediaCache.sniff(this.file);

        while (!this.stopped) {
            long startedAt = System.currentTimeMillis();
            try {
                if (type == MediaCache.MediaType.MP3
                    || name.endsWith(".mp3") || name.endsWith(".mpeg") || name.endsWith(".mpga")) {
                    streamMp3();
                } else if (type == MediaCache.MediaType.OGG || name.endsWith(".ogg") || name.endsWith(".oga")) {
                    streamOgg();
                } else {
                    streamSampled();
                }
            } catch (Throwable t) {
                FSCrates.LOGGER.error("[FSCrates] No se pudo reproducir la musica '{}': {}", name, t.toString());
                return;
            }

            // Si termina instantaneamente algo va mal: evitamos un bucle infinito.
            if (!this.stopped && System.currentTimeMillis() - startedAt < 250L) {
                FSCrates.LOGGER.warn("[FSCrates] La pista '{}' no produjo audio; se detiene el bucle.", name);
                return;
            }
        }
    }

    // -------------------------------------------------------------------- MP3

    private void streamMp3() throws Exception {
        SourceDataLine line = null;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(this.file), 16384)) {
            Bitstream bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();

            Header header;
            while (!this.stopped && (header = bitstream.readFrame()) != null) {
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (line == null) {
                    AudioFormat format =
                        new AudioFormat(output.getSampleFrequency(), 16, output.getChannelCount(), true, false);
                    line = openLine(format);
                }

                applyGainIfNeeded(line);
                byte[] pcm = toLittleEndian(output.getBuffer(), output.getBufferLength());
                line.write(pcm, 0, pcm.length);
                bitstream.closeFrame();
            }

            bitstream.close();
        } finally {
            closeLine(line);
        }
    }

    // -------------------------------------------------------------------- OGG

    private void streamOgg() throws Exception {
        byte[] bytes = Files.readAllBytes(this.file);
        ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
        SourceDataLine line = null;
        long handle = MemoryUtil.NULL;

        try {
            data.put(bytes);
            data.flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                handle = STBVorbis.stb_vorbis_open_memory(data, error, null);
                if (handle == MemoryUtil.NULL) {
                    throw new IllegalStateException("STBVorbis no pudo abrir el OGG (codigo " + error.get(0) + ")");
                }
            }

            int channels;
            int sampleRate;
            STBVorbisInfo info = STBVorbisInfo.malloc();
            try {
                STBVorbis.stb_vorbis_get_info(handle, info);
                channels = info.channels();
                sampleRate = info.sample_rate();
            } finally {
                info.free();
            }

            line = openLine(new AudioFormat(sampleRate, 16, channels, true, false));

            short[] pcm = new short[OGG_CHUNK_SAMPLES * channels];
            while (!this.stopped) {
                int samplesPerChannel = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
                if (samplesPerChannel <= 0) {
                    break;
                }
                applyGainIfNeeded(line);
                byte[] out = toLittleEndian(pcm, samplesPerChannel * channels);
                line.write(out, 0, out.length);
            }
        } finally {
            if (handle != MemoryUtil.NULL) {
                STBVorbis.stb_vorbis_close(handle);
            }
            MemoryUtil.memFree(data);
            closeLine(line);
        }
    }

    // ------------------------------------------------------------ WAV / AIFF

    private void streamSampled() throws Exception {
        SourceDataLine line = null;
        AudioInputStream raw = null;
        AudioInputStream pcmStream = null;

        try {
            raw = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(this.file), 16384));
            AudioFormat source = raw.getFormat();
            AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                source.getSampleRate(),
                16,
                source.getChannels(),
                source.getChannels() * 2,
                source.getSampleRate(),
                false
            );
            pcmStream = AudioSystem.getAudioInputStream(target, raw);
            line = openLine(target);

            byte[] buffer = new byte[8192];
            int read;
            while (!this.stopped && (read = pcmStream.read(buffer)) > 0) {
                applyGainIfNeeded(line);
                line.write(buffer, 0, read);
            }
        } finally {
            closeQuietly(pcmStream);
            closeQuietly(raw);
            closeLine(line);
        }
    }

    // ----------------------------------------------------------------- comunes

    private SourceDataLine openLine(AudioFormat format) throws Exception {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();
        this.activeLine = line;
        this.appliedGeneration = -1;
        applyGain(line);
        return line;
    }

    private void applyGainIfNeeded(SourceDataLine line) {
        if (this.appliedGeneration != this.volumeGeneration) {
            applyGain(line);
            this.appliedGeneration = this.volumeGeneration;
        }
    }

    private void applyGain(SourceDataLine line) {
        if (line == null) {
            return;
        }
        try {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl control = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                float linear = this.volume;
                float decibels = linear <= 0.0001F ? control.getMinimum() : (float) (20.0 * Math.log10(linear));
                control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), decibels)));
            }
        } catch (Exception ignored) {
        }
    }

    private static byte[] toLittleEndian(short[] samples, int length) {
        byte[] out = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            short sample = samples[i];
            out[i * 2] = (byte) (sample & 0xFF);
            out[i * 2 + 1] = (byte) (sample >> 8 & 0xFF);
        }
        return out;
    }

    private void closeLine(SourceDataLine line) {
        if (line == null) {
            return;
        }
        try {
            line.stop();
            line.flush();
            line.close();
        } catch (Exception ignored) {
        }
        if (this.activeLine == line) {
            this.activeLine = null;
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void close() {
        this.stopped = true;

        SourceDataLine line = this.activeLine;
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {
            }
            this.activeLine = null;
        }

        Thread t = this.thread;
        if (t != null) {
            t.interrupt();
            this.thread = null;
        }
    }
}
