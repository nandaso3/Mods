package com.fscrates.client.color;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Un texto con su estilo, guardado en campos separados.
 *
 * Es el mismo modelo que usa Fantastic Holograms (HoloLine): el campo de texto
 * SOLO lleva texto, y el color, la negrita, el degradado o el arcoiris van en
 * campos aparte. Antes esto se metia dentro de la propia cadena
 * ("&amp;#FF55FFHola", "&lt;rainbow:15&gt;Hola") y se colaba como texto literal en
 * cualquier sitio que mostrase la cadena en crudo: el campo del editor, el
 * nombre del item, el chat o el nombre flotante encima de la caja.
 */
public class FSTextStyle {
    public String text = "";
    public String color = "#FFFFFF";
    public boolean bold;
    public boolean italic;
    public boolean underline;
    public boolean strikethrough;
    public boolean obfuscated;
    public boolean gradient;
    public String gradFrom = "#FF5555";
    public String gradTo = "#55AAFF";
    public boolean rainbow;
    public int rainbowStyle;

    public FSTextStyle() {
    }

    public FSTextStyle(String text) {
        this.text = text == null ? "" : text;
    }

    public FSTextStyle(String text, String color) {
        this(text);
        this.color = color;
    }

    public boolean isBlank() {
        return this.text == null || this.text.isBlank();
    }

    public int colorRgb() {
        return FSColors.parse(this.color, 0xFFFFFF);
    }

    // ------------------------------------------------------------------ render

    /** Construye el texto ya coloreado, letra a letra si hace falta. */
    public MutableComponent toComponent(long timeMs) {
        String value = this.text == null ? "" : this.text;
        if (value.isEmpty()) {
            return Component.empty();
        }

        Style base = Style.EMPTY
            .withBold(this.bold)
            .withItalic(this.italic)
            .withUnderlined(this.underline)
            .withStrikethrough(this.strikethrough)
            .withObfuscated(this.obfuscated);

        // Sin efectos de color por letra: un solo componente, mas barato.
        if (!this.rainbow && !this.gradient) {
            return Component.literal(value).withStyle(base.withColor(TextColor.fromRgb(this.colorRgb())));
        }

        MutableComponent out = Component.empty();
        int length = value.length();
        float time = (timeMs % 3000L) / 3000.0F;
        int from = FSColors.parse(this.gradFrom, 0xFF5555);
        int to = FSColors.parse(this.gradTo, 0x55AAFF);

        for (int i = 0; i < length; i++) {
            float pos = length <= 1 ? 0.0F : i / (float) (length - 1);
            int rgb = this.rainbow ? FSRainbow.color(this.rainbowStyle, pos * 0.6F, time) : FSColors.lerp(from, to, pos);
            out.append(Component.literal(String.valueOf(value.charAt(i))).withStyle(base.withColor(TextColor.fromRgb(rgb))));
        }
        return out;
    }

    /** Version con codigos clasicos, para nombres de item y chat. */
    public String toLegacy() {
        StringBuilder sb = new StringBuilder();
        sb.append('\u00a7').append(FSText.nearestLegacyChar(this.rainbow ? 0xFF55FF : this.colorRgb()));
        if (this.bold) {
            sb.append("\u00a7l");
        }
        if (this.italic) {
            sb.append("\u00a7o");
        }
        if (this.underline) {
            sb.append("\u00a7n");
        }
        if (this.strikethrough) {
            sb.append("\u00a7m");
        }
        return sb.append(this.text == null ? "" : this.text).toString();
    }

    /** Resumen corto del estilo, para mostrarlo en el editor. */
    public String describe() {
        if (this.rainbow) {
            return "\u00a7dArcoiris: " + FSRainbow.name(this.rainbowStyle);
        }
        if (this.gradient) {
            return "\u00a7bDegradado " + this.gradFrom + " \u00bb " + this.gradTo;
        }
        return "\u00a77Color " + this.color;
    }

