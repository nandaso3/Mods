package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.render.ParticleBorder;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkHooks;

public class ClaimParticleMenuHandler extends ChestMenu {
   private static final int[] PARTICLE_SLOTS = new int[]{
      9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35
   };
   private static final int SLOT_TOGGLE = 4;
   private static final int SLOT_DENSITY_DOWN = 48;
   private static final int SLOT_DENSITY_INFO = 49;
   private static final int SLOT_DENSITY_UP = 50;
   private static final int SLOT_BACK = 45;
   private final SimpleContainer chest;
   private final Claim claim;
   private final ServerPlayer viewer;
   private final int returnPage;

   public ClaimParticleMenuHandler(int syncId, Inventory pInv, Claim claim, int returnPage) {
      this(syncId, pInv, new SimpleContainer(54), claim, returnPage);
   }

   private ClaimParticleMenuHandler(int syncId, Inventory pInv, SimpleContainer chest, Claim claim, int returnPage) {
      super(MenuType.GENERIC_9x6, syncId, pInv, chest, 6);
      this.chest = chest;
      this.claim = claim;
      this.viewer = (ServerPlayer)pInv.player;
      this.returnPage = returnPage;
      this.rebuild();
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

      boolean on = this.claim.getFlags().showParticles;
      this.chest
         .setItem(
            4,
            withLore(
               withName(
                  new ItemStack(on ? Items.LIME_DYE : Items.GRAY_DYE),
                  Component.literal(on ? "Partículas: ACTIVAS" : "Partículas: INACTIVAS")
                     .withStyle(new ChatFormatting[]{on ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD})
               ),
               List.of(
                  Component.literal("Llena tu protección con partículas.").withStyle(ChatFormatting.GRAY),
                  Component.literal("Clic para " + (on ? "desactivar" : "activar")).withStyle(ChatFormatting.YELLOW)
               )
            )
         );
      String current = this.claim.getFlags().borderParticle;
      String[] particles = ParticleBorder.availableParticles();

      for (int i = 0; i < particles.length && i < PARTICLE_SLOTS.length; i++) {
         String pid = particles[i];
         boolean selected = pid.equals(current);
         this.chest
            .setItem(
               PARTICLE_SLOTS[i],
               withLore(
                  withName(
                     new ItemStack(iconFor(pid)),
                     Component.literal(ParticleBorder.particleLabel(pid))
                        .withStyle(new ChatFormatting[]{selected ? ChatFormatting.GREEN : ChatFormatting.AQUA, ChatFormatting.BOLD})
                  ),
                  List.of(
                     Component.literal(selected ? "✔ Partícula seleccionada" : "Clic para usar esta partícula")
                        .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                     Component.literal("Activa las partículas automáticamente").withStyle(ChatFormatting.DARK_GRAY)
                  )
               )
            );
      }

      int density = this.claim.getFlags().particleDensity;
      this.chest.setItem(48, withName(new ItemStack(Items.REDSTONE), Component.literal("- Menos partículas (-5)").withStyle(ChatFormatting.RED)));
      this.chest
         .setItem(
            49,
            withLore(
               withName(
                  new ItemStack(Items.GLOWSTONE_DUST),
                  Component.literal("Densidad: " + density).withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})
               ),
               List.of(
                  Component.literal("Cantidad de partículas por emisión.").withStyle(ChatFormatting.GRAY),
                  Component.literal("Rango 1 - 200. Recomendado 5 - 40.").withStyle(ChatFormatting.DARK_GRAY)
               )
            )
         );
      this.chest.setItem(50, withName(new ItemStack(Items.GLOWSTONE), Component.literal("+ Más partículas (+5)").withStyle(ChatFormatting.GREEN)));
      this.chest.setItem(45, withName(new ItemStack(Items.ARROW), Component.literal("<< Volver").withStyle(ChatFormatting.AQUA)));
      this.broadcastChanges();
   }

   private static Item iconFor(String pid) {
      switch (pid) {
         case "minecraft:heart":
            return Items.POPPY;
         case "minecraft:flame":
            return Items.BLAZE_POWDER;
         case "minecraft:small_flame":
            return Items.TORCH;
         case "minecraft:soul_fire_flame":
            return Items.SOUL_TORCH;
         case "minecraft:soul":
            return Items.SOUL_LANTERN;
         case "minecraft:end_rod":
            return Items.END_ROD;
         case "minecraft:crit":
            return Items.IRON_SWORD;
         case "minecraft:enchanted_hit":
            return Items.DIAMOND_SWORD;
         case "minecraft:enchant":
            return Items.ENCHANTED_BOOK;
         case "minecraft:dragon_breath":
            return Items.DRAGON_BREATH;
         case "minecraft:portal":
            return Items.ENDER_PEARL;
         case "minecraft:reverse_portal":
            return Items.ENDER_EYE;
         case "minecraft:cloud":
            return Items.WHITE_WOOL;
         case "minecraft:electric_spark":
            return Items.AMETHYST_SHARD;
         case "minecraft:wax_on":
            return Items.HONEYCOMB;
         case "minecraft:glow":
            return Items.GLOW_INK_SAC;
         case "minecraft:totem_of_undying":
            return Items.TOTEM_OF_UNDYING;
         case "minecraft:firework":
            return Items.FIREWORK_ROCKET;
         case "minecraft:note":
            return Items.NOTE_BLOCK;
         case "minecraft:snowflake":
            return Items.SNOWBALL;
         case "minecraft:cherry_leaves":
            return Items.CHERRY_LEAVES;
         case "minecraft:spore_blossom_air":
            return Items.SPORE_BLOSSOM;
         case "minecraft:sculk_soul":
            return Items.SCULK_CATALYST;
         case "minecraft:lava":
            return Items.LAVA_BUCKET;
         case "minecraft:splash":
            return Items.WATER_BUCKET;
         case "minecraft:witch":
            return Items.FERMENTED_SPIDER_EYE;
         case "minecraft:happy_villager":
         default:
            return Items.EMERALD;
      }
   }

   public void clicked(int slotId, int button, ClickType clickType, Player player) {
      if (slotId >= 0 && slotId < 54) {
         if (slotId == 4) {
            this.claim.getFlags().showParticles = !this.claim.getFlags().showParticles;
            ClaimManager.getInstance().save();
            this.rebuild();
         } else if (slotId == 45) {
            ClaimMenuHandler.open(this.viewer, this.claim, this.returnPage);
         } else if (slotId != 48 && slotId != 50) {
            String[] particles = ParticleBorder.availableParticles();

            for (int i = 0; i < PARTICLE_SLOTS.length && i < particles.length; i++) {
               if (PARTICLE_SLOTS[i] == slotId) {
                  this.claim.getFlags().borderParticle = particles[i];
                  this.claim.getFlags().showParticles = true;
                  ClaimManager.getInstance().save();
                  this.viewer.displayClientMessage(Component.literal("✔ Partícula: " + ParticleBorder.particleLabel(particles[i])).withStyle(ChatFormatting.GREEN), true);
                  this.rebuild();
                  return;
               }
            }
         } else {
            int d = this.claim.getFlags().particleDensity + (slotId == 50 ? 5 : -5);
            this.claim.getFlags().particleDensity = Math.max(1, Math.min(200, d));
            ClaimManager.getInstance().save();
            this.rebuild();
         }
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

   public static void open(ServerPlayer player, final Claim claim, final int returnPage) {
      NetworkHooks.openScreen(player, new MenuProvider() {
         public Component getDisplayName() {
            return Component.literal("Partículas de la protección").withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD});
         }

         public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
            return new ClaimParticleMenuHandler(id, inv, claim, returnPage);
         }
      });
   }
}
