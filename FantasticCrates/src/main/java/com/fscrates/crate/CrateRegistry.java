package com.fscrates.crate;

import com.fscrates.config.CrateConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class CrateRegistry extends SavedData {
    public static final String NAME = "fscrates_definitions";

    /**
     * Sube cada vez que se recargan definiciones (por ejemplo con
     * /fscrate reload). Las cajas ya colocadas guardan su propia copia del
     * config, asi que lo miran para saber que tienen que refrescarse.
     */
    private static volatile int generation;

    public static int generation() {
        return generation;
    }

    public static void bumpGeneration() {
        generation++;
    }
    private final Map<String, CompoundTag> crates = new HashMap<>();

    public static CrateRegistry get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return (CrateRegistry)overworld.getDataStorage().computeIfAbsent(CrateRegistry::load, CrateRegistry::new, "fscrates_definitions");
    }

    public void put(CrateConfig crate) {
        this.crates.put(crate.id.toLowerCase(), crate.save());
        this.setDirty();
    }

    public CrateConfig get(String id) {
        CompoundTag tag = this.crates.get(id.toLowerCase());
        return tag == null ? null : CrateConfig.load(tag);
    }

    public boolean exists(String id) {
        return this.crates.containsKey(id.toLowerCase());
    }

    public boolean remove(String id) {
        boolean removed = this.crates.remove(id.toLowerCase()) != null;
        if (removed) {
            this.setDirty();
        }

        return removed;
    }

    public Set<String> ids() {
        return this.crates.keySet();
    }

    public static CrateRegistry load(CompoundTag tag) {
        CrateRegistry data = new CrateRegistry();
        CompoundTag stored = tag.getCompound("crates");

        for (String key : stored.getAllKeys()) {
            data.crates.put(key, stored.getCompound(key));
        }

        return data;
    }

    public CompoundTag save(CompoundTag tag) {
        CompoundTag stored = new CompoundTag();

        for (Entry<String, CompoundTag> e : this.crates.entrySet()) {
            stored.put(e.getKey(), e.getValue());
        }

        tag.put("crates", stored);
        return tag;
    }
}
