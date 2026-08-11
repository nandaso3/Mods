package com.fscrates.network;

import com.fscrates.block.CrateBlockEntity;
import com.fscrates.config.CrateConfig;
import com.fscrates.crate.CrateOpeningService;
import com.fscrates.crate.OpenAuthorization;
import com.fscrates.item.CrateItems;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Cliente -&gt; servidor: el jugador pulso ABRIR en la pantalla de pre-apertura.
 *
 * Toda la validacion se hace AQUI, en el servidor: cercania, llave y el resto de
 * comprobaciones. El cliente solo pide; nunca decide.
 */
public class RequestOpenPacket {
    private final BlockPos pos;
    private final boolean skipAnimation;

    public RequestOpenPacket(BlockPos pos, boolean skipAnimation) {
        this.pos = pos;
        this.skipAnimation = skipAnimation;
    }

    public static void encode(RequestOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.skipAnimation);
    }

    public static RequestOpenPacket decode(FriendlyByteBuf buf) {
        return new RequestOpenPacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(RequestOpenPacket msg, Supplier<Context> ctx) {
        Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            // Que siga estando al lado de la caja: nadie abre cajas a distancia.
            if (!player.level().isLoaded(msg.pos) || player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 64.0) {
                return;
            }

            if (!(player.level().getBlockEntity(msg.pos) instanceof CrateBlockEntity be)) {
                return;
            }

            // Solo se abre si el jugador llego aqui haciendo click en la caja de
            // verdad. Asi, si un plugin de proteccion cancela el click, no hay
            // permiso y este paquete no sirve de nada: no se puede saltar la
            // proteccion mandando el paquete a mano.
            if (!OpenAuthorization.consume(player, msg.pos)) {
                return;
            }

            CrateConfig crate = be.getConfig();
            ItemStack key = CrateItems.findUsableKey(player, crate);
            if (key.isEmpty()) {
                player.sendSystemMessage(
                    Component.literal("\u00a7cNecesitas \u00a7f" + CrateItems.requiredKeyName(crate) + "\u00a7c para abrir esta caja.")
                );
                return;
            }

            CrateOpeningService.open(player, crate, msg.pos, key, msg.skipAnimation);
        });
        context.setPacketHandled(true);
    }
}
