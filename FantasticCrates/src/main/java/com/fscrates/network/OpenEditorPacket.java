package com.fscrates.network;

import com.fscrates.client.ClientPacketHandler;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class OpenEditorPacket {
    private final CompoundTag configNbt;
    private final BlockPos pos;

    public OpenEditorPacket(CompoundTag configNbt) {
        this(configNbt, null);
    }

    public OpenEditorPacket(CompoundTag configNbt, BlockPos pos) {
        this.configNbt = configNbt;
        this.pos = pos;
    }

    public static void encode(OpenEditorPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.configNbt);
        boolean hasPos = msg.pos != null;
        buf.writeBoolean(hasPos);
        if (hasPos) {
            buf.writeBlockPos(msg.pos);
        }
    }

    public static OpenEditorPacket decode(FriendlyByteBuf buf) {
        CompoundTag nbt = buf.readNbt();
        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new OpenEditorPacket(nbt, pos);
    }

    public static void handle(OpenEditorPacket msg, Supplier<Context> ctx) {
        Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openEditor(msg.configNbt, msg.pos)));
        context.setPacketHandled(true);
    }
}
