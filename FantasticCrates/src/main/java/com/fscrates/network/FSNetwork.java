package com.fscrates.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkRegistry.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor.TargetPoint;
import net.minecraftforge.network.simple.SimpleChannel;

public final class FSNetwork {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = ChannelBuilder.named(new ResourceLocation("fscrates", "main"))
        .networkProtocolVersion(() -> "2")
        .clientAcceptedVersions("2"::equals)
        .serverAcceptedVersions("2"::equals)
        .simpleChannel();

    private FSNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenEditorPacket.class, OpenEditorPacket::encode, OpenEditorPacket::decode, OpenEditorPacket::handle);
        CHANNEL.registerMessage(id++, SaveCratePacket.class, SaveCratePacket::encode, SaveCratePacket::decode, SaveCratePacket::handle);
        CHANNEL.registerMessage(id++, PlayAnimationPacket.class, PlayAnimationPacket::encode, PlayAnimationPacket::decode, PlayAnimationPacket::handle);
        CHANNEL.registerMessage(id++, OpenPreviewPacket.class, OpenPreviewPacket::encode, OpenPreviewPacket::decode, OpenPreviewPacket::handle);
        CHANNEL.registerMessage(id++, RequestOpenPacket.class, RequestOpenPacket::encode, RequestOpenPacket::decode, RequestOpenPacket::handle);
    }

    public static void sendToClient(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToNear(ServerLevel level, BlockPos pos, double radius, Object packet) {
        CHANNEL.send(
            PacketDistributor.NEAR
                .with(
                    () -> new TargetPoint((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, radius, level.dimension())
                ),
            packet
        );
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
