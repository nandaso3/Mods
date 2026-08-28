package com.claimblocks.mixin;

import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.util.BorderGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({FlowingFluid.class})
public abstract class FluidGuardMixin {
    @Inject(
        method = {"canSpreadTo(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/material/Fluid;)Z"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void claimblocks$blockFluidEntering(
        BlockGetter blockgetter,
        BlockPos blockpos,
        BlockState blockstate,
        Direction direction,
        BlockPos blockpos1,
        BlockState blockstate1,
        FluidState fluidstate,
        Fluid fluid,
        CallbackInfoReturnable<Boolean> callbackinforeturnable
    ) {
        try {
            if (!(blockgetter instanceof Level)) {
                return;
            }

            if (BorderGuard.blocksFluidEntry((Level)blockgetter, blockpos, blockpos1)) {
                callbackinforeturnable.setReturnValue(false);
            }
        } catch (Throwable throwable) {
            ClaimBlocksMod.LOGGER.error("[FantasticClaims] Fallo controlando un fluido", throwable);
        }
    }
}
