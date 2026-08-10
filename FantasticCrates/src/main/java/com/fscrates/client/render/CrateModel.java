package com.fscrates.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

public class CrateModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("fscrates", "crate"), "main");
    private final ModelPart bottom;
    private final ModelPart lid;
    private final ModelPart lock;

    public CrateModel(ModelPart root) {
        this.bottom = root.getChild("bottom");
        this.lid = root.getChild("lid");
        this.lock = root.getChild("lock");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 19).addBox(1.0F, 0.0F, 1.0F, 14.0F, 10.0F, 14.0F), PartPose.ZERO);
        root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(1.0F, 0.0F, 1.0F, 14.0F, 5.0F, 14.0F), PartPose.offset(0.0F, 9.0F, 1.0F));
        root.addOrReplaceChild(
            "lock", CubeListBuilder.create().texOffs(0, 0).addBox(7.0F, -1.0F, 15.0F, 2.0F, 4.0F, 1.0F), PartPose.offset(0.0F, 8.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void render(PoseStack pose, VertexConsumer vc, int light, int overlay, float lidAngleRad, float r, float g, float b, float a) {
        this.lock.xRot = this.lid.xRot = -lidAngleRad;
        this.lid.render(pose, vc, light, overlay, r, g, b, a);
        this.lock.render(pose, vc, light, overlay, r, g, b, a);
        this.bottom.render(pose, vc, light, overlay, r, g, b, a);
    }
}
