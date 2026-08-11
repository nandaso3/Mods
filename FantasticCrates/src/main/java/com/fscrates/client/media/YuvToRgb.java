package com.fscrates.client.media;

import com.fscrates.FSCrates;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.jcodec.api.FrameGrab;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.VUIParameters;

/**
 * Convierte los planos YUV que suelta el decodificador a pixeles RGBA.
 *
 * Esta clase existe porque la conversion anterior estaba mal de dos formas
 * distintas, y las dos afectaban a TODOS los videos:
 *
 *  1. Usaba los coeficientes de BT.601 en rango completo (los de JPEG). Todo
 *     video HD esta en BT.709 y rango limitado. Al no expandir el rango
 *     (16-235 -> 0-255) la imagen sale lavada, con los negros grises y los
 *     blancos apagados: menos contraste, que es exactamente lo que uno percibe
 *     como "poco nitido" aunque el detalle este ahi. Y con la matriz
 *     equivocada los colores se desvian (se nota en rojos y verdes).
 *
 *  2. Repetia el color en bloques de 2x2. En 4:2:0 hay una muestra de color por
 *     cada cuatro de brillo, y copiarla tal cual a los cuatro pixeles deja los
 *     bordes de color escalonados. Interpolando se recupera la transicion.
 *
 * Lo que se aplica se saca de los metadatos del propio archivo (el SPS/VUI del
 * H.264), no de una suposicion: ahi viene declarada la matriz y el rango. Solo
 * si el archivo no lo declara se recurre a deducirlo por la resolucion, que es
 * lo mismo que hace ffmpeg en ese caso.
 *
 * Las cuentas van con tablas precalculadas en punto fijo 16.16. Cada pixel sale
 * de cuatro consultas a tabla y tres sumas, asi que a pesar de hacer mas trabajo
 * que la version anterior (que multiplicaba por pixel) no cuesta mas.
 */
public final class YuvToRgb {
    /** Matriz de conversion declarada en el archivo. */
    public enum Matrix {
        BT601("BT.601"),
        BT709("BT.709"),
        BT2020("BT.2020");

        private final String label;

