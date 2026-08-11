package com.fscrates.client.color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Selector de color: rueda de tono, deslizadores RGB, campo hexadecimal, paleta
 * de los 16 colores clasicos y una vista previa del color elegido.
 *
 * Es el mismo editor que usa Fantastic Holograms, para que los dos mods se
 * manejen igual.
 */
public class FSColorPickerScreen extends Screen {
    private static final int SWATCH = 13;
    private static final int PER_ROW = 8;

    /** Devuelve el color elegido y los cuatro estilos de letra. */
    public interface Sink {
        void apply(int rgb, boolean bold, boolean italic, boolean underline, boolean strike);
    }

    private final String titleText;
    private final String sample;
    private final Sink onApply;
    private final Runnable onBack;
    private final List<Integer> palette = new ArrayList<>();

    private int current;
    private boolean bold;
    private boolean italic;
    private boolean underline;
    private boolean strike;
    private boolean suppress;

    private ColorWheelWidget wheel;
    private RgbSliderWidget rgb;
    private EditBox hex;

    private int leftX;
    private int paletteX;
    private int paletteY;

    public FSColorPickerScreen(
        String titleText,
        String sample,
        int initial,
        boolean[] flags,
        Sink onApply,
        Runnable onBack
    ) {
        super(Component.literal(titleText));
        this.titleText = titleText;
        this.sample = sample == null || sample.isBlank() ? "Texto de ejemplo" : sample;
        this.current = initial & 0xFFFFFF;
        this.onApply = onApply;
        this.onBack = onBack;

        if (flags != null && flags.length >= 4) {
            this.bold = flags[0];
            this.italic = flags[1];
            this.underline = flags[2];
            this.strike = flags[3];
        }

        for (ChatFormatting f : ChatFormatting.values()) {
            if (f.isColor() && f.getColor() != null) {
                this.palette.add(f.getColor() & 0xFFFFFF);
            }
        }
    }

    @Override
    protected void init() {
        this.leftX = this.width / 2 - 120;
        int y = 64;

        this.wheel = this.addRenderableWidget(new ColorWheelWidget(this.leftX, y, 120, 60, c -> this.apply(c, true)));
        this.rgb = this.addRenderableWidget(new RgbSliderWidget(this.leftX, y + 66, 120, 34, c -> this.apply(c, true)));

        int rx = this.leftX + 130;
        this.hex = this.addRenderableWidget(new EditBox(this.font, rx, y, 100, 16, Component.literal("HEX")));
        this.hex.setMaxLength(7);
        this.hex.setFilter(s -> s.matches("#?[0-9a-fA-F]{0,6}"));
        this.hex.setResponder(s -> {
            if (!this.suppress) {
                String v = s.startsWith("#") ? s.substring(1) : s;
                if (v.length() == 6) {
                    this.apply(FSColors.parse(s, this.current), false);
                }
            }
        });

        this.paletteX = this.leftX;
        this.paletteY = y + 108;

        // Estilos de letra: negrita, cursiva, subrayado y tachado.
        int sx = rx;
        int sy = y + 46;
        int sw = 49;
        this.addStyleToggle(sx, sy, sw, "\u00a7lNegrita", this.bold, () -> this.bold = !this.bold);
        this.addStyleToggle(sx + sw + 2, sy, sw, "\u00a7oCursiva", this.italic, () -> this.italic = !this.italic);
        this.addStyleToggle(sx, sy + 20, sw, "\u00a7nSubray.", this.underline, () -> this.underline = !this.underline);
        this.addStyleToggle(sx + sw + 2, sy + 20, sw, "\u00a7mTachado", this.strike, () -> this.strike = !this.strike);

        this.addRenderableWidget(Button.builder(Component.literal("\u00a7aHecho"), b -> {
            if (this.onApply != null) {
                this.onApply.apply(this.current, this.bold, this.italic, this.underline, this.strike);
            }
            this.onClose();
        }).bounds(this.width / 2 + 20, 8, 90, 18).build());

        this.addRenderableWidget(
            Button.builder(Component.literal("Cancelar"), b -> this.onClose())
                .bounds(this.width / 2 - 110, 8, 90, 18)
                .build()
        );

        this.apply(this.current, true);
    }

    private void addStyleToggle(int x, int y, int w, String label, boolean state, Runnable toggle) {
        this.addRenderableWidget(
            Button.builder(Component.literal((state ? "\u00a7a\u2714 " : "\u00a77") + label), b -> {
                toggle.run();
                this.rebuildWidgets();
            }).bounds(x, y, w, 18).build()
        );
    }

    private void apply(int color, boolean syncHex) {
        this.current = color & 0xFFFFFF;
        if (this.wheel != null) {
            this.wheel.setColor(this.current);
        }
        if (this.rgb != null) {
            this.rgb.setColor(this.current);
        }
        if (syncHex && this.hex != null) {
            this.suppress = true;
            this.hex.setValue(FSColors.toHex(this.current));
            this.suppress = false;
        }
    }

    @Override
    public void onClose() {
        if (this.onBack != null) {
            this.onBack.run();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < this.palette.size(); i++) {
                int sx = this.paletteX + i % PER_ROW * SWATCH;
                int sy = this.paletteY + i / PER_ROW * SWATCH;
                if (mouseX >= sx && mouseX < sx + SWATCH - 1 && mouseY >= sy && mouseY < sy + SWATCH - 1) {
                    this.apply(this.palette.get(i), true);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int px = this.leftX - 14;
        int pw = 268;
        g.fill(px, 28, px + pw, 232, -400942558);
        g.fill(px, 28, px + pw, 48, -233959916);
        g.fill(px, 231, px + pw, 232, -13412950);
        g.drawString(this.font, "\u00a7b" + this.titleText, this.leftX, 34, 16777215, false);

        super.render(g, mouseX, mouseY, partialTick);

        for (int i = 0; i < this.palette.size(); i++) {
            int sx = this.paletteX + i % PER_ROW * SWATCH;
            int sy = this.paletteY + i / PER_ROW * SWATCH;
            g.fill(sx, sy, sx + SWATCH - 1, sy + SWATCH - 1, 0xFF000000 | this.palette.get(i));
            g.renderOutline(sx, sy, SWATCH - 1, SWATCH - 1, -16777216);
        }

        int rx = this.leftX + 130;
        g.fill(rx, 24 + 6, rx + 100, 24 + 22, 0xFF000000 | this.current);
        g.renderOutline(rx, 24 + 6, 100, 16, -16777216);
        g.drawString(this.font, FSColors.toHex(this.current), rx + 104, 24 + 10, -1, false);

        // Vista previa con el color y los estilos aplicados de verdad.
        StringBuilder codes = new StringBuilder();
        if (this.bold) {
            codes.append("\u00a7l");
        }
        if (this.italic) {
            codes.append("\u00a7o");
        }
        if (this.underline) {
            codes.append("\u00a7n");
        }
        if (this.strike) {
            codes.append("\u00a7m");
        }
        String preview = codes + this.font.plainSubstrByWidth(this.sample, 120);
        g.drawString(this.font, "\u00a77Vista previa:", rx, 196, 0xFFAAAAAA, false);
        g.fill(rx - 2, 206, rx + 128, 224, 0x60000000);
        g.drawString(this.font, preview, rx, 211, 0xFF000000 | this.current, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
