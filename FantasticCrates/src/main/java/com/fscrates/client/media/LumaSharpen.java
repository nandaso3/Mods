package com.fscrates.client.media;

/**
 * Enfoca el brillo del fotograma cuando la imagen se va a ampliar.
 *
 * El caso: si el video mide justo lo que el hueco de la pantalla se dibuja pixel
 * a pixel y sale igual de nitido que el archivo. Pero si mide menos (un 720p en
 * una pantalla 1080p) la grafica lo estira interpolando, y eso siempre ablanda
 * los bordes. Aplicando una mascara de enfoque ANTES de ampliar se recupera
 * parte de esa definicion, que es como se hace normalmente: enfocar antes de
 * escalar da mejor resultado que enfocar despues.
 *
 * Solo toca el brillo, no el color. El ojo saca el detalle del brillo, y enfocar
 * el color solo produciria bordes de colores raros.
 *
 * La fuerza va con lo que haya que ampliar y esta topada bajo a proposito: pasado
 * cierto punto el enfoque deja de recuperar detalle y empieza a dibujar halos
 * blancos en los contornos, que es ese aspecto artificial de "sobreprocesado".
 * Si no hay que ampliar no se toca nada.
 */
public final class LumaSharpen {
    /** Por debajo de esta ampliacion no merece la pena tocar el fotograma. */
    private static final float MIN_SCALE = 1.05F;

    /** Tope de fuerza. Mas que esto empieza a marcar halos. */
    private static final float MAX_AMOUNT = 0.35F;

    private LumaSharpen() {
    }

    /**
     * Fuerza de enfoque para una ampliacion dada, o 0 si no hay que enfocar.
     *
     * Crece con la ampliacion porque cuanto mas se estira mas se ablanda, y se
     * satura en el tope: a partir de ampliar al doble ya no se gana nada por
     * insistir.
     */
    public static float amountFor(float scale) {
        if (scale <= MIN_SCALE || Float.isNaN(scale)) {
            return 0.0F;
        }
        float amount = (scale - 1.0F) * 0.5F;
        return Math.min(MAX_AMOUNT, amount);
    }

    /**
     * Enfoca una fila del plano de brillo.
     *
     * Se lee de src y se escribe en dst para que las filas se puedan repartir
     * entre hilos sin pisarse: si se hiciera en el mismo array, una fila leeria
     * las vecinas ya modificadas y el resultado dependeria del orden.
     *
     * Las muestras vienen como bytes con signo (el valor real menos 128), y como
     * la mascara de enfoque es una resta de valores del mismo tipo, la cuenta
     * sale igual sin necesidad de convertirlos.
     */
    public static void row(byte[] src, byte[] dst, int w, int h, int y, int amount8) {
        int row = y * w;
        int up = (y > 0 ? y - 1 : 0) * w;
        int down = (y < h - 1 ? y + 1 : h - 1) * w;
        int last = w - 1;

        for (int x = 0; x < w; x++) {
            int left = x > 0 ? x - 1 : 0;
            int right = x < last ? x + 1 : last;

            int center = src[row + x];
            // Media de los 8 vecinos y el centro (caja 3x3): la version borrosa
            // con la que se compara.
            int sum = src[up + left] + src[up + x] + src[up + right]
                + src[row + left] + center + src[row + right]
                + src[down + left] + src[down + x] + src[down + right];
            int blurred = (sum + 4) / 9;

            // centro + fuerza * (centro - borroso): realza lo que el desenfoque
            // se habria comido, o sea los bordes.
            int sharpened = center + ((amount8 * (center - blurred)) >> 8);
            dst[row + x] = (byte) (sharpened < -128 ? -128 : (sharpened > 127 ? 127 : sharpened));
        }
    }
}
