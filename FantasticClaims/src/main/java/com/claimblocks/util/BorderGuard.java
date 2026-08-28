package com.claimblocks.util;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimConfig;
import com.claimblocks.data.ClaimFlags;
import com.claimblocks.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class BorderGuard {
    private BorderGuard() {
    }

    private static boolean sameZone(Claim claim, Claim claim1) {
        if (claim != null && claim1 != null) {
            return claim.getClaimId().equals(claim1.getClaimId()) ? true : claim.getGroupId() != null && claim.getGroupId().equals(claim1.getGroupId());
        } else {
            return false;
        }
    }

    public static boolean blocksItemExtraction(Level level, BlockPos blockpos, BlockPos blockpos1) {
        if (level == null || level.isClientSide() || blockpos == null || blockpos1 == null) {
            return false;
        } else if (!ClaimConfig.get().protectHoppers) {
            return false;
        } else {
            ClaimManager claimmanager = ClaimManager.getInstance();
            Claim claim = claimmanager.getClaimAt(level, blockpos);
            if (claim == null) {
                return false;
            } else {
                Claim claim1 = claimmanager.getClaimAt(level, blockpos1);
                if (sameZone(claim, claim1)) {
                    return false;
                } else {
                    ClaimFlags claimflags = claim.getFlags();
                    return claimflags.blockChestAccess || claimflags.publicMode;
                }
            }
        }
    }

    public static boolean blocksFluidEntry(Level level, BlockPos blockpos, BlockPos blockpos1) {
        if (level == null || level.isClientSide() || blockpos == null || blockpos1 == null) {
            return false;
        } else if (!ClaimConfig.get().protectFluids) {
            return false;
        } else {
            ClaimManager claimmanager = ClaimManager.getInstance();
            Claim claim = claimmanager.getClaimAt(level, blockpos1);
            if (claim == null) {
                return false;
            } else {
                Claim claim1 = claimmanager.getClaimAt(level, blockpos);
                if (sameZone(claim, claim1)) {
                    return false;
                } else {
                    ClaimFlags claimflags = claim.getFlags();
                    return claimflags.blockFluids || claimflags.publicMode;
                }
            }
        }
    }
}
