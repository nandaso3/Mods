package com.fscrates.command;

import com.fscrates.config.CrateConfig;
import com.fscrates.config.JsonCrateLoader;
import com.fscrates.config.RewardEntry;
import com.fscrates.crate.CrateRegistry;
import com.fscrates.crate.LootEngine;
import com.fscrates.item.CrateItems;
import com.fscrates.network.FSNetwork;
import com.fscrates.network.OpenEditorPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class FSCrateCommand {
    private FSCrateCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder root = Commands.literal("fscrate").requires(s -> s.hasPermission(4)).executes(FSCrateCommand::help);
        root.then(Commands.literal("help").executes(FSCrateCommand::help));
        root.then(Commands.literal("create").executes(FSCrateCommand::create));
        root.then(Commands.literal("list").executes(FSCrateCommand::list));
        root.then(
            ((LiteralArgumentBuilder)Commands.literal("editor").executes(c -> usage(c, "/fscrate editor give <jugador>")))
                .then(
                    ((LiteralArgumentBuilder)Commands.literal("give").executes(c -> usage(c, "/fscrate editor give <jugador>")))
                        .then(Commands.argument("player", EntityArgument.player()).executes(FSCrateCommand::giveEditorWand))
                )
        );
        root.then(
            ((LiteralArgumentBuilder)Commands.literal("give").executes(c -> usage(c, "/fscrate give <jugador> <crate>")))
                .then(
                    ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player()).executes(c -> usage(c, "/fscrate give <jugador> <crate>")))
                        .then(Commands.argument("crate", StringArgumentType.word()).suggests((c, b) -> suggestCrates(c, b)).executes(FSCrateCommand::giveCrate))
                )
        );
        root.then(
            ((LiteralArgumentBuilder)Commands.literal("key").executes(c -> usage(c, "/fscrate key give <jugador> [cantidad]")))
                .then(
                    ((LiteralArgumentBuilder)Commands.literal("give").executes(c -> usage(c, "/fscrate key give <jugador> [cantidad]")))
                        .then(
                            ((RequiredArgumentBuilder)Commands.argument("player", EntityArgument.player()).executes(FSCrateCommand::giveKey))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(FSCrateCommand::giveKey))
                        )
                )
        );
        root.then(
            ((LiteralArgumentBuilder)Commands.literal("preview").executes(c -> usage(c, "/fscrate preview <crate>")))
                .then(Commands.argument("crate", StringArgumentType.word()).suggests((c, b) -> suggestCrates(c, b)).executes(FSCrateCommand::preview))
        );
        root.then(
            ((LiteralArgumentBuilder)Commands.literal("delete").executes(c -> usage(c, "/fscrate delete <crate>")))
                .then(Commands.argument("crate", StringArgumentType.word()).suggests((c, b) -> suggestCrates(c, b)).executes(FSCrateCommand::delete))
        );
        root.then(Commands.literal("reload").executes(FSCrateCommand::reload));
        dispatcher.register(root);
    }

    private static int usage(CommandContext<CommandSourceStack> ctx, String usage) {
        ctx.getSource().sendSystemMessage(Component.literal("\u00a7eUso: \u00a7f" + usage));
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        s.sendSystemMessage(Component.literal("\u00a7d\u2726 \u00a7fFantastic Crates \u00a7d\u2726 \u00a77comandos:"));
        s.sendSystemMessage(Component.literal("\u00a7e/fscrate create \u00a77- crea y abre el editor de una crate nueva"));
        s.sendSystemMessage(
            Component.literal(
                "\u00a7e/fscrate editor give <jugador> \u00a77- da la \u00a7dVarita del Editor \u00a77(click derecho en un cofre para editarlo)"
            )
        );
        s.sendSystemMessage(Component.literal("\u00a7e/fscrate give <jugador> <crate> \u00a77- da el item de la crate"));
        s.sendSystemMessage(Component.literal("\u00a7e/fscrate key give <jugador> [cantidad] \u00a77- da la \u00a7dFantastic Key \u00a77(llave universal)"));
        s.sendSystemMessage(Component.literal("\u00a7e/fscrate preview <crate> \u00a77- simula 5 aperturas"));
        s.sendSystemMessage(Component.literal("\u00a7e/fscrate list \u00a77- lista las crates guardadas"));
        s.sendSystemMessage(Component.literal("\u00a7e/fscrate delete <crate> \u00a77- elimina una crate"));
        s.sendSystemMessage(
            Component.literal("\u00a7e/fscrate reload \u00a77- recarga las cajas desde \u00a7fconfig/fscrates/cajas/*.json")
        );
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestCrates(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            return SharedSuggestionProvider.suggest(CrateRegistry.get(ctx.getSource().getLevel()).ids(), builder);
        } catch (Exception var3) {
            return builder.buildFuture();
        }
    }

    private static int create(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        } else {
            CrateConfig crate = new CrateConfig("crate_" + System.currentTimeMillis() % 100000L);
            crate.rewards.add(exampleItem("minecraft:diamond", 60, "Diamante"));
            crate.rewards.add(exampleItem("minecraft:netherite_ingot", 10, "Netherite"));
            // Deja ya el archivo JSON creado para poder editarlo a mano.
            JsonCrateLoader.saveToFile(crate);
            FSNetwork.sendToClient(player, new OpenEditorPacket(crate.save()));
            return 1;
        }
    }

    private static RewardEntry exampleItem(String id, int chance, String label) {
        RewardEntry r = new RewardEntry(RewardEntry.Type.ITEM);
        r.chance = (double)chance;
        r.label = label;
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        r.item = item == null ? ItemStack.EMPTY : new ItemStack(item);
        return r;
    }

    private static int giveEditorWand(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer target = playerArg(ctx);
        if (target == null) {
            return 0;
        } else {
            ItemStack wand = CrateItems.buildEditorWand();
            if (!target.getInventory().add(wand)) {
                target.drop(wand, false);
            }

            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal(
                            "\u00a7dVarita del Editor\u00a7a entregada a "
                                + target.getName().getString()
                                + ". \u00a77Click derecho sobre un cofre para editarlo."
                        ),
                    true
                );
            return 1;
        }
    }

    private static int giveCrate(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer target = playerArg(ctx);
        if (target == null) {
            return 0;
        } else {
            String id = StringArgumentType.getString(ctx, "crate");
            CrateConfig crate = CrateRegistry.get(target.serverLevel()).get(id);
            if (crate == null) {
                ctx.getSource().sendFailure(Component.literal("No existe la crate '" + id + "'."));
                return 0;
            } else {
                ItemStack item = CrateItems.buildCrate(crate);
                if (!target.getInventory().add(item)) {
                    target.drop(item, false);
                }

                if (crate.uniqueKeyEnabled) {
                    ItemStack uniqueKey = CrateItems.buildUniqueKey(crate);
                    if (!target.getInventory().add(uniqueKey)) {
                        target.drop(uniqueKey, false);
                    }
                }

                ctx.getSource()
                    .sendSuccess(
                        () -> Component.literal(
                                "\u00a7aCrate '"
                                    + id
                                    + "' entregada a "
                                    + target.getName().getString()
                                    + (crate.uniqueKeyEnabled ? " \u00a77(+ su llave \u00fanica)" : "")
                            ),
                        true
                    );
                return 1;
            }
        }
    }

    private static int giveKey(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer target = playerArg(ctx);
        if (target == null) {
            return 0;
        } else {
            int amount = 1;

            try {
                amount = IntegerArgumentType.getInteger(ctx, "amount");
            } catch (IllegalArgumentException var6) {
                amount = 1;
            }

            int remaining = Math.max(1, amount);

            while (remaining > 0) {
                int take = Math.min(remaining, 64);
                remaining -= take;
                ItemStack key = CrateItems.buildKey();
                key.setCount(take);
                if (!target.getInventory().add(key)) {
                    target.drop(key, false);
                }
            }

            int given = Math.max(1, amount);
            ctx.getSource()
                .sendSuccess(
                    () -> Component.literal("\u00a7d\u2726 Fantastic Key \u2726\u00a7a x" + given + " entregada(s) a " + target.getName().getString()), true
                );
            return 1;
        }
    }

    private static int preview(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        } else {
            String id = StringArgumentType.getString(ctx, "crate");
            CrateConfig crate = CrateRegistry.get(player.serverLevel()).get(id);
            if (crate == null) {
                player.sendSystemMessage(Component.literal("\u00a7cNo existe la crate '" + id + "'."));
                return 0;
            } else {
                Random random = new Random();
                player.sendSystemMessage(Component.literal("\u00a7d\u2726 Vista previa de " + LootEngine.colorize(crate.displayName) + "\u00a7r\u00a7d:"));

                for (int i = 0; i < 5; i++) {
                    List<RewardEntry> rolled = LootEngine.roll(crate, random);
                    StringBuilder sb = new StringBuilder("\u00a77- ");

                    for (RewardEntry r : rolled) {
                        sb.append("\u00a7f").append(r.describe()).append("\u00a77, ");
                    }

                    player.sendSystemMessage(Component.literal(sb.toString()));
                }

                return 1;
            }
        }
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        } else {
            String id = StringArgumentType.getString(ctx, "crate");
            boolean removed = CrateRegistry.get(player.serverLevel()).remove(id);
            player.sendSystemMessage(Component.literal(removed ? "\u00a7aCrate '" + id + "' eliminada." : "\u00a7cNo existe la crate '" + id + "'."));
            return removed ? 1 : 0;
        }
    }

    /** Recarga en caliente todos los JSON de config/fscrates/cajas/. */
    private static int reload(CommandContext<CommandSourceStack> ctx) {
        int loaded = JsonCrateLoader.loadAll(ctx.getSource().getServer());
        ctx.getSource()
            .sendSuccess(
                () -> Component.literal(
                        "\u00a7aFantastic Crates: \u00a7fConfiguraci\u00f3n recargada desde JSON \u00a77(" + loaded + " caja(s))."
                    ),
                true
            );
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        } else {
            Set<String> ids = CrateRegistry.get(player.serverLevel()).ids();
            if (ids.isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00a7eNo hay crates. Crea una con \u00a7f/fscrate create"));
                return 1;
            } else {
                player.sendSystemMessage(Component.literal("\u00a7dCrates (" + ids.size() + "): \u00a7f" + String.join(", ", ids)));
                return 1;
            }
        }
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception var2) {
            ctx.getSource().sendFailure(Component.literal("Este comando debe ejecutarlo un jugador."));
            return null;
        }
    }

    private static ServerPlayer playerArg(CommandContext<CommandSourceStack> ctx) {
        try {
            return EntityArgument.getPlayer(ctx, "player");
        } catch (Exception var2) {
            ctx.getSource().sendFailure(Component.literal("Jugador no encontrado."));
            return null;
        }
    }
}
