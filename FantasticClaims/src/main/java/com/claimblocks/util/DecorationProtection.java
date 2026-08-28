package com.claimblocks.util;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimConfig;
import com.claimblocks.data.ClaimFlags;
import com.claimblocks.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

public final class DecorationProtection {
    private DecorationProtection() {
    }

    public static boolean isDecoration(Entity entity) {
        return !ClaimConfig.get().protectDecoration ? false : entity instanceof HangingEntity || entity instanceof ArmorStand;
    }

    public static Claim claimFor(Level level, Entity entity) {
        if (level != null && entity != null) {
            ClaimManager claimmanager = ClaimManager.getInstance();
            BlockPos blockpos = entity.blockPosition();
            Claim claim = claimmanager.getClaimAt(level, blockpos);
            if (claim != null) {
                return claim;
            } else {
                Direction direction = entity.getDirection();
                return direction != null ? claimmanager.getClaimAt(level, blockpos.relative(direction.getOpposite())) : null;
            }
        } else {
            return null;
        }
    }

    private static boolean isBypassing(Player player) {
        return player.hasPermissions(2) && ClaimManager.getInstance().isBypassing(player.getUUID());
    }

    public static boolean blocksPlayer(Claim claim, Player player) {
        if (claim == null || player == null) {
            return false;
        } else if (!claim.canModify(player) && !isBypassing(player)) {
            ClaimFlags claimflags = claim.getFlags();
            return claimflags.blockBuilding || claimflags.blockEntityInteract || claimflags.publicMode;
        } else {
            return false;
        }
    }

    public static boolean blocksDamage(Entity entity, DamageSource damagesource) {
        if (!isDecoration(entity)) {
            return false;
        } else {
            Level level = entity.level();
            if (level != null && !level.isClientSide()) {
                Claim claim = claimFor(level, entity);
                if (claim == null) {
                    return false;
                } else {
                    Player player = responsiblePlayer(damagesource);
                    if (player != null) {
                        return blocksPlayer(claim, player);
                    } else {
                        ClaimFlags claimflags = claim.getFlags();
                        return damagesource != null && damagesource.is(DamageTypeTags.IS_EXPLOSION)
                            ? ClaimConfig.get().protectDecorationFromExplosions && (claimflags.blockExplosions || claimflags.publicMode)
                            : claimflags.blockBuilding || claimflags.publicMode;
                    }
                }
            } else {
                return false;
            }
        }
    }

    public static Player responsiblePlayer(DamageSource damagesource) {
        if (damagesource == null) {
            return null;
        } else {
            Entity entity = damagesource.getEntity();
            if (entity instanceof Player) {
                return (Player)entity;
            } else {
                Entity entity1 = damagesource.getDirectEntity();
                return entity1 instanceof Player ? (Player)entity1 : ownerOf(entity1);
            }
        }
    }

    public static Player ownerOf(Entity entity) {
        if (entity instanceof Projectile) {
            Entity entity1 = ((Projectile)entity).getOwner();
            if (entity1 instanceof Player) {
                return (Player)entity1;
            }
        }

        return null;
    }
}
