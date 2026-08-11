package com.claimblocks.event;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

public final class PassiveEffectsManager {
   private static int counter = 0;
   private static final Set<UUID> grantedFlight = ConcurrentHashMap.newKeySet();

   private PassiveEffectsManager() {
   }

   public static void tick(MinecraftServer server) {
      if (++counter % 20 == 0) {
         boolean runEffects = counter % 40 == 0;

         for (ServerLevel world : server.getAllLevels()) {
            for (ServerPlayer player : world.players()) {
               Claim claim = ClaimManager.getInstance().getClaimAt(world, player.blockPosition());
               handleFlight(player, claim);
               if (runEffects) {
                  applyEffects(player, claim);
               }
            }
         }
      }
   }

   private static int paidLevel(ClaimTier t) {
      if (t == null) {
         return 0;
      } else {
         String var1 = t.id;

         return switch (var1) {
            case "claimstone_250x250" -> 1;
            case "claimstone_300x300" -> 2;
            case "claimstone_500x500" -> 3;
            default -> 0;
         };
      }
   }

   private static void handleFlight(ServerPlayer player, Claim claim) {
      UUID id = player.getUUID();
      GameType mode = player.gameMode.getGameModeForPlayer();
      if (mode != GameType.CREATIVE && mode != GameType.SPECTATOR) {
         boolean shouldHaveClaimFlight = false;
         if (claim != null) {
            int level = paidLevel(claim.getTier());
            if (level >= 3 && claim.isOwner(player) && claim.getFlags().allowFlight) {
               shouldHaveClaimFlight = true;
            }
         }

         boolean weGranted = grantedFlight.contains(id);
         boolean alreadyCanFly = player.getAbilities().mayfly;
         if (shouldHaveClaimFlight) {
            if (!weGranted && !alreadyCanFly) {
               player.getAbilities().mayfly = true;
               player.onUpdateAbilities();
               grantedFlight.add(id);
               player.displayClientMessage(Component.literal("✔ Vuelo activado (Owner 500x500).").withStyle(ChatFormatting.GREEN), true);
            }
         } else if (weGranted) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            grantedFlight.remove(id);
            player.displayClientMessage(Component.literal("[i] Saliste de la zona de vuelo.").withStyle(ChatFormatting.AQUA), true);
         }
      } else {
         grantedFlight.remove(id);
      }
   }

   private static void applyEffects(ServerPlayer player, Claim claim) {
      if (claim != null) {
         int level = paidLevel(claim.getTier());
         if (level != 0 && claim.canModify(player)) {
            if (level >= 1 && claim.getFlags().effectRegeneration) {
               player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, true));
            }

            if (level >= 2 && claim.getFlags().effectResistance) {
               player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false, true));
            }

            if (level >= 2 && claim.getFlags().effectSpeed) {
               player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false, true));
            }
         }
      }
   }

   public static void onPlayerDisconnect(UUID id) {
      grantedFlight.remove(id);
   }
}
