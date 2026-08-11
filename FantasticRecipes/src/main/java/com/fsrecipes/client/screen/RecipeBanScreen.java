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
 * <p>Funciona en dos pasos a proposito: primero seleccionas (un item de cualquiera de
 * las dos listas, o una categoria entera) y luego pulsas la accion. Un clic en una
 * lista no cambia nada por si solo.
 *
 * <p>El alto se reparte de abajo arriba: el bloque de botones tiene una altura fija
 * conocida y la lista se queda con TODO lo que sobra. Asi la pantalla sigue siendo
 * usable con la GUI a escala grande, donde hay poco alto disponible.
 */
public final class RecipeBanScreen extends Screen {
   /** Alto reservado bajo las listas: texto de seleccion + accion + 3 filas. */
   private static final int BOTTOM_BLOCK = 87;
   /** Alto de la cabecera: barra de titulo + ayuda + separador. */
   private static final int HEADER = 36;
   /** Solo margen: el boton de cerrar esta en la barra de titulo, no abajo. */
   private static final int FOOTER = 8;
   private static final int ROW = 18;

   private static final String TIP_RECIPE = "Quita la RECETA del item seleccionado.\n\n"
      + "§7Deja de poder craftearse, cocinarse o forjarse, pero se puede seguir teniendo, "
      + "usando y consiguiendo por loot, comandos o creativo.";
   private static final String TIP_ITEM = "Prohibe el ITEM seleccionado por completo.\n\n"
      + "§7Incluye quitar la receta, y ademas: no se puede tener en el inventario, usar, "
      + "recoger del suelo, tirar ni sacar del creativo. Se borra de todos los inventarios "
      + "donde aparezca, tambien dentro de mochilas, shulkers y cofres.";
   private static final String TIP_UNBAN = "Quita cualquier baneo de lo seleccionado y lo deja como estaba.";
   private static final String TIP_CATEGORY = "Selecciona TODOS los items de esta categoria.\n\n"
      + "§7Luego pulsa una de las tres acciones para aplicarsela de golpe.";

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

   private int gameItemTotal = 0;
   private int leftPos;
   private int topPos;
   private int panelW;
   private int panelH;
   private int selectionY;

   public RecipeBanScreen() {
      super(Component.literal("Fantastic Recipes"));
   }

   public void onBansUpdated() {
      this.rebuildWidgets();
   }

   /** Guarda scroll y seleccion antes de que se destruyan los widgets. */
   protected void rebuildWidgets() {
      if (this.catalogList != null) {
         this.catalogScroll = this.catalogList.getScroll();
      } else if (this.inventoryList != null) {
         this.catalogScroll = this.inventoryList.getScroll();
      }

      if (this.bannedList != null) {
         this.bannedScroll = this.bannedList.getScroll();
      }

      super.rebuildWidgets();
   }

   // ------------------------------------------------------------------ seleccion

