package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.GlobalFlags;
import java.util.List;
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
import net.minecraftforge.network.NetworkHooks;

public class AdminGlobalFlagsHandler extends ChestMenu {
   private final SimpleContainer inv;
   private final ServerPlayer viewer;

   public AdminGlobalFlagsHandler(int syncId, Inventory pInv) {
      this(syncId, pInv, new SimpleContainer(54));
   }

   private AdminGlobalFlagsHandler(int syncId, Inventory pInv, SimpleContainer inv) {
      super(MenuType.GENERIC_9x6, syncId, pInv, inv, 6);
      this.inv = inv;
      this.viewer = (ServerPlayer)pInv.player;
      this.rebuild();
   }

   public boolean stillValid(Player player) {
      return true;
   }

   public ItemStack quickMoveStack(Player player, int index) {
      return ItemStack.EMPTY;
   }

   private void rebuild() {
      ItemStack bg = withName(new ItemStack(Items.BLACK_STAINED_GLASS_PANE), Component.literal(" "));

      for (int i = 0; i < 54; i++) {
         this.inv.setItem(i, bg.copy());
      }

      GlobalFlags g = GlobalFlags.getInstance();
      this.inv.setItem(11, flagButton("PVP global", g.globalPVP, "Permite PVP fuera de claims"));
      this.inv.setItem(13, flagButton("Mob griefing global", g.globalMobGriefing, "Mobs destruyen bloques fuera de claims"));
      this.inv.setItem(15, flagButton("Propagación de fuego", g.globalFireSpread, "Fire spread global gamerule"));
      this.inv.setItem(22, withName(new ItemStack(Items.ARROW), Component.literal("Volver al panel").withStyle(ChatFormatting.AQUA)));
      this.broadcastChanges();
   }

   private static ItemStack flagButton(String name, boolean on, String desc) {
      ItemStack stack = new ItemStack(on ? Items.LIME_DYE : Items.GRAY_DYE);
      Component title = Component.literal(name + " " + (on ? "[ON]" : "[OFF]"))
         .withStyle(new ChatFormatting[]{on ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD});
      return withLore(
         withName(stack, title),
         List.of(
            Component.literal(desc).withStyle(ChatFormatting.GRAY),
            Component.literal("Estado: " + (on ? "ACTIVO" : "INACTIVO") + " - Clic para cambiar").withStyle(ChatFormatting.GRAY)
         )
      );
   }

   public void clicked(int slot, int button, ClickType clickType, Player player) {
      if (slot >= 0 && slot < 54) {
         if (slot == 22) {
            AdminPanelHandler.open(this.viewer, 0);
         } else {
            GlobalFlags g = GlobalFlags.getInstance();
            String name = null;
            boolean newVal = false;
            if (slot == 11) {
               name = "globalPVP";
               newVal = !g.globalPVP;
            } else if (slot == 13) {
               name = "globalMobGriefing";
               newVal = !g.globalMobGriefing;
            } else if (slot == 15) {
               name = "globalFireSpread";
               newVal = !g.globalFireSpread;
            }

            if (name != null) {
               g.set(name, newVal, this.viewer.server);
               Component bcast = Component.literal("[!] Un administrador cambió una configuración global del servidor.").withStyle(ChatFormatting.YELLOW);
               this.viewer.server.getPlayerList().getPlayers().forEach(p -> p.displayClientMessage(bcast, false));
               this.rebuild();
            }
         }
      }
   }

   private static ItemStack withName(ItemStack s, Component t) {
      s.setHoverName(t);
      return s;
   }

   private static ItemStack withLore(ItemStack s, List<Component> lore) {
      ClaimBlocks.setLore(s, lore);
      return s;
   }

   public static void open(ServerPlayer player) {
      NetworkHooks.openScreen(player, new MenuProvider() {
         public Component getDisplayName() {
            return Component.literal("Flags Globales").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
         }

         public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
            return new AdminGlobalFlagsHandler(id, inv);
         }
      });
   }
}
