package com.fsrecipes.client.screen;

import com.fsrecipes.BanMode;
import com.fsrecipes.client.ClientHooks;
import com.fsrecipes.client.RegistryLists;
import com.fsrecipes.client.Sfx;
import com.fsrecipes.client.widget.ScrollSelector;
import com.fsrecipes.network.BulkBanPacket;
import com.fsrecipes.network.Net;
import com.fsrecipes.network.ToggleBanPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * GUI de administracion.
 *
 * <p>Funciona en dos pasos, a proposito: primero seleccionas (un item de cualquiera de
 * las dos listas, o una categoria entera) y luego pulsas el boton de la accion que
 * quieras. Asi un clic en la lista no cambia nada por si solo.
 */
public final class RecipeBanScreen extends Screen {
   private static final String TIP_RECIPE = "Quita la RECETA del item seleccionado.\n\n"
      + "§7Deja de poder craftearse, cocinarse o forjarse, pero se puede seguir teniendo, "
      + "usando y consiguiendo por loot, comandos o creativo.";
   private static final String TIP_ITEM = "Prohibe el ITEM seleccionado por completo.\n\n"
      + "§7Incluye quitar la receta, y ademas: no se puede tener en el inventario, usar, "
      + "recoger del suelo, tirar ni sacar del creativo. Se borra de todos los inventarios "
      + "donde aparezca, tambien dentro de mochilas, shulkers y cofres.";
   private static final String TIP_UNBAN = "Quita cualquier baneo de lo seleccionado y lo deja como estaba.";

   /** Filtros de la columna de baneados. */
   private static final int VIEW_ALL = 0;
   private static final int VIEW_RECIPE = 1;
   private static final int VIEW_ITEM = 2;

   // --- estado que sobrevive a rebuildWidgets() ---
   private boolean fromInventory = false;
   private int bannedView = VIEW_ALL;
   private String catalogQuery = "";
   private String bannedQuery = "";
   private int catalogScroll = 0;
   private int bannedScroll = 0;

   /** Item seleccionado, o null. Excluyente con {@link #selectedCategory}. */
   private ResourceLocation selectedItem = null;
   private ResourceKey<CreativeModeTab> selectedCategory = null;
   private String selectedCategoryName = "";
   private int selectedCategorySize = 0;

   private final List<RecipeBanScreen.Label> labels = new ArrayList<>();
   private ScrollSelector<Item> catalogList;
   private ScrollSelector<ItemStack> inventoryList;
   private ScrollSelector<Item> bannedList;
   private Button banRecipeButton;
   private Button banItemButton;
   private Button unbanButton;

   private int catalogCount = 0;
   private int gameItemTotal = 0;
   private int leftPos;
   private int topPos;
   private int panelW;
   private int panelH;

   public RecipeBanScreen() {
      super(Component.literal("Fantastic Recipes"));
   }

   public void onBansUpdated() {
      this.rebuildWidgets();
   }

   /** Guarda scroll y seleccion antes de que se destruyan los widgets. */
   protected void rebuildWidgets() {
      this.catalogScroll = this.catalogList != null
         ? this.catalogList.getScroll()
         : (this.inventoryList != null ? this.inventoryList.getScroll() : this.catalogScroll);
      if (this.bannedList != null) {
         this.bannedScroll = this.bannedList.getScroll();
      }

      super.rebuildWidgets();
   }

   // ------------------------------------------------------------------ seleccion

   /**
    * Seleccionar no cambia nada en el servidor y no reconstruye la pantalla: el
    * resaltado y el texto son instantaneos.
    */
   private void selectItem(Item item) {
      this.selectedItem = RegistryLists.id(item);
      this.selectedCategory = null;

      // Que el resaltado se vea en las dos listas, no solo en la que se pulso.
      if (this.catalogList != null) {
         this.catalogList.setSelected(item);
      }
      if (this.bannedList != null) {
         this.bannedList.setSelected(item);
      }

      Sfx.click();
   }

   private void selectCategory(ResourceKey<CreativeModeTab> key, String name) {
      this.selectedCategory = key;
      this.selectedCategoryName = name;
      this.selectedCategorySize = RegistryLists.itemsOfTab(key).size();
      this.selectedItem = null;
      Sfx.click();
   }

   private boolean hasSelection() {
      return this.selectedItem != null || this.selectedCategory != null;
   }

