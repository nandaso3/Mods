package com.fscrates.client.widget;

/**
 * Estado y geometria del arrastre de la barra de scroll con click derecho.
 *
 * Se comparte entre todos los scrollbars dibujados a mano (ScrollSelector,
 * CratePoolScreen, ...) para no repetir el calculo del thumb en cada pantalla.
 */
public final class ScrollbarDrag {
    /** Alto minimo del thumb en pixeles. */
    public static final int MIN_THUMB = 10;

    private boolean dragging;
    private double anchorMouseY;
    private int anchorScroll;

    /** Alto del thumb segun cuantas filas se ven de cuantas hay. */
    public static int thumbHeight(int trackHeight, int visibleRows, int totalRows) {
        if (totalRows <= 0) {
            return trackHeight;
        }
        return Math.max(MIN_THUMB, Math.min(trackHeight, trackHeight * visibleRows / Math.max(1, totalRows)));
    }

    /** Posicion vertical del thumb dentro del track. */
    public static int thumbTop(int trackTop, int trackHeight, int thumbHeight, int scroll, int maxScroll) {
        if (maxScroll <= 0) {
            return trackTop;
        }
        return trackTop + (trackHeight - thumbHeight) * Math.max(0, Math.min(maxScroll, scroll)) / maxScroll;
    }

    public static boolean overThumb(double mouseX, double mouseY, int barX, int barWidth, int thumbTop, int thumbHeight) {
        return mouseX >= barX && mouseX <= barX + barWidth && mouseY >= thumbTop && mouseY <= thumbTop + thumbHeight;
    }

    /** Empieza a arrastrar guardando el punto de anclaje. */
    public void begin(double mouseY, int currentScroll) {
        this.dragging = true;
        this.anchorMouseY = mouseY;
        this.anchorScroll = currentScroll;
    }

    /** Devuelve el scroll nuevo mientras se arrastra. */
    public int drag(double mouseY, int trackHeight, int thumbHeight, int maxScroll) {
        int usable = trackHeight - thumbHeight;
        if (usable <= 0 || maxScroll <= 0) {
            return this.anchorScroll;
        }
        int delta = (int) Math.round((mouseY - this.anchorMouseY) * maxScroll / usable);
        return Math.max(0, Math.min(maxScroll, this.anchorScroll + delta));
    }

    public void end() {
        this.dragging = false;
    }

    public boolean isDragging() {
        return this.dragging;
    }
}
