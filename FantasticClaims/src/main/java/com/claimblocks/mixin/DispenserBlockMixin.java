package com.claimblocks.mixin;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({DispenserBlock.class})
public abstract class DispenserBlockMixin {
    @Inject(
        method = {"dispenseFrom"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void claimblocks$blockCrossClaimDispense(ServerLevel serverlevel, BlockPos blockpos, CallbackInfo callbackinfo) {
        BlockState blockstate = serverlevel.getBlockState(blockpos);

        Direction direction;
        try {
            direction = (Direction)blockstate.getValue(DispenserBlock.FACING);
        } catch (Exception exception) {
            return;
        }

        BlockPos blockpos1 = blockpos.relative(direction);
        ClaimManager claimmanager = ClaimManager.getInstance();
        Claim claim = claimmanager.getClaimAt(serverlevel, blockpos);
        Claim claim1 = claimmanager.getClaimAt(serverlevel, blockpos1);
        if (!sameClaim(claim, claim1) && (protectsBuilding(claim1) || protectsBuilding(claim))) {
            callbackinfo.cancel();
        }
    }

    private static boolean sameClaim(Claim claim, Claim claim1) {
        if (claim == null && claim1 == null) {
            return true;
        } else {
            return claim != null && claim1 != null ? claim.getClaimId().equals(claim1.getClaimId()) : false;
        }
    }

    private static boolean protectsBuilding(Claim claim) {
        return claim == null ? false : claim.getFlags().publicMode || claim.getFlags().blockBuilding;
    }
}
