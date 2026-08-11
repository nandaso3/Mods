package com.fscrates.client.media;

import com.fscrates.FSCrates;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Quita del MP4 las pistas que no son de video antes de dárselo al decodificador.
 *
 * El motivo es un fallo del decodificador: al abrir un MP4 construye TODAS las
 * pistas del archivo, y si tropieza con la de audio revienta la apertura entera,
 * aunque la de video este perfecta. Concretamente casca parseando el descriptor
 * del AAC:
 *
 *   DescriptorParser.parseSL -> IllegalStateException
 *
 * El resultado era una pantalla en negro con un video correcto: H.264 de 8 bits,
 * yuv420p, todo en orden. Imposible de adivinar mirando el video. No salio antes
 * porque el video con el que se probaba estaba exportado sin audio, y casi
 * cualquier video que descargue o grabe un usuario SI lleva audio.
 *
 * Como el mod nunca reproduce el audio del video (la musica va por su cuenta),
 * la solucion es no darle esas pistas al decodificador.
 *
 * El truco esta en como se quitan: en vez de recortar el archivo se le cambia el
 * nombre de la caja de 'trak' a 'free', que es la caja estandar de "espacio
 * libre, ignorame". Son cuatro bytes en el mismo sitio, asi que el archivo
 * conserva el tamaño EXACTO y nada se mueve. Es importante: las posiciones de
 * los fotogramas dentro del archivo estan guardadas como desplazamientos
 * absolutos, y recortar cualquier cosa las dejaria todas apuntando mal.
 */
public final class Mp4Sanitizer {
    /** Tope de tamaño de la cabecera que se lee a memoria, por prudencia. */
    private static final int MAX_HEADER_BYTES = 32 * 1024 * 1024;

    private Mp4Sanitizer() {
    }

    /**
     * Devuelve el archivo que hay que reproducir, ya sin pistas de audio.
     *
     * Si el archivo no tiene nada que quitar se devuelve el mismo, sin tocarlo.
     * Si algo sale mal tambien se devuelve el original: como maximo se queda como
     * estaba, que es mejor que no reproducir nada.
     */
    public static Path prepare(Path file) {
        if (file == null) {
            return null;
        }

        try {
            List<Long> extra = findNonVideoTracks(file);
            if (extra.isEmpty()) {
                return file;
            }

            Path target = writableCopy(file);
            patch(target, extra);
            FSCrates.LOGGER.info(
                "[FSCrates] '{}': {} pista(s) sin video apartadas para que el video se pueda abrir.",
                file.getFileName(),
                extra.size()
            );
            return target;
        } catch (Throwable t) {
            FSCrates.LOGGER.debug("[FSCrates] No se pudo revisar las pistas de '{}': {}", file, t.toString());
            return file;
        }
    }

    /**
     * Posiciones (absolutas) de las cajas 'trak' que no son de video.
     *
     * Solo se lee la cabecera del archivo, no el contenido: en un video de 60 MB
     * la cabecera son unos pocos KB.
     */
    private static List<Long> findNonVideoTracks(Path file) throws IOException {
        List<Long> found = new ArrayList<>();

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();

            Box moov = findBox(channel, 0L, size, "moov");
            if (moov == null) {
                return found;
            }
            long moovLength = moov.end() - moov.contentStart;
            if (moovLength <= 0 || moovLength > MAX_HEADER_BYTES) {
                return found;
            }

            // La cabecera entera de una vez: dentro hay que ir y venir.
            byte[] moovBytes = new byte[(int) moovLength];
            read(channel, moov.contentStart, ByteBuffer.wrap(moovBytes));

            for (Box trak : children(moovBytes, 0, moovBytes.length, "trak")) {
                String handler = handlerOf(moovBytes, trak);
                if (!"vide".equals(handler)) {
                    found.add(Long.valueOf((long) moov.contentStart + trak.start));
                }
            }
        }

