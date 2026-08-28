package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.chat.ChatPromptRouter;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimConfig;
import com.claimblocks.data.ClaimFlags;
import com.claimblocks.data.ClaimGroup;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import com.claimblocks.util.PlayerLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.network.NetworkHooks;

public class ClaimMenuHandler extends ChestMenu {
    public static final int SIZE = 54;
    private static final int[] FLAG_SLOTS_P0 = new int[]{18, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30, 31};
    private static final int[] FLAG_SLOTS_P1 = new int[]{18, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30, 31, 32, 33, 34, 35, 27};
    private static final ClaimFlags.FlagId[] PAGE_0 = new ClaimFlags.FlagId[]{
        ClaimFlags.FlagId.BUILDING,
        ClaimFlags.FlagId.BREAKING,
        ClaimFlags.FlagId.EXPLOSIONS,
        ClaimFlags.FlagId.FIRE,
        ClaimFlags.FlagId.MOB_SPAWN,
        ClaimFlags.FlagId.PVP,
        ClaimFlags.FlagId.MOB_DAMAGE,
        ClaimFlags.FlagId.ALERTS,
        ClaimFlags.FlagId.PUBLIC_MODE,
        ClaimFlags.FlagId.ANIMAL_KILLING,
        ClaimFlags.FlagId.CHEST_ACCESS,
        ClaimFlags.FlagId.CROP_HARVEST,
        ClaimFlags.FlagId.BURN_HOSTILES
    };
    private static final ClaimFlags.FlagId[] PAGE_1 = new ClaimFlags.FlagId[]{
        ClaimFlags.FlagId.ITEM_USE,
        ClaimFlags.FlagId.ENTITY_INTERACT,
        ClaimFlags.FlagId.TRAMPLING,
        ClaimFlags.FlagId.FLUIDS,
        ClaimFlags.FlagId.PVP_ALL,
        ClaimFlags.FlagId.TREE_CHOPPING,
        ClaimFlags.FlagId.SHOW_WELCOME,
        ClaimFlags.FlagId.ANVIL_USE,
        ClaimFlags.FlagId.ENDER_PEARL,
        ClaimFlags.FlagId.SIGN_EDITING,
        ClaimFlags.FlagId.DOORS_ACCESS,
        ClaimFlags.FlagId.EFFECT_REGEN,
        ClaimFlags.FlagId.EFFECT_RESIST,
        ClaimFlags.FlagId.EFFECT_SPEED,
        ClaimFlags.FlagId.ALLOW_FLIGHT,
        ClaimFlags.FlagId.SHOW_LEAVE,
        ClaimFlags.FlagId.SHOW_BORDER,
        ClaimFlags.FlagId.SHOW_PARTICLES
    };
    private static final int[] FLAG_SLOTS_P2 = new int[]{20, 22, 24};
    private static final ClaimFlags.FlagId[] PAGE_2 = new ClaimFlags.FlagId[]{
        ClaimFlags.FlagId.ALL_MOB_SPAWN, ClaimFlags.FlagId.PASSIVE_MOB_SPAWN, ClaimFlags.FlagId.BLOCK_ALL_INTERACT
    };
    private static final ClaimFlags.FlagId[][] PAGES = new ClaimFlags.FlagId[][]{PAGE_0, PAGE_1, PAGE_2};
    private static final int[][] PAGE_SLOTS = new int[][]{FLAG_SLOTS_P0, FLAG_SLOTS_P1, FLAG_SLOTS_P2};
    private static final int LAST_PAGE = PAGES.length - 1;
    private static final long PROMPT_TTL_MS = 90000L;
    private static final Map<UUID, ClaimMenuHandler.PendingChat> pending = new ConcurrentHashMap<>();
    private static final Map<UUID, String> pendingMergeName = new ConcurrentHashMap<>();
    private static final Map<String, ClaimMenuHandler.MergeInvite> invites = new ConcurrentHashMap<>();
    private final SimpleContainer chest;
    private final Claim claim;
    private final ServerPlayer viewer;
    private final int page;
    private boolean awaitingDeleteConfirm = false;

    public ClaimMenuHandler(int i, Inventory inventory, Claim claim, int j) {
        this(i, inventory, new SimpleContainer(54), claim, j);
    }

    private ClaimMenuHandler(int i, Inventory inventory, SimpleContainer simplecontainer, Claim claim, int j) {
        super(MenuType.GENERIC_9x6, i, inventory, simplecontainer, 6);
        this.chest = simplecontainer;
        this.claim = claim;
        this.viewer = (ServerPlayer)inventory.player;
        this.page = j;
        this.rebuild();
    }

    public Claim getClaim() {
        return this.claim;
    }

    public int getPage() {
        return this.page;
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
            this.chest.setItem(i, itemstack.copy());
        }

