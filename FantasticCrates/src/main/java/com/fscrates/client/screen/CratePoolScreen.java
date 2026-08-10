package com.fscrates.client.screen;

import com.fscrates.client.widget.ScrollbarDrag;
import com.fscrates.config.CrateConfig;
import com.fscrates.config.Rarity;
import com.fscrates.config.RewardEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * GUI que muestra el pool de recompensas de una crate: las entradas de tipo ITEM
 * con su rareza y, si la crate lo permite, el porcentaje normalizado.
 *
 * Se abre encima de la pantalla de pre-apertura y al cerrarse vuelve a ella sin
 * cortar el video ni la musica.
 */
public class CratePoolScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int BAR_WIDTH = 5;

    private final CrateConfig config;
    private final Screen parent;

    private String query = "";
    private int scroll;

    private final List<Row> rows = new ArrayList<>();
    private final ScrollbarDrag scrollbarDrag = new ScrollbarDrag();

    private EditBox search;
    private int leftPos;
    private int topPos;
    private int panelWidth;
    private int panelHeight;

    private record Row(ItemStack icon, String name, String rarity, int rarityColor, String odds) {
    }

    public CratePoolScreen(CrateConfig config, Screen parent) {
        super(Component.literal("Recompensas"));
        this.config = config == null ? new CrateConfig() : config;
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(this.width - 20, 330);
        this.panelHeight = Math.min(this.height - 20, 220);
        this.leftPos = (this.width - this.panelWidth) / 2;
        this.topPos = (this.height - this.panelHeight) / 2;

        this.search = new EditBox(this.font, this.leftPos + 9, this.topPos + 23, this.panelWidth - 18, 15, Component.empty());
        this.search.setMaxLength(64);
        this.search.setHint(Component.literal("\u00a78Buscar..."));
        this.search.setValue(this.query);
        this.search.setResponder(value -> {
            this.query = value == null ? "" : value;
            this.scroll = 0;
            this.rebuildRows();
        });
        this.addRenderableWidget(this.search);

        int closeWidth = Math.max(64, this.font.width("Cerrar") + 24);
        this.addRenderableWidget(
            Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.leftPos + (this.panelWidth - closeWidth) / 2, this.topPos + this.panelHeight - 25, closeWidth, 19)
                .build()
        );

        this.rebuildRows();
    }

    // ------------------------------------------------------------------- datos

    private void rebuildRows() {
        this.rows.clear();
        String needle = this.query.toLowerCase(Locale.ROOT).trim();

        for (RewardEntry entry : this.config.rewards) {
            if (entry.type != RewardEntry.Type.ITEM) {
                continue;
            }

            String label = entry.label == null || entry.label.isBlank()
                ? (entry.item == null || entry.item.isEmpty() ? "(item vac\u00edo)" : entry.item.getHoverName().getString())
                : entry.label;
            if (!needle.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }

            Rarity rarity = entry.effectiveRarity(this.config.rarity);
            // El porcentaje SOLO si la crate lo tiene activado.
            String odds = this.config.showOdds
                ? String.format(Locale.ROOT, "%.2f%%", this.config.normalizedPercentInPool(entry))
                : "";
            this.rows.add(new Row(entry.item, label, rarity.displayName(), rarity.rgb(), odds));
        }

        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll));
    }

    private int listTop() {
        return this.topPos + 43;
    }

    private int listHeight() {
        return this.panelHeight - 43 - 30;
    }

    private int visibleRows() {
        return Math.max(1, this.listHeight() / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, this.rows.size() - this.visibleRows());
    }

    private int barX() {
        return this.leftPos + this.panelWidth - 9 - BAR_WIDTH;
    }

    private int thumbHeight() {
        return ScrollbarDrag.thumbHeight(this.listHeight(), this.visibleRows(), this.rows.size());
    }

    private int thumbTop() {
        return ScrollbarDrag.thumbTop(this.listTop(), this.listHeight(), this.thumbHeight(), this.scroll, this.maxScroll());
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, -1610612736);
        FSGui.panel(g, this.leftPos, this.topPos, this.panelWidth, this.panelHeight);

        String title = "\u00a7d\u2726 \u00a7fRecompensas \u00a78(" + this.rows.size() + ")";
        g.drawString(this.font, title, this.leftPos + 9, this.topPos + 9, 16777215, false);

        this.renderRows(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics g, int mouseX, int mouseY) {
        int listTop = this.listTop();
        int listBottom = listTop + this.listHeight();
        boolean scrollable = this.maxScroll() > 0;
        int rowsLeft = this.leftPos + 9;
        int rowsWide = this.panelWidth - 18 - (scrollable ? BAR_WIDTH + 3 : 0);

        FSGui.inset(g, rowsLeft - 1, listTop - 1, rowsWide + 2, this.listHeight() + 2);

        if (this.rows.isEmpty()) {
            g.drawCenteredString(
                this.font,
                "\u00a78(sin items en el pool)",
                this.leftPos + this.panelWidth / 2,
                listTop + this.listHeight() / 2 - 4,
                11184810
            );
            return;
        }

        g.enableScissor(rowsLeft, listTop, rowsLeft + rowsWide, listBottom);

        int visible = this.visibleRows();
        for (int i = 0; i < visible; i++) {
            int index = this.scroll + i;
            if (index < 0 || index >= this.rows.size()) {
                break;
            }

            Row row = this.rows.get(index);
            int rowY = listTop + i * ROW_HEIGHT;
            int textY = rowY + (ROW_HEIGHT - 8) / 2;

            boolean hovered = mouseX >= rowsLeft && mouseX < rowsLeft + rowsWide && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) {
                g.fill(rowsLeft, rowY, rowsLeft + rowsWide, rowY + ROW_HEIGHT, 419430400);
            } else if ((index & 1) == 1) {
                g.fill(rowsLeft, rowY, rowsLeft + rowsWide, rowY + ROW_HEIGHT, 234881023);
            }

            if (row.icon() != null && !row.icon().isEmpty()) {
                g.renderItem(row.icon(), rowsLeft + 2, rowY + (ROW_HEIGHT - 16) / 2);
            }

            // Columna derecha: rareza y, si toca, el porcentaje.
            int right = rowsLeft + rowsWide - 3;
            if (!row.odds().isEmpty()) {
                int oddsWidth = this.font.width(row.odds());
                g.drawString(this.font, row.odds(), right - oddsWidth, textY, 11184810, false);
                right -= oddsWidth + 6;
            }
            int rarityWidth = this.font.width(row.rarity());
            g.drawString(this.font, row.rarity(), right - rarityWidth, textY, row.rarityColor(), false);
            right -= rarityWidth + 6;

            int nameX = rowsLeft + 22;
            String name = this.font.plainSubstrByWidth(row.name(), Math.max(12, right - nameX));
            g.drawString(this.font, name, nameX, textY, 14737632, false);
        }

        g.disableScissor();

        if (scrollable) {
            boolean onThumb = this.scrollbarDrag.isDragging()
                || ScrollbarDrag.overThumb(mouseX, mouseY, this.barX(), BAR_WIDTH, this.thumbTop(), this.thumbHeight());
            FSGui.scrollbar(g, this.barX(), listTop, BAR_WIDTH, this.listHeight(), this.thumbTop(), this.thumbHeight(), onThumb);
        }
    }

    // ------------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Arrastre del scrollbar: vale click izquierdo y derecho.
        if (ScrollbarDrag.isDragButton(button) && this.maxScroll() > 0) {
            if (ScrollbarDrag.overTrack(mouseX, mouseY, this.barX(), BAR_WIDTH, this.listTop(), this.listHeight())) {
                this.scroll = this.scrollbarDrag.beginOnTrack(
                    mouseY, this.scroll, this.listTop(), this.listHeight(), this.thumbHeight(), this.maxScroll()
                );
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrollbarDrag.isDragging()) {
            this.scroll = this.scrollbarDrag.drag(mouseY, this.listHeight(), this.thumbHeight(), this.maxScroll());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.scrollbarDrag.isDragging()) {
            this.scrollbarDrag.end();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.maxScroll() > 0) {
            this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
