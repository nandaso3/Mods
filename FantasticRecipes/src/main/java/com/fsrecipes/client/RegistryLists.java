package com.fsrecipes.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public final class RegistryLists {
   /**
    * El registro de items no cambia en runtime, asi que la lista ordenada se calcula
    * una sola vez. Antes se reconstruia y reordenaba en cada clic de la GUI, lo que en
    * modpacks grandes (miles de items) se notaba.
    */
   private static List<Item> cachedItems;

   private RegistryLists() {
   }

   public static List<Item> items() {
      List<Item> cache = cachedItems;
      if (cache == null) {
         List<Item> list = new ArrayList<>(ForgeRegistries.ITEMS.getValues());
         list.remove(Items.AIR);
         list.sort(Comparator.comparing(RegistryLists::itemId));
         cache = List.copyOf(list);
         cachedItems = cache;
      }

      return cache;
   }

   public static List<Item> itemsOfTab(ResourceKey<CreativeModeTab> key) {
      Set<Item> set = new LinkedHashSet<>();

      try {
         CreativeModeTab tab = (CreativeModeTab)BuiltInRegistries.CREATIVE_MODE_TAB.get(key);
         if (tab != null) {
            for (ItemStack stack : tab.getDisplayItems()) {
               if (stack != null && !stack.isEmpty()) {
                  set.add(stack.getItem());
               }
            }
         }
      } catch (Throwable var5) {
      }

      return new ArrayList<>(set);
   }

   public static String itemId(Item item) {
      ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
      return rl == null ? "minecraft:air" : rl.toString();
   }

   public static ResourceLocation id(Item item) {
      return ForgeRegistries.ITEMS.getKey(item);
   }

   public static String itemName(Item item) {
      return new ItemStack(item).getHoverName().getString();
   }
}
