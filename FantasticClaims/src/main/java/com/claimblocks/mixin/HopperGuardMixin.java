package com.claimblocks.mixin;

import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.util.BorderGuard;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({HopperBlockEntity.class})
public abstract class HopperGuardMixin {
    @Inject(
        method = {"suckInItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Z"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private static void claimblocks$blockHopperSuck(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> callbackinforeturnable) {
        try {
            if (level == null || hopper == null) {
                return;
            }

            BlockPos blockpos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY(), hopper.getLevelZ());
            BlockPos blockpos1 = blockpos.relative(Direction.UP);
            if (BorderGuard.blocksItemExtraction(level, blockpos1, blockpos)) {
                callbackinforeturnable.setReturnValue(false);
            }
        } catch (Throwable throwable) {
            ClaimBlocksMod.LOGGER.error("[FantasticClaims] Fallo controlando una tolva (absorcion)", throwable);
        }
    }

    @Inject(
        method = {"tryMoveItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/HopperBlockEntity;Ljava/util/function/BooleanSupplier;)Z"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private static void claimblocks$blockHopperPush(
        Level level,
        BlockPos blockpos,
        BlockState blockstate,
        HopperBlockEntity hopperblockentity,
        BooleanSupplier booleansupplier,
        CallbackInfoReturnable<Boolean> callbackinforeturnable
    ) {
        try {
            if (level == null || blockpos == null || blockstate == null) {
                return;
            }

            Direction direction = (Direction)blockstate.getValue(HopperBlock.FACING);
            if (direction == null) {
                return;
            }

            if (BorderGuard.blocksItemExtraction(level, blockpos, blockpos.relative(direction))) {
                callbackinforeturnable.setReturnValue(false);
            }
        } catch (Throwable throwable) {
            ClaimBlocksMod.LOGGER.error("[FantasticClaims] Fallo controlando una tolva (empuje)", throwable);
        }
    }
}
