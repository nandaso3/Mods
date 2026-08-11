package com.fscrates.client.color;

import java.awt.Color;
import java.util.Locale;
import net.minecraft.ChatFormatting;

public final class FSColors {
    private FSColors() {
    }

    public static int parse(String s, int def) {
        if (s != null && !s.isEmpty()) {
            String v = s.trim();

            try {
                if (v.startsWith("#")) {
                    return 16777215 & Integer.parseInt(v.substring(1), 16);
                } else {
                    ChatFormatting fmt = ChatFormatting.getByName(v.toLowerCase(Locale.ROOT));
                    return fmt != null && fmt.getColor() != null ? fmt.getColor() : 16777215 & Integer.parseInt(v, 16);
                }
            } catch (NumberFormatException var4) {
                return def;
            }
        } else {
            return def;
        }
    }

    /** Interpola entre dos colores RGB. */
    public static int lerp(int from, int to, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int fr = from >> 16 & 0xFF;
        int fg = from >> 8 & 0xFF;
        int fb = from & 0xFF;
        int tr = to >> 16 & 0xFF;
        int tg = to >> 8 & 0xFF;
        int tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return r << 16 | g << 8 | b;
    }

    public static int hsbToRgb(float h, float s, float b) {
        return 16777215 & Color.HSBtoRGB(h, s, b);
    }

    public static float[] rgbToHsb(int rgb) {
        return Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
    }

    public static String toHex(int rgb) {
        return String.format("#%06X", rgb & 16777215);
    }
}
