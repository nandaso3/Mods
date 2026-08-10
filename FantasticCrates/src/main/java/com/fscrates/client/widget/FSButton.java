package com.fscrates.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Boton plano con color de acento, para las pantallas del mod.
 *
 * Se dibuja a mano en vez de usar la textura de boton de vanilla: asi se puede
 * dar un acento de color (verde para abrir, azul para secundario) y se ve mejor
 * encima del video de la pantalla de pre-apertura.
 */
public class FSButton extends AbstractButton {
    private final int accent;
    private final Runnable action;
    private boolean centered = true;

    public FSButton(int x, int y, int width, int height, Component message, int accent, Runnable action) {
        super(x, y, width, height, message);
        this.accent = accent;
        this.action = action;
    }

    public FSButton leftAligned() {
        this.centered = false;
        return this;
    }

    @Override
    public void onPress() {
        if (this.action != null) {
            this.action.run();
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        boolean hovered = this.isHoveredOrFocused() && this.active;

        // Sombra inferior.
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, 0x50000000);

        int base = this.active ? (hovered ? 0xFF2C3340 : 0xFF20242E) : 0xFF191C23;
        g.fill(x, y, x + w, y + h, base);

        // Degradado interior para dar volumen.
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, hovered ? 0x28FFFFFF : 0x14FFFFFF, 0x00FFFFFF);

        // Borde: usa el acento cuando esta activo o el raton encima.
        int border = this.active ? (hovered ? this.accent : mix(this.accent, 0xFF000000, 0.45F)) : 0xFF2A2E38;
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);

        // Barrita de acento a la izquierda.
        if (this.active) {
            g.fill(x + 1, y + 1, x + 3, y + h - 1, hovered ? this.accent : mix(this.accent, 0xFF000000, 0.25F));
        }

        int textColor = this.active ? 0xFFFFFFFF : 0xFF6E7480;
        var font = Minecraft.getInstance().font;
        int textY = y + (h - 8) / 2;
        if (this.centered) {
            g.drawCenteredString(font, this.getMessage(), x + w / 2 + 1, textY, textColor);
        } else {
            g.drawString(font, this.getMessage(), x + 8, textY, textColor, false);
        }
    }

    /** Mezcla dos colores ARGB. amount = cuanto del segundo. */
    private static int mix(int a, int b, float amount) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * amount);
        int gg = (int) (ag + (bg - ag) * amount);
        int bl = (int) (ab + (bb - ab) * amount);
        return 0xFF000000 | r << 16 | gg << 8 | bl;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
