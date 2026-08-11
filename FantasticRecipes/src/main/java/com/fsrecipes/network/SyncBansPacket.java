package com.fsrecipes.network;

import com.fsrecipes.BanMode;
import com.fsrecipes.client.ClientHooks;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

/** Servidor -> cliente: estado completo de baneos (sin abrir la GUI). */
public class SyncBansPacket {
   private final Map<ResourceLocation, BanMode> bans;

   public SyncBansPacket(Map<ResourceLocation, BanMode> bans) {
      this.bans = bans;
   }

   public static void encode(SyncBansPacket msg, FriendlyByteBuf buf) {
      BanCodec.write(buf, msg.bans);
   }

   public static SyncBansPacket decode(FriendlyByteBuf buf) {
      return new SyncBansPacket(BanCodec.read(buf));
   }

   public static void handle(SyncBansPacket msg, Supplier<Context> ctx) {
      Context c = ctx.get();
      c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.updateBans(msg.bans)));
      c.setPacketHandled(true);
   }
}
