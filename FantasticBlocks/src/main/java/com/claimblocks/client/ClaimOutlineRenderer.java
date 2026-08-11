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
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_PARTICLES) {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         if (player != null && mc.level != null) {
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            BufferSource buffer = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buffer.getBuffer(RenderType.lines());
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);

            for (double[] b : ClientBorderStore.current()) {
               LevelRenderer.renderLineBox(pose, vc, b[0], b[1], b[2], b[3], b[4], b[5], (float)b[6], (float)b[7], (float)b[8], 0.9F);
            }

            ClaimTier tier = ClaimBlocks.readTier(player.getMainHandItem());
            if (tier == null) {
               tier = ClaimBlocks.readTier(player.getOffhandItem());
            }

            if (tier != null) {
               BlockPos c = player.blockPosition();
               double minX = (double)(c.getX() - tier.radius);
               double maxX = (double)(c.getX() + tier.radius + 1);
               double minZ = (double)(c.getZ() - tier.radius);
               double maxZ = (double)(c.getZ() + tier.radius + 1);
               double minY = (double)(c.getY() - tier.height);
               double maxY = (double)(c.getY() + tier.height + 1);
               LevelRenderer.renderLineBox(pose, vc, minX, minY, minZ, maxX, maxY, maxZ, tier.r, tier.g, tier.b, 1.0F);
            }

            pose.popPose();
            buffer.endBatch(RenderType.lines());
         }
      }
   }
}
