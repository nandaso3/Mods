package com.fsrecipes;

import com.fsrecipes.compat.CuriosCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hace cumplir el modo {@link BanMode#ITEM}: el item no se puede tener, usar,
 * recoger ni sacar del creativo, y se borra de cualquier inventario donde aparezca,
 * incluidos los anidados (mochilas, shulkers, cofres de mods...).
 *
 * <p>Nada de esto se ejecuta si no hay ningun item en modo ITEM: todos los
 * handlers salen por {@link RecipeBans#hasItemBans()} en la primera linea.
 */
@Mod.EventBusSubscriber(modid = FSRecipes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemBanEnforcer {
   /** Cada cuantos ticks se revisan los slots directos del jugador. */
   private static final int SWEEP_INTERVAL = 10;
   /** Cada cuantos ticks se entra ademas en los inventarios anidados. */
   private static final int DEEP_SWEEP_INTERVAL = 40;

   private ItemBanEnforcer() {
   }

   // ------------------------------------------------------------------ jugadores

   @SubscribeEvent
   public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
      if (event.phase == TickEvent.Phase.END
            && RecipeBans.hasItemBans()
            && event.player instanceof ServerPlayer sp) {
         if (sp.tickCount % DEEP_SWEEP_INTERVAL == 0) {
            sweepPlayer(sp, true);
         } else if (sp.tickCount % SWEEP_INTERVAL == 0) {
            sweepPlayer(sp, false);
         }
      }
   }

   /** Revisa a fondo el inventario de todos los jugadores conectados. */
   public static void sweepAll(MinecraftServer server) {
      if (server != null && RecipeBans.hasItemBans()) {
         for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sweepPlayer(sp, true);
         }
      }
   }

   public static int sweepPlayer(ServerPlayer sp) {
      return sweepPlayer(sp, true);
   }

   /**
    * Borra los items prohibidos del jugador: mochila, armadura, mano secundaria,
    * cursor, rejilla de crafteo, cofre de ender y, si {@code deep}, tambien todo lo
    * que haya dentro de contenedores anidados (mochilas, shulkers, bolsas...).
    *
    * @return cuantos stacks se eliminaron
    */
   public static int sweepPlayer(ServerPlayer sp, boolean deep) {
      if (sp == null || !RecipeBans.hasItemBans()) {
         return 0;
      }

      int removed = 0;
      Inventory inv = sp.getInventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack st = inv.getItem(i);
         if (st == null || st.isEmpty()) {
            continue;
         }

         if (RecipeBans.isItemBanned(st)) {
            inv.setItem(i, ItemStack.EMPTY);
            removed++;
         } else if (deep) {
            removed += DeepSweeper.cleanNested(st);
         }
      }

      // Cofre de ender: no forma parte del inventario normal.
      removed += DeepSweeper.sweepContainer(sp.getEnderChestInventory());

      // Slots de Curios: tampoco forman parte del inventario. Aqui es donde suele
      // acabar la mochila equipada, y hay que entrar en ella.
      if (deep) {
         removed += CuriosCompat.sweep(sp);
      }

      // Rejilla de crafteo 2x2 del inventario propio.
      if (sp.inventoryMenu != null) {
         CraftingContainer craft = sp.inventoryMenu.getCraftSlots();
         for (int i = 0; i < craft.getContainerSize(); i++) {
            if (RecipeBans.isItemBanned(craft.getItem(i))) {
               craft.setItem(i, ItemStack.EMPTY);
               removed++;
            }
         }
      }

      // Stack "en el cursor" del menu abierto (asi no se puede pasear el item por GUIs).
      AbstractContainerMenu menu = sp.containerMenu;
      if (menu != null) {
         if (RecipeBans.isItemBanned(menu.getCarried())) {
            menu.setCarried(ItemStack.EMPTY);
            removed++;
         } else if (deep) {
            removed += DeepSweeper.cleanNested(menu.getCarried());
         }
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
            Component.literal("§6[Recipes] §cSe elimino " + removed + " item(s) prohibido(s)."), true
         );
      }

      return removed;
   }

   /** Al abrir cualquier contenedor (cofre, maquina de mod, mochila) se limpia al momento. */
   @SubscribeEvent
   public static void onContainerOpen(PlayerContainerEvent.Open event) {
      if (RecipeBans.hasItemBans() && event.getEntity() instanceof ServerPlayer) {
         DeepSweeper.sweepMenu(event.getContainer());
      }
   }

   // ------------------------------------------------------------------ mundo

   /**
    * Un item prohibido nunca llega a existir como entidad en el suelo. Prioridad alta
    * para adelantarnos a los mods que recogen items del suelo automaticamente (como el
    * Pickup Upgrade de Sophisticated Backpacks).
    */
   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
      if (!RecipeBans.hasItemBans() || event.getLevel().isClientSide()) {
         return;
      }

      Entity entity = event.getEntity();
      if (entity instanceof ItemEntity ie) {
         if (RecipeBans.isItemBanned(ie.getItem())) {
            event.setCanceled(true);
         } else {
            DeepSweeper.cleanNested(ie.getItem());
         }
      } else if (!(entity instanceof Player)) {
         DeepSweeper.sweepEntity(entity);
      }
   }

   /** Los chunks que se cargan despues de aplicar el baneo tambien se limpian. */
   @SubscribeEvent
   public static void onChunkLoad(ChunkEvent.Load event) {
      if (RecipeBans.hasItemBans()
            && !event.getLevel().isClientSide()
            && event.getChunk() instanceof LevelChunk chunk) {
         DeepSweeper.sweepChunk(chunk);
      }
   }

   /** Por si alguna entidad prohibida ya estaba en el mundo antes del baneo. */
   @SubscribeEvent(priority = EventPriority.HIGHEST)
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

   /**
    * Purga puntual de todo lo que este cargado. Se lanza al aplicar un baneo de item;
    * de los chunks que se carguen despues se encarga {@link #onChunkLoad}.
    */
   public static void purgeEverything(MinecraftServer server) {
      if (server == null || !RecipeBans.hasItemBans()) {
         return;
      }

      long start = System.nanoTime();
      int removed = 0;
      int chunks = 0;
      int view = Math.max(2, server.getPlayerList().getViewDistance() + 1);

      for (ServerLevel level : server.getAllLevels()) {
         ServerChunkCache source = level.getChunkSource();
         Set<Long> visited = new HashSet<>();
         List<ChunkPos> targets = new ArrayList<>();

         for (ServerPlayer sp : level.players()) {
            ChunkPos center = sp.chunkPosition();
            for (int dx = -view; dx <= view; dx++) {
               for (int dz = -view; dz <= view; dz++) {
                  targets.add(new ChunkPos(center.x + dx, center.z + dz));
               }
            }
         }

         for (long packed : level.getForcedChunks()) {
            targets.add(new ChunkPos(packed));
         }

         for (ChunkPos pos : targets) {
            if (visited.add(pos.toLong())) {
               LevelChunk chunk = source.getChunkNow(pos.x, pos.z);
               if (chunk != null) {
                  chunks++;
                  removed += DeepSweeper.sweepChunk(chunk);
               }
            }
         }

         for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Player)) {
               removed += DeepSweeper.sweepEntity(entity);
            }
         }
      }

      sweepAll(server);

      long ms = (System.nanoTime() - start) / 1000000L;
      FSRecipes.LOGGER.info(
         "[FantasticRecipes] Purga de items prohibidos: {} stack(s) eliminados en {} chunk(s) cargados ({} ms).",
         removed,
         chunks,
         ms
      );
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

         if (event.getEntity() instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("§6[Recipes] §cEse item esta prohibido."), true);
            sweepPlayer(sp, false);
         }
      }
   }
}
