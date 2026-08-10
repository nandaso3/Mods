package com.fscrates.config;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

public class ParticleLayer {
    public String particleId = "minecraft:enchant";
    public ParticleLayer.Phase phase = ParticleLayer.Phase.IDLE;
    public ParticleLayer.Shape shape = ParticleLayer.Shape.HALO;
    public int count = 4;
    public double speed = 0.04;
    public double spread = 0.4;
    public double radius = 0.85;
    public double yOffset = 1.1;
    public boolean useRarityColor = true;
    public String colorHex = "#FFFFFF";
    public int interval = 4;

    public ParticleLayer() {
        this.applyShapeDefaults();
    }

    public ParticleLayer(String id, ParticleLayer.Phase phase, ParticleLayer.Shape shape) {
        this.particleId = id;
        this.phase = phase;
        this.shape = shape;
        this.applyShapeDefaults();
    }

    public void applyShapeDefaults() {
        switch (this.shape) {
            case HALO:
                this.radius = 0.85;
                this.yOffset = 1.1;
                this.spread = 0.2;
                this.count = 6;
                this.speed = 0.02;
                break;
            case RING:
                this.radius = 0.95;
                this.yOffset = 0.45;
                this.spread = 0.05;
                this.count = 24;
                this.speed = 0.02;
                break;
            case BURST:
                this.radius = 0.1;
                this.yOffset = 0.85;
                this.spread = 0.55;
                this.count = 14;
                this.speed = 0.3;
                break;
            case COLUMN:
                this.radius = 0.1;
                this.yOffset = 0.85;
                this.spread = 0.2;
                this.count = 6;
                this.speed = 0.1;
                break;
            case SPIRAL:
                this.radius = 0.85;
                this.yOffset = 0.2;
                this.spread = 0.1;
                this.count = 16;
                this.speed = 0.05;
                break;
            case FOUNTAIN:
                this.radius = 0.05;
                this.yOffset = 0.85;
                this.spread = 0.45;
                this.count = 10;
                this.speed = 0.3;
                break;
            case VORTEX:
                this.radius = 0.85;
                this.yOffset = 0.95;
                this.spread = 0.05;
                this.count = 16;
                this.speed = 0.05;
                break;
            case RAIN:
                this.radius = 0.0;
                this.yOffset = 1.8;
                this.spread = 1.1;
                this.count = 6;
                this.speed = 0.3;
                break;
            case POINT:
                this.radius = 0.0;
                this.yOffset = 0.9;
                this.spread = 0.05;
                this.count = 1;
                this.speed = 0.04;
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("p", this.particleId);
        t.putString("phase", this.phase.name());
        t.putString("shape", this.shape.name());
        t.putInt("count", this.count);
        t.putDouble("speed", this.speed);
        t.putDouble("spread", this.spread);
        t.putDouble("radius", this.radius);
        t.putDouble("y", this.yOffset);
        t.putBoolean("tier", this.useRarityColor);
        t.putString("hex", this.colorHex);
        t.putInt("interval", this.interval);
        return t;
    }

    public static ParticleLayer load(CompoundTag t) {
        ParticleLayer l = new ParticleLayer();
        l.particleId = t.contains("p") ? t.getString("p") : "minecraft:enchant";
        l.phase = enumOr(ParticleLayer.Phase.class, t.getString("phase"), ParticleLayer.Phase.IDLE);
        l.shape = enumOr(ParticleLayer.Shape.class, t.getString("shape"), ParticleLayer.Shape.HALO);
        l.count = t.contains("count") ? t.getInt("count") : 4;
        l.speed = t.contains("speed") ? t.getDouble("speed") : 0.04;
        l.spread = t.contains("spread") ? t.getDouble("spread") : 0.4;
        l.radius = t.contains("radius") ? t.getDouble("radius") : 0.85;
        l.yOffset = t.contains("y") ? t.getDouble("y") : 1.1;
        l.useRarityColor = !t.contains("tier") || t.getBoolean("tier");
        l.colorHex = t.contains("hex") ? t.getString("hex") : "#FFFFFF";
        l.interval = t.contains("interval") ? Math.max(1, t.getInt("interval")) : 4;
        return l;
    }

    public ParticleLayer copy() {
        return load(this.save());
    }

    public String shortLabel() {
        String pid = this.particleId.contains(":") ? this.particleId.substring(this.particleId.indexOf(58) + 1) : this.particleId;
        return ParticleNames.spanish(pid) + " \u00a78" + this.phase.label.charAt(0) + "/" + this.shape.label.charAt(0);
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E def) {
        try {
            return Enum.valueOf(type, name);
        } catch (Exception var4) {
            return def;
        }
    }

    public static List<ParticleLayer> defaults() {
        ArrayList<ParticleLayer> list = new ArrayList<>();
        ParticleLayer halo = new ParticleLayer("minecraft:enchant", ParticleLayer.Phase.IDLE, ParticleLayer.Shape.HALO);
        halo.count = 3;
        halo.interval = 4;
        list.add(halo);
        ParticleLayer ring = new ParticleLayer("minecraft:dust", ParticleLayer.Phase.IDLE, ParticleLayer.Shape.RING);
        ring.count = 12;
        ring.interval = 8;
        list.add(ring);
        ParticleLayer spiral = new ParticleLayer("minecraft:dust", ParticleLayer.Phase.ANTICIPATION, ParticleLayer.Shape.SPIRAL);
        spiral.count = 16;
        spiral.radius = 0.85;
        spiral.yOffset = 0.2;
        spiral.speed = 0.05;
        spiral.useRarityColor = false;
        spiral.colorHex = "#FFE08A";
        list.add(spiral);
        ParticleLayer spiralSpark = new ParticleLayer("minecraft:end_rod", ParticleLayer.Phase.ANTICIPATION, ParticleLayer.Shape.SPIRAL);
        spiralSpark.count = 10;
        spiralSpark.radius = 0.7;
        spiralSpark.yOffset = 0.2;
        spiralSpark.speed = 0.06;
        list.add(spiralSpark);
        ParticleLayer open = new ParticleLayer("minecraft:end_rod", ParticleLayer.Phase.OPEN, ParticleLayer.Shape.FOUNTAIN);
        open.count = 12;
        open.speed = 0.3;
        open.spread = 0.35;
        list.add(open);
        ParticleLayer reveal = new ParticleLayer("minecraft:dust", ParticleLayer.Phase.REVEAL, ParticleLayer.Shape.VORTEX);
        reveal.count = 18;
        reveal.radius = 0.95;
        reveal.yOffset = 1.15;
        list.add(reveal);
        ParticleLayer finale = new ParticleLayer("minecraft:firework", ParticleLayer.Phase.FINALE, ParticleLayer.Shape.BURST);
        finale.count = 28;
        finale.speed = 0.45;
        finale.spread = 0.7;
        finale.yOffset = 1.0;
        list.add(finale);
        return list;
    }

    public static enum Phase {
        IDLE("Reposo"),
        ANTICIPATION("Tensi\u00f3n"),
        OPEN("Apertura"),
        REVEAL("Revelaci\u00f3n"),
        FINALE("Final");

        public final String label;

        private Phase(String l) {
            this.label = l;
        }

        public ParticleLayer.Phase next() {
            ParticleLayer.Phase[] v = values();
            return v[(this.ordinal() + 1) % v.length];
        }
    }

    public static enum Shape {
        HALO("Halo"),
        RING("Anillo"),
        BURST("Estallido"),
        COLUMN("Columna"),
        SPIRAL("Espiral"),
        FOUNTAIN("Fuente"),
        VORTEX("V\u00f3rtice"),
        RAIN("Lluvia"),
        POINT("Punto");

        public final String label;

        private Shape(String l) {
            this.label = l;
        }

        public ParticleLayer.Shape next() {
            ParticleLayer.Shape[] v = values();
            return v[(this.ordinal() + 1) % v.length];
        }
    }
}
