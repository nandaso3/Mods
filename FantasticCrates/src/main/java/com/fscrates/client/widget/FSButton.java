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

    /**
     * Estilo clasico tipo Minecraft: gris con biselado (claro arriba, oscuro
     * abajo) y borde negro. El color de acento solo se usa como toque sutil en
     * el borde al pasar el raton, para no romper el aire vanilla.
     */
    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        boolean hovered = this.isHoveredOrFocused() && this.active;

        // Borde exterior negro.
        g.fill(x, y, x + w, y + h, 0xFF000000);

        int face;
        int light;
        int dark;
        if (!this.active) {
            face = 0xFF4A4A4A;
            light = 0xFF5C5C5C;
            dark = 0xFF303030;
        } else if (hovered) {
            face = 0xFF9098A6;
            light = 0xFFB6BECB;
            dark = 0xFF565D6B;
        } else {
            face = 0xFF6E6E6E;
            light = 0xFF8C8C8C;
            dark = 0xFF3F3F3F;
        }

        // Cara del boton.
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        // Biselado: brillo arriba y a la izquierda, sombra abajo y a la derecha.
        g.fill(x + 1, y + 1, x + w - 1, y + 2, light);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, light);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, dark);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, dark);

        // Toque de acento en el borde inferior cuando el raton esta encima.
        if (hovered) {
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, this.accent);
        }

        int textColor = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
        var font = Minecraft.getInstance().font;
        // Centrado exacto, tanto horizontal como vertical.
        int textY = y + (h - font.lineHeight) / 2 + 1;
        if (this.centered) {
            g.drawCenteredString(font, this.getMessage(), x + w / 2, textY, textColor);
        } else {
            g.drawString(font, this.getMessage(), x + 6, textY, textColor, false);
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
