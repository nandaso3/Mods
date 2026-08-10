package com.fscrates.client.color;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class ColorWheelWidget extends AbstractWidget {
    private static final int HUE_STRIP_WIDTH = 12;
    private static final int GAP = 4;
    private float hue;
    private float saturation;
    private float brightness = 1.0F;
    private final IntConsumer onColorChanged;
    private boolean draggingSquare;
    private boolean draggingHue;

    public ColorWheelWidget(int x, int y, int width, int height, IntConsumer onColorChanged) {
        super(x, y, width, height, Component.literal("Color"));
        this.onColorChanged = onColorChanged;
    }

    private int squareWidth() {
        return this.width - 12 - 4;
    }

    private int hueX() {
        return this.getX() + this.width - 12;
    }

    public int getColor() {
        return FSColors.hsbToRgb(this.hue, this.saturation, this.brightness);
    }

    public void setColor(int rgb) {
        float[] hsb = FSColors.rgbToHsb(rgb);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int sqX = this.getX();
        int sqY = this.getY();
        int sqW = this.squareWidth();
        int sqH = this.height;

        for (int i = 0; i < sqW; i++) {
            float s = sqW <= 1 ? 0.0F : (float)i / (float)(sqW - 1);
            int top = 0xFF000000 | FSColors.hsbToRgb(this.hue, s, 1.0F);
            g.fillGradient(sqX + i, sqY, sqX + i + 1, sqY + sqH, top, -16777216);
        }

        g.renderOutline(sqX - 1, sqY - 1, sqW + 2, sqH + 2, -16777216);
        int hx = this.hueX();

        for (int j = 0; j < sqH; j++) {
            float h = sqH <= 1 ? 0.0F : (float)j / (float)(sqH - 1);
            g.fill(hx, sqY + j, hx + 12, sqY + j + 1, 0xFF000000 | FSColors.hsbToRgb(h, 1.0F, 1.0F));
        }

        g.renderOutline(hx - 1, sqY - 1, 14, sqH + 2, -16777216);
        int markerX = sqX + Math.round(this.saturation * (float)(sqW - 1));
        int markerY = sqY + Math.round((1.0F - this.brightness) * (float)(sqH - 1));
        int marker = this.saturation + (1.0F - this.brightness) > 1.0F ? -1 : -16777216;
        g.renderOutline(markerX - 2, markerY - 2, 5, 5, marker);
        int hueMarkerY = sqY + Math.round(this.hue * (float)(sqH - 1));
        g.fill(hx - 1, hueMarkerY - 1, hx + 12 + 1, hueMarkerY + 2, -1);
        g.fill(hx, hueMarkerY, hx + 12, hueMarkerY + 1, -16777216);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !this.active || !this.visible) {
            return false;
        } else if (this.inSquare(mouseX, mouseY)) {
            this.draggingSquare = true;
            this.applySquare(mouseX, mouseY);
            return true;
        } else if (this.inHue(mouseX, mouseY)) {
            this.draggingHue = true;
            this.applyHue(mouseY);
            return true;
        } else {
            return false;
        }
    }

    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (this.draggingSquare) {
            this.applySquare(mouseX, mouseY);
        } else if (this.draggingHue) {
            this.applyHue(mouseY);
        }
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingSquare = false;
        this.draggingHue = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean inSquare(double mx, double my) {
        return mx >= (double)this.getX()
            && mx < (double)(this.getX() + this.squareWidth())
            && my >= (double)this.getY()
            && my < (double)(this.getY() + this.height);
    }

    private boolean inHue(double mx, double my) {
        return mx >= (double)this.hueX()
            && mx < (double)(this.hueX() + 12)
            && my >= (double)this.getY()
            && my < (double)(this.getY() + this.height);
    }

    private void applySquare(double mx, double my) {
        int sqW = this.squareWidth();
        this.saturation = clamp01((float)((mx - (double)this.getX()) / (double)Math.max(1, sqW - 1)));
        this.brightness = clamp01(1.0F - (float)((my - (double)this.getY()) / (double)Math.max(1, this.height - 1)));
        this.fire();
    }

    private void applyHue(double my) {
        this.hue = clamp01((float)((my - (double)this.getY()) / (double)Math.max(1, this.height - 1)));
        this.fire();
    }

    private void fire() {
        if (this.onColorChanged != null) {
            this.onColorChanged.accept(this.getColor());
        }
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }

    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }
}