   private BanMode modeOf(Item item) {
      return ClientHooks.mode(RegistryLists.id(item));
   }

   private String tagOf(Item item) {
      return BanMode.tagOf(this.modeOf(item));
   }

   // ------------------------------------------------------------------ acciones

   /** Aplica un modo ({@code null} = desbanear) a lo que este seleccionado. */
   private void applyToSelection(BanMode mode) {
      if (this.selectedItem != null) {
         ClientHooks.setLocal(this.selectedItem, mode);
         Net.CHANNEL.sendToServer(new ToggleBanPacket(this.selectedItem, mode));
      } else {
         if (this.selectedCategory == null) {
            return;
         }

         List<ResourceLocation> ids = new ArrayList<>();
         for (Item it : RegistryLists.itemsOfTab(this.selectedCategory)) {
            ResourceLocation id = RegistryLists.id(it);
            if (id != null) {
               ids.add(id);
               ClientHooks.setLocal(id, mode);
            }
         }

         if (ids.isEmpty()) {
            return;
         }

         Net.CHANNEL.sendToServer(BulkBanPacket.set(ids, mode));
      }

      if (mode == null) {
         Sfx.click();
      } else {
         Sfx.success();
      }

      this.rebuildWidgets();
   }

   // ------------------------------------------------------------------ layout

