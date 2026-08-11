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

public final class RecipeBanScreen extends Screen {
   private static final String MODE_TOOLTIP = "Modo que se aplica al hacer clic en un item.\n\n"
      + "§eSolo receta§r: el item deja de poder craftearse, pero se puede seguir teniendo y usando.\n\n"
      + "§cItem completo§r: quita la receta Y prohibe el item (no se puede tener, usar, recoger ni sacar de creativo).\n\n"
      + "Clic en un item que ya tiene el modo activo = desbanearlo. Todo es reversible.";

   /** Filtros de la columna de baneados. */
   private static final int VIEW_ALL = 0;
   private static final int VIEW_RECIPE = 1;
   private static final int VIEW_ITEM = 2;

   private boolean fromInventory = false;
   /** Modo que se aplica al hacer clic. Se conserva entre reconstrucciones de la pantalla. */
   private BanMode activeMode = BanMode.RECIPE;
   private int bannedView = VIEW_ALL;

   private final List<RecipeBanScreen.Label> labels = new ArrayList<>();
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

   // ------------------------------------------------------------------ estado

   private BanMode modeOf(Item item) {
      return ClientHooks.mode(RegistryLists.id(item));
   }

   private String tagOf(Item item) {
      return BanMode.tagOf(this.modeOf(item));
   }

   /** Aplica un modo (o {@code null} para desbanear) a un item. */
   private void apply(Item item, BanMode mode) {
      ResourceLocation id = RegistryLists.id(item);
      if (id != null) {
         ClientHooks.setLocal(id, mode);
         Net.CHANNEL.sendToServer(new ToggleBanPacket(id, mode));
      }
   }

   /** Clic en una fila del catalogo: alterna entre el modo activo y "sin baneo". */
   private void toggleWithActiveMode(Item item) {
      BanMode current = this.modeOf(item);
      BanMode target = current == this.activeMode ? null : this.activeMode;
      this.apply(item, target);
      if (target == null) {
         Sfx.click();
      } else {
         Sfx.success();
      }

      this.rebuildWidgets();
   }

   // ------------------------------------------------------------------ layout

   protected void init() {
      this.panelW = Math.min(this.width - 16, 480);
      this.panelH = Math.min(this.height - 16, 316);
      this.leftPos = (this.width - this.panelW) / 2;
      this.topPos = (this.height - this.panelH) / 2;
      this.labels.clear();
      this.gameItemTotal = RegistryLists.items().size();

      int x = this.bodyX();
      int y = this.bodyY();
      int colW = (this.bodyW() - 8) / 2;
      int rightX = x + colW + 8;
      int halfW = (colW - 2) / 2;

      // --- fila superior izquierda: fuente + modo de baneo ---
      this.addRenderableWidget(
         Button.builder(Component.literal(this.fromInventory ? "Fuente: §bInv." : "Fuente: §eReg."), b -> {
               this.fromInventory = !this.fromInventory;
               Sfx.click();
               this.rebuildWidgets();
            })
            .tooltip(
               Tooltip.create(
                  Component.literal("Registro = todos los items del juego (todos los mods).\nInventario = los items que llevas encima.")
               )
            )
            .bounds(x, y, halfW, 16)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("Modo: " + this.activeMode.display()), b -> {
               this.activeMode = this.activeMode == BanMode.RECIPE ? BanMode.ITEM : BanMode.RECIPE;
               Sfx.click();
               this.rebuildWidgets();
            })
            .tooltip(Tooltip.create(Component.literal(MODE_TOOLTIP)))
            .bounds(x + halfW + 2, y, colW - halfW - 2, 16)
            .build()
      );

      EditBox search = new EditBox(this.font, x, y + 18, colW, 16, Component.empty());
      search.setHint(Component.literal("Buscar item..."));
      this.addRenderableWidget(search);

      int listY = y + 38;
      // 4 filas de botones de 18px + una fila de texto: la lista acaba en bodyH-88.
      int bottomRows = 86;
      int listH = this.bodyH() - 38 - bottomRows - 2;

