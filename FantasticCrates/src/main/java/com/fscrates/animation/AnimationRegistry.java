package com.fscrates.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnimationRegistry {
    private static final Map<String, CrateAnimation> REGISTRY = new LinkedHashMap<>();

    private AnimationRegistry() {
    }

    private static void reg(String id, String name, CrateAnimation.Style style, CrateAnimation.Theme theme, int duration, String desc) {
        REGISTRY.put(id, new CrateAnimation(id, name, style, theme, duration, desc));
    }

    public static CrateAnimation register(String id, String name, CrateAnimation.Style style, CrateAnimation.Theme theme, int duration, String desc) {
        CrateAnimation a = new CrateAnimation(id, name, style, theme, duration, desc);
        REGISTRY.put(id, a);
        return a;
    }

    public static CrateAnimation get(String id) {
        return REGISTRY.getOrDefault(id, REGISTRY.get("roulette"));
    }

    public static boolean exists(String id) {
        return REGISTRY.containsKey(id);
    }

    public static List<CrateAnimation> all() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }

    public static List<String> ids() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.keySet()));
    }

    public static String defaultId() {
        return "roulette";
    }

    static {
        reg(
            "roulette",
            "Ruleta cl\u00e1sica",
            CrateAnimation.Style.ROULETTE,
            CrateAnimation.Theme.CLASSIC,
            440,
            "Carrusel horizontal de items que desacelera hasta el premio."
        );
        reg(
            "roulette_fast",
            "Ruleta r\u00e1pida",
            CrateAnimation.Style.ROULETTE,
            CrateAnimation.Theme.CLASSIC,
            348,
            "Como la cl\u00e1sica pero m\u00e1s corta."
        );
        reg("roulette_casino", "Ruleta casino", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.CASINO, 485, "Ruleta con confeti y fuegos al ganar.");
        reg("roulette_neon", "Ruleta ne\u00f3n", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.NEON, 485, "Ruleta con haz de luz de ne\u00f3n.");
        reg("roulette_infernal", "Ruleta infernal", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.INFERNAL, 485, "Ruleta con llamas y haz rojo.");
        reg(
            "roulette_celestial",
            "Ruleta celestial",
            CrateAnimation.Style.ROULETTE,
            CrateAnimation.Theme.CELESTIAL,
            501,
            "Ruleta con haz de luz divino y destellos."
        );
        reg(
            "roulette_arcane",
            "Ruleta arcana",
            CrateAnimation.Style.ROULETTE,
            CrateAnimation.Theme.MAGIC,
            501,
            "Ruleta con energ\u00eda m\u00e1gica y haz morado."
        );
        reg("roulette_ancient", "Ruleta ancestral", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.ANCIENT, 501, "Ruleta con runas y haz dorado.");
        reg("roulette_nature", "Ruleta natural", CrateAnimation.Style.ROULETTE, CrateAnimation.Theme.NATURE, 469, "Ruleta con esporas y p\u00e9talos.");
        reg("slot", "Tragamonedas", CrateAnimation.Style.SLOT_MACHINE, CrateAnimation.Theme.CASINO, 485, "Carrete vertical que para en el premio.");
        reg(
            "slot_jackpot",
            "Tragamonedas jackpot",
            CrateAnimation.Style.SLOT_MACHINE,
            CrateAnimation.Theme.CASINO,
            538,
            "Carrete vertical con fuegos artificiales al ganar."
        );
        reg(
            "slot_celestial",
            "Tragamonedas celestial",
            CrateAnimation.Style.SLOT_MACHINE,
            CrateAnimation.Theme.CELESTIAL,
            513,
            "Carrete vertical con haz de luz."
        );
        reg("instant", "Instant\u00e1neo", CrateAnimation.Style.INSTANT, CrateAnimation.Theme.CLASSIC, 1, "Sin animaci\u00f3n: entrega inmediata.");
    }
}
