package com.fsrecipes.client;

import com.fsrecipes.BanMode;
import com.fsrecipes.FSRecipes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Marca en el tooltip los items con la receta o el item baneado. */
@Mod.EventBusSubscriber(modid = FSRecipes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientTooltips {
   private ClientTooltips() {
   }

   @SubscribeEvent
   public static void onTooltip(ItemTooltipEvent event) {
      ItemStack stack = event.getItemStack();
      if (stack == null || stack.isEmpty()) {
         return;
      }

      BanMode mode = ClientHooks.mode(stack.getItem());
      if (mode == BanMode.RECIPE) {
         event.getToolTip().add(Component.literal("§e\u2716 Receta baneada §7(no se puede craftear)"));
      } else if (mode == BanMode.ITEM) {
         event.getToolTip().add(Component.literal("§c\u2716 Item prohibido §7(no se puede tener ni usar)"));
      }
   }
}
