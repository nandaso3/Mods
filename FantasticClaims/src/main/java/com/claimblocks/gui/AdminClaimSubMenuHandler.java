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

    public AdminClaimSubMenuHandler(int i, Inventory inventory, UUID uuid) {
        this(i, inventory, new SimpleContainer(54), uuid);
    }

    private AdminClaimSubMenuHandler(int i, Inventory inventory, SimpleContainer simplecontainer, UUID uuid) {
        super(MenuType.GENERIC_9x6, i, inventory, simplecontainer, 6);
        this.inv = simplecontainer;
        this.viewer = (ServerPlayer)inventory.player;
        this.claimId = uuid;
        this.rebuild();
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    private Claim claim() {
        return AdminPanelHandler.findClaim(this.claimId);
    }

    private void rebuild() {
        ItemStack itemstack = withName(new ItemStack(Items.BLACK_STAINED_GLASS_PANE), Component.literal(" "));

        for (int i = 0; i < 54; i++) {
            this.inv.setItem(i, itemstack.copy());
        }

        Claim claim = this.claim();
        if (claim != null) {
            String s = claim.getOwnerName();
            this.inv
                .setItem(
                    11,
                    withLore(
                        withName(
                            new ItemStack(Items.ENDER_PEARL),
                            Component.literal("Teleportar al claim").withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD})
                        ),
                        List.of(Component.literal("Te lleva al centro del claim de " + s).withStyle(ChatFormatting.GRAY))
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
                                Component.literal("Esto eliminará la zona de " + s).withStyle(ChatFormatting.YELLOW),
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
                                Component.literal("Elimina la zona de " + s).withStyle(ChatFormatting.YELLOW),
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

    public void clicked(int i, int j, ClickType clicktype, Player player) {
        if (i >= 0 && i < 54) {
            Claim claim = this.claim();
            if (claim == null) {
                this.viewer.closeContainer();
            } else {
                if (i != 13 && this.awaitingDeleteConfirm) {
                    this.awaitingDeleteConfirm = false;
                }

                if (i == 22) {
                    AdminPanelHandler.open(this.viewer, 0);
                } else if (i == 11) {
                    this.teleportToClaim(claim);
                } else if (i == 12) {
                    String s = "[Admin] Flags de " + claim.getOwnerName() + " - " + claim.sizeLabel();
                    ClaimMenuHandler.open(this.viewer, claim, 0, s);
                } else if (i == 13) {
                    if (!this.awaitingDeleteConfirm) {
                        this.awaitingDeleteConfirm = true;
                        this.rebuild();
                    } else {
                        this.adminDelete(claim);
                    }
                } else if (i == 15) {
                    this.startTransfer(claim);
                }
            }
        }
    }

    private void teleportToClaim(Claim claim) {
        ServerLevel serverlevel = null;

        for (ServerLevel serverlevel1 : this.viewer.server.getAllLevels()) {
            if (serverlevel1.dimension().location().toString().equals(claim.getWorld())) {
                serverlevel = serverlevel1;
                break;
            }
        }

        if (serverlevel == null) {
            this.viewer.displayClientMessage(Component.literal("[x] No se pudo encontrar la dimensión.").withStyle(ChatFormatting.RED), false);
            this.viewer.closeContainer();
        } else {
            int i = serverlevel.getHeight(Types.MOTION_BLOCKING, claim.getX(), claim.getZ());
            this.viewer
                .teleportTo(serverlevel, (double)claim.getX() + 0.5, (double)i, (double)claim.getZ() + 0.5, this.viewer.getYRot(), this.viewer.getXRot());
            this.viewer.displayClientMessage(Component.literal("✔ Teletransportado a la zona de " + claim.getOwnerName() + ".").withStyle(ChatFormatting.GREEN), false);
            this.viewer.closeContainer();
        }
    }

    private void adminDelete(Claim claim) {
        String s = claim.getOwnerName();
        UUID uuid = claim.getOwnerUUID();
        ServerLevel serverlevel = null;

        for (ServerLevel serverlevel1 : this.viewer.server.getAllLevels()) {
            if (serverlevel1.dimension().location().toString().equals(claim.getWorld())) {
                serverlevel = serverlevel1;
                break;
            }
        }

        BlockPos blockpos = claim.getCenter();
        if (serverlevel != null && ClaimBlocks.isClaimConcreteForTier(serverlevel.getBlockState(blockpos).getBlock(), claim.getTier())) {
            serverlevel.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 3);
        }

        ClaimManager.getInstance().removeClaim(serverlevel, claim.getCenter());
        this.viewer
            .displayClientMessage(
                Component.literal("✔ Zona de " + s + " eliminada por admin.").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}),
                false
            );
        ServerPlayer serverplayer = this.viewer.server.getPlayerList().getPlayer(uuid);
        MutableComponent mutablecomponent = Component.literal("[!] Un administrador eliminó tu zona ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(claim.sizeLabel()).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}))
            .append(Component.literal(" en X:" + claim.getX() + " Z:" + claim.getZ()).withStyle(ChatFormatting.YELLOW));
        if (serverplayer != null) {
            serverplayer.displayClientMessage(mutablecomponent, false);
        } else {
            ClaimManager.getInstance().queueMessage(uuid, mutablecomponent);
        }

        this.viewer.closeContainer();
    }

    private void startTransfer(Claim claim) {
        pendingTransfers.put(this.viewer.getUUID(), claim.getClaimId());
        this.viewer.displayClientMessage(Component.literal("[i] Escribe el nombre del nuevo dueño en el chat.").withStyle(ChatFormatting.AQUA), false);
        this.viewer.displayClientMessage(Component.literal("    Escribe 'cancelar' para abortar.").withStyle(ChatFormatting.GRAY), false);
        this.viewer.closeContainer();
    }

    public static UUID popPendingTransfer(UUID uuid) {
        return pendingTransfers.remove(uuid);
    }

    public static boolean hasPendingTransfer(UUID uuid) {
        return pendingTransfers.containsKey(uuid);
    }

    public static void clearPendingTransfer(UUID uuid) {
        if (uuid != null) {
            pendingTransfers.remove(uuid);
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

    public static void open(ServerPlayer serverplayer, final UUID uuid) {
        Claim claim = AdminPanelHandler.findClaim(uuid);
        if (claim == null) {
            serverplayer.displayClientMessage(Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
        } else {
            String s1 = "Admin - " + claim.getOwnerName() + " " + claim.sizeLabel();
            if (s1.length() > 40) {
                s1 = s1.substring(0, 37) + "...";
            }

            final String s = s1;

            NetworkHooks.openScreen(serverplayer, new MenuProvider() {
                public Component getDisplayName() {
                    return Component.literal(s).withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
                }

                public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
                    return new AdminClaimSubMenuHandler(i, inventory, uuid);
                }
            });
        }
    }
}
