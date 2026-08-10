package com.fscrates.client.widget;

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
    /** Ancho del thumb de la barra de scroll. */
    private static final int BAR_WIDTH = 4;
    private final List<T> all = new ArrayList<>();
    private final List<T> filtered = new ArrayList<>();
    private final Function<T, String> displayName;
    private final Function<T, String> filterText;
    private final Function<T, ItemStack> icon;
    private Consumer<T> onSelect = t -> {
    };
    private final int rowHeight;
    private int scroll = 0;
    private int selectedIndex = -1;
    private String query = "";
    /** Arrastre del scrollbar con click derecho. */
    private final ScrollbarDrag scrollbarDrag = new ScrollbarDrag();

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
        this.filtered.clear();
        if (this.query.isEmpty()) {
            this.filtered.addAll(this.all);
        } else {
            for (T t : this.all) {
                if (this.filterText.apply(t).toLowerCase(Locale.ROOT).contains(this.query)) {
                    this.filtered.add(t);
                }
            }
        }

        this.scroll = 0;
        this.selectedIndex = -1;
    }

    public T getSelected() {
        return this.selectedIndex >= 0 && this.selectedIndex < this.filtered.size() ? this.filtered.get(this.selectedIndex) : null;
    }

    private int visibleRows() {
        return Math.max(1, this.height / this.rowHeight);
    }

    private int maxScroll() {
        return Math.max(0, this.filtered.size() - this.visibleRows());
    }

    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, -1072689128);
        g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, -12961206);
        g.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, -12961206);
        Font font = Minecraft.getInstance().font;
        int rows = this.visibleRows();

        int index;
        for (int i = 0; i < rows && (index = this.scroll + i) >= 0 && index < this.filtered.size(); i++) {
            T entry = this.filtered.get(index);
            int rowY = this.getY() + i * this.rowHeight;
            boolean hovered = mouseX >= this.getX() && mouseX < this.getX() + this.width - 6 && mouseY >= rowY && mouseY < rowY + this.rowHeight;
            if (index == this.selectedIndex) {
                g.fill(this.getX(), rowY, this.getX() + this.width - 6, rowY + this.rowHeight, -13800225);
            } else if (hovered) {
                g.fill(this.getX(), rowY, this.getX() + this.width - 6, rowY + this.rowHeight, 1090519039);
            }

            int textX = this.getX() + 3;
            if (this.icon != null) {
                ItemStack stack = this.icon.apply(entry);
                if (stack != null && !stack.isEmpty()) {
                    g.renderItem(stack, this.getX() + 1, rowY + (this.rowHeight - 16) / 2);
                }

                textX = this.getX() + 20;
            }

            String name = this.displayName.apply(entry);
            String trimmed = font.plainSubstrByWidth(name, this.width - (textX - this.getX()) - 8);
            g.drawString(font, trimmed, textX, rowY + (this.rowHeight - 8) / 2, 14737632, false);
        }

        if (this.maxScroll() > 0) {
            int barX = this.barX();
            g.fill(barX, this.getY(), barX + BAR_WIDTH, this.getY() + this.height, 1610612736);
            int thumbH = this.thumbHeight();
            int thumbY = this.thumbTop();
            boolean onThumb = this.scrollbarDrag.isDragging()
                || ScrollbarDrag.overThumb(mouseX, mouseY, barX, BAR_WIDTH, thumbY, thumbH);
            g.fill(barX, thumbY, barX + BAR_WIDTH, thumbY + thumbH, onThumb ? -4144960 : -8355680);
        }
    }

    private int barX() {
        return this.getX() + this.width - 5;
    }

    private int thumbHeight() {
        return ScrollbarDrag.thumbHeight(this.height, this.visibleRows(), this.filtered.size());
    }

    private int thumbTop() {
        return ScrollbarDrag.thumbTop(this.getY(), this.height, this.thumbHeight(), this.scroll, this.maxScroll());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Click derecho sobre el thumb: empieza el arrastre de la barra.
        if (button == 1 && this.maxScroll() > 0 && this.isMouseOver(mouseX, mouseY)) {
            if (ScrollbarDrag.overThumb(mouseX, mouseY, this.barX(), BAR_WIDTH, this.thumbTop(), this.thumbHeight())) {
                this.scrollbarDrag.begin(mouseY, this.scroll);
                return true;
            }
        }

        if (this.isMouseOver(mouseX, mouseY) && button == 0) {
            int row = (int)((mouseY - (double)this.getY()) / (double)this.rowHeight);
            int index = this.scroll + row;
            if (index >= 0 && index < this.filtered.size() && mouseX < (double)(this.getX() + this.width - 6)) {
                this.selectedIndex = index;
                this.onSelect.accept(this.filtered.get(index));
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 1 && this.scrollbarDrag.isDragging()) {
            this.scroll = this.scrollbarDrag.drag(mouseY, this.height, this.thumbHeight(), this.maxScroll());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1 && this.scrollbarDrag.isDragging()) {
            this.scrollbarDrag.end();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
