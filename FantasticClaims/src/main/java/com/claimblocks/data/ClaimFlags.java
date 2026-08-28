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
    public boolean blockAllMobSpawn = false;
    public boolean blockPassiveMobSpawn = false;

    public boolean get(ClaimFlags.FlagId claimflags$flagid) {
        switch (claimflags$flagid) {
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
            case ALL_MOB_SPAWN:
                return this.blockAllMobSpawn;
            case PASSIVE_MOB_SPAWN:
                return this.blockPassiveMobSpawn;
            default:
                return false;
        }
    }

    public void set(ClaimFlags.FlagId claimflags$flagid, boolean flag) {
        switch (claimflags$flagid) {
            case BUILDING:
                this.blockBuilding = flag;
                break;
            case BREAKING:
                this.blockBreaking = flag;
                break;
            case EXPLOSIONS:
                this.blockExplosions = flag;
                break;
            case FIRE:
                this.blockFire = flag;
                break;
            case MOB_SPAWN:
                this.blockMobSpawn = flag;
                break;
            case PVP:
                this.blockPVP = flag;
                break;
            case MOB_DAMAGE:
                this.blockMobDamage = flag;
                break;
            case ALERTS:
                this.trespasserAlerts = flag;
                break;
            case ITEM_USE:
                this.blockItemUse = flag;
                break;
            case ENTITY_INTERACT:
                this.blockEntityInteract = flag;
                break;
            case TRAMPLING:
                this.blockTrampling = flag;
                break;
            case FLUIDS:
                this.blockFluids = flag;
                break;
            case PVP_ALL:
                this.pvpAll = flag;
                break;
            case TREE_CHOPPING:
                this.blockTreeChopping = flag;
                break;
            case PUBLIC_MODE:
                this.publicMode = flag;
                break;
            case SHOW_WELCOME:
                this.showWelcome = flag;
                break;
            case SHOW_LEAVE:
                this.showLeave = flag;
                break;
            case SHOW_BORDER:
                this.showBorder = flag;
                break;
            case SHOW_PARTICLES:
                this.showParticles = flag;
                break;
            case BURN_HOSTILES:
                this.burnHostiles = flag;
                break;
            case EFFECT_REGEN:
                this.effectRegeneration = flag;
                break;
            case EFFECT_RESIST:
                this.effectResistance = flag;
                break;
            case EFFECT_SPEED:
                this.effectSpeed = flag;
                break;
            case ANIMAL_KILLING:
                this.blockAnimalKilling = flag;
                break;
            case CHEST_ACCESS:
                this.blockChestAccess = flag;
                break;
            case CROP_HARVEST:
                this.blockCropHarvest = flag;
                break;
            case ANVIL_USE:
                this.blockAnvilUse = flag;
                break;
            case ENDER_PEARL:
                this.blockEnderPearl = flag;
                break;
            case SIGN_EDITING:
                this.blockSignEditing = flag;
                break;
            case ALLOW_FLIGHT:
                this.allowFlight = flag;
                break;
            case DOORS_ACCESS:
                this.blockDoorsAccess = flag;
                break;
            case BLOCK_ALL_INTERACT:
                this.blockAllInteractions = flag;
                break;
            case ALL_MOB_SPAWN:
                this.blockAllMobSpawn = flag;
                break;
            case PASSIVE_MOB_SPAWN:
                this.blockPassiveMobSpawn = flag;
        }
    }

    public void toggle(ClaimFlags.FlagId claimflags$flagid) {
        this.set(claimflags$flagid, !this.get(claimflags$flagid));
    }

    public static boolean isPaidOnly(ClaimFlags.FlagId claimflags$flagid) {
        return claimflags$flagid == ClaimFlags.FlagId.EFFECT_REGEN
            || claimflags$flagid == ClaimFlags.FlagId.EFFECT_RESIST
            || claimflags$flagid == ClaimFlags.FlagId.EFFECT_SPEED
            || claimflags$flagid == ClaimFlags.FlagId.ALLOW_FLIGHT;
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
        BLOCK_ALL_INTERACT,
        ALL_MOB_SPAWN,
        PASSIVE_MOB_SPAWN;
    }
}
