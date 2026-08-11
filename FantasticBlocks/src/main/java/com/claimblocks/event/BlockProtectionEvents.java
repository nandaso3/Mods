package com.claimblocks.event;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimGroup;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import com.claimblocks.gui.ClaimMenuHandler;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityTeleportEvent.EnderPearl;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.event.level.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.event.level.ExplosionEvent.Detonate;
import net.minecraftforge.event.level.PistonEvent.Pre;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class BlockProtectionEvents {
   private static int fireSweepCounter = 0;

   private static boolean isBypassing(Player var0) {
      return var0.hasPermissions(2) && ClaimManager.getInstance().isBypassing(var0.getUUID());
   }

   private static boolean denyForVisitor(Claim var0, Player var1) {
      if (var0.canModify(var1)) {
         return false;
      } else if (isBypassing(var1)) {
         return false;
      } else {
         return var0.getFlags().publicMode ? true : true;
      }
   }

   private static boolean denyForVisitor(Claim var0, Player var1, boolean var2) {
      return denyForVisitor(var0, var1);
   }

   private static void deny(Player var0, String var1) {
      if (var0 instanceof ServerPlayer var2 && !var1.isEmpty()) {
         var2.displayClientMessage(Component.literal(var1).withStyle(ChatFormatting.RED), true);
      }
   }

   @SubscribeEvent
   public void onBreak(BreakEvent var1) {
      Player var4;
      if (var1.getLevel() instanceof Level var3 && !var3.isClientSide && (var4 = var1.getPlayer()) != null && !isBypassing(var4)) {
         BlockPos var5 = var1.getPos();
         BlockState var6 = var1.getState();
         Claim var7 = ClaimManager.getInstance().getClaimByCenter(var3, var5);
         ClaimTier var8;
         if (var7 != null && (var8 = var7.getTier()) != null && ClaimBlocks.isClaimConcreteForTier(var6.getBlock(), var8)) {
            if (!var7.isOwner(var4) && !var4.hasPermissions(2)) {
               deny(var4, "[!] Solo el dueño puede romper esta protección.");
               var1.setCanceled(true);
            } else {
               ClaimManager.getInstance().removeClaim(var3, var5);
               if (!var4.getAbilities().instabuild) {
                  ItemStack var9 = ClaimBlocks.createTierItem(var8, 1);
                  if (!var4.getInventory().add(var9)) {
                     var4.drop(var9, false);
                  }
               }

               var3.playSound(null, var5, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 1.0F);
               if (var4 instanceof ServerPlayer) {
                  ((ServerPlayer)var4)
                     .displayClientMessage(Component.literal("✔ Zona eliminada. Protección devuelta a tu inventario.").withStyle(ChatFormatting.GREEN), false);
               }

               var3.setBlockAndUpdate(var5, Blocks.AIR.defaultBlockState());
               var1.setCanceled(false);
            }

            return;
         }

         Claim var10 = ClaimManager.getInstance().getClaimAt(var3, var5);
         if (var10 != null && !var10.canModify(var4)) {
            if (!var6.is(BlockTags.LOGS) || !var10.getFlags().publicMode && !var10.getFlags().blockTreeChopping) {
               if (!isMatureCrop(var6) || !var10.getFlags().publicMode && !var10.getFlags().blockCropHarvest) {
                  if (denyForVisitor(var10, var4, var10.getFlags().blockBreaking)) {
                     deny(var4, "[!] No puedes romper bloques aquí.");
                     var1.setCanceled(true);
                  }
               } else {
                  deny(var4, "[!] No puedes cosechar cultivos aquí.");
                  var1.setCanceled(true);
               }
            } else {
               deny(var4, "[!] No puedes talar árboles en esta zona.");
               var1.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public void onPlace(EntityPlaceEvent var1) {
      Claim var6;
      Player var5;
      Entity entity;
      if (var1.getLevel() instanceof Level var3
         && !var3.isClientSide
         && (entity = var1.getEntity()) instanceof Player
         && !isBypassing(var5 = (Player)entity)
         && (var6 = ClaimManager.getInstance().getClaimAt(var3, var1.getPos())) != null
         && denyForVisitor(var6, var5, var6.getFlags().blockBuilding)) {
         deny(var5, "[!] No puedes construir aquí.");
         var1.setCanceled(true);
      }
   }

   @SubscribeEvent
   public void onRightClickBlock(RightClickBlock var1) {
      Level var2 = var1.getLevel();
      if (!var2.isClientSide) {
         Player var3 = var1.getEntity();
         BlockPos var4 = var1.getPos();
         ItemStack var5 = var1.getItemStack();
         Claim var6 = ClaimManager.getInstance().getClaimByCenter(var2, var4);
         if (var6 != null) {
            ClaimTier var7 = var6.getTier();
            BlockState var8 = var2.getBlockState(var4);
            if (var7 != null && ClaimBlocks.isClaimConcreteForTier(var8.getBlock(), var7) && !var3.isShiftKeyDown()) {
               if (var1.getHand() == InteractionHand.MAIN_HAND) {
                  if (!var6.isOwner(var3) && !var3.hasPermissions(2)) {
                     deny(var3, "[x] Solo el dueño puede administrar esta zona.");
                  } else if (var3 instanceof ServerPlayer) {
                     ClaimMenuHandler.open((ServerPlayer)var3, var6, 0);
                  }
               }

               var1.setCanceled(true);
               var1.setCancellationResult(InteractionResult.SUCCESS);
               return;
            }
         }

         ClaimTier var9;
         if ((var9 = ClaimBlocks.readTier(var5)) != null && !isBypassing(var3)) {
            InteractionResult var11 = this.tryPlaceClaim(var3, var2, var1.getHand(), var1.getFace(), var4, var5, var9);
            var1.setCanceled(true);
            var1.setCancellationResult(var11);
         } else {
            InteractionResult var10 = this.regularChecks(var3, var2, var4, var1.getFace(), var5);
            if (var10 != InteractionResult.PASS) {
               var1.setCanceled(true);
               var1.setCancellationResult(var10);
            }
         }
      }
   }

   private InteractionResult tryPlaceClaim(Player var1, Level var2, InteractionHand var3, Direction var4, BlockPos var5, ItemStack var6, ClaimTier var7) {
      BlockState var8 = var2.getBlockState(var5);
      BlockPos var9 = var8.canBeReplaced() ? var5 : var5.relative(var4);
      BlockState var10 = var2.getBlockState(var9);
      if (!var10.isAir() && !var10.canBeReplaced()) {
         return InteractionResult.PASS;
      } else {
         ClaimManager var11 = ClaimManager.getInstance();
         Claim var12 = var11.getClaimAt(var2, var9);
         if (var12 != null && !var12.canModify(var1) && !var1.hasPermissions(2)) {
            deny(var1, "[x] No puedes construir en esta zona.");
            return InteractionResult.SUCCESS;
         } else {
            List<Claim> overlaps = var11.overlappingClaims(var2, var9, var7.radius, var7.height);
            UUID joinGroup = null;
            if (!overlaps.isEmpty()) {
               UUID gid = null;
               boolean sameGroup = true;
               Iterator newClaim = overlaps.iterator();

               while (true) {
                  if (newClaim.hasNext()) {
                     Claim oc = (Claim)newClaim.next();
                     if (oc.getGroupId() == null) {
                        sameGroup = false;
                     } else {
                        if (gid == null) {
                           gid = oc.getGroupId();
                           continue;
                        }

                        if (gid.equals(oc.getGroupId())) {
                           continue;
                        }

                        sameGroup = false;
                     }
                  }

                  if (!sameGroup || gid == null || !var11.isRegistered(gid, var1.getUUID())) {
                     deny(var1, "[x] Esta zona se solaparía con otra existente.");
                     return InteractionResult.SUCCESS;
                  }

                  joinGroup = gid;
                  break;
               }
            }

            int var13 = ClaimManager.getMaxClaimsPerPlayer();
            if (var13 > 0 && !var1.hasPermissions(2) && var11.getClaimsOf(var1.getUUID()).size() >= var13) {
               deny(var1, "[x] Has alcanzado el límite de zonas (" + var13 + ").");
               return InteractionResult.SUCCESS;
            } else {
               Block var14 = ClaimBlocks.blockForTier(var7);
               var2.setBlockAndUpdate(var9, var14.defaultBlockState());
               var2.playSound(null, var9, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
               Claim newClaim = var11.createClaim(var2, var9, var1, var7);
               if (joinGroup != null && newClaim != null) {
                  var11.joinClaimToGroup(newClaim, joinGroup);
               }

               if (!var1.getAbilities().instabuild) {
                  var6.shrink(1);
               }

               var1.swing(var3);
               if (var1 instanceof ServerPlayer) {
                  if (joinGroup != null) {
                     ClaimGroup jg = var11.getGroup(joinGroup);
                     String gname = jg != null ? jg.getName() : "grupo";
                     ((ServerPlayer)var1).displayClientMessage(Component.literal("✔ Piedra unida a la zona \"" + gname + "\".").withStyle(ChatFormatting.GREEN), false);
                  } else {
                     ((ServerPlayer)var1)
                        .displayClientMessage(
                           Component.literal("✔ Zona creada: " + var7.label() + " bloques | Altura: +/-" + var7.height).withStyle(ChatFormatting.GREEN),
                           false
                        );
                  }
               }

               return InteractionResult.SUCCESS;
            }
         }
      }
   }

   private InteractionResult regularChecks(Player var1, Level var2, BlockPos var3, Direction var4, ItemStack var5) {
      if (isBypassing(var1)) {
         return InteractionResult.PASS;
      } else {
         BlockPos var6 = var3.relative(var4);
         BlockState var7 = var2.getBlockState(var3);
         Block var8 = var7.getBlock();
         Claim var9 = ClaimManager.getInstance().getClaimAt(var2, var3);
         if (var9 != null && !var9.canModify(var1) && !isBypassing(var1) && var9.getFlags().blockAllInteractions) {
            deny(var1, "[!] No tienes ningún permiso de interacción en esta zona.");
            return InteractionResult.FAIL;
         } else {
            Claim var10;
            if (isContainer(var2, var3) && (var10 = ClaimManager.getInstance().getClaimAt(var2, var3)) != null && !var10.canModify(var1)) {
               deny(var1, "[!] No puedes abrir contenedores aquí.");
               return InteractionResult.FAIL;
            } else {
               Claim var11;
               if (var8 instanceof AnvilBlock && (var11 = ClaimManager.getInstance().getClaimAt(var2, var3)) != null && !var11.canModify(var1)) {
                  deny(var1, "[!] No puedes usar yunques aquí.");
                  return InteractionResult.FAIL;
               } else {
                  Claim var12;
                  if (var8 instanceof SignBlock && (var12 = ClaimManager.getInstance().getClaimAt(var2, var3)) != null && !var12.canModify(var1)) {
                     deny(var1, "[!] No puedes editar letreros aquí.");
                     return InteractionResult.FAIL;
                  } else {
                     Claim var13;
                     if (var5.getItem() instanceof BucketItem && (var13 = ClaimManager.getInstance().getClaimAt(var2, var6)) != null && !var13.canModify(var1)
                        )
                      {
                        deny(var1, "[!] No puedes colocar fluidos aquí.");
                        return InteractionResult.FAIL;
                     } else {
                        Claim var14;
                        if (isDoorLike(var7) && (var14 = ClaimManager.getInstance().getClaimAt(var2, var3)) != null && !var14.canModify(var1)) {
                           deny(var1, "[!] No puedes usar puertas, botones ni placas aquí.");
                           return InteractionResult.FAIL;
                        } else {
                           Claim var15;
                           if (isInteractiveBlock(var7) && (var15 = ClaimManager.getInstance().getClaimAt(var2, var3)) != null && !var15.canModify(var1)) {
                              deny(var1, "[!] No puedes interactuar aquí.");
                              return InteractionResult.FAIL;
                           } else {
                              Claim var16 = ClaimManager.getInstance().getClaimAt(var2, var3);
                              if (var16 != null && !var16.canModify(var1)) {
                                 deny(var1, "[!] No puedes interactuar en esta zona.");
                                 return InteractionResult.FAIL;
                              } else {
                                 return InteractionResult.PASS;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public void onRightClickItem(RightClickItem var1) {
      Level var2 = var1.getLevel();
      Claim var5;
      Player var3;
      if (!var2.isClientSide
         && !isBypassing(var3 = var1.getEntity())
         && ClaimBlocks.readTierId(var1.getItemStack()) == null
         && (var5 = ClaimManager.getInstance().getClaimAt(var2, var3.blockPosition())) != null
         && !var5.canModify(var3)) {
         deny(var3, "[!] No puedes usar items en esta zona.");
         var1.setCanceled(true);
         var1.setCancellationResult(InteractionResult.FAIL);
      }
   }

   @SubscribeEvent
   public void onTrample(FarmlandTrampleEvent var1) {
      Claim var4;
      if (var1.getLevel() instanceof Level var3
         && !var3.isClientSide
         && (var4 = ClaimManager.getInstance().getClaimAt(var3, var1.getPos())) != null
         && (var4.getFlags().blockTrampling || var4.getFlags().publicMode)) {
         var1.setCanceled(true);
      }
   }

   @SubscribeEvent
   public void onExplosion(Detonate var1) {
      Level var2 = var1.getLevel();
      if (!var2.isClientSide) {
         var1.getAffectedBlocks().removeIf(var1x -> {
            Claim var2x = ClaimManager.getInstance().getClaimAt(var2, var1x);
            return var2x != null && (var2x.getFlags().blockExplosions || var2x.getFlags().publicMode);
         });
      }
   }

   @SubscribeEvent
   public void onPiston(Pre var1) {
      if (var1.getLevel() instanceof Level var3 && !var3.isClientSide) {
         BlockPos var4 = var1.getPos();
         Direction var5 = var1.getDirection();
         Claim var6 = ClaimManager.getInstance().getClaimAt(var3, var4);
         PistonStructureResolver var7 = var1.getStructureHelper();
         if (var7 != null && var7.resolve()) {
            for (BlockPos var11 : var7.getToPush()) {
               if (crossClaimBlocked(var3, var6, var11, var11.relative(var5))) {
                  var1.setCanceled(true);
                  return;
               }
            }

            for (BlockPos var12 : var7.getToDestroy()) {
               if (crossClaimBlocked(var3, var6, var12, var12)) {
                  var1.setCanceled(true);
                  return;
               }
            }
         } else {
            BlockPos var8 = var4.relative(var5);
            if (crossClaimBlocked(var3, var6, var8, var8.relative(var5))) {
               var1.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public void onEnderPearl(EnderPearl var1) {
      ServerPlayer var2 = var1.getPlayer();
      if (var2 != null) {
         Level var3 = var2.level();
         BlockPos var4 = BlockPos.containing(var1.getTargetX(), var1.getTargetY(), var1.getTargetZ());
         Claim var5 = ClaimManager.getInstance().getClaimAt(var3, var4);
         if (var5 != null && !var5.canModify(var2) && !isBypassing(var2) && (var5.getFlags().blockEnderPearl || var5.getFlags().publicMode)) {
            var1.setCanceled(true);
            deny(var2, "[!] No puedes teletransportarte a esta zona.");
         }
      }
   }

   private static boolean crossClaimBlocked(Level var0, Claim var1, BlockPos var2, BlockPos var3) {
      Claim var4 = ClaimManager.getInstance().getClaimAt(var0, var2);
      Claim var5;
      return sameClaim(var4, var5 = ClaimManager.getInstance().getClaimAt(var0, var3)) && sameClaim(var1, var4)
         ? false
         : protectsBuilding(var4) || protectsBuilding(var5) || protectsBuilding(var1);
   }

   private static boolean sameClaim(Claim var0, Claim var1) {
      if (var0 == null && var1 == null) {
         return true;
      } else {
         return var0 != null && var1 != null ? var0.getClaimId().equals(var1.getClaimId()) : false;
      }
   }

   private static boolean protectsBuilding(Claim var0) {
      return var0 == null ? false : var0.getFlags().publicMode || var0.getFlags().blockBuilding;
   }

   public static boolean isContainer(Level var0, BlockPos var1) {
      BlockState var2 = var0.getBlockState(var1);
      Block var3 = var2.getBlock();
      if (!(var3 instanceof ChestBlock)
         && !(var3 instanceof BarrelBlock)
         && !(var3 instanceof ShulkerBoxBlock)
         && !(var3 instanceof DispenserBlock)
         && !(var3 instanceof HopperBlock)) {
         BlockEntity var4 = var0.getBlockEntity(var1);
         return var4 instanceof Container;
      } else {
         return true;
      }
   }

   private static boolean isMatureCrop(BlockState var0) {
      return false;
   }

   private static boolean isDoorLike(BlockState var0) {
      if (var0.is(BlockTags.DOORS)) {
         return true;
      } else if (var0.is(BlockTags.TRAPDOORS)) {
         return true;
      } else if (var0.is(BlockTags.FENCE_GATES)) {
         return true;
      } else {
         return var0.is(BlockTags.BUTTONS) ? true : var0.getBlock() == Blocks.LEVER;
      }
   }

   private static boolean isInteractiveBlock(BlockState var0) {
      Block var1 = var0.getBlock();
      return var1 == Blocks.CRAFTING_TABLE || var1 == Blocks.ENCHANTING_TABLE || var1 == Blocks.GRINDSTONE || var1 == Blocks.BREWING_STAND;
   }

   public static void tickFireSweep(MinecraftServer var0) {
      if (++fireSweepCounter % 40 == 0) {
         for (ServerLevel var1 : var0.getAllLevels()) {
            for (Claim var3 : ClaimManager.getInstance().getClaimsInWorld(var1.dimension().location().toString())) {
               if (var3.getFlags().blockFire || var3.getFlags().publicMode) {
                  for (ServerPlayer var5 : var1.players()) {
                     if (var3.contains(var5.blockPosition())) {
                        extinguishAround(var1, var5.blockPosition(), var3);
                     }
                  }
               }
            }
         }
      }
   }

   private static void extinguishAround(ServerLevel var0, BlockPos var1, Claim var2) {
      int var3 = 6;
      MutableBlockPos var4 = new MutableBlockPos();

      for (int var5 = -var3; var5 <= var3; var5++) {
         for (int var6 = -var3; var6 <= var3; var6++) {
            for (int var7 = -var3; var7 <= var3; var7++) {
               var4.set(var1.getX() + var5, var1.getY() + var6, var1.getZ() + var7);
               Block var8;
               if (var2.contains(var4) && ((var8 = var0.getBlockState(var4).getBlock()) == Blocks.FIRE || var8 == Blocks.SOUL_FIRE)) {
                  var0.setBlock(var4.immutable(), Blocks.AIR.defaultBlockState(), 3);
               }
            }
         }
      }
   }
}
