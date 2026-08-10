package com.fscrates.config;

import com.fscrates.FSCrates;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Genera config/fscrates/items_referencia.json con TODOS los items registrados
 * (incluidos los de otros mods), agrupados por mod.
 *
 * Sirve como chuleta para el admin que edita a mano los JSON de las cajas: de
 * ahi se copia el "id" que va en el campo item.id de cada recompensa.
 *
 * Se regenera en cada arranque para reflejar los mods instalados en ese momento.
 */
public final class ItemReferenceGenerator {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ItemReferenceGenerator() {
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("fscrates").resolve("items_referencia.json");
    }

    public static void generate(MinecraftServer server) {
        try {
            Map<String, List<JsonObject>> byMod = new TreeMap<>();
            int total = 0;

            for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item == null) {
                    continue;
                }

                JsonObject entry = new JsonObject();
                entry.addProperty("id", id.toString());
                // En servidor no hay diccionario de idioma cargado, asi que
                // damos el nombre "bonito" derivado del id y la clave de
                // traduccion para quien quiera el nombre exacto.
                entry.addProperty("nombre", prettyName(id));
                entry.addProperty("claveTrad", item.getDescriptionId());

                byMod.computeIfAbsent(id.getNamespace(), key -> new ArrayList<>()).add(entry);
                total++;
            }

            byMod.values().forEach(list -> list.sort(Comparator.comparing(o -> o.get("id").getAsString())));

            JsonObject root = new JsonObject();
            root.addProperty(
                "// ARCHIVO DE REFERENCIA DE ITEMS",
                "Generado automaticamente al iniciar el servidor. No editar a mano: se sobreescribe."
            );
            root.addProperty("// ULTIMA GENERACION", LocalDateTime.now().format(STAMP));
            root.addProperty(
                "// USO",
                "Copia el 'id' de cualquier item y usalo en el campo item.id de las recompensas en config/fscrates/cajas/<caja>.json"
            );
            root.addProperty("totalItems", total);
            root.addProperty("totalMods", byMod.size());

            JsonObject mods = new JsonObject();
            for (Map.Entry<String, List<JsonObject>> e : sortedByName(byMod).entrySet()) {
                JsonObject modObject = new JsonObject();
                modObject.addProperty("nombre", modName(e.getKey()));
                modObject.addProperty("totalItems", e.getValue().size());
                JsonArray items = new JsonArray();
                e.getValue().forEach(items::add);
                modObject.add("items", items);
                mods.add(e.getKey(), modObject);
            }
            root.add("mods", mods);

            Path out = file();
            Files.createDirectories(out.getParent());
            try (Writer writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root, writer);
            }

            FSCrates.LOGGER.info("[FSCrates] items_referencia.json generado: {} items de {} mod(s).", total, byMod.size());
        } catch (Exception e) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo escribir items_referencia.json", e);
        }
    }

    /** minecraft primero, luego fscrates, y el resto alfabetico. */
    private static Map<String, List<JsonObject>> sortedByName(Map<String, List<JsonObject>> source) {
        Map<String, List<JsonObject>> out = new LinkedHashMap<>();
        if (source.containsKey("minecraft")) {
            out.put("minecraft", source.get("minecraft"));
        }
        if (source.containsKey(FSCrates.MOD_ID)) {
            out.put(FSCrates.MOD_ID, source.get(FSCrates.MOD_ID));
        }
        source.forEach((key, value) -> out.putIfAbsent(key, value));
        return out;
    }

    private static String modName(String modId) {
        if ("minecraft".equals(modId)) {
            return "Minecraft";
        }
        try {
            return ModList.get()
                .getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(modId);
        } catch (Throwable t) {
            return modId;
        }
    }

    /** "minecraft:netherite_sword" -> "Netherite Sword" */
    private static String prettyName(ResourceLocation id) {
        String[] parts = id.getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() == 0 ? id.getPath() : sb.toString();
    }
}
