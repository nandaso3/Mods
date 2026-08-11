package com.fscrates.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Boton del mod: esquinas redondeadas, degradado teñido con su color de acento y
 * una transicion suave al pasar el raton.
 *
 * Se dibuja a mano en vez de usar la textura de vanilla por dos razones: para
 * poder darle el color de acento de cada accion (verde para abrir, azul para lo
 * secundario) y porque encima de un video hace falta mas contraste del que da la
 * textura plana. De ahi la sombra exterior y el borde oscuro, que es lo que
 * mantiene el boton legible sobre cualquier imagen.
 *
 * El redondeo se consigue dibujando fila a fila y metiendo hacia dentro las de
 * los extremos, en vez de con una textura. Asi funciona a cualquier tamaño y el
 * degradado sale exacto en cada fila.
 */
public class FSButton extends AbstractButton {
    /** Lo que tarda la animacion al pasar o quitar el raton. */
    private static final float HOVER_MS = 160.0F;

    /** Cuantas motas de luz suben por dentro del boton. */
    private static final int MOTES = 5;

    private final int accent;
    private final Runnable action;
    private boolean centered = true;

    /** Cuanto de "iluminado" esta ahora mismo, de 0 a 1. */
    private float hover;
    private long lastFrameMs = System.currentTimeMillis();

    /**
     * Reloj propio de la animacion, en segundos.
     *
     * Va aparte del reloj del juego para que el brillo respire igual de suave
     * aunque los fps bajen, que es justo lo que pasa en la pantalla del video.
     */
    private float clock;

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

        this.updateHover();
        float lit = this.active ? this.hover : 0.0F;

        // Sombra: separa el boton del video que hay detras. Va un pixel abajo y
        // sin degradado, que si no se ve sucia sobre imagenes claras.
        rounded(g, x, y + 2, w, h, 0x55000000, 0x55000000);

