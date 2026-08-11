package com.fscrates.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Utilidades de dibujo para que todas las pantallas del mod tengan el mismo
 * aspecto: paneles con borde, zonas hundidas para las listas y scrollbars.
 */
public final class FSGui {
    /**
     * Fondo del panel, de arriba y de abajo.
     *
     * Antes era un unico gris casi negro (14,15,20) a un 94% de opacidad, y
     * quedaba muy duro: un rectangulo negro tapando la escena. Ahora es un azul
     * grisaceo con un degradado suave y algo mas de transparencia, asi el video de
     * detras se intuye y la ventana se siente parte de la escena en vez de un
     * parche pegado encima.
     */
    public static final int PANEL_TOP = 0xEE242A38;
    public static final int PANEL_BOTTOM = 0xEE161A24;
    /** Se mantiene por compatibilidad con lo que ya lo usaba. */
    public static final int PANEL_BG = PANEL_TOP;
    /** Borde exterior del panel: mas claro que antes, para que no sea un corte seco. */
    public static final int PANEL_BORDER = 0xFF4C5468;
    /** Brillo superior del panel. */
    public static final int PANEL_HIGHLIGHT = 0xFF737D93;
    /** Fondo de las zonas hundidas (listas). */
    public static final int INSET_BG = 0x4D0E1218;
    public static final int INSET_BORDER = 0xFF383F4E;

    public static final int TEXT = 0xFFE6E6E6;
    public static final int TEXT_DIM = 0xFF9BA1AC;

    public static final int ACCENT_GREEN = 0xFF4CC46A;
    public static final int ACCENT_BLUE = 0xFF4AA8E0;
    public static final int ACCENT_RED = 0xFFD5544F;

    private FSGui() {
    }

    /**
     * Cuanto se mete cada fila para redondear la esquina.
     *
     * Las ventanas van con las puntas redondeadas dibujando las filas de los
     * extremos mas estrechas. Es lo que quita el aire de rectangulo recortado.
     */
    private static int cornerInset(int row, int height) {
        int fromEdge = Math.min(row, height - 1 - row);
        if (fromEdge == 0) {
            return 3;
        }
        if (fromEdge == 1) {
            return 2;
        }
        if (fromEdge == 2) {
            return 1;
        }
        return 0;
    }

    /** Rectangulo redondeado con degradado vertical, fila a fila. */
    private static void roundedGradient(GuiGraphics g, int x, int y, int width, int height, int top, int bottom) {
        for (int row = 0; row < height; row++) {
            int inset = cornerInset(row, height);
            float t = height <= 1 ? 0.0F : (float) row / (height - 1);
            g.fill(x + inset, y + row, x + width - inset, y + row + 1, lerpColor(top, bottom, t));
        }
    }

    /** Mezcla dos colores ARGB conservando la transparencia. */
    private static int lerpColor(int a, int b, float t) {
        int aa = a >>> 24;
        int ba = b >>> 24;
        int alpha = (int) (aa + (ba - aa) * t);
        int r = (int) ((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int gg = (int) ((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (alpha & 0xFF) << 24 | r << 16 | gg << 8 | bl;
    }

    /** Panel con sombra difusa, esquinas redondeadas, degradado y brillo arriba. */
    public static void panel(GuiGraphics g, int x, int y, int width, int height) {
        // Sombra en tres capas cada vez mas flojas: da una caida suave en lugar
        // del recuadro gris de una sola capa que se veia antes.
        for (int ring = 3; ring >= 1; ring--) {
            int alpha = 0x14 * ring;
            roundedGradient(
                g, x - ring, y - ring + 1, width + ring * 2, height + ring * 2,
                alpha << 24, alpha << 24
            );
        }

        roundedGradient(g, x, y, width, height, PANEL_TOP, PANEL_BOTTOM);

        // Borde: solo los cuatro lados, respetando el redondeo.
        borderRounded(g, x, y, width, height, PANEL_BORDER);

        // Brillo interior arriba, para que no se vea plano.
        g.fill(x + 4, y + 1, x + width - 4, y + 2, PANEL_HIGHLIGHT);
        g.fillGradient(x + 2, y + 2, x + width - 2, y + 20, 0x1AFFFFFF, 0x00FFFFFF);
    }

    /** Dibuja solo el contorno de un rectangulo redondeado. */
    private static void borderRounded(GuiGraphics g, int x, int y, int width, int height, int color) {
        for (int row = 0; row < height; row++) {
            int inset = cornerInset(row, height);
            int prev = row == 0 ? -1 : cornerInset(row - 1, height);
            if (row == 0 || row == height - 1 || inset != prev) {
                // Fila de la punta: se pinta entera para cerrar la curva.
                g.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            } else {
                g.fill(x + inset, y + row, x + inset + 1, y + row + 1, color);
                g.fill(x + width - inset - 1, y + row, x + width - inset, y + row + 1, color);
            }
        }
        // Cierre de abajo, que el bucle deja solo con los laterales.
        int lastInset = cornerInset(height - 1, height);
        g.fill(x + lastInset, y + height - 1, x + width - lastInset, y + height, color);
    }

    /** Zona hundida, para listas y cajas de contenido. */
    public static void inset(GuiGraphics g, int x, int y, int width, int height) {
        // Un pelin mas oscura arriba: parece hundida sin necesidad de un borde
        // duro alrededor.
        roundedGradient(g, x, y, width, height, 0x5A0B0E13, 0x3D0E1218);
        borderRounded(g, x, y, width, height, INSET_BORDER);
    }

    /** Barra de scroll con su carril y su thumb. */
    public static void scrollbar(
        GuiGraphics g,
        int x,
        int y,
        int width,
        int trackHeight,
        int thumbTop,
        int thumbHeight,
        boolean highlighted
    ) {
        g.fill(x, y, x + width, y + trackHeight, 0x60000000);

        int body = highlighted ? 0xFF8A93A6 : 0xFF5A6172;
        int edge = highlighted ? 0xFFB6BECD : 0xFF767E90;
        g.fill(x, thumbTop, x + width, thumbTop + thumbHeight, body);
        g.fill(x, thumbTop, x + width, thumbTop + 1, edge);
        g.fill(x, thumbTop + thumbHeight - 1, x + width, thumbTop + thumbHeight, 0xFF3E4450);
    }

    /** Separador horizontal fino. */
    public static void separator(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, 0x30FFFFFF);
    }
}
