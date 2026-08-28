package com.claimblocks.util;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

public final class ExplosionGuard {
    private ExplosionGuard() {
    }

    public static boolean protects(BlockGetter blockgetter, BlockPos blockpos) {
        if (blockpos != null && blockgetter instanceof Level level) {
            if (level.isClientSide) {
                return false;
            } else {
                Claim claim = ClaimManager.getInstance().getClaimAt(level, blockpos);
                return claim != null && (claim.getFlags().blockExplosions || claim.getFlags().publicMode);
            }
        } else {
            return false;
        }
    }
}
