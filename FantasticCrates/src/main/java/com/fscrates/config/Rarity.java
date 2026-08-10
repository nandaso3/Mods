package com.fscrates.config;

import net.minecraft.ChatFormatting;

public enum Rarity {
    COMMON("common", "Com\u00fan", ChatFormatting.WHITE, 13685464),
    RARE("rare", "Rara", ChatFormatting.AQUA, 5630965),
    EPIC("epic", "\u00c9pica", ChatFormatting.LIGHT_PURPLE, 14446320),
    LEGENDARY("legendary", "Legendaria", ChatFormatting.GOLD, 16757288),
    MYTHIC("mythic", "M\u00edtica", ChatFormatting.RED, 16732240);

    private final String id;
    private final String displayName;
    private final ChatFormatting color;
    private final int rgb;

    private Rarity(String id, String displayName, ChatFormatting color, int rgb) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.rgb = rgb;
    }

    public String id() {
        return this.id;
    }

    public String displayName() {
        return this.displayName;
    }

    public ChatFormatting color() {
        return this.color;
    }

    public int rgb() {
        return this.rgb;
    }

    public float redF() {
        return (float)(this.rgb >> 16 & 0xFF) / 255.0F;
    }

    public float greenF() {
        return (float)(this.rgb >> 8 & 0xFF) / 255.0F;
    }

    public float blueF() {
        return (float)(this.rgb & 0xFF) / 255.0F;
    }

    public Rarity next() {
        Rarity[] v = values();
        return v[(this.ordinal() + 1) % v.length];
    }

    public float sizeScale() {
        switch (this) {
            case LEGENDARY:
                return 2.85F;
            case MYTHIC:
                return 2.3F;
            case EPIC:
                return 1.3F;
            default:
                return 1.0F;
        }
    }

    public static Rarity byName(String name) {
        if (name == null) {
            return COMMON;
        } else {
            for (Rarity r : values()) {
                if (r.name().equalsIgnoreCase(name) || r.id.equalsIgnoreCase(name)) {
                    return r;
                }
            }

            return COMMON;
        }
    }
}
