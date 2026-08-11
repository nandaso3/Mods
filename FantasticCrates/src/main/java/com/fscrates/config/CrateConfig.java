package com.fscrates.config;

import com.fscrates.animation.AnimationRegistry;
import com.fscrates.client.color.FSTextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

public class CrateConfig {
    public String id = "nueva_crate";
    public String displayName = "\u00a7d\u2726 Crate \u2726";
    public Rarity rarity = Rarity.COMMON;
    public String styleId = "";
    public final List<RewardEntry> rewards = new ArrayList<>();
    public final LinkedHashMap<Rarity, Double> rarityChances = new LinkedHashMap<>();
    public int rolls = 1;
    public String animationId = AnimationRegistry.defaultId();
    public boolean glow = true;
    public boolean particles = true;
    public String nameColorHexOverride = "";
    public boolean floatingName = true;
    public boolean showOdds = false;
    public final List<String> floatingText = new ArrayList<>();
    /** URLs directas de video o imagen para la pantalla de pre-apertura. */
    public final List<String> videos = new ArrayList<>();
    /** URLs directas de musica para la pantalla de pre-apertura. */
    public final List<String> music = new ArrayList<>();
    /**
     * Textos de la pantalla de pre-apertura.
     *
     * El estilo (color, negrita, arcoiris...) va en campos aparte dentro de
     * FSTextStyle, no metido en la cadena: asi el texto nunca sale con codigos a
     * la vista.
     */
    public FSTextStyle sceneHeader = new FSTextStyle("\u2726 Fantastic Crates \u2726", "#FF55FF");
    /** Linea que sale DEBAJO del nombre de la crate. */
    public FSTextStyle sceneSubtitle = new FSTextStyle("Prep\u00e1rate para abrir tu caja", "#AAAAAA");
    /** Estilo con el que se pinta el nombre de la caja en la escena. */
    public FSTextStyle nameStyle = new FSTextStyle("", "#FFFFFF");
    /** Lineas extra de texto libre, debajo del subtitulo. */
    public final List<FSTextStyle> sceneLines = new ArrayList<>();
    public final List<ParticleLayer> particleLayers = new ArrayList<>();
    public boolean consumeKey = true;
    public boolean uniqueKeyEnabled = false;
    public String uniqueKeyModel = "";
    public String uniqueKeyName = "";
    public int cooldownSeconds = 0;
    public boolean broadcast = false;
    public boolean allowSkip = true;
    public int openDelayTicks = 0;
    public String requiredPermission = "";
    public float sizeScale = 1.0F;
    public float yOffset = 0.0F;
    public float yawOffset = 0.0F;
    public boolean openOncePerPlayer = false;
    public boolean pityEnabled = false;
    public int pityInterval = 10;
    public Rarity pityRarity = Rarity.LEGENDARY;

    public CrateConfig() {
        this.particleLayers.addAll(ParticleLayer.defaults());
        this.rarityChances.putAll(defaultRarityChances());
    }

    public CrateConfig(String id) {
        this();
        this.id = id;
    }

    public static LinkedHashMap<Rarity, Double> defaultRarityChances() {
        LinkedHashMap<Rarity, Double> m = new LinkedHashMap<>();
        m.put(Rarity.COMMON, Double.valueOf(60.0));
        m.put(Rarity.RARE, Double.valueOf(25.0));
        m.put(Rarity.EPIC, Double.valueOf(10.0));
        m.put(Rarity.LEGENDARY, Double.valueOf(4.0));
        m.put(Rarity.MYTHIC, Double.valueOf(1.0));
        return m;
    }

    public Rarity rollRarity(Random random) {
        double total = this.rarityChanceTotal();
        if (total <= 0.0) {
            return this.rarity == null ? Rarity.COMMON : this.rarity;
        } else {
            double pick = random.nextDouble() * total;
            double cursor = 0.0;

            for (Rarity r : Rarity.values()) {
                cursor += Math.max(0.0, this.rarityChances.getOrDefault(r, 0.0));
                if (pick < cursor) {
                    return r;
                }
            }

            return this.rarity == null ? Rarity.COMMON : this.rarity;
        }
    }

