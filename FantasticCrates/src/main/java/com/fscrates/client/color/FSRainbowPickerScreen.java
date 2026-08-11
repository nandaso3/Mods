package com.fscrates.client.color;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Selector de estilo de arcoiris: rejilla de dos columnas con el nombre de cada
 * estilo y una muestra animada al lado, con scroll.
 *
 * Es el mismo selector que usa Fantastic Holograms, con las mismas paletas.
 */
public class FSRainbowPickerScreen extends Screen {
    private static final int COLS = 2;

    private final IntConsumer onPick;
    private final Runnable onBack;

    private int selected;
    private int scroll;
    private int rowsVisible;
    private int leftPos;
    private int topPos;
    private int panelWidth;
    private int panelHeight;
    private int gridTop;
    private int cellW;
    private int cellH;

    public FSRainbowPickerScreen(int current, IntConsumer onPick, Runnable onBack) {
        super(Component.literal("Estilos de Arcoiris"));
        this.selected = current;
        this.onPick = onPick;
        this.onBack = onBack;
    }

    private int totalRows() {
        return (FSRainbow.count() + COLS - 1) / COLS;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(this.width - 20, 360);
        this.panelHeight = Math.min(this.height - 20, 280);
        this.leftPos = (this.width - this.panelWidth) / 2;
        this.topPos = (this.height - this.panelHeight) / 2;
        this.gridTop = this.topPos + 28;
        int gridBottom = this.topPos + this.panelHeight - 30;
        this.cellW = (this.panelWidth - 24) / COLS;
        this.cellH = 20;
        this.rowsVisible = Math.max(1, (gridBottom - this.gridTop) / this.cellH);
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.totalRows() - this.rowsVisible)));

        int count = FSRainbow.count();
        for (int row = 0; row < this.rowsVisible; row++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (this.scroll + row) * COLS + c;
                if (idx >= count) {
                    continue;
                }
                int bx = this.leftPos + 12 + c * this.cellW;
                int by = this.gridTop + row * this.cellH;
                boolean sel = idx == this.selected;
                String label = (sel ? "\u00a7e\u25b6 \u00a7f" : "\u00a77") + FSRainbow.name(idx);
                this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    this.selected = idx;
                    if (this.onPick != null) {
                        this.onPick.accept(idx);
                    }
                    this.rebuildWidgets();
                }).bounds(bx + 22, by, this.cellW - 26, 18).build());
            }
        }

        this.addRenderableWidget(
            Button.builder(Component.literal("\u00a7aListo"), b -> this.onClose())
                .bounds(this.leftPos + this.panelWidth / 2 - 45, this.topPos + this.panelHeight - 24, 90, 18)
                .build()
        );
    }

    @Override
    public void onClose() {
        if (this.onBack != null) {
            this.onBack.run();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, this.totalRows() - this.rowsVisible);
        int prev = this.scroll;
        this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(delta)));
        if (prev != this.scroll) {
            this.rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);

        int r = this.leftPos + this.panelWidth;
        int b = this.topPos + this.panelHeight;
        g.fill(this.leftPos, this.topPos, r, b, -300410848);
        g.fill(this.leftPos, this.topPos, r, this.topPos + 20, -233959916);
        g.fill(this.leftPos, b - 1, r, b, -12947803);
        g.drawString(
            this.font,
            "\u00a7d\u2726 \u00a7fArcoiris \u00a77(" + FSRainbow.count() + " estilos)",
            this.leftPos + 8,
            this.topPos + 6,
            16777215,
            false
        );

        float time = (System.currentTimeMillis() % 3000L) / 3000.0F;
        int count = FSRainbow.count();

        for (int row = 0; row < this.rowsVisible; row++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (this.scroll + row) * COLS + c;
                if (idx >= count) {
                    continue;
                }
                int bx = this.leftPos + 12 + c * this.cellW;
                int by = this.gridTop + row * this.cellH;
                int seg = 18;
                for (int s = 0; s < seg; s++) {
                    int col = FSRainbow.color(idx, (float) s / seg, time);
                    g.fill(bx + s, by, bx + s + 1, by + 18, 0xFF000000 | col & 0xFFFFFF);
                }
                g.renderOutline(bx, by, 18, 18, idx == this.selected ? -256 : -16777216);
            }
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
