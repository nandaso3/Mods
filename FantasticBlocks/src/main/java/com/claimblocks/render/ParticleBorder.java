package com.claimblocks.render;

import com.claimblocks.data.Claim;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;

public final class ParticleBorder {
   private static final int RENDER_DISTANCE = 24;

   private ParticleBorder() {
   }

   public static SimpleParticleType particleFor(String id) {
      if (id != null && !id.isEmpty()) {
         String rlStr = id.contains(":") ? id : legacyToRl(id);
         ResourceLocation rl = ResourceLocation.tryParse(rlStr);
         if (rl != null) {
            ParticleType<?> type = (ParticleType<?>)ForgeRegistries.PARTICLE_TYPES.getValue(rl);
            if (type instanceof SimpleParticleType) {
               return (SimpleParticleType)type;
            }
         }

         return ParticleTypes.HAPPY_VILLAGER;
      } else {
         return ParticleTypes.HAPPY_VILLAGER;
      }
   }

   private static String legacyToRl(String shortId) {
      switch (shortId) {
         case "flame":
            return "minecraft:flame";
         case "soul":
            return "minecraft:soul";
         case "heart":
            return "minecraft:heart";
         case "end_rod":
            return "minecraft:end_rod";
         case "crit":
            return "minecraft:crit";
         case "enchant":
            return "minecraft:enchant";
         case "dragon":
            return "minecraft:dragon_breath";
         case "portal":
            return "minecraft:portal";
         case "cloud":
            return "minecraft:cloud";
         case "spark":
            return "minecraft:electric_spark";
         case "wax":
            return "minecraft:wax_on";
         case "happy":
         default:
            return "minecraft:happy_villager";
      }
   }

   public static String[] availableParticles() {
      return new String[]{
         "minecraft:happy_villager",
         "minecraft:heart",
         "minecraft:flame",
         "minecraft:small_flame",
         "minecraft:soul_fire_flame",
         "minecraft:soul",
         "minecraft:end_rod",
         "minecraft:crit",
         "minecraft:enchanted_hit",
         "minecraft:enchant",
         "minecraft:dragon_breath",
         "minecraft:portal",
         "minecraft:reverse_portal",
         "minecraft:cloud",
         "minecraft:electric_spark",
         "minecraft:wax_on",
         "minecraft:glow",
         "minecraft:totem_of_undying",
         "minecraft:firework",
         "minecraft:note",
         "minecraft:snowflake",
         "minecraft:cherry_leaves",
         "minecraft:spore_blossom_air",
         "minecraft:sculk_soul",
         "minecraft:lava",
         "minecraft:splash",
         "minecraft:witch"
      };
   }

   public static String particleLabel(String id) {
      if (id == null) {
         return "Aldeano feliz";
      } else {
         switch (id) {
            case "minecraft:happy_villager":
            case "happy":
               return "Aldeano feliz";
            case "minecraft:heart":
            case "heart":
               return "Corazones";
            case "minecraft:flame":
            case "flame":
               return "Llamas";
            case "minecraft:small_flame":
               return "Llama pequeña";
            case "minecraft:soul_fire_flame":
            case "soul":
               return "Fuego del alma";
            case "minecraft:soul":
               return "Almas";
            case "minecraft:end_rod":
            case "end_rod":
               return "Vara del End";
            case "minecraft:crit":
            case "crit":
               return "Críticos";
            case "minecraft:enchanted_hit":
               return "Golpe encantado";
            case "minecraft:enchant":
            case "enchant":
               return "Encantamiento";
            case "minecraft:dragon_breath":
            case "dragon":
               return "Aliento de dragón";
            case "minecraft:portal":
            case "portal":
               return "Portal";
            case "minecraft:reverse_portal":
               return "Portal inverso";
            case "minecraft:cloud":
            case "cloud":
               return "Nube";
            case "minecraft:electric_spark":
            case "spark":
               return "Chispa eléctrica";
            case "minecraft:wax_on":
            case "wax":
               return "Cera brillante";
            case "minecraft:glow":
               return "Brillo (glow)";
            case "minecraft:totem_of_undying":
               return "Tótem";
            case "minecraft:firework":
               return "Fuegos artificiales";
            case "minecraft:note":
               return "Nota musical";
            case "minecraft:snowflake":
               return "Copo de nieve";
            case "minecraft:cherry_leaves":
               return "Pétalos de cerezo";
            case "minecraft:spore_blossom_air":
               return "Esporas";
            case "minecraft:sculk_soul":
               return "Alma de sculk";
            case "minecraft:lava":
               return "Lava";
            case "minecraft:splash":
               return "Salpicadura";
            case "minecraft:witch":
               return "Bruja";
            default:
               return id.contains(":") ? id.substring(id.indexOf(58) + 1) : id;
         }
      }
   }

   public static void fillClaim(ServerLevel level, ServerPlayer player, Claim claim) {
      SimpleParticleType particle = particleFor(claim.getFlags().borderParticle);
      int density = Math.max(1, Math.min(200, claim.getFlags().particleDensity));
      int r = claim.getRadius();
      int h = claim.getHeight();
      double claimMinX = (double)(claim.getX() - r);
      double claimMaxX = (double)(claim.getX() + r + 1);
      double claimMinZ = (double)(claim.getZ() - r);
      double claimMaxZ = (double)(claim.getZ() + r + 1);
      double claimMinY = (double)(claim.getY() - h);
      double claimMaxY = (double)(claim.getY() + h + 1);
      double loX = Math.max(claimMinX, player.getX() - 24.0);
      double hiX = Math.min(claimMaxX, player.getX() + 24.0);
      double loZ = Math.max(claimMinZ, player.getZ() - 24.0);
      double hiZ = Math.min(claimMaxZ, player.getZ() + 24.0);
      double loY = Math.max(claimMinY, player.getY() - 24.0);
      double hiY = Math.min(claimMaxY, player.getY() + 24.0);
      if (!(loX > hiX) && !(loZ > hiZ) && !(loY > hiY)) {
         RandomSource random = level.getRandom();

         for (int i = 0; i < density; i++) {
            double x = loX + random.nextDouble() * (hiX - loX);
            double y = loY + random.nextDouble() * (hiY - loY);
            double z = loZ + random.nextDouble() * (hiZ - loZ);
            level.sendParticles(player, particle, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
         }
      }
   }

   public static boolean withinRenderRange(ServerPlayer player, Claim claim) {
      double dx = Math.max(0.0, Math.abs(player.getX() - ((double)claim.getX() + 0.5)) - (double)claim.getRadius());
      double dz = Math.max(0.0, Math.abs(player.getZ() - ((double)claim.getZ() + 0.5)) - (double)claim.getRadius());
      return dx <= 24.0 && dz <= 24.0;
   }
}
