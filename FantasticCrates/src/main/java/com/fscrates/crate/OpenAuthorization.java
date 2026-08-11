package com.fscrates.crate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Permiso temporal para abrir una caja concreta.
 *
 * Por que existe: al pulsar ABRIR en la pantalla de pre-apertura el cliente manda
 * un paquete al servidor, y ese paquete NO pasa por la interaccion con el bloque.
 * En un servidor con plugins de proteccion (WorldGuard y compania, tipico en
 * Mohist o cualquier hibrido Forge+Bukkit) la proteccion cancela el evento de
 * click, no el paquete; asi que un cliente modificado podria pedir la apertura
 * directamente y saltarse la proteccion.
 *
 * Con esto, el permiso SOLO se concede dentro de CrateBlock.use(), que es lo que
 * se ejecuta despues de que el click haya pasado todos los filtros (Forge, Bukkit
 * y los plugins). Sin permiso, el paquete se ignora.
 */
public final class OpenAuthorization {
    /** Cuanto vale el permiso: da tiempo a mirar el pool de recompensas con calma. */
    private static final long TTL_MS = 5L * 60L * 1000L;

    private static final Map<UUID, Entry> GRANTED = new ConcurrentHashMap<>();

    private record Entry(BlockPos pos, long expiresAt) {
    }

    private OpenAuthorization() {
    }

    /** Concede permiso para esta caja. Se llama solo tras un click valido. */
    public static void grant(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        GRANTED.put(player.getUUID(), new Entry(pos.immutable(), System.currentTimeMillis() + TTL_MS));
    }

    /**
     * Gasta el permiso si existe y es para esta caja.
     *
     * @return true si el jugador tenia permiso para abrir justo esa caja
     */
    public static boolean consume(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }

        Entry entry = GRANTED.remove(player.getUUID());
        if (entry == null) {
            return false;
        }
        if (System.currentTimeMillis() > entry.expiresAt()) {
            return false;
        }
        return entry.pos().equals(pos);
    }

    /** Al salir del servidor se olvida el permiso. */
    public static void forget(ServerPlayer player) {
        if (player != null) {
            GRANTED.remove(player.getUUID());
        }
    }
}
