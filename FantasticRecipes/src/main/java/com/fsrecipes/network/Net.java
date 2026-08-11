package com.fsrecipes.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry.ChannelBuilder;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
   private static final String PROTOCOL = "1";
   public static SimpleChannel CHANNEL;

   private Net() {
   }

   public static void register() {
      CHANNEL = ChannelBuilder.named(new ResourceLocation("fsrecipes", "main"))
         .networkProtocolVersion(() -> "1")
         .clientAcceptedVersions("1"::equals)
         .serverAcceptedVersions("1"::equals)
         .simpleChannel();
      int id = 0;
      CHANNEL.registerMessage(id++, OpenScreenPacket.class, OpenScreenPacket::encode, OpenScreenPacket::decode, OpenScreenPacket::handle);
      CHANNEL.registerMessage(id++, SyncBansPacket.class, SyncBansPacket::encode, SyncBansPacket::decode, SyncBansPacket::handle);
      CHANNEL.registerMessage(id++, ToggleBanPacket.class, ToggleBanPacket::encode, ToggleBanPacket::decode, ToggleBanPacket::handle);
      CHANNEL.registerMessage(id++, BulkBanPacket.class, BulkBanPacket::encode, BulkBanPacket::decode, BulkBanPacket::handle);
   }
}
