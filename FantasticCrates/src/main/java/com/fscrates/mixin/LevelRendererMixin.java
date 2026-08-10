package com.fscrates.mixin;

import com.fscrates.client.CinematicDiag;
import com.fscrates.client.screen.CrateCinematicScreen;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public class LevelRendererMixin {
    private static final Logger FSCRATES_LOGGER = LogUtils.getLogger();
    private static boolean fscrates$loggedCull = false;

    @Inject(
        method = {"renderLevel"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void fscrates$skipWorldDuringCinematic(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CrateCinematicScreen) {
            CinematicDiag.markCull();
            ci.cancel();
        }
    }
}
