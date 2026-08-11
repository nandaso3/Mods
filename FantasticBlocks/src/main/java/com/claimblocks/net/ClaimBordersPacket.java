package com.claimblocks.net;

import com.claimblocks.client.ClientBorderStore;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class ClaimBordersPacket {
   public final List<double[]> boxes;

   public ClaimBordersPacket(List<double[]> boxes) {
      this.boxes = boxes;
   }

   public static void encode(ClaimBordersPacket msg, FriendlyByteBuf buf) {
      buf.writeVarInt(msg.boxes.size());

      for (double[] b : msg.boxes) {
         buf.writeDouble(b[0]);
         buf.writeDouble(b[1]);
         buf.writeDouble(b[2]);
         buf.writeDouble(b[3]);
         buf.writeDouble(b[4]);
         buf.writeDouble(b[5]);
         buf.writeFloat((float)b[6]);
         buf.writeFloat((float)b[7]);
         buf.writeFloat((float)b[8]);
      }
   }

   public static ClaimBordersPacket decode(FriendlyByteBuf buf) {
      int n = buf.readVarInt();
      List<double[]> list = new ArrayList<>(n);

      for (int i = 0; i < n; i++) {
         double[] b = new double[]{
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            (double)buf.readFloat(),
            (double)buf.readFloat(),
            (double)buf.readFloat()
         };
         list.add(b);
      }

      return new ClaimBordersPacket(list);
   }

   public static void handle(ClaimBordersPacket msg, Supplier<Context> ctxSupplier) {
      Context ctx = ctxSupplier.get();
      ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBorderStore.receive(msg.boxes)));
      ctx.setPacketHandled(true);
   }
}