    public double rarityChance(Rarity r) {
        return Math.max(0.0, this.rarityChances.getOrDefault(r, 0.0));
    }

    public double rarityChanceTotal() {
        double t = 0.0;

        for (Rarity r : Rarity.values()) {
            t += Math.max(0.0, this.rarityChances.getOrDefault(r, 0.0));
        }

        return t;
    }

    public double rarityChancePercent(Rarity r) {
        double t = this.rarityChanceTotal();
        return t > 0.0 ? this.rarityChance(r) * 100.0 / t : 0.0;
    }

    public double poolTotalChance(Rarity r) {
        double t = 0.0;

        for (RewardEntry e : this.rewards) {
            if (!e.guaranteed && e.effectiveRarity(this.rarity) == r) {
                t += Math.max(0.0, e.chance);
            }
        }

        return t;
    }

    public double normalizedPercentInPool(RewardEntry entry) {
        if (entry.guaranteed) {
            return 100.0;
        } else {
            double t = this.poolTotalChance(entry.effectiveRarity(this.rarity));
            return t > 0.0 ? Math.max(0.0, entry.chance) * 100.0 / t : 0.0;
        }
    }

    public int rewardCountForRarity(Rarity r) {
        int n = 0;

        for (RewardEntry e : this.rewards) {
            if (!e.guaranteed && e.effectiveRarity(this.rarity) == r) {
                n++;
            }
        }

        return n;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", this.id);
        tag.putString("displayName", this.displayName);
        tag.putString("rarity", this.rarity.name());
        CompoundTag rarityChancesTag = new CompoundTag();

        for (Rarity r : Rarity.values()) {
            rarityChancesTag.putDouble(r.name(), Math.max(0.0, this.rarityChances.getOrDefault(r, 0.0)));
        }

        tag.put("rarityChances", rarityChancesTag);
        tag.putString("styleId", this.styleId);
        tag.putInt("rolls", this.rolls);
        tag.putString("animationId", this.animationId);
        ListTag rewardList = new ListTag();

        for (RewardEntry rewardEntry : this.rewards) {
            rewardList.add(rewardEntry.save());
        }

        tag.put("rewards", rewardList);
        tag.putBoolean("glow", this.glow);
        tag.putBoolean("particles", this.particles);
        tag.putString("nameColorHex", this.nameColorHexOverride);
        tag.putBoolean("floatingName", this.floatingName);
        tag.putBoolean("showOdds", this.showOdds);
        ListTag floatList = new ListTag();

        for (String line : this.floatingText) {
            floatList.add(StringTag.valueOf(line));
        }

        tag.put("floatingText", floatList);
        ListTag videoList = new ListTag();

        for (String url : this.videos) {
            videoList.add(StringTag.valueOf(url));
        }

        tag.put("videos", videoList);
        ListTag musicList = new ListTag();

        for (String url : this.music) {
            musicList.add(StringTag.valueOf(url));
        }

        tag.put("music", musicList);
        tag.put("sceneHeaderStyle", (this.sceneHeader == null ? new FSTextStyle() : this.sceneHeader).save());
        tag.put("sceneSubtitleStyle", (this.sceneSubtitle == null ? new FSTextStyle() : this.sceneSubtitle).save());
        tag.put("nameStyle", (this.nameStyle == null ? new FSTextStyle() : this.nameStyle).save());
        ListTag sceneList = new ListTag();

        for (FSTextStyle line : this.sceneLines) {
            sceneList.add(line.save());
        }

        tag.put("sceneLineStyles", sceneList);
        ListTag listTag = new ListTag();

        for (ParticleLayer layer : this.particleLayers) {
            listTag.add(layer.save());
        }

        tag.put("particleLayers", listTag);
        tag.putBoolean("consumeKey", this.consumeKey);
        tag.putBoolean("uniqueKeyEnabled", this.uniqueKeyEnabled);
        tag.putString("uniqueKeyModel", this.uniqueKeyModel == null ? "" : this.uniqueKeyModel);
        tag.putString("uniqueKeyName", this.uniqueKeyName == null ? "" : this.uniqueKeyName);
        tag.putInt("cooldown", this.cooldownSeconds);
        tag.putBoolean("broadcast", this.broadcast);
        tag.putBoolean("allowSkip", this.allowSkip);
        tag.putInt("openDelay", this.openDelayTicks);
        tag.putString("permission", this.requiredPermission);
        tag.putFloat("sizeScale", this.sizeScale);
        tag.putFloat("yOffset", this.yOffset);
        tag.putFloat("yawOffset", this.yawOffset);
        tag.putBoolean("openOncePerPlayer", this.openOncePerPlayer);
        tag.putBoolean("pityEnabled", this.pityEnabled);
        tag.putInt("pityInterval", Math.max(1, this.pityInterval));
        tag.putString("pityRarity", (this.pityRarity == null ? Rarity.LEGENDARY : this.pityRarity).name());
        return tag;
    }

