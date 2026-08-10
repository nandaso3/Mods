package com.fscrates.client.widget;

/**
 * Estado y geometria del arrastre de la barra de scroll.
 *
 * Se comparte entre todos los scrollbars dibujados a mano (ScrollSelector,
 * CratePoolScreen, CrateEditorScreen, NbtEditorScreen) para no repetir el
 * calculo del thumb en cada pantalla.
 *
 * Funciona con click IZQUIERDO y con click derecho, y ademas se puede hacer
 * click en cualquier punto de la barra para saltar directamente ahi.
 */
public final class ScrollbarDrag {
    /** Alto minimo del thumb en pixeles. */
    public static final int MIN_THUMB = 12;

    private boolean dragging;
    private double anchorMouseY;
    private int anchorScroll;

    /** Botones validos para arrastrar: izquierdo (0) y derecho (1). */
    public static boolean isDragButton(int button) {
        return button == 0 || button == 1;
    }

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

    /** Toda la barra, no solo el thumb (con un par de pixeles de margen para acertar mejor). */
    public static boolean overTrack(double mouseX, double mouseY, int barX, int barWidth, int trackTop, int trackHeight) {
        return mouseX >= barX - 2
            && mouseX <= barX + barWidth + 2
            && mouseY >= trackTop
            && mouseY <= trackTop + trackHeight;
    }

    /**
     * Empieza a arrastrar. Si el click cae fuera del thumb, primero salta a esa
     * posicion (centrando el thumb en el cursor) y luego sigue arrastrando desde ahi.
     *
     * @return el scroll que hay que aplicar
     */
    public int beginOnTrack(double mouseY, int currentScroll, int trackTop, int trackHeight, int thumbHeight, int maxScroll) {
        int scroll = currentScroll;
        int thumb = thumbTop(trackTop, trackHeight, thumbHeight, currentScroll, maxScroll);

        boolean onThumb = mouseY >= thumb && mouseY <= thumb + thumbHeight;
        if (!onThumb) {
            int usable = trackHeight - thumbHeight;
            if (usable > 0 && maxScroll > 0) {
                double target = mouseY - trackTop - thumbHeight / 2.0;
                scroll = (int) Math.round(target * maxScroll / usable);
                scroll = Math.max(0, Math.min(maxScroll, scroll));
            }
        }

        this.dragging = true;
        this.anchorMouseY = mouseY;
        this.anchorScroll = scroll;
        return scroll;
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
