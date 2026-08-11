package com.fsrecipes.command;

import com.fsrecipes.BanMode;
import com.fsrecipes.RecipeBans;
import com.fsrecipes.network.Net;
import com.fsrecipes.network.OpenScreenPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * <pre>
 * /fsrecipes                       -> abre la GUI
 * /fsrecipes gui
 * /fsrecipes ban &lt;item&gt;            -> banea solo la receta (compatibilidad 1.0.x)
 * /fsrecipes ban receta &lt;item&gt;     -> banea solo la receta
 * /fsrecipes ban item &lt;item&gt;       -> banea el item completo (receta + item)
 * /fsrecipes unban &lt;item&gt;          -> quita cualquier baneo del item
 * /fsrecipes hand [receta|item|off]
 * /fsrecipes list
 * /fsrecipes clear [recetas|items]
 * </pre>
 */
public final class FSRecipesCommand {
   private FSRecipesCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
      dispatcher.register(
         Commands.literal("fsrecipes")
            .requires(src -> src.hasPermission(2))
            .executes(ctx -> openGui(ctx.getSource()))
            .then(Commands.literal("gui").executes(ctx -> openGui(ctx.getSource())))
            .then(
               Commands.literal("ban")
                  // /fsrecipes ban <item>  (legado: solo receta)
                  .then(
                     Commands.argument("item", ItemArgument.item(buildContext))
                        .executes(ctx -> setBan(ctx.getSource(), ItemArgument.getItem(ctx, "item"), BanMode.RECIPE))
                  )
                  .then(
                     Commands.literal("receta")
                        .then(
                           Commands.argument("item", ItemArgument.item(buildContext))
                              .executes(ctx -> setBan(ctx.getSource(), ItemArgument.getItem(ctx, "item"), BanMode.RECIPE))
                        )
                  )
                  .then(
                     Commands.literal("item")
                        .then(
                           Commands.argument("item", ItemArgument.item(buildContext))
                              .executes(ctx -> setBan(ctx.getSource(), ItemArgument.getItem(ctx, "item"), BanMode.ITEM))
                        )
                  )
            )
            .then(
               Commands.literal("unban")
                  .then(
                     Commands.argument("item", ItemArgument.item(buildContext))
                        .executes(ctx -> setBan(ctx.getSource(), ItemArgument.getItem(ctx, "item"), null))
                  )
            )
            .then(
               Commands.literal("hand")
                  .executes(ctx -> setHand(ctx.getSource(), BanMode.RECIPE))
                  .then(Commands.literal("receta").executes(ctx -> setHand(ctx.getSource(), BanMode.RECIPE)))
                  .then(Commands.literal("item").executes(ctx -> setHand(ctx.getSource(), BanMode.ITEM)))
                  .then(Commands.literal("off").executes(ctx -> setHand(ctx.getSource(), null)))
                  // compatibilidad 1.0.x: /fsrecipes hand <true|false>
                  .then(
                     Commands.argument("ban", BoolArgumentType.bool())
                        .executes(ctx -> setHand(ctx.getSource(), BoolArgumentType.getBool(ctx, "ban") ? BanMode.RECIPE : null))
                  )
            )
            .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
            .then(
               Commands.literal("clear")
                  .executes(ctx -> clear(ctx.getSource(), null))
                  .then(Commands.literal("recetas").executes(ctx -> clear(ctx.getSource(), BanMode.RECIPE)))
                  .then(Commands.literal("items").executes(ctx -> clear(ctx.getSource(), BanMode.ITEM)))
            )
      );
   }

   private static int openGui(CommandSourceStack src) {
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(Component.literal("§cSolo un jugador puede abrir la GUI."));
         return 0;
      } else {
         Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenScreenPacket(RecipeBans.snapshot()));
         return 1;
      }
   }

   private static int setBan(CommandSourceStack src, ItemInput input, BanMode mode) {
      ResourceLocation id = ForgeRegistries.ITEMS.getKey(input.getItem());
      if (id == null) {
         src.sendFailure(Component.literal("§cItem invalido."));
         return 0;
      } else {
         return applyAndReport(src, id, mode);
      }
   }

   private static int setHand(CommandSourceStack src, BanMode mode) {
      ServerPlayer player = src.getPlayer();
      if (player == null) {
         src.sendFailure(Component.literal("§cSolo un jugador puede usar 'hand'."));
         return 0;
      } else if (player.getMainHandItem().isEmpty()) {
         src.sendFailure(Component.literal("§cNo tienes nada en la mano."));
         return 0;
      } else {
         ResourceLocation id = ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());
         if (id == null) {
            src.sendFailure(Component.literal("§cItem invalido."));
            return 0;
         } else {
            return applyAndReport(src, id, mode);
         }
      }
   }

   private static int applyAndReport(CommandSourceStack src, ResourceLocation id, BanMode mode) {
      BanMode before = RecipeBans.mode(id);
      boolean changed = RecipeBans.setBan(src.getServer(), id, mode);

      if (!changed) {
         String estado = before == null ? "sin baneo" : before.display() + "§7";
         src.sendSuccess(() -> Component.literal("§7Sin cambios (" + id + " ya estaba en: " + estado + ")."), false);
         return 0;
      }

      if (mode == null) {
         src.sendSuccess(() -> Component.literal("§6[Recipes] §f" + id + " §adesbaneado§f."), true);
      } else {
         src.sendSuccess(() -> Component.literal("§6[Recipes] §f" + id + " §7-> " + mode.verb() + "§f."), true);
      }

      return 1;
   }

   private static int list(CommandSourceStack src) {
      Map<ResourceLocation, BanMode> bans = RecipeBans.snapshot();
      if (bans.isEmpty()) {
         src.sendSuccess(() -> Component.literal("§7No hay nada baneado."), false);
         return 0;
      }

      List<String> lines = new ArrayList<>(bans.size());
      for (Map.Entry<ResourceLocation, BanMode> e : bans.entrySet()) {
         lines.add(e.getValue().tag() + " §f" + e.getKey());
      }
      Collections.sort(lines);

      int recipes = RecipeBans.recipeBanCount();
      int items = RecipeBans.itemBanCount();
      src.sendSuccess(() -> Component.literal("§6[Recipes] §e" + recipes + " solo-receta §7/ §c" + items + " item completo§7:"), false);
      for (String s : lines) {
         src.sendSuccess(() -> Component.literal(" " + s), false);
      }

      return bans.size();
   }

   private static int clear(CommandSourceStack src, BanMode mode) {
      int n = mode == null ? RecipeBans.clearAll(src.getServer()) : RecipeBans.clearMode(src.getServer(), mode);
      String que = mode == null ? "todos los baneos" : "los baneos de tipo " + mode.display() + "§f";
      src.sendSuccess(() -> Component.literal("§6[Recipes] §aQuitados " + que + " §f(" + n + ")."), true);
      return n;
   }
}
