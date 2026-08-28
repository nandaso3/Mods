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
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
    private static final SuggestionProvider<CommandSourceStack> CLAIMSTONE_IDS = (commandcontext, suggestionsbuilder) -> {
        String[] astring = new String[ClaimTier.VALUES.length];

        for (int i = 0; i < ClaimTier.VALUES.length; i++) {
            astring[i] = ClaimTier.VALUES[i].id;
        }

        return SharedSuggestionProvider.suggest(astring, suggestionsbuilder);
    };
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (commandcontext, suggestionsbuilder) -> SharedSuggestionProvider.suggest(
            ((CommandSourceStack)commandcontext.getSource()).getOnlinePlayerNames(), suggestionsbuilder
        );
    private static final SuggestionProvider<CommandSourceStack> MEMBER_NAMES = (commandcontext, suggestionsbuilder) -> {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayer();
        Claim claim;
        return serverplayer != null && (claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition())) != null
            ? SharedSuggestionProvider.suggest(claim.getMemberNames(), suggestionsbuilder)
            : suggestionsbuilder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> commanddispatcher) {
        commanddispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                                "fsclaim"
                                                            )
                                                            .executes(ClaimCommands::help))
                                                        .then(Commands.literal("help").executes(ClaimCommands::help)))
                                                    .then(Commands.literal("menu").executes(ClaimCommands::menu)))
                                                .then(Commands.literal("info").executes(ClaimCommands::info)))
                                            .then(Commands.literal("list").executes(ClaimCommands::list)))
                                        .then(Commands.literal("remove").executes(ClaimCommands::remove)))
                                    .then(
                                        ((LiteralArgumentBuilder)Commands.literal("ban").requires(commandsourcestack -> commandsourcestack.hasPermission(2)))
                                            .then(Commands.argument("jugador", EntityArgument.player()).executes(ClaimCommands::ban))
                                    ))
                                .then(
                                    ((LiteralArgumentBuilder)Commands.literal("unban").requires(commandsourcestack -> commandsourcestack.hasPermission(2)))
                                        .then(Commands.argument("jugador", EntityArgument.player()).executes(ClaimCommands::unban))
                                ))
                            .then(
                                ((LiteralArgumentBuilder)Commands.literal("transfer").requires(commandsourcestack -> commandsourcestack.hasPermission(2)))
                                    .then(Commands.argument("jugador", EntityArgument.player()).executes(ClaimCommands::transfer))
                            ))
                        .then(
                            ((LiteralArgumentBuilder)Commands.literal("removemember").requires(commandsourcestack -> commandsourcestack.hasPermission(2)))
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
                        ((LiteralArgumentBuilder)Commands.literal("give").requires(commandsourcestack -> commandsourcestack.hasPermission(2)))
                            .then(
                                Commands.argument("jugador", EntityArgument.players())
                                    .then(Commands.argument("id", StringArgumentType.word()).suggests(CLAIMSTONE_IDS).executes(ClaimCommands::give))
                            )
                    ))
                .then(
                    ((LiteralArgumentBuilder)Commands.literal("clear").requires(commandsourcestack -> commandsourcestack.hasPermission(2)))
                        .then(Commands.argument("jugador", EntityArgument.player()).executes(ClaimCommands::clear))
                )
        );
    }

    private static int help(CommandContext<CommandSourceStack> commandcontext) {
        boolean flag = ((CommandSourceStack)commandcontext.getSource()).hasPermission(2);
        ((CommandSourceStack)commandcontext.getSource())
            .sendSuccess(
                () -> {
                    MutableComponent mutablecomponent = Component.literal("=== Fantastic Claims ===\n")
                        .withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})
                        .append(Component.literal("/fsclaim menu  ").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("- abre el menu de la zona\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/fsclaim info  ").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("- info de la zona\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/fsclaim list  ").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("- lista tus zonas\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/fsclaim remove  ").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("- borra tu zona actual\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/fsclaim addmember <jugador>  ").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("- añade un miembro (aunque esté offline)\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/fsclaim delmember <jugador>  ").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("- quita un miembro\n").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/fsclaim members  ").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("- lista los miembros de la zona\n").withStyle(ChatFormatting.GRAY));
                    if (flag) {
                        mutablecomponent.append(Component.literal("\n--- Solo Operadores ---\n").withStyle(ChatFormatting.RED))
                            .append(Component.literal("/fsclaim give <jugador> <tier>\n").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("/fsclaim clear <jugador>\n").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("/fsclaim ban|unban <jugador>\n").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("/fsclaim transfer <jugador>\n").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("/fsclaim removemember <jugador>\n").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("/fsclaimadmin  (panel, bypass, list, stats, globalflag)\n").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("/fsclaimadmin reload  - recarga la config").withStyle(ChatFormatting.YELLOW));
                    }

                    return mutablecomponent;
                },
                false
            );
        return 1;
    }

    private static int menu(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource())
                .sendFailure(Component.literal("[x] No estás en ninguna zona protegida.").withStyle(ChatFormatting.RED));
            return 0;
        } else if (!claim.isOwner(serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource())
                .sendFailure(Component.literal("[x] Solo el dueño puede abrir el menu.").withStyle(ChatFormatting.RED));
            return 0;
        } else {
            ClaimMenuHandler.open(serverplayer, claim, 0);
            return 1;
        }
    }

    private static int info(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource())
                .sendFailure(Component.literal("[x] No estás en ninguna zona protegida.").withStyle(ChatFormatting.RED));
            return 0;
        } else {
            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(
                    () -> Component.literal("=== Zona " + claim.sizeLabel() + " ===\n")
                            .withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("Dueño: " + claim.getOwnerName() + "\n").withStyle(ChatFormatting.GRAY))
                            .append(
                                Component.literal("Centro: X=" + claim.getX() + " Y=" + claim.getY() + " Z=" + claim.getZ() + "\n")
                                    .withStyle(ChatFormatting.GRAY)
                            )
                            .append(Component.literal("Miembros: " + claim.getMembers().size()).withStyle(ChatFormatting.GRAY)),
                    false
                );
            return 1;
        }
    }

    private static int list(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        List<Claim> list = ClaimManager.getInstance().getClaimsOf(serverplayer.getUUID());
        if (list.isEmpty()) {
            ((CommandSourceStack)commandcontext.getSource()).sendSuccess(() -> Component.literal("[i] No tienes zonas.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        } else {
            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(
                    () -> {
                        MutableComponent mutablecomponent = Component.literal("=== Tus zonas (" + list.size() + ") ===\n").withStyle(ChatFormatting.YELLOW);

                        for (Claim claim : list) {
                            mutablecomponent.append(
                                Component.literal("- " + claim.sizeLabel() + " en X=" + claim.getX() + " Z=" + claim.getZ() + " (" + claim.getWorld() + ")\n")
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

    private static int remove(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource())
                .sendFailure(Component.literal("[x] No estás en ninguna zona protegida.").withStyle(ChatFormatting.RED));
            return 0;
        } else if (!claim.isOwner(serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource())
                .sendFailure(Component.literal("[x] Solo el dueño puede eliminar esta zona.").withStyle(ChatFormatting.RED));
            return 0;
        } else {
            BlockPos blockpos = claim.getCenter();
            ClaimTier claimtier = claim.getTier();
            if (claimtier != null && ClaimBlocks.isClaimConcreteForTier(serverplayer.level().getBlockState(blockpos).getBlock(), claimtier)) {
                serverplayer.level().destroyBlock(blockpos, false);
            }

            ClaimManager.getInstance().removeClaim(serverplayer.level(), blockpos);
            serverplayer.level().playSound(null, blockpos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 1.0F);
            if (claimtier != null) {
                ItemStack itemstack = ClaimBlocks.createTierItem(claimtier, 1);
                if (!serverplayer.getInventory().add(itemstack)) {
                    serverplayer.drop(itemstack, false);
                }
            }

            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(() -> Component.literal("✔ Zona eliminada. Protección devuelta a tu inventario.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
    }

    private static int ban(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        ServerPlayer serverplayer1 = EntityArgument.getPlayer(commandcontext, "jugador");
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure(Component.literal("[x] No estás en ninguna zona.").withStyle(ChatFormatting.RED));
            return 0;
        } else if (!claim.isOwner(serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure(Component.literal("[x] Solo el dueño puede banear.").withStyle(ChatFormatting.RED));
            return 0;
        } else if (serverplayer1.hasPermissions(2) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure(Component.literal("[x] No puedes banear a un operador.").withStyle(ChatFormatting.RED));
            return 0;
        } else {
            claim.banPlayer(serverplayer1.getUUID());
            ClaimManager.getInstance().save();
            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(() -> Component.literal("✔ " + serverplayer1.getName().getString() + " baneado.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
    }

    private static int unban(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        ServerPlayer serverplayer1 = EntityArgument.getPlayer(commandcontext, "jugador");
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure(Component.literal("[x] No estás en ninguna zona.").withStyle(ChatFormatting.RED));
            return 0;
        } else if (!claim.isOwner(serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure(Component.literal("[x] Solo el dueño puede desbanear.").withStyle(ChatFormatting.RED));
            return 0;
        } else {
            claim.unbanPlayer(serverplayer1.getUUID());
            ClaimManager.getInstance().save();
            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(() -> Component.literal("✔ " + serverplayer1.getName().getString() + " desbaneado.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
    }

    private static int transfer(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        ServerPlayer serverplayer1 = EntityArgument.getPlayer(commandcontext, "jugador");
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure(Component.literal("[x] No estás en ninguna zona.").withStyle(ChatFormatting.RED));
            return 0;
        } else if (!claim.isOwner(serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure(Component.literal("[x] Solo el dueño puede transferir.").withStyle(ChatFormatting.RED));
            return 0;
        } else if (serverplayer1.hasPermissions(2) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource())
                .sendFailure(Component.literal("[x] No puedes transferir a un operador.").withStyle(ChatFormatting.RED));
            return 0;
        } else if (claim.isOwner(serverplayer1.getUUID())) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure(Component.literal("[x] Ya es el dueño actual.").withStyle(ChatFormatting.RED));
            return 0;
        } else {
            ClaimManager.getInstance().transferOwnership(claim, serverplayer1.getUUID(), serverplayer1.getName().getString());
            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(() -> Component.literal("✔ Zona transferida a " + serverplayer1.getName().getString()).withStyle(ChatFormatting.GREEN), true);
            serverplayer1.displayClientMessage(
                Component.literal("[Protección] Has recibido la propiedad de una zona en X=" + claim.getX() + " Z=" + claim.getZ())
                    .withStyle(ChatFormatting.GREEN),
                false
            );
            return 1;
        }
    }

    private static Claim ownedClaimAt(CommandSourceStack commandsourcestack, ServerPlayer serverplayer) {
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            commandsourcestack.sendFailure(Component.literal("[x] No estás en ninguna zona protegida.").withStyle(ChatFormatting.RED));
            return null;
        } else if (!claim.isOwner(serverplayer) && !serverplayer.hasPermissions(2)) {
            commandsourcestack.sendFailure(Component.literal("[x] Solo el dueño puede gestionar los miembros de esta zona.").withStyle(ChatFormatting.RED));
            return null;
        } else {
            return claim;
        }
    }

    private static int addMember(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ownedClaimAt((CommandSourceStack)commandcontext.getSource(), serverplayer);
        if (claim == null) {
            return 0;
        } else {
            String s = StringArgumentType.getString(commandcontext, "jugador");
            return ClaimMenuHandler.addMemberByName(serverplayer, claim, s, 0, false) ? 1 : 0;
        }
    }

    private static int delMember(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ownedClaimAt((CommandSourceStack)commandcontext.getSource(), serverplayer);
        if (claim == null) {
            return 0;
        } else {
            String s = StringArgumentType.getString(commandcontext, "jugador");
            return ClaimMenuHandler.removeMemberByName(serverplayer, claim, s, 0, false) ? 1 : 0;
        }
    }

    private static int members(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        Claim claim = ownedClaimAt((CommandSourceStack)commandcontext.getSource(), serverplayer);
        if (claim == null) {
            return 0;
        } else {
            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(
                    () -> {
                        MutableComponent mutablecomponent = Component.literal(
                                "=== Miembros de la zona de " + claim.getOwnerName() + " (" + claim.getMembers().size() + ") ===\n"
                            )
                            .withStyle(ChatFormatting.YELLOW);
                        if (claim.getMembers().isEmpty()) {
                            mutablecomponent.append(Component.literal("(sin miembros)").withStyle(ChatFormatting.DARK_GRAY));
                        } else {
                            for (int i = 0; i < claim.getMembers().size(); i++) {
                                String s = i < claim.getMemberNames().size() ? claim.getMemberNames().get(i) : claim.getMembers().get(i).toString();
                                mutablecomponent.append(Component.literal("- " + s + "\n").withStyle(ChatFormatting.WHITE));
                            }
                        }

                        return mutablecomponent;
                    },
                    false
                );
            return claim.getMembers().size();
        }
    }

    private static int removeMember(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
        ServerPlayer serverplayer1 = EntityArgument.getPlayer(commandcontext, "jugador");
        Claim claim = ClaimManager.getInstance().getClaimAt(serverplayer.level(), serverplayer.blockPosition());
        if (claim == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure(Component.literal("[x] No estás en ninguna zona.").withStyle(ChatFormatting.RED));
            return 0;
        } else if (!claim.isOwner(serverplayer) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource())
                .sendFailure(Component.literal("[x] Solo el dueño puede gestionar miembros.").withStyle(ChatFormatting.RED));
            return 0;
        } else if (serverplayer1.hasPermissions(2) && !serverplayer.hasPermissions(2)) {
            ((CommandSourceStack)commandcontext.getSource())
                .sendFailure(Component.literal("[x] No puedes gestionar a un operador.").withStyle(ChatFormatting.RED));
            return 0;
        } else if (!claim.isMember(serverplayer1.getUUID())) {
            ((CommandSourceStack)commandcontext.getSource())
                .sendFailure(Component.literal("[x] " + serverplayer1.getName().getString() + " no es miembro.").withStyle(ChatFormatting.RED));
            return 0;
        } else {
            claim.removeMember(serverplayer1.getUUID());
            ClaimManager.getInstance().save();
            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(
                    () -> Component.literal("✔ " + serverplayer1.getName().getString() + " eliminado de la zona.").withStyle(ChatFormatting.GREEN), true
                );
            return 1;
        }
    }

    private static int give(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        String s = StringArgumentType.getString(commandcontext, "id");
        ClaimTier claimtier = ClaimTier.byId(s);
        if (claimtier == null) {
            ((CommandSourceStack)commandcontext.getSource()).sendFailure(Component.literal("[x] ID no válido: " + s).withStyle(ChatFormatting.RED));
            return 0;
        } else {
            Collection<ServerPlayer> collection = EntityArgument.getPlayers(commandcontext, "jugador");

            for (ServerPlayer serverplayer : collection) {
                ItemStack itemstack = ClaimBlocks.createTierItem(claimtier, 1);
                if (!serverplayer.getInventory().add(itemstack)) {
                    serverplayer.drop(itemstack, false);
                }

                serverplayer.displayClientMessage(Component.literal("[+] Recibiste Protección " + claimtier.label()).withStyle(ChatFormatting.GREEN), false);
            }

            ((CommandSourceStack)commandcontext.getSource())
                .sendSuccess(
                    () -> Component.literal("✔ Protección " + claimtier.label() + " entregada a " + collection.size() + " jugador(es).")
                            .withStyle(ChatFormatting.GREEN),
                    true
                );
            return collection.size();
        }
    }

    private static int clear(CommandContext<CommandSourceStack> commandcontext) throws CommandSyntaxException {
        ServerPlayer serverplayer = EntityArgument.getPlayer(commandcontext, "jugador");
        int i = ClaimManager.getInstance().clearClaimsOf(serverplayer.getUUID());
        ((CommandSourceStack)commandcontext.getSource())
            .sendSuccess(
                () -> Component.literal("✔ Eliminadas " + i + " zona(s) de " + serverplayer.getName().getString()).withStyle(ChatFormatting.GREEN), true
            );
        return i;
    }
}