        Matrix(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    /**
     * Coeficientes por matriz y rango: {luma, r<-v, g<-u, g<-v, b<-u}.
     *
     * Los de rango limitado ya llevan dentro la expansion (255/219 para el
     * brillo y 255/224 para el color), por eso el primero es 1.164 y no 1.
     */
    private static final double[] BT601_LIMITED = {1.164383, 1.596027, -0.391762, -0.812968, 2.017232};
    private static final double[] BT601_FULL = {1.0, 1.402000, -0.344136, -0.714136, 1.772000};
    private static final double[] BT709_LIMITED = {1.164383, 1.792741, -0.213249, -0.532909, 2.112402};
    private static final double[] BT709_FULL = {1.0, 1.574800, -0.187324, -0.468124, 1.855600};
    private static final double[] BT2020_LIMITED = {1.164384, 1.678674, -0.187326, -0.650424, 2.141772};
    private static final double[] BT2020_FULL = {1.0, 1.474600, -0.164553, -0.571353, 1.881400};

    /** Brillo ya escalado, indexado por el valor de la muestra (0-255). */
    private final int[] yTab = new int[256];
    private final int[] rFromV = new int[256];
    private final int[] gFromU = new int[256];
    private final int[] gFromV = new int[256];
    private final int[] bFromU = new int[256];

    private final Matrix matrix;
    private final boolean fullRange;
    private final boolean declared;

    private YuvToRgb(Matrix matrix, boolean fullRange, boolean declared) {
        this.matrix = matrix;
        this.fullRange = fullRange;
        this.declared = declared;

        double[] k;
        switch (matrix) {
            case BT601:
                k = fullRange ? BT601_FULL : BT601_LIMITED;
                break;
            case BT2020:
                k = fullRange ? BT2020_FULL : BT2020_LIMITED;
                break;
            case BT709:
            default:
                k = fullRange ? BT709_FULL : BT709_LIMITED;
                break;
        }

        int black = fullRange ? 0 : 16;
        for (int i = 0; i < 256; i++) {
            this.yTab[i] = (int) Math.round(k[0] * (i - black) * 65536.0);
            int c = i - 128;
            this.rFromV[i] = (int) Math.round(k[1] * c * 65536.0);
            this.gFromU[i] = (int) Math.round(k[2] * c * 65536.0);
            this.gFromV[i] = (int) Math.round(k[3] * c * 65536.0);
            this.bFromU[i] = (int) Math.round(k[4] * c * 65536.0);
        }
    }

    public Matrix matrix() {
        return this.matrix;
    }

    public boolean fullRange() {
        return this.fullRange;
    }

    /** Describe lo aplicado, para el log. */
    public String describe() {
        return this.matrix + (this.fullRange ? " rango completo" : " rango limitado")
            + (this.declared ? " (declarado en el archivo)" : " (deducido por la resolucion)");
    }

    /**
     * Decide la conversion de un archivo leyendo sus metadatos.
     *
     * Si no se puede leer nada se deduce por la altura, que es la convencion:
     * de 720 lineas para arriba BT.709, por debajo BT.601, y rango limitado, que
     * es lo que produce cualquier herramienta normal.
     */
    public static YuvToRgb forFile(Path file, int width, int height) {
        try {
            VUIParameters vui = readVui(file);
            if (vui != null) {
                boolean full = vui.videoSignalTypePresentFlag && vui.videoFullRangeFlag;
                if (vui.colourDescriptionPresentFlag) {
                    Matrix m = fromCoefficients(vui.matrixCoefficients);
                    if (m != null) {
                        return new YuvToRgb(m, full, true);
                    }
                }
                // El rango si estaba declarado aunque la matriz no.
                if (vui.videoSignalTypePresentFlag) {
                    return new YuvToRgb(byHeight(height), full, false);
                }
            }
        } catch (Throwable t) {
            FSCrates.LOGGER.debug("[FSCrates] No se pudieron leer los metadatos de color: {}", t.toString());
        }
        return new YuvToRgb(byHeight(height), false, false);
    }

    /** Conversion por defecto, para cuando no hay archivo del que leer. */
    public static YuvToRgb byResolution(int width, int height) {
        return new YuvToRgb(byHeight(height), false, false);
    }

    private static Matrix byHeight(int height) {
        return height >= 720 ? Matrix.BT709 : Matrix.BT601;
    }

    /** Valores de matrix_coefficients de la norma H.264 (tabla E-5). */
    private static Matrix fromCoefficients(int code) {
        switch (code) {
            case 1:
                return Matrix.BT709;
            case 4:
            case 5:
            case 6:
                return Matrix.BT601;
            case 9:
            case 10:
                return Matrix.BT2020;
            default:
                // 0 (GBR), 2 (sin especificar), 8 (YCgCo)... nada util: se deduce.
                return null;
        }
    }

    /**
     * Saca el VUI del SPS que va en la cabecera de la pista.
     *
     * Se abre el archivo una vez al empezar a reproducir, no por fotograma. El
     * canal se cierra siempre: es Closeable y dejarlo abierto va acumulando
     * descriptores.
     */
    private static VUIParameters readVui(Path file) throws Exception {
        org.jcodec.common.io.SeekableByteChannel channel = null;
        try {
            channel = NIOUtils.readableChannel(new File(file.toString()));
            ByteBuffer priv = FrameGrab.createFrameGrab(channel).getVideoTrack().getMeta().getCodecPrivate();
            if (priv == null) {
                return null;
            }
            ByteBuffer sps = findNal(priv, 7);
            if (sps == null) {
                return null;
            }
            SeqParameterSet parsed = H264Utils.readSPS(sps);
            return parsed == null ? null : parsed.vuiParams;
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (Throwable ignored) {
                    // da igual: solo se leian metadatos
                }
            }
        }
    }

