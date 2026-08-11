package com.fscrates.client.color;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Formateo de texto enriquecido para los mensajes de la escena de pre-apertura.
 *
 * Ademas de los codigos clasicos con &amp; (&amp;a, &amp;l, ...) admite:
 *
 *   &amp;#RRGGBB                        color exacto en hexadecimal
 *   &lt;rainbow&gt;texto                   arcoiris animado
 *   &lt;gradient:#RRGGBB,#RRGGBB&gt;texto  degradado entre dos colores
 *
 * Las etiquetas afectan a todo lo que va detras hasta el final o hasta que
 * aparezca otra. Se construye un Component con el estilo puesto letra a letra,
 * que es la unica forma de tener colores RGB por caracter (los codigos con &amp;
 * solo dan 16 colores).
 */
public final class FSText {
    /** Vueltas completas de tono por segundo en el arcoiris. */
    private static final float RAINBOW_SPEED = 0.35F;
    /** Cuanto cambia el tono a lo largo del texto. */
    private static final float RAINBOW_SPREAD = 0.6F;

    private FSText() {
    }

    /** Convierte & en el caracter de seccion, para el texto plano de siempre. */
    public static String legacy(String raw) {
        if (raw == null) {
            return "";
        }
        char[] chars = raw.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(chars[i + 1]) >= 0) {
                chars[i] = '\u00a7';
            }
        }
        return new String(chars);
    }

    public static boolean hasEffects(String raw) {
        if (raw == null) {
            return false;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        return lower.contains("<rainbow>") || lower.contains("<gradient:") || lower.contains("&#");
    }

    /**
     * Construye el texto con sus efectos aplicados.
     *
     * @param timeMs reloj para animar el arcoiris
     */
    public static Component parse(String raw, long timeMs) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }

        MutableComponent out = Component.empty();

        // Estilo en curso
        Integer fixedColor = null;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        boolean obfuscated = false;

        // Modo de efecto: 0 nada, 1 arcoiris, 2 degradado
        int effect = 0;
        int gradientFrom = 0xFFFFFF;
        int gradientTo = 0xFFFFFF;

        // Para repartir el efecto se necesita saber cuantas letras quedan.
        int effectIndex = 0;
        int effectLength = 1;

        int i = 0;
        int length = raw.length();
        while (i < length) {
            char c = raw.charAt(i);

            // --- etiquetas
            if (c == '<') {
                int close = raw.indexOf('>', i);
                if (close > i) {
                    String tag = raw.substring(i + 1, close).toLowerCase(Locale.ROOT);
                    if (tag.equals("rainbow")) {
                        effect = 1;
                        effectIndex = 0;
                        effectLength = Math.max(1, visibleLength(raw, close + 1));
                        i = close + 1;
                        continue;
                    }
                    if (tag.startsWith("gradient:")) {
                        String[] parts = tag.substring("gradient:".length()).split(",");
                        if (parts.length >= 2) {
                            gradientFrom = FSColors.parse(parts[0].trim(), 0xFFFFFF);
                            gradientTo = FSColors.parse(parts[1].trim(), 0xFFFFFF);
                            effect = 2;
                            effectIndex = 0;
                            effectLength = Math.max(1, visibleLength(raw, close + 1));
                            i = close + 1;
                            continue;
                        }
                    }
                    if (tag.equals("/rainbow") || tag.equals("/gradient")) {
                        effect = 0;
                        i = close + 1;
                        continue;
                    }
                }
            }

            // --- color hexadecimal &#RRGGBB
            if ((c == '&' || c == '\u00a7') && i + 7 < length && raw.charAt(i + 1) == '#') {
                String hex = raw.substring(i + 2, i + 8);
                if (hex.matches("[0-9a-fA-F]{6}")) {
                    fixedColor = Integer.parseInt(hex, 16);
                    effect = 0;
                    i += 8;
                    continue;
                }
            }

            // --- codigos clasicos
            if ((c == '&' || c == '\u00a7') && i + 1 < length) {
                char code = Character.toLowerCase(raw.charAt(i + 1));
                int paletteIndex = "0123456789abcdef".indexOf(code);
                if (paletteIndex >= 0) {
                    fixedColor = legacyColor(paletteIndex);
                    effect = 0;
                    i += 2;
                    continue;
                }
                switch (code) {
                    case 'l':
                        bold = true;
                        i += 2;
                        continue;
                    case 'o':
                        italic = true;
                        i += 2;
                        continue;
                    case 'n':
                        underline = true;
                        i += 2;
                        continue;
                    case 'm':
                        strike = true;
                        i += 2;
                        continue;
                    case 'k':
                        obfuscated = true;
                        i += 2;
                        continue;
                    case 'r':
                        fixedColor = null;
                        bold = italic = underline = strike = obfuscated = false;
                        effect = 0;
                        i += 2;
                        continue;
                    default:
                        break;
                }
            }

            // --- caracter normal
            int color;
            if (effect == 1) {
                float hue = (effectIndex / (float) effectLength) * RAINBOW_SPREAD + (timeMs / 1000.0F) * RAINBOW_SPEED;
                color = hsvToRgb(hue - (float) Math.floor(hue), 0.85F, 1.0F);
                effectIndex++;
            } else if (effect == 2) {
                float t = effectLength <= 1 ? 0.0F : effectIndex / (float) (effectLength - 1);
                color = lerpColor(gradientFrom, gradientTo, t);
                effectIndex++;
            } else {
                color = fixedColor == null ? 0xFFFFFF : fixedColor;
            }

            Style style = Style.EMPTY
                .withColor(TextColor.fromRgb(color))
                .withBold(bold)
                .withItalic(italic)
                .withUnderlined(underline)
                .withStrikethrough(strike)
                .withObfuscated(obfuscated);
            out.append(Component.literal(String.valueOf(c)).withStyle(style));
            i++;
        }

        return out;
    }

    /** Cuantos caracteres visibles quedan desde una posicion (sin contar etiquetas). */
    private static int visibleLength(String raw, int from) {
        int count = 0;
        int i = from;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '<') {
                int close = raw.indexOf('>', i);
                if (close > i) {
                    i = close + 1;
                    continue;
                }
            }
            if ((c == '&' || c == '\u00a7') && i + 7 < raw.length() && raw.charAt(i + 1) == '#'
                && raw.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                i += 8;
                continue;
            }
            if ((c == '&' || c == '\u00a7') && i + 1 < raw.length()
                && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(raw.charAt(i + 1)) >= 0) {
                i += 2;
                continue;
            }
            count++;
            i++;
        }
        return count;
    }

    private static int legacyColor(int index) {
        return switch (index) {
            case 0 -> 0x000000;
            case 1 -> 0x0000AA;
            case 2 -> 0x00AA00;
            case 3 -> 0x00AAAA;
            case 4 -> 0xAA0000;
            case 5 -> 0xAA00AA;
            case 6 -> 0xFFAA00;
            case 7 -> 0xAAAAAA;
            case 8 -> 0x555555;
            case 9 -> 0x5555FF;
            case 10 -> 0x55FF55;
            case 11 -> 0x55FFFF;
            case 12 -> 0xFF5555;
            case 13 -> 0xFF55FF;
            case 14 -> 0xFFFF55;
            default -> 0xFFFFFF;
        };
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6.0F);
        float f = h * 6.0F - i;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255.0F) << 16) | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
    }
}
