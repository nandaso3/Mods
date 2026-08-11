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

/** Servidor -> cliente: abre la GUI con el estado actual de baneos. */
public class OpenScreenPacket {
   private final Map<ResourceLocation, BanMode> bans;

   public OpenScreenPacket(Map<ResourceLocation, BanMode> bans) {
      this.bans = bans;
   }

   public static void encode(OpenScreenPacket msg, FriendlyByteBuf buf) {
      BanCodec.write(buf, msg.bans);
   }

   public static OpenScreenPacket decode(FriendlyByteBuf buf) {
      return new OpenScreenPacket(BanCodec.read(buf));
   }

   public static void handle(OpenScreenPacket msg, Supplier<Context> ctx) {
      Context c = ctx.get();
      c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openScreen(msg.bans)));
      c.setPacketHandled(true);
   }
}
