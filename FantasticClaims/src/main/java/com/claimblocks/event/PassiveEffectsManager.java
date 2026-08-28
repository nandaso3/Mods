package com.claimblocks.event;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimConfig;
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

    public static void tick(MinecraftServer minecraftserver) {
        if (++counter % 20 == 0) {
            boolean flag = counter % Math.max(20, ClaimConfig.get().passiveEffectIntervalTicks) < 20;

            for (ServerLevel serverlevel : minecraftserver.getAllLevels()) {
                for (ServerPlayer serverplayer : serverlevel.players()) {
                    Claim claim = ClaimManager.getInstance().getClaimAt(serverlevel, serverplayer.blockPosition());
                    handleFlight(serverplayer, claim);
                    if (flag) {
                        applyEffects(serverplayer, claim);
                    }
                }
            }
        }
    }

    private static int paidLevel(ClaimTier claimtier) {
        if (claimtier == null) {
            return 0;
        } else {
            String s = claimtier.id;
            String s1 = claimtier.id;

            return switch (s1) {
                case "claimstone_250x250" -> 1;
                case "claimstone_300x300" -> 2;
                case "claimstone_500x500" -> 3;
                default -> 0;
            };
        }
    }

    private static void handleFlight(ServerPlayer serverplayer, Claim claim) {
        UUID uuid = serverplayer.getUUID();
        GameType gametype = serverplayer.gameMode.getGameModeForPlayer();
        if (gametype != GameType.CREATIVE && gametype != GameType.SPECTATOR) {
            boolean flag = false;
            if (claim != null && paidLevel(claim.getTier()) >= 3 && claim.canModify(serverplayer) && claim.getFlags().allowFlight) {
                flag = true;
            }

            boolean flag1 = grantedFlight.contains(uuid);
            boolean flag2 = serverplayer.getAbilities().mayfly;
            if (flag) {
                if (!flag1 && !flag2) {
                    serverplayer.getAbilities().mayfly = true;
                    serverplayer.onUpdateAbilities();
                    grantedFlight.add(uuid);
                    serverplayer.displayClientMessage(Component.literal("✔ Vuelo activado (zona 500x500).").withStyle(ChatFormatting.GREEN), true);
                }
            } else if (flag1) {
                grantedFlight.remove(uuid);
                serverplayer.getAbilities().mayfly = false;
                serverplayer.getAbilities().flying = false;
                serverplayer.onUpdateAbilities();
                serverplayer.displayClientMessage(Component.literal("[i] Saliste de la zona de vuelo.").withStyle(ChatFormatting.AQUA), true);
            }
        } else {
            grantedFlight.remove(uuid);
        }
    }

    private static void applyEffects(ServerPlayer serverplayer, Claim claim) {
        int i;
        if (claim != null && (i = paidLevel(claim.getTier())) != 0 && claim.canModify(serverplayer)) {
            if (i >= 1 && claim.getFlags().effectRegeneration) {
                serverplayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ClaimConfig.get().effectDurationTicks, 0, true, false, true));
            }

            if (i >= 2 && claim.getFlags().effectResistance) {
                serverplayer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ClaimConfig.get().effectDurationTicks, 0, true, false, true));
            }

            if (i >= 2 && claim.getFlags().effectSpeed) {
                serverplayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ClaimConfig.get().effectDurationTicks, 0, true, false, true));
            }
        }
    }

    public static void onPlayerDisconnect(UUID uuid) {
        grantedFlight.remove(uuid);
    }
}
