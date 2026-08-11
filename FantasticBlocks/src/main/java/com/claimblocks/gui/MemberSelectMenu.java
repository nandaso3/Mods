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

/**
 * Selector visual de jugadores para anadir miembros a una zona.
 *
 * <p>Es la ruta principal de "Anadir miembro" desde la 7.7.0: al ser clics dentro de un inventario,
 * no toca el chat en ningun momento, asi que funciona igual en Forge puro y en servidores hibridos
 * (Mohist/Arclight/Magma) por muchos plugins de chat que haya. El prompt escrito sigue disponible con
 * clic derecho para anadir a jugadores que no estan conectados.
 */
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

    public MemberSelectMenu(int syncId, Inventory pInv, Claim claim, int returnPage, int page) {
        this(syncId, pInv, new SimpleContainer(SIZE), claim, returnPage, page);
    }

    private MemberSelectMenu(int syncId, Inventory pInv, SimpleContainer chest, Claim claim, int returnPage, int page) {
        super(MenuType.GENERIC_9x6, syncId, pInv, chest, 6);
        this.chest = chest;
        this.claim = claim;
        this.viewer = (ServerPlayer) pInv.player;
        this.returnPage = returnPage;
        this.page = page;
        this.rebuild();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Jugadores conectados que todavia se pueden anadir a esta zona. */
    private List<ServerPlayer> collectCandidates() {
        List<ServerPlayer> out = new ArrayList<>();
        if (this.viewer.getServer() == null) {
            return out;
        }

        for (ServerPlayer p : this.viewer.getServer().getPlayerList().getPlayers()) {
            if (this.claim.isOwner(p.getUUID()) || this.claim.isMember(p.getUUID())) {
                continue;
            }
            out.add(p);
        }

        out.sort(Comparator.comparing(p -> p.getName().getString().toLowerCase()));
        return out;
    }

    private void rebuild() {
        this.candidates = this.collectCandidates();

        ItemStack bg = withName(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Component.literal(" "));
        for (int i = 0; i < SIZE; i++) {
            this.chest.setItem(i, bg.copy());
        }

        int start = this.page * ENTRIES_PER_PAGE;
        int end = Math.min(start + ENTRIES_PER_PAGE, this.candidates.size());

        if (this.candidates.isEmpty()) {
            this.chest.setItem(
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
            for (int i = start; i < end; i++) {
                this.chest.setItem(i - start, playerHead(this.candidates.get(i)));
            }
        }

        if (this.page > 0) {
            this.chest.setItem(SLOT_PREV, withName(new ItemStack(Items.ARROW), Component.literal("<< Página anterior").withStyle(ChatFormatting.AQUA)));
        }

        this.chest.setItem(
            SLOT_BY_NAME,
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

        this.chest.setItem(
            SLOT_BACK,
            withName(new ItemStack(Items.ARROW), Component.literal("Volver al menú de la zona").withStyle(ChatFormatting.AQUA))
        );

        this.chest.setItem(
            SLOT_INFO,
            withLore(
                withName(
                    new ItemStack(Items.PAPER),
                    Component.literal("Miembros: " + this.claim.getMembers().size()).withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})
                ),
                List.of(Component.literal("Clic en una cabeza para añadir a ese jugador.").withStyle(ChatFormatting.GRAY))
            )
        );

        if (end < this.candidates.size()) {
            this.chest.setItem(SLOT_NEXT, withName(new ItemStack(Items.ARROW), Component.literal("Página siguiente >>").withStyle(ChatFormatting.AQUA)));
        }

        this.broadcastChanges();
    }

    private static ItemStack playerHead(ServerPlayer player) {
        String name = player.getName().getString();
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        // SkullOwner por nombre: el servidor resuelve la skin y funciona en clientes vanilla.
        head.getOrCreateTag().putString("SkullOwner", name);
        return withLore(
            withName(head, Component.literal(name).withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})),
            List.of(Component.literal("Clic para añadirlo como miembro").withStyle(ChatFormatting.GRAY))
        );
    }

    @Override
    public void clicked(int slot, int button, ClickType clickType, Player player) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }

        if (slot == SLOT_BACK) {
            ClaimMenuHandler.open(this.viewer, this.claim, this.returnPage);
            return;
        }

        if (slot == SLOT_PREV && this.page > 0) {
            open(this.viewer, this.claim, this.returnPage, this.page - 1);
            return;
        }

        if (slot == SLOT_NEXT) {
            int max = Math.max(0, (this.candidates.size() - 1) / ENTRIES_PER_PAGE);
            if (this.page < max) {
                open(this.viewer, this.claim, this.returnPage, this.page + 1);
            }
            return;
        }

        if (slot == SLOT_BY_NAME) {
            ClaimMenuHandler.requestAddMember(this.viewer, this.claim, this.returnPage);
            this.viewer.closeContainer();
            return;
        }

        if (slot >= 0 && slot < ENTRIES_PER_PAGE) {
            int idx = this.page * ENTRIES_PER_PAGE + slot;
            if (idx >= this.candidates.size()) {
                return;
            }

            // La zona puede haberse borrado con el selector abierto: si ya no existe, escribir el alta
            // iria a un objeto huerfano y le diriamos al jugador que fue bien.
            if (ClaimManager.getInstance().findClaimById(this.claim.getClaimId()) == null) {
                this.viewer.displayClientMessage(
                    Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false
                );
                this.viewer.closeContainer();
                return;
            }

            // Se resuelve de nuevo en el momento del clic: el candidato pudo desconectarse desde que se
            // pinto el menu, y entonces el aviso debe encolarse en vez de mandarse a una conexion muerta.
            ServerPlayer snapshot = this.candidates.get(idx);
            PlayerLookup.Resolved target = PlayerLookup.resolve(this.viewer.getServer(), snapshot.getName().getString());
            if (target == null) {
                target = new PlayerLookup.Resolved(snapshot.getUUID(), snapshot.getName().getString(), null);
            }

            ClaimMenuHandler.addMemberResolved(this.viewer, this.claim, target);
            this.rebuild();
        }
    }

    private static ItemStack withName(ItemStack stack, Component name) {
        stack.setHoverName(name);
        return stack;
    }

    private static ItemStack withLore(ItemStack stack, List<Component> lore) {
        ClaimBlocks.setLore(stack, lore);
        return stack;
    }

    public static void open(ServerPlayer player, final Claim claim, final int returnPage, int page) {
        final int p = Math.max(0, page);
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Añadir miembro").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD});
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
                return new MemberSelectMenu(id, inv, claim, returnPage, p);
            }
        });
    }
}
