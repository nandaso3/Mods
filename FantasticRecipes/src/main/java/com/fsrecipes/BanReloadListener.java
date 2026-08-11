package com.fsrecipes;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Recarga los baneos desde disco y los reaplica en cada /reload y al arrancar el mundo. */
public final class BanReloadListener implements PreparableReloadListener {
   private final RecipeManager recipeManager;
   private final RegistryAccess registryAccess;

   public BanReloadListener(RecipeManager recipeManager, RegistryAccess registryAccess) {
      this.recipeManager = recipeManager;
      this.registryAccess = registryAccess;
   }

   public CompletableFuture<Void> reload(
      PreparationBarrier prepBarrier,
      ResourceManager resourceManager,
      ProfilerFiller preparationsProfiler,
      ProfilerFiller reloadProfiler,
      Executor backgroundExecutor,
      Executor gameExecutor
   ) {
      return prepBarrier.wait(Unit.INSTANCE).thenRunAsync(() -> {
         RecipeBans.loadFromDisk();
         RecipeBans.applyToManager(this.recipeManager, this.registryAccess, true);

         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
            // Si el JSON se edito a mano, clientes e inventarios se ponen al dia.
            RecipeBans.resyncClients(server);
            ItemBanEnforcer.purgeEverything(server);
         }
      }, gameExecutor);
   }
}
