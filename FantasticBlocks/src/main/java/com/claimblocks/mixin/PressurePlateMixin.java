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
      cancellable = true
   )
   private void claimblocks$blockVisitorPlate(Entity entity, Level level, BlockPos pos, BlockState state, int currentSignal, CallbackInfo ci) {
      if (!level.isClientSide) {
         if (entity instanceof ServerPlayer player) {
            Claim claim = ClaimManager.getInstance().getClaimAt(level, pos);
            if (claim != null) {
               if (!claim.getFlags().publicMode) {
                  boolean bypass = player.hasPermissions(2) && ClaimManager.getInstance().isBypassing(player.getUUID());
                  if (!claim.canModify(player) && !bypass) {
                     ci.cancel();
                  }
               }
            }
         }
      }
   }
}
