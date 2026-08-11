package com.fsrecipes;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hace cumplir el modo {@link BanMode#ITEM}: el item no se puede tener, usar,
 * recoger ni sacar del creativo.
 *
 * <p>Nada de esto se ejecuta si no hay ningun item en modo ITEM: todos los
 * handlers salen por {@link RecipeBans#hasItemBans()} en la primera linea.
 */
@Mod.EventBusSubscriber(modid = FSRecipes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemBanEnforcer {
   /** Cada cuantos ticks se revisa el inventario de cada jugador. */
   private static final int SWEEP_INTERVAL = 10;

   private ItemBanEnforcer() {
   }

   // ------------------------------------------------------------------ inventarios

   @SubscribeEvent
   public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
      if (event.phase == net.minecraftforge.event.TickEvent.Phase.END
            && RecipeBans.hasItemBans()
            && event.player instanceof ServerPlayer sp
            && sp.tickCount % SWEEP_INTERVAL == 0) {
         sweepPlayer(sp);
      }
   }

   /** Revisa el inventario de todos los jugadores conectados. */
   public static void sweepAll(MinecraftServer server) {
      if (server != null && RecipeBans.hasItemBans()) {
         for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sweepPlayer(sp);
         }
      }
   }

   /**
    * Borra del inventario del jugador (mochila, armadura, mano secundaria, cursor y
    * rejilla de crafteo personal) todos los items prohibidos.
    *
    * @return cuantos stacks se eliminaron
    */
   public static int sweepPlayer(ServerPlayer sp) {
      if (sp == null || !RecipeBans.hasItemBans()) {
         return 0;
      }

      int removed = 0;
      Inventory inv = sp.getInventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         if (RecipeBans.isItemBanned(inv.getItem(i))) {
            inv.setItem(i, ItemStack.EMPTY);
            removed++;
         }
      }

      // Rejilla de crafteo 2x2 del inventario propio.
      if (sp.inventoryMenu != null) {
         net.minecraft.world.inventory.CraftingContainer craft = sp.inventoryMenu.getCraftSlots();
         for (int i = 0; i < craft.getContainerSize(); i++) {
            if (RecipeBans.isItemBanned(craft.getItem(i))) {
               craft.setItem(i, ItemStack.EMPTY);
               removed++;
            }
         }
      }

      // Stack "en el cursor" del menu abierto (asi no se puede pasear el item por GUIs).
      AbstractContainerMenu menu = sp.containerMenu;
      if (menu != null && RecipeBans.isItemBanned(menu.getCarried())) {
         menu.setCarried(ItemStack.EMPTY);
         removed++;
      }

      if (removed > 0) {
         inv.setChanged();
         if (sp.inventoryMenu != null) {
            sp.inventoryMenu.broadcastChanges();
         }
         if (menu != null) {
            menu.broadcastChanges();
         }
         sp.displayClientMessage(
            Component.literal("§6[Recipes] §cSe elimino " + removed + " item(s) prohibido(s) de tu inventario."), true
         );
      }

      return removed;
   }

   // ------------------------------------------------------------------ mundo

   /** Un item prohibido nunca llega a existir como entidad en el suelo. */
   @SubscribeEvent
   public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
      if (RecipeBans.hasItemBans()
            && !event.getLevel().isClientSide()
            && event.getEntity() instanceof ItemEntity ie
            && RecipeBans.isItemBanned(ie.getItem())) {
         event.setCanceled(true);
      }
   }

   /** Por si alguna entidad prohibida ya estaba en el mundo antes del baneo. */
   @SubscribeEvent
   public static void onPickup(EntityItemPickupEvent event) {
      if (RecipeBans.hasItemBans()) {
         ItemEntity ie = event.getItem();
         if (ie != null && RecipeBans.isItemBanned(ie.getItem())) {
            ie.discard();
            event.setCanceled(true);
         }
      }
   }

   /** Tirar un item prohibido lo destruye en lugar de dejarlo en el suelo. */
   @SubscribeEvent
   public static void onToss(ItemTossEvent event) {
      if (RecipeBans.hasItemBans() && RecipeBans.isItemBanned(event.getEntity().getItem())) {
         event.setCanceled(true);
         event.getPlayer().containerMenu.broadcastChanges();
      }
   }

   // ------------------------------------------------------------------ uso

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
      denyIfBanned(event, event.getItemStack());
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
      denyIfBanned(event, event.getItemStack());
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
      denyIfBanned(event, event.getItemStack());
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
      denyIfBanned(event, event.getItemStack());
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
      denyIfBanned(event, event.getItemStack());
   }

   /** Comer, beber, tensar arcos, usar escudos... */
   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onUseStart(LivingEntityUseItemEvent.Start event) {
      if (RecipeBans.hasItemBans() && RecipeBans.isItemBanned(event.getItem())) {
         event.setCanceled(true);
      }
   }

   /** No se puede golpear con un item prohibido. */
   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onAttack(AttackEntityEvent event) {
      if (RecipeBans.hasItemBans() && RecipeBans.isItemBanned(event.getEntity().getMainHandItem())) {
         event.setCanceled(true);
      }
   }

   private static void denyIfBanned(PlayerInteractEvent event, ItemStack stack) {
      if (RecipeBans.hasItemBans() && RecipeBans.isItemBanned(stack)) {
         event.setCanceled(true);

         Player player = event.getEntity();
         if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("§6[Recipes] §cEse item esta prohibido."), true);
            sweepPlayer(sp);
         }
      }
   }
}
