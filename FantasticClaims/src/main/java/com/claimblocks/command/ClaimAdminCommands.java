package com.claimblocks.command;

import com.claimblocks.chat.ChatPromptRouter;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimConfig;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.GlobalFlags;
import com.claimblocks.gui.AdminPanelHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class ClaimAdminCommands {
    private static final SuggestionProvider<CommandSourceStack> GLOBAL_FLAGS = (commandcontext, suggestionsbuilder) -> SharedSuggestionProvider.suggest(
            new String[]{"globalPVP", "globalMobGriefing", "globalFireSpread", "globalNoMobSpawn"}, suggestionsbuilder
        );
    private static final SuggestionProvider<CommandSourceStack> ON_OFF = (commandcontext, suggestionsbuilder) -> SharedSuggestionProvider.suggest(
            new String[]{"on", "off"}, suggestionsbuilder
        );

    public static void register(CommandDispatcher<CommandSourceStack> commanddispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("fsclaimadmin");
        literalargumentbuilder.requires(commandsourcestack -> commandsourcestack.hasPermission(2));
        literalargumentbuilder.executes(ClaimAdminCommands::openPanel);
        literalargumentbuilder.then(Commands.literal("bypass").executes(ClaimAdminCommands::toggleBypass));
        literalargumentbuilder.then(Commands.literal("list").executes(ClaimAdminCommands::list));
        literalargumentbuilder.then(Commands.literal("stats").executes(ClaimAdminCommands::stats));
        literalargumentbuilder.then(Commands.literal("reload").executes(ClaimAdminCommands::reload));
        literalargumentbuilder.then(
            Commands.literal("globalflag")
                .then(
                    Commands.argument("flag", StringArgumentType.word())
                        .suggests(GLOBAL_FLAGS)
                        .then(Commands.argument("value", StringArgumentType.word()).suggests(ON_OFF).executes(ClaimAdminCommands::globalFlag))
                )
        );
        commanddispatcher.register(literalargumentbuilder);
    }

    private static int openPanel(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        AdminPanelHandler.open(serverplayer, 0);
        return 1;
    }

    private static int toggleBypass(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        boolean flag = ClaimManager.getInstance().toggleBypass(serverplayer.getUUID());
        ((CommandSourceStack)commandcontext.getSource())
            .sendSuccess(
                () -> Component.literal(flag ? "✔ Bypass ACTIVADO (ignoras protecciones)." : "[i] Bypass desactivado.")
                        .withStyle(flag ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                false
            );
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> commandcontext) {
        List<Claim> list = ClaimManager.getInstance().getAllClaims();
        if (list.isEmpty()) {
            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(() -> Component.literal("[i] No hay zonas en el servidor.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        } else {
            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(
                    () -> {
                        MutableComponent mutablecomponent = Component.literal("=== Zonas (" + list.size() + ") ===\n").withStyle(ChatFormatting.YELLOW);
                        int i = 0;

                        for (Claim claim : list) {
                            if (i++ >= 30) {
                                mutablecomponent.append(Component.literal("... y " + (list.size() - 30) + " más").withStyle(ChatFormatting.GRAY));
                                break;
                            }

                            mutablecomponent.append(
                                Component.literal(claim.getOwnerName() + " - " + claim.sizeLabel() + " @ X=" + claim.getX() + " Z=" + claim.getZ() + "\n")
                                    .withStyle(ChatFormatting.GRAY)
                            );
                        }

                        return mutablecomponent;
                    },
                    false
                );
            return list.size();
        }
    }

    private static int stats(CommandContext<CommandSourceStack> commandcontext) {
        List<Claim> list = ClaimManager.getInstance().getAllClaims();
        GlobalFlags globalflags = GlobalFlags.getInstance();
        ((CommandSourceStack)commandcontext.getSource())
            .sendSuccess(
                () -> Component.literal("=== Stats ClaimBlocks ===\n")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("Total zonas: " + list.size() + "\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("PVP global: " + (globalflags.globalPVP ? "ON" : "OFF") + "\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("MobGriefing: " + (globalflags.globalMobGriefing ? "ON" : "OFF") + "\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("FireSpread: " + (globalflags.globalFireSpread ? "ON" : "OFF") + "\n").withStyle(ChatFormatting.GRAY))
                        .append(
                            Component.literal("Sin spawn de mobs (global): " + (globalflags.globalNoMobSpawn ? "ON" : "OFF") + "\n")
                                .withStyle(ChatFormatting.GRAY)
                        )
                        .append(
                            Component.literal("Captura de chat por paquete: " + (ChatPromptRouter.isPacketCaptureActive() ? "ACTIVA" : "sin usar aún"))
                                .withStyle(ChatFormatting.DARK_GRAY)
                        ),
                false
            );
        return 1;
    }

    private static int globalFlag(CommandContext<CommandSourceStack> commandcontext) {
        String s = StringArgumentType.getString(commandcontext, "flag");
        String s1 = StringArgumentType.getString(commandcontext, "value");
        if (!s.equals("globalPVP") && !s.equals("globalMobGriefing") && !s.equals("globalFireSpread") && !s.equals("globalNoMobSpawn")) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure(Component.literal("[x] Flag desconocida: " + s).withStyle(ChatFormatting.RED));
            return 0;
        } else {
            boolean flag = s1.equalsIgnoreCase("on") || s1.equalsIgnoreCase("true");
            GlobalFlags.getInstance().set(s, flag, ((CommandSourceStack)commandcontext.getSource()).getServer());
            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(() -> Component.literal("✔ " + s + " = " + (flag ? "ON" : "OFF")).withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
    }

    static int reload(CommandContext<CommandSourceStack> commandcontext) {
        boolean flag = ClaimConfig.get().reload();
        ClaimConfig claimconfig = ClaimConfig.get();
        if (!flag) {
            ((CommandSourceStack)commandcontext.getSource())
                .sendFailure(Component.literal("[x] No se pudo recargar la configuracion (revisa la consola).").withStyle(ChatFormatting.RED));
            return 0;
        } else {
            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(
                    () -> Component.literal("✔ Configuracion de Fantastic Claims recargada.")
                            .withStyle(ChatFormatting.GREEN)
                            .append(
                                Component.literal(
                                        "\n  Zonas por jugador: "
                                            + (claimconfig.maxClaimsPerPlayer == 0 ? "sin limite" : String.valueOf(claimconfig.maxClaimsPerPlayer))
                                    )
                                    .withStyle(ChatFormatting.GRAY)
                            )
                            .append(
                                Component.literal(
                                        "\n  Miembros por zona: "
                                            + (claimconfig.maxMembersPerClaim == 0 ? "sin limite" : String.valueOf(claimconfig.maxMembersPerClaim))
                                    )
                                    .withStyle(ChatFormatting.GRAY)
                            )
                            .append(
                                Component.literal(
                                        "\n  Tolvas: "
                                            + (claimconfig.protectHoppers ? "protegido" : "OFF")
                                            + " | Fluidos: "
                                            + (claimconfig.protectFluids ? "protegido" : "OFF")
                                            + " | Decoracion: "
                                            + (claimconfig.protectDecoration ? "protegido" : "OFF")
                                    )
                                    .withStyle(ChatFormatting.GRAY)
                            ),
                    true
                );
            return 1;
        }
    }
}
