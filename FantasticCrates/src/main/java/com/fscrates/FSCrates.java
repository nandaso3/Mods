package com.fscrates;

import com.fscrates.client.ClientSetup;
import com.fscrates.command.FSCrateCommand;
import com.fscrates.config.ItemReferenceGenerator;
import com.fscrates.config.MediaGuide;
import com.fscrates.config.JsonCrateLoader;
import com.fscrates.network.FSNetwork;
import com.fscrates.registry.ModRegistry;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("fscrates")
public class FSCrates {
    public static final String MOD_ID = "fscrates";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FSCrates() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        LOGGER.info("[FSCrates] Initializing Fantastic Crates");
    }

    /**
     * Al arrancar el servidor: se genera la chuleta de items instalados y se
     * cargan las cajas definidas en config/fscrates/cajas/*.json.
     */
    private void onServerStarted(ServerStartedEvent event) {
        try {
            ItemReferenceGenerator.generate(event.getServer());
        } catch (Throwable t) {
            LOGGER.error("[FSCrates] Fallo generando la referencia de items", t);
        }

        // Chuleta de formatos de video, para el admin que configura las cajas
        // desde el servidor.
        MediaGuide.write();

        try {
            JsonCrateLoader.loadAll(event.getServer());
        } catch (Throwable t) {
            LOGGER.error("[FSCrates] Fallo cargando las cajas desde JSON", t);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FSNetwork::register);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ClientSetup::init);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        FSCrateCommand.register(event.getDispatcher());
    }
}
