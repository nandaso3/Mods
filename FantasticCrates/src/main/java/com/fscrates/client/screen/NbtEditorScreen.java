package com.fscrates.client.screen;

import com.fscrates.client.color.FSTextStyleScreen;
import com.fscrates.client.widget.ScrollSelector;
import com.fscrates.config.EsNames;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

public class NbtEditorScreen extends Screen {
    private static final String COLOR_CHARS = "f7e6cab9d5234180";
    private static final String[] OPS = new String[]{"Sumar", "x base", "x total"};
    private static final String[] SLOTS = new String[]{"any", "mainhand", "offhand", "head", "chest", "legs", "feet"};
    private static final String[] SLOTS_ES = new String[]{"Cualquiera", "Mano principal", "Mano secundaria", "Cabeza", "Pecho", "Piernas", "Pies"};
    private final Screen parent;
    private final ItemStack stack;
    private NbtEditorScreen.Tab activeTab = NbtEditorScreen.Tab.GENERAL;
    private int leftPos;
    private int topPos;
    private int panelWidth;
    private int panelHeight;
    private final List<NbtEditorScreen.EnchEntry> enchEntries = new ArrayList<>();
    private boolean enchLoaded = false;
    private final List<NbtEditorScreen.AttrEntry> attrEntries = new ArrayList<>();
    private boolean attrLoaded = false;
    private boolean nameLoaded = false;
    private String nameText = "";
    private int nameRgb = 16777215;
    private final boolean[] nameFlags = new boolean[5];
    private int nameSwatchX = -1;
    private int nameSwatchY = -1;
    private int[] loreRgb = new int[8];
    private final boolean[][] loreFlags = new boolean[8][5];
    private final int[] loreSwatchX = new int[8];
    private final int[] loreSwatchY = new int[8];

