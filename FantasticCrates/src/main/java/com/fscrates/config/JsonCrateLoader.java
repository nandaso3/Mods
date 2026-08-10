package com.fscrates.config;

import com.fscrates.FSCrates;
import com.fscrates.crate.CrateRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Lee y escribe la configuracion de cada crate en
 * config/fscrates/cajas/&lt;id&gt;.json (nombres de campo en espanol).
 *
 * Es una capa ADICIONAL sobre el registro NBT: al recargar sobreescribe lo que
 * hay en memoria, pero el SavedData de {@link CrateRegistry} sigue siendo la
 * persistencia principal.
 *
 * En el JSON solo se soportan recompensas de tipo ITEM. El resto de tipos
 * (COMMAND, XP, EFFECT, KEY) se siguen configurando desde el editor in-game y se
 * preservan al reescribir el archivo.
 */
public final class JsonCrateLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Separador decorativo usado en las claves de comentario del JSON. */
    private static final String RULE = "\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014\u2014";

    private JsonCrateLoader() {
    }

    /** config/fscrates/cajas/ */
    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve("fscrates").resolve("cajas");
    }

    public static Path fileFor(String id) {
        return folder().resolve(sanitize(id) + ".json");
    }

    private static String sanitize(String id) {
        String clean = (id == null ? "" : id.trim().toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9_.-]", "_");
        return clean.isEmpty() ? "crate" : clean;
    }

    // ------------------------------------------------------------------ lectura

    /**
     * Lee todos los .json de la carpeta y los mete en el registro del nivel.
     * Un archivo roto solo se salta a si mismo: nunca tira el servidor.
     */
    public static int loadAll(MinecraftServer server) {
        if (server == null) {
            return 0;
        }

        Path dir = folder();
        int loaded = 0;

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo crear la carpeta {}: {}", dir, e.toString());
            return 0;
        }

        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return 0;
        }
        CrateRegistry registry = CrateRegistry.get(overworld);

        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .sorted()
                .forEach(files::add);
        } catch (IOException e) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo listar {}: {}", dir, e.toString());
            return 0;
        }

        for (Path file : files) {
            try {
                CrateConfig config = read(file);
                if (config == null) {
                    continue;
                }
                registry.put(config);
                loaded++;
            } catch (Exception e) {
                FSCrates.LOGGER.error("[FSCrates] Error leyendo '{}': {}", file.getFileName(), e.toString());
            }
        }

        FSCrates.LOGGER.info("[FSCrates] Configuracion JSON recargada: {} caja(s) desde {}", loaded, dir);
        return loaded;
    }

    /** Parsea un archivo de caja. Devuelve null si no tiene forma de objeto JSON. */
    public static CrateConfig read(Path file) throws IOException {
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                FSCrates.LOGGER.warn("[FSCrates] '{}' no contiene un objeto JSON; se ignora.", file.getFileName());
                return null;
            }
            json = parsed.getAsJsonObject();
        }

        String id = string(json, "id", "");
        if (id.isBlank()) {
            // Sin id explicito usamos el nombre del archivo.
            String name = file.getFileName().toString();
            id = name.substring(0, name.length() - 5);
        }

        CrateConfig config = new CrateConfig(id.toLowerCase(Locale.ROOT));
        config.displayName = string(json, "nombreVisible", config.displayName);
        config.rarity = Rarity.byName(string(json, "rareza", config.rarity.name()));

        config.styleId = string(json, "estiloBloque", config.styleId);
        config.animationId = string(json, "animacion", config.animationId);
        config.glow = bool(json, "brillar", config.glow);
        config.particles = bool(json, "particulas", config.particles);
        config.floatingName = bool(json, "mostrarNombreFlotante", config.floatingName);
        config.nameColorHexOverride = string(json, "colorNombreHex", config.nameColorHexOverride);
        config.sizeScale = (float) number(json, "escala", config.sizeScale);
        config.yOffset = (float) number(json, "offsetAltura", config.yOffset);
        config.yawOffset = (float) number(json, "offsetRotacion", config.yawOffset);

        config.floatingText.clear();
        config.floatingText.addAll(strings(json, "textoFlotante"));

        config.consumeKey = bool(json, "consumirLlave", config.consumeKey);
        config.uniqueKeyEnabled = bool(json, "llaveUnicaActivada", config.uniqueKeyEnabled);
        config.uniqueKeyModel = string(json, "llaveUnicaModelo", config.uniqueKeyModel);
        config.uniqueKeyName = string(json, "llaveUnicaNombre", config.uniqueKeyName);

        config.rolls = Math.max(1, (int) number(json, "tiradasPorApertura", config.rolls));
        config.cooldownSeconds = Math.max(0, (int) number(json, "cooldownSegundos", config.cooldownSeconds));
        // En el JSON el retraso va en SEGUNDOS; internamente son ticks.
        if (json.has("retrasoAperturaSegundos")) {
            config.openDelayTicks = Math.max(0, (int) Math.round(number(json, "retrasoAperturaSegundos", 0.0) * 20.0));
        }
        config.allowSkip = bool(json, "permitirSaltar", config.allowSkip);
        config.broadcast = bool(json, "anuncioGlobal", config.broadcast);
        config.openOncePerPlayer = bool(json, "soloUnAperturaPorJugador", config.openOncePerPlayer);
        config.requiredPermission = string(json, "permisoRequerido", config.requiredPermission);

        config.pityEnabled = bool(json, "pityActivado", config.pityEnabled);
        config.pityInterval = Math.max(1, (int) number(json, "pityIntervalo", config.pityInterval));
        config.pityRarity = Rarity.byName(string(json, "pityRareza", config.pityRarity.name()));

        if (json.has("probabilidadRareza") && json.get("probabilidadRareza").isJsonObject()) {
            JsonObject chances = json.getAsJsonObject("probabilidadRareza");
            config.rarityChances.clear();
            for (Rarity rarity : Rarity.values()) {
                if (chances.has(rarity.name())) {
                    config.rarityChances.put(rarity, Math.max(0.0, chances.get(rarity.name()).getAsDouble()));
                }
            }
            if (config.rarityChances.isEmpty()) {
                config.rarityChances.putAll(CrateConfig.defaultRarityChances());
            }
        }

        config.showOdds = bool(json, "mostrarProbabilidades", config.showOdds);

        config.videos.clear();
        config.videos.addAll(strings(json, "videos"));
        config.music.clear();
        config.music.addAll(strings(json, "musica"));

        config.rewards.clear();
        if (json.has("recompensas") && json.get("recompensas").isJsonArray()) {
            JsonArray rewards = json.getAsJsonArray("recompensas");
            for (int i = 0; i < rewards.size(); i++) {
                JsonElement element = rewards.get(i);
                if (!element.isJsonObject()) {
                    continue;
                }
                RewardEntry entry = readReward(element.getAsJsonObject(), file, i);
                if (entry != null) {
                    config.rewards.add(entry);
                }
            }
        }

        // Los tipos que el JSON no soporta se recuperan del registro para no
        // perderlos al recargar (se guardan en un bloque aparte del archivo).
        config.rewards.addAll(readPreservedRewards(json));

        return config;
    }

    private static RewardEntry readReward(JsonObject json, Path file, int index) {
        String type = string(json, "tipo", "ITEM").trim().toUpperCase(Locale.ROOT);
        if (!"ITEM".equals(type)) {
            FSCrates.LOGGER.warn(
                "[FSCrates] '{}' recompensa #{}: el tipo '{}' no se puede definir en JSON (solo ITEM). Usa el editor in-game. Se ignora.",
                file.getFileName(),
                index,
                type
            );
            return null;
        }

        RewardEntry entry = new RewardEntry(RewardEntry.Type.ITEM);
        entry.label = string(json, "etiqueta", entry.label);
        entry.rarity = string(json, "rareza", "");
        entry.chance = number(json, "probabilidad", entry.chance);
        entry.guaranteed = bool(json, "garantizada", entry.guaranteed);
        entry.minAmount = Math.max(1, (int) number(json, "cantidadMinima", entry.minAmount));
        entry.maxAmount = Math.max(entry.minAmount, (int) number(json, "cantidadMaxima", entry.maxAmount));
        entry.item = readItem(json.has("item") && json.get("item").isJsonObject() ? json.getAsJsonObject("item") : null, file, index);
        return entry;
    }

    /** Construye el ItemStack desde {"id":..,"Count":..,"tag":{..}}. */
    private static ItemStack readItem(JsonObject json, Path file, int index) {
        if (json == null) {
            return ItemStack.EMPTY;
        }

        String itemId = string(json, "id", "").trim();
        if (itemId.isEmpty() || "minecraft:air".equals(itemId)) {
            return ItemStack.EMPTY;
        }

        ResourceLocation location = ResourceLocation.tryParse(itemId);
        // Cuidado: ForgeRegistries.ITEMS.getValue() devuelve AIR (no null) para
        // claves desconocidas, asi que hay que preguntar por containsKey.
        if (location == null || !ForgeRegistries.ITEMS.containsKey(location)) {
            FSCrates.LOGGER.warn(
                "[FSCrates] '{}' recompensa #{}: el item '{}' no existe (mod no instalado?). Se deja vacio.",
                file.getFileName(),
                index,
                itemId
            );
            return ItemStack.EMPTY;
        }

        Item item = ForgeRegistries.ITEMS.getValue(location);
        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);
        stack.setCount(Math.max(1, (int) number(json, "Count", 1.0)));

        if (json.has("tag") && json.get("tag").isJsonObject() && json.getAsJsonObject("tag").size() > 0) {
            try {
                JsonObject tagJson = json.getAsJsonObject("tag");
                // Compatibilidad: si el tag se guardo como SNBT crudo lo leemos tal cual.
                String snbt = tagJson.has("__snbt") ? tagJson.get("__snbt").getAsString() : GSON.toJson(tagJson);
                CompoundTag nbt = TagParser.parseTag(snbt);
                stack.setTag(nbt);
            } catch (Exception e) {
                FSCrates.LOGGER.warn(
                    "[FSCrates] '{}' recompensa #{}: el NBT de '{}' no se pudo leer ({}). Se entrega sin NBT.",
                    file.getFileName(),
                    index,
                    itemId,
                    e.getMessage()
                );
            }
        }

        return stack;
    }

    /** Recupera las recompensas no-ITEM que guardamos en crudo (NBT en base de texto). */
    private static List<RewardEntry> readPreservedRewards(JsonObject json) {
        List<RewardEntry> out = new ArrayList<>();
        if (!json.has("recompensasAvanzadas") || !json.get("recompensasAvanzadas").isJsonArray()) {
            return out;
        }

        JsonArray array = json.getAsJsonArray("recompensasAvanzadas");
        for (int i = 0; i < array.size(); i++) {
            try {
                String snbt = array.get(i).getAsString();
                if (snbt == null || snbt.isBlank()) {
                    continue;
                }
                out.add(RewardEntry.load(TagParser.parseTag(snbt)));
            } catch (Exception e) {
                FSCrates.LOGGER.warn("[FSCrates] No se pudo leer una recompensa avanzada: {}", e.toString());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ escritura

    /** Serializa la crate a config/fscrates/cajas/&lt;id&gt;.json */
    public static void saveToFile(CrateConfig config) {
        if (config == null) {
            return;
        }

        Path file = fileFor(config.id);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(toJson(config), writer);
            }
            FSCrates.LOGGER.info("[FSCrates] Caja guardada en {}", file);
        } catch (Exception e) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo escribir '{}': {}", file, e.toString());
        }
    }

    public static JsonObject toJson(CrateConfig config) {
        JsonObject root = new JsonObject();

        root.addProperty("// IDENTIFICACION", RULE);
        root.addProperty("id", config.id);
        root.addProperty("nombreVisible", config.displayName);
        root.addProperty("rareza", config.rarity.name());

        root.addProperty("// ANIMACION Y VISUAL", RULE);
        root.addProperty("estiloBloque", config.styleId == null ? "" : config.styleId);
        root.addProperty("animacion", config.animationId == null ? "" : config.animationId);
        root.addProperty("brillar", config.glow);
        root.addProperty("particulas", config.particles);
        root.addProperty("mostrarNombreFlotante", config.floatingName);
        JsonArray floating = new JsonArray();
        config.floatingText.forEach(floating::add);
        root.add("textoFlotante", floating);
        root.addProperty("colorNombreHex", config.nameColorHexOverride == null ? "" : config.nameColorHexOverride);
        root.addProperty("escala", config.sizeScale);
        root.addProperty("offsetAltura", config.yOffset);
        root.addProperty("offsetRotacion", config.yawOffset);

        root.addProperty("// LLAVES", RULE);
        root.addProperty("consumirLlave", config.consumeKey);
        root.addProperty("llaveUnicaActivada", config.uniqueKeyEnabled);
        root.addProperty("llaveUnicaModelo", config.uniqueKeyModel == null ? "" : config.uniqueKeyModel);
        root.addProperty("llaveUnicaNombre", config.uniqueKeyName == null ? "" : config.uniqueKeyName);

        root.addProperty("// APERTURA", RULE);
        root.addProperty("tiradasPorApertura", config.rolls);
        root.addProperty("cooldownSegundos", config.cooldownSeconds);
        root.addProperty("retrasoAperturaSegundos", config.openDelayTicks / 20.0);
        root.addProperty("permitirSaltar", config.allowSkip);
        root.addProperty("anuncioGlobal", config.broadcast);
        root.addProperty("soloUnAperturaPorJugador", config.openOncePerPlayer);
        root.addProperty("permisoRequerido", config.requiredPermission == null ? "" : config.requiredPermission);

        root.addProperty("// PITY (garantia)", RULE);
        root.addProperty("pityActivado", config.pityEnabled);
        root.addProperty("pityIntervalo", config.pityInterval);
        root.addProperty("pityRareza", (config.pityRarity == null ? Rarity.LEGENDARY : config.pityRarity).name());

        root.addProperty("// PROBABILIDADES DE RAREZA", RULE);
        JsonObject chances = new JsonObject();
        for (Rarity rarity : Rarity.values()) {
            chances.addProperty(rarity.name(), config.rarityChance(rarity));
        }
        root.add("probabilidadRareza", chances);

        root.addProperty("// MOSTRAR ODDS EN POOL", RULE);
        root.addProperty("mostrarProbabilidades", config.showOdds);

        root.addProperty("// RECOMPENSAS (solo tipo ITEM)", RULE);
        JsonArray rewards = new JsonArray();
        JsonArray advanced = new JsonArray();
        for (RewardEntry entry : config.rewards) {
            if (entry.type == RewardEntry.Type.ITEM) {
                rewards.add(rewardToJson(entry));
            } else {
                // Tipos no editables en JSON: se conservan en crudo.
                advanced.add(entry.save().toString());
            }
        }
        root.add("recompensas", rewards);
        if (!advanced.isEmpty()) {
            root.addProperty("// RECOMPENSAS AVANZADAS", "No editar a mano: COMMAND/XP/EFFECT/KEY se configuran en el editor in-game.");
            root.add("recompensasAvanzadas", advanced);
        }

        root.addProperty("// MEDIA (pantalla de pre-apertura)", "Links directos. El video debe ser MP4 (H.264); WEBM no se puede reproducir.");
        JsonArray videos = new JsonArray();
        config.videos.forEach(videos::add);
        root.add("videos", videos);
        JsonArray music = new JsonArray();
        config.music.forEach(music::add);
        root.add("musica", music);

        return root;
    }

    private static JsonObject rewardToJson(RewardEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("tipo", "ITEM");
        json.addProperty("etiqueta", entry.label == null ? "" : entry.label);
        json.addProperty("rareza", entry.rarity == null ? "" : entry.rarity);
        json.addProperty("probabilidad", entry.chance);
        json.addProperty("garantizada", entry.guaranteed);
        json.addProperty("cantidadMinima", entry.minAmount);
        json.addProperty("cantidadMaxima", entry.maxAmount);

        JsonObject item = new JsonObject();
        ItemStack stack = entry.item;
        if (stack != null && !stack.isEmpty()) {
            item.addProperty("id", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
            item.addProperty("Count", stack.getCount());
            CompoundTag tag = stack.getTag();
            item.add("tag", tag == null || tag.isEmpty() ? new JsonObject() : nbtToJson(tag));
        } else {
            item.addProperty("id", "minecraft:air");
            item.addProperty("Count", 1);
            item.add("tag", new JsonObject());
        }
        json.add("item", item);
        return json;
    }

    /**
     * Convierte NBT a JSON limpio y editable a mano.
     *
     * No sirve usar CompoundTag.toString() porque el SNBT lleva sufijos de tipo
     * (1b, 3.0f, [I;1,2]) y claves sin comillas, que no son JSON valido. Usamos
     * la conversion de DataFixerUpper NbtOps -&gt; JsonOps, que es la que emplea
     * Minecraft para lo mismo.
     */
    private static JsonElement nbtToJson(CompoundTag tag) {
        try {
            JsonElement converted = new Dynamic<>(NbtOps.INSTANCE, (Tag) tag).convert(JsonOps.INSTANCE).getValue();
            if (converted != null && converted.isJsonObject()) {
                return converted;
            }
        } catch (Exception e) {
            FSCrates.LOGGER.warn("[FSCrates] No se pudo convertir el NBT a JSON, se guarda como SNBT: {}", e.toString());
        }

        // Fallback: guardamos el SNBT crudo bajo una clave especial que sabemos leer.
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("__snbt", tag.toString());
        return wrapper;
    }

    // ------------------------------------------------------------------- helpers

    private static String string(JsonObject json, String key, String fallback) {
        try {
            if (json.has(key) && json.get(key).isJsonPrimitive()) {
                return json.get(key).getAsString();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        try {
            if (json.has(key) && json.get(key).isJsonPrimitive()) {
                return json.get(key).getAsBoolean();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static double number(JsonObject json, String key, double fallback) {
        try {
            if (json.has(key) && json.get(key).isJsonPrimitive()) {
                return json.get(key).getAsDouble();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray array = json.getAsJsonArray(key);
            for (int i = 0; i < array.size(); i++) {
                try {
                    String value = array.get(i).getAsString();
                    if (value != null && !value.isBlank()) {
                        out.add(value.trim());
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }
}
