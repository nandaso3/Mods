package com.fscrates.client.render;

import com.fscrates.config.CrateConfig;
import com.fscrates.item.CrateItems;
import com.fscrates.registry.ModRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class CrateItemRenderer extends BlockEntityWithoutLevelRenderer {
    public CrateItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CrateConfig cfg = CrateItems.readConfig(stack);
        if (cfg == null) {
            cfg = new CrateConfig();
            cfg.rarity = CrateItems.rarity(stack);
        }

        BakedModel base = CrateBakedModels.baseModel(cfg);
        BakedModel lid = CrateBakedModels.lidModel(cfg);
        BlockState state = ModRegistry.CRATE_BLOCK.get().defaultBlockState();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        ModelBlockRenderer mr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        pose.pushPose();
        float baseScale = CrateBakedModels.scaleFor(cfg) * Math.max(0.05F, cfg.sizeScale);
        pose.translate(0.5, 0.0, 0.5);
        pose.scale(baseScale, baseScale, baseScale);
        pose.translate(-0.5, 0.0, -0.5);
        mr.renderModel(pose.last(), vc, state, base, 1.0F, 1.0F, 1.0F, light, overlay);
        if (lid != null) {
            mr.renderModel(pose.last(), vc, state, lid, 1.0F, 1.0F, 1.0F, light, overlay);
        }

        pose.popPose();
    }
}