        if (!this.active) {
            rounded(g, x, y, w, h, 0xFF1A1A1A, 0xFF1A1A1A);
            rounded(g, x + 1, y + 1, w - 2, h - 2, 0xFF4B4B4B, 0xFF3A3A3A);
        } else {
            // Borde: oscuro en reposo y del color de acento al pasar el raton.
            int border = mix(0xFF14161A, darken(this.accent, 0.55F), 0.25F + 0.75F * lit);
            rounded(g, x, y, w, h, border, border);

            // Cara: gris azulado teñido con el acento, mas claro arriba. El tinte
            // sube con el raton encima, pero sin llegar al color puro: a pantalla
            // completa un boton saturado canta demasiado.
            int top = mix(0xFF525966, mix(this.accent, 0xFFFFFFFF, 0.25F), 0.22F + 0.30F * lit);
            int bottom = mix(0xFF2C313A, darken(this.accent, 0.7F), 0.22F + 0.30F * lit);
            rounded(g, x + 1, y + 1, w - 2, h - 2, top, bottom);

            // Resplandor que sube desde el borde de abajo. Queda DENTRO del
            // boton: se dibuja fila a fila con el mismo recorte de las esquinas,
            // asi que no se sale ni por los lados ni por las puntas.
            if (lit > 0.0F) {
                this.renderGlow(g, x, y, w, h, lit);
            }

            // Brillo de una fila arriba: da el aire de relieve sin biselar los
            // cuatro lados, que es lo que hacia que se viera plano y anticuado.
            g.fill(x + 3, y + 1, x + w - 3, y + 2, 0x33FFFFFF);

            // Linea de acento abajo, mas viva con el raton encima.
            g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, withAlpha(this.accent, (int) (130 + 100 * lit)));
        }

        int textColor = this.active ? 0xFFFFFFFF : 0xFF9A9A9A;
        var font = Minecraft.getInstance().font;
        int textY = y + (h - font.lineHeight) / 2 + 1;
        if (this.centered) {
            g.drawCenteredString(font, this.getMessage(), x + w / 2, textY, textColor);
        } else {
            g.drawString(font, this.getMessage(), x + 8, textY, textColor, true);
        }
    }

    /**
     * Resplandor de abajo hacia arriba, con unas motas de luz subiendo.
     *
     * Todo va recortado a la forma del boton. La idea es que parezca que el boton
     * se enciende por debajo, no que le salgan cosas por fuera: nada se dibuja
     * mas alla de su borde.
     */
    private void renderGlow(GuiGraphics g, int x, int y, int w, int h, float lit) {
        // La altura del resplandor respira despacio, entre el 45% y el 70% del
        // boton. Sin ese vaiven la luz parece pegada y no encendida.
        float breath = 0.62F + 0.18F * (float) Math.sin(this.clock * 2.6F);
        float glowRows = (h - 2) * breath * lit;
        int baseAlpha = (int) (165 * lit);

        for (int row = 0; row < h - 2; row++) {
            // 0 en el borde de abajo, 1 en lo alto del resplandor.
            float up = row / Math.max(1.0F, glowRows);
            if (up > 1.0F) {
                break;
            }
            // Se apaga con el cuadrado de la distancia: concentra la luz abajo y
            // la difumina arriba, en vez de dejar un corte recto.
            float fade = (1.0F - up) * (1.0F - up);
            int alpha = (int) (baseAlpha * fade);
            if (alpha <= 0) {
                continue;
            }
            int rowY = y + h - 2 - row;
            int inset = Math.max(1, cornerInset(rowY - y, h));
            g.fill(x + inset, rowY, x + w - inset, rowY + 1, withAlpha(this.accent, alpha));
        }

        // Motas de luz subiendo por dentro. Son deterministas: cada una siempre
        // sale por la misma columna, asi no parpadean de sitio en sitio.
        int usable = w - 8;
        if (usable <= 0) {
            return;
        }
        for (int i = 0; i < MOTES; i++) {
            float speed = 0.45F + 0.12F * i;
            float phase = (this.clock * speed + i * 0.37F) % 1.0F;
            // Sube desde el borde de abajo hasta media altura y se apaga al subir.
            float travel = phase * (h * 0.62F);
            int moteY = (int) (y + h - 2 - travel);
            if (moteY <= y + 2) {
                continue;
            }
            // Reparto con la proporcion dorada: quedan repartidas por todo el
            // ancho en vez de amontonarse a un lado, que es lo que pasaba
            // multiplicando por un numero grande y sacando el resto.
            int moteX = x + 4 + (int) ((0.13F + i * 0.618F) % 1.0F * usable);
            int alpha = (int) (205 * lit * (1.0F - phase) * Math.min(1.0F, phase * 5.0F));
            if (alpha <= 4) {
                continue;
            }
            g.fill(moteX, moteY, moteX + 1, moteY + 1, withAlpha(0xFFFFFFFF, alpha));
        }
    }

    /**
     * Avanza la animacion del raton.
     *
     * Se mide en tiempo real y no en ticks para que vaya igual de suave a
     * cualquier fps, que es justo la pantalla donde importa: encima del video.
     */
    private void updateHover() {
        long now = System.currentTimeMillis();
        float step = (now - this.lastFrameMs) / HOVER_MS;
        this.lastFrameMs = now;
        // Un salto de tiempo grande (cambio de pantalla, tiron) no debe disparar
        // la animacion: se acota.
        step = Math.min(1.0F, Math.max(0.0F, step));

        boolean target = this.isHoveredOrFocused() && this.active;
        this.hover = Math.max(0.0F, Math.min(1.0F, this.hover + (target ? step : -step)));

        // El reloj de la animacion avanza en segundos reales.
        this.clock += step * (HOVER_MS / 1000.0F);
    }

    /**
     * Rectangulo con las esquinas redondeadas y degradado vertical.
     *
     * Se dibuja fila a fila metiendo hacia dentro las dos de arriba y las dos de
     * abajo, que es lo que redondea la esquina. Cada fila lleva su color exacto
     * del degradado, asi no hay bandas.
     */
    private static void rounded(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 4 || h <= 2) {
            g.fill(x, y, x + w, y + h, top);
            return;
        }
        for (int row = 0; row < h; row++) {
            int inset = cornerInset(row, h);
            float t = h == 1 ? 0.0F : (float) row / (h - 1);
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, mixKeepAlpha(top, bottom, t));
        }
    }

    /** Cuanto se mete cada fila para redondear: 2 en la punta, 1 en la siguiente. */
    private static int cornerInset(int row, int h) {
        if (row == 0 || row == h - 1) {
            return 2;
        }
        if (row == 1 || row == h - 2) {
            return 1;
        }
        return 0;
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

    /** Como mix pero conservando la transparencia, que hace falta para la sombra. */
    private static int mixKeepAlpha(int a, int b, float amount) {
        int aa = a >>> 24;
        int ba = b >>> 24;
        int alpha = (int) (aa + (ba - aa) * amount);
        return (alpha & 0xFF) << 24 | (mix(a, b, amount) & 0xFFFFFF);
    }

    private static int darken(int color, float factor) {
        return mix(color, 0xFF000000, 1.0F - factor);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }


    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
