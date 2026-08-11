package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraftforge.network.NetworkHooks;

public class AdminClaimSubMenuHandler extends ChestMenu {
   private static final Map<UUID, UUID> pendingTransfers = new ConcurrentHashMap<>();
   private final SimpleContainer inv;
   private final ServerPlayer viewer;
   private final UUID claimId;
   private boolean awaitingDeleteConfirm = false;

   public AdminClaimSubMenuHandler(int syncId, Inventory pInv, UUID claimId) {
      this(syncId, pInv, new SimpleContainer(54), claimId);
   }

   private AdminClaimSubMenuHandler(int syncId, Inventory pInv, SimpleContainer inv, UUID claimId) {
      super(MenuType.GENERIC_9x6, syncId, pInv, inv, 6);
      this.inv = inv;
      this.viewer = (ServerPlayer)pInv.player;
      this.claimId = claimId;
      this.rebuild();
   }

   public boolean stillValid(Player player) {
      return true;
   }

   public ItemStack quickMoveStack(Player player, int index) {
      return ItemStack.EMPTY;
   }

   private Claim claim() {
      return AdminPanelHandler.findClaim(this.claimId);
   }

   private void rebuild() {
      ItemStack bg = withName(new ItemStack(Items.BLACK_STAINED_GLASS_PANE), Component.literal(" "));

      for (int i = 0; i < 54; i++) {
         this.inv.setItem(i, bg.copy());
      }

      Claim c = this.claim();
      if (c != null) {
         String owner = c.getOwnerName();
         this.inv
            .setItem(
               11,
               withLore(
                  withName(
                     new ItemStack(Items.ENDER_PEARL),
                     Component.literal("Teleportar al claim").withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD})
                  ),
                  List.of(Component.literal("Te lleva al centro del claim de " + owner).withStyle(ChatFormatting.GRAY))
               )
            );
         this.inv
            .setItem(
               12,
               withLore(
                  withName(
                     new ItemStack(Items.COMPARATOR),
                     Component.literal("Ver y editar flags").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})
                  ),
                  List.of(Component.literal("Abre el menú de flags de este claim").withStyle(ChatFormatting.GRAY))
               )
            );
         if (this.awaitingDeleteConfirm) {
            this.inv
               .setItem(
                  13,
                  withLore(
                     withName(
                        new ItemStack(Items.TNT),
                        Component.literal("¿Confirmar eliminación?").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                     ),
                     List.of(
                        Component.literal("Esto eliminará la zona de " + owner).withStyle(ChatFormatting.YELLOW),
                        Component.literal("El bloque NO se devuelve al dueño").withStyle(ChatFormatting.RED),
                        Component.literal("Clic de nuevo para confirmar").withStyle(ChatFormatting.GRAY)
                     )
                  )
               );
         } else {
            this.inv
               .setItem(
                  13,
                  withLore(
                     withName(
                        new ItemStack(Items.BARRIER),
                        Component.literal("Eliminar este claim").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                     ),
                     List.of(
                        Component.literal("Elimina la zona de " + owner).withStyle(ChatFormatting.YELLOW),
                        Component.literal("Clic para pedir confirmación").withStyle(ChatFormatting.GRAY)
                     )
                  )
               );
         }

         this.inv
            .setItem(
               15,
               withLore(
                  withName(
                     new ItemStack(Items.PAPER),
                     Component.literal("Transferir claim").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})
                  ),
                  List.of(Component.literal("Cambia el dueño de esta zona").withStyle(ChatFormatting.GRAY))
               )
            );
         this.inv.setItem(22, withName(new ItemStack(Items.ARROW), Component.literal("Volver al panel").withStyle(ChatFormatting.AQUA)));
         this.broadcastChanges();
      }
   }

   public void clicked(int slot, int button, ClickType clickType, Player player) {
      if (slot >= 0 && slot < 54) {
         Claim c = this.claim();
         if (c == null) {
            this.viewer.closeContainer();
         } else {
            if (slot != 13 && this.awaitingDeleteConfirm) {
               this.awaitingDeleteConfirm = false;
            }

            if (slot == 22) {
               AdminPanelHandler.open(this.viewer, 0);
            } else if (slot == 11) {
               this.teleportToClaim(c);
            } else if (slot == 12) {
               String title = "[Admin] Flags de " + c.getOwnerName() + " - " + c.sizeLabel();
               ClaimMenuHandler.open(this.viewer, c, 0, title);
            } else if (slot == 13) {
               if (!this.awaitingDeleteConfirm) {
                  this.awaitingDeleteConfirm = true;
                  this.rebuild();
               } else {
                  this.adminDelete(c);
               }
            } else if (slot == 15) {
               this.startTransfer(c);
            }
         }
      }
   }

   private void teleportToClaim(Claim c) {
      ServerLevel world = null;

      for (ServerLevel w : this.viewer.server.getAllLevels()) {
         if (w.dimension().location().toString().equals(c.getWorld())) {
            world = w;
            break;
         }
      }

      if (world == null) {
         this.viewer.displayClientMessage(Component.literal("[x] No se pudo encontrar la dimensión.").withStyle(ChatFormatting.RED), false);
         this.viewer.closeContainer();
      } else {
         int topY = world.getHeight(Types.MOTION_BLOCKING, c.getX(), c.getZ());
         this.viewer.teleportTo(world, (double)c.getX() + 0.5, (double)topY, (double)c.getZ() + 0.5, this.viewer.getYRot(), this.viewer.getXRot());
         this.viewer.displayClientMessage(Component.literal("✔ Teletransportado a la zona de " + c.getOwnerName() + ".").withStyle(ChatFormatting.GREEN), false);
         this.viewer.closeContainer();
      }
   }

   private void adminDelete(Claim c) {
      String ownerName = c.getOwnerName();
      UUID ownerId = c.getOwnerUUID();
      ServerLevel world = null;

      for (ServerLevel w : this.viewer.server.getAllLevels()) {
         if (w.dimension().location().toString().equals(c.getWorld())) {
            world = w;
            break;
         }
      }

      BlockPos pos = c.getCenter();
      if (world != null && ClaimBlocks.isClaimConcreteForTier(world.getBlockState(pos).getBlock(), c.getTier())) {
         world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      }

      ClaimManager.getInstance().removeClaim(world, c.getCenter());
      this.viewer
         .displayClientMessage(
            Component.literal("✔ Zona de " + ownerName + " eliminada por admin.").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}),
            false
         );
      ServerPlayer owner = this.viewer.server.getPlayerList().getPlayer(ownerId);
      MutableComponent msg = Component.literal("[!] Un administrador eliminó tu zona ")
         .withStyle(ChatFormatting.YELLOW)
         .append(Component.literal(c.sizeLabel()).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}))
         .append(Component.literal(" en X:" + c.getX() + " Z:" + c.getZ()).withStyle(ChatFormatting.YELLOW));
      if (owner != null) {
         owner.displayClientMessage(msg, false);
      } else {
         ClaimManager.getInstance().queueMessage(ownerId, msg);
      }

      this.viewer.closeContainer();
   }

   private void startTransfer(Claim c) {
      pendingTransfers.put(this.viewer.getUUID(), c.getClaimId());
      this.viewer.displayClientMessage(Component.literal("[i] Escribe el nombre del nuevo dueño en el chat.").withStyle(ChatFormatting.AQUA), false);
      this.viewer.displayClientMessage(Component.literal("    Escribe 'cancelar' para abortar.").withStyle(ChatFormatting.GRAY), false);
      this.viewer.closeContainer();
   }

   public static UUID popPendingTransfer(UUID opId) {
      return pendingTransfers.remove(opId);
   }

   public static boolean hasPendingTransfer(UUID opId) {
      return pendingTransfers.containsKey(opId);
   }

   private static ItemStack withName(ItemStack s, Component t) {
      s.setHoverName(t);
      return s;
   }

   private static ItemStack withLore(ItemStack s, List<Component> lore) {
      ClaimBlocks.setLore(s, lore);
      return s;
   }

   public static void open(ServerPlayer player, final UUID claimId) {
      Claim c = AdminPanelHandler.findClaim(claimId);
      if (c == null) {
         player.displayClientMessage(Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
      } else {
         String title = "Admin - " + c.getOwnerName() + " " + c.sizeLabel();
         if (title.length() > 40) {
            title = title.substring(0, 37) + "...";
         }

         final String t = title;
         NetworkHooks.openScreen(player, new MenuProvider() {
            public Component getDisplayName() {
               return Component.literal(t).withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
            }

            public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
               return new AdminClaimSubMenuHandler(id, inv, claimId);
            }
         });
      }
   }
}
