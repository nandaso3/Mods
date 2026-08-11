package com.claimblocks.event;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimFlags;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.GlobalFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
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

   // ------------------------------------------------------------------------------------------
   // Control de spawn de mobs
   //
   // Dos enganches, con responsabilidades distintas:
   //
   //   1. MobSpawnEvent.FinalizeSpawn -> el hook oficial de spawn. Aporta el MobSpawnType, o sea que
   //      sabemos con certeza si el mob lo genera el mundo (natural, spawner, estructura, patrulla...)
   //      o una accion del jugador (huevo, cubo, /summon, dispensador). Es el que corta de verdad.
   //
   //   2. EntityJoinLevelEvent -> el que ya usaba la 7.6.5. Se conserva porque en vanilla hay mobs
   //      que entran al mundo SIN pasar por finalizeSpawn (crias de animales y aldeanos, golems
   //      construidos con calabaza, gallinas de huevos lanzados). Precisamente por eso aqui hay que
   //      ser conservador: todo eso lo crea el jugador y no debe borrarse.
   // ------------------------------------------------------------------------------------------

   /** Marca en el mob que su spawn fue autorizado, para que la red de seguridad no lo revise. */
   private static final String SPAWN_OK_TAG = "claimblocks_spawn_allowed";

   /**
    * ¿Este spawn lo ha provocado un jugador a proposito? Esos no se bloquean nunca: las flags son
    * para que no aparezcan mobs solos en la zona, no para impedir que el dueno use huevos de spawn,
    * cubos de peces o {@code /summon} dentro de su propia proteccion.
    */
   private static boolean isPlayerDrivenSpawn(MobSpawnType type) {
      if (type == null) {
         return false;
      }

      return switch (type) {
         case SPAWN_EGG, BUCKET, BREEDING, COMMAND, DISPENSER, CONVERSION -> true;
         default -> false;
      };
   }

   /**
    * Mobs "pacificos" a efectos de la flag de animales: animales de granja, fauna acuatica y
    * ambiental. Deliberadamente NO incluye aldeanos, comerciantes ni golems de aldea, que se rigen
    * por la flag general.
    */
   private static boolean isPassiveAnimal(Mob mob) {
      return mob instanceof Animal || mob instanceof WaterAnimal || mob instanceof AmbientCreature;
   }

   /**
    * Mobs que existen porque un jugador los ha creado o los cuida, y que la red de seguridad no debe
    * borrar nunca.
    *
    * <p>Las crias y los golems entran aqui porque en vanilla llegan al mundo sin {@code MobSpawnType}
    * (la cria de animales y aldeanos usa {@code addFreshEntityWithPassengers}, y los golems los crea
    * {@code CarvedPumpkinBlock}), asi que no hay forma de reconocerlos por el tipo de spawn. Se
    * enumeran los dos golems construibles en lugar de {@code AbstractGolem} porque el shulker
    * tambien extiende esa clase.
    */
   private static boolean isPlayerOwnedMob(Mob mob) {
      if (mob.hasCustomName() || mob.isPersistenceRequired()) {
         return true;
      }

      if (mob instanceof TamableAnimal tamable && tamable.isTame()) {
         return true;
      }

      if (mob instanceof IronGolem || mob instanceof SnowGolem) {
         return true;
      }

      return mob instanceof AgeableMob ageable && ageable.isBaby();
   }

   /**
    * Decide si hay que impedir la aparicion de este mob en esa posicion.
    *
    * @param includeMonsterFlag si tambien se aplica la flag historica de monstruos
    *                           ({@code blockMobSpawn}). Se deja fuera en la red de seguridad para no
    *                           alterar el comportamiento que esa flag ya tenia en 7.6.5.
    */
   private static boolean shouldBlockSpawn(Level level, BlockPos pos, Mob mob, boolean includeMonsterFlag) {
      if (GlobalFlags.getInstance().globalNoMobSpawn) {
         return true;
      }

      Claim claim = ClaimManager.getInstance().getClaimAt(level, pos);
      if (claim == null) {
         return false;
      }

      ClaimFlags flags = claim.getFlags();

      // Flag general: no spawnea NADA (hostiles, animales, acuaticos, ambientales y de otros mods).
      if (flags.blockAllMobSpawn) {
         return true;
      }

      if (flags.blockPassiveMobSpawn && isPassiveAnimal(mob)) {
         return true;
      }

      // Flag historica: solo Monster, tal y como se comportaba en 7.6.5.
      return includeMonsterFlag && mob instanceof Monster && (flags.blockMobSpawn || flags.publicMode);
   }

   @SubscribeEvent
   public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
      Mob mob = event.getEntity();

      // El tipo de spawn es la fuente de verdad aqui, asi que no se usan heuristicas del mob: en este
      // punto hasCustomName()/isPersistenceRequired() todavia no reflejan nada util, y usarlas
      // dejaria pasar hostiles nombrados que la flag de monstruos si bloqueaba antes.
      if (isPlayerDrivenSpawn(event.getSpawnType())) {
         mob.getPersistentData().putBoolean(SPAWN_OK_TAG, true);
         return;
      }

      Level level = event.getLevel().getLevel();
      BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
      if (shouldBlockSpawn(level, pos, mob, true)) {
         // setSpawnCancelled es lo que impide el spawn de verdad; cancelar el evento a secas solo se
         // salta finalizeSpawn. Solo vale antes de que la entidad entre al mundo, que es donde estamos.
         event.setSpawnCancelled(true);
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public void onEntityJoin(EntityJoinLevelEvent event) {
      Level level = event.getLevel();
      if (level.isClientSide() || !(event.getEntity() instanceof Mob mob) || mob.tickCount != 0) {
         return;
      }

      // Comportamiento historico de la flag de monstruos, sin tocar: mismo filtro (Monster +
      // tickCount 0) y mismas condiciones que en 7.6.5.
      if (mob instanceof Monster) {
         Claim claim = ClaimManager.getInstance().getClaimAt(level, mob.blockPosition());
         if (claim != null && (claim.getFlags().blockMobSpawn || claim.getFlags().publicMode)) {
            event.setCanceled(true);
            return;
         }
      }

      // Red de seguridad de las flags nuevas.
      //
      // Se limita a mobs HOSTILES a proposito. Todo lo pacifico que llega hasta aqui sin pasar por
      // finalizeSpawn lo ha creado el jugador (crias de animales y aldeanos, golems de calabaza,
      // gallinas de huevos lanzados) o viene de cruzar un portal, casos en los que reaparece con
      // tickCount 0 y borrarlo seria destruir algo que el dueno queria conservar. Los mobs pacificos
      // que de verdad spawnean si pasan por finalizeSpawn, que es donde se cortan.
      if (!(mob instanceof Enemy)) {
         return;
      }

      if (event.loadedFromDisk() || mob.getPersistentData().getBoolean(SPAWN_OK_TAG) || isPlayerOwnedMob(mob)) {
         return;
      }

      if (shouldBlockSpawn(level, mob.blockPosition(), mob, false)) {
         event.setCanceled(true);
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
