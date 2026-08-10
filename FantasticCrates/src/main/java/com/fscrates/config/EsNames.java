package com.fscrates.config;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class EsNames {
    private static final Map<String, String> ENCH = new HashMap<>();
    private static final Map<String, String> EFFECT = new HashMap<>();
    private static final Map<String, String> ATTR = new HashMap<>();

    private EsNames() {
    }

    private static void e(String id, String es) {
        ENCH.put("minecraft:" + id, es);
    }

    private static void f(String id, String es) {
        EFFECT.put("minecraft:" + id, es);
    }

    private static void a(String id, String es) {
        ATTR.put("minecraft:" + id, es);
    }

    public static String enchant(ResourceLocation rl) {
        if (rl == null) {
            return "(encantamiento)";
        } else {
            String s = ENCH.get(rl.toString());
            return s != null ? s : prettify(rl.getPath());
        }
    }

    public static String effect(ResourceLocation rl) {
        if (rl == null) {
            return "(efecto)";
        } else {
            String s = EFFECT.get(rl.toString());
            return s != null ? s : prettify(rl.getPath());
        }
    }

    public static String attribute(ResourceLocation rl) {
        if (rl == null) {
            return "(atributo)";
        } else {
            String s = ATTR.get(rl.toString());
            return s != null ? s : prettify(rl.getPath().replace('.', '_'));
        }
    }

    public static String attributeByRawId(String rawId) {
        if (rawId != null && !rawId.isEmpty()) {
            String s = ATTR.get(rawId.contains(":") ? rawId : "minecraft:" + rawId);
            return s != null ? s : prettify(rawId.substring(rawId.indexOf(58) + 1).replace('.', '_'));
        } else {
            return "(atributo)";
        }
    }

    public static String prettify(String path) {
        if (path != null && !path.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            boolean cap = true;

            for (char c : path.toCharArray()) {
                if (c != '_' && c != '.') {
                    sb.append(cap ? Character.toUpperCase(c) : c);
                    cap = false;
                } else {
                    sb.append(' ');
                    cap = true;
                }
            }

            return sb.toString();
        } else {
            return "(?)";
        }
    }

    static {
        e("protection", "Protecci\u00f3n");
        e("fire_protection", "Protecci\u00f3n contra el fuego");
        e("feather_falling", "Ca\u00edda de pluma");
        e("blast_protection", "Protecci\u00f3n contra explosiones");
        e("projectile_protection", "Protecci\u00f3n contra proyectiles");
        e("respiration", "Respiraci\u00f3n");
        e("aqua_affinity", "Afinidad acu\u00e1tica");
        e("thorns", "Espinas");
        e("depth_strider", "Agilidad acu\u00e1tica");
        e("frost_walker", "Paso helado");
        e("binding_curse", "Maldici\u00f3n de vinculaci\u00f3n");
        e("soul_speed", "Velocidad del alma");
        e("swift_sneak", "Sigilo veloz");
        e("sharpness", "Filo");
        e("smite", "Golpeo");
        e("bane_of_arthropods", "Perdici\u00f3n de los artr\u00f3podos");
        e("knockback", "Empuje");
        e("fire_aspect", "Aspecto \u00edgneo");
        e("looting", "Bot\u00edn");
        e("sweeping", "Filo de barrido");
        e("sweeping_edge", "Filo de barrido");
        e("efficiency", "Eficiencia");
        e("silk_touch", "Toque de seda");
        e("unbreaking", "Irrompibilidad");
        e("fortune", "Fortuna");
        e("power", "Poder");
        e("punch", "Retroceso");
        e("flame", "Fuego");
        e("infinity", "Infinidad");
        e("luck_of_the_sea", "Suerte marina");
        e("lure", "Atracci\u00f3n");
        e("loyalty", "Lealtad");
        e("impaling", "Empalamiento");
        e("riptide", "Propulsi\u00f3n acu\u00e1tica");
        e("channeling", "Canalizaci\u00f3n");
        e("multishot", "Disparo m\u00faltiple");
        e("quick_charge", "Carga r\u00e1pida");
        e("piercing", "Perforaci\u00f3n");
        e("mending", "Reparaci\u00f3n");
        e("vanishing_curse", "Maldici\u00f3n de desaparici\u00f3n");
        f("speed", "Velocidad");
        f("slowness", "Lentitud");
        f("haste", "Prisa");
        f("mining_fatigue", "Fatiga minera");
        f("strength", "Fuerza");
        f("instant_health", "Curaci\u00f3n instant\u00e1nea");
        f("instant_damage", "Da\u00f1o instant\u00e1neo");
        f("jump_boost", "Salto");
        f("nausea", "N\u00e1useas");
        f("regeneration", "Regeneraci\u00f3n");
        f("resistance", "Resistencia");
        f("fire_resistance", "Resistencia al fuego");
        f("water_breathing", "Respiraci\u00f3n acu\u00e1tica");
        f("invisibility", "Invisibilidad");
        f("blindness", "Ceguera");
        f("night_vision", "Visi\u00f3n nocturna");
        f("hunger", "Hambre");
        f("weakness", "Debilidad");
        f("poison", "Veneno");
        f("wither", "Marchitamiento");
        f("health_boost", "Impulso de salud");
        f("absorption", "Absorci\u00f3n");
        f("saturation", "Saturaci\u00f3n");
        f("glowing", "Brillo");
        f("levitation", "Levitaci\u00f3n");
        f("luck", "Suerte");
        f("unluck", "Mala suerte");
        f("slow_falling", "Ca\u00edda lenta");
        f("conduit_power", "Poder del conducto");
        f("dolphins_grace", "Gracia del delf\u00edn");
        f("bad_omen", "Mal presagio");
        f("hero_of_the_village", "H\u00e9roe de la aldea");
        f("darkness", "Oscuridad");
        a("generic.max_health", "Vida m\u00e1xima");
        a("generic.follow_range", "Rango de seguimiento");
        a("generic.knockback_resistance", "Resistencia al empuje");
        a("generic.movement_speed", "Velocidad de movimiento");
        a("generic.flying_speed", "Velocidad de vuelo");
        a("generic.attack_damage", "Da\u00f1o de ataque");
        a("generic.attack_knockback", "Empuje de ataque");
        a("generic.attack_speed", "Velocidad de ataque");
        a("generic.armor", "Armadura");
        a("generic.armor_toughness", "Dureza de armadura");
        a("generic.luck", "Suerte");
        a("horse.jump_strength", "Fuerza de salto (caballo)");
        a("zombie.spawn_reinforcements", "Refuerzos de zombi");
        a("generic.scale", "Escala (tama\u00f1o)");
        a("generic.step_height", "Altura de paso");
        a("generic.gravity", "Gravedad");
        a("generic.safe_fall_distance", "Distancia de ca\u00edda segura");
        a("generic.fall_damage_multiplier", "Multiplicador de da\u00f1o de ca\u00edda");
        a("generic.jump_strength", "Fuerza de salto");
        a("generic.oxygen_bonus", "Bono de ox\u00edgeno");
        a("generic.burning_time", "Tiempo ardiendo");
        a("generic.explosion_knockback_resistance", "Resistencia a empuje de explosi\u00f3n");
        a("generic.water_movement_efficiency", "Eficiencia de movimiento en agua");
        a("generic.movement_efficiency", "Eficiencia de movimiento");
        a("generic.attack_damage", "Da\u00f1o de ataque");
        a("player.entity_interaction_range", "Alcance de interacci\u00f3n con entidades");
        a("player.block_interaction_range", "Alcance de interacci\u00f3n con bloques");
        a("player.block_break_speed", "Velocidad de romper bloques");
        a("player.mining_efficiency", "Eficiencia de minado");
        a("player.sneaking_speed", "Velocidad al agacharse");
        a("player.submerged_mining_speed", "Velocidad de minado sumergido");
        a("player.sweeping_damage_ratio", "Proporci\u00f3n de da\u00f1o en barrido");
    }
}
