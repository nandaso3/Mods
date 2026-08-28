package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import java.util.List;
import java.util.UUID;
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
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkHooks;

public class AdminPanelHandler extends ChestMenu {
    private static final int CLAIMS_PER_PAGE = 45;
    private final SimpleContainer inv;
    private final ServerPlayer viewer;
    private final int page;
    private final List<Claim> claims;

    public AdminPanelHandler(int i, Inventory inventory, int j) {
        this(i, inventory, new SimpleContainer(54), j);
    }

    private AdminPanelHandler(int i, Inventory inventory, SimpleContainer simplecontainer, int j) {
        super(MenuType.GENERIC_9x6, i, inventory, simplecontainer, 6);
        this.inv = simplecontainer;
        this.viewer = (ServerPlayer)inventory.player;
        this.page = j;
        this.claims = ClaimManager.getInstance().getAllClaims();
        this.rebuild();
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    private void rebuild() {
        ItemStack itemstack = withName(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Component.literal(" "));

        for (int i = 0; i < 54; i++) {
            this.inv.setItem(i, itemstack.copy());
        }

        int l = this.page * 45;
        int j = Math.min(l + 45, this.claims.size());

        for (int k = l; k < j; k++) {
            this.inv.setItem(k - l, claimItem(this.claims.get(k)));
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
        boolean flag = ClaimManager.getInstance().isBypassing(this.viewer.getUUID());
        this.inv
            .setItem(
                48,
                withLore(
                    withName(
                        new ItemStack(Items.ENDER_EYE),
                        Component.literal("Modo Bypass: " + (flag ? "ON" : "OFF"))
                            .withStyle(new ChatFormatting[]{flag ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD})
                    ),
                    List.of(Component.literal("Ignorar protecciones de zonas").withStyle(ChatFormatting.GRAY))
                )
            );
        this.inv.setItem(49, withName(new ItemStack(Items.BARRIER), Component.literal("Cerrar panel").withStyle(ChatFormatting.WHITE)));
        if (j < this.claims.size()) {
            this.inv.setItem(53, withName(new ItemStack(Items.ARROW), Component.literal("Página siguiente >>").withStyle(ChatFormatting.AQUA)));
        }

        this.broadcastChanges();
    }

    private static ItemStack claimItem(Claim claim) {
        ClaimTier claimtier = claim.getTier();
        Block block = claimtier != null ? ClaimBlocks.blockForTier(claimtier) : null;
        ItemStack itemstack = block != null ? new ItemStack(block.asItem()) : new ItemStack(Items.PAPER);
        MutableComponent mutablecomponent = Component.literal(claim.getOwnerName() + " - " + claim.sizeLabel())
            .withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
        return withLore(
            withName(itemstack, mutablecomponent),
            List.of(
                Component.literal("Posición: X:" + claim.getX() + " Z:" + claim.getZ()).withStyle(ChatFormatting.GRAY),
                Component.literal("Dimensión: " + claim.getWorld()).withStyle(ChatFormatting.DARK_AQUA),
                Component.literal("Clic para gestionar este claim").withStyle(ChatFormatting.YELLOW)
            )
        );
    }

    static ItemStack withName(ItemStack itemstack, Component component) {
        itemstack.setHoverName(component);
        return itemstack;
    }

    static ItemStack withLore(ItemStack itemstack, List<Component> list) {
        ClaimBlocks.setLore(itemstack, list);
        return itemstack;
    }

    public void clicked(int i, int l, ClickType clicktype, Player player) {
        if (i >= 0 && i < 54) {
            if (i == 45 && this.page > 0) {
                open(this.viewer, this.page - 1);
            } else if (i == 53) {
                int j = (this.claims.size() - 1) / 45;
                if (this.page < j) {
                    open(this.viewer, this.page + 1);
                }
            } else if (i == 49) {
                this.viewer.closeContainer();
            } else if (i == 46) {
                this.viewer.closeContainer();
                this.viewer.server.getCommands().performPrefixedCommand(this.viewer.createCommandSourceStack(), "fsclaimadmin stats");
            } else if (i == 47) {
                AdminGlobalFlagsHandler.open(this.viewer);
            } else if (i == 48) {
                ClaimManager.getInstance().toggleBypass(this.viewer.getUUID());
                this.rebuild();
            } else {
                int k = this.page * 45 + i;
                if (k < this.claims.size()) {
                    AdminClaimSubMenuHandler.open(this.viewer, this.claims.get(k).getClaimId());
                }
            }
        }
    }

    public static void open(ServerPlayer serverplayer, int i) {
        final int j = Math.max(0, i);
        NetworkHooks.openScreen(serverplayer, new MenuProvider() {
            public Component getDisplayName() {
                return Component.literal("Panel de Administración").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
            }

            public AbstractContainerMenu createMenu(int k, Inventory inventory, Player player) {
                return new AdminPanelHandler(k, inventory, j);
            }
        });
    }

    public static Claim findClaim(UUID uuid) {
        for (Claim claim : ClaimManager.getInstance().getAllClaims()) {
            if (claim.getClaimId().equals(uuid)) {
                return claim;
            }
        }

        return null;
    }
}
