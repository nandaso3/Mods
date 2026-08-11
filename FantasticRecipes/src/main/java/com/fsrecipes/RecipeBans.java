package com.fsrecipes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fsrecipes.network.Net;
import com.fsrecipes.network.SyncBansPacket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registro central de baneos. Mantiene dos conjuntos disjuntos:
 *
 * <ul>
 *   <li>{@code RECIPE_BANS}: solo se elimina la receta del item.</li>
 *   <li>{@code ITEM_BANS}: se elimina la receta Y el item queda prohibido por completo.</li>
 * </ul>
 *
 * Todo es reversible: se puede pasar de un modo a otro o quitar el baneo en cualquier momento.
 */
public final class RecipeBans {
   private static final Set<ResourceLocation> RECIPE_BANS = ConcurrentHashMap.newKeySet();
   private static final Set<ResourceLocation> ITEM_BANS = ConcurrentHashMap.newKeySet();

   /** Recetas que quitamos del RecipeManager, para poder devolverlas al desbanear. */
   private static final Map<ResourceLocation, Recipe<?>> REMOVED = new ConcurrentHashMap<>();

   /** Cache resuelta de los items prohibidos, para los eventos que corren cada tick. */
   private static volatile Set<Item> ITEM_BAN_CACHE = Collections.emptySet();

   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private RecipeBans() {
   }

   private static Path file() {
      return FMLPaths.CONFIGDIR.get().resolve("fantasticrecipes-bans.json");
   }

   // ------------------------------------------------------------------ consultas

   /** Todos los baneos actuales como mapa id -> modo. */
   public static Map<ResourceLocation, BanMode> snapshot() {
      Map<ResourceLocation, BanMode> out = new LinkedHashMap<>();
      for (ResourceLocation id : RECIPE_BANS) {
         out.put(id, BanMode.RECIPE);
      }
      for (ResourceLocation id : ITEM_BANS) {
         out.put(id, BanMode.ITEM);
      }
      return out;
   }

   /** Modo de baneo de un item, o {@code null} si no esta baneado. */
   public static BanMode mode(ResourceLocation id) {
      if (id == null) {
         return null;
      } else if (ITEM_BANS.contains(id)) {
         return BanMode.ITEM;
      } else {
         return RECIPE_BANS.contains(id) ? BanMode.RECIPE : null;
      }
   }

   /** true si hay que quitar las recetas de este item (vale para los dos modos). */
   public static boolean isRecipeBanned(ResourceLocation id) {
      return id != null && (RECIPE_BANS.contains(id) || ITEM_BANS.contains(id));
   }

   /** true si el item en si esta prohibido (solo modo {@link BanMode#ITEM}). */
   public static boolean isItemBanned(ResourceLocation id) {
      return id != null && ITEM_BANS.contains(id);
   }

   public static boolean isItemBanned(Item item) {
      Set<Item> cache = ITEM_BAN_CACHE;
      return !cache.isEmpty() && item != null && cache.contains(item);
   }

   public static boolean isItemBanned(ItemStack stack) {
      return !ITEM_BAN_CACHE.isEmpty() && stack != null && !stack.isEmpty() && isItemBanned(stack.getItem());
   }

   /** Atajo rapido para los eventos: evita trabajo cuando no hay ningun item prohibido. */
   public static boolean hasItemBans() {
      return !ITEM_BAN_CACHE.isEmpty();
   }

   public static Set<ResourceLocation> recipeBans() {
      return Collections.unmodifiableSet(RECIPE_BANS);
   }

   public static Set<ResourceLocation> itemBans() {
      return Collections.unmodifiableSet(ITEM_BANS);
   }

   public static int count() {
      return RECIPE_BANS.size() + ITEM_BANS.size();
   }

   public static int recipeBanCount() {
      return RECIPE_BANS.size();
   }

   public static int itemBanCount() {
      return ITEM_BANS.size();
   }

   // ------------------------------------------------------------------ persistencia

