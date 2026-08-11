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
      cancellable = true
   )
   private void claimblocks$blockCrossClaimDispense(ServerLevel level, BlockPos pos, CallbackInfo ci) {
      BlockState state = level.getBlockState(pos);

      Direction facing;
      try {
         facing = (Direction)state.getValue(DispenserBlock.FACING);
      } catch (Exception var10) {
         return;
      }

      BlockPos target = pos.relative(facing);
      ClaimManager mgr = ClaimManager.getInstance();
      Claim self = mgr.getClaimAt(level, pos);
      Claim tgt = mgr.getClaimAt(level, target);
      if (!sameClaim(self, tgt)) {
         if (protectsBuilding(tgt) || protectsBuilding(self)) {
            ci.cancel();
         }
      }
   }

   private static boolean sameClaim(Claim a, Claim b) {
      if (a == null && b == null) {
         return true;
      } else {
         return a != null && b != null ? a.getClaimId().equals(b.getClaimId()) : false;
      }
   }

   private static boolean protectsBuilding(Claim c) {
      return c == null ? false : c.getFlags().publicMode || c.getFlags().blockBuilding;
   }
}
