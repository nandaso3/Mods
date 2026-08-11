package com.fscrates.network;

import com.fscrates.client.ClientPacketHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Servidor -&gt; cliente: abre la pantalla de pre-apertura de una caja.
 *
 * Se manda al hacer click en la caja fisica, SIN exigir llave: la llave se pide
 * despues, al pulsar ABRIR dentro de la pantalla.
 */
public class OpenPreviewPacket {
    private final CompoundTag config;
    private final BlockPos pos;

    public OpenPreviewPacket(CompoundTag config, BlockPos pos) {
        this.config = config;
        this.pos = pos;
    }

    public static void encode(OpenPreviewPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.config);
        buf.writeBlockPos(msg.pos);
    }

    public static OpenPreviewPacket decode(FriendlyByteBuf buf) {
        return new OpenPreviewPacket(buf.readNbt(), buf.readBlockPos());
    }

    public static void handle(OpenPreviewPacket msg, Supplier<Context> ctx) {
        Context context = ctx.get();
        context.enqueueWork(
            () -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openPreview(msg.config, msg.pos))
        );
        context.setPacketHandled(true);
    }
}
