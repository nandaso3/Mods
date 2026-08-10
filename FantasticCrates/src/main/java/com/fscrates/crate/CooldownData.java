package com.fscrates.crate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class CooldownData extends SavedData {
    public static final String NAME = "fscrates_cooldowns";
    private final Map<String, Long> expiry = new HashMap<>();

    public static CooldownData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return (CooldownData)overworld.getDataStorage().computeIfAbsent(CooldownData::load, CooldownData::new, "fscrates_cooldowns");
    }

    private static String key(UUID player, String crateId) {
        return player.toString() + "|" + crateId;
    }

    public long remainingSeconds(UUID player, String crateId) {
        Long until = this.expiry.get(key(player, crateId));
        if (until == null) {
            return 0L;
        } else {
            long remainingMs = until - System.currentTimeMillis();
            return remainingMs <= 0L ? 0L : (remainingMs + 999L) / 1000L;
        }
    }

    public boolean isReady(UUID player, String crateId) {
        return this.remainingSeconds(player, crateId) <= 0L;
    }

    public void startCooldown(UUID player, String crateId, int seconds) {
        if (seconds > 0) {
            this.expiry.put(key(player, crateId), System.currentTimeMillis() + (long)seconds * 1000L);
            this.setDirty();
        }
    }

    public static CooldownData load(CompoundTag tag) {
        CooldownData data = new CooldownData();
        CompoundTag stored = tag.getCompound("cooldowns");

        for (String k : stored.getAllKeys()) {
            data.expiry.put(k, stored.getLong(k));
        }

        return data;
    }

    public CompoundTag save(CompoundTag tag) {
        CompoundTag stored = new CompoundTag();
        long now = System.currentTimeMillis();

        for (Entry<String, Long> e : this.expiry.entrySet()) {
            if (e.getValue() > now) {
                stored.putLong(e.getKey(), e.getValue());
            }
        }

        tag.put("cooldowns", stored);
        return tag;
    }
}
