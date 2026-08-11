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
    /** Ancho de la barra de scroll. */
    private static final int BAR_WIDTH = 5;
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
    /** Posicion recordada por la pantalla, para sobrevivir a rebuildWidgets(). */
    private ScrollMemory memory;

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

    /**
     * Ata la lista a una posicion recordada por la pantalla.
     *
     * Sin esto la lista vuelve arriba cada vez que la pantalla se reconstruye,
     * que es en cuanto eliges cualquier cosa.
     */
    public ScrollSelector<T> remember(ScrollMemory memory) {
        this.memory = memory;
        return this;
    }

    public void setItems(List<T> items) {
        this.all.clear();
        this.all.addAll(items);
        // Cambiar los items no es motivo para volver arriba: normalmente es la
        // misma lista que se vuelve a poblar tras reconstruir la pantalla.
        this.applyFilter(true);
    }

    public void setQuery(String q) {
        this.query = q == null ? "" : q.toLowerCase(Locale.ROOT).trim();
        // Buscar SI vuelve arriba: los resultados son otros y la posicion
        // anterior no significa nada.
        this.applyFilter(false);
    }

    private void applyFilter(boolean keepPosition) {
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

        if (!keepPosition) {
            this.selectedIndex = -1;
            if (this.memory != null) {
                this.memory.selected = null;
            }
            this.setScroll(0);
            return;
        }

        // Se recupera la fila elegida por identidad, no por indice: entre
        // reconstrucciones la lista puede haberse reordenado.
        this.selectedIndex = -1;
        Object wanted = this.memory == null ? null : this.memory.selected;
        if (wanted != null) {
            for (int i = 0; i < this.filtered.size(); i++) {
                if (this.filtered.get(i) == wanted) {
                    this.selectedIndex = i;
                    break;
                }
            }
        }
        // Si la lista ha encogido (borraste algo) el clamp de setScroll evita
        // quedarse en un hueco vacio por debajo del final.
        this.setScroll(this.memory == null ? this.scroll : this.memory.scroll);
    }

    /** Unico sitio donde se mueve el scroll: acota y deja constancia en la memoria. */
    private void setScroll(int value) {
        this.scroll = Math.max(0, Math.min(this.maxScroll(), value));
        if (this.memory != null) {
            this.memory.scroll = this.scroll;
        }
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
            boolean onThumb = this.scrollbarDrag.isDragging()
                || ScrollbarDrag.overTrack(mouseX, mouseY, barX, BAR_WIDTH, this.getY(), this.height);
            com.fscrates.client.screen.FSGui.scrollbar(
                g, barX, this.getY(), BAR_WIDTH, this.height, this.thumbTop(), this.thumbHeight(), onThumb
            );
        }
    }

    private int barX() {
        return this.getX() + this.width - BAR_WIDTH - 1;
    }

    private int thumbHeight() {
        return ScrollbarDrag.thumbHeight(this.height, this.visibleRows(), this.filtered.size());
    }

    private int thumbTop() {
        return ScrollbarDrag.thumbTop(this.getY(), this.height, this.thumbHeight(), this.scroll, this.maxScroll());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // La barra de scroll tiene prioridad sobre la seleccion de fila: si no,
        // hacer click en la barra seleccionaria el item que hay detras.
        // Sirven click izquierdo y derecho, y se puede clicar en cualquier punto
        // del carril para saltar ahi.
        if (ScrollbarDrag.isDragButton(button)
            && this.maxScroll() > 0
            && ScrollbarDrag.overTrack(mouseX, mouseY, this.barX(), BAR_WIDTH, this.getY(), this.height)) {
            this.setScroll(this.scrollbarDrag.beginOnTrack(
                mouseY, this.scroll, this.getY(), this.height, this.thumbHeight(), this.maxScroll()
            ));
            return true;
        }

        if (this.isMouseOver(mouseX, mouseY) && button == 0) {
            int row = (int)((mouseY - (double)this.getY()) / (double)this.rowHeight);
            int index = this.scroll + row;
            if (index >= 0 && index < this.filtered.size() && mouseX < (double)(this.getX() + this.width - 6)) {
                this.selectedIndex = index;
                // Se apunta la eleccion ANTES de avisar, porque quien escucha
                // suele reconstruir la pantalla y leer esta memoria al vuelo.
                if (this.memory != null) {
                    this.memory.selected = this.filtered.get(index);
                }
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
        if (this.scrollbarDrag.isDragging()) {
            this.setScroll(this.scrollbarDrag.drag(mouseY, this.height, this.thumbHeight(), this.maxScroll()));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.scrollbarDrag.isDragging()) {
            this.scrollbarDrag.end();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        } else {
            this.setScroll(this.scroll - (int)Math.signum(delta));
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
