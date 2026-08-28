package com.claimblocks;

import com.claimblocks.data.ClaimTier;
import com.claimblocks.item.ClaimItems;
import com.claimblocks.item.ProtectionItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class ClaimBlocks {
    public static final String NBT_KEY = "claimblocks";
    public static final String NBT_TIER_FIELD = "tier";

    private ClaimBlocks() {
    }

    public static Block blockForTier(ClaimTier claimtier) {
        if (claimtier == null) {
            return Blocks.WHITE_CONCRETE;
        } else {
            String s = claimtier.id;
            String s1 = claimtier.id;

            return switch (s1) {
                case "claimstone_10x10" -> Blocks.WHITE_CONCRETE;
                case "claimstone_25x25" -> Blocks.LIGHT_GRAY_CONCRETE;
                case "claimstone_40x40" -> Blocks.CYAN_CONCRETE;
                case "claimstone_64x64" -> Blocks.LIGHT_BLUE_CONCRETE;
                case "claimstone_80x80" -> Blocks.LIME_CONCRETE;
                case "claimstone_100x100" -> Blocks.YELLOW_CONCRETE;
                case "claimstone_150x150" -> Blocks.ORANGE_CONCRETE;
                case "claimstone_250x250" -> Blocks.PINK_CONCRETE;
                case "claimstone_300x300" -> Blocks.MAGENTA_CONCRETE;
                case "claimstone_500x500" -> Blocks.PURPLE_CONCRETE;
                default -> Blocks.WHITE_CONCRETE;
            };
        }
    }

    public static Item itemForTier(ClaimTier claimtier) {
        return blockForTier(claimtier).asItem();
    }

    public static boolean isClaimConcreteForTier(Block block, ClaimTier claimtier) {
        return block == blockForTier(claimtier);
    }

    public static boolean isAnyClaimConcrete(Block block) {
        for (ClaimTier claimtier : ClaimTier.VALUES) {
            if (block == blockForTier(claimtier)) {
                return true;
            }
        }

        return false;
    }

    public static ChatFormatting colorForTier(ClaimTier claimtier) {
        if (claimtier == null) {
            return ChatFormatting.WHITE;
        } else {
            String s = claimtier.id;
            String s1 = claimtier.id;

            return switch (s1) {
                case "claimstone_10x10" -> ChatFormatting.WHITE;
                case "claimstone_25x25" -> ChatFormatting.GRAY;
                case "claimstone_40x40" -> ChatFormatting.AQUA;
                case "claimstone_64x64" -> ChatFormatting.BLUE;
                case "claimstone_80x80" -> ChatFormatting.GREEN;
                case "claimstone_100x100" -> ChatFormatting.YELLOW;
                case "claimstone_150x150" -> ChatFormatting.GOLD;
                case "claimstone_250x250" -> ChatFormatting.LIGHT_PURPLE;
                case "claimstone_300x300" -> ChatFormatting.LIGHT_PURPLE;
                case "claimstone_500x500" -> ChatFormatting.DARK_PURPLE;
                default -> ChatFormatting.WHITE;
            };
        }
    }

    public static ItemStack createTierItem(ClaimTier claimtier, int i) {
        Item item = ClaimItems.itemFor(claimtier);
        ChatFormatting chatformatting = colorForTier(claimtier);
        MutableComponent mutablecomponent = Component.literal("Protección " + claimtier.label())
            .setStyle(Style.EMPTY.withColor(chatformatting).withBold(true).withItalic(false));
        if (item != null) {
            ItemStack itemstack1 = new ItemStack(item, i);
            itemstack1.setHoverName(mutablecomponent);
            return itemstack1;
        } else {
            ItemStack itemstack = new ItemStack(itemForTier(claimtier), i);
            CompoundTag compoundtag = itemstack.getOrCreateTag();
            CompoundTag compoundtag1 = new CompoundTag();
            compoundtag1.putString("tier", claimtier.id);
            compoundtag.put("claimblocks", compoundtag1);
            ListTag listtag = new ListTag();
            CompoundTag compoundtag2 = new CompoundTag();
            compoundtag2.putString("id", "minecraft:unbreaking");
            compoundtag2.putInt("lvl", 1);
            listtag.add(compoundtag2);
            compoundtag.put("Enchantments", listtag);
            compoundtag.putInt("HideFlags", 1);
            itemstack.setHoverName(mutablecomponent);
            ArrayList arraylist = new ArrayList();
            arraylist.add(Component.literal("Radio: " + claimtier.radius + " · Altura: ±" + claimtier.height).withStyle(ChatFormatting.GRAY));
            arraylist.add(Component.literal("Coloca para crear una protección").withStyle(chatformatting));
            setLore(itemstack, arraylist);
            return itemstack;
        }
    }

    public static void setLore(ItemStack itemstack, List<Component> list) {
        CompoundTag compoundtag = itemstack.getOrCreateTagElement("display");
        ListTag listtag = new ListTag();

        for (Component component : list) {
            listtag.add(StringTag.valueOf(Serializer.toJson(component)));
        }

        compoundtag.put("Lore", listtag);
    }

    public static String readTierId(ItemStack itemstack) {
        if (itemstack != null && !itemstack.isEmpty()) {
            if (itemstack.getItem() instanceof ProtectionItem protectionitem) {
                return protectionitem.tier.id;
            } else {
                CompoundTag compoundtag = itemstack.getTag();
                if (compoundtag != null && compoundtag.contains("claimblocks", 10)) {
                    CompoundTag compoundtag1 = compoundtag.getCompound("claimblocks");
                    return !compoundtag1.contains("tier", 8) ? null : compoundtag1.getString("tier");
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    public static ClaimTier readTier(ItemStack itemstack) {
        String s = readTierId(itemstack);
        return s == null ? null : ClaimTier.byId(s);
    }
}
