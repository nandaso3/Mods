package com.fscrates.client.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class CrateStyles {
    public static final String AUTO = "";
    private static final Map<String, CrateStyles.Style> STYLES = new LinkedHashMap<>();

    private CrateStyles() {
    }

    private static ResourceLocation rl(String path) {
        return new ResourceLocation("fscrates", "block/" + path);
    }

    private static void reg(String id, String display, String model, float scale) {
        STYLES.put(id, new CrateStyles.Style(id, display, rl(model), null, null, scale));
    }

    private static void regLid(String id, String display, String model, String lidModel, float[] hinge, float scale) {
        STYLES.put(id, new CrateStyles.Style(id, display, rl(model), rl(lidModel), hinge, scale));
    }

    private static void regCine(String id, String display, String model, String lidModel, float[] hinge, float scale) {
        STYLES.put(id, new CrateStyles.Style(id, display, rl(model), rl(lidModel), hinge, scale, true));
    }

    public static CrateStyles.Style get(String id) {
        return id == null ? null : STYLES.get(id);
    }

    public static Collection<CrateStyles.Style> all() {
        return STYLES.values();
    }

    public static List<String> cycleIds() {
        ArrayList<String> ids = new ArrayList<>();
        ids.add("");
        ids.addAll(STYLES.keySet());
        return ids;
    }

    public static String displayName(String id) {
        CrateStyles.Style s = get(id);
        return s != null ? s.display : "\u00a77Auto (por rareza)";
    }

    static {
        regLid("classic_common", "\u00a7fCofre Cl\u00e1sico Com\u00fan", "crate_common", "crate_common_lid", new float[]{0.5F, 0.55052F, 0.79445F}, 1.9301F);
        regLid("classic_rare", "\u00a7bCofre Cl\u00e1sico Raro", "crate_rare", "crate_rare_lid", new float[]{0.5F, 0.57405F, 0.81105F}, 1.583F);
        regLid("classic_epic", "\u00a7dCofre Cl\u00e1sico \u00c9pico", "crate_epic", "crate_epic_lid", new float[]{0.5F, 0.52543F, 0.91443F}, 1.7538F);
        regLid(
            "classic_legendary",
            "\u00a76Cofre Cl\u00e1sico Legendario",
            "crate_legendary",
            "crate_legendary_lid",
            new float[]{0.47306F, 0.3235F, 0.71127F},
            1.8966F
        );
        regLid("classic_mythic", "\u00a7cCofre Cl\u00e1sico M\u00edtico", "crate_mythic", "crate_mythic_lid", new float[]{0.5F, 0.45352F, 0.84924F}, 1.7909F);
        regLid("crate_lvl1", "\u00a77Cofre de Madera", "crate_lvl1", "crate_lvl1_lid", new float[]{0.5F, 0.5625F, 1.125F}, 0.8299F);
        regLid("crate_lvl2", "\u00a7eCofre Dorado", "crate_lvl2", "crate_lvl2_lid", new float[]{0.5F, 0.60625F, 1.21609F}, 0.7613F);
        regLid("crate_lvl3", "\u00a7bCofre de Diamante", "crate_lvl3", "crate_lvl3_lid", new float[]{0.5F, 0.70865F, 1.19063F}, 0.8F);
        regLid("crate_lvl4", "\u00a7dCofre Arcano", "crate_lvl4", "crate_lvl4_lid", new float[]{0.5F, 0.98539F, 1.59062F}, 0.6047F);
        regLid("elite_icechest", "\u00a7bCofre de Hielo", "elite_icechest", "elite_icechest_lid", new float[]{0.5F, 0.66601F, 0.90869F}, 0.724F);
        regLid("elite_lavachest", "\u00a7cCofre de Lava", "elite_lavachest", "elite_lavachest_lid", new float[]{0.5F, 0.5F, 0.90625F}, 0.8122F);
        regLid(
            "elite_naturechest", "\u00a7aCofre de la Naturaleza", "elite_naturechest", "elite_naturechest_lid", new float[]{0.5F, 0.71014F, 0.79987F}, 0.5935F
        );
        regLid("elite_windchest", "\u00a7fCofre del Viento", "elite_windchest", "elite_windchest_lid", new float[]{0.5F, 0.5F, 1.00241F}, 0.7433F);
        regLid("elite_lovechest", "\u00a7dCofre del Amor", "elite_lovechest", "elite_lovechest_lid", new float[]{0.5F, 0.76326F, 0.72751F}, 1.2242F);
        regLid("dedou_1", "\u00a7fCofre Rareza Com\u00fan", "dedou_1", "dedou_1_lid", new float[]{0.5F, 0.47396F, 1.17708F}, 1.0615F);
        regLid("dedou_2", "\u00a7aCofre Rareza Raro", "dedou_2", "dedou_2_lid", new float[]{0.5F, 0.47396F, 1.17708F}, 1.0615F);
        regLid("dedou_3", "\u00a75Cofre Rareza \u00c9pico", "dedou_3", "dedou_3_lid", new float[]{0.5F, 0.47396F, 1.17708F}, 1.0615F);
        regLid("dedou_4", "\u00a76Cofre Rareza Legendario", "dedou_4", "dedou_4_lid", new float[]{0.5F, 0.19102F, 0.77289F}, 2.6339F);
        regLid("dedou_5", "\u00a7bCofre Rareza Divino", "dedou_5", "dedou_5_lid", new float[]{0.5F, 0.47396F, 1.17708F}, 1.0615F);
        regLid("blackgold_1", "\u00a7eCofre Black & Gold I", "blackgold_1", "blackgold_1_lid", new float[]{0.5F, 0.62725F, 1.20228F}, 0.8047F);
        regLid("blackgold_2", "\u00a7eCofre Black & Gold II", "blackgold_2", "blackgold_2_lid", new float[]{0.5F, 0.49331F, 1.08036F}, 0.6561F);
        regLid("blackgold_3", "\u00a7eCofre Black & Gold III", "blackgold_3", "blackgold_3_lid", new float[]{0.5F, 0.47524F, 0.97674F}, 0.8524F);
        regLid("greek_1b", "\u00a7eCaja Griega I\u00b7A", "greek_1b", "greek_1b_lid", new float[]{0.5F, 0.60938F, 1.3125F}, 0.7946F);
        regLid("greek_1g", "\u00a7eCaja Griega I\u00b7B", "greek_1g", "greek_1g_lid", new float[]{0.5F, 0.60938F, 1.3125F}, 0.7946F);
        regLid("greek_1i", "\u00a7eCaja Griega I\u00b7C", "greek_1i", "greek_1i_lid", new float[]{0.5F, 0.60938F, 1.3125F}, 0.7946F);
        regLid("greek_2b", "\u00a76Caja Griega II\u00b7A", "greek_2b", "greek_2b_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8939F);
        regLid("greek_2g", "\u00a76Caja Griega II\u00b7B", "greek_2g", "greek_2g_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8939F);
        regLid("greek_2i", "\u00a76Caja Griega II\u00b7C", "greek_2i", "greek_2i_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8939F);
        regLid("greek_3b", "\u00a7bCaja Griega III\u00b7A", "greek_3b", "greek_3b_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8492F);
        regLid("greek_3g", "\u00a7bCaja Griega III\u00b7B", "greek_3g", "greek_3g_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8492F);
        regLid("greek_3i", "\u00a7bCaja Griega III\u00b7C", "greek_3i", "greek_3i_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.8492F);
        regLid("greek_4b", "\u00a7dCaja Griega IV\u00b7A", "greek_4b", "greek_4b_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.7077F);
        regLid("greek_4g", "\u00a7dCaja Griega IV\u00b7B", "greek_4g", "greek_4g_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.7077F);
        regLid("greek_4i", "\u00a7dCaja Griega IV\u00b7C", "greek_4i", "greek_4i_lid", new float[]{0.5F, 0.70664F, 1.22222F}, 0.7077F);
        regLid("toffy_explosive", "\u00a7cCofre Explosivo", "toffy_explosive", "toffy_explosive_lid", new float[]{0.5F, 0.6875F, 0.9375F}, 1.0222F);
        regLid("toffy_inhabitant", "\u00a7dCofre Habitante", "toffy_inhabitant", "toffy_inhabitant_lid", new float[]{0.5F, 0.8125F, 0.98331F}, 0.9601F);
        regLid("toffy_owl", "\u00a7eCofre B\u00faho", "toffy_owl", "toffy_owl_lid", new float[]{0.5F, 0.53791F, 1.3125F}, 0.7935F);
        regLid("toffy_piano", "\u00a7bCofre Piano", "toffy_piano", "toffy_piano_lid", new float[]{0.5F, 0.53612F, 1.3125F}, 1.1403F);
        regLid("aquatic_4", "\u00a73Cofre Acu\u00e1tico I", "aquatic_4", "aquatic_4_lid", new float[]{0.5F, 0.48629F, 1.07094F}, 0.8553F);
        regLid("aquatic_5", "\u00a73Cofre Acu\u00e1tico II", "aquatic_5", "aquatic_5_lid", new float[]{0.5F, 0.53366F, 0.94789F}, 0.7697F);
        regLid("aquatic_6", "\u00a73Cofre Acu\u00e1tico III", "aquatic_6", "aquatic_6_lid", new float[]{0.5F, 0.79824F, 1.04167F}, 0.7565F);
        regLid("pirate_1", "\u00a76Cofre Pirata I", "pirate_1", "pirate_1_lid", new float[]{0.5F, 0.34574F, 0.99701F}, 1.3646F);
        regLid("pirate_2", "\u00a76Cofre Pirata II", "pirate_2", "pirate_2_lid", new float[]{0.5F, 0.51704F, 1.20171F}, 0.7242F);
        regLid("pirate_3", "\u00a76Cofre Pirata III", "pirate_3", "pirate_3_lid", new float[]{0.5F, 0.3447F, 1.21401F}, 1.0862F);
        regLid("pirate_4", "\u00a76Cofre Pirata IV", "pirate_4", "pirate_4_lid", new float[]{0.5F, 0.53F, 1.09375F}, 0.9524F);
        regLid("crates1_lvl1", "\u00a77Cofre Nivel I", "crates1_lvl1", "crates1_lvl1_lid", new float[]{0.5F, 0.5F, 1.0625F}, 1.15F);
        regLid("crates1_lvl2", "\u00a7eCofre Nivel II", "crates1_lvl2", "crates1_lvl2_lid", new float[]{0.5F, 0.5625F, 1.0625F}, 0.8659F);
        regLid("crates1_lvl3", "\u00a7bCofre Nivel III", "crates1_lvl3", "crates1_lvl3_lid", new float[]{0.5F, 0.6875F, 1.06228F}, 0.8762F);
        regLid("crates1_lvl4", "\u00a7dCofre Nivel IV", "crates1_lvl4", "crates1_lvl4_lid", new float[]{0.5F, 0.76875F, 0.9375F}, 0.7863F);
        regLid("toro_minotaur", "\u00a74Cofre Jefe Minotauro", "toro_minotaur", "toro_minotaur_lid", new float[]{0.5F, 0.41391F, 1.00589F}, 1.0612F);
        regLid(
            "toro_soulknight", "\u00a78Cofre Jefe Caballero de Almas", "toro_soulknight", "toro_soulknight_lid", new float[]{0.5F, 0.58036F, 1.16741F}, 0.7206F
        );
        regLid("toro_xi", "\u00a75Cofre Jefe Xi", "toro_xi", "toro_xi_lid", new float[]{0.5F, 0.58036F, 1.22835F}, 0.9436F);
        regLid("toro_slimy", "\u00a72Cofre Jefe Slimy", "toro_slimy", "toro_slimy_lid", new float[]{0.5F, 0.4625F, 0.95312F}, 1.2778F);
        regCine("cine_common", "\u00a7fCofre Cinem\u00e1tico Com\u00fan", "cine_common", "cine_common_lid", new float[]{0.5F, 0.67241F, 1.25646F}, 0.6662F);
        regCine("cine_rare", "\u00a7bCofre Cinem\u00e1tico Raro", "cine_rare", "cine_rare_lid", new float[]{0.5F, 0.66102F, 1.22988F}, 0.6777F);
        regCine("cine_epic", "\u00a75Cofre Cinem\u00e1tico \u00c9pico", "cine_epic", "cine_epic_lid", new float[]{0.5F, 0.65F, 1.20594F}, 0.6961F);
        regCine(
            "cine_legendary", "\u00a76Cofre Cinem\u00e1tico Legendario", "cine_legendary", "cine_legendary_lid", new float[]{0.5F, 0.24127F, 1.21127F}, 0.6961F
        );
        regCine(
            "cine_mythical", "\u00a7dCofre Cinem\u00e1tico M\u00edtico", "cine_mythical", "cine_mythical_lid", new float[]{0.5F, 0.51332F, 1.28281F}, 0.6829F
        );
        regCine(
            "cine_ultimate", "\u00a7cCofre Cinem\u00e1tico Definitivo", "cine_ultimate", "cine_ultimate_lid", new float[]{0.5F, 0.47963F, 1.10953F}, 0.8267F
        );
    }

    public static final class Style {
        public final String id;
        public final String display;
        public final ResourceLocation base;
        public final ResourceLocation lid;
        public final float[] hinge;
        public final float scale;
        public final boolean cinematic;

        public Style(String id, String display, ResourceLocation base, ResourceLocation lid, float[] hinge, float scale) {
            this(id, display, base, lid, hinge, scale, false);
        }

        public Style(String id, String display, ResourceLocation base, ResourceLocation lid, float[] hinge, float scale, boolean cinematic) {
            this.id = id;
            this.display = display;
            this.base = base;
            this.lid = lid;
            this.hinge = hinge;
            this.scale = scale;
            this.cinematic = cinematic;
        }

        public boolean hasLid() {
            return this.lid != null;
        }

        public boolean isCinematic() {
            return this.cinematic;
        }
    }
}