        ClaimGroup claimgroup1 = ClaimManager.getInstance().getGroupOf(this.claim);
        String s = claimgroup1 != null ? "Grupo: " + claimgroup1.getName() : "Zona " + this.claim.sizeLabel() + " - " + this.claim.getOwnerName();
        this.chest
            .setItem(
                4,
                withName(
                    new ItemStack(Items.PAPER),
                    Component.literal(truncate(s, 30)).withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})
                )
            );
        this.chest
            .setItem(
                11,
                withLore(
                    withName(new ItemStack(Items.COMPASS), Component.literal("Coordenadas").withStyle(ChatFormatting.AQUA)),
                    List.of(
                        Component.literal("X=" + this.claim.getX() + " Y=" + this.claim.getY() + " Z=" + this.claim.getZ()).withStyle(ChatFormatting.WHITE)
                    )
                )
            );
        this.chest
            .setItem(
                13,
                withLore(
                    withName(new ItemStack(Items.PLAYER_HEAD), Component.literal("Dueño").withStyle(ChatFormatting.AQUA)),
                    List.of(
                        Component.literal(truncate(this.claim.getOwnerName(), 35)).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD})
                    )
                )
            );
        this.chest
            .setItem(
                15,
                withLore(
                    withName(new ItemStack(Items.DIAMOND), Component.literal("Zona " + this.claim.sizeLabel()).withStyle(ChatFormatting.YELLOW)),
                    List.of(
                        Component.literal("Zona " + this.claim.sizeLabel() + " bloques").withStyle(ChatFormatting.GRAY),
                        Component.literal("Altura: +/-" + this.claim.getHeight()).withStyle(ChatFormatting.GRAY)
                    )
                )
            );
        this.chest
            .setItem(
                17,
                withLore(
                    withName(new ItemStack(Items.MAP), Component.literal("Mundo").withStyle(ChatFormatting.AQUA)),
                    List.of(Component.literal(truncate(this.claim.getWorld(), 35)).withStyle(ChatFormatting.GRAY))
                )
            );
        ClaimFlags claimflags = this.claim.getFlags();
        ClaimFlags.FlagId[] aclaimflags$flagid = PAGES[this.pageIndex()];
        int[] aint = PAGE_SLOTS[this.pageIndex()];
        int j = paidLevelOf(this.claim.getTier());

        for (int k = 0; k < aclaimflags$flagid.length; k++) {
            ClaimFlags.FlagId claimflags$flagid = aclaimflags$flagid[k];
            int l = requiredPaidLevel(claimflags$flagid);
            if (l > 0 && j < l) {
                this.chest.setItem(aint[k], this.lockedEffectButton(claimflags$flagid, l));
            } else {
                this.chest.setItem(aint[k], this.flagButton(claimflags$flagid, claimflags.get(claimflags$flagid)));
            }
        }

        this.chest
            .setItem(
                38,
                withLore(
                    withName(
                        new ItemStack(Items.WRITABLE_BOOK),
                        Component.literal("Miembros (" + this.claim.getMembers().size() + ")").withStyle(ChatFormatting.YELLOW)
                    ),
                    this.buildMemberLore()
                )
            );
        this.chest
            .setItem(
                40,
                withLore(
                    withName(new ItemStack(Items.NAME_TAG), Component.literal("Quitar miembro").withStyle(ChatFormatting.RED)),
                    List.of(
                        Component.literal("Pide nombre por chat").withStyle(ChatFormatting.GRAY),
                        Component.literal("Clic para eliminar a un invitado").withStyle(ChatFormatting.GRAY)
                    )
                )
            );
        this.chest
            .setItem(
                42,
                withLore(
                    withName(
                        new ItemStack(Items.PLAYER_HEAD),
                        Component.literal("Añadir miembro").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})
                    ),
                    List.of(
                        Component.literal("Clic izq: elegir de una lista").withStyle(ChatFormatting.GRAY),
                        Component.literal("Clic der: escribir el nombre por chat").withStyle(ChatFormatting.GRAY),
                        Component.literal("También sirve /fsclaim addmember <jugador>").withStyle(ChatFormatting.DARK_GRAY)
                    )
                )
            );
        this.chest
            .setItem(
                39,
                withLore(
                    withName(
                        new ItemStack(Items.IRON_BARS),
                        Component.literal("Banear jugador").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                    ),
                    this.buildBanLore()
                )
            );
        this.chest
            .setItem(
                41,
                withLore(
                    withName(new ItemStack(Items.TRIPWIRE_HOOK), Component.literal("Desbanear jugador").withStyle(ChatFormatting.GREEN)),
                    List.of(
                        Component.literal("Pide nombre por chat").withStyle(ChatFormatting.GRAY),
                        Component.literal("Clic para quitar del baneo").withStyle(ChatFormatting.GRAY)
                    )
                )
            );
        if (this.page > 0) {
            this.chest.setItem(45, withName(new ItemStack(Items.ARROW), Component.literal("<< Página anterior").withStyle(ChatFormatting.AQUA)));
        }

        if (this.awaitingDeleteConfirm) {
            this.chest
                .setItem(
                    46,
                    withLore(
                        withName(
                            new ItemStack(Items.TNT),
                            Component.literal("Confirmar eliminación").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                        ),
                        List.of(Component.literal("Haz clic de nuevo para confirmar").withStyle(ChatFormatting.YELLOW))
                    )
                );
            this.chest
                .setItem(
                    47,
                    withLore(
                        withName(
                            new ItemStack(Items.LIME_DYE),
                            Component.literal("Cancelar").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})
                        ),
                        List.of(Component.literal("Cancela la eliminación").withStyle(ChatFormatting.GRAY))
                    )
                );
        } else {
            this.chest
                .setItem(
                    46,
                    withLore(
                        withName(
                            new ItemStack(Items.BARRIER),
                            Component.literal("Eliminar zona").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                        ),
                        List.of(
                            Component.literal("Clic para iniciar eliminación").withStyle(ChatFormatting.YELLOW),
                            Component.literal("Devuelve la protección al inv.").withStyle(ChatFormatting.GRAY)
                        )
                    )
                );
        }

        this.chest.setItem(49, withName(new ItemStack(Items.RED_DYE), Component.literal("Cerrar").withStyle(ChatFormatting.WHITE)));
        this.chest.setItem(52, withName(new ItemStack(Items.BOOK), Component.literal("Ver lista de zonas").withStyle(ChatFormatting.AQUA)));
        if (this.page < LAST_PAGE) {
            this.chest
                .setItem(
                    53,
                    withLore(
                        withName(new ItemStack(Items.ARROW), Component.literal("Página siguiente >>").withStyle(ChatFormatting.AQUA)),
                        List.of(Component.literal("Página " + (this.page + 1) + " de " + (LAST_PAGE + 1)).withStyle(ChatFormatting.DARK_GRAY))
                    )
                );
        }

        ClaimGroup claimgroup;
        if ((claimgroup = ClaimManager.getInstance().getGroupOf(this.claim)) == null) {
            this.chest
                .setItem(
                    43,
                    withLore(
                        withName(
                            new ItemStack(Items.SLIME_BALL),
                            Component.literal("Unir protección").withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD})
                        ),
                        List.of(
                            Component.literal("Crea un grupo y une zonas de tu equipo").withStyle(ChatFormatting.GRAY),
                            Component.literal("Clic: elegir nombre e invitar jugadores").withStyle(ChatFormatting.GRAY)
                        )
                    )
                );
        } else {
            this.chest
                .setItem(
                    43,
                    withLore(
                        withName(
                            new ItemStack(Items.SLIME_BALL),
                            Component.literal(truncate("Grupo: " + claimgroup.getName(), 30))
                                .withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD})
                        ),
                        List.of(
                            Component.literal("Miembros registrados: " + claimgroup.getRegisteredPlayers().size()).withStyle(ChatFormatting.GRAY),
                            Component.literal("Clic: invitar mas jugadores").withStyle(ChatFormatting.GRAY)
                        )
                    )
                );
            this.chest
                .setItem(
                    44,
                    withLore(
                        withName(
                            new ItemStack(Items.SHEARS),
                            Component.literal("Disolver grupo").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                        ),
                        List.of(
                            Component.literal("Separa todas las piedras del grupo").withStyle(ChatFormatting.GRAY),
                            Component.literal("Cada zona vuelve a ser independiente").withStyle(ChatFormatting.GRAY)
                        )
                    )
                );
        }

        this.broadcastChanges();
    }

    private List<Component> buildMemberLore() {
        ArrayList<Component> arraylist = new ArrayList<>();
        if (this.claim.getMembers().isEmpty()) {
            arraylist.add(Component.literal("(sin miembros)").withStyle(ChatFormatting.DARK_GRAY));
            return arraylist;
        } else {
            int i = Math.min(5, this.claim.getMembers().size());

            for (int j = 0; j < i; j++) {
                String s = j < this.claim.getMemberNames().size() ? this.claim.getMemberNames().get(j) : this.claim.getMembers().get(j).toString();
                arraylist.add(Component.literal(truncate(" - " + s, 35)).withStyle(ChatFormatting.WHITE));
            }

            if (this.claim.getMembers().size() > i) {
                arraylist.add(Component.literal(" - ... y " + (this.claim.getMembers().size() - i) + " más").withStyle(ChatFormatting.GRAY));
            }

            return arraylist;
        }
    }

    private List<Component> buildBanLore() {
        ArrayList<Component> arraylist = new ArrayList<>();
        arraylist.add(Component.literal("Escribe el nombre por chat para banear.").withStyle(ChatFormatting.GRAY));
        arraylist.add(Component.literal("Si entran, la barrera los saca de la zona.").withStyle(ChatFormatting.DARK_GRAY));
        Set<UUID> set = this.claim.getBannedPlayers();
        arraylist.add(Component.literal("Baneados: " + set.size()).withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}));
        int i = 0;

        for (UUID uuid : set) {
            if (i++ >= 8) {
                arraylist.add(Component.literal(" - ...").withStyle(ChatFormatting.GRAY));
                break;
            }

            arraylist.add(Component.literal(truncate(" - " + PlayerLookup.nameOf(this.viewer.getServer(), uuid), 35)).withStyle(ChatFormatting.WHITE));
        }

        return arraylist;
    }

    public static void requestBanPlayer(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.BAN_PLAYER, claim.getClaimId(), i));
        serverplayer.displayClientMessage(Component.literal("[Protección] Escribe el nombre del jugador a BANEAR (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false);
    }

    public static void requestUnbanPlayer(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.UNBAN_PLAYER, claim.getClaimId(), i));
        serverplayer.displayClientMessage(
            Component.literal("[Protección] Escribe el nombre del jugador a DESBANEAR (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false
        );
    }

    private static void handleBanPlayer(ServerPlayer serverplayer, Claim claim, String s, int i) {
        String s1 = ChatPromptRouter.extractPlayerName(s);
        UUID uuid = claim.getClaimId();
        PlayerLookup.resolveAsync(
            serverplayer.getServer(),
            s1,
            playerlookup$resolved -> {
                if (!serverplayer.hasDisconnected()) {
                    Claim claim1 = findClaimById(uuid);
                    if (claim1 == null) {
                        serverplayer.displayClientMessage(Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
                    } else if (playerlookup$resolved == null) {
                        serverplayer.displayClientMessage(Component.literal("[x] Jugador no encontrado: " + s1).withStyle(ChatFormatting.RED), false);
                        open(serverplayer, claim1, i);
                    } else if (claim1.isOwner(playerlookup$resolved.id())) {
                        serverplayer.displayClientMessage(Component.literal("[x] No puedes banear al dueño.").withStyle(ChatFormatting.RED), false);
                        open(serverplayer, claim1, i);
                    } else {
                        claim1.banPlayer(playerlookup$resolved.id());
                        ClaimManager.getInstance().save();
                        serverplayer.displayClientMessage(
                            Component.literal("✔ " + playerlookup$resolved.name() + " baneado de la zona.").withStyle(ChatFormatting.GREEN), false
                        );
                        MutableComponent mutablecomponent = Component.literal("[!] Has sido baneado de una zona de " + serverplayer.getName().getString())
                            .withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD});
                        if (playerlookup$resolved.isOnline()) {
                            playerlookup$resolved.online().displayClientMessage(mutablecomponent, false);
                        } else {
                            ClaimManager.getInstance().queueMessage(playerlookup$resolved.id(), mutablecomponent);
                        }

                        open(serverplayer, claim1, i);
                    }
                }
            }
        );
    }

    private static void handleUnbanPlayer(ServerPlayer serverplayer, Claim claim, String s, int i) {
        String s1 = ChatPromptRouter.extractPlayerName(s);
        UUID uuid = claim.getClaimId();
        PlayerLookup.resolveAsync(serverplayer.getServer(), s1, playerlookup$resolved -> {
            if (!serverplayer.hasDisconnected()) {
                Claim claim1 = findClaimById(uuid);
                if (claim1 == null) {
                    serverplayer.displayClientMessage(Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
                } else {
                    if (playerlookup$resolved != null && claim1.isBanned(playerlookup$resolved.id())) {
                        claim1.unbanPlayer(playerlookup$resolved.id());
                        ClaimManager.getInstance().save();
                        serverplayer.displayClientMessage(Component.literal("✔ " + playerlookup$resolved.name() + " desbaneado.").withStyle(ChatFormatting.GREEN), false);
                    } else {
                        serverplayer.displayClientMessage(Component.literal("[x] Ese jugador no está baneado.").withStyle(ChatFormatting.RED), false);
                    }

                    open(serverplayer, claim1, i);
                }
            }
        });
    }

    private static int paidLevelOf(ClaimTier claimtier) {
        if (claimtier == null) {
            return 0;
        } else {
            String s1 = claimtier.id;
            String s = claimtier.id;
            String s2 = claimtier.id;

            return switch (s2) {
                case "claimstone_250x250" -> 1;
                case "claimstone_300x300" -> 2;
                case "claimstone_500x500" -> 3;
                default -> 0;
            };
        }
    }

    private static int requiredPaidLevel(ClaimFlags.FlagId claimflags$flagid) {
        return switch (claimflags$flagid) {
            case EFFECT_REGEN -> 1;
            case EFFECT_RESIST -> 2;
            case EFFECT_SPEED -> 2;
            case ALLOW_FLIGHT -> 3;
            default -> 0;
        };
    }

    private static String requiredTierLabel(int i) {
        return switch (i) {
            case 1 -> "250x250";
            case 2 -> "300x300";
            case 3 -> "500x500";
            default -> "?";
        };
    }

    private ItemStack lockedEffectButton(ClaimFlags.FlagId claimflags$flagid, int i) {
        ItemStack itemstack = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        return withLore(
            withName(itemstack, Component.literal(effectName(claimflags$flagid) + " [LOCKED]").withStyle(ChatFormatting.DARK_GRAY)),
            List.of(
                Component.literal("Requiere zona " + requiredTierLabel(i) + " o superior").withStyle(ChatFormatting.GRAY),
                Component.literal(effectShortDesc(claimflags$flagid)).withStyle(ChatFormatting.DARK_GRAY)
            )
        );
    }

    private static String effectShortDesc(ClaimFlags.FlagId claimflags$flagid) {
        return switch (claimflags$flagid) {
            case EFFECT_REGEN -> "Regenera vida a duenio y miembros";
            case EFFECT_RESIST -> "Reduce dano a duenio y miembros";
            case EFFECT_SPEED -> "Da velocidad a duenio y miembros";
            case ALLOW_FLIGHT -> "El duenio y los miembros pueden volar en la zona";
            default -> "Perk pasivo";
        };
    }

    private static String effectName(ClaimFlags.FlagId claimflags$flagid) {
        return switch (claimflags$flagid) {
            case EFFECT_REGEN -> "Regeneración pasiva";
            case EFFECT_RESIST -> "Resistencia pasiva";
            case EFFECT_SPEED -> "Velocidad pasiva";
            case ALLOW_FLIGHT -> "Vuelo en zona";
            default -> "Perk pasivo";
        };
    }

    private ItemStack flagButton(ClaimFlags.FlagId claimflags$flagid, boolean flag) {
        ItemStack itemstack = new ItemStack(flag ? Items.LIME_DYE : Items.GRAY_DYE);
        MutableComponent mutablecomponent = Component.literal(flagDisplayName(claimflags$flagid, flag))
            .withStyle(new ChatFormatting[]{flag ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD});
        String[] astring = flagLore(claimflags$flagid);
        return withLore(
            withName(itemstack, mutablecomponent),
            List.of(
                Component.literal(astring[0]).withStyle(ChatFormatting.GRAY),
                Component.literal("Estado: " + (flag ? "ACTIVO" : "INACTIVO") + " - " + astring[1]).withStyle(ChatFormatting.GRAY)
            )
        );
    }

    private static String flagDisplayName(ClaimFlags.FlagId claimflags$flagid, boolean flag) {
        return switch (claimflags$flagid) {
            case EFFECT_REGEN -> flag ? "Regeneración pasiva [ON]" : "Regeneración pasiva [OFF]";
            case EFFECT_RESIST -> flag ? "Resistencia pasiva [ON]" : "Resistencia pasiva [OFF]";
            case EFFECT_SPEED -> flag ? "Velocidad pasiva [ON]" : "Velocidad pasiva [OFF]";
            case ALLOW_FLIGHT -> flag ? "Vuelo en zona: ACTIVO [ON]" : "Vuelo en zona: inactivo [OFF]";
            case BUILDING -> flag ? "Construir: BLOQUEADO [ON]" : "Construir: permitido [OFF]";
            case BREAKING -> flag ? "Romper: BLOQUEADO [ON]" : "Romper: permitido [OFF]";
            case EXPLOSIONS -> flag ? "Explosiones: BLOQUEADAS [ON]" : "Explosiones: permitidas [OFF]";
            case FIRE -> flag ? "Fuego: BLOQUEADO [ON]" : "Fuego: permitido [OFF]";
            case MOB_SPAWN -> flag ? "Mobs hostiles: BLOQUEADOS [ON]" : "Mobs hostiles: permit. [OFF]";
            case PVP -> flag ? "PVP: BLOQUEADO [ON]" : "PVP: permitido [OFF]";
            case MOB_DAMAGE -> flag ? "Daño de mobs: BLOQUEADO [ON]" : "Daño de mobs: permit. [OFF]";
            case ALERTS -> flag ? "Alertas intrusos: ON [ON]" : "Alertas intrusos: OFF [OFF]";
            case ITEM_USE -> flag ? "Usar items: BLOQUEADO [ON]" : "Usar items: permitido [OFF]";
            case ENTITY_INTERACT -> flag ? "Entidades: BLOQUEADAS [ON]" : "Entidades: libres [OFF]";
            case TRAMPLING -> flag ? "Cultivos: PROTEGIDOS [ON]" : "Cultivos: sin protec. [OFF]";
            case FLUIDS -> flag ? "Fluidos: BLOQUEADOS [ON]" : "Fluidos: permitidos [OFF]";
            case PVP_ALL -> flag ? "Zona PVP libre: ACTIVA [ON]" : "Zona PVP libre: inact. [OFF]";
            case TREE_CHOPPING -> flag ? "Árboles: PROTEGIDOS [ON]" : "Árboles: se talan [OFF]";
            case PUBLIC_MODE -> flag ? "Modo visita: ACTIVO [ON]" : "Modo visita: inactivo [OFF]";
            case SHOW_WELCOME -> flag ? "Bienvenida custom: ON [ON]" : "Bienvenida custom: OFF [OFF]";
            case SHOW_LEAVE -> flag ? "Mensaje de salida: ON [ON]" : "Mensaje de salida: OFF [OFF]";
            case SHOW_BORDER -> flag ? "Ver contorno: ON [ON]" : "Ver contorno: OFF [OFF]";
            case SHOW_PARTICLES -> flag ? "Ver partículas: ON [ON]" : "Ver partículas: OFF [OFF]";
            case BURN_HOSTILES -> flag ? "Repeler hostiles: ON [ON]" : "Repeler hostiles: OFF [OFF]";
            case ANIMAL_KILLING -> flag ? "Animales: PROTEGIDOS [ON]" : "Animales: se matan [OFF]";
            case CHEST_ACCESS -> flag ? "Cofres: BLOQUEADOS [ON]" : "Cofres: acceso libre [OFF]";
            case CROP_HARVEST -> flag ? "Cosecha: PROTEGIDA [ON]" : "Cosecha: libre [OFF]";
            case ANVIL_USE -> flag ? "Yunques: BLOQUEADOS [ON]" : "Yunques: uso libre [OFF]";
            case ENDER_PEARL -> flag ? "Ender pearl: BLOQUEADA [ON]" : "Ender pearl: permitida [OFF]";
            case SIGN_EDITING -> flag ? "Letreros: BLOQUEADOS [ON]" : "Letreros: editables [OFF]";
            case DOORS_ACCESS -> flag ? "Puertas/Botones: BLOQ [ON]" : "Puertas/Botones: libres [OFF]";
            case ALL_MOB_SPAWN -> flag ? "Spawn de mobs: BLOQUEADO [ON]" : "Spawn de mobs: permitido [OFF]";
            case PASSIVE_MOB_SPAWN -> flag ? "Animales: NO spawnean [ON]" : "Animales: spawnean [OFF]";
            case BLOCK_ALL_INTERACT -> flag ? "Interacción total: BLOQ [ON]" : "Interacción total: libre [OFF]";
        };
    }

    private static String[] flagLore(ClaimFlags.FlagId claimflags$flagid) {
        String s = switch (claimflags$flagid) {
            case EFFECT_REGEN -> "Regenera vida a dueño y miembros";
            case EFFECT_RESIST -> "Reduce daño a dueño y miembros";
            case EFFECT_SPEED -> "Da velocidad a dueño y miembros";
            case ALLOW_FLIGHT -> "Dueño y miembros pueden volar";
            case BUILDING -> "Intrusos no pueden colocar bloques";
            case BREAKING -> "Intrusos no pueden romper nada";
            case EXPLOSIONS -> "TNT y creepers no destruyen";
            case FIRE -> "El fuego no se propaga aquí";
            case MOB_SPAWN -> "Zombies, skeletons no spawnean";
            case PVP -> "Jugadores no pueden atacarse";
            case MOB_DAMAGE -> "Los mobs no dañan a jugadores";
            case ALERTS -> "Avisa al dueño cuando entran";
            case ITEM_USE -> "Intrusos no pueden usar items";
            case ENTITY_INTERACT -> "Intrusos no usan mobs/aldeanos";
            case TRAMPLING -> "Intrusos no destruyen la tierra";
            case FLUIDS -> "Nadie coloca agua ni lava aquí";
            case PVP_ALL -> "Todos se pueden atacar aquí";
            case TREE_CHOPPING -> "Intrusos no pueden talar árboles";
            case PUBLIC_MODE -> "Todos entran pero no modifican";
            case SHOW_WELCOME -> "Mensaje personalizado al entrar";
            case SHOW_LEAVE -> "Mensaje personalizado al salir";
            case SHOW_BORDER -> "Dibuja el contorno de tu protección (líneas)";
            case SHOW_PARTICLES -> "Llena tu protección con partículas";
            case BURN_HOSTILES -> "Quema a los mobs hostiles que entren (día o noche)";
            case ANIMAL_KILLING -> "Intrusos no pueden matar animales";
            case CHEST_ACCESS -> "Intrusos no abren cofres ni barriles";
            case CROP_HARVEST -> "Intrusos no cosechan cultivos";
            case ANVIL_USE -> "Intrusos no pueden usar yunques";
            case ENDER_PEARL -> "Intrusos no se teletransportan";
            case SIGN_EDITING -> "Intrusos no editan letreros";
            case DOORS_ACCESS -> "Intrusos no usan puertas, botones ni placas";
            case ALL_MOB_SPAWN -> "Nada spawnea aquí: hostiles, animales y mobs de otros mods";
            case PASSIVE_MOB_SPAWN -> "Animales, peces y murciélagos dejan de spawnear (aldeanos no)";
            case BLOCK_ALL_INTERACT -> "Intrusos no pueden interactuar con NADA en la zona";
            default -> "";
        };
        String s1 = claimflags$flagid != ClaimFlags.FlagId.SHOW_WELCOME && claimflags$flagid != ClaimFlags.FlagId.SHOW_LEAVE
            ? (claimflags$flagid == ClaimFlags.FlagId.SHOW_PARTICLES ? "Clic para elegir partícula y densidad" : "Clic para cambiar")
            : "Clic izq: editar | Clic der: on/off";
        return new String[]{s, s1};
    }

    private static ItemStack withName(ItemStack itemstack, Component component) {
        itemstack.setHoverName(component);
        return itemstack;
    }

    private static ItemStack withLore(ItemStack itemstack, List<Component> list) {
        ClaimBlocks.setLore(itemstack, list);
        return itemstack;
    }

    private static String truncate(String s, int i) {
        if (s == null) {
            return "";
        } else {
            return s.length() <= i ? s : s.substring(0, Math.max(0, i - 3)) + "...";
        }
    }

    public void clicked(int i, int j, ClickType clicktype, Player player) {
        if (ClaimManager.getInstance().findClaimById(this.claim.getClaimId()) == null) {
            this.viewer.displayClientMessage(Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
            this.viewer.closeContainer();
        } else {
            if (i >= 0 && i < 54) {
                if (i == 45 && this.page > 0) {
                    open(this.viewer, this.claim, this.page - 1);
                } else if (i == 53 && this.page < LAST_PAGE) {
                    open(this.viewer, this.claim, this.page + 1);
                } else if (i == 46) {
                    if (!this.awaitingDeleteConfirm) {
                        this.awaitingDeleteConfirm = true;
                        this.rebuild();
                        this.viewer.displayClientMessage(Component.literal("[!] Haz clic de nuevo para confirmar.").withStyle(ChatFormatting.YELLOW), true);
                    } else {
                        this.performDelete();
                    }
                } else if (i == 47 && this.awaitingDeleteConfirm) {
                    this.awaitingDeleteConfirm = false;
                    this.rebuild();
                    this.viewer.displayClientMessage(Component.literal("[i] Eliminación cancelada.").withStyle(ChatFormatting.AQUA), true);
                } else {
                    if (this.awaitingDeleteConfirm) {
                        this.awaitingDeleteConfirm = false;
                    }

                    ClaimFlags.FlagId claimflags$flagid;
                    if ((claimflags$flagid = this.slotToFlag(i)) != null) {
                        int k = requiredPaidLevel(claimflags$flagid);
                        if (k > 0 && paidLevelOf(this.claim.getTier()) < k) {
                            this.viewer
                                .displayClientMessage(Component.literal("[x] Requiere zona " + requiredTierLabel(k) + " o superior.").withStyle(ChatFormatting.RED), true);
                            return;
                        }

                        if (claimflags$flagid == ClaimFlags.FlagId.SHOW_WELCOME) {
                            if (j == 1) {
                                this.claim.getFlags().showWelcome = !this.claim.getFlags().showWelcome;
                                ClaimManager.getInstance().save();
                                this.rebuild();
                            } else {
                                requestEditWelcome(this.viewer, this.claim, this.page);
                                this.viewer.closeContainer();
                            }
                        } else if (claimflags$flagid == ClaimFlags.FlagId.SHOW_LEAVE) {
                            if (j == 1) {
                                this.claim.getFlags().showLeave = !this.claim.getFlags().showLeave;
                                ClaimManager.getInstance().save();
                                this.rebuild();
                            } else {
                                requestEditLeave(this.viewer, this.claim, this.page);
                                this.viewer.closeContainer();
                            }
                        } else if (claimflags$flagid == ClaimFlags.FlagId.SHOW_BORDER) {
                            this.claim.getFlags().showBorder = !this.claim.getFlags().showBorder;
                            ClaimManager.getInstance().save();
                            this.rebuild();
                        } else if (claimflags$flagid == ClaimFlags.FlagId.SHOW_PARTICLES) {
                            ClaimParticleMenuHandler.open(this.viewer, this.claim, this.page);
                        } else {
                            this.claim.getFlags().toggle(claimflags$flagid);
                            ClaimManager.getInstance().save();
                            this.rebuild();
                        }
                    } else if (i == 38) {
                        this.viewer.displayClientMessage(Component.literal("[Protección] Miembros de la zona:").withStyle(ChatFormatting.GRAY), false);
                        if (this.claim.getMembers().isEmpty()) {
                            this.viewer.displayClientMessage(Component.literal("  (sin miembros)").withStyle(ChatFormatting.DARK_GRAY), false);
                        } else {
                            for (int l = 0; l < this.claim.getMembers().size(); l++) {
                                String s = l < this.claim.getMemberNames().size()
                                    ? this.claim.getMemberNames().get(l)
                                    : this.claim.getMembers().get(l).toString();
                                this.viewer.displayClientMessage(Component.literal("  - " + s).withStyle(ChatFormatting.WHITE), false);
                            }
                        }
                    } else if (i == 42) {
                        if (j == 1) {
                            requestAddMember(this.viewer, this.claim, this.page);
                            this.viewer.closeContainer();
                        } else {
                            MemberSelectMenu.open(this.viewer, this.claim, this.page, 0);
                        }
                    } else if (i == 40) {
                        if (this.claim.getMembers().isEmpty()) {
                            this.viewer.displayClientMessage(Component.literal("[i] Esta zona no tiene miembros que quitar.").withStyle(ChatFormatting.YELLOW), true);
                        } else {
                            requestRemoveMember(this.viewer, this.claim, this.page);
                            this.viewer.closeContainer();
                        }
                    } else if (i == 39) {
                        requestBanPlayer(this.viewer, this.claim, this.page);
                        this.viewer.closeContainer();
                    } else if (i == 41) {
                        if (this.claim.getBannedPlayers().isEmpty()) {
                            this.viewer.displayClientMessage(Component.literal("[i] No hay jugadores baneados.").withStyle(ChatFormatting.YELLOW), true);
                        } else {
                            requestUnbanPlayer(this.viewer, this.claim, this.page);
                            this.viewer.closeContainer();
                        }
                    } else if (i == 43) {
                        ClaimGroup claimgroup = ClaimManager.getInstance().getGroupOf(this.claim);
                        if (claimgroup == null) {
                            requestMergeName(this.viewer, this.claim, this.page);
                            this.viewer.closeContainer();
                        } else if (this.claim.isGroupMother()) {
                            requestMergeUsers(this.viewer, this.claim, this.page);
                            this.viewer.closeContainer();
                        }
                    } else if (i == 44) {
                        ClaimGroup claimgroup1 = ClaimManager.getInstance().getGroupOf(this.claim);
                        if (claimgroup1 != null && this.claim.isGroupMother()) {
                            ClaimManager.getInstance().dissolveGroupBreaking(claimgroup1.getGroupId());
                            this.viewer
                                .displayClientMessage(
                                    Component.literal("✔ Grupo disuelto. Las piedras solapadas se devolvieron a sus duenos.").withStyle(ChatFormatting.GREEN),
                                    false
                                );
                            this.rebuild();
                        }
                    } else if (i == 49) {
                        this.viewer.closeContainer();
                    } else if (i == 52) {
                        this.viewer.closeContainer();
                        this.viewer.server.getCommands().performPrefixedCommand(this.viewer.createCommandSourceStack(), "fsclaim list");
                    }
                }
            }
        }
    }

    private void performDelete() {
        ClaimTier claimtier = this.claim.getTier();
        Level level = this.viewer.level();
        BlockPos blockpos = this.claim.getCenter();
        if (claimtier != null && ClaimBlocks.isClaimConcreteForTier(level.getBlockState(blockpos).getBlock(), claimtier)) {
            level.destroyBlock(blockpos, false);
        }

        level.playSound(null, blockpos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 1.0F);
        ClaimManager.getInstance().removeClaim(level, blockpos);
        if (claimtier != null) {
            ItemStack itemstack = ClaimBlocks.createTierItem(claimtier, 1);
            if (!this.viewer.getInventory().add(itemstack)) {
                this.viewer.drop(itemstack, false);
            }
        }

        this.viewer.displayClientMessage(Component.literal("✔ Zona eliminada. Protección devuelta a tu inventario.").withStyle(ChatFormatting.GREEN), false);
        this.viewer.closeContainer();
    }

    private int pageIndex() {
        return Math.max(0, Math.min(LAST_PAGE, this.page));
    }

    private ClaimFlags.FlagId slotToFlag(int i) {
        ClaimFlags.FlagId[] aclaimflags$flagid = PAGES[this.pageIndex()];
        int[] aint = PAGE_SLOTS[this.pageIndex()];

        for (int j = 0; j < aint.length; j++) {
            if (aint[j] == i) {
                return aclaimflags$flagid[j];
            }
        }

        return null;
    }

    public static void open(ServerPlayer serverplayer, Claim claim, int i) {
        open(serverplayer, claim, i, null);
    }

    public static void open(ServerPlayer serverplayer, final Claim claim, int i, String s) {
        if (claim.getGroupId() != null && !claim.isGroupMother()) {
            Claim claim1 = claim.getMother();
            String s2 = claim1 != null ? claim1.getOwnerName() : "?";
            serverplayer.displayClientMessage(
                Component.literal(
                        "[!] Esta piedra pertenece al grupo de " + s2 + ". Solo la piedra nodriza gestiona el grupo. Puedes romperla para recuperarla."
                    )
                    .withStyle(ChatFormatting.YELLOW),
                false
            );
        } else {
            final int j = Math.max(0, Math.min(LAST_PAGE, i));
            ClaimGroup claimgroup = ClaimManager.getInstance().getGroupOf(claim);
            final String s1 = s != null
                ? truncate(s, 40)
                : (
                    claimgroup != null
                        ? truncate("Grupo: " + claimgroup.getName(), 40)
                        : truncate("Zona " + claim.sizeLabel() + " - " + claim.getOwnerName(), 40)
                );
            NetworkHooks.openScreen(serverplayer, new MenuProvider() {
                public Component getDisplayName() {
                    return Component.literal(s1).withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD});
                }

                public AbstractContainerMenu createMenu(int k, Inventory inventory, Player player) {
                    return new ClaimMenuHandler(k, inventory, claim, j);
                }
            });
        }
    }

    public static void requestAddMember(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.ADD_MEMBER, claim.getClaimId(), i));
        serverplayer.displayClientMessage(Component.literal("[Protección] Escribe el nombre del jugador a añadir (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false);
        serverplayer.displayClientMessage(
            Component.literal("    No hace falta que esté conectado. Alternativa: /fsclaim addmember <jugador>").withStyle(ChatFormatting.DARK_GRAY), false
        );
    }

    public static void requestRemoveMember(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.REMOVE_MEMBER, claim.getClaimId(), i));
        StringBuilder stringbuilder = new StringBuilder();
        List<String> list = claim.getMemberNames();

        for (int j = 0; j < list.size(); j++) {
            if (j > 0) {
                stringbuilder.append(", ");
            }

            stringbuilder.append((String)list.get(j));
        }

        serverplayer.displayClientMessage(
            Component.literal("[Protección] Miembros: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(stringbuilder.toString()).withStyle(ChatFormatting.WHITE)),
            false
        );
        serverplayer.displayClientMessage(
            Component.literal("[Protección] Escribe el nombre del invitado a quitar (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false
        );
    }

    public static void requestEditWelcome(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.EDIT_WELCOME, claim.getClaimId(), i));
        serverplayer.displayClientMessage(Component.literal("[Protección] Escribe tu bienvenida (max 60 chars) o 'cancelar':").withStyle(ChatFormatting.YELLOW), false);
    }

    public static void requestEditLeave(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.EDIT_LEAVE, claim.getClaimId(), i));
        serverplayer.displayClientMessage(
            Component.literal("[Protección] Escribe tu mensaje de salida (max 60 chars) o 'cancelar':").withStyle(ChatFormatting.YELLOW), false
        );
    }

    public static boolean hasPrompt(UUID uuid) {
        if (uuid == null) {
            return false;
        } else {
            ClaimMenuHandler.PendingChat claimmenuhandler$pendingchat = pending.get(uuid);
            if (claimmenuhandler$pendingchat == null) {
                return false;
            } else if (claimmenuhandler$pendingchat.isExpired()) {
                pending.remove(uuid, claimmenuhandler$pendingchat);
                return false;
            } else {
                return true;
            }
        }
    }

    public static ClaimMenuHandler.PendingChat popPrompt(UUID uuid) {
        if (uuid == null) {
            return null;
        } else {
            ClaimMenuHandler.PendingChat claimmenuhandler$pendingchat = pending.remove(uuid);
            return claimmenuhandler$pendingchat != null && !claimmenuhandler$pendingchat.isExpired() ? claimmenuhandler$pendingchat : null;
        }
    }

    public static void clearPrompt(UUID uuid) {
        if (uuid != null) {
            pending.remove(uuid);
            pendingMergeName.remove(uuid);
        }
    }

    public static void handleChat(ServerChatEvent serverchatevent) {
        ServerPlayer serverplayer = serverchatevent.getPlayer();
        if (serverplayer != null) {
            String s = serverchatevent.getRawText();
            if (ChatPromptRouter.consume(serverplayer, s)) {
                serverchatevent.setCanceled(true);
            } else {
                if (ChatPromptRouter.shouldSuppress(serverplayer.getUUID(), s)) {
                    serverchatevent.setCanceled(true);
                }
            }
        }
    }

    public static void dispatchPrompt(ServerPlayer serverplayer, ClaimMenuHandler.PendingChat claimmenuhandler$pendingchat, String s) {
        if (serverplayer != null && claimmenuhandler$pendingchat != null && !serverplayer.hasDisconnected()) {
            if (ChatPromptRouter.isCancel(s)) {
                serverplayer.displayClientMessage(Component.literal("[Protección] Cancelado.").withStyle(ChatFormatting.GRAY), false);
            } else {
                Claim claimx = findClaimById(claimmenuhandler$pendingchat.claimId());
                if (claimx == null) {
                    serverplayer.displayClientMessage(Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
                } else {
                    switch (claimmenuhandler$pendingchat.type()) {
                        case ADD_MEMBER:
                            handleAddMember(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        case REMOVE_MEMBER:
                            handleRemoveMember(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        case EDIT_WELCOME:
                            handleEditWelcome(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        case EDIT_LEAVE:
                            handleEditLeave(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        case BAN_PLAYER:
                            handleBanPlayer(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        case UNBAN_PLAYER:
                            handleUnbanPlayer(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        case MERGE_NAME:
                            handleMergeName(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                            break;
                        case MERGE_USERS:
                            handleMergeUsers(serverplayer, claimx, s, claimmenuhandler$pendingchat.returnPage());
                    }
                }
            }
        }
    }

    public static void dispatchAdminTransfer(ServerPlayer serverplayer, UUID uuid, String s) {
        if (serverplayer != null && !serverplayer.hasDisconnected()) {
            if (ChatPromptRouter.isCancel(s)) {
                serverplayer.displayClientMessage(Component.literal("[Protección] Cancelado.").withStyle(ChatFormatting.GRAY), false);
            } else {
                handleAdminTransfer(serverplayer, uuid, ChatPromptRouter.extractPlayerName(s));
            }
        }
    }

    private static void handleAdminTransfer(ServerPlayer serverplayer, UUID uuid, String s) {
        PlayerLookup.resolveAsync(serverplayer.getServer(), s, playerlookup$resolved -> {
            if (!serverplayer.hasDisconnected()) {
                applyAdminTransfer(serverplayer, uuid, s, playerlookup$resolved);
            }
        });
    }

    private static void applyAdminTransfer(ServerPlayer serverplayer, UUID uuid, String s, PlayerLookup.Resolved playerlookup$resolved) {
        Claim claimx = findClaimById(uuid);
        if (claimx == null) {
            serverplayer.displayClientMessage(Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
        } else if (playerlookup$resolved == null) {
            serverplayer.displayClientMessage(Component.literal("[x] Jugador no encontrado: " + s).withStyle(ChatFormatting.RED), false);
        } else {
            claimx.setOwner(playerlookup$resolved.id(), playerlookup$resolved.name());
            claimx.getMembers().clear();
            claimx.getMemberNames().clear();
            ClaimManager.getInstance().save();
            serverplayer.displayClientMessage(Component.literal("✔ Zona transferida a " + playerlookup$resolved.name() + ".").withStyle(ChatFormatting.GREEN), false);
            MutableComponent mutablecomponent = Component.literal("[!] Un administrador te transfirió una zona ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(claimx.sizeLabel()).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}))
                .append(Component.literal(" en X:" + claimx.getX() + " Z:" + claimx.getZ()).withStyle(ChatFormatting.YELLOW));
            if (playerlookup$resolved.isOnline()) {
                playerlookup$resolved.online().displayClientMessage(mutablecomponent, false);
            } else {
                ClaimManager.getInstance().queueMessage(playerlookup$resolved.id(), mutablecomponent);
            }
        }
    }

    private static void handleAddMember(ServerPlayer serverplayer, Claim claim, String s, int i) {
        addMemberByName(serverplayer, claim, s, i, true);
    }

    public static boolean addMemberByName(ServerPlayer serverplayer, Claim claim, String s, int i, boolean flag) {
        String s1 = ChatPromptRouter.extractPlayerName(s);
        UUID uuid = claim.getClaimId();
        PlayerLookup.resolveAsync(
            serverplayer.getServer(),
            s1,
            playerlookup$resolved -> {
                if (!serverplayer.hasDisconnected()) {
                    Claim claim1 = findClaimById(uuid);
                    if (claim1 == null) {
                        serverplayer.displayClientMessage(Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
                    } else {
                        if (playerlookup$resolved == null) {
                            serverplayer.displayClientMessage(
                                Component.literal(
                                        "[x] No encuentro al jugador \"" + s1 + "\". Revisa el nombre; si nunca ha entrado al servidor, no puedo resolverlo."
                                    )
                                    .withStyle(ChatFormatting.RED),
                                false
                            );
                        } else {
                            addMemberResolved(serverplayer, claim1, playerlookup$resolved);
                        }

                        if (flag) {
                            open(serverplayer, claim1, i);
                        }
                    }
                }
            }
        );
        return true;
    }

    public static boolean addMemberResolved(ServerPlayer serverplayer, Claim claim, PlayerLookup.Resolved playerlookup$resolved) {
        if (claim.isOwner(playerlookup$resolved.id())) {
            serverplayer.displayClientMessage(Component.literal("[x] Ese jugador ya es el dueño.").withStyle(ChatFormatting.RED), false);
            return false;
        } else if (claim.isMember(playerlookup$resolved.id())) {
            serverplayer.displayClientMessage(
                Component.literal("[i] " + playerlookup$resolved.name() + " ya es miembro de esta zona.").withStyle(ChatFormatting.YELLOW), false
            );
            return false;
        } else {
            int i = ClaimConfig.get().maxMembersPerClaim;
            if (i > 0 && claim.getMembers().size() >= i) {
                serverplayer.displayClientMessage(Component.literal("[x] Esta zona ya tiene el maximo de miembros (" + i + ").").withStyle(ChatFormatting.RED), false);
                return false;
            } else {
                if (claim.isBanned(playerlookup$resolved.id())) {
                    claim.unbanPlayer(playerlookup$resolved.id());
                    serverplayer.displayClientMessage(
                        Component.literal("[i] " + playerlookup$resolved.name() + " estaba baneado de la zona; se le quitó el baneo.")
                            .withStyle(ChatFormatting.YELLOW),
                        false
                    );
                }

                claim.addMember(playerlookup$resolved.id(), playerlookup$resolved.name());
                ClaimManager.getInstance().save();
                serverplayer.displayClientMessage(
                    Component.literal("✔ " + playerlookup$resolved.name() + " agregado como miembro de la zona.").withStyle(ChatFormatting.GREEN), false
                );
                MutableComponent mutablecomponent = Component.literal("[Protección] Eres miembro de la zona de " + serverplayer.getName().getString())
                    .withStyle(ChatFormatting.AQUA);
                if (playerlookup$resolved.isOnline()) {
                    playerlookup$resolved.online().displayClientMessage(mutablecomponent, false);
                } else {
                    ClaimManager.getInstance().queueMessage(playerlookup$resolved.id(), mutablecomponent);
                }

                return true;
            }
        }
    }

    private static void handleRemoveMember(ServerPlayer serverplayer, Claim claim, String s, int i) {
        removeMemberByName(serverplayer, claim, s, i, true);
    }

    public static boolean removeMemberByName(ServerPlayer serverplayer, Claim claim, String s, int i, boolean flag) {
        String s1 = ChatPromptRouter.extractPlayerName(s);
        UUID uuid = null;
        String s2 = s1;

        for (int j = 0; j < claim.getMemberNames().size() && j < claim.getMembers().size(); j++) {
            if (claim.getMemberNames().get(j).equalsIgnoreCase(s1)) {
                uuid = claim.getMembers().get(j);
                s2 = claim.getMemberNames().get(j);
                break;
            }
        }

        PlayerLookup.Resolved playerlookup$resolved;
        if (uuid == null && (playerlookup$resolved = PlayerLookup.resolve(serverplayer.getServer(), s1)) != null && claim.isMember(playerlookup$resolved.id())) {
            uuid = playerlookup$resolved.id();
            s2 = playerlookup$resolved.name();
        }

        if (uuid == null) {
            serverplayer.displayClientMessage(Component.literal("[x] " + s1 + " no es miembro de esta zona.").withStyle(ChatFormatting.RED), false);
            if (flag) {
                open(serverplayer, claim, i);
            }

            return false;
        } else {
            claim.removeMember(uuid);
            ClaimManager.getInstance().save();
            serverplayer.displayClientMessage(Component.literal("✔ " + s2 + " fue eliminado de la zona.").withStyle(ChatFormatting.GREEN), false);
            MutableComponent mutablecomponent = Component.literal("[Protección] Ya no eres miembro de la zona de " + serverplayer.getName().getString())
                .withStyle(ChatFormatting.YELLOW);
            ServerPlayer serverplayer1 = serverplayer.getServer() == null ? null : serverplayer.getServer().getPlayerList().getPlayer(uuid);
            if (serverplayer1 != null) {
                serverplayer1.displayClientMessage(mutablecomponent, false);
            } else {
                ClaimManager.getInstance().queueMessage(uuid, mutablecomponent);
            }

            if (flag) {
                open(serverplayer, claim, i);
            }

            return true;
        }
    }

    private static void handleEditWelcome(ServerPlayer serverplayer, Claim claim, String s, int i) {
        int j = ClaimConfig.get().maxWelcomeLength;
        if (s.length() > j) {
            s = s.substring(0, j);
        }

        claim.getFlags().welcomeMessage = s;
        claim.getFlags().showWelcome = !s.isBlank();
        ClaimManager.getInstance().save();
        serverplayer.displayClientMessage(Component.literal("✔ Bienvenida guardada.").withStyle(ChatFormatting.GREEN), false);
        open(serverplayer, claim, i);
    }

    private static void handleEditLeave(ServerPlayer serverplayer, Claim claim, String s, int i) {
        int j = ClaimConfig.get().maxWelcomeLength;
        if (s.length() > j) {
            s = s.substring(0, j);
        }

        claim.getFlags().leaveMessage = s;
        claim.getFlags().showLeave = !s.isBlank();
        ClaimManager.getInstance().save();
        serverplayer.displayClientMessage(Component.literal("✔ Mensaje de salida guardado.").withStyle(ChatFormatting.GREEN), false);
        open(serverplayer, claim, i);
    }

    public static void requestMergeName(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.MERGE_NAME, claim.getClaimId(), i));
        serverplayer.displayClientMessage(Component.literal("[Grupo] Escribe el NOMBRE de la zona unida (o 'cancelar'):").withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    public static void requestMergeUsers(ServerPlayer serverplayer, Claim claim, int i) {
        pending.put(serverplayer.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.MERGE_USERS, claim.getClaimId(), i));
        serverplayer.displayClientMessage(
            Component.literal("[Grupo] Escribe el/los jugadores a invitar (separados por espacio) o 'cancelar':").withStyle(ChatFormatting.LIGHT_PURPLE),
            false
        );
    }

    private static void handleMergeName(ServerPlayer serverplayer, Claim claim, String s, int i) {
        String s1 = s.length() > 32 ? s.substring(0, 32) : s;
        pendingMergeName.put(serverplayer.getUUID(), s1);
        pending.put(serverplayer.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.MERGE_USERS, claim.getClaimId(), i));
        serverplayer.displayClientMessage(
            Component.literal("[Grupo] Nombre: \"" + s1 + "\". Ahora escribe el/los jugadores a invitar (separados por espacio):")
                .withStyle(ChatFormatting.LIGHT_PURPLE),
            false
        );
    }

    private static void handleMergeUsers(ServerPlayer serverplayer, Claim claim, String s, int i) {
        ClaimManager claimmanager = ClaimManager.getInstance();
        ClaimGroup claimgroup = claimmanager.getGroupOf(claim);
        if (claimgroup == null) {
            String s1 = pendingMergeName.getOrDefault(serverplayer.getUUID(), "Grupo");
            claimgroup = claimmanager.createGroup(claim, s1);
        }

        pendingMergeName.remove(serverplayer.getUUID());
        String[] astring = ChatPromptRouter.sanitize(s).split("[ ,]+");
        int j = 0;

        for (String s2 : astring) {
            String s3 = s2.trim();
            if (!s3.isEmpty()) {
                ServerPlayer serverplayer1 = serverplayer.server.getPlayerList().getPlayerByName(s3);
                if (serverplayer1 == null) {
                    serverplayer.displayClientMessage(
                        Component.literal("[x] " + s3 + " no esta en linea (debe estar conectado para invitarlo).").withStyle(ChatFormatting.RED), false
                    );
                } else if (!serverplayer1.getUUID().equals(serverplayer.getUUID())) {
                    if (claimgroup.isRegistered(serverplayer1.getUUID())) {
                        serverplayer.displayClientMessage(
                            Component.literal("[i] " + serverplayer1.getName().getString() + " ya esta en el grupo.").withStyle(ChatFormatting.GRAY), false
                        );
                    } else {
                        String s4 = genCode();
                        invites.put(
                            s4,
                            new ClaimMenuHandler.MergeInvite(
                                s4, claimgroup.getGroupId(), serverplayer1.getUUID(), serverplayer.getName().getString(), claimgroup.getName()
                            )
                        );
                        sendInvite(serverplayer1, serverplayer.getName().getString(), claimgroup.getName(), s4);
                        j++;
                    }
                }
            }
        }

        if (j > 0) {
            serverplayer.displayClientMessage(
                Component.literal("✔ Invitacion enviada a " + j + " jugador(es). Grupo: \"" + claimgroup.getName() + "\".").withStyle(ChatFormatting.GREEN),
                false
            );
        }

        open(serverplayer, claim, i);
    }

    private static void sendInvite(ServerPlayer serverplayer, String s, String s1, String s2) {
        serverplayer.displayClientMessage(
            Component.literal("[Grupo] " + s + " te invita a unir tu proteccion al grupo \"" + s1 + "\".").withStyle(ChatFormatting.AQUA), false
        );
        MutableComponent mutablecomponent = Component.literal(" [✔ ACEPTAR] ")
            .withStyle(
                Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true).withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/fsclaimmerge accept " + s2))
            );
        MutableComponent mutablecomponent1 = Component.literal("[✘ RECHAZAR]")
            .withStyle(
                Style.EMPTY.withColor(ChatFormatting.RED).withBold(true).withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/fsclaimmerge reject " + s2))
            );
        serverplayer.displayClientMessage(Component.literal("").append(mutablecomponent).append(mutablecomponent1), false);
    }

    public static void acceptMerge(ServerPlayer serverplayer, String s) {
        ClaimMenuHandler.MergeInvite claimmenuhandler$mergeinvite = invites.remove(s);
        if (claimmenuhandler$mergeinvite != null && serverplayer.getUUID().equals(claimmenuhandler$mergeinvite.targetId())) {
            ClaimManager claimmanager = ClaimManager.getInstance();
            ClaimGroup claimgroup = claimmanager.getGroup(claimmenuhandler$mergeinvite.groupId());
            if (claimgroup == null) {
                serverplayer.displayClientMessage(Component.literal("[x] El grupo ya no existe.").withStyle(ChatFormatting.RED), false);
            } else {
                claimmanager.registerPlayer(claimgroup.getGroupId(), serverplayer.getUUID());
                serverplayer.displayClientMessage(
                    Component.literal("✔ Te uniste al grupo \"" + claimgroup.getName() + "\". Ahora tus piedras colocadas dentro de esa zona se uniran.")
                        .withStyle(ChatFormatting.GREEN),
                    false
                );
                MutableComponent mutablecomponent = Component.literal(
                        serverplayer.getName().getString() + " acepto unirse al grupo \"" + claimgroup.getName() + "\"."
                    )
                    .withStyle(ChatFormatting.GREEN);
                ServerPlayer serverplayer1 = claimgroup.getMotherOwnerId() == null
                    ? null
                    : serverplayer.server.getPlayerList().getPlayer(claimgroup.getMotherOwnerId());
                if (serverplayer1 != null) {
                    serverplayer1.displayClientMessage(mutablecomponent, false);
                } else if (claimgroup.getMotherOwnerId() != null) {
                    claimmanager.queueMessage(claimgroup.getMotherOwnerId(), mutablecomponent);
                }
            }
        } else {
            serverplayer.displayClientMessage(Component.literal("[x] Invitacion no valida o expirada.").withStyle(ChatFormatting.RED), false);
        }
    }

    public static void rejectMerge(ServerPlayer serverplayer, String s) {
        ClaimMenuHandler.MergeInvite claimmenuhandler$mergeinvite = invites.remove(s);
        if (claimmenuhandler$mergeinvite != null && serverplayer.getUUID().equals(claimmenuhandler$mergeinvite.targetId())) {
            serverplayer.displayClientMessage(Component.literal("[i] Rechazaste la invitacion de union.").withStyle(ChatFormatting.GRAY), false);
            ClaimGroup claimgroup = ClaimManager.getInstance().getGroup(claimmenuhandler$mergeinvite.groupId());
            if (claimgroup != null && claimgroup.getMotherOwnerId() != null) {
                MutableComponent mutablecomponent = Component.literal(
                        serverplayer.getName().getString() + " rechazo unirse al grupo \"" + claimgroup.getName() + "\"."
                    )
                    .withStyle(ChatFormatting.YELLOW);
                ServerPlayer serverplayer1 = serverplayer.server.getPlayerList().getPlayer(claimgroup.getMotherOwnerId());
                if (serverplayer1 != null) {
                    serverplayer1.displayClientMessage(mutablecomponent, false);
                } else {
                    ClaimManager.getInstance().queueMessage(claimgroup.getMotherOwnerId(), mutablecomponent);
                }
            }
        } else {
            serverplayer.displayClientMessage(Component.literal("[x] Invitacion no valida o expirada.").withStyle(ChatFormatting.RED), false);
        }
    }

    public static void leaveMerge(ServerPlayer serverplayer) {
        ClaimManager claimmanager = ClaimManager.getInstance();
        ClaimGroup claimgroup = claimmanager.getGroupByRegistered(serverplayer.getUUID());
        if (claimgroup == null) {
            serverplayer.displayClientMessage(Component.literal("[!] No estas en ningun grupo.").withStyle(ChatFormatting.YELLOW), false);
        } else {
            boolean flag = serverplayer.getUUID().equals(claimgroup.getMotherOwnerId());
            String s = claimgroup.getName();
            claimmanager.leaveGroupBreaking(claimgroup.getGroupId(), serverplayer.getUUID());
            serverplayer.displayClientMessage(
                Component.literal(
                        flag ? "✔ Disolviste el grupo \"" + s + "\"." : "✔ Saliste del grupo \"" + s + "\". Tus piedras vuelven a ser independientes."
                    )
                    .withStyle(ChatFormatting.GREEN),
                false
            );
        }
    }

    private static String genCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Claim findClaimById(UUID uuid) {
        for (Claim claimx : ClaimManager.getInstance().getAllClaims()) {
            if (claimx.getClaimId().equals(uuid)) {
                return claimx;
            }
        }

        return null;
    }

    public static record MergeInvite(String code, UUID groupId, UUID targetId, String inviterName, String groupName) {
    }

    public static record PendingChat(ClaimMenuHandler.PendingType type, UUID claimId, int returnPage, long createdAtMillis) {
        public PendingChat(ClaimMenuHandler.PendingType claimmenuhandler$pendingtype, UUID uuid, int i) {
            this(claimmenuhandler$pendingtype, uuid, i, System.currentTimeMillis());
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - this.createdAtMillis > ClaimConfig.get().chatPromptMillis();
        }
    }

    public static enum PendingType {
        ADD_MEMBER,
        EDIT_WELCOME,
        EDIT_LEAVE,
        BAN_PLAYER,
        UNBAN_PLAYER,
        REMOVE_MEMBER,
        MERGE_NAME,
        MERGE_USERS;
    }
}
