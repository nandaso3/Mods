package com.fscrates.client;

import com.fscrates.config.EsNames;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class RegistryLists {
    public static final String[] FS_PRESETS = new String[]{
        "fs_dust_red",
        "fs_dust_orange",
        "fs_dust_gold",
        "fs_dust_yellow",
        "fs_dust_lime",
        "fs_dust_green",
        "fs_dust_aqua",
        "fs_dust_blue",
        "fs_dust_purple",
        "fs_dust_magenta",
        "fs_dust_pink",
        "fs_dust_white",
        "fs_dust_tiny",
        "fs_dust_huge",
        "fs_fade_fire",
        "fs_fade_ice",
        "fs_fade_void",
        "fs_fade_toxic",
        "fs_fade_royal",
        "fs_shard_gold",
        "fs_shard_diamond",
        "fs_shard_amethyst",
        "fs_shard_emerald",
        "fs_burst_star",
        "fs_burst_gem",
        "fs_soul_swirl"
    };

    private RegistryLists() {
    }

    public static List<Item> items() {
        ArrayList<Item> list = new ArrayList<>(ForgeRegistries.ITEMS.getValues());
        list.sort(Comparator.comparing(RegistryLists::itemId));
        return list;
    }

    public static String itemId(Item item) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
        return rl == null ? "minecraft:air" : rl.toString();
    }

    public static String itemName(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    public static List<ResourceLocation> effects() {
        ArrayList<ResourceLocation> list = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getKeys());
        list.sort(Comparator.comparing(ResourceLocation::toString));
        return list;
    }

    public static String effectName(ResourceLocation rl) {
        return EsNames.effect(rl);
    }

    public static List<ResourceLocation> particles() {
        ArrayList<ResourceLocation> list = new ArrayList<>();
        list.add(new ResourceLocation("minecraft", "dust"));

        for (Entry e : ForgeRegistries.PARTICLE_TYPES.getEntries()) {
            ParticleType type = (ParticleType)e.getValue();
            ResourceLocation key;
            if (type instanceof SimpleParticleType && !(key = ((ResourceKey)e.getKey()).location()).toString().equals("minecraft:dust")) {
                list.add(key);
            }
        }

        String[] parametric = new String[]{"dust_color_transition", "block", "block_marker", "falling_dust", "item", "sculk_charge", "shriek"};

        for (String p : parametric) {
            ResourceLocation rl = new ResourceLocation("minecraft", p);
            if (ForgeRegistries.PARTICLE_TYPES.containsKey(rl) && !list.contains(rl)) {
                list.add(rl);
            }
        }

        for (String px : FS_PRESETS) {
            list.add(new ResourceLocation("fscrates", px));
        }

        list.sort(Comparator.comparing(ResourceLocation::toString));
        return list;
    }
}