    /**
     * Busca un NAL de un tipo concreto en un buffer en formato Annex B.
     *
     * La cabecera de la pista llega como una tira de NALs separados por
     * 00 00 01 (o 00 00 00 01). Se devuelve el contenido SIN el byte de
     * cabecera, que es lo que espera readSPS.
     */
    private static ByteBuffer findNal(ByteBuffer buffer, int nalType) {
        byte[] b = new byte[buffer.remaining()];
        buffer.duplicate().get(b);

        for (int i = 0; i + 4 < b.length; i++) {
            boolean start3 = b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 1;
            boolean start4 = b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 0 && b[i + 3] == 1;
            if (!start3 && !start4) {
                continue;
            }

            int start = i + (start4 ? 4 : 3);
            if (start >= b.length || (b[start] & 0x1F) != nalType) {
                continue;
            }

            int end = b.length;
            for (int j = start + 1; j + 2 < b.length; j++) {
                if (b[j] == 0 && b[j + 1] == 0
                    && (b[j + 2] == 1 || (b[j + 2] == 0 && j + 3 < b.length && b[j + 3] == 1))) {
                    end = j;
                    break;
                }
            }
            return ByteBuffer.wrap(b, start + 1, end - start - 1);
        }
        return null;
    }

    /**
     * Convierte una fila entera a RGBA.
     *
     * El color se interpola en vez de repetirse. En H.264 4:2:0 las muestras de
     * color caen alineadas con las columnas pares y a media altura entre dos
     * filas, asi que:
     *   - en horizontal, las columnas pares cogen la muestra tal cual y las
     *     impares la media de las dos vecinas;
     *   - en vertical siempre se mezcla con la fila de al lado, 3 partes de la
     *     propia por 1 de la vecina.
     */
    public void row(
        int y,
        byte[] luma,
        byte[] cb,
        byte[] cr,
        int w,
        int h,
        int cw,
        int ch,
        int[] out
    ) {
        int lumaRow = y * w;

        int near = y >> 1;
        // La fila de color vecina hacia la que se inclina la mezcla.
        int far = (y & 1) == 0 ? near - 1 : near + 1;
        if (far < 0) {
            far = 0;
        } else if (far > ch - 1) {
            far = ch - 1;
        }
        int nearRow = near * cw;
        int farRow = far * cw;

        // Se recorre de dos en dos pixeles (el par y el impar de cada muestra de
        // color) en vez de uno a uno preguntando si es par. Asi no hay un salto
        // condicional por pixel y las dos muestras que comparten se leen una
        // sola vez, que en 2 millones de pixeles por fotograma se nota.
        int lastCol = cw - 1;
        int x = 0;
        for (int cx = 0; cx < cw && x < w; cx++, x += 2) {
            int cx2 = cx < lastCol ? cx + 1 : lastCol;

            int uNear = cb[nearRow + cx] + 128;
            int uFar = cb[farRow + cx] + 128;
            int vNear = cr[nearRow + cx] + 128;
            int vFar = cr[farRow + cx] + 128;

            // Pixel par: la muestra de color cae justo en esta columna, solo se
            // mezcla en vertical.
            this.write(out, lumaRow + x, luma[lumaRow + x] + 128,
                (3 * uNear + uFar + 2) >> 2, (3 * vNear + vFar + 2) >> 2);

            if (x + 1 >= w) {
                break;
            }

            // Pixel impar: cae a medio camino entre esta muestra y la siguiente.
            int uNext = cb[nearRow + cx2] + 128;
            int uFarNext = cb[farRow + cx2] + 128;
            int vNext = cr[nearRow + cx2] + 128;
            int vFarNext = cr[farRow + cx2] + 128;
            this.write(out, lumaRow + x + 1, luma[lumaRow + x + 1] + 128,
                (3 * ((uNear + uNext + 1) >> 1) + ((uFar + uFarNext + 1) >> 1) + 2) >> 2,
                (3 * ((vNear + vNext + 1) >> 1) + ((vFar + vFarNext + 1) >> 1) + 2) >> 2);
        }
    }

    /** Escribe un pixel ya con su color resuelto. */
    private void write(int[] out, int index, int lumaValue, int u, int v) {
        int yy = this.yTab[lumaValue];
        int r = (yy + this.rFromV[v]) >> 16;
        int g = (yy + this.gFromU[u] + this.gFromV[v]) >> 16;
        int b = (yy + this.bFromU[u]) >> 16;

        // El buffer va en orden nativo (little endian), asi que este int
        // aterriza en memoria como R, G, B, A: lo que espera GL_RGBA.
        out[index] = 0xFF000000 | (clamp(b) << 16) | (clamp(g) << 8) | clamp(r);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
