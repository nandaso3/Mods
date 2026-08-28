package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.util.PlayerLookup;
import java.util.ArrayList;
import java.util.Comparator;
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

public class MemberSelectMenu extends ChestMenu {
    private static final int SIZE = 54;
    private static final int ENTRIES_PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BY_NAME = 48;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_INFO = 50;
    private static final int SLOT_NEXT = 53;
    private final SimpleContainer chest;
    private final Claim claim;
    private final ServerPlayer viewer;
    private final int returnPage;
    private final int page;
    private List<ServerPlayer> candidates;

    public MemberSelectMenu(int i, Inventory inventory, Claim claim, int j, int k) {
        this(i, inventory, new SimpleContainer(54), claim, j, k);
    }

    private MemberSelectMenu(int i, Inventory inventory, SimpleContainer simplecontainer, Claim claim, int j, int k) {
        super(MenuType.GENERIC_9x6, i, inventory, simplecontainer, 6);
        this.chest = simplecontainer;
        this.claim = claim;
        this.viewer = (ServerPlayer)inventory.player;
        this.returnPage = j;
        this.page = k;
        this.rebuild();
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    private List<ServerPlayer> collectCandidates() {
        ArrayList<ServerPlayer> arraylist = new ArrayList<>();
        if (this.viewer.getServer() == null) {
            return arraylist;
        } else {
            for (ServerPlayer serverplayer : this.viewer.getServer().getPlayerList().getPlayers()) {
                if (!this.claim.isOwner(serverplayer.getUUID()) && !this.claim.isMember(serverplayer.getUUID())) {
                    arraylist.add(serverplayer);
                }
            }

            arraylist.sort(Comparator.comparing(serverplayer1 -> serverplayer1.getName().getString().toLowerCase()));
            return arraylist;
        }
    }

    private void rebuild() {
        this.candidates = this.collectCandidates();
        ItemStack itemstack = withName(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Component.literal(" "));

        for (int i = 0; i < 54; i++) {
            this.chest.setItem(i, itemstack.copy());
        }

        int l = this.page * 45;
        int j = Math.min(l + 45, this.candidates.size());
        if (this.candidates.isEmpty()) {
            this.chest
                .setItem(
                    22,
                    withLore(
                        withName(new ItemStack(Items.BARRIER), Component.literal("No hay jugadores disponibles").withStyle(ChatFormatting.RED)),
                        List.of(
                            Component.literal("Todos los conectados ya son miembros,").withStyle(ChatFormatting.GRAY),
                            Component.literal("o eres el único en el servidor.").withStyle(ChatFormatting.GRAY),
                            Component.literal("Usa \"Escribir nombre\" para alguien offline.").withStyle(ChatFormatting.YELLOW)
                        )
                    )
                );
        } else {
            for (int k = l; k < j; k++) {
                this.chest.setItem(k - l, playerHead(this.candidates.get(k)));
            }
        }

        if (this.page > 0) {
            this.chest.setItem(45, withName(new ItemStack(Items.ARROW), Component.literal("<< Página anterior").withStyle(ChatFormatting.AQUA)));
        }

        this.chest
            .setItem(
                48,
                withLore(
                    withName(
                        new ItemStack(Items.NAME_TAG),
                        Component.literal("Escribir nombre").withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})
                    ),
                    List.of(
                        Component.literal("Para añadir a alguien que NO está conectado.").withStyle(ChatFormatting.GRAY),
                        Component.literal("Se pide el nombre por chat.").withStyle(ChatFormatting.GRAY)
                    )
                )
            );
        this.chest.setItem(49, withName(new ItemStack(Items.ARROW), Component.literal("Volver al menú de la zona").withStyle(ChatFormatting.AQUA)));
        this.chest
            .setItem(
                50,
                withLore(
                    withName(
                        new ItemStack(Items.PAPER),
                        Component.literal("Miembros: " + this.claim.getMembers().size())
                            .withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})
                    ),
                    List.of(Component.literal("Clic en una cabeza para añadir a ese jugador.").withStyle(ChatFormatting.GRAY))
                )
            );
        if (j < this.candidates.size()) {
            this.chest.setItem(53, withName(new ItemStack(Items.ARROW), Component.literal("Página siguiente >>").withStyle(ChatFormatting.AQUA)));
        }

        this.broadcastChanges();
    }

    private static ItemStack playerHead(ServerPlayer serverplayer) {
        String s = serverplayer.getName().getString();
        ItemStack itemstack = new ItemStack(Items.PLAYER_HEAD);
        itemstack.getOrCreateTag().putString("SkullOwner", s);
        return withLore(
            withName(itemstack, Component.literal(s).withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})),
            List.of(Component.literal("Clic para añadirlo como miembro").withStyle(ChatFormatting.GRAY))
        );
    }

    public void clicked(int i, int l, ClickType clicktype, Player player) {
        if (i >= 0 && i < 54) {
            if (i == 49) {
                ClaimMenuHandler.open(this.viewer, this.claim, this.returnPage);
            } else if (i == 45 && this.page > 0) {
                open(this.viewer, this.claim, this.returnPage, this.page - 1);
            } else if (i == 53) {
                int k = Math.max(0, (this.candidates.size() - 1) / 45);
                if (this.page < k) {
                    open(this.viewer, this.claim, this.returnPage, this.page + 1);
                }
            } else if (i == 48) {
                ClaimMenuHandler.requestAddMember(this.viewer, this.claim, this.returnPage);
                this.viewer.closeContainer();
            } else {
                if (i >= 0 && i < 45) {
                    int j = this.page * 45 + i;
                    if (j >= this.candidates.size()) {
                        return;
                    }

                    if (ClaimManager.getInstance().findClaimById(this.claim.getClaimId()) == null) {
                        this.viewer.displayClientMessage(Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
                        this.viewer.closeContainer();
                        return;
                    }

                    ServerPlayer serverplayer = this.candidates.get(j);
                    PlayerLookup.Resolved playerlookup$resolved = PlayerLookup.resolve(this.viewer.getServer(), serverplayer.getName().getString());
                    if (playerlookup$resolved == null) {
                        playerlookup$resolved = new PlayerLookup.Resolved(serverplayer.getUUID(), serverplayer.getName().getString(), null);
                    }

                    ClaimMenuHandler.addMemberResolved(this.viewer, this.claim, playerlookup$resolved);
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

    public static void open(ServerPlayer serverplayer, final Claim claim, final int i, int j) {
        final int k = Math.max(0, j);
        NetworkHooks.openScreen(serverplayer, new MenuProvider() {
            public Component getDisplayName() {
                return Component.literal("Añadir miembro").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD});
            }

            public AbstractContainerMenu createMenu(int l, Inventory inventory, Player player) {
                return new MemberSelectMenu(l, inventory, claim, i, k);
            }
        });
    }
}
