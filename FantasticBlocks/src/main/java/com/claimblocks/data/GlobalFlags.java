package com.claimblocks.data;

import com.claimblocks.ClaimBlocksMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.storage.LevelResource;

public final class GlobalFlags {
   private static final String FILE = "global_flags.json";
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static GlobalFlags INSTANCE;
   public boolean globalPVP = true;
   public boolean globalMobGriefing = true;
   public boolean globalFireSpread = true;

   private GlobalFlags() {
   }

   public static GlobalFlags getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new GlobalFlags();
      }

      return INSTANCE;
   }

   public boolean get(String key) {
      return switch (key) {
         case "globalPVP" -> this.globalPVP;
         case "globalMobGriefing" -> this.globalMobGriefing;
         case "globalFireSpread" -> this.globalFireSpread;
         default -> false;
      };
   }

   public void set(String key, boolean value, MinecraftServer server) {
      switch (key) {
         case "globalPVP":
            this.globalPVP = value;
            break;
         case "globalMobGriefing":
            this.globalMobGriefing = value;
            break;
         case "globalFireSpread":
            this.globalFireSpread = value;
      }

      this.applyToServer(server);
      this.save(server);
   }

   public void applyToServer(MinecraftServer server) {
      if (server != null) {
         server.setPvpAllowed(this.globalPVP);
         GameRules rules = server.getGameRules();
         ((BooleanValue)rules.getRule(GameRules.RULE_MOBGRIEFING)).set(this.globalMobGriefing, server);
         ((BooleanValue)rules.getRule(GameRules.RULE_DOFIRETICK)).set(this.globalFireSpread, server);
      }
   }

   public void load(MinecraftServer server) {
      Path file = this.file(server);
      if (!Files.exists(file)) {
         ClaimBlocksMod.LOGGER.info("No global_flags.json, defaults applied.");
         this.applyToServer(server);
      } else {
         try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            if (o.has("globalPVP")) {
               this.globalPVP = o.get("globalPVP").getAsBoolean();
            }

            if (o.has("globalMobGriefing")) {
               this.globalMobGriefing = o.get("globalMobGriefing").getAsBoolean();
            }

            if (o.has("globalFireSpread")) {
               this.globalFireSpread = o.get("globalFireSpread").getAsBoolean();
            }

            this.applyToServer(server);
            ClaimBlocksMod.LOGGER
               .info("Global flags cargadas: PVP={} MobGrief={} FireSpread={}", new Object[]{this.globalPVP, this.globalMobGriefing, this.globalFireSpread});
         } catch (Exception var51) {
            ClaimBlocksMod.LOGGER.error("No se pudo cargar global_flags.json", var51);
         }
      }
   }

   public void save(MinecraftServer server) {
      Path file = this.file(server);

      try {
         JsonObject o = new JsonObject();
         o.addProperty("globalPVP", this.globalPVP);
         o.addProperty("globalMobGriefing", this.globalMobGriefing);
         o.addProperty("globalFireSpread", this.globalFireSpread);
         Files.createDirectories(file.getParent());
         Files.writeString(file, GSON.toJson(o), StandardCharsets.UTF_8);
      } catch (IOException var41) {
         ClaimBlocksMod.LOGGER.error("No se pudo guardar global_flags.json", var41);
      }
   }

   private Path file(MinecraftServer s) {
      return s.getWorldPath(LevelResource.ROOT).resolve("global_flags.json");
   }
}
