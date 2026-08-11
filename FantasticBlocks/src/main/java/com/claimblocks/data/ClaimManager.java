package com.claimblocks.data;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.ClaimBlocksMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

public class ClaimManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String DATA_FILE = "claimblocks_data.json";
   private static final String CONFIG_FILE = "claimblocks_config.json";
   private static int MAX_CLAIMS_PER_PLAYER = 0;
   private static ClaimManager INSTANCE;
   private final Map<String, List<Claim>> claimsByWorld = new ConcurrentHashMap<>();
   private MinecraftServer server;
   private final Set<UUID> bypassPlayers = ConcurrentHashMap.newKeySet();
   private final Map<UUID, List<Component>> pendingMessages = new ConcurrentHashMap<>();
   private final Map<UUID, ClaimGroup> groups = new ConcurrentHashMap<>();
   private final Map<UUID, Claim> claimIndex = new ConcurrentHashMap<>();

   private ClaimManager() {
   }

   public static ClaimManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ClaimManager();
      }

      return INSTANCE;
   }

   public static int getMaxClaimsPerPlayer() {
      return MAX_CLAIMS_PER_PLAYER;
   }

   public static void setMaxClaimsPerPlayer(int n) {
      MAX_CLAIMS_PER_PLAYER = Math.max(0, n);
   }

   public MinecraftServer getServer() {
      return this.server;
   }

   public Claim createClaim(Level world, BlockPos pos, Player owner, ClaimTier tier) {
      String dim = world.dimension().location().toString();
      Claim c = Claim.create(owner.getUUID(), owner.getName().getString(), tier, dim, pos);
      if (tier != null) {
         String var7 = tier.id;
         String var8 = tier.id;
         switch (var8) {
            case "claimstone_500x500":
               c.getFlags().effectRegeneration = true;
               c.getFlags().effectResistance = true;
               c.getFlags().effectSpeed = true;
               c.getFlags().allowFlight = true;
               break;
            case "claimstone_300x300":
               c.getFlags().effectRegeneration = true;
               c.getFlags().effectResistance = true;
               c.getFlags().effectSpeed = true;
               break;
            case "claimstone_250x250":
               c.getFlags().effectRegeneration = true;
         }
      }

      this.claimsByWorld.computeIfAbsent(dim, k -> Collections.synchronizedList(new ArrayList<>())).add(c);
      this.claimIndex.put(c.getClaimId(), c);
      this.save();
      return c;
   }

   public boolean removeClaim(Level world, BlockPos pos) {
      String dim = world.dimension().location().toString();
      List<Claim> list = this.claimsByWorld.get(dim);
      if (list == null) {
         return false;
      } else {
         Claim found = null;
         synchronized (list) {
            for (Claim c : list) {
               if (c.getX() == pos.getX() && c.getY() == pos.getY() && c.getZ() == pos.getZ()) {
                  found = c;
                  break;
               }
            }

            if (found != null) {
               list.remove(found);
            }
         }

         if (found != null) {
            this.claimIndex.remove(found.getClaimId());
            this.onClaimRemoved(found);
            this.save();
            return true;
         } else {
            return false;
         }
      }
   }

   private void onClaimRemoved(Claim c) {
      if (c.getGroupId() != null) {
         ClaimGroup g = this.groups.get(c.getGroupId());
         if (g != null && c.getClaimId().equals(g.getMotherClaimId())) {
            this.dissolveGroupBreaking(g.getGroupId());
         }
      }
   }

   public int clearClaimsOf(UUID playerId) {
      int total = 0;

      for (Entry<String, List<Claim>> e : this.claimsByWorld.entrySet()) {
         List<Claim> list = e.getValue();
         ArrayList<Claim> toRemove = new ArrayList<>();
         synchronized (list) {
            for (Claim c : list) {
               if (c.isOwner(playerId)) {
                  toRemove.add(c);
               }
            }
         }

         for (Claim cx : toRemove) {
            ServerLevel w;
            BlockPos p;
            if (this.server != null
               && (w = this.worldFor(e.getKey())) != null
               && ClaimBlocks.isClaimConcreteForTier(w.getBlockState(p = cx.getCenter()).getBlock(), cx.getTier())) {
               w.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }

            synchronized (list) {
               list.remove(cx);
            }

            this.claimIndex.remove(cx.getClaimId());
            this.onClaimRemoved(cx);
            total++;
         }
      }

      if (total > 0) {
         this.save();
      }

      return total;
   }

   public boolean transferOwnership(Claim claim, UUID newOwnerId, String newOwnerName) {
      if (claim != null && newOwnerId != null) {
         claim.setOwner(newOwnerId, newOwnerName);
         this.save();
         return true;
      } else {
         return false;
      }
   }

   private ServerLevel worldFor(String dimensionKey) {
      if (this.server == null) {
         return null;
      } else {
         for (ServerLevel w : this.server.getAllLevels()) {
            if (w.dimension().location().toString().equals(dimensionKey)) {
               return w;
            }
         }

         return null;
      }
   }

   public Claim getClaimAt(Level world, BlockPos pos) {
      String dim = world.dimension().location().toString();
      List<Claim> list = this.claimsByWorld.get(dim);
      if (list == null) {
         return null;
      } else {
         synchronized (list) {
            for (Claim c : list) {
               if (c.contains(pos)) {
                  return c;
               }
            }

            return null;
         }
      }
   }

   public Claim getClaimByCenter(Level world, BlockPos pos) {
      String dim = world.dimension().location().toString();
      List<Claim> list = this.claimsByWorld.get(dim);
      if (list == null) {
         return null;
      } else {
         synchronized (list) {
            for (Claim c : list) {
               if (c.getX() == pos.getX() && c.getY() == pos.getY() && c.getZ() == pos.getZ()) {
                  return c;
               }
            }

            return null;
         }
      }
   }

   public boolean wouldOverlap(Level world, BlockPos pos, int radius, int height) {
      String dim = world.dimension().location().toString();
      List<Claim> list = this.claimsByWorld.get(dim);
      if (list == null) {
         return false;
      } else {
         synchronized (list) {
            for (Claim c : list) {
               if (c.overlapsWith(pos, radius, height)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   public List<Claim> overlappingClaims(Level world, BlockPos pos, int radius, int height) {
      ArrayList<Claim> out = new ArrayList<>();
      String dim = world.dimension().location().toString();
      List<Claim> list = this.claimsByWorld.get(dim);
      if (list == null) {
         return out;
      } else {
         synchronized (list) {
            for (Claim c : list) {
               if (c.overlapsWith(pos, radius, height)) {
                  out.add(c);
               }
            }

            return out;
         }
      }
   }

   public ClaimGroup getGroup(UUID groupId) {
      return groupId == null ? null : this.groups.get(groupId);
   }

   public ClaimGroup getGroupOf(Claim claim) {
      return claim == null ? null : this.getGroup(claim.getGroupId());
   }

   public Claim findClaimById(UUID id) {
      return id == null ? null : this.claimIndex.get(id);
   }

   public Claim getMotherClaim(UUID groupId) {
      ClaimGroup g = this.getGroup(groupId);
      return g != null && g.getMotherClaimId() != null ? this.claimIndex.get(g.getMotherClaimId()) : null;
   }

   public ClaimGroup createGroup(Claim mother, String name) {
      UUID gid = UUID.randomUUID();
      ClaimGroup g = new ClaimGroup(gid, name, mother.getClaimId(), mother.getOwnerUUID());
      this.groups.put(gid, g);
      mother.setGroupId(gid);
      this.save();
      return g;
   }

   public void registerPlayer(UUID groupId, UUID playerId) {
      ClaimGroup g = this.getGroup(groupId);
      if (g != null) {
         g.register(playerId);
         this.save();
      }
   }

   public boolean isRegistered(UUID groupId, UUID playerId) {
      ClaimGroup g = this.getGroup(groupId);
      return g != null && g.isRegistered(playerId);
   }

   public ClaimGroup getGroupByRegistered(UUID playerId) {
      for (ClaimGroup g : this.groups.values()) {
         if (g.isRegistered(playerId)) {
            return g;
         }
      }

      return null;
   }

   public void joinClaimToGroup(Claim claim, UUID groupId) {
      if (claim != null && this.groups.containsKey(groupId)) {
         claim.setGroupId(groupId);
         this.save();
      }
   }

   public List<Claim> getGroupClaims(UUID groupId) {
      ArrayList<Claim> out = new ArrayList<>();
      if (groupId == null) {
         return out;
      } else {
         for (Claim c : this.getAllClaims()) {
            if (groupId.equals(c.getGroupId())) {
               out.add(c);
            }
         }

         return out;
      }
   }

   public void dissolveGroup(UUID groupId) {
      if (this.groups.remove(groupId) != null) {
         for (Claim c : this.getAllClaims()) {
            if (groupId.equals(c.getGroupId())) {
               c.setGroupId(null);
            }
         }

         this.save();
      }
   }

   public void dissolveGroupBreaking(UUID groupId) {
      ClaimGroup g = this.groups.get(groupId);
      if (g != null) {
         Claim mother = this.getMotherClaim(groupId);
         UUID motherClaimId = mother != null ? mother.getClaimId() : g.getMotherClaimId();

         for (Claim c : this.getGroupClaims(groupId)) {
            if (motherClaimId == null || !c.getClaimId().equals(motherClaimId)) {
               this.breakAndReturn(c);
            }
         }

         this.groups.remove(groupId);

         for (Claim cx : this.getAllClaims()) {
            if (groupId.equals(cx.getGroupId())) {
               cx.setGroupId(null);
            }
         }

         this.save();
      }
   }

   public void leaveGroupBreaking(UUID groupId, UUID playerId) {
      ClaimGroup g = this.getGroup(groupId);
      if (g != null) {
         if (playerId != null && playerId.equals(g.getMotherOwnerId())) {
            this.dissolveGroupBreaking(groupId);
         } else {
            g.unregister(playerId);

            for (Claim c : this.getGroupClaims(groupId)) {
               if (c.isOwner(playerId)) {
                  this.breakAndReturn(c);
               }
            }

            this.save();
         }
      }
   }

   private void breakAndReturn(Claim c) {
      ServerLevel w = this.worldFor(c.getWorld());
      BlockPos p = c.getCenter();
      ClaimTier tier = c.getTier();
      if (w != null && tier != null && ClaimBlocks.isClaimConcreteForTier(w.getBlockState(p).getBlock(), tier)) {
         w.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
      }

      if (w != null && tier != null) {
         ItemStack stack = ClaimBlocks.createTierItem(tier, 1);
         ServerPlayer owner = this.server != null && c.getOwnerUUID() != null ? this.server.getPlayerList().getPlayer(c.getOwnerUUID()) : null;
         if (owner != null) {
            if (!owner.getInventory().add(stack)) {
               owner.drop(stack, false);
            }
         } else {
            w.addFreshEntity(new ItemEntity(w, (double)p.getX() + 0.5, (double)p.getY() + 0.5, (double)p.getZ() + 0.5, stack));
         }
      }

      List<Claim> list = this.claimsByWorld.get(c.getWorld());
      if (list != null) {
         synchronized (list) {
            list.remove(c);
         }
      }

      this.claimIndex.remove(c.getClaimId());
   }

   public void removePlayerFromGroup(UUID groupId, UUID playerId) {
      ClaimGroup g = this.getGroup(groupId);
      if (g != null) {
         if (playerId != null && playerId.equals(g.getMotherOwnerId())) {
            this.dissolveGroup(groupId);
         } else {
            g.unregister(playerId);

            for (Claim c : this.getGroupClaims(groupId)) {
               if (c.isOwner(playerId)) {
                  c.setGroupId(null);
               }
            }

            this.save();
         }
      }
   }

   public List<Claim> getAllClaims() {
      ArrayList<Claim> all = new ArrayList<>();
      Iterator<List<Claim>> iterator = this.claimsByWorld.values().iterator();

      while (iterator.hasNext()) {
         List<Claim> l;
         List<Claim> list = l = iterator.next();
         synchronized (list) {
            all.addAll(l);
         }
      }

      return all;
   }

   public List<Claim> getClaimsOf(UUID playerId) {
      ArrayList<Claim> r = new ArrayList<>();
      Iterator<List<Claim>> iterator = this.claimsByWorld.values().iterator();

      while (iterator.hasNext()) {
         List<Claim> l;
         List<Claim> list = l = iterator.next();
         synchronized (list) {
            for (Claim c : l) {
               if (c.isOwner(playerId)) {
                  r.add(c);
               }
            }
         }
      }

      return r;
   }

   public List<Claim> getClaimsInWorld(String dim) {
      List l;
      List list = l = this.claimsByWorld.getOrDefault(dim, Collections.emptyList());
      synchronized (list) {
         return new ArrayList<>(l);
      }
   }

   public void save() {
      if (this.server != null) {
         Path file = this.dataFile(this.server);

         try {
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();

            for (Claim c : this.getAllClaims()) {
               arr.add(c.toJson());
            }

            root.add("claims", arr);
            JsonArray garr = new JsonArray();

            for (ClaimGroup g : this.groups.values()) {
               garr.add(g.toJson());
            }

            root.add("groups", garr);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
         } catch (IOException var7) {
            ClaimBlocksMod.LOGGER.error("Could not save claims to " + file, var7);
         }
      }
   }

   public void load(MinecraftServer server) {
      this.server = server;
      this.claimsByWorld.clear();
      this.claimIndex.clear();
      this.groups.clear();
      this.loadConfig(server);
      Path file = this.dataFile(server);
      if (!Files.exists(file)) {
         ClaimBlocksMod.LOGGER.info("No existing claims file at {}, starting fresh.", file);
      } else {
         try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.isBlank()) {
               return;
            }

            JsonElement el = JsonParser.parseString(text);
            if (!el.isJsonObject()) {
               return;
            }

            JsonArray arr = el.getAsJsonObject().getAsJsonArray("claims");
            if (arr == null) {
               return;
            }

            int count = 0;
            int migrated = 0;

            for (JsonElement e : arr) {
               JsonObject obj = e.getAsJsonObject();
               boolean wasLegacy = !obj.has("radius") && obj.has("tier");
               Claim c = Claim.fromJson(obj);
               this.claimsByWorld.computeIfAbsent(c.getWorld(), k -> Collections.synchronizedList(new ArrayList<>())).add(c);
               this.claimIndex.put(c.getClaimId(), c);
               count++;
               if (wasLegacy) {
                  migrated++;
               }
            }

            JsonArray garr = el.getAsJsonObject().getAsJsonArray("groups");
            if (garr != null) {
               for (JsonElement ge : garr) {
                  ClaimGroup g = ClaimGroup.fromJson(ge.getAsJsonObject());
                  this.groups.put(g.getGroupId(), g);
               }
            }

            List<UUID> dead = new ArrayList<>();

            for (ClaimGroup g : this.groups.values()) {
               if (g.getMotherClaimId() == null || this.claimIndex.get(g.getMotherClaimId()) == null) {
                  dead.add(g.getGroupId());
               }
            }

            for (UUID gid : dead) {
               this.groups.remove(gid);

               for (Claim c : this.getAllClaims()) {
                  if (gid.equals(c.getGroupId())) {
                     c.setGroupId(null);
                  }
               }
            }

            ClaimBlocksMod.LOGGER.info("Loaded {} claims from {} (migrated {} legacy)", new Object[]{count, file, migrated});
            if (migrated > 0) {
               this.save();
            }
         } catch (Exception var14) {
            ClaimBlocksMod.LOGGER.error("Could not load claims from " + file, var14);
         }
      }
   }

   private void loadConfig(MinecraftServer s) {
      Path cfg = s.getWorldPath(LevelResource.ROOT).resolve("claimblocks_config.json");

      try {
         if (!Files.exists(cfg)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("maxClaimsPerPlayer", 0);
            obj.addProperty("_doc_maxClaimsPerPlayer", "0 = unlimited; max claims a non-OP player can own");
            Files.createDirectories(cfg.getParent());
            Files.writeString(cfg, GSON.toJson(obj), StandardCharsets.UTF_8);
            return;
         }

         JsonElement el = JsonParser.parseString(Files.readString(cfg, StandardCharsets.UTF_8));
         JsonObject o;
         if (el != null && el.isJsonObject() && (o = el.getAsJsonObject()).has("maxClaimsPerPlayer")) {
            setMaxClaimsPerPlayer(o.get("maxClaimsPerPlayer").getAsInt());
         }
      } catch (Exception var51) {
         ClaimBlocksMod.LOGGER.error("Could not load config " + cfg, var51);
      }
   }

   private Path dataFile(MinecraftServer s) {
      return s.getWorldPath(LevelResource.ROOT).resolve("claimblocks_data.json");
   }

   public boolean isBypassing(UUID id) {
      return this.bypassPlayers.contains(id);
   }

   public boolean toggleBypass(UUID id) {
      if (this.bypassPlayers.contains(id)) {
         this.bypassPlayers.remove(id);
         return false;
      } else {
         this.bypassPlayers.add(id);
         return true;
      }
   }

   public Set<UUID> getBypassPlayers() {
      return this.bypassPlayers;
   }

   public void queueMessage(UUID owner, Component msg) {
      this.pendingMessages.computeIfAbsent(owner, k -> Collections.synchronizedList(new ArrayList<>())).add(msg);
   }

   public void flushPendingTo(ServerPlayer player) {
      List<Component> msgs = this.pendingMessages.remove(player.getUUID());
      if (msgs != null) {
         synchronized (msgs) {
            for (Component t : msgs) {
               player.displayClientMessage(t, false);
            }
         }
      }
   }

   public void onPlayerDisconnect(UUID id) {
      this.bypassPlayers.remove(id);
   }
}
