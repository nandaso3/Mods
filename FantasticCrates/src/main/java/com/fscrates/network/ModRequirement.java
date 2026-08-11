package com.fscrates.network;

import com.fscrates.FSCrates;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.player.PlayerNegotiationEvent;
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.NetworkHooks;

/**
 * Impide entrar al servidor sin el mod instalado.
 *
 * Forge ya rechaza por su cuenta a quien le falte un canal de red, y el de este
 * mod exige version exacta, asi que en un servidor de Forge normal esto ya estaba
 * cubierto. El problema son los servidores hibridos tipo Mohist o Magma: su razon
 * de ser es dejar entrar tambien a clientes sin mods, y para eso relajan justamente
 * esa comprobacion. Ahi el apreton de manos de Forge no basta.
 *
 * Esta comprobacion es aparte y no depende de eso: se mira lo que el cliente ha
 * declarado al conectarse y, si el mod no esta, se le echa con un motivo claro.
 *
 * Salta durante el inicio de sesion, antes de que el jugador exista en el mundo, o
 * sea que no llega a entrar ni un instante.
 */
public final class ModRequirement {
    /** El canal de red del mod, que es la otra forma de reconocerlo. */
    private static final ResourceLocation CHANNEL = new ResourceLocation(FSCrates.MOD_ID, "main");

    private static final Component MESSAGE = Component.literal(
        "\u00a7d\u2726 \u00a7fFantastic Crates\u00a7d \u2726\n\n"
            + "\u00a7fEste servidor necesita el mod \u00a7dFantastic Crates\u00a7f para entrar.\n"
            + "\u00a77Instalalo en tu cliente y vuelve a conectarte."
    );

    private ModRequirement() {
    }

    /** Comprueba a cada jugador que inicia sesion. */
    public static void onNegotiation(PlayerNegotiationEvent event) {
        Connection connection = event.getConnection();
        if (connection == null) {
            return;
        }

        // Un mundo de un jugador (o abrir a LAN) va por una conexion en memoria: ahi
        // el cliente es este mismo proceso, asi que tiene el mod por definicion. Sin
        // esta salida el anfitrion se echaria a si mismo de su propia partida.
        if (connection.isMemoryConnection()) {
            return;
        }

        if (has(connection)) {
            return;
        }

        String who = event.getProfile() == null ? "?" : event.getProfile().getName();
        FSCrates.LOGGER.info(
            "[FSCrates] Se rechaza a '{}' ({}): no tiene el mod instalado.",
            who,
            connection.getRemoteAddress()
        );
        connection.disconnect(MESSAGE);
    }

    /**
     * true si el cliente ha declarado tener el mod.
     *
     * Se aceptan las dos señales que manda Forge, la lista de mods y la de canales,
     * porque no todas las plataformas rellenan las dos igual. Con que aparezca en
     * una, vale.
     */
    private static boolean has(Connection connection) {
        if (NetworkHooks.isVanillaConnection(connection)) {
            return false;
        }

        ConnectionData data = NetworkHooks.getConnectionData(connection);
        if (data == null) {
            // Ni siquiera hay datos del apreton de manos: no es un cliente con este
            // mod. Se rechaza, que es lo que se pide.
            return false;
        }

        return data.getModList().contains(FSCrates.MOD_ID) || data.getChannels().containsKey(CHANNEL);
    }
}
