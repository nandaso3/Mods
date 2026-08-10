package com.fscrates.client.color;

import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class RgbSliderWidget extends AbstractWidget {
    private static final int LABEL_WIDTH = 14;
    private int red;
    private int green;
    private int blue;
    private final IntConsumer onColorChanged;
    private int draggingChannel = -1;

    public RgbSliderWidget(int x, int y, int width, int height, IntConsumer onColorChanged) {
        super(x, y, width, height, Component.literal("RGB"));
        this.onColorChanged = onColorChanged;
    }

    public int getColor() {
        return this.red << 16 | this.green << 8 | this.blue;
    }

    public void setColor(int rgb) {
        this.red = rgb >> 16 & 0xFF;
        this.green = rgb >> 8 & 0xFF;
        this.blue = rgb & 0xFF;
    }

    private int rowHeight() {
        return this.height / 3;
    }

    private int trackX() {
        return this.getX() + 14;
    }

    private int trackWidth() {
        return this.width - 14;
    }

    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int rowH = this.rowHeight();
        int[] values = new int[]{this.red, this.green, this.blue};
        String[] labels = new String[]{"R", "G", "B"};
        int[] baseColors = new int[]{-43691, -11149995, -11167233};

        for (int k = 0; k < 3; k++) {
            int rowY = this.getY() + k * rowH;
            int midY = rowY + rowH / 2;
            g.drawString(Minecraft.getInstance().font, labels[k], this.getX(), midY - 4, baseColors[k], false);
            g.fill(this.trackX(), midY - 2, this.trackX() + this.trackWidth(), midY + 2, -14671840);
            g.renderOutline(this.trackX(), midY - 2, this.trackWidth(), 4, -16777216);
            int knobX = this.trackX() + Math.round((float)values[k] / 255.0F * (float)(this.trackWidth() - 1));
            g.fill(knobX - 2, rowY + 2, knobX + 3, rowY + rowH - 2, baseColors[k]);
            g.renderOutline(knobX - 2, rowY + 2, 5, rowH - 4, -16777216);
            g.drawString(Minecraft.getInstance().font, String.valueOf(values[k]), this.trackX() + this.trackWidth() - 22, rowY + 1, -5592406, false);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int channel;
        if (button == 0
            && this.active
            && this.visible
            && mouseX >= (double)this.trackX()
            && mouseX <= (double)(this.trackX() + this.trackWidth())
            && mouseY >= (double)this.getY()
            && mouseY <= (double)(this.getY() + this.height)
            && (channel = (int)((mouseY - (double)this.getY()) / (double)this.rowHeight())) >= 0
            && channel < 3) {
            this.draggingChannel = channel;
            this.applyChannel(channel, mouseX);
            return true;
        } else {
            return false;
        }
    }

    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (this.draggingChannel >= 0) {
            this.applyChannel(this.draggingChannel, mouseX);
        }
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingChannel = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void applyChannel(int channel, double mouseX) {
        float ratio = (float)((mouseX - (double)this.trackX()) / (double)Math.max(1, this.trackWidth() - 1));
        int value = Math.max(0, Math.min(255, Math.round(ratio * 255.0F)));
        switch (channel) {
            case 0:
                this.red = value;
                break;
            case 1:
                this.green = value;
                break;
            default:
                this.blue = value;
        }

        if (this.onColorChanged != null) {
            this.onColorChanged.accept(this.getColor());
        }
    }

    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }
}
