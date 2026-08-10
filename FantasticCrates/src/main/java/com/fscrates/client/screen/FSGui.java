package com.fscrates.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Utilidades de dibujo para que todas las pantallas del mod tengan el mismo
 * aspecto: paneles con borde, zonas hundidas para las listas y scrollbars.
 */
public final class FSGui {
    /** Fondo de panel. */
    public static final int PANEL_BG = 0xF00E0F14;
    /** Borde exterior del panel. */
    public static final int PANEL_BORDER = 0xFF3A3F4B;
    /** Brillo superior del panel. */
    public static final int PANEL_HIGHLIGHT = 0xFF565D6E;
    /** Fondo de las zonas hundidas (listas). */
    public static final int INSET_BG = 0x80000000;
    public static final int INSET_BORDER = 0xFF2A2E38;

    public static final int TEXT = 0xFFE6E6E6;
    public static final int TEXT_DIM = 0xFF9BA1AC;

    public static final int ACCENT_GREEN = 0xFF4CC46A;
    public static final int ACCENT_BLUE = 0xFF4AA8E0;
    public static final int ACCENT_RED = 0xFFD5544F;

    private FSGui() {
    }

    /** Panel con sombra, borde y una linea de brillo arriba. */
    public static void panel(GuiGraphics g, int x, int y, int width, int height) {
        // Sombra suave alrededor.
        g.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0x40000000);

        g.fill(x, y, x + width, y + height, PANEL_BG);

        // Borde de 1px.
        g.fill(x, y, x + width, y + 1, PANEL_BORDER);
        g.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);
        g.fill(x, y, x + 1, y + height, PANEL_BORDER);
        g.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);

        // Brillo interior arriba, para que no se vea plano.
        g.fill(x + 1, y + 1, x + width - 1, y + 2, PANEL_HIGHLIGHT);
        g.fillGradient(x + 1, y + 2, x + width - 1, y + 18, 0x18FFFFFF, 0x00FFFFFF);
    }

    /** Zona hundida, para listas y cajas de contenido. */
    public static void inset(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, INSET_BG);
        g.fill(x, y, x + width, y + 1, INSET_BORDER);
        g.fill(x, y + height - 1, x + width, y + height, INSET_BORDER);
        g.fill(x, y, x + 1, y + height, INSET_BORDER);
        g.fill(x + width - 1, y, x + width, y + height, INSET_BORDER);
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
