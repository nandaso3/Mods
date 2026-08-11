package com.fsrecipes.network;

import com.fsrecipes.BanMode;
import com.fsrecipes.RecipeBans;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

/** Cliente -> servidor: operaciones masivas (categorias enteras y limpiezas). */
public class BulkBanPacket {
   /** Aplica {@code mode} a la lista de ids ({@code null} = desbanear esos ids). */
   public static final int OP_SET = 0;
   /** Quita todos los baneos de los dos modos. */
   public static final int OP_CLEAR_ALL = 1;
   /** Quita solo los baneos de tipo "solo receta". */
   public static final int OP_CLEAR_RECIPES = 2;
   /** Quita solo los baneos de tipo "item completo". */
   public static final int OP_CLEAR_ITEMS = 3;

   private final int op;
   private final BanMode mode;
   private final List<ResourceLocation> ids;

   public BulkBanPacket(int op, BanMode mode, List<ResourceLocation> ids) {
      this.op = op;
      this.mode = mode;
      this.ids = ids;
   }

   public static BulkBanPacket set(List<ResourceLocation> ids, BanMode mode) {
      return new BulkBanPacket(OP_SET, mode, ids);
   }

   public static BulkBanPacket clear(int op) {
      return new BulkBanPacket(op, null, new ArrayList<>());
   }

   public static void encode(BulkBanPacket msg, FriendlyByteBuf buf) {
      buf.writeByte(msg.op);
      buf.writeByte(BanMode.idOf(msg.mode));
      buf.writeVarInt(msg.ids.size());
      for (ResourceLocation id : msg.ids) {
         buf.writeResourceLocation(id);
      }
   }

   public static BulkBanPacket decode(FriendlyByteBuf buf) {
      int op = buf.readByte();
      BanMode mode = BanMode.byId(buf.readByte());
      int n = buf.readVarInt();
      List<ResourceLocation> ids = new ArrayList<>(Math.max(16, n));
      for (int i = 0; i < n; i++) {
         ids.add(buf.readResourceLocation());
      }
      return new BulkBanPacket(op, mode, ids);
   }

   public static void handle(BulkBanPacket msg, Supplier<Context> ctx) {
      Context c = ctx.get();
      c.enqueueWork(() -> {
         ServerPlayer sp = c.getSender();
         if (sp != null && sp.hasPermissions(2)) {
            switch (msg.op) {
               case OP_CLEAR_ALL -> RecipeBans.clearAll(sp.server);
               case OP_CLEAR_RECIPES -> RecipeBans.clearMode(sp.server, BanMode.RECIPE);
               case OP_CLEAR_ITEMS -> RecipeBans.clearMode(sp.server, BanMode.ITEM);
               default -> RecipeBans.setBanBulk(sp.server, msg.ids, msg.mode);
            }
         }
      });
      c.setPacketHandled(true);
   }
}
