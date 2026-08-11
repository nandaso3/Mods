package com.fsrecipes.client;

import com.fsrecipes.BanMode;
import com.fsrecipes.client.screen.RecipeBanScreen;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/** Copia local (cliente) del estado de baneos, usada por la GUI y los tooltips. */
public final class ClientHooks {
   private static final Map<ResourceLocation, BanMode> CLIENT_BANS = new LinkedHashMap<>();

   private ClientHooks() {
   }

   public static Map<ResourceLocation, BanMode> bans() {
      return CLIENT_BANS;
   }

   public static BanMode mode(ResourceLocation id) {
      return id == null ? null : CLIENT_BANS.get(id);
   }

   public static BanMode mode(Item item) {
      if (item == null || CLIENT_BANS.isEmpty()) {
         return null;
      }
      return mode(ForgeRegistries.ITEMS.getKey(item));
   }

   /** Cambio optimista en el cliente para que la GUI responda al instante. */
   public static void setLocal(ResourceLocation id, BanMode mode) {
      if (id != null) {
         if (mode == null) {
            CLIENT_BANS.remove(id);
         } else {
            CLIENT_BANS.put(id, mode);
         }
      }
   }

   public static void openScreen(Map<ResourceLocation, BanMode> bans) {
      CLIENT_BANS.clear();
      CLIENT_BANS.putAll(bans);
      Minecraft.getInstance().setScreen(new RecipeBanScreen());
   }

   public static void updateBans(Map<ResourceLocation, BanMode> bans) {
      CLIENT_BANS.clear();
      CLIENT_BANS.putAll(bans);
      if (Minecraft.getInstance().screen instanceof RecipeBanScreen screen) {
         screen.onBansUpdated();
      }
   }
}
