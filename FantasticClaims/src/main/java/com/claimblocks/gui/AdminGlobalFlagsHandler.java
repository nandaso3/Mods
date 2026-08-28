package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.GlobalFlags;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

    public AdminGlobalFlagsHandler(int i, Inventory inventory) {
        this(i, inventory, new SimpleContainer(54));
    }

    private AdminGlobalFlagsHandler(int i, Inventory inventory, SimpleContainer simplecontainer) {
        super(MenuType.GENERIC_9x6, i, inventory, simplecontainer, 6);
        this.inv = simplecontainer;
        this.viewer = (ServerPlayer)inventory.player;
        this.rebuild();
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    private void rebuild() {
        ItemStack itemstack = withName(new ItemStack(Items.BLACK_STAINED_GLASS_PANE), Component.literal(" "));

        for (int i = 0; i < 54; i++) {
            this.inv.setItem(i, itemstack.copy());
        }

        GlobalFlags globalflags = GlobalFlags.getInstance();
        this.inv.setItem(11, flagButton("PVP global", globalflags.globalPVP, "Permite PVP fuera de claims"));
        this.inv.setItem(13, flagButton("Mob griefing global", globalflags.globalMobGriefing, "Mobs destruyen bloques fuera de claims"));
        this.inv.setItem(15, flagButton("Propagación de fuego", globalflags.globalFireSpread, "Fire spread global gamerule"));
        this.inv.setItem(17, flagButton("Sin spawn de mobs (global)", globalflags.globalNoMobSpawn, "Ningún mob spawnea en TODO el servidor"));
        this.inv.setItem(22, withName(new ItemStack(Items.ARROW), Component.literal("Volver al panel").withStyle(ChatFormatting.AQUA)));
        this.broadcastChanges();
    }

    private static ItemStack flagButton(String s, boolean flag, String s1) {
        ItemStack itemstack = new ItemStack(flag ? Items.LIME_DYE : Items.GRAY_DYE);
        MutableComponent mutablecomponent = Component.literal(s + " " + (flag ? "[ON]" : "[OFF]"))
            .withStyle(new ChatFormatting[]{flag ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD});
        return withLore(
            withName(itemstack, mutablecomponent),
            List.of(
                Component.literal(s1).withStyle(ChatFormatting.GRAY),
                Component.literal("Estado: " + (flag ? "ACTIVO" : "INACTIVO") + " - Clic para cambiar").withStyle(ChatFormatting.GRAY)
            )
        );
    }

    public void clicked(int i, int j, ClickType clicktype, Player player) {
        if (i >= 0 && i < 54) {
            if (i == 22) {
                AdminPanelHandler.open(this.viewer, 0);
            } else {
                GlobalFlags globalflags = GlobalFlags.getInstance();
                String s = null;
                boolean flag = false;
                if (i == 11) {
                    s = "globalPVP";
                    flag = !globalflags.globalPVP;
                } else if (i == 13) {
                    s = "globalMobGriefing";
                    flag = !globalflags.globalMobGriefing;
                } else if (i == 15) {
                    s = "globalFireSpread";
                    flag = !globalflags.globalFireSpread;
                } else if (i == 17) {
                    s = "globalNoMobSpawn";
                    flag = !globalflags.globalNoMobSpawn;
                }

                if (s != null) {
                    globalflags.set(s, flag, this.viewer.server);
                    MutableComponent mutablecomponent = Component.literal("[!] Un administrador cambió una configuración global del servidor.")
                        .withStyle(ChatFormatting.YELLOW);
                    this.viewer.server.getPlayerList().getPlayers().forEach(serverplayer -> lambda$clicked$0(mutablecomponent, serverplayer));
                    this.rebuild();
                }
            }
        }
    }

    private static ItemStack withName(ItemStack itemstack, Component component) {
        itemstack.setHoverName(component);
        return itemstack;
    }

    private static ItemStack withLore(ItemStack itemstack, List<Component> list) {
        ClaimBlocks.setLore(itemstack, list);
        return itemstack;
    }

    public static void open(ServerPlayer serverplayer) {
        NetworkHooks.openScreen(serverplayer, new MenuProvider() {
            public Component getDisplayName() {
                return Component.literal("Flags Globales").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
            }

            public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
                return new AdminGlobalFlagsHandler(i, inventory);
            }
        });
    }

    private static void lambda$clicked$0(Component component, ServerPlayer serverplayer) {
        serverplayer.displayClientMessage(component, false);
    }
}