    public static CrateConfig load(CompoundTag tag) {
        CrateConfig c = new CrateConfig();
        c.id = tag.contains("id") ? tag.getString("id") : "nueva_crate";
        c.displayName = tag.contains("displayName") ? tag.getString("displayName") : "\u00a7d\u2726 Crate \u2726";
        c.rarity = Rarity.byName(tag.getString("rarity"));
        c.rarityChances.clear();
        if (tag.contains("rarityChances")) {
            CompoundTag rarityChancesTag = tag.getCompound("rarityChances");

            for (Rarity r : Rarity.values()) {
                if (rarityChancesTag.contains(r.name())) {
                    c.rarityChances.put(r, Double.valueOf(rarityChancesTag.getDouble(r.name())));
                }
            }

            if (c.rarityChances.isEmpty()) {
                c.rarityChances.putAll(defaultRarityChances());
            }
        } else {
            c.rarityChances.put(c.rarity, Double.valueOf(100.0));
        }

        c.styleId = tag.getString("styleId");
        c.rolls = tag.contains("rolls") ? Math.max(1, tag.getInt("rolls")) : 1;
        String string = c.animationId = tag.contains("animationId") ? tag.getString("animationId") : AnimationRegistry.defaultId();
        if (!AnimationRegistry.exists(c.animationId)) {
            c.animationId = AnimationRegistry.defaultId();
        }

        c.rewards.clear();
        ListTag rewardList = tag.getList("rewards", 10);

        for (int i = 0; i < rewardList.size(); i++) {
            c.rewards.add(RewardEntry.load(rewardList.getCompound(i)));
        }

        c.glow = !tag.contains("glow") || tag.getBoolean("glow");
        c.particles = !tag.contains("particles") || tag.getBoolean("particles");
        c.nameColorHexOverride = tag.getString("nameColorHex");
        c.floatingName = !tag.contains("floatingName") || tag.getBoolean("floatingName");
        c.showOdds = tag.getBoolean("showOdds");
        c.floatingText.clear();
        ListTag floatList = tag.getList("floatingText", 8);

        for (int j = 0; j < floatList.size(); j++) {
            c.floatingText.add(floatList.getString(j));
        }

        c.videos.clear();
        ListTag videoList = tag.getList("videos", 8);

        for (int v = 0; v < videoList.size(); v++) {
            String url = videoList.getString(v);
            if (url != null && !url.isBlank()) {
                c.videos.add(url);
            }
        }

        c.music.clear();
        ListTag musicList = tag.getList("music", 8);

        for (int m = 0; m < musicList.size(); m++) {
            String url = musicList.getString(m);
            if (url != null && !url.isBlank()) {
                c.music.add(url);
            }
        }

        // Formato nuevo (estilo en campos aparte).
        if (tag.contains("sceneHeaderStyle")) {
            c.sceneHeader = FSTextStyle.load(tag.getCompound("sceneHeaderStyle"));
        } else if (tag.contains("sceneHeader")) {
            // Formato viejo: el estilo venia dentro de la cadena.
            c.sceneHeader = FSTextStyle.migrate(tag.getString("sceneHeader"));
        }

        if (tag.contains("sceneSubtitleStyle")) {
            c.sceneSubtitle = FSTextStyle.load(tag.getCompound("sceneSubtitleStyle"));
        } else if (tag.contains("sceneSubtitle")) {
            c.sceneSubtitle = FSTextStyle.migrate(tag.getString("sceneSubtitle"));
        }

        if (tag.contains("nameStyle")) {
            c.nameStyle = FSTextStyle.load(tag.getCompound("nameStyle"));
        }

        c.sceneLines.clear();
        if (tag.contains("sceneLineStyles")) {
            ListTag styled = tag.getList("sceneLineStyles", 10);
            for (int s = 0; s < styled.size(); s++) {
                c.sceneLines.add(FSTextStyle.load(styled.getCompound(s)));
            }
        } else {
            ListTag legacy = tag.getList("sceneLines", 8);
            for (int s = 0; s < legacy.size(); s++) {
                c.sceneLines.add(FSTextStyle.migrate(legacy.getString(s)));
            }
        }

        // El nombre de la caja pudo quedar con codigos dentro de builds anteriores:
        // se limpian y se pasan al estilo, para que el campo del editor quede solo
        // con el texto.
        if (c.displayName != null && (c.displayName.contains("&#") || c.displayName.contains("<rainbow"))) {
            FSTextStyle migrated = FSTextStyle.migrate(c.displayName);
            c.nameStyle = migrated;
            c.displayName = migrated.text;
        }

        c.particleLayers.clear();
        if (tag.contains("particleLayers")) {
            ListTag particleList = tag.getList("particleLayers", 10);

            for (int k = 0; k < particleList.size(); k++) {
                c.particleLayers.add(ParticleLayer.load(particleList.getCompound(k)));
            }
        } else {
            c.particleLayers.addAll(ParticleLayer.defaults());
        }

        c.consumeKey = !tag.contains("consumeKey") || tag.getBoolean("consumeKey");
        c.uniqueKeyEnabled = tag.getBoolean("uniqueKeyEnabled");
        c.uniqueKeyModel = tag.getString("uniqueKeyModel");
        c.uniqueKeyName = tag.getString("uniqueKeyName");
        c.cooldownSeconds = tag.getInt("cooldown");
        c.broadcast = tag.getBoolean("broadcast");
        c.allowSkip = !tag.contains("allowSkip") || tag.getBoolean("allowSkip");
        c.openDelayTicks = tag.getInt("openDelay");
        c.requiredPermission = tag.getString("permission");
        c.sizeScale = tag.contains("sizeScale") ? tag.getFloat("sizeScale") : 1.0F;
        c.yOffset = tag.getFloat("yOffset");
        c.yawOffset = tag.getFloat("yawOffset");
        c.openOncePerPlayer = tag.getBoolean("openOncePerPlayer");
        c.pityEnabled = tag.getBoolean("pityEnabled");
        c.pityInterval = tag.contains("pityInterval") ? Math.max(1, tag.getInt("pityInterval")) : 10;
        c.pityRarity = tag.contains("pityRarity") ? Rarity.byName(tag.getString("pityRarity")) : Rarity.LEGENDARY;
        return c;
    }

    public double totalChance() {
        double total = 0.0;

        for (RewardEntry r : this.rewards) {
            if (!r.guaranteed) {
                total += Math.max(0.0, r.chance);
            }
        }

        return total;
    }

    public double normalizedPercent(RewardEntry entry) {
        if (entry.guaranteed) {
            return 100.0;
        } else {
            double total = this.totalChance();
            return total > 0.0 ? Math.max(0.0, entry.chance) * 100.0 / total : 0.0;
        }
    }

    public String floatingTextJoined() {
        return String.join("\n", this.floatingText);
    }

    public void setFloatingText(String multiline) {
        this.floatingText.clear();
        if (multiline != null) {
            for (String line : multiline.split("\n", -1)) {
                this.floatingText.add(line);
            }

            while (!this.floatingText.isEmpty() && this.floatingText.get(this.floatingText.size() - 1).isEmpty()) {
                this.floatingText.remove(this.floatingText.size() - 1);
            }
        }
    }

    public CrateConfig copy() {
        return load(this.save());
    }
}
