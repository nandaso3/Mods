package com.claimblocks.util;

import com.claimblocks.ClaimBlocksMod;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;

public final class PlayerLookup {
    private static final ExecutorService LOOKUP = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ClaimBlocks-ProfileLookup");
        thread.setDaemon(true);
        return thread;
    });

    private PlayerLookup() {
    }

    public static boolean isOfflineMode(MinecraftServer minecraftserver) {
        return minecraftserver != null && !minecraftserver.usesAuthentication();
    }

    public static PlayerLookup.Resolved resolve(MinecraftServer minecraftserver, String s) {
        if (minecraftserver != null && s != null && !s.isBlank()) {
            String s1 = s.trim();
            ServerPlayer serverplayer = minecraftserver.getPlayerList().getPlayerByName(s1);
            if (serverplayer != null) {
                return new PlayerLookup.Resolved(serverplayer.getUUID(), serverplayer.getName().getString(), serverplayer);
            } else if (isOfflineMode(minecraftserver)) {
                UUID uuid = UUIDUtil.createOfflinePlayerUUID(s1);
                return new PlayerLookup.Resolved(uuid, s1, minecraftserver.getPlayerList().getPlayer(uuid));
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static void resolveAsync(MinecraftServer minecraftserver, String s, Consumer<PlayerLookup.Resolved> consumer) {
        if (minecraftserver != null && s != null && !s.isBlank()) {
            String s1 = s.trim();
            PlayerLookup.Resolved playerlookup$resolved = resolve(minecraftserver, s1);
            if (playerlookup$resolved != null) {
                consumer.accept(playerlookup$resolved);
            } else {
                GameProfileCache gameprofilecache = minecraftserver.getProfileCache();
                if (gameprofilecache == null) {
                    consumer.accept(null);
                } else {
                    LOOKUP.execute(
                        () -> {
                            PlayerLookup.Resolved playerlookup$resolved1 = null;

                            try {
                                Optional<GameProfile> optional = gameprofilecache.get(s1);
                                if (optional.isPresent() && ((GameProfile)optional.get()).getId() != null) {
                                    GameProfile gameprofile = (GameProfile)optional.get();
                                    playerlookup$resolved1 = new PlayerLookup.Resolved(
                                        gameprofile.getId(), gameprofile.getName() == null ? s1 : gameprofile.getName(), null
                                    );
                                }
                            } catch (Throwable throwable) {
                                ClaimBlocksMod.LOGGER.warn("[FantasticClaims] No se pudo resolver el perfil de '" + s1 + "'", throwable);
                            }

                            PlayerLookup.Resolved playerlookup$resolved2 = playerlookup$resolved1;
                            minecraftserver.execute(
                                () -> {
                                    PlayerLookup.Resolved playerlookup$resolved4 = playerlookup$resolved2;
                                    if (playerlookup$resolved2 != null) {
                                        playerlookup$resolved4 = new PlayerLookup.Resolved(
                                            playerlookup$resolved2.id(),
                                            playerlookup$resolved2.name(),
                                            minecraftserver.getPlayerList().getPlayer(playerlookup$resolved2.id())
                                        );
                                    }

                                    consumer.accept(playerlookup$resolved4);
                                }
                            );
                        }
                    );
                }
            }
        } else {
            consumer.accept(null);
        }
    }

    public static String nameOf(MinecraftServer minecraftserver, UUID uuid) {
        if (uuid == null) {
            return "?";
        } else {
            if (minecraftserver != null) {
                ServerPlayer serverplayer = minecraftserver.getPlayerList().getPlayer(uuid);
                if (serverplayer != null) {
                    return serverplayer.getName().getString();
                }

                GameProfileCache gameprofilecache = minecraftserver.getProfileCache();
                Optional<GameProfile> optional;
                if (gameprofilecache != null && (optional = gameprofilecache.get(uuid)).isPresent() && ((GameProfile)optional.get()).getName() != null) {
                    return ((GameProfile)optional.get()).getName();
                }
            }

            return uuid.toString().substring(0, 8);
        }
    }

    public static record Resolved(UUID id, String name, ServerPlayer online) {
        public boolean isOnline() {
            return this.online != null;
        }
    }
}
