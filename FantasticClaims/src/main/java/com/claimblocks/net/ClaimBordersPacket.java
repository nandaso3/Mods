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

    public ClaimBordersPacket(List<double[]> list) {
        this.boxes = list;
    }

    public static void encode(ClaimBordersPacket claimborderspacket, FriendlyByteBuf friendlybytebuf) {
        friendlybytebuf.writeVarInt(claimborderspacket.boxes.size());

        for (double[] adouble : claimborderspacket.boxes) {
            friendlybytebuf.writeDouble(adouble[0]);
            friendlybytebuf.writeDouble(adouble[1]);
            friendlybytebuf.writeDouble(adouble[2]);
            friendlybytebuf.writeDouble(adouble[3]);
            friendlybytebuf.writeDouble(adouble[4]);
            friendlybytebuf.writeDouble(adouble[5]);
            friendlybytebuf.writeFloat((float)adouble[6]);
            friendlybytebuf.writeFloat((float)adouble[7]);
            friendlybytebuf.writeFloat((float)adouble[8]);
        }
    }

    public static ClaimBordersPacket decode(FriendlyByteBuf friendlybytebuf) {
        int i = friendlybytebuf.readVarInt();
        ArrayList arraylist = new ArrayList(i);

        for (int j = 0; j < i; j++) {
            double[] adouble = new double[]{
                friendlybytebuf.readDouble(),
                friendlybytebuf.readDouble(),
                friendlybytebuf.readDouble(),
                friendlybytebuf.readDouble(),
                friendlybytebuf.readDouble(),
                friendlybytebuf.readDouble(),
                (double)friendlybytebuf.readFloat(),
                (double)friendlybytebuf.readFloat(),
                (double)friendlybytebuf.readFloat()
            };
            arraylist.add(adouble);
        }

        return new ClaimBordersPacket(arraylist);
    }

    public static void handle(ClaimBordersPacket claimborderspacket, Supplier<Context> supplier) {
        Context context = (Context)supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBorderStore.receive(claimborderspacket.boxes)));
        context.setPacketHandled(true);
    }
}
