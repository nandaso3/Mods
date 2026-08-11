package com.fsrecipes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Trabajo diferido del servidor, para que aplicar un baneo no congele el tick.
 *
 * <p>Reparto de responsabilidades al cambiar un baneo:
 *
 * <ul>
 *   <li><b>Inmediato</b>: quitar la receta del RecipeManager (el crafteo queda bloqueado
 *       al instante) y mandar el mapa de baneos a los clientes, que son unos pocos bytes.</li>
 *   <li><b>Diferido y agrupado</b>: reenviar el libro de recetas. Ese paquete lleva TODAS
 *       las recetas del juego, asi que en un modpack grande son megas y el cliente se
 *       queda tieso un momento al recibirlo. Se manda una sola vez cuando dejas de tocar
 *       cosas, y solo si el conjunto de recetas eliminadas cambio de verdad.</li>
 *   <li><b>Diferido y agrupado</b>: guardar el JSON en disco.</li>
 *   <li><b>Repartido entre ticks</b>: la purga de inventarios del mundo, con un
 *       presupuesto de tiempo por tick.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = FSRecipes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BanTasks {
   /** Tiempo maximo por tick dedicado a la purga. 3 ms de 50 ms no se nota. */
   private static final long PURGE_BUDGET_NANOS = 3000000L;
   /** Ticks de calma antes de reenviar el libro de recetas. */
   private static final int RECIPE_SYNC_DELAY = 20;
   /** Ticks de calma antes de escribir el JSON. */
   private static final int SAVE_DELAY = 20;

   /** Tope de chunks en espera. Si se desborda da igual: los cubre el abrir contenedor. */
   private static final int MAX_PENDING_CHUNKS = 4096;

   private static int recipeSyncCountdown = -1;
   private static int saveCountdown = -1;
   private static PurgeJob purge = null;

   /** Chunks recien cargados pendientes de revisar, como (nivel, posicion). */
   private static final ArrayDeque<PendingChunk> pendingChunks = new ArrayDeque<>();

   private BanTasks() {
   }

   /**
    * Encola un chunk recien cargado. No se barre en el momento a proposito: un jugador
    * volando carga muchos chunks por segundo y hacerlo ahi mismo se notaria.
    */
   static void enqueueChunk(ServerLevel level, ChunkPos pos) {
      if (level != null && pos != null && pendingChunks.size() < MAX_PENDING_CHUNKS) {
         pendingChunks.add(new PendingChunk(level, pos));
      }
   }

   static void markRecipeSyncDirty() {
      recipeSyncCountdown = RECIPE_SYNC_DELAY;
   }

   static void markSaveDirty() {
      saveCountdown = SAVE_DELAY;
   }

   /** Lanza (o reinicia) la purga del mundo. */
   static void requestPurge(MinecraftServer server) {
      if (server != null && RecipeBans.hasItemBans()) {
         purge = new PurgeJob(server);
      }
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server == null) {
         return;
      }

      if (saveCountdown > 0 && --saveCountdown == 0) {
         RecipeBans.saveNow();
      }

      if (recipeSyncCountdown > 0 && --recipeSyncCountdown == 0) {
         RecipeBans.sendRecipeBook(server);
      }

      if (purge == null && pendingChunks.isEmpty()) {
         return;
      }

      if (!RecipeBans.hasItemBans()) {
         purge = null;
         pendingChunks.clear();
         return;
      }

      long deadline = System.nanoTime() + PURGE_BUDGET_NANOS;

      do {
         if (purge != null) {
            if (!purge.step()) {
               purge.report();
               purge = null;
            }
         } else {
            PendingChunk next = pendingChunks.poll();
            if (next == null) {
               break;
            }

            LevelChunk chunk = next.level.getChunkSource().getChunkNow(next.pos.x, next.pos.z);
            if (chunk != null) {
               DeepSweeper.sweepChunk(chunk);
            }
         }
      } while (System.nanoTime() < deadline);
   }

   private record PendingChunk(ServerLevel level, ChunkPos pos) {
   }

   /** El estado es estatico: al arrancar un mundo hay que empezar limpio. */
   @SubscribeEvent
   public static void onServerStarting(ServerStartingEvent event) {
      purge = null;
      pendingChunks.clear();
      recipeSyncCountdown = -1;
      saveCountdown = -1;
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      purge = null;
      pendingChunks.clear();
      recipeSyncCountdown = -1;
      if (saveCountdown > 0) {
         saveCountdown = -1;
         RecipeBans.saveNow();
      }
   }

   /**
    * Purga a trozos: jugadores primero (para que quien acaba de banear vea el efecto ya),
    * luego las entidades y por ultimo los block entities de los chunks cargados.
    */
   private static final class PurgeJob {
      private final List<ServerLevel> levels = new ArrayList<>();
      private final List<ServerPlayer> players;
      private final long startNanos = System.nanoTime();

      private int playerIndex = 0;
      private int levelIndex = -1;
      private ServerLevel level = null;
      private List<Entity> entities = Collections.emptyList();
      private int entityIndex = 0;
      private List<ChunkPos> chunks = Collections.emptyList();
      private int chunkIndex = 0;

      private int removed = 0;
      private int chunksDone = 0;
      private final int viewDistance;

      private PurgeJob(MinecraftServer server) {
         this.players = new ArrayList<>(server.getPlayerList().getPlayers());
         this.viewDistance = Math.max(2, server.getPlayerList().getViewDistance() + 1);
         for (ServerLevel serverLevel : server.getAllLevels()) {
            this.levels.add(serverLevel);
         }
      }

      /** Hace una unidad de trabajo. Devuelve false cuando ya no queda nada. */
      private boolean step() {
         if (!RecipeBans.hasItemBans()) {
            return false;
         }

         if (this.playerIndex < this.players.size()) {
            ServerPlayer sp = this.players.get(this.playerIndex++);
            if (sp != null && !sp.hasDisconnected()) {
               this.removed += ItemBanEnforcer.sweepPlayer(sp, true);
            }

            return true;
         }

         if (this.entityIndex < this.entities.size()) {
            Entity entity = this.entities.get(this.entityIndex++);
            if (!(entity instanceof Player)) {
               this.removed += DeepSweeper.sweepEntity(entity);
            }

            return true;
         }

         if (this.chunkIndex < this.chunks.size()) {
            ChunkPos pos = this.chunks.get(this.chunkIndex++);
            LevelChunk chunk = this.level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk != null) {
               this.chunksDone++;
               this.removed += DeepSweeper.sweepChunk(chunk);
            }

            return true;
         }

         return this.nextLevel();
      }

      private boolean nextLevel() {
         this.levelIndex++;
         if (this.levelIndex >= this.levels.size()) {
            return false;
         }

         this.level = this.levels.get(this.levelIndex);

         // Copia de las entidades: iterar el almacen vivo a lo largo de varios ticks
         // reventaria en cuanto algo apareciese o muriese.
         this.entities = new ArrayList<>();
         for (Entity entity : this.level.getAllEntities()) {
            this.entities.add(entity);
         }
         this.entityIndex = 0;

         this.chunks = this.collectChunks(this.level);
         this.chunkIndex = 0;
         return true;
      }

      /** Chunks alrededor de los jugadores mas los forzados, sin duplicados. */
      private List<ChunkPos> collectChunks(ServerLevel serverLevel) {
         Set<Long> seen = new HashSet<>();
         List<ChunkPos> out = new ArrayList<>();

         for (ServerPlayer sp : serverLevel.players()) {
            ChunkPos center = sp.chunkPosition();
            for (int dx = -this.viewDistance; dx <= this.viewDistance; dx++) {
               for (int dz = -this.viewDistance; dz <= this.viewDistance; dz++) {
                  ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                  if (seen.add(pos.toLong())) {
                     out.add(pos);
                  }
               }
            }
         }

         for (long packed : serverLevel.getForcedChunks()) {
            ChunkPos pos = new ChunkPos(packed);
            if (seen.add(pos.toLong())) {
               out.add(pos);
            }
         }

         return out;
      }

      private void report() {
         if (this.removed > 0 || this.chunksDone > 0) {
            FSRecipes.LOGGER.info(
               "[FantasticRecipes] Purga terminada: {} stack(s) prohibidos eliminados, {} chunk(s) revisados, {} ms de reloj.",
               this.removed,
               this.chunksDone,
               (System.nanoTime() - this.startNanos) / 1000000L
            );
         }
      }
   }
}
