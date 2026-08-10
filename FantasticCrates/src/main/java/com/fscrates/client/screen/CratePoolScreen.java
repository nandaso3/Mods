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
 * GUI del pool de recompensas de una crate.
 *
 * Tiene dos modos que se alternan con el boton de la esquina:
 *  - POOL:      recompensas de tipo ITEM de la crate, con su rareza y (si la
 *               crate lo permite) el porcentaje normalizado.
 *  - INVENTARIO: los items que el jugador lleva encima, con su cantidad.
 *
 * Se abre encima de la pantalla de pre-apertura y al cerrarse vuelve a ella.
 */
public class CratePoolScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int BAR_WIDTH = 4;

    private final CrateConfig config;
    private final Screen parent;

    private boolean inventoryMode;
    private String query = "";
    private int scroll;

    private final List<Row> rows = new ArrayList<>();
    private final ScrollbarDrag scrollbarDrag = new ScrollbarDrag();

    private EditBox search;
    private int leftPos;
    private int topPos;
    private int panelWidth;
    private int panelHeight;

    /** Una fila ya preparada para dibujar. */
    private record Row(ItemStack icon, String name, String rightText, int rightColor) {
    }

    public CratePoolScreen(CrateConfig config, Screen parent) {
        super(Component.literal("Pool de recompensas"));
        this.config = config == null ? new CrateConfig() : config;
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(this.width - 20, 340);
        this.panelHeight = Math.min(this.height - 20, 220);
        this.leftPos = (this.width - this.panelWidth) / 2;
        this.topPos = (this.height - this.panelHeight) / 2;

        int toggleW = 92;
        int searchW = this.panelWidth - 16 - toggleW - 4;

        this.search = new EditBox(this.font, this.leftPos + 8, this.topPos + 22, searchW, 16, Component.empty());
        this.search.setMaxLength(64);
        this.search.setHint(Component.literal("\u00a77Buscar..."));
        this.search.setValue(this.query);
        this.search.setResponder(value -> {
            this.query = value == null ? "" : value;
            this.scroll = 0;
            this.rebuildRows();
        });
        this.addRenderableWidget(this.search);

        this.addRenderableWidget(
            Button.builder(
                    Component.literal(this.inventoryMode ? "\u00a7e\u2756 Inventario" : "\u00a7b\u2756 Pool"),
                    b -> {
                        this.inventoryMode = !this.inventoryMode;
                        this.scroll = 0;
                        this.rebuildWidgets();
                    }
                )
                .bounds(this.leftPos + this.panelWidth - 8 - toggleW, this.topPos + 22, toggleW, 16)
                .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.leftPos + this.panelWidth - 8 - 70, this.topPos + this.panelHeight - 24, 70, 18)
                .build()
        );

        this.rebuildRows();
    }

    // ------------------------------------------------------------------- datos

    private void rebuildRows() {
        this.rows.clear();
        String needle = this.query.toLowerCase(Locale.ROOT).trim();

        if (this.inventoryMode) {
            if (this.minecraft != null && this.minecraft.player != null) {
                for (ItemStack stack : this.minecraft.player.getInventory().items) {
                    if (stack == null || stack.isEmpty()) {
                        continue;
                    }
                    String name = stack.getHoverName().getString();
                    if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) {
                        continue;
                    }
                    this.rows.add(new Row(stack, name, "x" + stack.getCount(), 11184810));
                }
            }
        } else {
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
                String right = rarity.displayName();
                // El porcentaje SOLO se muestra si la crate lo tiene activado.
                if (this.config.showOdds) {
                    right = right + " \u00a77" + String.format(Locale.ROOT, "%.2f", this.config.normalizedPercentInPool(entry)) + "%";
                }
                this.rows.add(new Row(entry.item, label, right, rarity.rgb()));
            }
        }

        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll));
    }

    private int listTop() {
        return this.topPos + 44;
    }

    private int listHeight() {
        return this.panelHeight - 44 - 28;
    }

    private int visibleRows() {
        return Math.max(1, this.listHeight() / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, this.rows.size() - this.visibleRows());
    }

    private int barX() {
        return this.leftPos + this.panelWidth - 8 - BAR_WIDTH;
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
        // Fondo translucido para que se siga viendo la pantalla de detras.
        g.fill(0, 0, this.width, this.height, -1610612736);

        g.fill(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + this.panelHeight, -14671840);
        g.fill(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + 1, -9408400);
        g.fill(this.leftPos, this.topPos + this.panelHeight - 1, this.leftPos + this.panelWidth, this.topPos + this.panelHeight, -9408400);
        g.fill(this.leftPos, this.topPos, this.leftPos + 1, this.topPos + this.panelHeight, -9408400);
        g.fill(this.leftPos + this.panelWidth - 1, this.topPos, this.leftPos + this.panelWidth, this.topPos + this.panelHeight, -9408400);

        String title = this.inventoryMode
            ? "\u00a7e\u2756 Tu inventario"
            : "\u00a7d\u2726 Pool de recompensas \u00a77(" + this.rows.size() + ")";
        g.drawString(this.font, title, this.leftPos + 8, this.topPos + 8, 16777215, false);

        this.renderRows(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics g, int mouseX, int mouseY) {
        int listTop = this.listTop();
        int listBottom = listTop + this.listHeight();
        int rowsWide = this.panelWidth - 16 - (this.maxScroll() > 0 ? BAR_WIDTH + 2 : 0);

        if (this.rows.isEmpty()) {
            String empty = this.inventoryMode ? "\u00a78(inventario vac\u00edo)" : "\u00a78(sin items en el pool)";
            g.drawCenteredString(this.font, empty, this.leftPos + this.panelWidth / 2, listTop + 8, 11184810);
            return;
        }

        g.enableScissor(this.leftPos + 8, listTop, this.leftPos + 8 + rowsWide, listBottom);

        int visible = this.visibleRows();
        for (int i = 0; i < visible; i++) {
            int index = this.scroll + i;
            if (index < 0 || index >= this.rows.size()) {
                break;
            }

            Row row = this.rows.get(index);
            int rowY = listTop + i * ROW_HEIGHT;
            boolean hovered = mouseX >= this.leftPos + 8
                && mouseX < this.leftPos + 8 + rowsWide
                && mouseY >= rowY
                && mouseY < rowY + ROW_HEIGHT;
            if (hovered) {
                g.fill(this.leftPos + 8, rowY, this.leftPos + 8 + rowsWide, rowY + ROW_HEIGHT, 1090519039);
            }

            if (row.icon() != null && !row.icon().isEmpty()) {
                g.renderItem(row.icon(), this.leftPos + 10, rowY + (ROW_HEIGHT - 16) / 2);
            }

            int rightWidth = this.font.width(row.rightText());
            int nameX = this.leftPos + 30;
            int nameMax = rowsWide - 30 + 8 - rightWidth - 8;
            String name = this.font.plainSubstrByWidth(row.name(), Math.max(10, nameMax));
            g.drawString(this.font, name, nameX, rowY + (ROW_HEIGHT - 8) / 2, 14737632, false);
            g.drawString(
                this.font,
                row.rightText(),
                this.leftPos + 8 + rowsWide - rightWidth - 2,
                rowY + (ROW_HEIGHT - 8) / 2,
                row.rightColor(),
                false
            );
        }

        g.disableScissor();

        if (this.maxScroll() > 0) {
            int barX = this.barX();
            g.fill(barX, listTop, barX + BAR_WIDTH, listBottom, 1610612736);
            int thumbH = this.thumbHeight();
            int thumbY = this.thumbTop();
            boolean onThumb = this.scrollbarDrag.isDragging()
                || ScrollbarDrag.overThumb(mouseX, mouseY, barX, BAR_WIDTH, thumbY, thumbH);
            g.fill(barX, thumbY, barX + BAR_WIDTH, thumbY + thumbH, onThumb ? -4144960 : -8355680);
        }
    }

    // ------------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && this.maxScroll() > 0
            && ScrollbarDrag.overThumb(mouseX, mouseY, this.barX(), BAR_WIDTH, this.thumbTop(), this.thumbHeight())) {
            this.scrollbarDrag.begin(mouseY, this.scroll);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 1 && this.scrollbarDrag.isDragging()) {
            this.scroll = this.scrollbarDrag.drag(mouseY, this.listHeight(), this.thumbHeight(), this.maxScroll());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1 && this.scrollbarDrag.isDragging()) {
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
        // Vuelve a la pantalla anterior (la pre-apertura sigue reproduciendo).
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
