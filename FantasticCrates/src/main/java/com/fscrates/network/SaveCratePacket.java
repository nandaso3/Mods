package com.fscrates.network;

import com.fscrates.block.CrateBlockEntity;
import com.fscrates.config.CrateConfig;
import com.fscrates.config.JsonCrateLoader;
import com.fscrates.crate.CrateRegistry;
import com.fscrates.item.CrateItems;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent.Context;

public class SaveCratePacket {
    private final CompoundTag configNbt;
    private final BlockPos pos;

    public SaveCratePacket(CompoundTag configNbt) {
        this(configNbt, null);
    }

    public SaveCratePacket(CompoundTag configNbt, BlockPos pos) {
        this.configNbt = configNbt;
        this.pos = pos;
    }

    public static void encode(SaveCratePacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.configNbt);
        boolean hasPos = msg.pos != null;
        buf.writeBoolean(hasPos);
        if (hasPos) {
            buf.writeBlockPos(msg.pos);
        }
    }

    public static SaveCratePacket decode(FriendlyByteBuf buf) {
        CompoundTag nbt = buf.readNbt();
        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new SaveCratePacket(nbt, pos);
    }

    public static void handle(SaveCratePacket msg, Supplier<Context> ctx) {
        Context context = ctx.get();
        context.enqueueWork(
            () -> {
                ServerPlayer player = context.getSender();
                if (player != null && player.hasPermissions(4) && msg.configNbt != null) {
                    CrateConfig crate = CrateConfig.load(msg.configNbt);
                    if (crate.id == null || crate.id.isBlank()) {
                        crate.id = "crate_" + System.currentTimeMillis();
                    }

                    CrateRegistry.get(player.serverLevel()).put(crate);
                    // Espejo en config/fscrates/cajas/<id>.json para poder editarlo a mano.
                    JsonCrateLoader.saveToFile(crate);
                    if (msg.pos != null) {
                        ServerLevel level = player.serverLevel();
                        if (level.getBlockEntity(msg.pos) instanceof CrateBlockEntity crateBe) {
                            crateBe.setConfig(crate);
                            player.sendSystemMessage(Component.literal("\u00a7aCofre '" + crate.id + "' actualizado en el sitio."));
                        } else {
                            player.sendSystemMessage(Component.literal("\u00a7eEl cofre ya no esta ahi; se guardo '" + crate.id + "' en el registro."));
                        }

                        if (crate.uniqueKeyEnabled) {
                            ItemStack uniqueKey = CrateItems.buildUniqueKey(crate);
                            if (!player.getInventory().add(uniqueKey)) {
                                player.drop(uniqueKey, false);
                            }

                            player.sendSystemMessage(
                                Component.literal("\u00a7b\u2726 Llave \u00fanica \u2726\u00a7a de '" + crate.id + "' entregada a tu inventario.")
                            );
                        }
                    } else {
                        ItemStack crateItem = CrateItems.buildCrate(crate);
                        if (!player.getInventory().add(crateItem)) {
                            player.drop(crateItem, false);
                        }

                        if (crate.uniqueKeyEnabled) {
                            ItemStack uniqueKey = CrateItems.buildUniqueKey(crate);
                            if (!player.getInventory().add(uniqueKey)) {
                                player.drop(uniqueKey, false);
                            }

                            player.sendSystemMessage(
                                Component.literal(
                                    "\u00a7aCrate '" + crate.id + "' guardada y entregada \u00a77(+ su \u00a7b\u2726 llave \u00fanica \u2726\u00a77)."
                                )
                            );
                        } else {
                            player.sendSystemMessage(
                                Component.literal(
                                    "\u00a7aCrate '" + crate.id + "' guardada y entregada. Usa \u00a7e/fscrate key give\u00a7a para dar llaves."
                                )
                            );
                        }
                    }
                }
            }
        );
        context.setPacketHandled(true);
    }
}