        return found;
    }

    /** El tipo de pista, que esta en trak/mdia/hdlr. */
    private static String handlerOf(byte[] buffer, Box trak) {
        for (Box mdia : children(buffer, trak.contentStart, trak.end(), "mdia")) {
            for (Box hdlr : children(buffer, mdia.contentStart, mdia.end(), "hdlr")) {
                // hdlr: 4 bytes de version y flags, 4 reservados, y luego el tipo.
                int at = hdlr.contentStart + 8;
                if (at + 4 <= buffer.length) {
                    return new String(buffer, at, 4, StandardCharsets.US_ASCII);
                }
            }
        }
        return null;
    }

    /**
     * Da un archivo sobre el que se pueda escribir.
     *
     * Los archivos de la carpeta del mod (la cache y la media que se extrae) se
     * modifican en el sitio: el tamaño no cambia, no se gasta espacio de mas y
     * solo hay que hacerlo una vez. Cualquier otro archivo es del usuario y no se
     * toca: se trabaja sobre una copia en la cache.
     */
    private static Path writableCopy(Path file) throws IOException {
        Path root = MediaCache.cacheRoot();
        Path absolute = file.toAbsolutePath().normalize();
        if (absolute.startsWith(root.getParent() == null ? root : root.getParent())) {
            return file;
        }

        Path folder = root.resolve("sinaudio");
        Files.createDirectories(folder);
        Path copy = folder.resolve(MediaCache.hash(absolute.toString()) + ".mp4");

        // Si ya se preparo antes y sigue cuadrando el tamaño, se reutiliza.
        if (Files.isRegularFile(copy) && Files.size(copy) == Files.size(file)) {
            return copy;
        }
        Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
        return copy;
    }

    /** Renombra a 'free' cada caja indicada. Cuatro bytes por caja. */
    private static void patch(Path file, List<Long> trackOffsets) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            for (long offset : trackOffsets) {
                ByteBuffer free = ByteBuffer.wrap("free".getBytes(StandardCharsets.US_ASCII));
                channel.position(offset + 4);
                while (free.hasRemaining()) {
                    channel.write(free);
                }
            }
        }
    }

    /** Una caja del MP4: donde empieza, cuanto mide y donde empieza su contenido. */
    private static final class Box {
        final int start;
        final long size;
        final int contentStart;

        Box(int start, long size, int contentStart) {
            this.start = start;
            this.size = size;
            this.contentStart = contentStart;
        }

        int end() {
            return (int) (this.start + this.size);
        }
    }

    /** Recorre las cajas de un tramo del buffer y devuelve las del tipo pedido. */
    private static List<Box> children(byte[] buffer, int from, int to, String type) {
        List<Box> out = new ArrayList<>();
        int p = from;
        while (p + 8 <= to) {
            long size = readUint32(buffer, p);
            String boxType = new String(buffer, p + 4, 4, StandardCharsets.US_ASCII);
            int header = 8;

            if (size == 1L) {
                // Tamaño de 64 bits: viene justo despues del tipo.
                if (p + 16 > to) {
                    break;
                }
                size = readUint64(buffer, p + 8);
                header = 16;
            } else if (size == 0L) {
                // Hasta el final del tramo.
                size = to - p;
            }

            if (size < header || p + size > to) {
                break;
            }
            if (boxType.equals(type)) {
                out.add(new Box(p, size, p + header));
            }
            p += (int) size;
        }
        return out;
    }

    /** Igual que children pero leyendo del archivo, para el nivel de arriba. */
    private static Box findBox(FileChannel channel, long from, long to, String type) throws IOException {
        long p = from;
        ByteBuffer head = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);

        while (p + 8 <= to) {
            head.clear();
            head.limit(8);
            read(channel, p, head);
            head.flip();

            long size = head.getInt() & 0xFFFFFFFFL;
            byte[] typeBytes = new byte[4];
            head.get(typeBytes);
            String boxType = new String(typeBytes, StandardCharsets.US_ASCII);
            int header = 8;

            if (size == 1L) {
                head.clear();
                head.limit(8);
                read(channel, p + 8, head);
                head.flip();
                size = head.getLong();
                header = 16;
            } else if (size == 0L) {
                size = to - p;
            }

            if (size < header || p + size > to) {
                return null;
            }
            if (boxType.equals(type)) {
                return new Box((int) p, size, (int) (p + header));
            }
            p += size;
        }
        return null;
    }

    private static void read(FileChannel channel, long position, ByteBuffer into) throws IOException {
        long at = position;
        while (into.hasRemaining()) {
            int n = channel.read(into, at);
            if (n < 0) {
                throw new IOException("archivo cortado en " + at);
            }
            at += n;
        }
    }

    private static long readUint32(byte[] b, int at) {
        return ((long) (b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    private static long readUint64(byte[] b, int at) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (b[at + i] & 0xFF);
        }
        return v;
    }
}
