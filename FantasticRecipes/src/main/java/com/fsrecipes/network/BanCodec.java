package com.fsrecipes.network;

import com.fsrecipes.BanMode;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Serializacion compartida del mapa de baneos (id -> modo). */
public final class BanCodec {
   private BanCodec() {
   }

   public static void write(FriendlyByteBuf buf, Map<ResourceLocation, BanMode> bans) {
      buf.writeVarInt(bans.size());
      for (Map.Entry<ResourceLocation, BanMode> e : bans.entrySet()) {
         buf.writeResourceLocation(e.getKey());
         buf.writeByte(BanMode.idOf(e.getValue()));
      }
   }

   public static Map<ResourceLocation, BanMode> read(FriendlyByteBuf buf) {
      int n = buf.readVarInt();
      Map<ResourceLocation, BanMode> out = new LinkedHashMap<>(Math.max(16, n));
      for (int i = 0; i < n; i++) {
         ResourceLocation id = buf.readResourceLocation();
         BanMode mode = BanMode.byId(buf.readByte());
         if (mode != null) {
            out.put(id, mode);
         }
      }
      return out;
   }
}
