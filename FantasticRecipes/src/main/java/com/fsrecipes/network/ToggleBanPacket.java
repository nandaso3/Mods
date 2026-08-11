package com.fsrecipes.network;

import com.fsrecipes.BanMode;
import com.fsrecipes.RecipeBans;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Cliente -> servidor: pone (o quita) el baneo de un item.
 *
 * <p>{@code mode == null} significa desbanear.
 */
public class ToggleBanPacket {
   private final ResourceLocation itemId;
   private final BanMode mode;

   public ToggleBanPacket(ResourceLocation itemId, BanMode mode) {
      this.itemId = itemId;
      this.mode = mode;
   }

   public static void encode(ToggleBanPacket msg, FriendlyByteBuf buf) {
      buf.writeResourceLocation(msg.itemId);
      buf.writeByte(BanMode.idOf(msg.mode));
   }

   public static ToggleBanPacket decode(FriendlyByteBuf buf) {
      ResourceLocation id = buf.readResourceLocation();
      return new ToggleBanPacket(id, BanMode.byId(buf.readByte()));
   }

   public static void handle(ToggleBanPacket msg, Supplier<Context> ctx) {
      Context c = ctx.get();
      c.enqueueWork(() -> {
         ServerPlayer sp = c.getSender();
         if (sp != null && sp.hasPermissions(2)) {
            // setBan ya guarda en disco y resincroniza a todos los clientes.
            RecipeBans.setBan(sp.server, msg.itemId, msg.mode);
         }
      });
      c.setPacketHandled(true);
   }
}
