package com.claimblocks.command;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import com.claimblocks.gui.ClaimMenuHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class ClaimCommands {
   private static final SuggestionProvider<CommandSourceStack> CLAIMSTONE_IDS = (ctx, builder) -> {
      String[] ids = new String[ClaimTier.VALUES.length];

      for (int i = 0; i < ClaimTier.VALUES.length; i++) {
         ids[i] = ClaimTier.VALUES[i].id;
      }

      return SharedSuggestionProvider.suggest(ids, builder);
   };
   private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> SharedSuggestionProvider.suggest(
      ((CommandSourceStack)ctx.getSource()).getOnlinePlayerNames(), builder
   );
   private static final SuggestionProvider<CommandSourceStack> MEMBER_NAMES = (ctx, builder) -> {
      ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayer();
      if (p != null) {
         Claim c = ClaimManager.getInstance().getClaimAt(p.level(), p.blockPosition());
         if (c != null) {
            return SharedSuggestionProvider.suggest(c.getMemberNames(), builder);
         }
      }

      return builder.buildFuture();
   };

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                "claim"
                                             )
                                             .executes(ClaimCommands::help))
                                          .then(Commands.literal("help").executes(ClaimCommands::help)))
                                       .then(Commands.literal("menu").executes(ClaimCommands::menu)))
                                    .then(Commands.literal("info").executes(ClaimCommands::info)))
                                 .then(Commands.literal("list").executes(ClaimCommands::list)))
                              .then(Commands.literal("remove").executes(ClaimCommands::remove)))
                           .then(
                              ((LiteralArgumentBuilder)Commands.literal("ban").requires(s -> s.hasPermission(2)))
                                 .then(Commands.argument("jugador", EntityArgument.player()).executes(ClaimCommands::ban))
                           ))
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("unban").requires(s -> s.hasPermission(2)))
                              .then(Commands.argument("jugador", EntityArgument.player()).executes(ClaimCommands::unban))
                        ))
                     .then(
                        ((LiteralArgumentBuilder)Commands.literal("transfer").requires(s -> s.hasPermission(2)))
                           .then(Commands.argument("jugador", EntityArgument.player()).executes(ClaimCommands::transfer))
                     ))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("removemember").requires(s -> s.hasPermission(2)))
                        .then(Commands.argument("jugador", EntityArgument.player()).executes(ClaimCommands::removeMember))
                  )
                  .then(
                     Commands.literal("addmember")
                        .then(Commands.argument("jugador", StringArgumentType.word()).suggests(ONLINE_PLAYERS).executes(ClaimCommands::addMember))
                  )
                  .then(
                     Commands.literal("delmember")
                        .then(Commands.argument("jugador", StringArgumentType.word()).suggests(MEMBER_NAMES).executes(ClaimCommands::delMember))
                  )
                  .then(Commands.literal("members").executes(ClaimCommands::members)))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("give").requires(s -> s.hasPermission(2)))
                     .then(
                        Commands.argument("jugador", EntityArgument.players())
                           .then(Commands.argument("id", StringArgumentType.word()).suggests(CLAIMSTONE_IDS).executes(ClaimCommands::give))
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("clear").requires(s -> s.hasPermission(2)))
                  .then(Commands.argument("jugador", EntityArgument.player()).executes(ClaimCommands::clear))
            )
      );
   }

   private static int help(CommandContext<CommandSourceStack> ctx) {
      boolean isOp = ((CommandSourceStack)ctx.getSource()).hasPermission(2);
      ((CommandSourceStack)ctx.getSource())
         .sendSuccess(
            () -> {
               MutableComponent t = Component.literal("=== ClaimBlocks Comandos ===\n")
                  .withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})
                  .append(Component.literal("/claim menu  ").withStyle(ChatFormatting.AQUA))
                  .append(Component.literal("- abre el menu de la zona\n").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal("/claim info  ").withStyle(ChatFormatting.AQUA))
                  .append(Component.literal("- info de la zona\n").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal("/claim list  ").withStyle(ChatFormatting.AQUA))
                  .append(Component.literal("- lista tus zonas\n").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal("/claim remove  ").withStyle(ChatFormatting.AQUA))
                  .append(Component.literal("- borra tu zona actual\n").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal("/claim addmember <jugador>  ").withStyle(ChatFormatting.AQUA))
                  .append(Component.literal("- añade un miembro (aunque esté offline)\n").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal("/claim delmember <jugador>  ").withStyle(ChatFormatting.AQUA))
                  .append(Component.literal("- quita un miembro\n").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal("/claim members  ").withStyle(ChatFormatting.AQUA))
                  .append(Component.literal("- lista los miembros de la zona\n").withStyle(ChatFormatting.GRAY));
               if (isOp) {
                  t.append(Component.literal("\n--- Solo Operadores ---\n").withStyle(ChatFormatting.RED))
                     .append(Component.literal("/claim give <jugador> <tier>\n").withStyle(ChatFormatting.YELLOW))
                     .append(Component.literal("/claim clear <jugador>\n").withStyle(ChatFormatting.YELLOW))
                     .append(Component.literal("/claim ban|unban <jugador>\n").withStyle(ChatFormatting.YELLOW))
                     .append(Component.literal("/claim transfer <jugador>\n").withStyle(ChatFormatting.YELLOW))
                     .append(Component.literal("/claim removemember <jugador>\n").withStyle(ChatFormatting.YELLOW))
                     .append(Component.literal("/claimadmin").withStyle(ChatFormatting.YELLOW));
               }

               return t;
            },
            false
         );
      return 1;
   }

   private static int menu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      Claim c = ClaimManager.getInstance().getClaimAt(p.level(), p.blockPosition());
      if (c == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] No estás en ninguna zona protegida.").withStyle(ChatFormatting.RED));
         return 0;
      } else if (!c.isOwner(p) && !p.hasPermissions(2)) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] Solo el dueño puede abrir el menu.").withStyle(ChatFormatting.RED));
         return 0;
      } else {
         ClaimMenuHandler.open(p, c, 0);
         return 1;
      }
   }

   private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      Claim c = ClaimManager.getInstance().getClaimAt(p.level(), p.blockPosition());
      if (c == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] No estás en ninguna zona protegida.").withStyle(ChatFormatting.RED));
         return 0;
      } else {
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(
               () -> Component.literal("=== Zona " + c.sizeLabel() + " ===\n")
                     .withStyle(ChatFormatting.YELLOW)
                     .append(Component.literal("Dueño: " + c.getOwnerName() + "\n").withStyle(ChatFormatting.GRAY))
                     .append(Component.literal("Centro: X=" + c.getX() + " Y=" + c.getY() + " Z=" + c.getZ() + "\n").withStyle(ChatFormatting.GRAY))
                     .append(Component.literal("Miembros: " + c.getMembers().size()).withStyle(ChatFormatting.GRAY)),
               false
            );
         return 1;
      }
   }

   private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      List<Claim> claims = ClaimManager.getInstance().getClaimsOf(p.getUUID());
      if (claims.isEmpty()) {
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("[i] No tienes zonas.").withStyle(ChatFormatting.GRAY), false);
         return 0;
      } else {
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(
               () -> {
                  MutableComponent t = Component.literal("=== Tus zonas (" + claims.size() + ") ===\n").withStyle(ChatFormatting.YELLOW);

                  for (Claim c : claims) {
                     t.append(
                        Component.literal("- " + c.sizeLabel() + " en X=" + c.getX() + " Z=" + c.getZ() + " (" + c.getWorld() + ")\n")
                           .withStyle(ChatFormatting.GRAY)
                     );
                  }

                  return t;
               },
               false
            );
         return claims.size();
      }
   }

   private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      Claim c = ClaimManager.getInstance().getClaimAt(p.level(), p.blockPosition());
      if (c == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] No estás en ninguna zona protegida.").withStyle(ChatFormatting.RED));
         return 0;
      } else if (!c.isOwner(p) && !p.hasPermissions(2)) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] Solo el dueño puede eliminar esta zona.").withStyle(ChatFormatting.RED));
         return 0;
      } else {
         BlockPos centre = c.getCenter();
         ClaimTier tier = c.getTier();
         if (tier != null && ClaimBlocks.isClaimConcreteForTier(p.level().getBlockState(centre).getBlock(), tier)) {
            p.level().destroyBlock(centre, false);
         }

         ClaimManager.getInstance().removeClaim(p.level(), centre);
         p.level().playSound(null, centre, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 1.0F);
         if (tier != null) {
            ItemStack stack = ClaimBlocks.createTierItem(tier, 1);
            if (!p.getInventory().add(stack)) {
               p.drop(stack, false);
            }
         }

         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> Component.literal("✔ Zona eliminada. Protección devuelta a tu inventario.").withStyle(ChatFormatting.GREEN), false);
         return 1;
      }
   }

   private static int ban(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      ServerPlayer target = EntityArgument.getPlayer(ctx, "jugador");
      Claim c = ClaimManager.getInstance().getClaimAt(exec.level(), exec.blockPosition());
      if (c == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] No estás en ninguna zona.").withStyle(ChatFormatting.RED));
         return 0;
      } else if (!c.isOwner(exec) && !exec.hasPermissions(2)) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] Solo el dueño puede banear.").withStyle(ChatFormatting.RED));
         return 0;
      } else if (target.hasPermissions(2) && !exec.hasPermissions(2)) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] No puedes banear a un operador.").withStyle(ChatFormatting.RED));
         return 0;
      } else {
         c.banPlayer(target.getUUID());
         ClaimManager.getInstance().save();
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> Component.literal("✔ " + target.getName().getString() + " baneado.").withStyle(ChatFormatting.GREEN), true);
         return 1;
      }
   }

   private static int unban(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      ServerPlayer target = EntityArgument.getPlayer(ctx, "jugador");
      Claim c = ClaimManager.getInstance().getClaimAt(exec.level(), exec.blockPosition());
      if (c == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] No estás en ninguna zona.").withStyle(ChatFormatting.RED));
         return 0;
      } else if (!c.isOwner(exec) && !exec.hasPermissions(2)) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] Solo el dueño puede desbanear.").withStyle(ChatFormatting.RED));
         return 0;
      } else {
         c.unbanPlayer(target.getUUID());
         ClaimManager.getInstance().save();
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> Component.literal("✔ " + target.getName().getString() + " desbaneado.").withStyle(ChatFormatting.GREEN), true);
         return 1;
      }
   }

   private static int transfer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      ServerPlayer target = EntityArgument.getPlayer(ctx, "jugador");
      Claim c = ClaimManager.getInstance().getClaimAt(exec.level(), exec.blockPosition());
      if (c == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] No estás en ninguna zona.").withStyle(ChatFormatting.RED));
         return 0;
      } else if (!c.isOwner(exec) && !exec.hasPermissions(2)) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] Solo el dueño puede transferir.").withStyle(ChatFormatting.RED));
         return 0;
      } else if (target.hasPermissions(2) && !exec.hasPermissions(2)) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] No puedes transferir a un operador.").withStyle(ChatFormatting.RED));
         return 0;
      } else if (c.isOwner(target.getUUID())) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] Ya es el dueño actual.").withStyle(ChatFormatting.RED));
         return 0;
      } else {
         ClaimManager.getInstance().transferOwnership(c, target.getUUID(), target.getName().getString());
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> Component.literal("✔ Zona transferida a " + target.getName().getString()).withStyle(ChatFormatting.GREEN), true);
         target.displayClientMessage(
            Component.literal("[Protección] Has recibido la propiedad de una zona en X=" + c.getX() + " Z=" + c.getZ()).withStyle(ChatFormatting.GREEN),
            false
         );
         return 1;
      }
   }

   /**
    * Devuelve la zona donde esta el ejecutor, comprobando que puede administrarla.
    * Manda el error al ejecutor y devuelve null si no procede.
    */
   private static Claim ownedClaimAt(CommandSourceStack source, ServerPlayer p) {
      Claim c = ClaimManager.getInstance().getClaimAt(p.level(), p.blockPosition());
      if (c == null) {
         source.sendFailure(Component.literal("[x] No estás en ninguna zona protegida.").withStyle(ChatFormatting.RED));
         return null;
      }

      if (!c.isOwner(p) && !p.hasPermissions(2)) {
         source.sendFailure(Component.literal("[x] Solo el dueño puede gestionar los miembros de esta zona.").withStyle(ChatFormatting.RED));
         return null;
      }

      return c;
   }

   /**
    * {@code /claim addmember <jugador>}: alta de miembro sin pasar por el chat.
    *
    * <p>Existe porque el menu pedia el nombre por chat y en servidores hibridos (Mohist) los plugins
    * de chat pueden quedarse el mensaje. Esta ruta es un comando, asi que nunca depende de eso.
    */
   private static int addMember(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      Claim c = ownedClaimAt((CommandSourceStack)ctx.getSource(), exec);
      if (c == null) {
         return 0;
      }

      String name = StringArgumentType.getString(ctx, "jugador");
      return ClaimMenuHandler.addMemberByName(exec, c, name, 0, false) ? 1 : 0;
   }

   /** {@code /claim delmember <jugador>}: baja de miembro, tambien para jugadores desconectados. */
   private static int delMember(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      Claim c = ownedClaimAt((CommandSourceStack)ctx.getSource(), exec);
      if (c == null) {
         return 0;
      }

      String name = StringArgumentType.getString(ctx, "jugador");
      return ClaimMenuHandler.removeMemberByName(exec, c, name, 0, false) ? 1 : 0;
   }

   /** {@code /claim members}: lista los miembros de la zona actual. */
   private static int members(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      // La lista de miembros solo la ve quien administra la zona, igual que en el menu.
      Claim c = ownedClaimAt((CommandSourceStack)ctx.getSource(), exec);
      if (c == null) {
         return 0;
      }

      final Claim claim = c;
      ((CommandSourceStack)ctx.getSource())
         .sendSuccess(
            () -> {
               MutableComponent t = Component.literal("=== Miembros de la zona de " + claim.getOwnerName() + " (" + claim.getMembers().size() + ") ===\n")
                  .withStyle(ChatFormatting.YELLOW);
               if (claim.getMembers().isEmpty()) {
                  t.append(Component.literal("(sin miembros)").withStyle(ChatFormatting.DARK_GRAY));
               } else {
                  for (int i = 0; i < claim.getMembers().size(); i++) {
                     String n = i < claim.getMemberNames().size() ? claim.getMemberNames().get(i) : claim.getMembers().get(i).toString();
                     t.append(Component.literal("- " + n + "\n").withStyle(ChatFormatting.WHITE));
                  }
               }

               return t;
            },
            false
         );
      return claim.getMembers().size();
   }

   private static int removeMember(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      ServerPlayer target = EntityArgument.getPlayer(ctx, "jugador");
      Claim c = ClaimManager.getInstance().getClaimAt(exec.level(), exec.blockPosition());
      if (c == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] No estás en ninguna zona.").withStyle(ChatFormatting.RED));
         return 0;
      } else if (!c.isOwner(exec) && !exec.hasPermissions(2)) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] Solo el dueño puede gestionar miembros.").withStyle(ChatFormatting.RED));
         return 0;
      } else if (target.hasPermissions(2) && !exec.hasPermissions(2)) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] No puedes gestionar a un operador.").withStyle(ChatFormatting.RED));
         return 0;
      } else if (!c.isMember(target.getUUID())) {
         ((CommandSourceStack)ctx.getSource())
            .sendFailure(Component.literal("[x] " + target.getName().getString() + " no es miembro.").withStyle(ChatFormatting.RED));
         return 0;
      } else {
         c.removeMember(target.getUUID());
         ClaimManager.getInstance().save();
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> Component.literal("✔ " + target.getName().getString() + " eliminado de la zona.").withStyle(ChatFormatting.GREEN), true);
         return 1;
      }
   }

   private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      String id = StringArgumentType.getString(ctx, "id");
      ClaimTier tier = ClaimTier.byId(id);
      if (tier == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] ID no válido: " + id).withStyle(ChatFormatting.RED));
         return 0;
      } else {
         Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "jugador");

         for (ServerPlayer p : targets) {
            ItemStack stack = ClaimBlocks.createTierItem(tier, 1);
            if (!p.getInventory().add(stack)) {
               p.drop(stack, false);
            }

            p.displayClientMessage(Component.literal("[+] Recibiste Protección " + tier.label()).withStyle(ChatFormatting.GREEN), false);
         }

         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(
               () -> Component.literal("✔ Protección " + tier.label() + " entregada a " + targets.size() + " jugador(es).").withStyle(ChatFormatting.GREEN),
               true
            );
         return targets.size();
      }
   }

   private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer target = EntityArgument.getPlayer(ctx, "jugador");
      int n = ClaimManager.getInstance().clearClaimsOf(target.getUUID());
      ((CommandSourceStack)ctx.getSource())
         .sendSuccess(() -> Component.literal("✔ Eliminadas " + n + " zona(s) de " + target.getName().getString()).withStyle(ChatFormatting.GREEN), true);
      return n;
   }
}
