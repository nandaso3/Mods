package com.claimblocks.data;

public class ClaimFlags {
   public boolean blockBuilding = true;
   public boolean blockBreaking = true;
   public boolean blockExplosions = true;
   public boolean blockFire = true;
   public boolean blockMobSpawn = false;
   public boolean blockPVP = true;
   public boolean blockMobDamage = false;
   public boolean trespasserAlerts = false;
   public boolean blockItemUse = true;
   public boolean blockEntityInteract = true;
   public boolean blockTrampling = true;
   public boolean blockFluids = true;
   public boolean pvpAll = false;
   public boolean blockTreeChopping = true;
   public boolean publicMode = false;
   public boolean showWelcome = false;
   public String welcomeMessage = "";
   public boolean showLeave = false;
   public String leaveMessage = "";
   public boolean showBorder = false;
   public boolean showParticles = false;
   public String borderParticle = "minecraft:happy_villager";
   public int particleDensity = 10;
   public boolean burnHostiles = true;
   public boolean effectRegeneration = false;
   public boolean effectResistance = false;
   public boolean effectSpeed = false;
   public boolean blockAnimalKilling = true;
   public boolean blockChestAccess = true;
   public boolean blockCropHarvest = true;
   public boolean blockAnvilUse = true;
   public boolean blockEnderPearl = true;
   public boolean blockSignEditing = true;
   public boolean allowFlight = false;
   public boolean blockDoorsAccess = true;
   public boolean blockAllInteractions = true;

   public boolean get(ClaimFlags.FlagId var1) {
      switch (var1) {
         case BUILDING:
            return this.blockBuilding;
         case BREAKING:
            return this.blockBreaking;
         case EXPLOSIONS:
            return this.blockExplosions;
         case FIRE:
            return this.blockFire;
         case MOB_SPAWN:
            return this.blockMobSpawn;
         case PVP:
            return this.blockPVP;
         case MOB_DAMAGE:
            return this.blockMobDamage;
         case ALERTS:
            return this.trespasserAlerts;
         case ITEM_USE:
            return this.blockItemUse;
         case ENTITY_INTERACT:
            return this.blockEntityInteract;
         case TRAMPLING:
            return this.blockTrampling;
         case FLUIDS:
            return this.blockFluids;
         case PVP_ALL:
            return this.pvpAll;
         case TREE_CHOPPING:
            return this.blockTreeChopping;
         case PUBLIC_MODE:
            return this.publicMode;
         case SHOW_WELCOME:
            return this.showWelcome;
         case SHOW_LEAVE:
            return this.showLeave;
         case SHOW_BORDER:
            return this.showBorder;
         case SHOW_PARTICLES:
            return this.showParticles;
         case BURN_HOSTILES:
            return this.burnHostiles;
         case EFFECT_REGEN:
            return this.effectRegeneration;
         case EFFECT_RESIST:
            return this.effectResistance;
         case EFFECT_SPEED:
            return this.effectSpeed;
         case ANIMAL_KILLING:
            return this.blockAnimalKilling;
         case CHEST_ACCESS:
            return this.blockChestAccess;
         case CROP_HARVEST:
            return this.blockCropHarvest;
         case ANVIL_USE:
            return this.blockAnvilUse;
         case ENDER_PEARL:
            return this.blockEnderPearl;
         case SIGN_EDITING:
            return this.blockSignEditing;
         case ALLOW_FLIGHT:
            return this.allowFlight;
         case DOORS_ACCESS:
            return this.blockDoorsAccess;
         case BLOCK_ALL_INTERACT:
            return this.blockAllInteractions;
         default:
            throw new IncompatibleClassChangeError();
      }
   }

