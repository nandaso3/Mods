package com.claimblocks.util;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;

/**
 * Resolucion de jugadores por nombre que NO exige que esten conectados.
 *
 * <p>Antes de la 7.7.0 el flujo de "anadir miembro" solo miraba la lista de jugadores conectados
 * ({@code PlayerList#getPlayerByName}), asi que anadir a alguien desconectado era imposible. Aqui
 * se replica el mismo orden de busqueda que ya usaba el baneo: primero online, y si no, la cache de
 * perfiles del servidor (usercache.json), que en un servidor con jugadores recurrentes cubre a todos
 * los que han entrado alguna vez.
 */
public final class PlayerLookup {

    /** Jugador resuelto. {@code online} es null si el jugador no esta conectado. */
    public record Resolved(UUID id, String name, ServerPlayer online) {
        public boolean isOnline() {
            return this.online != null;
        }
    }

    private PlayerLookup() {
    }

    /**
     * Busca un jugador por nombre.
     *
     * @return el jugador resuelto, o {@code null} si no se pudo identificar.
     */
    public static Resolved resolve(MinecraftServer server, String name) {
        if (server == null || name == null || name.isBlank()) {
            return null;
        }

        String query = name.trim();

        ServerPlayer online = server.getPlayerList().getPlayerByName(query);
        if (online != null) {
            return new Resolved(online.getUUID(), online.getName().getString(), online);
        }

        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            Optional<GameProfile> profile = cache.get(query);
            if (profile.isPresent() && profile.get().getId() != null) {
                GameProfile p = profile.get();
                String resolvedName = p.getName() == null ? query : p.getName();
                ServerPlayer byId = server.getPlayerList().getPlayer(p.getId());
                return new Resolved(p.getId(), resolvedName, byId);
            }
        }

        return null;
    }

    /** Nombre legible de un UUID usando la cache de perfiles; cae al UUID corto si no se conoce. */
    public static String nameOf(MinecraftServer server, UUID id) {
        if (id == null) {
            return "?";
        }

        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                return online.getName().getString();
            }

            GameProfileCache cache = server.getProfileCache();
            if (cache != null) {
                Optional<GameProfile> profile = cache.get(id);
                if (profile.isPresent() && profile.get().getName() != null) {
                    return profile.get().getName();
                }
            }
        }

        return id.toString().substring(0, 8);
    }
}
