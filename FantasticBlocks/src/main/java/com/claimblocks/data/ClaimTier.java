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

   private ClaimTier(String id, int radius, int height, int rR, int rG, int rB) {
      this.id = id;
      this.radius = radius;
      this.height = height;
      this.r = (float)rR / 255.0F;
      this.g = (float)rG / 255.0F;
      this.b = (float)rB / 255.0F;
   }

   public String label() {
      return this.id.substring("claimstone_".length());
   }

   public boolean isPaid() {
      return this.id.equals("claimstone_250x250") || this.id.equals("claimstone_300x300") || this.id.equals("claimstone_500x500");
   }

   public static ClaimTier byId(String id) {
      for (ClaimTier t : VALUES) {
         if (t.id.equals(id)) {
            return t;
         }
      }

      return null;
   }

   public static ClaimTier byLegacyTier(int legacyTier) {
      return switch (legacyTier) {
         case 1 -> byId("claimstone_10x10");
         case 2 -> byId("claimstone_25x25");
         case 3 -> byId("claimstone_40x40");
         case 4 -> byId("claimstone_64x64");
         case 5 -> byId("claimstone_80x80");
         default -> null;
      };
   }

   public static ClaimTier closestMatch(int radius, int height) {
      ClaimTier best = VALUES[0];
      int bestScore = Integer.MAX_VALUE;

      for (ClaimTier t : VALUES) {
         int score = Math.abs(t.radius - radius) + Math.abs(t.height - height);
         if (score < bestScore) {
            bestScore = score;
            best = t;
         }
      }

      return best;
   }
}