   protected void init() {
      this.panelW = Math.min(this.width - 16, 500);
      this.panelH = Math.min(this.height - 16, 340);
      this.leftPos = (this.width - this.panelW) / 2;
      this.topPos = (this.height - this.panelH) / 2;
      this.labels.clear();
      this.catalogList = null;
      this.inventoryList = null;
      this.gameItemTotal = RegistryLists.items().size();

      int x = this.bodyX();
      int y = this.bodyY();
      int bodyH = this.bodyH();
      int colW = (this.bodyW() - 8) / 2;
      int rightX = x + colW + 8;
      int halfW = (colW - 2) / 2;

      int listY = y + 38;
      // La lista acaba 100px antes del final del cuerpo: ahi van el texto de seleccion,
      // la barra de acciones y las tres filas de categorias.
      int listH = Math.max(36, bodyH - 138);
      int actionY = y + bodyH - 84;
      int r1 = y + bodyH - 58;
      int r2 = y + bodyH - 40;
      int r3 = y + bodyH - 22;

      // --- columna izquierda: fuente + buscador + catalogo ---
      this.addRenderableWidget(
         Button.builder(Component.literal(this.fromInventory ? "Fuente: §bInventario" : "Fuente: §eRegistro"), b -> {
               this.fromInventory = !this.fromInventory;
               this.catalogScroll = 0;
               Sfx.click();
               this.rebuildWidgets();
            })
            .tooltip(
               Tooltip.create(
                  Component.literal("Registro = todos los items del juego, de todos los mods.\nInventario = solo los que llevas encima.")
               )
            )
            .bounds(x, y, colW, 16)
            .build()
      );

      EditBox search = new EditBox(this.font, x, y + 18, colW, 16, Component.empty());
      search.setHint(Component.literal("Buscar item..."));

      if (this.fromInventory) {
         List<ItemStack> inv = new ArrayList<>();
         Player p = this.minecraft != null ? this.minecraft.player : null;
         if (p != null) {
            for (ItemStack st : p.getInventory().items) {
               if (st != null && !st.isEmpty()) {
                  inv.add(st.copy());
               }
            }
         }

         ScrollSelector<ItemStack> list = new ScrollSelector<ItemStack>(
               x,
               listY,
               colW,
               listH,
               18,
               stx -> stx.getHoverName().getString(),
               stx -> stx.getHoverName().getString() + " " + RegistryLists.itemId(stx.getItem()),
               stx -> stx
            )
            .withTag(stx -> this.tagOf(stx.getItem()))
            .onSelect(stx -> this.selectItem(stx.getItem()));
         list.setItems(inv);
         this.inventoryList = list;
         this.catalogCount = inv.size();
         search.setResponder(text -> {
            this.catalogQuery = text;
            list.setQuery(text);
         });
         this.addRenderableWidget(list);
         if (inv.isEmpty()) {
            this.addLabel("§7Tu inventario esta vacio.", x + 2, listY + 4);
         }
      } else {
         ScrollSelector<Item> list = new ScrollSelector<>(
               x, listY, colW, listH, 18, RegistryLists::itemName, it -> RegistryLists.itemName(it) + " " + RegistryLists.itemId(it), ItemStack::new
            )
            .withTag(this::tagOf)
            .onSelect(this::selectItem);
         List<Item> allItems = RegistryLists.items();
         list.setItems(allItems);
         this.catalogList = list;
         this.catalogCount = allItems.size();
         search.setResponder(text -> {
            this.catalogQuery = text;
            list.setQuery(text);
         });
         this.addRenderableWidget(list);
      }

      search.setValue(this.catalogQuery);
      this.addRenderableWidget(search);

      // --- columna derecha: filtro + buscador + baneados ---
      Map<ResourceLocation, BanMode> bans = ClientHooks.bans();
      int recipeCount = 0;
      int itemCount = 0;
      List<Item> bannedItems = new ArrayList<>();

      for (Map.Entry<ResourceLocation, BanMode> e : bans.entrySet()) {
         if (e.getValue() == BanMode.ITEM) {
            itemCount++;
         } else {
            recipeCount++;
         }

         if (this.bannedView == VIEW_ALL
               || (this.bannedView == VIEW_RECIPE && e.getValue() == BanMode.RECIPE)
               || (this.bannedView == VIEW_ITEM && e.getValue() == BanMode.ITEM)) {
            Item it = ForgeRegistries.ITEMS.getValue(e.getKey());
            if (it != null) {
               bannedItems.add(it);
            }
         }
      }

      bannedItems.sort((a, b) -> RegistryLists.itemId(a).compareTo(RegistryLists.itemId(b)));

      String viewLabel = switch (this.bannedView) {
         case VIEW_RECIPE -> "Ver: §esolo receta";
         case VIEW_ITEM -> "Ver: §citem completo";
         default -> "Ver: §ftodos";
      };
      this.addRenderableWidget(
         Button.builder(Component.literal(viewLabel), b -> {
               this.bannedView = (this.bannedView + 1) % 3;
               this.bannedScroll = 0;
               Sfx.click();
               this.rebuildWidgets();
            })
            .tooltip(Tooltip.create(Component.literal("Filtra la lista de baneados por tipo de baneo.")))
            .bounds(rightX, y, colW, 16)
            .build()
      );

      EditBox bannedSearch = new EditBox(this.font, rightX, y + 18, colW, 16, Component.empty());
      bannedSearch.setHint(Component.literal("Buscar baneado..."));

      ScrollSelector<Item> banned = new ScrollSelector<>(
            rightX, listY, colW, listH, 18, RegistryLists::itemName, it -> RegistryLists.itemName(it) + " " + RegistryLists.itemId(it), ItemStack::new
         )
         .withTag(this::tagOf)
         .onSelect(this::selectItem);
      banned.setItems(bannedItems);
      this.bannedList = banned;
      bannedSearch.setResponder(text -> {
         this.bannedQuery = text;
         banned.setQuery(text);
      });
      this.addRenderableWidget(banned);
      bannedSearch.setValue(this.bannedQuery);
      this.addRenderableWidget(bannedSearch);

      // --- barra de acciones: se aplica a lo seleccionado ---
      int actionW = (this.bodyW() - 8) / 3;
      this.banRecipeButton = Button.builder(Component.literal("§eBanear receta"), b -> this.applyToSelection(BanMode.RECIPE))
         .tooltip(Tooltip.create(Component.literal(TIP_RECIPE)))
         .bounds(x, actionY, actionW, 20)
         .build();
      this.banItemButton = Button.builder(Component.literal("§cBanear item"), b -> this.applyToSelection(BanMode.ITEM))
         .tooltip(Tooltip.create(Component.literal(TIP_ITEM)))
         .bounds(x + actionW + 4, actionY, actionW, 20)
         .build();
      this.unbanButton = Button.builder(Component.literal("§aDesbanear"), b -> this.applyToSelection(null))
         .tooltip(Tooltip.create(Component.literal(TIP_UNBAN)))
         .bounds(x + 2 * (actionW + 4), actionY, this.bodyW() - 2 * (actionW + 4), 20)
         .build();
      this.addRenderableWidget(this.banRecipeButton);
      this.addRenderableWidget(this.banItemButton);
      this.addRenderableWidget(this.unbanButton);
      this.updateActionButtons();

      // --- categorias: seleccionan, no aplican ---
      int bw = colW / 3 - 2;
      this.addRenderableWidget(this.catButton("Bloques", x, r1, bw, CreativeModeTabs.BUILDING_BLOCKS));
      this.addRenderableWidget(this.catButton("Naturales", x + bw + 2, r1, bw, CreativeModeTabs.NATURAL_BLOCKS));
      this.addRenderableWidget(this.catButton("Funcional", x + 2 * (bw + 2), r1, bw, CreativeModeTabs.FUNCTIONAL_BLOCKS));
      this.addRenderableWidget(this.catButton("Combate", x, r2, bw, CreativeModeTabs.COMBAT));
      this.addRenderableWidget(this.catButton("Herram.", x + bw + 2, r2, bw, CreativeModeTabs.TOOLS_AND_UTILITIES));
      this.addRenderableWidget(this.catButton("Redstone", x + 2 * (bw + 2), r2, bw, CreativeModeTabs.REDSTONE_BLOCKS));
      this.addRenderableWidget(this.catButton("Comida", x, r3, bw, CreativeModeTabs.FOOD_AND_DRINKS));
      this.addRenderableWidget(this.catButton("Ingred.", x + bw + 2, r3, bw, CreativeModeTabs.INGREDIENTS));
      this.addRenderableWidget(this.catButton("Deco", x + 2 * (bw + 2), r3, bw, CreativeModeTabs.COLORED_BLOCKS));

      // --- limpiezas masivas ---
      final int recipeTotal = recipeCount;
      final int itemTotal = itemCount;

      this.addRenderableWidget(
         Button.builder(Component.literal("§eQuitar recetas"), b -> {
               Net.CHANNEL.sendToServer(BulkBanPacket.clear(BulkBanPacket.OP_CLEAR_RECIPES));
               ClientHooks.bans().values().removeIf(m -> m == BanMode.RECIPE);
               Sfx.success();
               this.rebuildWidgets();
            })
            .tooltip(Tooltip.create(Component.literal("Quita los " + recipeTotal + " baneo(s) de tipo \"solo receta\". No toca los items prohibidos.")))
            .bounds(rightX, r2, halfW, 16)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("§cQuitar items"), b -> {
               Net.CHANNEL.sendToServer(BulkBanPacket.clear(BulkBanPacket.OP_CLEAR_ITEMS));
               ClientHooks.bans().values().removeIf(m -> m == BanMode.ITEM);
               Sfx.success();
               this.rebuildWidgets();
            })
            .tooltip(Tooltip.create(Component.literal("Quita los " + itemTotal + " baneo(s) de tipo \"item completo\". No toca los baneos de solo receta.")))
            .bounds(rightX + halfW + 2, r2, colW - halfW - 2, 16)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("§aDesbanear TODO"), b -> {
               Net.CHANNEL.sendToServer(BulkBanPacket.clear(BulkBanPacket.OP_CLEAR_ALL));
               ClientHooks.bans().clear();
               Sfx.success();
               this.rebuildWidgets();
            })
            .tooltip(Tooltip.create(Component.literal("Quita TODOS los baneos, de los dos tipos.")))
            .bounds(rightX, r3, colW, 16)
            .build()
      );

      this.addRenderableWidget(
         Button.builder(Component.literal("Cerrar"), b -> this.onClose())
            .bounds(this.leftPos + this.panelW - 88, this.topPos + this.panelH - 24, 80, 18)
            .build()
      );

      // --- textos ---
      String fuente = this.fromInventory ? "inventario" : "todos los mods";
      this.addLabel("§eCatalogo §f" + this.catalogCount + " items §7(" + fuente + ")", x + 2, y - 12);
      this.addLabel("§cBaneados §7(clic para seleccionar)", rightX + 2, y - 12);
      this.addLabel("§7Categorias §8(seleccionan, luego pulsa una accion)", x + 2, r1 - 11);
      this.addLabel("§eSolo receta: §f" + recipeCount + "  §cItem completo: §f" + itemCount, rightX + 2, r1 + 4);

      // Restaurar posicion de scroll y resaltado de la seleccion.
      if (this.catalogList != null) {
         this.catalogList.setScroll(this.catalogScroll);
         if (this.selectedItem != null) {
            this.catalogList.setSelected(ForgeRegistries.ITEMS.getValue(this.selectedItem));
         }
      }

      if (this.inventoryList != null) {
         this.inventoryList.setScroll(this.catalogScroll);
      }

      this.bannedList.setScroll(this.bannedScroll);
      if (this.selectedItem != null) {
         this.bannedList.setSelected(ForgeRegistries.ITEMS.getValue(this.selectedItem));
      }
   }

   private Button catButton(String label, int x, int y, int w, ResourceKey<CreativeModeTab> key) {
      boolean active = key.equals(this.selectedCategory);
      return Button.builder(Component.literal(active ? "§a> " + label : label), b -> {
            this.selectCategory(key, label);
            this.rebuildWidgets();
         })
         .tooltip(Tooltip.create(Component.literal("Selecciona TODOS los items de esta categoria.\n\n§7Luego pulsa una de las tres acciones de arriba para aplicarsela de golpe.")))
         .bounds(x, y, w, 16)
         .build();
   }

   /** Texto que dice exactamente sobre que van a actuar los botones. */
   private String selectionText() {
      if (this.selectedItem != null) {
         Item item = ForgeRegistries.ITEMS.getValue(this.selectedItem);
         String name = item != null ? RegistryLists.itemName(item) : this.selectedItem.toString();
         BanMode mode = ClientHooks.mode(this.selectedItem);
         String estado = mode == null ? "§7sin baneo" : mode.display();
         return "§fSeleccionado: §b" + name + " §7- ahora: " + estado;
      } else if (this.selectedCategory != null) {
         return "§fSeleccionado: §dcategoria " + this.selectedCategoryName + " §7(" + this.selectedCategorySize + " items)";
      } else {
         return "§8Selecciona un item o una categoria, y pulsa una accion.";
      }
   }

   private void updateActionButtons() {
      boolean any = this.hasSelection();
      this.banRecipeButton.active = any;
      this.banItemButton.active = any;
      this.unbanButton.active = any;
   }

   private int bodyX() {
      return this.leftPos + 8;
   }

   private int bodyY() {
      return this.topPos + 46;
   }

   private int bodyW() {
      return this.panelW - 16;
   }

   private int bodyH() {
      return this.panelH - 46 - 28;
   }

   private void addLabel(String text, int x, int y) {
      this.labels.add(new RecipeBanScreen.Label(text, x, y));
   }

   // ------------------------------------------------------------------ render

   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      this.renderBackground(g);
      g.fill(this.leftPos, this.topPos, this.leftPos + this.panelW, this.topPos + this.panelH, -535160294);
      g.fill(this.leftPos, this.topPos, this.leftPos + this.panelW, this.topPos + 18, -14013910);
      g.fill(this.leftPos, this.topPos + this.panelH - 1, this.leftPos + this.panelW, this.topPos + this.panelH, -12961222);
      g.fill(this.leftPos + 6, this.topPos + 32, this.leftPos + this.panelW - 6, this.topPos + 33, -12961222);

      int recipeCount = 0;
      int itemCount = 0;
      for (BanMode m : ClientHooks.bans().values()) {
         if (m == BanMode.ITEM) {
            itemCount++;
         } else {
            recipeCount++;
         }
      }

      g.drawString(
         this.font,
         "§6\u2726 Fantastic Recipes §7- §f" + this.gameItemTotal + " items §7- §e" + recipeCount + " recetas §7- §c" + itemCount + " items",
         this.leftPos + 8,
         this.topPos + 5,
         16777215,
         false
      );
      g.drawString(
         this.font,
         "§7Baneo de §ereceta§7: no se puede craftear. Baneo de §citem§7: no se puede ni tener.",
         this.leftPos + 8,
         this.topPos + 22,
         14737632,
         false
      );

      this.updateActionButtons();
      super.render(g, mouseX, mouseY, partial);

      for (RecipeBanScreen.Label l : this.labels) {
         g.drawString(this.font, l.text, l.x, l.y, 14737632, false);
      }

      // El texto de seleccion se redibuja aparte: cambia sin reconstruir la pantalla.
      g.drawString(this.font, this.selectionText(), this.bodyX() + 2, this.bodyY() + this.bodyH() - 96, 16777215, false);
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static record Label(String text, int x, int y) {
   }
}
