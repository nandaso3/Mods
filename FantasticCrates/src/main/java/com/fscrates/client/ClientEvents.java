package com.fscrates.client;

import com.fscrates.client.render.CrateBakedModels;
import com.fscrates.client.render.CrateModel;
import com.fscrates.client.render.CrateRenderer;
import com.fscrates.registry.ModRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.client.event.ModelEvent.RegisterAdditional;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
    modid = "fscrates",
    value = {Dist.CLIENT},
    bus = Bus.MOD
)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerLayers(RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CrateModel.LAYER, CrateModel::createLayer);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(RegisterAdditional event) {
        CrateBakedModels.registerAll(event);
    }

    @SubscribeEvent
    public static void registerRenderers(RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.CRATE_BE.get(), CrateRenderer::new);
    }
}
