package com.fsrecipes.client.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ScrollSelector<T> extends AbstractWidget {
   private static final int SCROLLBAR_WIDTH = 6;
   private final List<T> all = new ArrayList<>();
   private final List<T> filtered = new ArrayList<>();
   private final Function<T, String> displayName;
   private final Function<T, String> filterText;
   private final Function<T, ItemStack> icon;
   private Consumer<T> onSelect = t -> {
   };
   /** Etiqueta de estado que se dibuja delante del nombre (p.ej. "§c[ITEM]"). */
   private Function<T, String> tag;
   private int tagWidth = 40;
   private final int rowHeight;
   private int scroll = 0;
   private int selectedIndex = -1;
   private String query = "";
   private boolean draggingThumb = false;
   private int dragOffsetY = 0;

   public ScrollSelector(
      int x, int y, int width, int height, int rowHeight, Function<T, String> displayName, Function<T, String> filterText, Function<T, ItemStack> icon
   ) {
      super(x, y, width, height, Component.empty());
      this.rowHeight = rowHeight;
      this.displayName = displayName;
      this.filterText = filterText;
      this.icon = icon;
   }

   public ScrollSelector<T> onSelect(Consumer<T> cb) {
      this.onSelect = cb;
      return this;
   }

   /** Dibuja una etiqueta de estado (ancho fijo) delante del nombre de cada fila. */
   public ScrollSelector<T> withTag(Function<T, String> tagProvider) {
      this.tag = tagProvider;
      return this;
   }

   public ScrollSelector<T> withTag(Function<T, String> tagProvider, int width) {
      this.tag = tagProvider;
      this.tagWidth = width;
      return this;
   }

   public void setItems(List<T> items) {
      this.all.clear();
      this.all.addAll(items);
      this.applyFilter();
   }

   public void setQuery(String q) {
      this.query = q == null ? "" : q.toLowerCase(Locale.ROOT).trim();
      this.applyFilter();
   }

   private void applyFilter() {
      T previous = this.getSelected();
      this.filtered.clear();
      if (this.query.isEmpty()) {
         this.filtered.addAll(this.all);
      } else {
         for (T t : this.all) {
            String text = this.filterText.apply(t).toLowerCase(Locale.ROOT);
            if (text.contains(this.query)) {
               this.filtered.add(t);
            }
         }
      }

      this.scroll = Math.min(this.scroll, this.maxScroll());
      // La seleccion sigue al valor, no a la posicion: filtrar no debe perderla.
      this.selectedIndex = previous == null ? -1 : this.filtered.indexOf(previous);
   }

   public T getSelected() {
      return this.selectedIndex >= 0 && this.selectedIndex < this.filtered.size() ? this.filtered.get(this.selectedIndex) : null;
   }

   public void setSelected(T value) {
      this.selectedIndex = value == null ? -1 : this.filtered.indexOf(value);
   }

   public int getScroll() {
      return this.scroll;
   }

   /** Restaura la posicion del scroll (al reconstruir la pantalla, para no dar saltos). */
   public void setScroll(int value) {
      this.scroll = Math.max(0, Math.min(this.maxScroll(), value));
   }

   /** Deja visible la fila seleccionada. */
   public void scrollToSelection() {
      if (this.selectedIndex >= 0) {
         int rows = this.visibleRows();
         if (this.selectedIndex < this.scroll) {
            this.scroll = this.selectedIndex;
         } else if (this.selectedIndex >= this.scroll + rows) {
            this.scroll = this.selectedIndex - rows + 1;
         }

         this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll));
      }
   }

   private int visibleRows() {
      return Math.max(1, this.height / this.rowHeight);
   }

   private int maxScroll() {
      return Math.max(0, this.filtered.size() - this.visibleRows());
   }

   private int contentRight() {
      return this.getX() + this.width - 6;
   }

   private boolean overScrollbar(double mouseX, double mouseY) {
      return mouseX >= (double)this.contentRight()
         && mouseX < (double)(this.getX() + this.width)
         && mouseY >= (double)this.getY()
         && mouseY < (double)(this.getY() + this.height);
   }

   private int thumbHeight() {
      return this.filtered.isEmpty() ? this.height : Math.max(12, this.height * this.visibleRows() / Math.max(1, this.filtered.size()));
   }

   private int thumbY() {
      int max = this.maxScroll();
      return max <= 0 ? this.getY() : this.getY() + (this.height - this.thumbHeight()) * this.scroll / max;
   }

   protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, -1072689128);
      g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, -12961206);
      g.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, -12961206);
      Font font = Minecraft.getInstance().font;
      int rows = this.visibleRows();

      for (int i = 0; i < rows; i++) {
         int index = this.scroll + i;
         if (index < 0 || index >= this.filtered.size()) {
            break;
         }

         T entry = this.filtered.get(index);
         int rowY = this.getY() + i * this.rowHeight;
         boolean hovered = mouseX >= this.getX() && mouseX < this.contentRight() && mouseY >= rowY && mouseY < rowY + this.rowHeight;
         if (index == this.selectedIndex) {
            g.fill(this.getX(), rowY, this.contentRight(), rowY + this.rowHeight, -13800225);
         } else if (hovered) {
            g.fill(this.getX(), rowY, this.contentRight(), rowY + this.rowHeight, 1090519039);
         }

         int textX = this.getX() + 3;
         if (this.icon != null) {
            ItemStack stack = this.icon.apply(entry);
            if (stack != null && !stack.isEmpty()) {
               g.renderItem(stack, this.getX() + 1, rowY + (this.rowHeight - 16) / 2);
            }

            textX = this.getX() + 20;
         }

         if (this.tag != null) {
            String label = this.tag.apply(entry);
            if (label != null) {
               g.drawString(font, label, textX, rowY + (this.rowHeight - 8) / 2, 16777215, false);
            }

            textX += this.tagWidth;
         }

         String name = this.displayName.apply(entry);
         String trimmed = font.plainSubstrByWidth(name, this.width - (textX - this.getX()) - 8);
         g.drawString(font, trimmed, textX, rowY + (this.rowHeight - 8) / 2, 14737632, false);
      }

      int sbX = this.contentRight();
      g.fill(sbX, this.getY(), sbX + 6, this.getY() + this.height, 1610612736);
      if (this.maxScroll() > 0) {
         int ty = this.thumbY();
         int th = this.thumbHeight();
         int color = this.draggingThumb ? -3092272 : -8355712;
         g.fill(sbX + 1, ty, sbX + 6 - 1, ty + th, color);
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isMouseOver(mouseX, mouseY) || button != 0) {
         return false;
      } else if (this.overScrollbar(mouseX, mouseY) && this.maxScroll() > 0) {
         int ty = this.thumbY();
         int th = this.thumbHeight();
         if (mouseY >= (double)ty && mouseY < (double)(ty + th)) {
            this.draggingThumb = true;
            this.dragOffsetY = (int)(mouseY - (double)ty);
         } else {
            this.scroll = mouseY < (double)ty ? Math.max(0, this.scroll - this.visibleRows()) : Math.min(this.maxScroll(), this.scroll + this.visibleRows());
         }

         return true;
      } else {
         int row = (int)((mouseY - (double)this.getY()) / (double)this.rowHeight);
         int index = this.scroll + row;
         if (index >= 0 && index < this.filtered.size() && mouseX < (double)this.contentRight()) {
            this.selectedIndex = index;
            this.onSelect.accept(this.filtered.get(index));
            return true;
         } else {
            return false;
         }
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
      if (this.draggingThumb && this.maxScroll() > 0) {
         int trackTop = this.getY();
         int trackHeight = this.height - this.thumbHeight();
         if (trackHeight > 0) {
            int newThumbY = (int)Math.max((double)trackTop, Math.min((double)(trackTop + trackHeight), mouseY - (double)this.dragOffsetY));
            this.scroll = (newThumbY - trackTop) * this.maxScroll() / trackHeight;
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0 && this.draggingThumb) {
         this.draggingThumb = false;
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (!this.isMouseOver(mouseX, mouseY)) {
         return false;
      } else {
         this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int)Math.signum(delta)));
         return true;
      }
   }

   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX >= (double)this.getX()
         && mouseX < (double)(this.getX() + this.width)
         && mouseY >= (double)this.getY()
         && mouseY < (double)(this.getY() + this.height);
   }

   protected void updateWidgetNarration(NarrationElementOutput out) {
   }
}
