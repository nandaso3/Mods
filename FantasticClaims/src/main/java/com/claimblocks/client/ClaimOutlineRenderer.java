package com.claimblocks.client;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.ClaimTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
    modid = "claimblocks",
    value = {Dist.CLIENT},
    bus = Bus.FORGE
)
public final class ClaimOutlineRenderer {
    private ClaimOutlineRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent renderlevelstageevent) {
        if (renderlevelstageevent.getStage() == Stage.AFTER_PARTICLES) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer localplayer = minecraft.player;
            if (localplayer != null && minecraft.level != null) {
                Vec3 vec3 = renderlevelstageevent.getCamera().getPosition();
                PoseStack posestack = renderlevelstageevent.getPoseStack();
                BufferSource buffersource = minecraft.renderBuffers().bufferSource();
                VertexConsumer vertexconsumer = buffersource.getBuffer(RenderType.lines());
                posestack.pushPose();
                posestack.translate(-vec3.x, -vec3.y, -vec3.z);

                for (double[] adouble : ClientBorderStore.current()) {
                    LevelRenderer.renderLineBox(
                        posestack,
                        vertexconsumer,
                        adouble[0],
                        adouble[1],
                        adouble[2],
                        adouble[3],
                        adouble[4],
                        adouble[5],
                        (float)adouble[6],
                        (float)adouble[7],
                        (float)adouble[8],
                        0.9F
                    );
                }

                ClaimTier claimtier = ClaimBlocks.readTier(localplayer.getMainHandItem());
                if (claimtier == null) {
                    claimtier = ClaimBlocks.readTier(localplayer.getOffhandItem());
                }

                if (claimtier != null) {
                    BlockPos blockpos = localplayer.blockPosition();
                    double d0 = (double)(blockpos.getX() - claimtier.radius);
                    double d1 = (double)(blockpos.getX() + claimtier.radius + 1);
                    double d2 = (double)(blockpos.getZ() - claimtier.radius);
                    double d3 = (double)(blockpos.getZ() + claimtier.radius + 1);
                    double d4 = (double)(blockpos.getY() - claimtier.height);
                    double d5 = (double)(blockpos.getY() + claimtier.height + 1);
                    LevelRenderer.renderLineBox(posestack, vertexconsumer, d0, d4, d2, d1, d5, d3, claimtier.r, claimtier.g, claimtier.b, 1.0F);
                }

                posestack.popPose();
                buffersource.endBatch(RenderType.lines());
            }
        }
    }
}
