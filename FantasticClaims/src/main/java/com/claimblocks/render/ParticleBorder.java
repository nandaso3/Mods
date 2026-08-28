package com.claimblocks.render;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimConfig;
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

    public static SimpleParticleType particleFor(String s) {
        if (s != null && !s.isEmpty()) {
            String s1 = s.contains(":") ? s : legacyToRl(s);
            ResourceLocation resourcelocation = ResourceLocation.tryParse(s1);
            ParticleType particletype;
            return resourcelocation != null
                    && (particletype = (ParticleType)ForgeRegistries.PARTICLE_TYPES.getValue(resourcelocation)) instanceof SimpleParticleType
                ? (SimpleParticleType)particletype
                : ParticleTypes.HAPPY_VILLAGER;
        } else {
            return ParticleTypes.HAPPY_VILLAGER;
        }
    }

    private static String legacyToRl(String s) {
        switch (s) {
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

    public static String particleLabel(String s) {
        if (s == null) {
            return "Aldeano feliz";
        } else {
            switch (s) {
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
                    return s.contains(":") ? s.substring(s.indexOf(58) + 1) : s;
            }
        }
    }

    public static void fillClaim(ServerLevel serverlevel, ServerPlayer serverplayer, Claim claim) {
        SimpleParticleType simpleparticletype = particleFor(claim.getFlags().borderParticle);
        int i = Math.max(1, Math.min(200, claim.getFlags().particleDensity));
        int j = claim.getRadius();
        int k = claim.getHeight();
        double d0 = (double)(claim.getX() - j);
        double d1 = (double)(claim.getX() + j + 1);
        double d2 = (double)(claim.getZ() - j);
        double d3 = (double)(claim.getZ() + j + 1);
        double d4 = (double)(claim.getY() - k);
        double d5 = (double)(claim.getY() + k + 1);
        double d6 = Math.max(d0, serverplayer.getX() - 24.0);
        double d7 = Math.min(d1, serverplayer.getX() + 24.0);
        double d8 = Math.max(d2, serverplayer.getZ() - 24.0);
        double d9 = Math.min(d3, serverplayer.getZ() + 24.0);
        double d10 = Math.max(d4, serverplayer.getY() - 24.0);
        double d11 = Math.min(d5, serverplayer.getY() + 24.0);
        if (!(d6 > d7) && !(d8 > d9) && !(d10 > d11)) {
            RandomSource randomsource = serverlevel.getRandom();

            for (int l = 0; l < i; l++) {
                double d12 = d6 + randomsource.nextDouble() * (d7 - d6);
                double d13 = d10 + randomsource.nextDouble() * (d11 - d10);
                double d14 = d8 + randomsource.nextDouble() * (d9 - d8);
                serverlevel.sendParticles(serverplayer, simpleparticletype, true, d12, d13, d14, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    public static boolean withinRenderRange(ServerPlayer serverplayer, Claim claim) {
        double d0 = Math.max(0.0, Math.abs(serverplayer.getX() - ((double)claim.getX() + 0.5)) - (double)claim.getRadius());
        double d1 = Math.max(0.0, Math.abs(serverplayer.getZ() - ((double)claim.getZ() + 0.5)) - (double)claim.getRadius());
        double d2 = (double)ClaimConfig.get().particleRenderDistance;
        return d0 <= d2 && d1 <= d2;
    }
}