    private static String slotEs(String slot) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i].equals(slot)) {
                return SLOTS_ES[i];
            }
        }

        return slot;
    }

    private void loadName() {
        if (this.stack.hasCustomHoverName()) {
            Component hn = this.stack.getHoverName();
            this.nameText = stripColor(hn.getString());
            Style st = hn.getStyle();
            TextColor tc = st.getColor();
            this.nameRgb = tc != null ? tc.getValue() & 16777215 : 16777215;
            this.nameFlags[0] = st.isBold();
            this.nameFlags[1] = st.isItalic();
            this.nameFlags[2] = st.isUnderlined();
            this.nameFlags[3] = st.isStrikethrough();
            this.nameFlags[4] = st.isObfuscated();
        }

        this.nameLoaded = true;
    }

    private void applyStyledName() {
        if (this.nameText != null && !this.nameText.isEmpty()) {
            Style st = Style.EMPTY
                .withColor(TextColor.fromRgb(this.nameRgb))
                .withBold(this.nameFlags[0])
                .withItalic(this.nameFlags[1])
                .withUnderlined(this.nameFlags[2])
                .withStrikethrough(this.nameFlags[3])
                .withObfuscated(this.nameFlags[4]);
            this.stack.setHoverName(Component.literal(this.nameText).withStyle(st));
        } else {
            this.stack.resetHoverName();
        }
    }

    public NbtEditorScreen(Screen parent, ItemStack stack) {
        super(Component.literal("Editor de NBT"));
        this.parent = parent;
        this.stack = stack;
    }

    protected void init() {
        this.panelWidth = Math.min(this.width - 20, 540);
        this.panelHeight = Math.min(this.height - 20, 320);
        this.leftPos = (this.width - this.panelWidth) / 2;
        this.topPos = (this.height - this.panelHeight) / 2;
        NbtEditorScreen.Tab[] tabs = NbtEditorScreen.Tab.values();
        String[] names = new String[]{"General", "Lore", "Encantamientos", "Atributos"};
        int gap = 4;
        int tabW = (this.panelWidth - 16 - 4 * (tabs.length - 1)) / tabs.length;

        for (int i = 0; i < tabs.length; i++) {
            NbtEditorScreen.Tab t = tabs[i];
            String text = (t == this.activeTab ? "\u00a7f\u00a7l" : "\u00a77") + names[i];
            this.addRenderableWidget(Button.builder(Component.literal(text), b -> {
                this.activeTab = t;
                this.rebuildWidgets();
            }).bounds(this.leftPos + 8 + i * (tabW + 4), this.topPos + 24, tabW, 18).build());
        }

        this.addRenderableWidget(
            Button.builder(Component.literal("\u00a7aListo"), b -> this.onClose())
                .bounds(this.leftPos + this.panelWidth - 88, this.topPos + this.panelHeight - 24, 80, 18)
                .build()
        );
        switch (this.activeTab) {
            case GENERAL:
                this.initGeneral();
                break;
            case LORE:
                this.initLore();
                break;
            case ENCHANTS:
                this.initEnchants();
                break;
            case ATTRIBUTES:
                this.initAttributes();
        }
    }

    private int bx() {
        return this.leftPos + 12;
    }

    private int by() {
        return this.topPos + 56;
    }

    private int bw() {
        return this.panelWidth - 24;
    }

    private int bh() {
        return this.panelHeight - 56 - 28;
    }

    public void onClose() {
        if (this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.fill(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + this.panelHeight, -535291870);
        g.fill(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + 20, -14408646);
        g.drawString(
            this.font,
            "\u00a7d\u2726 \u00a7fEditor de NBT del item: \u00a7e" + this.stack.getHoverName().getString(),
            this.leftPos + 8,
            this.topPos + 6,
            16777215,
            false
        );
        g.drawString(this.font, "\u00a77Cambia nombre, lore, encantamientos y atributos del item.", this.leftPos + 8, this.topPos + 46, 10133680, false);
        if (this.activeTab == NbtEditorScreen.Tab.GENERAL && this.nameLoaded && this.nameSwatchX >= 0) {
            g.fill(this.nameSwatchX, this.nameSwatchY, this.nameSwatchX + 12, this.nameSwatchY + 12, 0xFF000000 | this.nameRgb);
            g.renderOutline(this.nameSwatchX, this.nameSwatchY, 12, 12, -16777216);
        }

        if (this.activeTab == NbtEditorScreen.Tab.LORE) {
            for (int i = 0; i < 8; i++) {
                if (this.loreSwatchX[i] > 0) {
                    g.fill(this.loreSwatchX[i], this.loreSwatchY[i], this.loreSwatchX[i] + 12, this.loreSwatchY[i] + 12, 0xFF000000 | this.loreRgb[i]);
                    g.renderOutline(this.loreSwatchX[i], this.loreSwatchY[i], 12, 12, -16777216);
                }
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void initGeneral() {
        int x = this.bx();
        int y = this.by();
        if (!this.nameLoaded) {
            this.loadName();
        }

        int bwv = this.bw();
        EditBox name = new EditBox(this.font, x, y, bwv - 120, 16, Component.empty());
        name.setMaxLength(128);
        name.setValue(this.nameText);
        name.setHint(Component.literal("Nombre personalizado del item"));
        name.setResponder(s -> {
            this.nameText = s;
            this.applyStyledName();
        });
        this.addRenderableWidget(name);
        this.nameSwatchX = x + bwv - 116;
        this.nameSwatchY = y + 2;
        this.addRenderableWidget(
            Button.builder(
                    Component.literal("\u00a7bColor/estilo \u00bb"),
                    b -> this.minecraft
                            .setScreen(
                                new FSTextStyleScreen(
                                    "Color y estilo del nombre", this.nameText, this.nameRgb, this.nameFlags, (rgb, bold, italic, underline, strike, obf) -> {
                                        this.nameRgb = rgb & 16777215;
                                        this.nameFlags[0] = bold;
                                        this.nameFlags[1] = italic;
                                        this.nameFlags[2] = underline;
                                        this.nameFlags[3] = strike;
                                        this.nameFlags[4] = obf;
                                        this.applyStyledName();
                                    }, () -> this.minecraft.setScreen(this)
                                )
                            )
                )
                .bounds(x + bwv - 100, y, 100, 16)
                .build()
        );
        boolean unbreak = this.stack.getOrCreateTag().getBoolean("Unbreakable");
        this.addRenderableWidget(Button.builder(Component.literal((unbreak ? "\u00a7a" : "\u00a77") + "Irrompible: " + (unbreak ? "S\u00ed" : "No")), b -> {
            boolean now = !this.stack.getOrCreateTag().getBoolean("Unbreakable");
            this.stack.getOrCreateTag().putBoolean("Unbreakable", now);
            if (!now) {
                this.stack.getOrCreateTag().remove("Unbreakable");
            }

            this.rebuildWidgets();
        }).bounds(x, y + 28, 200, 16).build());
        EditBox cmd = new EditBox(this.font, x + 220, y + 28, 80, 16, Component.empty());
        cmd.setMaxLength(8);
        cmd.setValue(this.stack.getOrCreateTag().contains("CustomModelData") ? Integer.toString(this.stack.getOrCreateTag().getInt("CustomModelData")) : "");
        cmd.setHint(Component.literal("CMD"));
        cmd.setResponder(s -> {
            String t2 = s.trim();
            if (t2.isEmpty()) {
                this.stack.getOrCreateTag().remove("CustomModelData");
            } else {
                try {
                    this.stack.getOrCreateTag().putInt("CustomModelData", Integer.parseInt(t2));
                } catch (NumberFormatException var4x) {
                }
            }
        });
        this.addRenderableWidget(cmd);
        EditBox dmg = new EditBox(this.font, x + 220, y + 50, 80, 16, Component.empty());
        dmg.setMaxLength(8);
        dmg.setValue(this.stack.getOrCreateTag().contains("Damage") ? Integer.toString(this.stack.getOrCreateTag().getInt("Damage")) : "");
        dmg.setHint(Component.literal("Da\u00f1o"));
        dmg.setResponder(s -> {
            String t4 = s.trim();
            if (t4.isEmpty()) {
                this.stack.getOrCreateTag().remove("Damage");
            } else {
                try {
                    this.stack.getOrCreateTag().putInt("Damage", Integer.parseInt(t4));
                } catch (NumberFormatException var4x) {
                }
            }
        });
        this.addRenderableWidget(dmg);
        boolean hideAll = this.stack.getOrCreateTag().getInt("HideFlags") == 127;
        this.addRenderableWidget(
            Button.builder(Component.literal((hideAll ? "\u00a7a" : "\u00a77") + "Ocultar flags vanilla: " + (hideAll ? "S\u00ed" : "No")), b -> {
                CompoundTag t = this.stack.getOrCreateTag();
                if (t.getInt("HideFlags") == 127) {
                    t.remove("HideFlags");
                } else {
                    t.putInt("HideFlags", 127);
                }

                this.rebuildWidgets();
            }).bounds(x, y + 50, 200, 16).build()
        );
    }

    private char currentNameColor() {
        if (!this.stack.hasCustomHoverName()) {
            return 'f';
        } else {
            String full = Serializer.toJson(this.stack.getHoverName());
            int idx = full.indexOf("\"color\":\"");
            if (idx < 0) {
                return 'f';
            } else {
                String tail = full.substring(idx + 9);
                int end = tail.indexOf(34);
                String name = end > 0 ? tail.substring(0, end) : "white";
                return colorNameToChar(name);
            }
        }
    }

    private static char colorNameToChar(String n) {
        return switch (n) {
            case "black" -> '0';
            case "dark_blue" -> '1';
            case "dark_green" -> '2';
            case "dark_aqua" -> '3';
            case "dark_red" -> '4';
            case "dark_purple" -> '5';
            case "gold" -> '6';
            case "gray" -> '7';
            case "dark_gray" -> '8';
            case "blue" -> '9';
            case "green" -> 'a';
            case "aqua" -> 'b';
            case "red" -> 'c';
            case "light_purple" -> 'd';
            case "yellow" -> 'e';
            default -> 'f';
        };
    }

    private static String stripColor(String s) {
        if (s == null) {
            return "";
        } else {
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ((c == 167 || c == '&') && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
                    i++;
                } else {
                    sb.append(c);
                }
            }

            return sb.toString();
        }
    }

    private void applyName(char color, String text) {
        if (text != null && !text.isEmpty()) {
            this.stack.setHoverName(Component.literal("\u00a7" + color + text));
        } else {
            this.stack.resetHoverName();
        }
    }

    private void initLore() {
        int x = this.bx();
        int y = this.by();
        ListTag existing = this.stack.getOrCreateTagElement("display").getList("Lore", 8);
        String[] texts = new String[8];
        int[] rgb = this.loreRgb;
        boolean[][] fl = this.loreFlags;

        for (int i = 0; i < 8; i++) {
            String raw = i < existing.size() ? existing.getString(i) : "";
            MutableComponent c = null;

            try {
                c = raw.isEmpty() ? null : Serializer.fromJson(raw);
            } catch (Exception var14) {
            }

            String plain = c != null ? c.getString() : raw;
            Style st = c != null ? c.getStyle() : Style.EMPTY;
            int col = 16777215;
            ChatFormatting f;
            if (plain.length() >= 2
                && (plain.charAt(0) == 167 || plain.charAt(0) == '&')
                && (f = ChatFormatting.getByCode(plain.charAt(1))) != null
                && f.isColor()
                && f.getColor() != null) {
                col = f.getColor();
            }

            if (st.getColor() != null) {
                col = st.getColor().getValue() & 16777215;
            }

            fl[i][0] = st.isBold();
            fl[i][1] = st.isItalic();
            fl[i][2] = st.isUnderlined();
            fl[i][3] = st.isStrikethrough();
            fl[i][4] = st.isObfuscated();
            texts[i] = stripColor(plain);
            rgb[i] = col;
        }

        Runnable sync = () -> {
            CompoundTag display = this.stack.getOrCreateTagElement("display");
            ListTag list = new ListTag();

            for (int k = 0; k < 8; k++) {
                if (texts[k] != null && !texts[k].isEmpty()) {
                    Style stx = Style.EMPTY
                        .withColor(TextColor.fromRgb(rgb[k]))
                        .withBold(fl[k][0])
                        .withItalic(fl[k][1])
                        .withUnderlined(fl[k][2])
                        .withStrikethrough(fl[k][3])
                        .withObfuscated(fl[k][4]);
                    String json = Serializer.toJson(Component.literal(texts[k]).withStyle(stx));
                    list.add(StringTag.valueOf(json));
                }
            }

            if (list.isEmpty()) {
                display.remove("Lore");
                if (display.isEmpty()) {
                    this.stack.removeTagKey("display");
                }
            } else {
                display.put("Lore", list);
            }
        };

        for (int j = 0; j < 8; j++) {
            int idx = j;
            int ry = y + j * 20;
            this.loreSwatchX[idx] = x;
            this.loreSwatchY[idx] = ry + 2;
            this.addRenderableWidget(
                Button.builder(
                        Component.literal(""),
                        b -> {
                            sync.run();
                            this.minecraft
                                .setScreen(
                                    new FSTextStyleScreen(
                                        "Color y estilo \u2014 l\u00ednea " + (idx + 1),
                                        texts[idx],
                                        rgb[idx],
                                        fl[idx],
                                        (colx, bold, italic, underline, strike, obf) -> {
                                            rgb[idx] = colx & 16777215;
                                            fl[idx][0] = bold;
                                            fl[idx][1] = italic;
                                            fl[idx][2] = underline;
                                            fl[idx][3] = strike;
                                            fl[idx][4] = obf;
                                            sync.run();
                                        },
                                        () -> this.minecraft.setScreen(this)
                                    )
                                );
                        }
                    )
                    .bounds(x, ry, 16, 16)
                    .build()
            );
            EditBox eb = new EditBox(this.font, x + 22, ry, this.bw() - 130, 16, Component.empty());
            eb.setMaxLength(96);
            eb.setValue(texts[idx]);
            eb.setHint(Component.literal("L\u00ednea de lore " + (idx + 1)));
            eb.setResponder(s -> {
                texts[idx] = s;
                sync.run();
            });
            this.addRenderableWidget(eb);
            this.addRenderableWidget(
                Button.builder(
                        Component.literal("\u00a7bColor/estilo \u00bb"),
                        b -> {
                            sync.run();
                            this.minecraft
                                .setScreen(
                                    new FSTextStyleScreen(
                                        "Color y estilo \u2014 l\u00ednea " + (idx + 1),
                                        texts[idx],
                                        rgb[idx],
                                        fl[idx],
                                        (colx, bold, italic, underline, strike, obf) -> {
                                            rgb[idx] = colx & 16777215;
                                            fl[idx][0] = bold;
                                            fl[idx][1] = italic;
                                            fl[idx][2] = underline;
                                            fl[idx][3] = strike;
                                            fl[idx][4] = obf;
                                            sync.run();
                                        },
                                        () -> this.minecraft.setScreen(this)
                                    )
                                );
                        }
                    )
                    .bounds(x + this.bw() - 104, ry, 104, 16)
                    .build()
            );
        }
    }

    private static String jsonToPlain(String json) {
        try {
            MutableComponent c = Serializer.fromJson(json);
            return c == null ? json : c.getString();
        } catch (Exception var21) {
            return json;
        }
    }

    private void loadEnchants() {
        this.enchEntries.clear();
        ListTag list = this.stack.getOrCreateTag().getList("Enchantments", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            NbtEditorScreen.EnchEntry e = new NbtEditorScreen.EnchEntry();
            e.id = t.getString("id");
            e.level = t.getInt("lvl");
            this.enchEntries.add(e);
        }

        this.enchLoaded = true;
    }

    private void saveEnchants() {
        ListTag list = new ListTag();

        for (NbtEditorScreen.EnchEntry e : this.enchEntries) {
            if (e.id != null && !e.id.isEmpty()) {
                CompoundTag t = new CompoundTag();
                t.putString("id", e.id);
                t.putShort("lvl", (short)Math.max(0, e.level));
                list.add(t);
            }
        }

        if (list.isEmpty()) {
            this.stack.removeTagKey("Enchantments");
        } else {
            this.stack.getOrCreateTag().put("Enchantments", list);
        }
    }

    private void initEnchants() {
        if (!this.enchLoaded) {
            this.loadEnchants();
        }

        int x = this.bx();
        int y = this.by();
        int colW = (this.bw() - 8) / 2;
        int rightX = x + colW + 8;
        ArrayList<ResourceLocation> ids = new ArrayList<>();
        Iterator iterator = ForgeRegistries.ENCHANTMENTS.getValues().iterator();
        Enchantment enchCur = null;
        ResourceLocation enchRl = null;

        while (iterator.hasNext()) {
            enchCur = (Enchantment)iterator.next();
            enchRl = ForgeRegistries.ENCHANTMENTS.getKey(enchCur);
            if (enchRl != null) {
                ids.add(enchRl);
            }
        }

        ids.sort(Comparator.comparing(ResourceLocation::toString));
        EditBox search = new EditBox(this.font, x, y, colW, 16, Component.empty());
        search.setHint(Component.literal("Buscar encantamiento..."));
        this.addRenderableWidget(search);
        ScrollSelector<ResourceLocation> picker = new ScrollSelector<>(
            x, y + 20, colW, this.bh() - 22, 14, rl -> EsNames.enchant(rl), ResourceLocation::toString, rl -> ItemStack.EMPTY
        );
        picker.setItems(ids);
        picker.onSelect(rl -> {
            NbtEditorScreen.EnchEntry e3 = new NbtEditorScreen.EnchEntry();
            e3.id = rl.toString();
            e3.level = 1;
            this.enchEntries.add(e3);
            this.saveEnchants();
            this.rebuildWidgets();
        });
        search.setResponder(picker::setQuery);
        this.addRenderableWidget(picker);
        int ry = y;
        int i = 0;

        while (i < this.enchEntries.size()) {
            NbtEditorScreen.EnchEntry e2 = this.enchEntries.get(i);
            ResourceLocation erl = ResourceLocation.tryParse(e2.id);
            String label = erl != null ? EsNames.enchant(erl) : e2.id;
            EditBox idBox = new EditBox(this.font, rightX, ry, colW - 90, 16, Component.empty());
            idBox.setMaxLength(64);
            idBox.setValue(label);
            idBox.setEditable(false);
            this.addRenderableWidget(idBox);
            EditBox lvl = new EditBox(this.font, rightX + colW - 86, ry, 36, 16, Component.empty());
            lvl.setValue(Integer.toString(e2.level));
            lvl.setResponder(s -> {
                try {
                    e2.level = Integer.parseInt(s.trim());
                    this.saveEnchants();
                } catch (NumberFormatException var4x) {
                }
            });
            this.addRenderableWidget(lvl);
            int gone = i++;
            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cX"), b -> {
                this.enchEntries.remove(gone);
                this.saveEnchants();
                this.rebuildWidgets();
            }).bounds(rightX + colW - 46, ry, 22, 16).build());
            ry += 18;
            if (ry > y + this.bh() - 22) {
                break;
            }
        }
    }

    private void loadAttrs() {
        this.attrEntries.clear();
        ListTag list = this.stack.getOrCreateTag().getList("AttributeModifiers", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            NbtEditorScreen.AttrEntry e = new NbtEditorScreen.AttrEntry();
            e.id = t.getString("AttributeName");
            e.amount = t.getDouble("Amount");
            e.op = t.getInt("Operation");
            e.slot = t.contains("Slot") ? t.getString("Slot") : "mainhand";
            this.attrEntries.add(e);
        }

        this.attrLoaded = true;
    }

    private void saveAttrs() {
        ListTag list = new ListTag();

        for (NbtEditorScreen.AttrEntry e : this.attrEntries) {
            if (e.id != null && !e.id.isEmpty()) {
                CompoundTag t = new CompoundTag();
                t.putString("AttributeName", e.id);
                t.putString("Name", "fscrates");
                t.putDouble("Amount", e.amount);
                t.putInt("Operation", Math.max(0, Math.min(2, e.op)));
                if (!"any".equals(e.slot)) {
                    t.putString("Slot", e.slot);
                }

                UUID u = UUID.randomUUID();
                t.putIntArray(
                    "UUID",
                    new int[]{
                        (int)(u.getMostSignificantBits() >> 32),
                        (int)(u.getMostSignificantBits() & 4294967295L),
                        (int)(u.getLeastSignificantBits() >> 32),
                        (int)(u.getLeastSignificantBits() & 4294967295L)
                    }
                );
                list.add(t);
            }
        }

        if (list.isEmpty()) {
            this.stack.removeTagKey("AttributeModifiers");
        } else {
            this.stack.getOrCreateTag().put("AttributeModifiers", list);
        }
    }

    private void initAttributes() {
        if (!this.attrLoaded) {
            this.loadAttrs();
        }

        int x = this.bx();
        int y = this.by();
        int colW = (this.bw() - 8) / 2;
        int rightX = x + colW + 8;
        ArrayList<ResourceLocation> ids = new ArrayList<>();
        ResourceLocation attrRl = null;

        for (Attribute a : ForgeRegistries.ATTRIBUTES.getValues()) {
            attrRl = ForgeRegistries.ATTRIBUTES.getKey(a);
            if (attrRl != null) {
                ids.add(attrRl);
            }
        }

        ids.sort(Comparator.comparing(ResourceLocation::toString));
        EditBox search = new EditBox(this.font, x, y, colW, 16, Component.empty());
        search.setHint(Component.literal("Buscar atributo..."));
        this.addRenderableWidget(search);
        ScrollSelector<ResourceLocation> picker = new ScrollSelector<>(
            x, y + 20, colW, this.bh() - 22, 14, rl -> EsNames.attribute(rl), ResourceLocation::toString, rl -> ItemStack.EMPTY
        );
        picker.setItems(ids);
        picker.onSelect(rl -> {
            NbtEditorScreen.AttrEntry e2 = new NbtEditorScreen.AttrEntry();
            e2.id = rl.toString();
            e2.amount = 1.0;
            e2.op = 0;
            e2.slot = "mainhand";
            this.attrEntries.add(e2);
            this.saveAttrs();
            this.rebuildWidgets();
        });
        search.setResponder(picker::setQuery);
        this.addRenderableWidget(picker);
        int ry = y;
        int i = 0;

        while (i < this.attrEntries.size()) {
            NbtEditorScreen.AttrEntry e = this.attrEntries.get(i);
            String label = EsNames.attributeByRawId(e.id);
            EditBox idBox = new EditBox(this.font, rightX, ry, colW - 90, 16, Component.empty());
            idBox.setMaxLength(96);
            idBox.setValue(label);
            idBox.setEditable(false);
            this.addRenderableWidget(idBox);
            EditBox amt = new EditBox(this.font, rightX + colW - 86, ry, 36, 16, Component.empty());
            amt.setValue(String.format(Locale.ROOT, "%.2f", e.amount));
            amt.setResponder(s -> {
                try {
                    e.amount = Double.parseDouble(s.trim());
                    this.saveAttrs();
                } catch (NumberFormatException var4x) {
                }
            });
            this.addRenderableWidget(amt);
            int gone = i++;
            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cX"), b -> {
                this.attrEntries.remove(gone);
                this.saveAttrs();
                this.rebuildWidgets();
            }).bounds(rightX + colW - 46, ry, 22, 16).build());
            Builder var10001 = Button.builder(Component.literal("Op: " + OPS[e.op]), b -> {
                e.op = (e.op + 1) % 3;
                this.saveAttrs();
                this.rebuildWidgets();
            });
            ry += 18;
            this.addRenderableWidget(var10001.bounds(rightX, ry, (colW - 8) / 2, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("Slot: " + slotEs(e.slot)), b -> {
                int idx = 0;

                for (int k = 0; k < SLOTS.length; k++) {
                    if (SLOTS[k].equals(e.slot)) {
                        idx = k;
                        break;
                    }
                }

                e.slot = SLOTS[(idx + 1) % SLOTS.length];
                this.saveAttrs();
                this.rebuildWidgets();
            }).bounds(rightX + (colW - 8) / 2 + 8, ry, (colW - 8) / 2, 16).build());
            ry += 22;
            if (ry > y + this.bh() - 24) {
                break;
            }
        }
    }

    private static class AttrEntry {
        String id = "minecraft:generic.attack_damage";
        double amount = 1.0;
        int op = 0;
        String slot = "mainhand";
    }

    private static class EnchEntry {
        String id = "minecraft:sharpness";
        int level = 1;
    }

    private static enum Tab {
        GENERAL,
        LORE,
        ENCHANTS,
        ATTRIBUTES;
    }
}