   /**
    * Seleccionar no toca el servidor y no reconstruye la pantalla: el resaltado y el
    * texto de estado son inmediatos.
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

   private String tagOf(Item item) {
      return BanMode.tagOf(ClientHooks.mode(RegistryLists.id(item)));
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

      int x = this.leftPos + 8;
      int y = this.topPos + HEADER;
      int bodyW = this.panelW - 16;
      int bodyH = this.panelH - HEADER - FOOTER;
      int colW = (bodyW - 8) / 2;
      int rightX = x + colW + 8;
      int halfW = (colW - 2) / 2;

      // Reparto vertical: el bloque de abajo manda, la lista se queda con el resto.
      int listY = y + 38;
      int listH = Math.max(2 * ROW, bodyH - 38 - BOTTOM_BLOCK);
      int selY = listY + listH + 3;
      int actionY = selY + 11;
      int r1 = y + bodyH - 53;
      int r2 = y + bodyH - 36;
      int r3 = y + bodyH - 19;
      this.selectionY = selY;

      // --- columna izquierda: catalogo ---
      List<Item> allItems = RegistryLists.items();
      int catalogCount = this.fromInventory ? this.inventoryStacks().size() : allItems.size();
      String sourceLabel = this.fromInventory
         ? "§7Catalogo: §bInventario §8(" + catalogCount + ")"
         : "§7Catalogo: §eRegistro §8(" + catalogCount + ")";

      this.addRenderableWidget(
         Button.builder(Component.literal(sourceLabel), b -> {
               this.fromInventory = !this.fromInventory;
               this.catalogScroll = 0;
               Sfx.click();
               this.rebuildWidgets();
            })
            .tooltip(
               Tooltip.create(
                  Component.literal("Cambia de donde salen los items de la izquierda.\n\n§7Registro = todos los items del juego, de todos los mods.\nInventario = solo los que llevas encima.")
               )
            )
            .bounds(x, y, colW, 16)
            .build()
      );

      EditBox search = new EditBox(this.font, x, y + 18, colW, 16, Component.empty());
      search.setHint(Component.literal("Buscar item..."));

      if (this.fromInventory) {
         List<ItemStack> inv = this.inventoryStacks();
         ScrollSelector<ItemStack> list = new ScrollSelector<ItemStack>(
               x,
               listY,
               colW,
               listH,
               ROW,
               stx -> stx.getHoverName().getString(),
               stx -> stx.getHoverName().getString() + " " + RegistryLists.itemId(stx.getItem()),
               stx -> stx
            )
            .withTag(stx -> this.tagOf(stx.getItem()))
            .onSelect(stx -> this.selectItem(stx.getItem()));
         list.setItems(inv);
         this.inventoryList = list;
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
               x, listY, colW, listH, ROW, RegistryLists::itemName, it -> RegistryLists.itemName(it) + " " + RegistryLists.itemId(it), ItemStack::new
            )
            .withTag(this::tagOf)
            .onSelect(this::selectItem);
         list.setItems(allItems);
         this.catalogList = list;
         search.setResponder(text -> {
            this.catalogQuery = text;
            list.setQuery(text);
         });
         this.addRenderableWidget(list);
      }

      search.setValue(this.catalogQuery);
      this.addRenderableWidget(search);

      // --- columna derecha: baneados ---
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
         case VIEW_RECIPE -> "§7Baneados: §esolo receta §8(" + bannedItems.size() + ")";
         case VIEW_ITEM -> "§7Baneados: §citem completo §8(" + bannedItems.size() + ")";
         default -> "§7Baneados: §ftodos §8(" + bannedItems.size() + ")";
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
            rightX, listY, colW, listH, ROW, RegistryLists::itemName, it -> RegistryLists.itemName(it) + " " + RegistryLists.itemId(it), ItemStack::new
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

      // --- barra de acciones (ancho completo) ---
      int actionW = (bodyW - 8) / 3;
      this.banRecipeButton = Button.builder(Component.literal("§eBanear receta"), b -> this.applyToSelection(BanMode.RECIPE))
         .tooltip(Tooltip.create(Component.literal(TIP_RECIPE)))
         .bounds(x, actionY, actionW, 18)
         .build();
      this.banItemButton = Button.builder(Component.literal("§cBanear item"), b -> this.applyToSelection(BanMode.ITEM))
         .tooltip(Tooltip.create(Component.literal(TIP_ITEM)))
         .bounds(x + actionW + 4, actionY, actionW, 18)
         .build();
      this.unbanButton = Button.builder(Component.literal("§aDesbanear"), b -> this.applyToSelection(null))
         .tooltip(Tooltip.create(Component.literal(TIP_UNBAN)))
         .bounds(x + 2 * (actionW + 4), actionY, bodyW - 2 * (actionW + 4), 18)
         .build();
      this.addRenderableWidget(this.banRecipeButton);
      this.addRenderableWidget(this.banItemButton);
      this.addRenderableWidget(this.unbanButton);
      this.updateActionButtons();

      // --- categorias (seleccionan) ---
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

      // Cerrar va en la barra de titulo: abajo esos 18px se los queda la lista.
      this.addRenderableWidget(
         Button.builder(Component.literal("§cX"), b -> this.onClose())
            .tooltip(Tooltip.create(Component.literal("Cerrar (Esc)")))
            .bounds(this.leftPos + this.panelW - 18, this.topPos + 2, 14, 14)
            .build()
      );

      this.addLabel("§8Limpiezas masivas", rightX + 2, r1 + 4);

      // Restaurar posicion de scroll y resaltado de la seleccion.
      Item selected = this.selectedItem != null ? ForgeRegistries.ITEMS.getValue(this.selectedItem) : null;

      if (this.catalogList != null) {
         this.catalogList.setScroll(this.catalogScroll);
         this.catalogList.setSelected(selected);
      }

      if (this.inventoryList != null) {
         this.inventoryList.setScroll(this.catalogScroll);
      }

      this.bannedList.setScroll(this.bannedScroll);
      this.bannedList.setSelected(selected);
   }

   private List<ItemStack> inventoryStacks() {
      List<ItemStack> inv = new ArrayList<>();
      Player p = this.minecraft != null ? this.minecraft.player : null;
      if (p != null) {
         for (ItemStack st : p.getInventory().items) {
            if (st != null && !st.isEmpty()) {
               inv.add(st.copy());
            }
         }
      }

      return inv;
   }

   private Button catButton(String label, int x, int y, int w, ResourceKey<CreativeModeTab> key) {
      boolean active = key.equals(this.selectedCategory);
      return Button.builder(Component.literal(active ? "§a" + label : label), b -> {
            this.selectCategory(key, label);
            this.rebuildWidgets();
         })
         .tooltip(Tooltip.create(Component.literal(TIP_CATEGORY)))
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

   private void addLabel(String text, int x, int y) {
      this.labels.add(new RecipeBanScreen.Label(text, x, y));
   }

   // ------------------------------------------------------------------ render

   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      this.renderBackground(g);
      g.fill(this.leftPos, this.topPos, this.leftPos + this.panelW, this.topPos + this.panelH, -535160294);
      g.fill(this.leftPos, this.topPos, this.leftPos + this.panelW, this.topPos + 18, -14013910);
      g.fill(this.leftPos, this.topPos + this.panelH - 1, this.leftPos + this.panelW, this.topPos + this.panelH, -12961222);
      g.fill(this.leftPos + 6, this.topPos + HEADER - 4, this.leftPos + this.panelW - 6, this.topPos + HEADER - 3, -12961222);

      int recipeCount = 0;
      int itemCount = 0;
      for (BanMode m : ClientHooks.bans().values()) {
         if (m == BanMode.ITEM) {
            itemCount++;
         } else {
            recipeCount++;
         }
      }

      // Los textos se recortan al ancho disponible: con la GUI a escala grande el panel
      // se queda estrecho y antes se salian por el borde.
      String title = "§6\u2726 Fantastic Recipes §7- §f"
         + this.gameItemTotal
         + " items §7- §e"
         + recipeCount
         + " recetas §7- §c"
         + itemCount
         + " items";
      g.drawString(this.font, this.fit(title, this.panelW - 32), this.leftPos + 8, this.topPos + 5, 16777215, false);

      String help = this.panelW >= 470
         ? "§8Baneo de §7receta§8: no se puede craftear.  Baneo de §7item§8: no se puede ni tener."
         : "§8§7receta§8 = no se craftea · §7item§8 = no se puede tener";
      g.drawString(this.font, this.fit(help, this.panelW - 16), this.leftPos + 8, this.topPos + 21, 14737632, false);

      this.updateActionButtons();
      super.render(g, mouseX, mouseY, partial);

      for (RecipeBanScreen.Label l : this.labels) {
         g.drawString(this.font, l.text, l.x, l.y, 14737632, false);
      }

      // Aparte del resto: cambia al seleccionar, sin reconstruir la pantalla.
      g.drawString(this.font, this.fit(this.selectionText(), this.panelW - 20), this.leftPos + 10, this.selectionY, 16777215, false);
   }

   /** Recorta un texto para que quepa en el ancho dado. */
   private String fit(String text, int maxWidth) {
      return this.font.width(text) <= maxWidth ? text : this.font.plainSubstrByWidth(text, maxWidth);
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static record Label(String text, int x, int y) {
   }
}
