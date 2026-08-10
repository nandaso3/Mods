package com.fscrates.client;

import com.fscrates.block.CrateBlockEntity;
import com.fscrates.client.screen.CrateCinematicScreen;
import com.fscrates.client.screen.CrateEditorScreen;
import com.fscrates.client.screen.CratePreOpenScreen;
import com.fscrates.config.CrateConfig;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void openEditor(CompoundTag configNbt) {
        openEditor(configNbt, null);
    }

    public static void openEditor(CompoundTag configNbt, BlockPos pos) {
        CrateConfig cfg = configNbt == null ? new CrateConfig() : CrateConfig.load(configNbt);
        Minecraft.getInstance().setScreen(new CrateEditorScreen(cfg, pos));
    }

    public static void playAnimation(BlockPos pos, String animationId, int rarityColor, int winnerIndex, int winnerRarity, CompoundTag candidates, UUID opener) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        BlockEntity blockEntity;
        if (level != null && (blockEntity = level.getBlockEntity(pos)) instanceof CrateBlockEntity) {
            CrateBlockEntity be = (CrateBlockEntity)blockEntity;
            List<ItemStack> cands = CrateBlockEntity.decodeItems(candidates);
            int[] candRarities = CrateBlockEntity.decodeRarities(candidates);
            boolean isOpener = mc.player != null && opener != null && mc.player.getUUID().equals(opener);
            boolean isInstant = "instant".equals(animationId);
            boolean cinematic = isOpener && cands != null && !cands.isEmpty() && !isInstant;
            if (cinematic) {
                // Lo que se hacia antes directamente: abrir la cinematica.
                // Ahora se ejecuta cuando el jugador pulsa "ABRIR" en la pre-apertura.
                Runnable openCinematic = () -> {
                    try {
                        mc.setScreen(new CrateCinematicScreen(be.getConfig(), rarityColor, winnerRarity, winnerIndex, cands, candRarities));
                    } catch (Throwable error) {
                        be.startAnimation(animationId, rarityColor, winnerIndex, winnerRarity, candRarities, cands);
                        return;
                    }

                    be.startSceneLid(rarityColor, winnerRarity, true);
                };

                try {
                    mc.setScreen(new CratePreOpenScreen(be.getConfig(), openCinematic));
                } catch (Throwable error) {
                    // Si la pre-apertura falla, no se pierde la apertura.
                    openCinematic.run();
                }

                return;
            }

            if (!isInstant) {
                be.startSceneLid(rarityColor, winnerRarity, false);
            } else {
                be.startAnimation(animationId, rarityColor, winnerIndex, winnerRarity, candRarities, cands);
            }
        }
    }
}
