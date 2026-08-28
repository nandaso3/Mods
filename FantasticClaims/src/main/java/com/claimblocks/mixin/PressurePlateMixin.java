package com.claimblocks.mixin;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BasePressurePlateBlock.class})
public abstract class PressurePlateMixin {
    @Inject(
        method = {"checkPressed"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void claimblocks$blockVisitorPlate(Entity entity, Level level, BlockPos blockpos, BlockState blockstate, int i, CallbackInfo callbackinfo) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverplayer) {
            Claim claim = ClaimManager.getInstance().getClaimAt(level, blockpos);
            if (claim != null && !claim.getFlags().publicMode) {
                boolean flag = serverplayer.hasPermissions(2) && ClaimManager.getInstance().isBypassing(serverplayer.getUUID());
                if (!claim.canModify(serverplayer) && !flag) {
                    callbackinfo.cancel();
                }
            }
        }
    }
}
