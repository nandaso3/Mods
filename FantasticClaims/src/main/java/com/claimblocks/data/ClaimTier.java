package com.claimblocks.data;

public final class ClaimTier {
    public static final ClaimTier[] VALUES = new ClaimTier[]{
        new ClaimTier("claimstone_10x10", 10, 15, 176, 190, 197),
        new ClaimTier("claimstone_25x25", 25, 20, 100, 181, 246),
        new ClaimTier("claimstone_40x40", 40, 30, 77, 208, 225),
        new ClaimTier("claimstone_64x64", 64, 40, 129, 199, 132),
        new ClaimTier("claimstone_80x80", 80, 50, 56, 142, 60),
        new ClaimTier("claimstone_100x100", 100, 60, 255, 213, 79),
        new ClaimTier("claimstone_150x150", 150, 80, 255, 138, 101),
        new ClaimTier("claimstone_250x250", 250, 100, 239, 83, 80),
        new ClaimTier("claimstone_300x300", 300, 120, 183, 28, 28),
        new ClaimTier("claimstone_500x500", 500, 150, 123, 31, 162)
    };
    public final String id;
    public final int radius;
    public final int height;
    public final float r;
    public final float g;
    public final float b;

    private ClaimTier(String s, int i, int j, int k, int l, int i1) {
        this.id = s;
        this.radius = i;
        this.height = j;
        this.r = (float)k / 255.0F;
        this.g = (float)l / 255.0F;
        this.b = (float)i1 / 255.0F;
    }

    public String label() {
        return this.id.substring("claimstone_".length());
    }

    public boolean isPaid() {
        return this.id.equals("claimstone_250x250") || this.id.equals("claimstone_300x300") || this.id.equals("claimstone_500x500");
    }

    public static ClaimTier byId(String s) {
        for (ClaimTier claimtier : VALUES) {
            if (claimtier.id.equals(s)) {
                return claimtier;
            }
        }

        return null;
    }

    public static ClaimTier byLegacyTier(int i) {
        return switch (i) {
            case 1 -> byId("claimstone_10x10");
            case 2 -> byId("claimstone_25x25");
            case 3 -> byId("claimstone_40x40");
            case 4 -> byId("claimstone_64x64");
            case 5 -> byId("claimstone_80x80");
            default -> null;
        };
    }

    public static ClaimTier closestMatch(int i, int j) {
        ClaimTier claimtier = VALUES[0];
        int k = Integer.MAX_VALUE;

        for (ClaimTier claimtier1 : VALUES) {
            int l = Math.abs(claimtier1.radius - i) + Math.abs(claimtier1.height - j);
            if (l < k) {
                k = l;
                claimtier = claimtier1;
            }
        }

        return claimtier;
    }
}
