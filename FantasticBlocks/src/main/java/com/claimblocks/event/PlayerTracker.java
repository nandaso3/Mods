package com.claimblocks.event;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimGroup;
import com.claimblocks.data.ClaimManager;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class PlayerTracker {
   private static final Map<UUID, UUID> lastClaim = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();
   private static final long ALERT_COOLDOWN_TICKS = 600L;
   private static int bypassReminderCounter = 0;
   private static final Map<UUID, Long> lastBanHit = new ConcurrentHashMap<>();

   public static void onDisconnect(UUID id) {
      lastClaim.remove(id);
      lastAlert.remove(id);
      ClaimManager.getInstance().onPlayerDisconnect(id);
      PassiveEffectsManager.onPlayerDisconnect(id);
   }

   public static void tick(MinecraftServer server) {
      boolean showBypassReminder = ++bypassReminderCounter % 60 == 0;

      for (ServerLevel world : server.getAllLevels()) {
         for (ServerPlayer player : world.players()) {
            handle(world, player);
            if (showBypassReminder && player.hasPermissions(2) && ClaimManager.getInstance().isBypassing(player.getUUID())) {
               player.displayClientMessage(Component.literal("[!] BYPASS ACTIVO").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}), true);
            }
         }
      }
   }

   private static void handle(ServerLevel world, ServerPlayer player) {
      Claim now = ClaimManager.getInstance().getClaimAt(world, player.blockPosition());
      UUID prev = lastClaim.get(player.getUUID());
      UUID nowId = now == null ? null : zoneId(now);
      if (Objects.equals(prev, nowId)) {
         if (now != null && now.isBanned(player.getUUID()) && !player.hasPermissions(2)) {
            repelBanned(world, player, now);
         }
      } else {
         Claim left = resolveZone(prev);
         if (left != null) {
            MutableComponent msg = left.getFlags().showLeave && left.getFlags().leaveMessage != null && !left.getFlags().leaveMessage.isBlank()
               ? Component.literal("[Protección] ")
                  .withStyle(ChatFormatting.GRAY)
                  .append(Component.literal(truncate(left.getFlags().leaveMessage, 50)).withStyle(ChatFormatting.GOLD))
               : Component.literal("[Protección] ")
                  .withStyle(ChatFormatting.GRAY)
                  .append(Component.literal("Saliendo de la zona ").withStyle(ChatFormatting.RED))
                  .append(Component.literal(truncate(zoneLabel(left), 24)).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}));
            player.displayClientMessage(msg, true);
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 0.9F);
         }

         if (now != null) {
            if (now.isBanned(player.getUUID()) && !player.hasPermissions(2)) {
               repelBanned(world, player, now);
               lastClaim.remove(player.getUUID());
               return;
            }

            MutableComponent entryMsg = now.getFlags().showWelcome && now.getFlags().welcomeMessage != null && !now.getFlags().welcomeMessage.isBlank()
               ? Component.literal("[Protección] ")
                  .withStyle(ChatFormatting.GRAY)
                  .append(Component.literal(truncate(now.getFlags().welcomeMessage, 50)).withStyle(ChatFormatting.GREEN))
               : Component.literal("[Protección] ")
                  .withStyle(ChatFormatting.GRAY)
                  .append(Component.literal("Entrando a la zona ").withStyle(ChatFormatting.GREEN))
                  .append(Component.literal(truncate(zoneLabel(now), 24)).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}));
            player.displayClientMessage(entryMsg, true);
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 1.4F);
            if (now.getFlags().trespasserAlerts && !now.canModify(player)) {
               long t = world.getGameTime();
               Long last = lastAlert.get(player.getUUID());
               if (last == null || t - last > 600L) {
                  lastAlert.put(player.getUUID(), t);
                  UUID ownerId = zoneOwner(now);
                  ServerPlayer owner = ownerId == null ? null : world.getServer().getPlayerList().getPlayer(ownerId);
                  if (owner != null) {
                     MutableComponent alert = Component.literal("[!] ")
                        .withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                        .append(
                           Component.literal(truncate(player.getName().getString(), 16))
                              .withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD})
                        )
                        .append(Component.literal(" entró a tu zona en X=" + now.getX() + " Z=" + now.getZ()).withStyle(ChatFormatting.YELLOW));
                     owner.displayClientMessage(alert, false);
                  }
               }
            }
         }

         if (nowId == null) {
            lastClaim.remove(player.getUUID());
         } else {
            lastClaim.put(player.getUUID(), nowId);
         }
      }
   }

   private static UUID zoneId(Claim c) {
      return c.getGroupId() != null ? c.getGroupId() : c.getClaimId();
   }

   private static Claim resolveZone(UUID zoneId) {
      if (zoneId == null) {
         return null;
      } else {
         ClaimGroup g = ClaimManager.getInstance().getGroup(zoneId);
         return g != null ? ClaimManager.getInstance().getMotherClaim(zoneId) : findClaimById(zoneId);
      }
   }

   private static String zoneLabel(Claim c) {
      if (c == null) {
         return "";
      } else {
         if (c.getGroupId() != null) {
            ClaimGroup g = ClaimManager.getInstance().getGroup(c.getGroupId());
            if (g != null) {
               return g.getName();
            }
         }

         return c.getOwnerName() + " (" + c.sizeLabel() + ")";
      }
   }

   private static UUID zoneOwner(Claim c) {
      if (c.getGroupId() != null) {
         ClaimGroup g = ClaimManager.getInstance().getGroup(c.getGroupId());
         if (g != null && g.getMotherOwnerId() != null) {
            return g.getMotherOwnerId();
         }
      }

      return c.getOwnerUUID();
   }

   private static Claim findClaimById(UUID id) {
      for (Claim c : ClaimManager.getInstance().getAllClaims()) {
         if (c.getClaimId().equals(id)) {
            return c;
         }
      }

      return null;
   }

   private static void repelBanned(ServerLevel world, ServerPlayer player, Claim claim) {
      double cx = (double)claim.getX() + 0.5;
      double cz = (double)claim.getZ() + 0.5;
      double dx = player.getX() - cx;
      double dz = player.getZ() - cz;
      double mag = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
      double dirX = dx / mag;
      double dirZ = dz / mag;
      player.setDeltaMovement(dirX * 1.5, 0.42, dirZ * 1.5);
      player.hurtMarked = true;
      player.hasImpulse = true;
      long now = world.getGameTime();
      Long last = lastBanHit.get(player.getUUID());
      if (last == null || now - last >= 15L) {
         lastBanHit.put(player.getUUID(), now);
         player.invulnerableTime = 0;
         player.hurt(player.damageSources().magic(), 5.0F);
         player.displayClientMessage(Component.literal("[!] Estás baneado de esta zona.").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}), false);
      }
   }

   private static String truncate(String s, int max) {
      if (s == null) {
         return "";
      } else {
         return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "...";
      }
   }
}
