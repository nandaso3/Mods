package com.claimblocks.mixin;

import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.chat.ChatPromptRouter;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captura las respuestas de los menus del mod directamente del paquete de chat del cliente.
 *
 * <p>Este es el arreglo de fondo del bug de "Anadir miembro" en servidores hibridos: al enganchar en
 * {@code HEAD} de {@code handleChat} leemos el texto tal y como lo escribio el jugador, <b>antes</b>
 * de {@code AsyncPlayerChatEvent} de Bukkit, de los plugins de chat y del {@code ServerChatEvent} de
 * Forge. Da igual que despues alguien cancele o reescriba el mensaje: el prompt ya se resolvio.
 *
 * <h2>Dos inyecciones, y por que</h2>
 * <ol>
 *   <li>{@code handleChat} (HEAD, <b>sin cancelar</b>): solo lee. No se cancela porque ese metodo
 *       tambien lleva la contabilidad de acuses del chat firmado de 1.20.1
 *       ({@code LastSeenMessagesValidator#applyUpdate}); saltarsela haria que el servidor expulsara
 *       al jugador con "Chat validation failed" en su siguiente mensaje.</li>
 *   <li>{@code broadcastChatMessage} (HEAD, cancelable): aqui si es seguro cortar, y es lo que evita
 *       que el nombre que escribio el jugador aparezca en el chat publico.</li>
 * </ol>
 *
 * <p>Ambas usan {@code require = 0} y {@code try/catch}: si un servidor hibrido reescribe estos
 * metodos, el mod sigue cargando y quedan los respaldos ({@code ServerChatEvent}, el selector visual
 * de miembros y {@code /claim addmember}).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerChatPromptMixin {

    @Inject(method = "handleChat", at = @At("HEAD"), require = 0)
    private void claimblocks$captureMenuPrompt(ServerboundChatPacket packet, CallbackInfo ci) {
        try {
            ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
            ServerPlayer sender = self.getPlayer();
            if (sender == null || !ChatPromptRouter.hasPending(sender.getUUID())) {
                return;
            }

            ChatPromptRouter.markPacketCaptureActive();
            ChatPromptRouter.consume(sender, packet.message());
        } catch (Throwable t) {
            // Nunca romper el chat del servidor por culpa del mod.
            ClaimBlocksMod.LOGGER.error("[ClaimBlocks] Fallo capturando la respuesta del menu", t);
        }
    }

    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void claimblocks$hideConsumedPrompt(PlayerChatMessage message, CallbackInfo ci) {
        try {
            ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
            ServerPlayer sender = self.getPlayer();
            if (sender == null) {
                return;
            }

            if (ChatPromptRouter.shouldSuppress(sender.getUUID(), message.signedContent())) {
                ci.cancel();
            }
        } catch (Throwable t) {
            ClaimBlocksMod.LOGGER.error("[ClaimBlocks] Fallo ocultando la respuesta del menu", t);
        }
    }
}
