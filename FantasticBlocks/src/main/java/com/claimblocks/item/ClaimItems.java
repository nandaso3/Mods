package com.claimblocks.item;

import com.claimblocks.data.ClaimTier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ClaimItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "claimblocks");
   private static final Map<String, RegistryObject<Item>> BY_TIER = new HashMap<>();

   private ClaimItems() {
   }

   public static void register(IEventBus modBus) {
      ITEMS.register(modBus);
   }

   public static Item itemFor(ClaimTier tier) {
      if (tier == null) {
         return null;
      } else {
         RegistryObject<Item> ro = BY_TIER.get(tier.id);
         return ro != null && ro.isPresent() ? (Item)ro.get() : null;
      }
   }

   public static String registryName(ClaimTier tier) {
      return tier == null ? "" : "claimblocks:proteccion_" + tier.label().toLowerCase(Locale.ROOT);
   }

   static {
      for (ClaimTier tier : ClaimTier.VALUES) {
         String name = "proteccion_" + tier.label().toLowerCase(Locale.ROOT);
         BY_TIER.put(tier.id, ITEMS.register(name, () -> new ProtectionItem(tier)));
      }
   }
}