   public void set(ClaimFlags.FlagId var1, boolean var2) {
      switch (var1) {
         case BUILDING:
            this.blockBuilding = var2;
            break;
         case BREAKING:
            this.blockBreaking = var2;
            break;
         case EXPLOSIONS:
            this.blockExplosions = var2;
            break;
         case FIRE:
            this.blockFire = var2;
            break;
         case MOB_SPAWN:
            this.blockMobSpawn = var2;
            break;
         case PVP:
            this.blockPVP = var2;
            break;
         case MOB_DAMAGE:
            this.blockMobDamage = var2;
            break;
         case ALERTS:
            this.trespasserAlerts = var2;
            break;
         case ITEM_USE:
            this.blockItemUse = var2;
            break;
         case ENTITY_INTERACT:
            this.blockEntityInteract = var2;
            break;
         case TRAMPLING:
            this.blockTrampling = var2;
            break;
         case FLUIDS:
            this.blockFluids = var2;
            break;
         case PVP_ALL:
            this.pvpAll = var2;
            break;
         case TREE_CHOPPING:
            this.blockTreeChopping = var2;
            break;
         case PUBLIC_MODE:
            this.publicMode = var2;
            break;
         case SHOW_WELCOME:
            this.showWelcome = var2;
            break;
         case SHOW_LEAVE:
            this.showLeave = var2;
            break;
         case SHOW_BORDER:
            this.showBorder = var2;
            break;
         case SHOW_PARTICLES:
            this.showParticles = var2;
            break;
         case BURN_HOSTILES:
            this.burnHostiles = var2;
            break;
         case EFFECT_REGEN:
            this.effectRegeneration = var2;
            break;
         case EFFECT_RESIST:
            this.effectResistance = var2;
            break;
         case EFFECT_SPEED:
            this.effectSpeed = var2;
            break;
         case ANIMAL_KILLING:
            this.blockAnimalKilling = var2;
            break;
         case CHEST_ACCESS:
            this.blockChestAccess = var2;
            break;
         case CROP_HARVEST:
            this.blockCropHarvest = var2;
            break;
         case ANVIL_USE:
            this.blockAnvilUse = var2;
            break;
         case ENDER_PEARL:
            this.blockEnderPearl = var2;
            break;
         case SIGN_EDITING:
            this.blockSignEditing = var2;
            break;
         case ALLOW_FLIGHT:
            this.allowFlight = var2;
            break;
         case DOORS_ACCESS:
            this.blockDoorsAccess = var2;
            break;
         case BLOCK_ALL_INTERACT:
            this.blockAllInteractions = var2;
      }
   }

   public void toggle(ClaimFlags.FlagId var1) {
      this.set(var1, !this.get(var1));
   }

   public static boolean isPaidOnly(ClaimFlags.FlagId var0) {
      return var0 == ClaimFlags.FlagId.EFFECT_REGEN
         || var0 == ClaimFlags.FlagId.EFFECT_RESIST
         || var0 == ClaimFlags.FlagId.EFFECT_SPEED
         || var0 == ClaimFlags.FlagId.ALLOW_FLIGHT;
   }

   public static enum FlagId {
      BUILDING,
      BREAKING,
      EXPLOSIONS,
      FIRE,
      MOB_SPAWN,
      PVP,
      MOB_DAMAGE,
      ALERTS,
      ITEM_USE,
      ENTITY_INTERACT,
      TRAMPLING,
      FLUIDS,
      PVP_ALL,
      TREE_CHOPPING,
      PUBLIC_MODE,
      SHOW_WELCOME,
      SHOW_LEAVE,
      SHOW_BORDER,
      SHOW_PARTICLES,
      BURN_HOSTILES,
      EFFECT_REGEN,
      EFFECT_RESIST,
      EFFECT_SPEED,
      ANIMAL_KILLING,
      CHEST_ACCESS,
      CROP_HARVEST,
      ANVIL_USE,
      ENDER_PEARL,
      SIGN_EDITING,
      ALLOW_FLIGHT,
      DOORS_ACCESS,
      BLOCK_ALL_INTERACT;
   }
}
