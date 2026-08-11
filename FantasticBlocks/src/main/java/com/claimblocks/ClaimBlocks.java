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

   public static Block blockForTier(ClaimTier tier) {
      if (tier == null) {
         return Blocks.WHITE_CONCRETE;
      } else {
         String var1 = tier.id;

         return switch (var1) {
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

   public static Item itemForTier(ClaimTier tier) {
      return blockForTier(tier).asItem();
   }

   public static boolean isClaimConcreteForTier(Block block, ClaimTier tier) {
      return block == blockForTier(tier);
   }

   public static boolean isAnyClaimConcrete(Block block) {
      for (ClaimTier t : ClaimTier.VALUES) {
         if (block == blockForTier(t)) {
            return true;
         }
      }

      return false;
   }

   public static ChatFormatting colorForTier(ClaimTier tier) {
      if (tier == null) {
         return ChatFormatting.WHITE;
      } else {
         String var1 = tier.id;

         return switch (var1) {
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

   public static ItemStack createTierItem(ClaimTier tier, int amount) {
      Item registered = ClaimItems.itemFor(tier);
      ChatFormatting color = colorForTier(tier);
      MutableComponent name = Component.literal("Protección " + tier.label()).setStyle(Style.EMPTY.withColor(color).withBold(true).withItalic(false));
      if (registered != null) {
         ItemStack stack = new ItemStack(registered, amount);
         stack.setHoverName(name);
         return stack;
      } else {
         ItemStack stack = new ItemStack(itemForTier(tier), amount);
         CompoundTag tag = stack.getOrCreateTag();
         CompoundTag root = new CompoundTag();
         root.putString("tier", tier.id);
         tag.put("claimblocks", root);
         ListTag ench = new ListTag();
         CompoundTag e = new CompoundTag();
         e.putString("id", "minecraft:unbreaking");
         e.putInt("lvl", 1);
         ench.add(e);
         tag.put("Enchantments", ench);
         tag.putInt("HideFlags", 1);
         stack.setHoverName(name);
         List<Component> lore = new ArrayList<>();
         lore.add(Component.literal("Radio: " + tier.radius + " · Altura: ±" + tier.height).withStyle(ChatFormatting.GRAY));
         lore.add(Component.literal("Coloca para crear una protección").withStyle(color));
         setLore(stack, lore);
         return stack;
      }
   }

   public static void setLore(ItemStack stack, List<Component> lore) {
      CompoundTag display = stack.getOrCreateTagElement("display");
      ListTag loreList = new ListTag();

      for (Component line : lore) {
         loreList.add(StringTag.valueOf(Serializer.toJson(line)));
      }

      display.put("Lore", loreList);
   }

   public static String readTierId(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         if (stack.getItem() instanceof ProtectionItem pi) {
            return pi.tier.id;
         } else {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("claimblocks", 10)) {
               CompoundTag root = tag.getCompound("claimblocks");
               return !root.contains("tier", 8) ? null : root.getString("tier");
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static ClaimTier readTier(ItemStack stack) {
      String id = readTierId(stack);
      return id == null ? null : ClaimTier.byId(id);
   }
}
