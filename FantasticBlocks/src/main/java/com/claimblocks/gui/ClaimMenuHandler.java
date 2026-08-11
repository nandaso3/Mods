package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.chat.ChatPromptRouter;
import com.claimblocks.data.Claim;
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
      ClaimFlags.FlagId.ALL_MOB_SPAWN,
      ClaimFlags.FlagId.PASSIVE_MOB_SPAWN,
      ClaimFlags.FlagId.BLOCK_ALL_INTERACT
   };
   /** Paginas de flags del menu, en orden. Anadir una pagina nueva es anadir una entrada aqui. */
   private static final ClaimFlags.FlagId[][] PAGES = new ClaimFlags.FlagId[][]{PAGE_0, PAGE_1, PAGE_2};
   private static final int[][] PAGE_SLOTS = new int[][]{FLAG_SLOTS_P0, FLAG_SLOTS_P1, FLAG_SLOTS_P2};
   private static final int LAST_PAGE = PAGES.length - 1;
   /** Tiempo que un prompt de chat espera respuesta antes de descartarse solo. */
   private static final long PROMPT_TTL_MS = 90_000L;
   private static final Map<UUID, ClaimMenuHandler.PendingChat> pending = new ConcurrentHashMap<>();
   private static final Map<UUID, String> pendingMergeName = new ConcurrentHashMap<>();
   private static final Map<String, ClaimMenuHandler.MergeInvite> invites = new ConcurrentHashMap<>();
   private final SimpleContainer chest;
   private final Claim claim;
   private final ServerPlayer viewer;
   private final int page;
   private boolean awaitingDeleteConfirm = false;

   public ClaimMenuHandler(int syncId, Inventory pInv, Claim claim, int page) {
      this(syncId, pInv, new SimpleContainer(54), claim, page);
   }

   private ClaimMenuHandler(int syncId, Inventory pInv, SimpleContainer chest, Claim claim, int page) {
      super(MenuType.GENERIC_9x6, syncId, pInv, chest, 6);
      this.chest = chest;
      this.claim = claim;
      this.viewer = (ServerPlayer)pInv.player;
      this.page = page;
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

   public ItemStack quickMoveStack(Player player, int index) {
      return ItemStack.EMPTY;
   }

   private void rebuild() {
      ItemStack bg = withName(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Component.literal(" "));

      for (int i = 0; i < 54; i++) {
         this.chest.setItem(i, bg.copy());
      }

      ClaimGroup hdrGrp = ClaimManager.getInstance().getGroupOf(this.claim);
      String header = hdrGrp != null ? "Grupo: " + hdrGrp.getName() : "Zona " + this.claim.sizeLabel() + " - " + this.claim.getOwnerName();
      this.chest
         .setItem(
            4,
            withName(
               new ItemStack(Items.PAPER),
               Component.literal(truncate(header, 30)).withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})
            )
         );
      this.chest
         .setItem(
            11,
            withLore(
               withName(new ItemStack(Items.COMPASS), Component.literal("Coordenadas").withStyle(ChatFormatting.AQUA)),
               List.of(Component.literal("X=" + this.claim.getX() + " Y=" + this.claim.getY() + " Z=" + this.claim.getZ()).withStyle(ChatFormatting.WHITE))
            )
         );
      this.chest
         .setItem(
            13,
            withLore(
               withName(new ItemStack(Items.PLAYER_HEAD), Component.literal("Dueño").withStyle(ChatFormatting.AQUA)),
               List.of(Component.literal(truncate(this.claim.getOwnerName(), 35)).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}))
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
      ClaimFlags f = this.claim.getFlags();
      ClaimFlags.FlagId[] ids = PAGES[this.pageIndex()];
      int[] slots = PAGE_SLOTS[this.pageIndex()];
      int tierLevel = paidLevelOf(this.claim.getTier());

      for (int i = 0; i < ids.length; i++) {
         ClaimFlags.FlagId id = ids[i];
         int reqLevel = requiredPaidLevel(id);
         if (reqLevel > 0 && tierLevel < reqLevel) {
            this.chest.setItem(slots[i], this.lockedEffectButton(id, reqLevel));
         } else {
            this.chest.setItem(slots[i], this.flagButton(id, f.get(id)));
         }
      }

      this.chest
         .setItem(
            38,
            withLore(
               withName(
                  new ItemStack(Items.WRITABLE_BOOK), Component.literal("Miembros (" + this.claim.getMembers().size() + ")").withStyle(ChatFormatting.YELLOW)
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
                  Component.literal("También sirve /claim addmember <jugador>").withStyle(ChatFormatting.DARK_GRAY)
               )
            )
         );
      this.chest
         .setItem(
            39,
            withLore(
               withName(
                  new ItemStack(Items.IRON_BARS), Component.literal("Banear jugador").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
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
                     new ItemStack(Items.LIME_DYE), Component.literal("Cancelar").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})
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

      ClaimGroup grp = ClaimManager.getInstance().getGroupOf(this.claim);
      if (grp == null) {
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
                     Component.literal(truncate("Grupo: " + grp.getName(), 30))
                        .withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD})
                  ),
                  List.of(
                     Component.literal("Miembros registrados: " + grp.getRegisteredPlayers().size()).withStyle(ChatFormatting.GRAY),
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
      ArrayList<Component> lore = new ArrayList<>();
      if (this.claim.getMembers().isEmpty()) {
         lore.add(Component.literal("(sin miembros)").withStyle(ChatFormatting.DARK_GRAY));
         return lore;
      } else {
         int max = Math.min(5, this.claim.getMembers().size());

         for (int i = 0; i < max; i++) {
            String n = i < this.claim.getMemberNames().size() ? this.claim.getMemberNames().get(i) : this.claim.getMembers().get(i).toString();
            lore.add(Component.literal(truncate(" - " + n, 35)).withStyle(ChatFormatting.WHITE));
         }

         if (this.claim.getMembers().size() > max) {
            lore.add(Component.literal(" - ... y " + (this.claim.getMembers().size() - max) + " más").withStyle(ChatFormatting.GRAY));
         }

         return lore;
      }
   }

   private List<Component> buildBanLore() {
      ArrayList<Component> lore = new ArrayList<>();
      lore.add(Component.literal("Escribe el nombre por chat para banear.").withStyle(ChatFormatting.GRAY));
      lore.add(Component.literal("Al entrar, la barrera los empuja y daña.").withStyle(ChatFormatting.DARK_GRAY));
      Set<UUID> banned = this.claim.getBannedPlayers();
      lore.add(Component.literal("Baneados: " + banned.size()).withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}));
      int i = 0;

      for (UUID id : banned) {
         if (i++ >= 8) {
            lore.add(Component.literal(" - ...").withStyle(ChatFormatting.GRAY));
            break;
         }

         lore.add(Component.literal(truncate(" - " + PlayerLookup.nameOf(this.viewer.getServer(), id), 35)).withStyle(ChatFormatting.WHITE));
      }

      return lore;
   }

   public static void requestBanPlayer(ServerPlayer player, Claim claim, int returnPage) {
      pending.put(player.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.BAN_PLAYER, claim.getClaimId(), returnPage));
      player.displayClientMessage(Component.literal("[Protección] Escribe el nombre del jugador a BANEAR (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false);
   }

   public static void requestUnbanPlayer(ServerPlayer player, Claim claim, int returnPage) {
      pending.put(player.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.UNBAN_PLAYER, claim.getClaimId(), returnPage));
      player.displayClientMessage(Component.literal("[Protección] Escribe el nombre del jugador a DESBANEAR (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false);
   }

   private static void handleBanPlayer(ServerPlayer sender, Claim claim, String name, int page) {
      String query = ChatPromptRouter.extractPlayerName(name);
      PlayerLookup.Resolved target = PlayerLookup.resolve(sender.getServer(), query);

      if (target == null) {
         sender.displayClientMessage(Component.literal("[x] Jugador no encontrado: " + query).withStyle(ChatFormatting.RED), false);
         open(sender, claim, page);
         return;
      }

      if (claim.isOwner(target.id())) {
         sender.displayClientMessage(Component.literal("[x] No puedes banear al dueño.").withStyle(ChatFormatting.RED), false);
         open(sender, claim, page);
         return;
      }

      claim.banPlayer(target.id());
      claim.removeMember(target.id());
      ClaimManager.getInstance().save();
      sender.displayClientMessage(Component.literal("\u2714 " + target.name() + " baneado de la zona.").withStyle(ChatFormatting.GREEN), false);

      Component notice = Component.literal("[!] Has sido baneado de una zona de " + sender.getName().getString())
         .withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD});
      if (target.isOnline()) {
         target.online().displayClientMessage(notice, false);
      } else {
         ClaimManager.getInstance().queueMessage(target.id(), notice);
      }

      open(sender, claim, page);
   }

   private static void handleUnbanPlayer(ServerPlayer sender, Claim claim, String name, int page) {
      String query = ChatPromptRouter.extractPlayerName(name);
      PlayerLookup.Resolved target = PlayerLookup.resolve(sender.getServer(), query);

      if (target != null && claim.isBanned(target.id())) {
         claim.unbanPlayer(target.id());
         ClaimManager.getInstance().save();
         sender.displayClientMessage(Component.literal("\u2714 " + target.name() + " desbaneado.").withStyle(ChatFormatting.GREEN), false);
      } else {
         sender.displayClientMessage(Component.literal("[x] Ese jugador no está baneado.").withStyle(ChatFormatting.RED), false);
      }

      open(sender, claim, page);
   }

   private static int paidLevelOf(ClaimTier t) {
      if (t == null) {
         return 0;
      } else {
         String var1 = t.id;
         String var2 = t.id;

         return switch (var2) {
            case "claimstone_250x250" -> 1;
            case "claimstone_300x300" -> 2;
            case "claimstone_500x500" -> 3;
            default -> 0;
         };
      }
   }

   private static int requiredPaidLevel(ClaimFlags.FlagId id) {
      return switch (id) {
         case EFFECT_REGEN -> 1;
         case EFFECT_RESIST -> 2;
         case EFFECT_SPEED -> 2;
         case ALLOW_FLIGHT -> 3;
         default -> 0;
      };
   }

   private static String requiredTierLabel(int reqLevel) {
      return switch (reqLevel) {
         case 1 -> "250x250";
         case 2 -> "300x300";
         case 3 -> "500x500";
         default -> "?";
      };
   }

   private ItemStack lockedEffectButton(ClaimFlags.FlagId id, int reqLevel) {
      ItemStack stack = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
      return withLore(
         withName(stack, Component.literal(effectName(id) + " [LOCKED]").withStyle(ChatFormatting.DARK_GRAY)),
         List.of(
            Component.literal("Requiere zona " + requiredTierLabel(reqLevel) + " o superior").withStyle(ChatFormatting.GRAY),
            Component.literal(effectShortDesc(id)).withStyle(ChatFormatting.DARK_GRAY)
         )
      );
   }

   private static String effectShortDesc(ClaimFlags.FlagId id) {
      return switch (id) {
         case EFFECT_REGEN -> "Regenera vida a duenio y miembros";
         case EFFECT_RESIST -> "Reduce dano a duenio y miembros";
         case EFFECT_SPEED -> "Da velocidad a duenio y miembros";
         case ALLOW_FLIGHT -> "Solo el duenio puede volar en su zona";
         default -> "Perk pasivo";
      };
   }

   private static String effectName(ClaimFlags.FlagId id) {
      return switch (id) {
         case EFFECT_REGEN -> "Regeneración pasiva";
         case EFFECT_RESIST -> "Resistencia pasiva";
         case EFFECT_SPEED -> "Velocidad pasiva";
         case ALLOW_FLIGHT -> "Vuelo en zona";
         default -> "Perk pasivo";
      };
   }

   private ItemStack flagButton(ClaimFlags.FlagId id, boolean enabled) {
      ItemStack stack = new ItemStack(enabled ? Items.LIME_DYE : Items.GRAY_DYE);
      MutableComponent name = Component.literal(flagDisplayName(id, enabled))
         .withStyle(new ChatFormatting[]{enabled ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD});
      String[] lore = flagLore(id);
      return withLore(
         withName(stack, name),
         List.of(
            Component.literal(lore[0]).withStyle(ChatFormatting.GRAY),
            Component.literal("Estado: " + (enabled ? "ACTIVO" : "INACTIVO") + " - " + lore[1]).withStyle(ChatFormatting.GRAY)
         )
      );
   }

   private static String flagDisplayName(ClaimFlags.FlagId id, boolean on) {
      return switch (id) {
         case EFFECT_REGEN -> on ? "Regeneración pasiva [ON]" : "Regeneración pasiva [OFF]";
         case EFFECT_RESIST -> on ? "Resistencia pasiva [ON]" : "Resistencia pasiva [OFF]";
         case EFFECT_SPEED -> on ? "Velocidad pasiva [ON]" : "Velocidad pasiva [OFF]";
         case ALLOW_FLIGHT -> on ? "Vuelo en zona: ACTIVO [ON]" : "Vuelo en zona: inactivo [OFF]";
         case BUILDING -> on ? "Construir: BLOQUEADO [ON]" : "Construir: permitido [OFF]";
         case BREAKING -> on ? "Romper: BLOQUEADO [ON]" : "Romper: permitido [OFF]";
         case EXPLOSIONS -> on ? "Explosiones: BLOQUEADAS [ON]" : "Explosiones: permitidas [OFF]";
         case FIRE -> on ? "Fuego: BLOQUEADO [ON]" : "Fuego: permitido [OFF]";
         case MOB_SPAWN -> on ? "Mobs hostiles: BLOQUEADOS [ON]" : "Mobs hostiles: permit. [OFF]";
         case PVP -> on ? "PVP: BLOQUEADO [ON]" : "PVP: permitido [OFF]";
         case MOB_DAMAGE -> on ? "Daño de mobs: BLOQUEADO [ON]" : "Daño de mobs: permit. [OFF]";
         case ALERTS -> on ? "Alertas intrusos: ON [ON]" : "Alertas intrusos: OFF [OFF]";
         case ITEM_USE -> on ? "Usar items: BLOQUEADO [ON]" : "Usar items: permitido [OFF]";
         case ENTITY_INTERACT -> on ? "Entidades: BLOQUEADAS [ON]" : "Entidades: libres [OFF]";
         case TRAMPLING -> on ? "Cultivos: PROTEGIDOS [ON]" : "Cultivos: sin protec. [OFF]";
         case FLUIDS -> on ? "Fluidos: BLOQUEADOS [ON]" : "Fluidos: permitidos [OFF]";
         case PVP_ALL -> on ? "Zona PVP libre: ACTIVA [ON]" : "Zona PVP libre: inact. [OFF]";
         case TREE_CHOPPING -> on ? "Árboles: PROTEGIDOS [ON]" : "Árboles: se talan [OFF]";
         case PUBLIC_MODE -> on ? "Modo visita: ACTIVO [ON]" : "Modo visita: inactivo [OFF]";
         case SHOW_WELCOME -> on ? "Bienvenida custom: ON [ON]" : "Bienvenida custom: OFF [OFF]";
         case SHOW_LEAVE -> on ? "Mensaje de salida: ON [ON]" : "Mensaje de salida: OFF [OFF]";
         case SHOW_BORDER -> on ? "Ver contorno: ON [ON]" : "Ver contorno: OFF [OFF]";
         case SHOW_PARTICLES -> on ? "Ver partículas: ON [ON]" : "Ver partículas: OFF [OFF]";
         case BURN_HOSTILES -> on ? "Repeler hostiles: ON [ON]" : "Repeler hostiles: OFF [OFF]";
         case ANIMAL_KILLING -> on ? "Animales: PROTEGIDOS [ON]" : "Animales: se matan [OFF]";
         case CHEST_ACCESS -> on ? "Cofres: BLOQUEADOS [ON]" : "Cofres: acceso libre [OFF]";
         case CROP_HARVEST -> on ? "Cosecha: PROTEGIDA [ON]" : "Cosecha: libre [OFF]";
         case ANVIL_USE -> on ? "Yunques: BLOQUEADOS [ON]" : "Yunques: uso libre [OFF]";
         case ENDER_PEARL -> on ? "Ender pearl: BLOQUEADA [ON]" : "Ender pearl: permitida [OFF]";
         case SIGN_EDITING -> on ? "Letreros: BLOQUEADOS [ON]" : "Letreros: editables [OFF]";
         case DOORS_ACCESS -> on ? "Puertas/Botones: BLOQ [ON]" : "Puertas/Botones: libres [OFF]";
         case ALL_MOB_SPAWN -> on ? "Spawn de mobs: BLOQUEADO [ON]" : "Spawn de mobs: permitido [OFF]";
         case PASSIVE_MOB_SPAWN -> on ? "Animales: NO spawnean [ON]" : "Animales: spawnean [OFF]";
         case BLOCK_ALL_INTERACT -> on ? "Interacción total: BLOQ [ON]" : "Interacción total: libre [OFF]";
      };
   }

   private static String[] flagLore(ClaimFlags.FlagId id) {
      String desc = switch (id) {
         case EFFECT_REGEN -> "Regenera vida a dueño y miembros";
         case EFFECT_RESIST -> "Reduce daño a dueño y miembros";
         case EFFECT_SPEED -> "Da velocidad a dueño y miembros";
         case ALLOW_FLIGHT -> "Dueño puede volar";
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
      };
      String action = id != ClaimFlags.FlagId.SHOW_WELCOME && id != ClaimFlags.FlagId.SHOW_LEAVE
         ? (id == ClaimFlags.FlagId.SHOW_PARTICLES ? "Clic para elegir partícula y densidad" : "Clic para cambiar")
         : "Clic izq: editar | Clic der: on/off";
      return new String[]{desc, action};
   }

   private static ItemStack withName(ItemStack stack, Component name) {
      stack.setHoverName(name);
      return stack;
   }

   private static ItemStack withLore(ItemStack stack, List<Component> lore) {
      ClaimBlocks.setLore(stack, lore);
      return stack;
   }

   private static String truncate(String s, int max) {
      if (s == null) {
         return "";
      } else {
         return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "...";
      }
   }

   public void clicked(int slotId, int button, ClickType clickType, Player player) {
      if (slotId >= 0 && slotId < 54) {
         if (slotId == 45 && this.page > 0) {
            open(this.viewer, this.claim, this.page - 1);
         } else if (slotId == 53 && this.page < LAST_PAGE) {
            open(this.viewer, this.claim, this.page + 1);
         } else if (slotId == 46) {
            if (!this.awaitingDeleteConfirm) {
               this.awaitingDeleteConfirm = true;
               this.rebuild();
               this.viewer.displayClientMessage(Component.literal("[!] Haz clic de nuevo para confirmar.").withStyle(ChatFormatting.YELLOW), true);
            } else {
               this.performDelete();
            }
         } else if (slotId == 47 && this.awaitingDeleteConfirm) {
            this.awaitingDeleteConfirm = false;
            this.rebuild();
            this.viewer.displayClientMessage(Component.literal("[i] Eliminación cancelada.").withStyle(ChatFormatting.AQUA), true);
         } else {
            if (this.awaitingDeleteConfirm) {
               this.awaitingDeleteConfirm = false;
            }

            ClaimFlags.FlagId clicked;
            if ((clicked = this.slotToFlag(slotId)) != null) {
               int reqLevel = requiredPaidLevel(clicked);
               if (reqLevel > 0 && paidLevelOf(this.claim.getTier()) < reqLevel) {
                  this.viewer
                     .displayClientMessage(Component.literal("[x] Requiere zona " + requiredTierLabel(reqLevel) + " o superior.").withStyle(ChatFormatting.RED), true);
                  return;
               }

               if (clicked == ClaimFlags.FlagId.SHOW_WELCOME) {
                  if (button == 1) {
                     this.claim.getFlags().showWelcome = !this.claim.getFlags().showWelcome;
                     ClaimManager.getInstance().save();
                     this.rebuild();
                  } else {
                     requestEditWelcome(this.viewer, this.claim, this.page);
                     this.viewer.closeContainer();
                  }
               } else if (clicked == ClaimFlags.FlagId.SHOW_LEAVE) {
                  if (button == 1) {
                     this.claim.getFlags().showLeave = !this.claim.getFlags().showLeave;
                     ClaimManager.getInstance().save();
                     this.rebuild();
                  } else {
                     requestEditLeave(this.viewer, this.claim, this.page);
                     this.viewer.closeContainer();
                  }
               } else if (clicked == ClaimFlags.FlagId.SHOW_BORDER) {
                  this.claim.getFlags().showBorder = !this.claim.getFlags().showBorder;
                  ClaimManager.getInstance().save();
                  this.rebuild();
               } else if (clicked == ClaimFlags.FlagId.SHOW_PARTICLES) {
                  ClaimParticleMenuHandler.open(this.viewer, this.claim, this.page);
               } else {
                  this.claim.getFlags().toggle(clicked);
                  ClaimManager.getInstance().save();
                  this.rebuild();
               }
            } else if (slotId == 38) {
               this.viewer.displayClientMessage(Component.literal("[Protección] Miembros de la zona:").withStyle(ChatFormatting.GRAY), false);
               if (this.claim.getMembers().isEmpty()) {
                  this.viewer.displayClientMessage(Component.literal("  (sin miembros)").withStyle(ChatFormatting.DARK_GRAY), false);
               } else {
                  for (int i = 0; i < this.claim.getMembers().size(); i++) {
                     String n = i < this.claim.getMemberNames().size() ? this.claim.getMemberNames().get(i) : this.claim.getMembers().get(i).toString();
                     this.viewer.displayClientMessage(Component.literal("  - " + n).withStyle(ChatFormatting.WHITE), false);
                  }
               }
            } else if (slotId == 42) {
               // Clic izquierdo: selector visual (no depende del chat, funciona en cualquier server).
               // Clic derecho: prompt por chat, para jugadores que no estan conectados.
               if (button == 1) {
                  requestAddMember(this.viewer, this.claim, this.page);
                  this.viewer.closeContainer();
               } else {
                  MemberSelectMenu.open(this.viewer, this.claim, this.page, 0);
               }
            } else if (slotId == 40) {
               if (this.claim.getMembers().isEmpty()) {
                  this.viewer.displayClientMessage(Component.literal("[i] Esta zona no tiene miembros que quitar.").withStyle(ChatFormatting.YELLOW), true);
               } else {
                  requestRemoveMember(this.viewer, this.claim, this.page);
                  this.viewer.closeContainer();
               }
            } else if (slotId == 39) {
               requestBanPlayer(this.viewer, this.claim, this.page);
               this.viewer.closeContainer();
            } else if (slotId == 41) {
               if (this.claim.getBannedPlayers().isEmpty()) {
                  this.viewer.displayClientMessage(Component.literal("[i] No hay jugadores baneados.").withStyle(ChatFormatting.YELLOW), true);
               } else {
                  requestUnbanPlayer(this.viewer, this.claim, this.page);
                  this.viewer.closeContainer();
               }
            } else if (slotId == 43) {
               ClaimGroup g = ClaimManager.getInstance().getGroupOf(this.claim);
               if (g == null) {
                  requestMergeName(this.viewer, this.claim, this.page);
                  this.viewer.closeContainer();
               } else if (this.claim.isGroupMother()) {
                  requestMergeUsers(this.viewer, this.claim, this.page);
                  this.viewer.closeContainer();
               }
            } else if (slotId == 44) {
               ClaimGroup g = ClaimManager.getInstance().getGroupOf(this.claim);
               if (g != null && this.claim.isGroupMother()) {
                  ClaimManager.getInstance().dissolveGroupBreaking(g.getGroupId());
                  this.viewer
                     .displayClientMessage(
                        Component.literal("✔ Grupo disuelto. Las piedras solapadas se devolvieron a sus duenos.").withStyle(ChatFormatting.GREEN), false
                     );
                  this.rebuild();
               }
            } else if (slotId == 49) {
               this.viewer.closeContainer();
            } else if (slotId == 52) {
               this.viewer.closeContainer();
               this.viewer.server.getCommands().performPrefixedCommand(this.viewer.createCommandSourceStack(), "claim list");
            }
         }
      }
   }

   private void performDelete() {
      ClaimTier tier = this.claim.getTier();
      Level world = this.viewer.level();
      BlockPos centre = this.claim.getCenter();
      if (tier != null && ClaimBlocks.isClaimConcreteForTier(world.getBlockState(centre).getBlock(), tier)) {
         world.destroyBlock(centre, false);
      }

      world.playSound(null, centre, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 1.0F);
      ClaimManager.getInstance().removeClaim(world, centre);
      if (tier != null) {
         ItemStack stack = ClaimBlocks.createTierItem(tier, 1);
         if (!this.viewer.getInventory().add(stack)) {
            this.viewer.drop(stack, false);
         }
      }

      this.viewer.displayClientMessage(Component.literal("✔ Zona eliminada. Protección devuelta a tu inventario.").withStyle(ChatFormatting.GREEN), false);
      this.viewer.closeContainer();
   }

   /** Indice de pagina siempre dentro de rango, para que indexar los arrays no pueda explotar. */
   private int pageIndex() {
      return Math.max(0, Math.min(LAST_PAGE, this.page));
   }

   private ClaimFlags.FlagId slotToFlag(int slotIndex) {
      ClaimFlags.FlagId[] ids = PAGES[this.pageIndex()];
      int[] slots = PAGE_SLOTS[this.pageIndex()];

      for (int i = 0; i < slots.length; i++) {
         if (slots[i] == slotIndex) {
            return ids[i];
         }
      }

      return null;
   }

   public static void open(ServerPlayer player, Claim claim, int page) {
      open(player, claim, page, null);
   }

   public static void open(ServerPlayer player, final Claim claim, int page, String customTitle) {
      if (claim.getGroupId() != null && !claim.isGroupMother()) {
         Claim mother = claim.getMother();
         String on = mother != null ? mother.getOwnerName() : "?";
         player.displayClientMessage(
            Component.literal("[!] Esta piedra pertenece al grupo de " + on + ". Solo la piedra nodriza gestiona el grupo. Puedes romperla para recuperarla.")
               .withStyle(ChatFormatting.YELLOW),
            false
         );
      } else {
         final int p = Math.max(0, Math.min(LAST_PAGE, page));
         ClaimGroup titleGrp = ClaimManager.getInstance().getGroupOf(claim);
         final String title = customTitle != null
            ? truncate(customTitle, 40)
            : (titleGrp != null ? truncate("Grupo: " + titleGrp.getName(), 40) : truncate("Zona " + claim.sizeLabel() + " - " + claim.getOwnerName(), 40));
         NetworkHooks.openScreen(player, new MenuProvider() {
            public Component getDisplayName() {
               return Component.literal(title).withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD});
            }

            public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
               return new ClaimMenuHandler(id, inv, claim, p);
            }
         });
      }
   }

   public static void requestAddMember(ServerPlayer player, Claim claim, int returnPage) {
      pending.put(player.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.ADD_MEMBER, claim.getClaimId(), returnPage));
      player.displayClientMessage(
         Component.literal("[Protección] Escribe el nombre del jugador a añadir (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false
      );
      player.displayClientMessage(
         Component.literal("    No hace falta que esté conectado. Alternativa: /claim addmember <jugador>").withStyle(ChatFormatting.DARK_GRAY), false
      );
   }

   public static void requestRemoveMember(ServerPlayer player, Claim claim, int returnPage) {
      pending.put(player.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.REMOVE_MEMBER, claim.getClaimId(), returnPage));
      StringBuilder sb = new StringBuilder();
      List<String> names = claim.getMemberNames();

      for (int i = 0; i < names.size(); i++) {
         if (i > 0) {
            sb.append(", ");
         }

         sb.append(names.get(i));
      }

      player.displayClientMessage(
         Component.literal("[Protección] Miembros: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(sb.toString()).withStyle(ChatFormatting.WHITE)),
         false
      );
      player.displayClientMessage(Component.literal("[Protección] Escribe el nombre del invitado a quitar (o 'cancelar'):").withStyle(ChatFormatting.YELLOW), false);
   }

   public static void requestEditWelcome(ServerPlayer player, Claim claim, int returnPage) {
      pending.put(player.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.EDIT_WELCOME, claim.getClaimId(), returnPage));
      player.displayClientMessage(Component.literal("[Protección] Escribe tu bienvenida (max 60 chars) o 'cancelar':").withStyle(ChatFormatting.YELLOW), false);
   }

   public static void requestEditLeave(ServerPlayer player, Claim claim, int returnPage) {
      pending.put(player.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.EDIT_LEAVE, claim.getClaimId(), returnPage));
      player.displayClientMessage(Component.literal("[Protección] Escribe tu mensaje de salida (max 60 chars) o 'cancelar':").withStyle(ChatFormatting.YELLOW), false);
   }

   /** ¿Este jugador tiene un prompt del menu esperando respuesta por chat? */
   public static boolean hasPrompt(UUID playerId) {
      if (playerId == null) {
         return false;
      }

      ClaimMenuHandler.PendingChat p = pending.get(playerId);
      if (p == null) {
         return false;
      }

      if (p.isExpired()) {
         pending.remove(playerId, p);
         return false;
      }

      return true;
   }

   /**
    * Extrae (de forma atomica) el prompt pendiente de un jugador.
    *
    * <p>Es {@code remove} y no {@code get} a proposito: la respuesta puede llegar por dos rutas a la
    * vez (el mixin del paquete de chat y {@code ServerChatEvent}), y solo una debe procesarla.
    */
   public static ClaimMenuHandler.PendingChat popPrompt(UUID playerId) {
      if (playerId == null) {
         return null;
      }

      ClaimMenuHandler.PendingChat p = pending.remove(playerId);
      return p == null || p.isExpired() ? null : p;
   }

   /** Cancela el prompt pendiente de un jugador, si tenia alguno. */
   public static void clearPrompt(UUID playerId) {
      if (playerId != null) {
         pending.remove(playerId);
         pendingMergeName.remove(playerId);
      }
   }

   /**
    * Respaldo para Forge puro. En servidores hibridos este evento puede no llegar nunca, por eso el
    * camino principal es {@link com.claimblocks.mixin.ServerChatPromptMixin}. Si el mixin ya se quedo
    * con el mensaje, aqui no queda prompt pendiente y no se hace nada.
    */
   public static void handleChat(ServerChatEvent event) {
      ServerPlayer sender = event.getPlayer();
      if (sender == null) {
         return;
      }

      String raw = event.getRawText();

      // Ruta de respaldo: si el mixin no pudo capturar el mensaje, se procesa aqui.
      if (ChatPromptRouter.consume(sender, raw)) {
         event.setCanceled(true);
         return;
      }

      // El mixin ya lo procesó: aquí solo hay que evitar que salga en el chat público.
      if (ChatPromptRouter.shouldSuppress(sender.getUUID(), raw)) {
         event.setCanceled(true);
      }
   }

   /** Aplica la respuesta de un prompt. Debe ejecutarse en el hilo del servidor. */
   public static void dispatchPrompt(ServerPlayer sender, ClaimMenuHandler.PendingChat p, String text) {
      // El texto se captura en el hilo de red y esto corre despues en el hilo del servidor: entre
      // ambos momentos el jugador puede haberse ido.
      if (sender == null || p == null || sender.hasDisconnected()) {
         return;
      }

      if (ChatPromptRouter.isCancel(text)) {
         sender.displayClientMessage(Component.literal("[Protección] Cancelado.").withStyle(ChatFormatting.GRAY), false);
         return;
      }

      Claim claim = findClaimById(p.claimId());
      if (claim == null) {
         sender.displayClientMessage(Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
         return;
      }

      switch (p.type()) {
         case ADD_MEMBER:
            handleAddMember(sender, claim, text, p.returnPage());
            break;
         case REMOVE_MEMBER:
            handleRemoveMember(sender, claim, text, p.returnPage());
            break;
         case EDIT_WELCOME:
            handleEditWelcome(sender, claim, text, p.returnPage());
            break;
         case EDIT_LEAVE:
            handleEditLeave(sender, claim, text, p.returnPage());
            break;
         case BAN_PLAYER:
            handleBanPlayer(sender, claim, text, p.returnPage());
            break;
         case UNBAN_PLAYER:
            handleUnbanPlayer(sender, claim, text, p.returnPage());
            break;
         case MERGE_NAME:
            handleMergeName(sender, claim, text, p.returnPage());
            break;
         case MERGE_USERS:
            handleMergeUsers(sender, claim, text, p.returnPage());
            break;
      }
   }

   /** Aplica una transferencia de zona pedida desde el panel admin. Hilo del servidor. */
   public static void dispatchAdminTransfer(ServerPlayer op, UUID claimId, String text) {
      if (op == null || op.hasDisconnected()) {
         return;
      }

      if (ChatPromptRouter.isCancel(text)) {
         op.displayClientMessage(Component.literal("[Protección] Cancelado.").withStyle(ChatFormatting.GRAY), false);
         return;
      }

      handleAdminTransfer(op, claimId, ChatPromptRouter.extractPlayerName(text));
   }

   private static void handleAdminTransfer(ServerPlayer op, UUID claimId, String name) {
      Claim claim = findClaimById(claimId);
      if (claim == null) {
         op.displayClientMessage(Component.literal("[x] La zona ya no existe.").withStyle(ChatFormatting.RED), false);
         return;
      }

      PlayerLookup.Resolved target = PlayerLookup.resolve(op.getServer(), name);
      if (target == null) {
         op.displayClientMessage(Component.literal("[x] Jugador no encontrado: " + name).withStyle(ChatFormatting.RED), false);
         return;
      }

      claim.setOwner(target.id(), target.name());
      claim.getMembers().clear();
      claim.getMemberNames().clear();
      ClaimManager.getInstance().save();
      op.displayClientMessage(Component.literal("\u2714 Zona transferida a " + target.name() + ".").withStyle(ChatFormatting.GREEN), false);

      MutableComponent msg = Component.literal("[!] Un administrador te transfirió una zona ")
         .withStyle(ChatFormatting.YELLOW)
         .append(Component.literal(claim.sizeLabel()).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}))
         .append(Component.literal(" en X:" + claim.getX() + " Z:" + claim.getZ()).withStyle(ChatFormatting.YELLOW));
      if (target.isOnline()) {
         target.online().displayClientMessage(msg, false);
      } else {
         ClaimManager.getInstance().queueMessage(target.id(), msg);
      }
   }

   private static void handleAddMember(ServerPlayer sender, Claim claim, String name, int page) {
      addMemberByName(sender, claim, name, page, true);
   }

   /**
    * Anade un miembro resolviendo el nombre. Es el camino unico que usan el prompt de chat, el
    * selector visual y el comando {@code /claim addmember}.
    *
    * <p>A diferencia de la 7.6.5, el jugador <b>no necesita estar conectado</b>: si no esta online se
    * resuelve con la cache de perfiles del servidor y se le deja el aviso en cola para cuando entre.
    *
    * @param reopenMenu si hay que volver a abrir el menu de la zona al terminar.
    * @return true si el miembro quedo anadido.
    */
   public static boolean addMemberByName(ServerPlayer sender, Claim claim, String name, int page, boolean reopenMenu) {
      String query = ChatPromptRouter.extractPlayerName(name);
      PlayerLookup.Resolved target = PlayerLookup.resolve(sender.getServer(), query);

      if (target == null) {
         sender.displayClientMessage(
            Component.literal("[x] No encuentro al jugador \"" + query + "\". Revisa el nombre; si nunca ha entrado al servidor, no puedo resolverlo.")
               .withStyle(ChatFormatting.RED),
            false
         );
         if (reopenMenu) {
            open(sender, claim, page);
         }

         return false;
      }

      boolean added = addMemberResolved(sender, claim, target);
      if (reopenMenu) {
         open(sender, claim, page);
      }

      return added;
   }

   /** Anade un miembro ya resuelto (UUID + nombre). Devuelve true si se anadio de verdad. */
   public static boolean addMemberResolved(ServerPlayer sender, Claim claim, PlayerLookup.Resolved target) {
      if (claim.isOwner(target.id())) {
         sender.displayClientMessage(Component.literal("[x] Ese jugador ya es el dueño.").withStyle(ChatFormatting.RED), false);
         return false;
      }

      if (claim.isMember(target.id())) {
         sender.displayClientMessage(
            Component.literal("[i] " + target.name() + " ya es miembro de esta zona.").withStyle(ChatFormatting.YELLOW), false
         );
         return false;
      }

      if (claim.isBanned(target.id())) {
         claim.unbanPlayer(target.id());
         sender.displayClientMessage(
            Component.literal("[i] " + target.name() + " estaba baneado de la zona; se le quitó el baneo.").withStyle(ChatFormatting.YELLOW), false
         );
      }

      claim.addMember(target.id(), target.name());
      ClaimManager.getInstance().save();
      sender.displayClientMessage(
         Component.literal("✔ " + target.name() + " agregado como miembro de la zona.").withStyle(ChatFormatting.GREEN), false
      );

      Component notice = Component.literal("[Protección] Eres miembro de la zona de " + sender.getName().getString()).withStyle(ChatFormatting.AQUA);
      if (target.isOnline()) {
         target.online().displayClientMessage(notice, false);
      } else {
         // El jugador esta desconectado: se le entrega el aviso en su proximo login.
         ClaimManager.getInstance().queueMessage(target.id(), notice);
      }

      return true;
   }

   private static void handleRemoveMember(ServerPlayer sender, Claim claim, String name, int page) {
      removeMemberByName(sender, claim, name, page, true);
   }

   /**
    * Quita un miembro por nombre. Busca primero en la lista guardada de la zona (asi funciona con
    * jugadores desconectados) y luego entre los conectados.
    *
    * @param reopenMenu si hay que volver a abrir el menu de la zona al terminar.
    * @return true si el miembro se quito.
    */
   public static boolean removeMemberByName(ServerPlayer sender, Claim claim, String name, int page, boolean reopenMenu) {
      String query = ChatPromptRouter.extractPlayerName(name);
      UUID targetId = null;
      String resolvedName = query;

      for (int i = 0; i < claim.getMemberNames().size() && i < claim.getMembers().size(); i++) {
         if (claim.getMemberNames().get(i).equalsIgnoreCase(query)) {
            targetId = claim.getMembers().get(i);
            resolvedName = claim.getMemberNames().get(i);
            break;
         }
      }

      if (targetId == null) {
         PlayerLookup.Resolved resolved = PlayerLookup.resolve(sender.getServer(), query);
         if (resolved != null && claim.isMember(resolved.id())) {
            targetId = resolved.id();
            resolvedName = resolved.name();
         }
      }

      if (targetId == null) {
         sender.displayClientMessage(
            Component.literal("[x] " + query + " no es miembro de esta zona.").withStyle(ChatFormatting.RED), false
         );
         if (reopenMenu) {
            open(sender, claim, page);
         }

         return false;
      }

      claim.removeMember(targetId);
      ClaimManager.getInstance().save();
      sender.displayClientMessage(Component.literal("\u2714 " + resolvedName + " fue eliminado de la zona.").withStyle(ChatFormatting.GREEN), false);

      Component notice = Component.literal("[Protecci\u00f3n] Ya no eres miembro de la zona de " + sender.getName().getString())
         .withStyle(ChatFormatting.YELLOW);
      ServerPlayer removed = sender.getServer() == null ? null : sender.getServer().getPlayerList().getPlayer(targetId);
      if (removed != null) {
         removed.displayClientMessage(notice, false);
      } else {
         ClaimManager.getInstance().queueMessage(targetId, notice);
      }

      if (reopenMenu) {
         open(sender, claim, page);
      }

      return true;
   }

   private static void handleEditWelcome(ServerPlayer sender, Claim claim, String text, int page) {
      if (text.length() > 60) {
         text = text.substring(0, 60);
      }

      claim.getFlags().welcomeMessage = text;
      claim.getFlags().showWelcome = !text.isBlank();
      ClaimManager.getInstance().save();
      sender.displayClientMessage(Component.literal("✔ Bienvenida guardada.").withStyle(ChatFormatting.GREEN), false);
      open(sender, claim, page);
   }

   private static void handleEditLeave(ServerPlayer sender, Claim claim, String text, int page) {
      if (text.length() > 60) {
         text = text.substring(0, 60);
      }

      claim.getFlags().leaveMessage = text;
      claim.getFlags().showLeave = !text.isBlank();
      ClaimManager.getInstance().save();
      sender.displayClientMessage(Component.literal("✔ Mensaje de salida guardado.").withStyle(ChatFormatting.GREEN), false);
      open(sender, claim, page);
   }

   public static void requestMergeName(ServerPlayer player, Claim claim, int returnPage) {
      pending.put(player.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.MERGE_NAME, claim.getClaimId(), returnPage));
      player.displayClientMessage(Component.literal("[Grupo] Escribe el NOMBRE de la zona unida (o 'cancelar'):").withStyle(ChatFormatting.LIGHT_PURPLE), false);
   }

   public static void requestMergeUsers(ServerPlayer player, Claim claim, int returnPage) {
      pending.put(player.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.MERGE_USERS, claim.getClaimId(), returnPage));
      player.displayClientMessage(
         Component.literal("[Grupo] Escribe el/los jugadores a invitar (separados por espacio) o 'cancelar':").withStyle(ChatFormatting.LIGHT_PURPLE), false
      );
   }

   private static void handleMergeName(ServerPlayer sender, Claim claim, String text, int page) {
      String name = text.length() > 32 ? text.substring(0, 32) : text;
      pendingMergeName.put(sender.getUUID(), name);
      pending.put(sender.getUUID(), new ClaimMenuHandler.PendingChat(ClaimMenuHandler.PendingType.MERGE_USERS, claim.getClaimId(), page));
      sender.displayClientMessage(
         Component.literal("[Grupo] Nombre: \"" + name + "\". Ahora escribe el/los jugadores a invitar (separados por espacio):")
            .withStyle(ChatFormatting.LIGHT_PURPLE),
         false
      );
   }

   private static void handleMergeUsers(ServerPlayer sender, Claim claim, String text, int page) {
      ClaimManager mgr = ClaimManager.getInstance();
      ClaimGroup g = mgr.getGroupOf(claim);
      if (g == null) {
         String name = pendingMergeName.getOrDefault(sender.getUUID(), "Grupo");
         g = mgr.createGroup(claim, name);
      }

      pendingMergeName.remove(sender.getUUID());
      // Se sanea antes de partir por si un plugin de chat inyecto formato en el mensaje.
      String[] parts = ChatPromptRouter.sanitize(text).split("[ ,]+");
      int sent = 0;

      for (String raw : parts) {
         String pname = raw.trim();
         if (!pname.isEmpty()) {
            ServerPlayer target = sender.server.getPlayerList().getPlayerByName(pname);
            if (target == null) {
               sender.displayClientMessage(
                  Component.literal("[x] " + pname + " no esta en linea (debe estar conectado para invitarlo).").withStyle(ChatFormatting.RED), false
               );
            } else if (!target.getUUID().equals(sender.getUUID())) {
               if (g.isRegistered(target.getUUID())) {
                  sender.displayClientMessage(Component.literal("[i] " + target.getName().getString() + " ya esta en el grupo.").withStyle(ChatFormatting.GRAY), false);
               } else {
                  String code = genCode();
                  invites.put(code, new ClaimMenuHandler.MergeInvite(code, g.getGroupId(), target.getUUID(), sender.getName().getString(), g.getName()));
                  sendInvite(target, sender.getName().getString(), g.getName(), code);
                  sent++;
               }
            }
         }
      }

      if (sent > 0) {
         sender.displayClientMessage(
            Component.literal("✔ Invitacion enviada a " + sent + " jugador(es). Grupo: \"" + g.getName() + "\".").withStyle(ChatFormatting.GREEN), false
         );
      }

      open(sender, claim, page);
   }

   private static void sendInvite(ServerPlayer target, String inviterName, String groupName, String code) {
      target.displayClientMessage(
         Component.literal("[Grupo] " + inviterName + " te invita a unir tu proteccion al grupo \"" + groupName + "\".").withStyle(ChatFormatting.AQUA),
         false
      );
      Component accept = Component.literal(" [✔ ACEPTAR] ")
         .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true).withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/claimmerge accept " + code)));
      Component reject = Component.literal("[✘ RECHAZAR]")
         .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true).withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/claimmerge reject " + code)));
      target.displayClientMessage(Component.literal("").append(accept).append(reject), false);
   }

   public static void acceptMerge(ServerPlayer target, String code) {
      ClaimMenuHandler.MergeInvite inv = invites.remove(code);
      if (inv != null && target.getUUID().equals(inv.targetId())) {
         ClaimManager mgr = ClaimManager.getInstance();
         ClaimGroup g = mgr.getGroup(inv.groupId());
         if (g == null) {
            target.displayClientMessage(Component.literal("[x] El grupo ya no existe.").withStyle(ChatFormatting.RED), false);
         } else {
            mgr.registerPlayer(g.getGroupId(), target.getUUID());
            target.displayClientMessage(
               Component.literal("✔ Te uniste al grupo \"" + g.getName() + "\". Ahora tus piedras colocadas dentro de esa zona se uniran.")
                  .withStyle(ChatFormatting.GREEN),
               false
            );
            Component note = Component.literal(target.getName().getString() + " acepto unirse al grupo \"" + g.getName() + "\".")
               .withStyle(ChatFormatting.GREEN);
            ServerPlayer inviter = g.getMotherOwnerId() == null ? null : target.server.getPlayerList().getPlayer(g.getMotherOwnerId());
            if (inviter != null) {
               inviter.displayClientMessage(note, false);
            } else if (g.getMotherOwnerId() != null) {
               mgr.queueMessage(g.getMotherOwnerId(), note);
            }
         }
      } else {
         target.displayClientMessage(Component.literal("[x] Invitacion no valida o expirada.").withStyle(ChatFormatting.RED), false);
      }
   }

   public static void rejectMerge(ServerPlayer target, String code) {
      ClaimMenuHandler.MergeInvite inv = invites.remove(code);
      if (inv != null && target.getUUID().equals(inv.targetId())) {
         target.displayClientMessage(Component.literal("[i] Rechazaste la invitacion de union.").withStyle(ChatFormatting.GRAY), false);
         ClaimGroup g = ClaimManager.getInstance().getGroup(inv.groupId());
         if (g != null && g.getMotherOwnerId() != null) {
            Component note = Component.literal(target.getName().getString() + " rechazo unirse al grupo \"" + g.getName() + "\".")
               .withStyle(ChatFormatting.YELLOW);
            ServerPlayer inviter = target.server.getPlayerList().getPlayer(g.getMotherOwnerId());
            if (inviter != null) {
               inviter.displayClientMessage(note, false);
            } else {
               ClaimManager.getInstance().queueMessage(g.getMotherOwnerId(), note);
            }
         }
      } else {
         target.displayClientMessage(Component.literal("[x] Invitacion no valida o expirada.").withStyle(ChatFormatting.RED), false);
      }
   }

   public static void leaveMerge(ServerPlayer player) {
      ClaimManager mgr = ClaimManager.getInstance();
      ClaimGroup g = mgr.getGroupByRegistered(player.getUUID());
      if (g == null) {
         player.displayClientMessage(Component.literal("[!] No estas en ningun grupo.").withStyle(ChatFormatting.YELLOW), false);
      } else {
         boolean wasMother = player.getUUID().equals(g.getMotherOwnerId());
         String name = g.getName();
         mgr.leaveGroupBreaking(g.getGroupId(), player.getUUID());
         player.displayClientMessage(
            Component.literal(
                  wasMother ? "✔ Disolviste el grupo \"" + name + "\"." : "✔ Saliste del grupo \"" + name + "\". Tus piedras vuelven a ser independientes."
               )
               .withStyle(ChatFormatting.GREEN),
            false
         );
      }
   }

   private static String genCode() {
      return UUID.randomUUID().toString().substring(0, 8);
   }

   private static Claim findClaimById(UUID id) {
      for (Claim c : ClaimManager.getInstance().getAllClaims()) {
         if (c.getClaimId().equals(id)) {
            return c;
         }
      }

      return null;
   }

   public static record MergeInvite(String code, UUID groupId, UUID targetId, String inviterName, String groupName) {
   }

   /**
    * Prompt de chat pendiente para un jugador.
    *
    * <p>Lleva marca de tiempo porque desde la 7.7.0 la respuesta se captura a nivel de paquete: si el
    * jugador se olvida de que dejo un prompt abierto, su siguiente mensaje de chat se interpretaria
    * como respuesta. Con la caducidad, pasado un rato el prompt simplemente se descarta y el chat
    * vuelve a ser chat.
    */
   public static record PendingChat(ClaimMenuHandler.PendingType type, UUID claimId, int returnPage, long createdAtMillis) {
      public PendingChat(ClaimMenuHandler.PendingType type, UUID claimId, int returnPage) {
         this(type, claimId, returnPage, System.currentTimeMillis());
      }

      public boolean isExpired() {
         return System.currentTimeMillis() - this.createdAtMillis > PROMPT_TTL_MS;
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
