package com.claimblocks.mixin;

import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.util.ExplosionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({EntityBasedExplosionDamageCalculator.class})
public abstract class EntityExplosionCalculatorGuardMixin {
    @Inject(
        method = {"shouldBlockExplode(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;F)Z"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void claimblocks$keepClaimedBlocks(
        Explosion explosion, BlockGetter blockgetter, BlockPos blockpos, BlockState blockstate, float f, CallbackInfoReturnable<Boolean> callbackinforeturnable
    ) {
        try {
            if (ExplosionGuard.protects(blockgetter, blockpos)) {
                callbackinforeturnable.setReturnValue(false);
            }
        } catch (Throwable throwable) {
            ClaimBlocksMod.LOGGER.error("[FantasticClaims] Fallo protegiendo bloques de una explosion", throwable);
        }
    }
}
