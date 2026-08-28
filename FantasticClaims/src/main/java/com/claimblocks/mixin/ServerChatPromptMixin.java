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

@Mixin({ServerGamePacketListenerImpl.class})
public abstract class ServerChatPromptMixin {
    @Inject(
        method = {"handleChat"},
        at = {@At("HEAD")},
        require = 0
    )
    private void claimblocks$captureMenuPrompt(ServerboundChatPacket serverboundchatpacket, CallbackInfo callbackinfo) {
        try {
            ServerGamePacketListenerImpl servergamepacketlistenerimpl = (ServerGamePacketListenerImpl)(Object)this;
            ServerPlayer serverplayer = servergamepacketlistenerimpl.getPlayer();
            if (serverplayer == null || !ChatPromptRouter.hasPending(serverplayer.getUUID())) {
                return;
            }

            ChatPromptRouter.markPacketCaptureActive();
            ChatPromptRouter.consume(serverplayer, serverboundchatpacket.message());
        } catch (Throwable throwable) {
            ClaimBlocksMod.LOGGER.error("[FantasticClaims] Fallo capturando la respuesta del menu", throwable);
        }
    }

    @Inject(
        method = {"broadcastChatMessage"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void claimblocks$hideConsumedPrompt(PlayerChatMessage playerchatmessage, CallbackInfo callbackinfo) {
        try {
            ServerGamePacketListenerImpl servergamepacketlistenerimpl = (ServerGamePacketListenerImpl)(Object)this;
            ServerPlayer serverplayer = servergamepacketlistenerimpl.getPlayer();
            if (serverplayer == null) {
                return;
            }

            if (ChatPromptRouter.shouldSuppress(serverplayer.getUUID(), playerchatmessage.signedContent())) {
                callbackinfo.cancel();
            }
        } catch (Throwable throwable) {
            ClaimBlocksMod.LOGGER.error("[FantasticClaims] Fallo ocultando la respuesta del menu", throwable);
        }
    }
}
