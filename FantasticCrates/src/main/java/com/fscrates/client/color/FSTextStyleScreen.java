package com.fscrates.client.color;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public class FSTextStyleScreen extends Screen {
    private static final int SWATCH = 13;
    private static final int PER_ROW = 8;
    private final String titleText;
    private final String sample;
    private final FSTextStyleScreen.Sink onApply;
    private final Runnable onBack;
    private final List<Integer> palette = new ArrayList<>();
    private int current;
    private boolean bold;
    private boolean italic;
    private boolean underline;
    private boolean strike;
    private boolean obf;
    private boolean suppress;
    private ColorWheelWidget wheel;
    private RgbSliderWidget rgb;
    private EditBox hex;
    private int leftX;
    private int paletteX;
    private int paletteY;

    public FSTextStyleScreen(String titleText, String sample, int initial, boolean[] flags, FSTextStyleScreen.Sink onApply, Runnable onBack) {
        super(Component.literal(titleText));
        this.titleText = titleText;
        this.sample = sample != null && !sample.isEmpty() ? sample : "Texto de ejemplo 123";
        this.current = initial & 16777215;
        if (flags != null && flags.length >= 5) {
            this.bold = flags[0];
            this.italic = flags[1];
            this.underline = flags[2];
            this.strike = flags[3];
            this.obf = flags[4];
        }

        this.onApply = onApply;
        this.onBack = onBack;

        for (ChatFormatting f : ChatFormatting.values()) {
            if (f.isColor() && f.getColor() != null) {
                this.palette.add(f.getColor() & 16777215);
            }
        }
    }

    protected void init() {
        this.leftX = this.width / 2 - 120;
        int y = 64;
        this.wheel = (ColorWheelWidget)this.addRenderableWidget(new ColorWheelWidget(this.leftX, y, 120, 60, c -> this.apply(c, true)));
        this.rgb = (RgbSliderWidget)this.addRenderableWidget(new RgbSliderWidget(this.leftX, y + 66, 120, 34, c -> this.apply(c, true)));
        int rx = this.leftX + 130;
        this.hex = (EditBox)this.addRenderableWidget(new EditBox(this.font, rx, y, 100, 16, Component.literal("HEX")));
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
        int syy = y + 92;
        this.addRenderableWidget(this.styleBtn(rx, syy, "N", this.bold, () -> this.bold = !this.bold));
        this.addRenderableWidget(this.styleBtn(rx + 22, syy, "C", this.italic, () -> this.italic = !this.italic));
        this.addRenderableWidget(this.styleBtn(rx + 44, syy, "S", this.underline, () -> this.underline = !this.underline));
        this.addRenderableWidget(this.styleBtn(rx + 66, syy, "T", this.strike, () -> this.strike = !this.strike));
        this.addRenderableWidget(this.styleBtn(rx + 88, syy, "?", this.obf, () -> this.obf = !this.obf));
        this.addRenderableWidget(Button.builder(Component.literal("\u00a7aHecho"), b -> {
            if (this.onApply != null) {
                this.onApply.apply(this.current, this.bold, this.italic, this.underline, this.strike, this.obf);
            }

            this.onClose();
        }).bounds(this.width / 2 + 20, 8, 90, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> this.onClose()).bounds(this.width / 2 - 110, 8, 90, 18).build());
        this.apply(this.current, true);
    }

    private Button styleBtn(int x, int y, String label, boolean state, Runnable toggle) {
        return Button.builder(Component.literal((state ? "\u00a7a\u00a7l" : "\u00a77") + label), b -> {
            toggle.run();
            this.rebuildWidgets();
        }).bounds(x, y, 20, 16).build();
    }

    private void apply(int color, boolean syncHex) {
        this.current = color & 16777215;
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

    public void onClose() {
        if (this.onBack != null) {
            this.onBack.run();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < this.palette.size(); i++) {
                int sx = this.paletteX + i % 8 * 13;
                int sy = this.paletteY + i / 8 * 13;
                if (mouseX >= (double)sx && mouseX < (double)(sx + 13 - 1) && mouseY >= (double)sy && mouseY < (double)(sy + 13 - 1)) {
                    this.apply(this.palette.get(i), true);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int px = this.leftX - 14;
        int pw = 268;
        g.fill(px, 28, px + pw, 244, -400942558);
        g.fill(px, 28, px + pw, 48, -233959916);
        g.fill(px, 243, px + pw, 244, -13412950);
        g.drawString(this.font, "\u00a7b" + this.titleText, this.leftX, 34, 16777215, false);
        super.render(g, mouseX, mouseY, partialTick);

        for (int i = 0; i < this.palette.size(); i++) {
            int sx = this.paletteX + i % 8 * 13;
            int sy = this.paletteY + i / 8 * 13;
            g.fill(sx, sy, sx + 13 - 1, sy + 13 - 1, 0xFF000000 | this.palette.get(i));
            g.renderOutline(sx, sy, 12, 12, -16777216);
        }

        Style st = Style.EMPTY
            .withColor(TextColor.fromRgb(this.current))
            .withBold(this.bold)
            .withItalic(this.italic)
            .withUnderlined(this.underline)
            .withStrikethrough(this.strike)
            .withObfuscated(this.obf);
        MutableComponent prev = Component.literal(this.sample).withStyle(st);
        g.drawString(this.font, "\u00a77Vista previa:", this.leftX, this.paletteY + 34, 11184810, false);
        g.drawString(this.font, prev, this.leftX, this.paletteY + 46, 16777215, true);
        g.drawString(this.font, FSColors.toHex(this.current), this.leftX + 130, 94, 16777215, false);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public interface Sink {
        void apply(int var1, boolean var2, boolean var3, boolean var4, boolean var5, boolean var6);
    }
}
