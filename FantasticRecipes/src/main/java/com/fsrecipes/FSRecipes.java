package com.fsrecipes;

import com.fsrecipes.command.FSRecipesCommand;
import com.fsrecipes.network.Net;
import com.fsrecipes.network.SyncBansPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

@Mod("fsrecipes")
public final class FSRecipes {
   public static final String MODID = "fsrecipes";
   public static final Logger LOGGER = LogUtils.getLogger();

   public FSRecipes() {
      Net.register();
   }

   @EventBusSubscriber(
      modid = "fsrecipes",
      bus = Bus.FORGE
   )
   public static final class ForgeEvents {
      private ForgeEvents() {
      }

      @SubscribeEvent
      public static void onAddReloadListener(AddReloadListenerEvent event) {
         event.addListener(new BanReloadListener(event.getServerResources().getRecipeManager(), event.getRegistryAccess()));
      }

      @SubscribeEvent
      public static void onRegisterCommands(RegisterCommandsEvent event) {
         FSRecipesCommand.register(event.getDispatcher(), event.getBuildContext());
      }

      /** Al entrar, el cliente recibe el estado de baneos (para tooltips y GUI). */
      @SubscribeEvent
      public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
         if (event.getEntity() instanceof ServerPlayer sp && Net.CHANNEL != null) {
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new SyncBansPacket(RecipeBans.snapshot()));
            ItemBanEnforcer.sweepPlayer(sp);
         }
      }
   }
}
