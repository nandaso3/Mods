package com.claimblocks.event;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.GlobalFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class EntityProtectionEvents {
   private static final String BARRIER_TAG = "claimblocks_barrier_tick";
   private static final long BARRIER_WINDOW = 200L;

   private static boolean isBypassing(Player player) {
      return player.hasPermissions(2) && ClaimManager.getInstance().isBypassing(player.getUUID());
   }

   private static void deny(Player player, String msg) {
      if (player instanceof ServerPlayer sp) {
         sp.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.RED), true);
      }
   }

   @SubscribeEvent
   public void onHostileTick(LivingTickEvent event) {
      LivingEntity entity = event.getEntity();
      Level level = entity.level();
      if (!level.isClientSide() && entity instanceof Enemy) {
         if (entity.tickCount % 5 == 0) {
            Claim claim = ClaimManager.getInstance().getClaimAt(level, entity.blockPosition());
            if (claim != null && claim.getFlags().burnHostiles) {
               repelHostile(claim, entity);
            }
         }
      }
   }

   private static void repelHostile(Claim claim, LivingEntity mob) {
      double ex = mob.getX();
      double ez = mob.getZ();
      int r = claim.getRadius();
      double cx = (double)claim.getX() + 0.5;
      double cz = (double)claim.getZ() + 0.5;
      double toWest = ex - (cx - (double)r);
      double toEast = cx + (double)r - ex;
      double toNorth = ez - (cz - (double)r);
      double toSouth = cz + (double)r - ez;
      double dirX = 0.0;
      double dirZ = 0.0;
      double min = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));
      if (min == toWest) {
         dirX = -1.0;
      } else if (min == toEast) {
         dirX = 1.0;
      } else if (min == toNorth) {
         dirZ = -1.0;
      } else {
         dirZ = 1.0;
      }

      mob.setDeltaMovement(dirX * 1.1, 0.42, dirZ * 1.1);
      mob.hasImpulse = true;
      mob.hurtMarked = true;
      mob.setSecondsOnFire(3);
      mob.invulnerableTime = 0;
      mob.hurt(mob.damageSources().generic(), 3.0F);
      mob.getPersistentData().putLong("claimblocks_barrier_tick", mob.level().getGameTime());
   }

   private static boolean killedByBarrier(LivingEntity entity, Level level) {
      long tick = entity.getPersistentData().getLong("claimblocks_barrier_tick");
      if (tick > 0L && level.getGameTime() - tick <= 200L) {
         return true;
      } else {
         Claim claim = ClaimManager.getInstance().getClaimAt(level, entity.blockPosition());
         return claim != null && claim.getFlags().burnHostiles;
      }
   }

   @SubscribeEvent
   public void onHostileDrops(LivingDropsEvent event) {
      LivingEntity entity = event.getEntity();
      Level level = entity.level();
      if (!level.isClientSide() && entity instanceof Enemy) {
         DamageSource src = event.getSource();
         if (src == null || !(src.getEntity() instanceof Player)) {
            if (killedByBarrier(entity, level)) {
               event.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public void onHostileXp(LivingExperienceDropEvent event) {
      LivingEntity entity = event.getEntity();
      if (entity instanceof Enemy) {
         if (event.getAttackingPlayer() == null) {
            if (killedByBarrier(entity, entity.level())) {
               event.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public void onEntityJoin(EntityJoinLevelEvent event) {
      if (!event.getLevel().isClientSide() && event.getEntity() instanceof Monster monster && monster.tickCount == 0) {
         Claim claim = ClaimManager.getInstance().getClaimAt(event.getLevel(), monster.blockPosition());
         if (claim != null && (claim.getFlags().blockMobSpawn || claim.getFlags().publicMode)) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public void onLivingHurt(LivingHurtEvent event) {
      LivingEntity victim = event.getEntity();
      Level level = victim.level();
      if (!level.isClientSide()) {
         DamageSource source = event.getSource();
         Entity attacker = source.getEntity();
         Claim claim = ClaimManager.getInstance().getClaimAt(level, victim.blockPosition());
         if (victim instanceof Player && attacker instanceof Player && !GlobalFlags.getInstance().globalPVP) {
            deny((Player)attacker, "[!] El PVP está desactivado en este servidor.");
            event.setCanceled(true);
         } else if (claim != null) {
            if (victim instanceof Player && attacker instanceof Player pAttacker) {
               if (isBypassing(pAttacker)) {
                  return;
               }

               if (claim.getFlags().blockPVP && (!claim.canModify(pAttacker) || !claim.canModify((Player)victim) || claim.getFlags().publicMode)) {
                  deny(pAttacker, "[!] El PVP está desactivado en esta zona.");
                  event.setCanceled(true);
                  return;
               }
            }

            if (victim instanceof Player
               && attacker instanceof LivingEntity
               && !(attacker instanceof Player)
               && (claim.getFlags().blockMobDamage || claim.getFlags().publicMode)) {
               event.setCanceled(true);
            } else {
               if (victim instanceof Animal
                  && attacker instanceof Player pAttacker
                  && !claim.canModify(pAttacker)
                  && !isBypassing(pAttacker)
                  && (claim.getFlags().publicMode || claim.getFlags().blockAnimalKilling)) {
                  deny(pAttacker, "[!] No puedes matar animales en esta zona.");
                  event.setCanceled(true);
                  return;
               }

               if (claim.getFlags().blockExplosions && source.is(DamageTypeTags.IS_EXPLOSION)) {
                  event.setCanceled(true);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public void onAttackEntity(AttackEntityEvent event) {
      Player player = event.getEntity();
      Level level = player.level();
      if (!level.isClientSide() && !isBypassing(player)) {
         Entity target = event.getTarget();
         Claim claim = ClaimManager.getInstance().getClaimAt(level, target.blockPosition());
         if (claim != null
            && !claim.canModify(player)
            && (claim.getFlags().publicMode || claim.getFlags().blockAnimalKilling || claim.getFlags().blockEntityInteract || claim.getFlags().blockBuilding)) {
            deny(player, "[!] No puedes dañar entidades aquí.");
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public void onEntityInteract(EntityInteract event) {
      Level level = event.getLevel();
      Player player = event.getEntity();
      if (!level.isClientSide() && !isBypassing(player)) {
         Entity target = event.getTarget();
         Claim claim = ClaimManager.getInstance().getClaimAt(level, target.blockPosition());
         if (claim != null && !claim.canModify(player)) {
            if (claim.getFlags().blockAllInteractions) {
               deny(player, "[!] No tienes ningún permiso de interacción en esta zona.");
               event.setCanceled(true);
            } else if ((!claim.getFlags().blockEntityInteract || !(target instanceof ItemFrame)) && target instanceof Container) {
               deny(player, "[!] No puedes abrir este contenedor aquí.");
               event.setCanceled(true);
            } else {
               deny(player, "[!] No puedes interactuar con entidades aquí.");
               event.setCanceled(true);
            }
         }
      }
   }
}
