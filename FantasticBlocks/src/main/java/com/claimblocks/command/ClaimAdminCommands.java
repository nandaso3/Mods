package com.claimblocks.command;

import com.claimblocks.chat.ChatPromptRouter;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.GlobalFlags;
import com.claimblocks.gui.AdminPanelHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class ClaimAdminCommands {
   private static final SuggestionProvider<CommandSourceStack> GLOBAL_FLAGS = (ctx, builder) -> SharedSuggestionProvider.suggest(
         new String[]{"globalPVP", "globalMobGriefing", "globalFireSpread", "globalNoMobSpawn"}, builder
      );
   private static final SuggestionProvider<CommandSourceStack> ON_OFF = (ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"on", "off"}, builder);

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                              "claimadmin"
                           )
                           .requires(s -> s.hasPermission(2)))
                        .executes(ClaimAdminCommands::openPanel))
                     .then(Commands.literal("bypass").executes(ClaimAdminCommands::toggleBypass)))
                  .then(Commands.literal("list").executes(ClaimAdminCommands::list)))
               .then(Commands.literal("stats").executes(ClaimAdminCommands::stats)))
            .then(
               Commands.literal("globalflag")
                  .then(
                     Commands.argument("flag", StringArgumentType.word())
                        .suggests(GLOBAL_FLAGS)
                        .then(Commands.argument("value", StringArgumentType.word()).suggests(ON_OFF).executes(ClaimAdminCommands::globalFlag))
                  )
            )
      );
   }

   private static int openPanel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      AdminPanelHandler.open(p, 0);
      return 1;
   }

   private static int toggleBypass(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
      boolean on = ClaimManager.getInstance().toggleBypass(p.getUUID());
      ((CommandSourceStack)ctx.getSource())
         .sendSuccess(
            () -> Component.literal(on ? "✔ Bypass ACTIVADO (ignoras protecciones)." : "[i] Bypass desactivado.")
                  .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY),
            false
         );
      return 1;
   }

   private static int list(CommandContext<CommandSourceStack> ctx) {
      List<Claim> all = ClaimManager.getInstance().getAllClaims();
      if (all.isEmpty()) {
         ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("[i] No hay zonas en el servidor.").withStyle(ChatFormatting.GRAY), false);
         return 0;
      } else {
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(
               () -> {
                  MutableComponent t = Component.literal("=== Zonas (" + all.size() + ") ===\n").withStyle(ChatFormatting.YELLOW);
                  int shown = 0;

                  for (Claim c : all) {
                     if (shown++ >= 30) {
                        t.append(Component.literal("... y " + (all.size() - 30) + " más").withStyle(ChatFormatting.GRAY));
                        break;
                     }

                     t.append(
                        Component.literal(c.getOwnerName() + " - " + c.sizeLabel() + " @ X=" + c.getX() + " Z=" + c.getZ() + "\n")
                           .withStyle(ChatFormatting.GRAY)
                     );
                  }

                  return t;
               },
               false
            );
         return all.size();
      }
   }

   private static int stats(CommandContext<CommandSourceStack> ctx) {
      List<Claim> all = ClaimManager.getInstance().getAllClaims();
      GlobalFlags gf = GlobalFlags.getInstance();
      ((CommandSourceStack)ctx.getSource())
         .sendSuccess(
            () -> Component.literal("=== Stats ClaimBlocks ===\n")
                  .withStyle(ChatFormatting.YELLOW)
                  .append(Component.literal("Total zonas: " + all.size() + "\n").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal("PVP global: " + (gf.globalPVP ? "ON" : "OFF") + "\n").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal("MobGriefing: " + (gf.globalMobGriefing ? "ON" : "OFF") + "\n").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal("FireSpread: " + (gf.globalFireSpread ? "ON" : "OFF") + "\n").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal("Sin spawn de mobs (global): " + (gf.globalNoMobSpawn ? "ON" : "OFF") + "\n").withStyle(ChatFormatting.GRAY))
                  .append(
                     Component.literal("Captura de chat por paquete: " + (ChatPromptRouter.isPacketCaptureActive() ? "ACTIVA" : "sin usar aún"))
                        .withStyle(ChatFormatting.DARK_GRAY)
                  ),
            false
         );
      return 1;
   }

   private static int globalFlag(CommandContext<CommandSourceStack> ctx) {
      String flag = StringArgumentType.getString(ctx, "flag");
      String value = StringArgumentType.getString(ctx, "value");
      if (!flag.equals("globalPVP") && !flag.equals("globalMobGriefing") && !flag.equals("globalFireSpread") && !flag.equals("globalNoMobSpawn")) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[x] Flag desconocida: " + flag).withStyle(ChatFormatting.RED));
         return 0;
      } else {
         boolean on = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true");
         GlobalFlags.getInstance().set(flag, on, ((CommandSourceStack)ctx.getSource()).getServer());
         ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> Component.literal("✔ " + flag + " = " + (on ? "ON" : "OFF")).withStyle(ChatFormatting.GREEN), true);
         return 1;
      }
   }
}
