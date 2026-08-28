package com.claimblocks.event;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimConfig;
import com.claimblocks.data.ClaimFlags;
import com.claimblocks.data.ClaimGroup;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import com.claimblocks.gui.ClaimMenuHandler;
import com.claimblocks.util.DecorationProtection;
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
import net.minecraft.world.level.block.CropBlock;
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

    private static boolean isBypassing(Player player) {
        return player.hasPermissions(2) && ClaimManager.getInstance().isBypassing(player.getUUID());
    }

    private static boolean denyForVisitor(Claim claim, Player player, boolean flag) {
        if (claim.canModify(player)) {
            return false;
        } else {
            return isBypassing(player) ? false : flag;
        }
    }

    private static void deny(Player player, String s) {
        if (player instanceof ServerPlayer serverplayer && !s.isEmpty()) {
            serverplayer.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void onBreak(BreakEvent breakevent) {
        Player player;
        if (breakevent.getLevel() instanceof Level level && !level.isClientSide && (player = breakevent.getPlayer()) != null && !isBypassing(player)) {
            BlockPos blockpos = breakevent.getPos();
            BlockState blockstate = breakevent.getState();
            Claim claim = ClaimManager.getInstance().getClaimByCenter(level, blockpos);
            ClaimTier claimtier;
            if (claim != null && (claimtier = claim.getTier()) != null && ClaimBlocks.isClaimConcreteForTier(blockstate.getBlock(), claimtier)) {
                if (!claim.isOwner(player) && !player.hasPermissions(2)) {
                    deny(player, "[!] Solo el dueño puede romper esta protección.");
                    breakevent.setCanceled(true);
                } else {
                    ClaimManager.getInstance().removeClaim(level, blockpos);
                    if (!player.getAbilities().instabuild) {
                        ItemStack itemstack = ClaimBlocks.createTierItem(claimtier, 1);
                        if (!player.getInventory().add(itemstack)) {
                            player.drop(itemstack, false);
                        }
                    }

                    level.playSound(null, blockpos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0F, 1.0F);
                    if (player instanceof ServerPlayer) {
                        ((ServerPlayer)player)
                            .displayClientMessage(Component.literal("✔ Zona eliminada. Protección devuelta a tu inventario.").withStyle(ChatFormatting.GREEN), false);
                    }

                    level.setBlockAndUpdate(blockpos, Blocks.AIR.defaultBlockState());
                    breakevent.setCanceled(false);
                }

                return;
            }

            Claim claim1 = ClaimManager.getInstance().getClaimAt(level, blockpos);
            if (claim1 != null && !claim1.canModify(player)) {
                if (!blockstate.is(BlockTags.LOGS) || !claim1.getFlags().publicMode && !claim1.getFlags().blockTreeChopping) {
                    if (!isMatureCrop(blockstate) || !claim1.getFlags().publicMode && !claim1.getFlags().blockCropHarvest) {
                        if (denyForVisitor(claim1, player, claim1.getFlags().blockBreaking || claim1.getFlags().publicMode)) {
                            deny(player, "[!] No puedes romper bloques aquí.");
                            breakevent.setCanceled(true);
                        }
                    } else {
                        deny(player, "[!] No puedes cosechar cultivos aquí.");
                        breakevent.setCanceled(true);
                    }
                } else {
                    deny(player, "[!] No puedes talar árboles en esta zona.");
                    breakevent.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlace(EntityPlaceEvent entityplaceevent) {
        Claim claim;
        Player player;
        Entity entity;
        if (entityplaceevent.getLevel() instanceof Level level
            && !level.isClientSide
            && (entity = entityplaceevent.getEntity()) instanceof Player
            && !isBypassing(player = (Player)entity)
            && (claim = ClaimManager.getInstance().getClaimAt(level, entityplaceevent.getPos())) != null
            && denyForVisitor(claim, player, claim.getFlags().blockBuilding || claim.getFlags().publicMode)) {
            deny(player, "[!] No puedes construir aquí.");
            entityplaceevent.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(RightClickBlock rightclickblock) {
        Level level = rightclickblock.getLevel();
        if (!level.isClientSide) {
            Player player = rightclickblock.getEntity();
            BlockPos blockpos = rightclickblock.getPos();
            ItemStack itemstack = rightclickblock.getItemStack();
            Claim claim = ClaimManager.getInstance().getClaimByCenter(level, blockpos);
            if (claim != null) {
                ClaimTier claimtier1 = claim.getTier();
                BlockState blockstate = level.getBlockState(blockpos);
                if (claimtier1 != null && ClaimBlocks.isClaimConcreteForTier(blockstate.getBlock(), claimtier1) && !player.isShiftKeyDown()) {
                    if (rightclickblock.getHand() == InteractionHand.MAIN_HAND) {
                        if (!claim.isOwner(player) && !player.hasPermissions(2)) {
                            deny(player, "[x] Solo el dueño puede administrar esta zona.");
                        } else if (player instanceof ServerPlayer) {
                            ClaimMenuHandler.open((ServerPlayer)player, claim, 0);
                        }
                    }

                    rightclickblock.setCanceled(true);
                    rightclickblock.setCancellationResult(InteractionResult.SUCCESS);
                    return;
                }
            }

            ClaimTier claimtier;
            if ((claimtier = ClaimBlocks.readTier(itemstack)) != null && !isBypassing(player)) {
                InteractionResult interactionresult1 = this.tryPlaceClaim(
                    player, level, rightclickblock.getHand(), rightclickblock.getFace(), blockpos, itemstack, claimtier
                );
                rightclickblock.setCanceled(true);
                rightclickblock.setCancellationResult(interactionresult1);
            } else {
                InteractionResult interactionresult = this.regularChecks(player, level, blockpos, rightclickblock.getFace(), itemstack);
                if (interactionresult != InteractionResult.PASS) {
                    rightclickblock.setCanceled(true);
                    rightclickblock.setCancellationResult(interactionresult);
                }
            }
        }
    }

    private InteractionResult tryPlaceClaim(
        Player player, Level level, InteractionHand interactionhand, Direction direction, BlockPos blockpos, ItemStack itemstack, ClaimTier claimtier
    ) {
        BlockState blockstate = level.getBlockState(blockpos);
        BlockPos blockpos1 = blockstate.canBeReplaced() ? blockpos : blockpos.relative(direction);
        BlockState blockstate1 = level.getBlockState(blockpos1);
        if (!blockstate1.isAir() && !blockstate1.canBeReplaced()) {
            return InteractionResult.PASS;
        } else {
            ClaimManager claimmanager = ClaimManager.getInstance();
            Claim claim = claimmanager.getClaimAt(level, blockpos1);
            if (claim != null && !claim.canModify(player) && !player.hasPermissions(2)) {
                deny(player, "[x] No puedes construir en esta zona.");
                return InteractionResult.SUCCESS;
            } else {
                List<Claim> list = claimmanager.overlappingClaims(level, blockpos1, claimtier.radius, claimtier.height);
                UUID uuid = null;
                if (!list.isEmpty()) {
                    UUID uuid1 = null;
                    boolean flag = true;
                    Iterator<Claim> iterator = list.iterator();

                    while (true) {
                        if (iterator.hasNext()) {
                            Claim claim1 = (Claim)iterator.next();
                            if (claim1.getGroupId() == null) {
                                flag = false;
                            } else {
                                if (uuid1 == null) {
                                    uuid1 = claim1.getGroupId();
                                    continue;
                                }

                                if (uuid1.equals(claim1.getGroupId())) {
                                    continue;
                                }

                                flag = false;
                            }
                        }

                        if (!flag || uuid1 == null || !claimmanager.isRegistered(uuid1, player.getUUID())) {
                            deny(player, "[x] Esta zona se solaparía con otra existente.");
                            return InteractionResult.SUCCESS;
                        }

                        uuid = uuid1;
                        break;
                    }
                }

                int i;
                if ((i = ClaimManager.getMaxClaimsPerPlayer()) > 0 && !player.hasPermissions(2) && claimmanager.getClaimsOf(player.getUUID()).size() >= i) {
                    deny(player, "[x] Has alcanzado el límite de zonas (" + i + ").");
                    return InteractionResult.SUCCESS;
                } else {
                    Block block = ClaimBlocks.blockForTier(claimtier);
                    level.setBlockAndUpdate(blockpos1, block.defaultBlockState());
                    level.playSound(null, blockpos1, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
                    Claim claim2 = claimmanager.createClaim(level, blockpos1, player, claimtier);
                    if (uuid != null && claim2 != null) {
                        claimmanager.joinClaimToGroup(claim2, uuid);
                    }

                    if (!player.getAbilities().instabuild) {
                        itemstack.shrink(1);
                    }

                    player.swing(interactionhand);
                    if (player instanceof ServerPlayer) {
                        if (uuid != null) {
                            ClaimGroup claimgroup = claimmanager.getGroup(uuid);
                            String s = claimgroup != null ? claimgroup.getName() : "grupo";
                            ((ServerPlayer)player)
                                .displayClientMessage(Component.literal("✔ Piedra unida a la zona \"" + s + "\".").withStyle(ChatFormatting.GREEN), false);
                        } else {
                            ((ServerPlayer)player)
                                .displayClientMessage(
                                    Component.literal("✔ Zona creada: " + claimtier.label() + " bloques | Altura: +/-" + claimtier.height)
                                        .withStyle(ChatFormatting.GREEN),
                                    false
                                );
                        }
                    }

                    return InteractionResult.SUCCESS;
                }
            }
        }
    }

    private InteractionResult regularChecks(Player player, Level level, BlockPos blockpos, Direction direction, ItemStack itemstack) {
        if (isBypassing(player)) {
            return InteractionResult.PASS;
        } else {
            ClaimManager claimmanager = ClaimManager.getInstance();
            Claim claim = claimmanager.getClaimAt(level, blockpos);
            boolean flag = claim != null && !claim.canModify(player);
            if (itemstack.getItem() instanceof BucketItem) {
                Claim claim1 = claimmanager.getClaimAt(level, blockpos.relative(direction));
                if (claim1 != null && !claim1.canModify(player) && claim1.getFlags().blockFluids) {
                    deny(player, "[!] No puedes colocar fluidos aquí.");
                    return InteractionResult.FAIL;
                }
            }

            if (!flag) {
                return InteractionResult.PASS;
            } else {
                ClaimFlags claimflags = claim.getFlags();
                if (claimflags.blockAllInteractions) {
                    deny(player, "[!] No tienes ningún permiso de interacción en esta zona.");
                    return InteractionResult.FAIL;
                } else {
                    BlockState blockstate = level.getBlockState(blockpos);
                    Block block = blockstate.getBlock();
                    if (claimflags.blockChestAccess && isContainer(level, blockpos)) {
                        deny(player, "[!] No puedes abrir contenedores aquí.");
                        return InteractionResult.FAIL;
                    } else if (claimflags.blockAnvilUse && block instanceof AnvilBlock) {
                        deny(player, "[!] No puedes usar yunques aquí.");
                        return InteractionResult.FAIL;
                    } else if (claimflags.blockSignEditing && block instanceof SignBlock) {
                        deny(player, "[!] No puedes editar letreros aquí.");
                        return InteractionResult.FAIL;
                    } else if (claimflags.blockDoorsAccess && isDoorLike(blockstate)) {
                        deny(player, "[!] No puedes usar puertas, botones ni placas aquí.");
                        return InteractionResult.FAIL;
                    } else if (claimflags.blockEntityInteract && isInteractiveBlock(blockstate)) {
                        deny(player, "[!] No puedes interactuar aquí.");
                        return InteractionResult.FAIL;
                    } else {
                        return InteractionResult.PASS;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onRightClickItem(RightClickItem rightclickitem) {
        Level level = rightclickitem.getLevel();
        Claim claim;
        Player player;
        if (!level.isClientSide
            && !isBypassing(player = rightclickitem.getEntity())
            && ClaimBlocks.readTierId(rightclickitem.getItemStack()) == null
            && (claim = ClaimManager.getInstance().getClaimAt(level, player.blockPosition())) != null
            && !claim.canModify(player)
            && claim.getFlags().blockItemUse) {
            deny(player, "[!] No puedes usar items en esta zona.");
            rightclickitem.setCanceled(true);
            rightclickitem.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onTrample(FarmlandTrampleEvent farmlandtrampleevent) {
        Claim claim;
        if (farmlandtrampleevent.getLevel() instanceof Level level
            && !level.isClientSide
            && (claim = ClaimManager.getInstance().getClaimAt(level, farmlandtrampleevent.getPos())) != null
            && (claim.getFlags().blockTrampling || claim.getFlags().publicMode)) {
            farmlandtrampleevent.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExplosion(Detonate detonate) {
        Level level = detonate.getLevel();
        if (!level.isClientSide) {
            detonate.getAffectedBlocks().removeIf(blockpos -> {
                Claim claim = ClaimManager.getInstance().getClaimAt(level, blockpos);
                return claim != null && (claim.getFlags().blockExplosions || claim.getFlags().publicMode);
            });
            detonate.getAffectedEntities().removeIf(entity -> {
                if (!ClaimConfig.get().protectDecorationFromExplosions) {
                    return false;
                } else if (!DecorationProtection.isDecoration(entity)) {
                    return false;
                } else {
                    Claim claim = DecorationProtection.claimFor(level, entity);
                    return claim != null && (claim.getFlags().blockExplosions || claim.getFlags().publicMode);
                }
            });
        }
    }

    @SubscribeEvent
    public void onPiston(Pre pre) {
        if (pre.getLevel() instanceof Level level && !level.isClientSide) {
            BlockPos blockpos = pre.getPos();
            Direction direction = pre.getDirection();
            Claim claim = ClaimManager.getInstance().getClaimAt(level, blockpos);
            PistonStructureResolver pistonstructureresolver = pre.getStructureHelper();
            if (pistonstructureresolver != null && pistonstructureresolver.resolve()) {
                for (BlockPos blockpos2 : pistonstructureresolver.getToPush()) {
                    if (crossClaimBlocked(level, claim, blockpos2, blockpos2.relative(direction))) {
                        pre.setCanceled(true);
                        return;
                    }
                }

                for (BlockPos blockpos3 : pistonstructureresolver.getToDestroy()) {
                    if (crossClaimBlocked(level, claim, blockpos3, blockpos3)) {
                        pre.setCanceled(true);
                        return;
                    }
                }
            } else {
                BlockPos blockpos1 = blockpos.relative(direction);
                if (crossClaimBlocked(level, claim, blockpos1, blockpos1.relative(direction))) {
                    pre.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public void onEnderPearl(EnderPearl enderpearl) {
        ServerPlayer serverplayer = enderpearl.getPlayer();
        if (serverplayer != null) {
            Level level = serverplayer.level();
            BlockPos blockpos = BlockPos.containing(enderpearl.getTargetX(), enderpearl.getTargetY(), enderpearl.getTargetZ());
            Claim claim = ClaimManager.getInstance().getClaimAt(level, blockpos);
            if (claim != null
                && !claim.canModify(serverplayer)
                && !isBypassing(serverplayer)
                && (claim.getFlags().blockEnderPearl || claim.getFlags().publicMode)) {
                enderpearl.setCanceled(true);
                deny(serverplayer, "[!] No puedes teletransportarte a esta zona.");
            }
        }
    }

    private static boolean crossClaimBlocked(Level level, Claim claim, BlockPos blockpos, BlockPos blockpos1) {
        Claim claim2 = ClaimManager.getInstance().getClaimAt(level, blockpos);
        Claim claim1;
        return sameClaim(claim2, claim1 = ClaimManager.getInstance().getClaimAt(level, blockpos1)) && sameClaim(claim, claim2)
            ? false
            : protectsBuilding(claim2) || protectsBuilding(claim1) || protectsBuilding(claim);
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

    public static boolean isContainer(Level level, BlockPos blockpos) {
        BlockState blockstate = level.getBlockState(blockpos);
        Block block = blockstate.getBlock();
        if (!(block instanceof ChestBlock)
            && !(block instanceof BarrelBlock)
            && !(block instanceof ShulkerBoxBlock)
            && !(block instanceof DispenserBlock)
            && !(block instanceof HopperBlock)) {
            BlockEntity blockentity = level.getBlockEntity(blockpos);
            return blockentity instanceof Container;
        } else {
            return true;
        }
    }

    private static boolean isMatureCrop(BlockState blockstate) {
        return blockstate.getBlock() instanceof CropBlock cropblock ? cropblock.isMaxAge(blockstate) : false;
    }

    private static boolean isDoorLike(BlockState blockstate) {
        if (blockstate.is(BlockTags.DOORS)) {
            return true;
        } else if (blockstate.is(BlockTags.TRAPDOORS)) {
            return true;
        } else if (blockstate.is(BlockTags.FENCE_GATES)) {
            return true;
        } else {
            return blockstate.is(BlockTags.BUTTONS) ? true : blockstate.getBlock() == Blocks.LEVER;
        }
    }

    private static boolean isInteractiveBlock(BlockState blockstate) {
        Block block = blockstate.getBlock();
        return block == Blocks.CRAFTING_TABLE || block == Blocks.ENCHANTING_TABLE || block == Blocks.GRINDSTONE || block == Blocks.BREWING_STAND;
    }

    public static void tickFireSweep(MinecraftServer minecraftserver) {
        if (++fireSweepCounter % ClaimConfig.get().fireSweepIntervalTicks == 0) {
            for (ServerLevel serverlevel : minecraftserver.getAllLevels()) {
                for (Claim claim : ClaimManager.getInstance().getClaimsInWorld(serverlevel.dimension().location().toString())) {
                    if (claim.getFlags().blockFire || claim.getFlags().publicMode) {
                        for (ServerPlayer serverplayer : serverlevel.players()) {
                            if (claim.contains(serverplayer.blockPosition())) {
                                extinguishAround(serverlevel, serverplayer.blockPosition(), claim);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void extinguishAround(ServerLevel serverlevel, BlockPos blockpos, Claim claim) {
        int i = ClaimConfig.get().fireSweepRadius;
        MutableBlockPos mutableblockpos = new MutableBlockPos();

        for (int j = -i; j <= i; j++) {
            for (int k = -i; k <= i; k++) {
                for (int l = -i; l <= i; l++) {
                    mutableblockpos.set(blockpos.getX() + j, blockpos.getY() + k, blockpos.getZ() + l);
                    Block block;
                    if (claim.contains(mutableblockpos)
                        && ((block = serverlevel.getBlockState(mutableblockpos).getBlock()) == Blocks.FIRE || block == Blocks.SOUL_FIRE)) {
                        serverlevel.setBlock(mutableblockpos.immutable(), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}