      // --- catalogo ---
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
            .onSelect(stx -> this.toggleWithActiveMode(stx.getItem()));
         list.setItems(inv);
         this.catalogCount = inv.size();
         search.setResponder(list::setQuery);
         this.addRenderableWidget(list);
         if (inv.isEmpty()) {
            this.addLabel("§7Tu inventario esta vacio.", x + 2, listY + 4);
         }
      } else {
         ScrollSelector<Item> list = new ScrollSelector<>(
               x, listY, colW, listH, 18, RegistryLists::itemName, it -> RegistryLists.itemName(it) + " " + RegistryLists.itemId(it), ItemStack::new
            )
            .withTag(this::tagOf)
            .onSelect(this::toggleWithActiveMode);
         List<Item> allItems = RegistryLists.items();
         list.setItems(allItems);
         this.catalogCount = allItems.size();
         search.setResponder(list::setQuery);
         this.addRenderableWidget(list);
      }

      int bw = colW / 3 - 2;
      int r1 = y + this.bodyH() - 72;
      int r2 = y + this.bodyH() - 54;
      int r3 = y + this.bodyH() - 36;
      int r4 = y + this.bodyH() - 18;

      // --- categorias (aplican el modo activo de golpe) ---
      this.addRenderableWidget(this.catButton("Bloques", x, r1, bw, CreativeModeTabs.BUILDING_BLOCKS));
      this.addRenderableWidget(this.catButton("Naturales", x + bw + 2, r1, bw, CreativeModeTabs.NATURAL_BLOCKS));
      this.addRenderableWidget(this.catButton("Funcional", x + 2 * (bw + 2), r1, bw, CreativeModeTabs.FUNCTIONAL_BLOCKS));
      this.addRenderableWidget(this.catButton("Combate", x, r2, bw, CreativeModeTabs.COMBAT));
      this.addRenderableWidget(this.catButton("Herram.", x + bw + 2, r2, bw, CreativeModeTabs.TOOLS_AND_UTILITIES));
      this.addRenderableWidget(this.catButton("Redstone", x + 2 * (bw + 2), r2, bw, CreativeModeTabs.REDSTONE_BLOCKS));
      this.addRenderableWidget(this.catButton("Comida", x, r3, bw, CreativeModeTabs.FOOD_AND_DRINKS));
      this.addRenderableWidget(this.catButton("Ingred.", x + bw + 2, r3, bw, CreativeModeTabs.INGREDIENTS));
      this.addRenderableWidget(this.catButton("Deco", x + 2 * (bw + 2), r3, bw, CreativeModeTabs.COLORED_BLOCKS));

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
         case VIEW_RECIPE -> "Ver: §eSolo receta";
         case VIEW_ITEM -> "Ver: §cItem completo";
         default -> "Ver: §fTodos";
      };
      this.addRenderableWidget(
         Button.builder(Component.literal(viewLabel), b -> {
               this.bannedView = (this.bannedView + 1) % 3;
               Sfx.click();
               this.rebuildWidgets();
            })
            .tooltip(Tooltip.create(Component.literal("Filtra la lista de baneados por tipo de baneo.")))
            .bounds(rightX, y, colW, 16)
            .build()
      );

      EditBox bannedSearch = new EditBox(this.font, rightX, y + 18, colW, 16, Component.empty());
      bannedSearch.setHint(Component.literal("Buscar baneado..."));
      this.addRenderableWidget(bannedSearch);

      ScrollSelector<Item> banned = new ScrollSelector<>(
            rightX, listY, colW, listH, 18, RegistryLists::itemName, it -> RegistryLists.itemName(it) + " " + RegistryLists.itemId(it), ItemStack::new
         )
         .withTag(this::tagOf)
         .onSelect(it -> {
            this.apply(it, null);
            Sfx.click();
            this.rebuildWidgets();
         });
      banned.setItems(bannedItems);
      bannedSearch.setResponder(banned::setQuery);
      this.addRenderableWidget(banned);

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
            .bounds(rightX, r4, colW, 16)
            .build()
      );

      this.addRenderableWidget(
         Button.builder(Component.literal("Cerrar"), b -> this.onClose())
            .bounds(this.leftPos + this.panelW - 88, this.topPos + this.panelH - 24, 80, 18)
            .build()
      );

      String fuente = this.fromInventory ? "inventario" : "todos los mods";
      this.addLabel("§eCatalogo §f" + this.catalogCount + " items §7(" + fuente + ")", x + 2, y + this.bodyH() - 86);
      this.addLabel("§7Clic = aplicar/quitar el modo activo", x + 2, r4 + 4);
      this.addLabel("§cBaneados §7(clic = desbanear)", rightX + 2, y - 12);
      this.addLabel("§eSolo receta: §f" + recipeCount + "  §cItem completo: §f" + itemCount, rightX + 2, r1 + 4);
   }

   private Button catButton(String label, int x, int y, int w, ResourceKey<CreativeModeTab> key) {
      BanMode mode = this.activeMode;
      return Button.builder(Component.literal(label), b -> {
            List<ResourceLocation> ids = new ArrayList<>();

            for (Item it : RegistryLists.itemsOfTab(key)) {
               ResourceLocation id = RegistryLists.id(it);
               if (id != null) {
                  ids.add(id);
                  ClientHooks.setLocal(id, mode);
               }
            }

            if (!ids.isEmpty()) {
               Net.CHANNEL.sendToServer(BulkBanPacket.set(ids, mode));
               Sfx.success();
            }

            this.rebuildWidgets();
         })
         .tooltip(
            Tooltip.create(
               Component.literal("Aplica el modo activo (" + mode.display() + "§r) a TODOS los items de esta categoria de golpe.")
            )
         )
         .bounds(x, y, w, 16)
         .build();
   }

   private int bodyX() {
      return this.leftPos + 8;
   }

   private int bodyY() {
      return this.topPos + 40;
   }

   private int bodyW() {
      return this.panelW - 16;
   }

   private int bodyH() {
      return this.panelH - 40 - 28;
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
      g.fill(this.leftPos + 6, this.topPos + 34, this.leftPos + this.panelW - 6, this.topPos + 35, -12961222);

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
      super.render(g, mouseX, mouseY, partial);

      for (RecipeBanScreen.Label l : this.labels) {
         g.drawString(this.font, l.text, l.x, l.y, 14737632, false);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static record Label(String text, int x, int y) {
   }
}
