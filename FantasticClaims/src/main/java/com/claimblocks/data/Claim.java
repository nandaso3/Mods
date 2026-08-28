package com.claimblocks.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class Claim {
    private final UUID claimId;
    private UUID ownerUUID;
    private String ownerName;
    private String tierId;
    private final int radius;
    private final int height;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private long createdAt;
    private UUID groupId;
    private final List<UUID> members = new ArrayList<>();
    private final List<String> memberNames = new ArrayList<>();
    private final Set<UUID> bannedPlayers = new HashSet<>();
    private final ClaimFlags flags = new ClaimFlags();

    public Claim(UUID uuid, UUID uuid1, String s, String s1, int i, int j, String s2, int k, int l, int i1) {
        this.claimId = uuid;
        this.ownerUUID = uuid1;
        this.ownerName = s;
        this.tierId = s1;
        this.radius = i;
        this.height = j;
        this.world = s2;
        this.x = k;
        this.y = l;
        this.z = i1;
        this.createdAt = System.currentTimeMillis();
    }

    public static Claim create(UUID uuid, String s, ClaimTier claimtier, String s1, BlockPos blockpos) {
        return new Claim(
            UUID.randomUUID(), uuid, s, claimtier.id, claimtier.radius, claimtier.height, s1, blockpos.getX(), blockpos.getY(), blockpos.getZ()
        );
    }

    public UUID getClaimId() {
        return this.claimId;
    }

    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public String getTierId() {
        return this.tierId;
    }

    public int getRadius() {
        return this.radius;
    }

    public int getHeight() {
        return this.effectiveHeight();
    }

    public int getOwnHeight() {
        return this.height;
    }

    public UUID getGroupId() {
        return this.groupId;
    }

    public void setGroupId(UUID uuid) {
        this.groupId = uuid;
    }

    public boolean isGrouped() {
        return this.groupId != null;
    }

    public Claim getMother() {
        return this.groupId == null ? null : ClaimManager.getInstance().getMotherClaim(this.groupId);
    }

    public boolean isGroupMother() {
        if (this.groupId == null) {
            return false;
        } else {
            ClaimGroup claimgroup = ClaimManager.getInstance().getGroup(this.groupId);
            return claimgroup != null && this.claimId.equals(claimgroup.getMotherClaimId());
        }
    }

    public int effectiveHeight() {
        Claim claim1;
        return this.groupId != null && (claim1 = ClaimManager.getInstance().getMotherClaim(this.groupId)) != null && claim1 != this
            ? claim1.height
            : this.height;
    }

    public String getWorld() {
        return this.world;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public BlockPos getCenter() {
        return new BlockPos(this.x, this.y, this.z);
    }

    public List<UUID> getMembers() {
        return this.members;
    }

    public List<String> getMemberNames() {
        return this.memberNames;
    }

    private Claim banHolder() {
        Claim claim1;
        return this.groupId != null && (claim1 = ClaimManager.getInstance().getMotherClaim(this.groupId)) != null ? claim1 : this;
    }

    public Set<UUID> getBannedPlayers() {
        return this.banHolder().bannedPlayers;
    }

    public Set<UUID> getOwnBannedPlayers() {
        return this.bannedPlayers;
    }

    public ClaimFlags getFlags() {
        Claim claim1;
        return this.groupId != null && (claim1 = ClaimManager.getInstance().getMotherClaim(this.groupId)) != null && claim1 != this ? claim1.flags : this.flags;
    }

    public ClaimFlags getOwnFlags() {
        return this.flags;
    }

    public long getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(long i) {
        this.createdAt = i;
    }

    public ClaimTier getTier() {
        ClaimTier claimtier;
        return this.tierId != null && (claimtier = ClaimTier.byId(this.tierId)) != null ? claimtier : ClaimTier.closestMatch(this.radius, this.height);
    }

    public String sizeLabel() {
        if (this.tierId != null && this.tierId.startsWith("claimstone_")) {
            return this.tierId.substring("claimstone_".length());
        } else {
            ClaimTier claimtier = this.getTier();
            return claimtier == null ? this.radius + "x" + this.radius : claimtier.label();
        }
    }

    public boolean contains(BlockPos blockpos) {
        if (Math.abs(blockpos.getX() - this.x) <= this.radius && Math.abs(blockpos.getZ() - this.z) <= this.radius) {
            int i = this.y;
            int j = this.height;
            Claim claim1;
            if (this.groupId != null && (claim1 = ClaimManager.getInstance().getMotherClaim(this.groupId)) != null) {
                i = claim1.y;
                j = claim1.height;
            }

            return blockpos.getY() - i <= j && i - blockpos.getY() <= j;
        } else {
            return false;
        }
    }

    public boolean overlapsWith(BlockPos blockpos, int i, int j) {
        return Math.abs(blockpos.getX() - this.x) < this.radius + i
            && Math.abs(blockpos.getZ() - this.z) < this.radius + i
            && Math.abs(blockpos.getY() - this.y) < this.height + j;
    }

    public AABB getBoundingBox() {
        return new AABB(
            (double)(this.x - this.radius),
            (double)(this.y - this.height),
            (double)(this.z - this.radius),
            (double)(this.x + this.radius + 1),
            (double)(this.y + this.height + 1),
            (double)(this.z + this.radius + 1)
        );
    }

    public boolean isOwner(UUID uuid) {
        return this.ownerUUID != null && this.ownerUUID.equals(uuid);
    }

    public boolean isOwner(Player player) {
        return this.isOwner(player.getUUID());
    }

    public boolean isMember(UUID uuid) {
        return this.members.contains(uuid);
    }

    public boolean isMember(Player player) {
        return this.isMember(player.getUUID());
    }

    public boolean isBanned(UUID uuid) {
        return this.banHolder().bannedPlayers.contains(uuid);
    }

    public boolean canModify(Player player) {
        ClaimGroup claimgroup;
        return !this.isOwner(player) && !this.isMember(player) && !player.hasPermissions(2)
            ? this.groupId != null && (claimgroup = ClaimManager.getInstance().getGroup(this.groupId)) != null && claimgroup.isRegistered(player.getUUID())
            : true;
    }

    public void addMember(UUID uuid, String s) {
        if (!this.members.contains(uuid)) {
            this.members.add(uuid);
            this.memberNames.add(s == null ? "" : s);
        }
    }

    public void removeMember(UUID uuid) {
        int i = this.members.indexOf(uuid);
        if (i >= 0) {
            this.members.remove(i);
            if (i < this.memberNames.size()) {
                this.memberNames.remove(i);
            }
        }
    }

    public void banPlayer(UUID uuid) {
        this.banHolder().bannedPlayers.add(uuid);
        this.removeMember(uuid);
        if (this.groupId != null) {
            for (Claim claim1 : ClaimManager.getInstance().getGroupClaims(this.groupId)) {
                claim1.removeMember(uuid);
            }
        }
    }

    public void unbanPlayer(UUID uuid) {
        this.banHolder().bannedPlayers.remove(uuid);
        this.bannedPlayers.remove(uuid);
        if (this.groupId != null) {
            for (Claim claim1 : ClaimManager.getInstance().getGroupClaims(this.groupId)) {
                claim1.bannedPlayers.remove(uuid);
            }
        }
    }

    public void setOwner(UUID uuid, String s) {
        this.ownerUUID = uuid;
        this.ownerName = s;
    }

    public void setTierId(String s) {
        this.tierId = s;
    }

    public JsonObject toJson() {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("claimId", this.claimId.toString());
        jsonobject.addProperty("ownerUUID", this.ownerUUID == null ? "" : this.ownerUUID.toString());
        jsonobject.addProperty("ownerName", this.ownerName == null ? "" : this.ownerName);
        if (this.tierId != null) {
            jsonobject.addProperty("tierId", this.tierId);
        }

        jsonobject.addProperty("radius", this.radius);
        jsonobject.addProperty("height", this.height);
        jsonobject.addProperty("world", this.world);
        jsonobject.addProperty("x", this.x);
        jsonobject.addProperty("y", this.y);
        jsonobject.addProperty("z", this.z);
        jsonobject.addProperty("createdAt", this.createdAt);
        if (this.groupId != null) {
            jsonobject.addProperty("groupId", this.groupId.toString());
        }

        JsonArray jsonarray = new JsonArray();

        for (UUID uuid : this.members) {
            jsonarray.add(uuid.toString());
        }

        jsonobject.add("members", jsonarray);
        JsonArray jsonarray1 = new JsonArray();

        for (String s : this.memberNames) {
            jsonarray1.add(s == null ? "" : s);
        }

        jsonobject.add("memberNames", jsonarray1);
        JsonArray jsonarray2 = new JsonArray();

        for (UUID uuid1 : this.bannedPlayers) {
            jsonarray2.add(uuid1.toString());
        }

        jsonobject.add("bannedPlayers", jsonarray2);
        JsonObject jsonobject1 = new JsonObject();
        jsonobject1.addProperty("blockBuilding", this.flags.blockBuilding);
        jsonobject1.addProperty("blockBreaking", this.flags.blockBreaking);
        jsonobject1.addProperty("blockExplosions", this.flags.blockExplosions);
        jsonobject1.addProperty("blockFire", this.flags.blockFire);
        jsonobject1.addProperty("blockMobSpawn", this.flags.blockMobSpawn);
        jsonobject1.addProperty("blockPVP", this.flags.blockPVP);
        jsonobject1.addProperty("blockMobDamage", this.flags.blockMobDamage);
        jsonobject1.addProperty("trespasserAlerts", this.flags.trespasserAlerts);
        jsonobject1.addProperty("blockItemUse", this.flags.blockItemUse);
        jsonobject1.addProperty("blockEntityInteract", this.flags.blockEntityInteract);
        jsonobject1.addProperty("blockTrampling", this.flags.blockTrampling);
        jsonobject1.addProperty("blockFluids", this.flags.blockFluids);
        jsonobject1.addProperty("pvpAll", this.flags.pvpAll);
        jsonobject1.addProperty("blockTreeChopping", this.flags.blockTreeChopping);
        jsonobject1.addProperty("publicMode", this.flags.publicMode);
        jsonobject1.addProperty("showWelcome", this.flags.showWelcome);
        jsonobject1.addProperty("welcomeMessage", this.flags.welcomeMessage == null ? "" : this.flags.welcomeMessage);
        jsonobject1.addProperty("showLeave", this.flags.showLeave);
        jsonobject1.addProperty("leaveMessage", this.flags.leaveMessage == null ? "" : this.flags.leaveMessage);
        jsonobject1.addProperty("showBorder", this.flags.showBorder);
        jsonobject1.addProperty("showParticles", this.flags.showParticles);
        jsonobject1.addProperty("borderParticle", this.flags.borderParticle == null ? "happy" : this.flags.borderParticle);
        jsonobject1.addProperty("particleDensity", this.flags.particleDensity);
        jsonobject1.addProperty("burnHostiles", this.flags.burnHostiles);
        jsonobject1.addProperty("effectRegeneration", this.flags.effectRegeneration);
        jsonobject1.addProperty("effectResistance", this.flags.effectResistance);
        jsonobject1.addProperty("effectSpeed", this.flags.effectSpeed);
        jsonobject1.addProperty("blockAnimalKilling", this.flags.blockAnimalKilling);
        jsonobject1.addProperty("blockChestAccess", this.flags.blockChestAccess);
        jsonobject1.addProperty("blockCropHarvest", this.flags.blockCropHarvest);
        jsonobject1.addProperty("blockAnvilUse", this.flags.blockAnvilUse);
        jsonobject1.addProperty("blockEnderPearl", this.flags.blockEnderPearl);
        jsonobject1.addProperty("blockSignEditing", this.flags.blockSignEditing);
        jsonobject1.addProperty("allowFlight", this.flags.allowFlight);
        jsonobject1.addProperty("blockDoorsAccess", this.flags.blockDoorsAccess);
        jsonobject1.addProperty("blockAllInteractions", this.flags.blockAllInteractions);
        jsonobject1.addProperty("blockAllMobSpawn", this.flags.blockAllMobSpawn);
        jsonobject1.addProperty("blockPassiveMobSpawn", this.flags.blockPassiveMobSpawn);
        jsonobject.add("flags", jsonobject1);
        return jsonobject;
    }

    public static Claim fromJson(JsonObject jsonobject) {
        UUID uuid = jsonobject.has("claimId") ? UUID.fromString(jsonobject.get("claimId").getAsString()) : UUID.randomUUID();
        UUID uuid1 = jsonobject.has("ownerUUID") && !jsonobject.get("ownerUUID").getAsString().isEmpty()
            ? UUID.fromString(jsonobject.get("ownerUUID").getAsString())
            : null;
        String s1 = jsonobject.has("ownerName") ? jsonobject.get("ownerName").getAsString() : "";
        String s2 = jsonobject.has("world") ? jsonobject.get("world").getAsString() : "minecraft:overworld";
        int k = jsonobject.get("x").getAsInt();
        int l = jsonobject.get("y").getAsInt();
        int i1 = jsonobject.get("z").getAsInt();
        String s;
        int i;
        int j;
        if (jsonobject.has("radius") && jsonobject.has("height")) {
            j = jsonobject.get("radius").getAsInt();
            i = jsonobject.get("height").getAsInt();
            s = jsonobject.has("tierId") ? jsonobject.get("tierId").getAsString() : null;
        } else if (jsonobject.has("tier")) {
            int j1 = jsonobject.get("tier").getAsInt();
            ClaimTier claimtier = ClaimTier.byLegacyTier(j1);
            if (claimtier == null) {
                claimtier = ClaimTier.VALUES[0];
            }

            j = claimtier.radius;
            i = claimtier.height;
            s = claimtier.id;
        } else {
            j = 10;
            i = 15;
            s = "claimstone_10x10";
        }

        Claim claim = new Claim(uuid, uuid1, s1, s, j, i, s2, k, l, i1);
        claim.createdAt = jsonobject.has("createdAt") ? jsonobject.get("createdAt").getAsLong() : 0L;
        long l1 = claim.createdAt;
        if (jsonobject.has("groupId") && !jsonobject.get("groupId").getAsString().isEmpty()) {
            claim.groupId = UUID.fromString(jsonobject.get("groupId").getAsString());
        }

        if (jsonobject.has("members")) {
            JsonArray jsonarray = jsonobject.getAsJsonArray("members");
            JsonArray jsonarray1 = jsonobject.has("memberNames") ? jsonobject.getAsJsonArray("memberNames") : new JsonArray();

            for (int k1 = 0; k1 < jsonarray.size(); k1++) {
                UUID uuid2 = UUID.fromString(jsonarray.get(k1).getAsString());
                String s3 = k1 < jsonarray1.size() ? jsonarray1.get(k1).getAsString() : "";
                claim.addMember(uuid2, s3);
            }
        }

        if (jsonobject.has("bannedPlayers")) {
            JsonArray jsonarray2 = jsonobject.getAsJsonArray("bannedPlayers");

            for (int i2 = 0; i2 < jsonarray2.size(); i2++) {
                claim.bannedPlayers.add(UUID.fromString(jsonarray2.get(i2).getAsString()));
            }
        }

        if (jsonobject.has("flags")) {
            JsonObject jsonobject1 = jsonobject.getAsJsonObject("flags");
            applyBool(jsonobject1, "blockBuilding", flag -> claim.flags.blockBuilding = flag);
            applyBool(jsonobject1, "blockBreaking", flag -> claim.flags.blockBreaking = flag);
            applyBool(jsonobject1, "blockExplosions", flag -> claim.flags.blockExplosions = flag);
            applyBool(jsonobject1, "blockFire", flag -> claim.flags.blockFire = flag);
            applyBool(jsonobject1, "blockMobSpawn", flag -> claim.flags.blockMobSpawn = flag);
            applyBool(jsonobject1, "blockPVP", flag -> claim.flags.blockPVP = flag);
            applyBool(jsonobject1, "blockMobDamage", flag -> claim.flags.blockMobDamage = flag);
            applyBool(jsonobject1, "trespasserAlerts", flag -> claim.flags.trespasserAlerts = flag);
            applyBool(jsonobject1, "blockItemUse", flag -> claim.flags.blockItemUse = flag);
            applyBool(jsonobject1, "blockEntityInteract", flag -> claim.flags.blockEntityInteract = flag);
            applyBool(jsonobject1, "blockTrampling", flag -> claim.flags.blockTrampling = flag);
            applyBool(jsonobject1, "blockFluids", flag -> claim.flags.blockFluids = flag);
            applyBool(jsonobject1, "pvpAll", flag -> claim.flags.pvpAll = flag);
            applyBool(jsonobject1, "blockTreeChopping", flag -> claim.flags.blockTreeChopping = flag);
            applyBool(jsonobject1, "publicMode", flag -> claim.flags.publicMode = flag);
            applyBool(jsonobject1, "showWelcome", flag -> claim.flags.showWelcome = flag);
            if (jsonobject1.has("welcomeMessage")) {
                claim.flags.welcomeMessage = jsonobject1.get("welcomeMessage").getAsString();
            }

            applyBool(jsonobject1, "showLeave", flag -> claim.flags.showLeave = flag);
            if (jsonobject1.has("leaveMessage")) {
                claim.flags.leaveMessage = jsonobject1.get("leaveMessage").getAsString();
            }

            applyBool(jsonobject1, "showBorder", flag -> claim.flags.showBorder = flag);
            applyBool(jsonobject1, "showParticles", flag -> claim.flags.showParticles = flag);
            if (jsonobject1.has("borderParticle")) {
                claim.flags.borderParticle = jsonobject1.get("borderParticle").getAsString();
            }

            if (jsonobject1.has("particleDensity")) {
                claim.flags.particleDensity = jsonobject1.get("particleDensity").getAsInt();
            }

            applyBool(jsonobject1, "burnHostiles", flag -> claim.flags.burnHostiles = flag);
            applyBool(jsonobject1, "effectRegeneration", flag -> claim.flags.effectRegeneration = flag);
            applyBool(jsonobject1, "effectResistance", flag -> claim.flags.effectResistance = flag);
            applyBool(jsonobject1, "effectSpeed", flag -> claim.flags.effectSpeed = flag);
            applyBool(jsonobject1, "blockAnimalKilling", flag -> claim.flags.blockAnimalKilling = flag);
            applyBool(jsonobject1, "blockChestAccess", flag -> claim.flags.blockChestAccess = flag);
            applyBool(jsonobject1, "blockCropHarvest", flag -> claim.flags.blockCropHarvest = flag);
            applyBool(jsonobject1, "blockAnvilUse", flag -> claim.flags.blockAnvilUse = flag);
            applyBool(jsonobject1, "blockEnderPearl", flag -> claim.flags.blockEnderPearl = flag);
            applyBool(jsonobject1, "blockSignEditing", flag -> claim.flags.blockSignEditing = flag);
            applyBool(jsonobject1, "allowFlight", flag -> claim.flags.allowFlight = flag);
            applyBool(jsonobject1, "blockDoorsAccess", flag -> claim.flags.blockDoorsAccess = flag);
            applyBool(jsonobject1, "blockAllInteractions", flag -> claim.flags.blockAllInteractions = flag);
            applyBool(jsonobject1, "blockAllMobSpawn", flag -> claim.flags.blockAllMobSpawn = flag);
            applyBool(jsonobject1, "blockPassiveMobSpawn", flag -> claim.flags.blockPassiveMobSpawn = flag);
        }

        return claim;
    }

    private static void applyBool(JsonObject jsonobject, String s, Claim.BoolSetter claim$boolsetter) {
        if (jsonobject.has(s)) {
            claim$boolsetter.set(jsonobject.get(s).getAsBoolean());
        }
    }

    private interface BoolSetter {
        void set(boolean flag);
    }
}