    // ----------------------------------------------------------- persistencia

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("text", this.text == null ? "" : this.text);
        tag.putString("color", this.color == null ? "#FFFFFF" : this.color);
        tag.putBoolean("bold", this.bold);
        tag.putBoolean("italic", this.italic);
        tag.putBoolean("underline", this.underline);
        tag.putBoolean("strikethrough", this.strikethrough);
        tag.putBoolean("obfuscated", this.obfuscated);
        tag.putBoolean("gradient", this.gradient);
        tag.putString("gradFrom", this.gradFrom == null ? "#FF5555" : this.gradFrom);
        tag.putString("gradTo", this.gradTo == null ? "#55AAFF" : this.gradTo);
        tag.putBoolean("rainbow", this.rainbow);
        tag.putInt("rainbowStyle", this.rainbowStyle);
        return tag;
    }

    public static FSTextStyle load(CompoundTag tag) {
        FSTextStyle style = new FSTextStyle();
        if (tag == null) {
            return style;
        }
        style.text = tag.getString("text");
        if (tag.contains("color")) {
            style.color = tag.getString("color");
        }
        style.bold = tag.getBoolean("bold");
        style.italic = tag.getBoolean("italic");
        style.underline = tag.getBoolean("underline");
        style.strikethrough = tag.getBoolean("strikethrough");
        style.obfuscated = tag.getBoolean("obfuscated");
        style.gradient = tag.getBoolean("gradient");
        if (tag.contains("gradFrom")) {
            style.gradFrom = tag.getString("gradFrom");
        }
        if (tag.contains("gradTo")) {
            style.gradTo = tag.getString("gradTo");
        }
        style.rainbow = tag.getBoolean("rainbow");
        style.rainbowStyle = tag.getInt("rainbowStyle");
        return style;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("texto", this.text == null ? "" : this.text);
        json.addProperty("color", this.color == null ? "#FFFFFF" : this.color);
        json.addProperty("negrita", this.bold);
        json.addProperty("cursiva", this.italic);
        json.addProperty("subrayado", this.underline);
        json.addProperty("tachado", this.strikethrough);
        json.addProperty("degradado", this.gradient);
        json.addProperty("degradadoDesde", this.gradFrom);
        json.addProperty("degradadoHasta", this.gradTo);
        json.addProperty("arcoiris", this.rainbow);
        json.addProperty("estiloArcoiris", this.rainbowStyle);
        return json;
    }

    public static FSTextStyle fromJson(JsonObject json) {
        FSTextStyle style = new FSTextStyle();
        if (json == null) {
            return style;
        }
        if (json.has("texto")) {
            style.text = json.get("texto").getAsString();
        }
        if (json.has("color")) {
            style.color = json.get("color").getAsString();
        }
        style.bold = json.has("negrita") && json.get("negrita").getAsBoolean();
        style.italic = json.has("cursiva") && json.get("cursiva").getAsBoolean();
        style.underline = json.has("subrayado") && json.get("subrayado").getAsBoolean();
        style.strikethrough = json.has("tachado") && json.get("tachado").getAsBoolean();
        style.gradient = json.has("degradado") && json.get("degradado").getAsBoolean();
        if (json.has("degradadoDesde")) {
            style.gradFrom = json.get("degradadoDesde").getAsString();
        }
        if (json.has("degradadoHasta")) {
            style.gradTo = json.get("degradadoHasta").getAsString();
        }
        style.rainbow = json.has("arcoiris") && json.get("arcoiris").getAsBoolean();
        if (json.has("estiloArcoiris")) {
            style.rainbowStyle = json.get("estiloArcoiris").getAsInt();
        }
        return style;
    }

    public FSTextStyle copy() {
        FSTextStyle style = new FSTextStyle();
        style.text = this.text;
        style.color = this.color;
        style.bold = this.bold;
        style.italic = this.italic;
        style.underline = this.underline;
        style.strikethrough = this.strikethrough;
        style.obfuscated = this.obfuscated;
        style.gradient = this.gradient;
        style.gradFrom = this.gradFrom;
        style.gradTo = this.gradTo;
        style.rainbow = this.rainbow;
        style.rainbowStyle = this.rainbowStyle;
        return style;
    }

    /**
     * Convierte un texto del formato antiguo (con las etiquetas y los codigos
     * dentro de la cadena) a este modelo, para no perder lo ya configurado.
     */
    public static FSTextStyle migrate(String raw) {
        FSTextStyle style = new FSTextStyle();
        if (raw == null || raw.isEmpty()) {
            return style;
        }

        int rainbowStyle = FSText.rainbowStyleOf(raw);
        if (rainbowStyle >= 0) {
            style.rainbow = true;
            style.rainbowStyle = rainbowStyle;
        }

        java.util.regex.Matcher hex = java.util.regex.Pattern.compile("[&\u00a7]#([0-9a-fA-F]{6})").matcher(raw);
        if (hex.find()) {
            style.color = "#" + hex.group(1).toUpperCase(java.util.Locale.ROOT);
        } else {
            java.util.regex.Matcher legacy = java.util.regex.Pattern.compile("[&\u00a7]([0-9a-fA-F])").matcher(raw);
            if (legacy.find()) {
                style.color = String.format("#%06X", FSText.legacyColorOf(legacy.group(1).charAt(0)));
            }
        }

        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        style.bold = lower.contains("&l") || lower.contains("\u00a7l");
        style.italic = lower.contains("&o") || lower.contains("\u00a7o");
        style.underline = lower.contains("&n") || lower.contains("\u00a7n");
        style.strikethrough = lower.contains("&m") || lower.contains("\u00a7m");

        style.text = FSText.plain(raw);
        return style;
    }
}
