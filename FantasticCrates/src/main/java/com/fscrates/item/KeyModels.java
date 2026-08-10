package com.fscrates.item;

import java.util.ArrayList;
import java.util.List;

public final class KeyModels {
    public static final List<KeyModels.Entry> ALL = new ArrayList<>();

    private static void a(String id, String name, String group) {
        ALL.add(new KeyModels.Entry(id, name, group, ALL.size() + 1));
    }

    public static KeyModels.Entry byId(String id) {
        if (id == null) {
            return null;
        } else {
            for (KeyModels.Entry e : ALL) {
                if (e.id.equals(id)) {
                    return e;
                }
            }

            return null;
        }
    }

    public static KeyModels.Entry byCmd(int cmd) {
        for (KeyModels.Entry e : ALL) {
            if (e.cmd == cmd) {
                return e;
            }
        }

        return null;
    }

    public static KeyModels.Entry first() {
        return ALL.isEmpty() ? null : ALL.get(0);
    }

    private KeyModels() {
    }

    static {
        a("cp1_lvl1", "Llave Comun", "Crates Pack");
        a("cp1_lvl2", "Llave Rara", "Crates Pack");
        a("cp1_lvl3", "Llave Epica", "Crates Pack");
        a("cp1_lvl4", "Llave Legendaria", "Crates Pack");
        a("aq_legendary", "Llave Legendaria Acuatica", "AquaticCrates");
        a("aq_mythic", "Llave Mitica Acuatica", "AquaticCrates");
        a("aq_vote", "Llave de Voto Acuatica", "AquaticCrates");
        a("bg_key1", "Llave BlackGold I", "BlackGold");
        a("bg_key2", "Llave BlackGold II", "BlackGold");
        a("bg_key3", "Llave BlackGold III", "BlackGold");
        a("cp2_lvl1", "Llave Comun v2", "Crates Pack v2");
        a("cp2_lvl2", "Llave Rara v2", "Crates Pack v2");
        a("cp2_lvl3", "Llave Epica v2", "Crates Pack v2");
        a("cp2_lvl4", "Llave Legendaria v2", "Crates Pack v2");
        a("dd_common", "Llave Comun (Dedou)", "Dedou3D");
        a("dd_rare", "Llave Rara (Dedou)", "Dedou3D");
        a("dd_epic", "Llave Epica (Dedou)", "Dedou3D");
        a("dd_legendary", "Llave Legendaria (Dedou)", "Dedou3D");
        a("dd_divine", "Llave Divina (Dedou)", "Dedou3D");
        a("ec_ice", "Llave de Hielo", "EliteCreatures");
        a("ec_lava", "Llave de Lava", "EliteCreatures");
        a("ec_nature", "Llave de Naturaleza", "EliteCreatures");
        a("ec_valentine", "Llave de San Valentin", "EliteCreatures");
        a("ec_wind", "Llave de Viento", "EliteCreatures");
        a("gb_k1b", "Llave Griega I Bronce", "GreekBox");
        a("gb_k1g", "Llave Griega I Oro", "GreekBox");
        a("gb_k1i", "Llave Griega I Hierro", "GreekBox");
        a("gb_k2b", "Llave Griega II Bronce", "GreekBox");
        a("gb_k2g", "Llave Griega II Oro", "GreekBox");
        a("gb_k2i", "Llave Griega II Hierro", "GreekBox");
        a("gb_k3b", "Llave Griega III Bronce", "GreekBox");
        a("gb_k3g", "Llave Griega III Oro", "GreekBox");
        a("gb_k3i", "Llave Griega III Hierro", "GreekBox");
        a("gb_k4b", "Llave Griega IV Bronce", "GreekBox");
        a("gb_k4g", "Llave Griega IV Oro", "GreekBox");
        a("gb_k4i", "Llave Griega IV Hierro", "GreekBox");
        a("tf_explosive", "Llave Explosiva", "Toffy");
        a("tf_inhabitant", "Llave del Habitante", "Toffy");
        a("tf_owl", "Llave del Buho", "Toffy");
        a("tf_piano", "Llave del Piano", "Toffy");
        a("pr_key1", "Llave Pirata I", "Pirate");
        a("pr_key2", "Llave Pirata II", "Pirate");
        a("pr_key3", "Llave Pirata III", "Pirate");
        a("pr_key4", "Llave Pirata IV", "Pirate");
        a("w6_common", "Llave Comun (Cinematic)", "W6 Cinematic");
        a("w6_rare", "Llave Rara (Cinematic)", "W6 Cinematic");
        a("w6_epic", "Llave Epica (Cinematic)", "W6 Cinematic");
        a("w6_legendary", "Llave Legendaria (Cinematic)", "W6 Cinematic");
        a("w6_mythical", "Llave Mitica (Cinematic)", "W6 Cinematic");
        a("fsc_common", "Llave Comun (FSCrates)", "FSCrates");
        a("fsc_rare", "Llave Rara (FSCrates)", "FSCrates");
        a("fsc_epic", "Llave Epica (FSCrates)", "FSCrates");
        a("fsc_legendary", "Llave Legendaria (FSCrates)", "FSCrates");
        a("fsc_mythic", "Llave Mitica (FSCrates)", "FSCrates");
    }

    public static final class Entry {
        public final String id;
        public final String defaultName;
        public final String group;
        public final int cmd;

        Entry(String id, String defaultName, String group, int cmd) {
            this.id = id;
            this.defaultName = defaultName;
            this.group = group;
            this.cmd = cmd;
        }
    }
}
