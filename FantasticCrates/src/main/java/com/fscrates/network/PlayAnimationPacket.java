package com.fscrates.network;

import com.fscrates.client.ClientPacketHandler;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class PlayAnimationPacket {
    private final BlockPos pos;
    private final String animationId;
    private final int rarityColor;
    private final int winnerIndex;
    private final int winnerRarity;
    private final CompoundTag candidates;
    private final long openerMost;
    private final long openerLeast;

    public PlayAnimationPacket(BlockPos pos, String animationId, int rarityColor, int winnerIndex, int winnerRarity, CompoundTag candidates) {
        this(pos, animationId, rarityColor, winnerIndex, winnerRarity, candidates, 0L, 0L);
    }

    public PlayAnimationPacket(
        BlockPos pos, String animationId, int rarityColor, int winnerIndex, int winnerRarity, CompoundTag candidates, long openerMost, long openerLeast
    ) {
        this.pos = pos;
        this.animationId = animationId;
        this.rarityColor = rarityColor;
        this.winnerIndex = winnerIndex;
        this.winnerRarity = winnerRarity;
        this.candidates = candidates;
        this.openerMost = openerMost;
        this.openerLeast = openerLeast;
    }

    public static void encode(PlayAnimationPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.animationId);
        buf.writeInt(msg.rarityColor);
        buf.writeInt(msg.winnerIndex);
        buf.writeInt(msg.winnerRarity);
        buf.writeNbt(msg.candidates);
        buf.writeLong(msg.openerMost);
        buf.writeLong(msg.openerLeast);
    }

    public static PlayAnimationPacket decode(FriendlyByteBuf buf) {
        return new PlayAnimationPacket(
            buf.readBlockPos(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readNbt(), buf.readLong(), buf.readLong()
        );
    }

    public static void handle(PlayAnimationPacket msg, Supplier<Context> ctx) {
        Context context = ctx.get();
        context.enqueueWork(
            () -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> ClientPacketHandler.playAnimation(
                                msg.pos,
                                msg.animationId,
                                msg.rarityColor,
                                msg.winnerIndex,
                                msg.winnerRarity,
                                msg.candidates,
                                new UUID(msg.openerMost, msg.openerLeast)
                            )
                )
        );
        context.setPacketHandled(true);
    }
}