   public static synchronized void loadFromDisk() {
      RECIPE_BANS.clear();
      ITEM_BANS.clear();
      Path path = file();
      if (Files.exists(path)) {
         try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) {
               // Formato antiguo (1.0.x): array plano de ids = baneo de solo receta.
               readInto(root.getAsJsonArray(), RECIPE_BANS);
            } else if (root.isJsonObject()) {
               JsonObject obj = root.getAsJsonObject();
               if (obj.has("recipes") && obj.get("recipes").isJsonArray()) {
                  readInto(obj.getAsJsonArray("recipes"), RECIPE_BANS);
               }
               if (obj.has("items") && obj.get("items").isJsonArray()) {
                  readInto(obj.getAsJsonArray("items"), ITEM_BANS);
               }
            }
            // Un item nunca puede estar en los dos: el baneo total manda.
            RECIPE_BANS.removeAll(new HashSet<>(ITEM_BANS));
         } catch (Exception ex) {
            FSRecipes.LOGGER.error("[FantasticRecipes] No se pudo leer {}: {}", path, ex.toString());
         }
      }

      rebuildItemCache();
   }

   private static void readInto(JsonArray arr, Set<ResourceLocation> target) {
      for (JsonElement e : arr) {
         try {
            ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
            if (id != null) {
               target.add(id);
            }
         } catch (Exception ignored) {
         }
      }
   }

   /** Escritura efectiva del JSON. La llama {@link BanTasks} de forma diferida. */
   static synchronized void saveNow() {
      saveToDisk();
   }

   private static synchronized void saveToDisk() {
      JsonObject root = new JsonObject();
      root.add("recipes", sortedArray(RECIPE_BANS));
      root.add("items", sortedArray(ITEM_BANS));

      try {
         Files.createDirectories(file().getParent());
         Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
      } catch (IOException ex) {
         FSRecipes.LOGGER.error("[FantasticRecipes] No se pudo guardar {}: {}", file(), ex.toString());
      }
   }

   private static JsonArray sortedArray(Set<ResourceLocation> set) {
      List<String> ids = new ArrayList<>(set.size());
      for (ResourceLocation id : set) {
         ids.add(id.toString());
      }
      Collections.sort(ids);

      JsonArray arr = new JsonArray();
      for (String s : ids) {
         arr.add(s);
      }
      return arr;
   }

   // ------------------------------------------------------------------ mutaciones

   /**
    * Aplica (o quita) un baneo.
    *
    * @param mode modo destino, o {@code null} para desbanear
    * @return true si el estado del item cambio
    */
   public static synchronized boolean setBan(MinecraftServer server, ResourceLocation itemId, BanMode mode) {
      if (itemId == null) {
         return false;
      }

      boolean newItemBan = mode == BanMode.ITEM && !ITEM_BANS.contains(itemId);
      boolean changed = apply(itemId, mode);
      if (changed) {
         afterChange(server, newItemBan);
      }
      return changed;
   }

   /** Aplica (o quita) un baneo a varios items de golpe. Devuelve cuantos cambiaron. */
   public static synchronized int setBanBulk(MinecraftServer server, List<ResourceLocation> ids, BanMode mode) {
      int n = 0;
      boolean newItemBan = false;

      for (ResourceLocation id : ids) {
         if (id != null) {
            if (mode == BanMode.ITEM && !ITEM_BANS.contains(id)) {
               newItemBan = true;
            }

            if (apply(id, mode)) {
               n++;
            }
         }
      }

      if (n > 0) {
         afterChange(server, newItemBan);
      }
      return n;
   }

   /** Quita TODOS los baneos de los dos modos. */
   public static synchronized int clearAll(MinecraftServer server) {
      int n = count();
      if (n > 0) {
         RECIPE_BANS.clear();
         ITEM_BANS.clear();
         afterChange(server, false);
      }
      return n;
   }

   /** Quita todos los baneos de un modo concreto, dejando el otro intacto. */
   public static synchronized int clearMode(MinecraftServer server, BanMode mode) {
      Set<ResourceLocation> target = mode == BanMode.ITEM ? ITEM_BANS : RECIPE_BANS;
      int n = target.size();
      if (n > 0) {
         target.clear();
         afterChange(server, false);
      }
      return n;
   }

   /** Cambia los conjuntos en memoria sin guardar ni resincronizar. */
   private static boolean apply(ResourceLocation id, BanMode mode) {
      BanMode before = mode(id);
      if (before == mode) {
         return false;
      }

      RECIPE_BANS.remove(id);
      ITEM_BANS.remove(id);
      if (mode == BanMode.RECIPE) {
         RECIPE_BANS.add(id);
      } else if (mode == BanMode.ITEM) {
         ITEM_BANS.add(id);
      }
      return true;
   }

   /**
    * Todo lo barato va aqui y ahora; lo caro lo agrupa {@link BanTasks} para no
    * congelar el tick en cada clic.
    *
    * @param newItemBan si el cambio añadio algun item nuevo en modo ITEM, lo unico que
    *                   obliga a repasar los inventarios del mundo
    */
   private static void afterChange(MinecraftServer server, boolean newItemBan) {
      rebuildItemCache();
      BanTasks.markSaveDirty();

      if (server != null) {
         // Inmediato: el crafteo queda bloqueado en este mismo tick.
         boolean recipesChanged = applyToManager(server.getRecipeManager(), server.registryAccess(), false);

         // Inmediato: son unos bytes, y es lo que mueve la GUI y los tooltips.
         if (Net.CHANNEL != null) {
            Net.CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncBansPacket(snapshot()));
         }

         // Diferido: el libro de recetas es enorme, y solo si de verdad cambio.
         if (recipesChanged) {
            BanTasks.markRecipeSyncDirty();
         }

         // Repartido entre ticks: inventarios, contenedores y entidades.
         if (newItemBan) {
            BanTasks.requestPurge(server);
         }
      }
   }

   private static void rebuildItemCache() {
      if (ITEM_BANS.isEmpty()) {
         ITEM_BAN_CACHE = Collections.emptySet();
         return;
      }

      Set<Item> items = Collections.newSetFromMap(new ConcurrentHashMap<>());
      for (ResourceLocation id : ITEM_BANS) {
         Item item = ForgeRegistries.ITEMS.getValue(id);
         if (item != null) {
            items.add(item);
         }
      }
      ITEM_BAN_CACHE = items;
   }

   // ------------------------------------------------------------------ recetas

   /**
    * Recalcula que recetas sobran y se las pasa al RecipeManager.
    *
    * @return true si el conjunto de recetas eliminadas cambio. Si no cambio no se toca
    *         el manager ni hace falta reenviar el libro de recetas a los clientes, que
    *         es justo la parte cara. Banear un item que no tiene ninguna receta
    *         (o pasar de "solo receta" a "item completo") sale gratis por aqui.
    */
   public static synchronized boolean applyToManager(RecipeManager rm, RegistryAccess registryAccess, boolean freshReload) {
      if (rm == null) {
         return false;
      }

      Map<ResourceLocation, Recipe<?>> full = new LinkedHashMap<>();

      for (Recipe<?> r : rm.getRecipes()) {
         full.put(r.getId(), r);
      }

      if (!freshReload) {
         for (Recipe<?> r : REMOVED.values()) {
            full.putIfAbsent(r.getId(), r);
         }
      }

      Map<ResourceLocation, Recipe<?>> removed = new LinkedHashMap<>();
      List<Recipe<?>> keep = new ArrayList<>(full.size());

      for (Recipe<?> r : full.values()) {
         if (isBannedOutput(r, registryAccess)) {
            removed.put(r.getId(), r);
         } else {
            keep.add(r);
         }
      }

      if (!freshReload && removed.keySet().equals(REMOVED.keySet())) {
         return false;
      }

      REMOVED.clear();
      REMOVED.putAll(removed);
      rm.replaceRecipes(keep);
      return true;
   }

   private static boolean isBannedOutput(Recipe<?> recipe, RegistryAccess registryAccess) {
      if (RECIPE_BANS.isEmpty() && ITEM_BANS.isEmpty()) {
         return false;
      } else {
         ItemStack out;
         try {
            out = recipe.getResultItem(registryAccess);
         } catch (Throwable ignored) {
            return false;
         }

         if (out != null && !out.isEmpty()) {
            return isRecipeBanned(ForgeRegistries.ITEMS.getKey(out.getItem()));
         } else {
            return false;
         }
      }
   }

   /**
    * Reenvia el libro de recetas completo. Es un paquete muy gordo (lleva todas las
    * recetas del juego), asi que solo se llama desde {@link BanTasks} una vez por
    * rafaga de cambios, nunca en cada clic.
    */
   static void sendRecipeBook(MinecraftServer server) {
      if (server != null) {
         ClientboundUpdateRecipesPacket pkt = new ClientboundUpdateRecipesPacket(server.getRecipeManager().getRecipes());

         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(pkt);
         }
      }
   }

   /** Manda a los clientes el estado de baneos (barato) y el libro de recetas (caro). */
   public static void resyncClients(MinecraftServer server) {
      if (server != null) {
         sendRecipeBook(server);

         if (Net.CHANNEL != null) {
            Net.CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncBansPacket(snapshot()));
         }
      }
   }
}
