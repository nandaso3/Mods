package com.fscrates.client;

import com.fscrates.client.media.CrateMedia;
import com.fscrates.client.screen.CratePoolScreen;
import com.fscrates.client.screen.CratePreOpenScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Red de seguridad para la media de la pre-apertura.
 *
 * Si por cualquier via el jugador acaba fuera de la pre-apertura o del pool de
 * recompensas (desconexion, otro mod cambiando de pantalla, un crash de GUI...)
 * paramos el video y la musica para no dejar hilos ni audio colgando.
 * Es el mismo enfoque que usa Fantastic Pass con su clientTick().
 */
@EventBusSubscriber(
    modid = "fscrates",
    value = {Dist.CLIENT},
    bus = Bus.FORGE
)
public final class MediaTicker {
    private MediaTicker() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.END || !CrateMedia.isActive()) {
            return;
        }

        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof CratePreOpenScreen) && !(screen instanceof CratePoolScreen)) {
            CrateMedia.stop();
        }
    }
}
