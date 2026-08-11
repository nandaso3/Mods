package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkHooks;

public class AdminPanelHandler extends ChestMenu {
   private static final int CLAIMS_PER_PAGE = 45;
   private final SimpleContainer inv;
   private final ServerPlayer viewer;
   private final int page;
   private final List<Claim> claims;

   public AdminPanelHandler(int syncId, Inventory pInv, int page) {
      this(syncId, pInv, new SimpleContainer(54), page);
   }

   private AdminPanelHandler(int syncId, Inventory pInv, SimpleContainer inv, int page) {
      super(MenuType.GENERIC_9x6, syncId, pInv, inv, 6);
      this.inv = inv;
      this.viewer = (ServerPlayer)pInv.player;
      this.page = page;
      this.claims = ClaimManager.getInstance().getAllClaims();
      this.rebuild();
   }

   public boolean stillValid(Player player) {
      return true;
   }

   public ItemStack quickMoveStack(Player player, int index) {
      return ItemStack.EMPTY;
   }

   private void rebuild() {
      ItemStack bg = withName(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Component.literal(" "));

      for (int i = 0; i < 54; i++) {
         this.inv.setItem(i, bg.copy());
      }

      int start = this.page * 45;
      int end = Math.min(start + 45, this.claims.size());

      for (int i = start; i < end; i++) {
         this.inv.setItem(i - start, claimItem(this.claims.get(i)));
      }

      if (this.page > 0) {
         this.inv.setItem(45, withName(new ItemStack(Items.ARROW), Component.literal("<< Página anterior").withStyle(ChatFormatting.AQUA)));
      }

      this.inv
         .setItem(
            46,
            withLore(
               withName(
                  new ItemStack(Items.BOOK),
                  Component.literal("Estadísticas").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})
               ),
               List.of(Component.literal("Resumen del servidor").withStyle(ChatFormatting.GRAY))
            )
         );
      this.inv
         .setItem(
            47,
            withLore(
               withName(
                  new ItemStack(Items.COMPARATOR),
                  Component.literal("Flags Globales").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})
               ),
               List.of(Component.literal("PVP / Mob griefing / Fire").withStyle(ChatFormatting.GRAY))
            )
         );
      boolean bypassing = ClaimManager.getInstance().isBypassing(this.viewer.getUUID());
      this.inv
         .setItem(
            48,
            withLore(
               withName(
                  new ItemStack(Items.ENDER_EYE),
                  Component.literal("Modo Bypass: " + (bypassing ? "ON" : "OFF"))
                     .withStyle(new ChatFormatting[]{bypassing ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD})
               ),
               List.of(Component.literal("Ignorar protecciones de zonas").withStyle(ChatFormatting.GRAY))
            )
         );
      this.inv.setItem(49, withName(new ItemStack(Items.BARRIER), Component.literal("Cerrar panel").withStyle(ChatFormatting.WHITE)));
      if (end < this.claims.size()) {
         this.inv.setItem(53, withName(new ItemStack(Items.ARROW), Component.literal("Página siguiente >>").withStyle(ChatFormatting.AQUA)));
      }

      this.broadcastChanges();
   }

   private static ItemStack claimItem(Claim c) {
      ClaimTier tier = c.getTier();
      Block block = tier != null ? ClaimBlocks.blockForTier(tier) : null;
      ItemStack stack = block != null ? new ItemStack(block.asItem()) : new ItemStack(Items.PAPER);
      Component name = Component.literal(c.getOwnerName() + " - " + c.sizeLabel())
         .withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
      return withLore(
         withName(stack, name),
         List.of(
            Component.literal("Posición: X:" + c.getX() + " Z:" + c.getZ()).withStyle(ChatFormatting.GRAY),
            Component.literal("Dimensión: " + c.getWorld()).withStyle(ChatFormatting.DARK_AQUA),
            Component.literal("Clic para gestionar este claim").withStyle(ChatFormatting.YELLOW)
         )
      );
   }

   static ItemStack withName(ItemStack s, Component t) {
      s.setHoverName(t);
      return s;
   }

   static ItemStack withLore(ItemStack s, List<Component> lore) {
      ClaimBlocks.setLore(s, lore);
      return s;
   }

   public void clicked(int slot, int button, ClickType clickType, Player player) {
      if (slot >= 0 && slot < 54) {
         if (slot == 45 && this.page > 0) {
            open(this.viewer, this.page - 1);
         } else if (slot == 53) {
            int max = (this.claims.size() - 1) / 45;
            if (this.page < max) {
               open(this.viewer, this.page + 1);
            }
         } else if (slot == 49) {
            this.viewer.closeContainer();
         } else if (slot == 46) {
            this.viewer.closeContainer();
            this.viewer.server.getCommands().performPrefixedCommand(this.viewer.createCommandSourceStack(), "claimadmin stats");
         } else if (slot == 47) {
            AdminGlobalFlagsHandler.open(this.viewer);
         } else if (slot == 48) {
            ClaimManager.getInstance().toggleBypass(this.viewer.getUUID());
            this.rebuild();
         } else {
            int idx = this.page * 45 + slot;
            if (idx < this.claims.size()) {
               AdminClaimSubMenuHandler.open(this.viewer, this.claims.get(idx).getClaimId());
            }
         }
      }
   }

   public static void open(ServerPlayer player, int page) {
      final int p = Math.max(0, page);
      NetworkHooks.openScreen(player, new MenuProvider() {
         public Component getDisplayName() {
            return Component.literal("Panel de Administración").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
         }

         public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
            return new AdminPanelHandler(id, inv, p);
         }
      });
   }

   public static Claim findClaim(UUID id) {
      for (Claim c : ClaimManager.getInstance().getAllClaims()) {
         if (c.getClaimId().equals(id)) {
            return c;
         }
      }

      return null;
   }
}
