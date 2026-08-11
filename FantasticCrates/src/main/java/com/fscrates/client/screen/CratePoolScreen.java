package com.fscrates.client.screen;

import com.fscrates.client.media.CrateMedia;
import com.fscrates.client.widget.FSButton;
import com.fscrates.client.widget.ScrollbarDrag;
import com.fscrates.config.CrateConfig;
import com.fscrates.config.Rarity;
import com.fscrates.item.CrateItems;
import com.fscrates.config.RewardEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.TooltipFlag;
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

    private int scroll;

    private final List<Row> rows = new ArrayList<>();
    private final ScrollbarDrag scrollbarDrag = new ScrollbarDrag();

    private int leftPos;
    private int topPos;
    private int panelWidth;
    private int panelHeight;

    private record Row(ItemStack icon, String name, String rarity, int rarityColor, String odds) {
    }

    /**
     * Item cuyos detalles se estan mostrando al lado, o vacio si ninguno.
     *
     * Se decide en cada dibujado: se pone al pasar por encima de una fila que tenga
     * algo que contar y se limpia al principio de cada fotograma.
     */
    private ItemStack detailStack = ItemStack.EMPTY;

    /**
     * true si este item tiene algo que merezca la pena mostrar aparte.
     *
     * Las llaves quedan fuera a proposito: llevan NBT propio del mod (a que caja
     * pertenecen, que modelo usan) que no le dice nada al jugador, y sacar una
     * ventana por cada llave de la lista seria justo el estorbo que no se quiere.
     * Las cajas tampoco, por lo mismo.
     */
    private static boolean hasDetails(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (CrateItems.isKey(stack) || CrateItems.isUniqueKey(stack) || CrateItems.isCrate(stack)) {
            return false;
        }
        if (stack.isEnchanted()) {
            return true;
        }

        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return false;
        }
        // Encantamientos guardados (libros), modificadores de atributo, efectos de
        // pocion y lore puesto a mano.
        return tag.contains("StoredEnchantments")
            || tag.contains("AttributeModifiers")
            || tag.contains("CustomPotionEffects")
            || tag.contains("Potion")
            || tag.getCompound("display").contains("Lore");
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

        // Boton del mod y no el de vanilla: el de vanilla es la textura gris de
        // siempre y desentonaba con el resto de la ventana, ademas de quedarse sin
        // el brillo y el sonido de los demas.
        int closeWidth = Math.max(70, this.font.width("Cerrar") + 30);
        this.addRenderableWidget(
            new FSButton(
                this.leftPos + (this.panelWidth - closeWidth) / 2,
                this.topPos + this.panelHeight - 26,
                closeWidth,
                20,
                Component.literal("Cerrar"),
                FSGui.ACCENT_BLUE,
                this::onClose
            )
        );

        this.rebuildRows();
    }

    // ------------------------------------------------------------------- datos

    private void rebuildRows() {
        this.rows.clear();
        // Ordenado de la rareza mas alta a la mas baja, y dentro de cada rareza
        // por nombre: asi lo bueno se ve arriba y la lista no sale a lo loco.
        List<RewardEntry> ordered = new ArrayList<>();
        for (RewardEntry entry : this.config.rewards) {
            if (entry.type == RewardEntry.Type.ITEM) {
                ordered.add(entry);
            }
        }
        ordered.sort(
            Comparator.<RewardEntry>comparingInt(e -> -e.effectiveRarity(this.config.rarity).ordinal())
                .thenComparing(e -> labelOf(e).toLowerCase(Locale.ROOT))
        );

        for (RewardEntry entry : ordered) {

            String label = labelOf(entry);
            Rarity rarity = entry.effectiveRarity(this.config.rarity);
            // El porcentaje SOLO si la crate lo tiene activado.
            String odds = this.config.showOddsInPool
                ? String.format(Locale.ROOT, "%.2f%%", this.config.normalizedPercentInPool(entry))
                : "";
            this.rows.add(new Row(entry.item, label, rarity.displayName(), rarity.rgb(), odds));
        }

        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll));
    }

    /** Nombre que se muestra de una recompensa. */
    private static String labelOf(RewardEntry entry) {
        if (entry.label != null && !entry.label.isBlank()) {
            return entry.label;
        }
        return entry.item == null || entry.item.isEmpty() ? "(item vac\u00edo)" : entry.item.getHoverName().getString();
    }

    private int listTop() {
        // Sin buscador: la lista empieza justo debajo del titulo.
        return this.topPos + 22;
    }

    private int listHeight() {
        return this.panelHeight - 22 - 30;
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
        // Se sigue viendo la escena de la pre-apertura detras (video y musica no
        // se cortan): esta pantalla se abre ENCIMA, no en lugar de ella.
        if (CrateMedia.isActive()) {
            CrateMedia.renderBackground(g, this.width, this.height);
        }

        // Velo sobre la escena. Estaba al 63% de negro y dejaba todo apagado;
        // al 38% se sigue leyendo la ventana y el video no queda muerto detras.
        g.fill(0, 0, this.width, this.height, 0x61000000);
        FSGui.panel(g, this.leftPos, this.topPos, this.panelWidth, this.panelHeight);

        String title = "\u00a7d\u2726 \u00a7fRecompensas \u00a78(" + this.rows.size() + ")";
        g.drawString(this.font, title, this.leftPos + 9, this.topPos + 9, 16777215, false);

        // Separador bajo el titulo, que se apaga hacia los lados.
        //
        // Antes se dibujaba con fillGradient y salia media linea: ese metodo
        // interpola de arriba a abajo, asi que en una linea de un pixel de alto se
        // queda con el color de arriba, que en la mitad izquierda era transparente.
        // Para que se apague de lado a lado hay que ir por tramos. Y va DOS pixeles
        // por encima de la lista, que antes caia justo sobre su borde y se veian
        // dos lineas pisandose.
        this.fadeLine(g, this.leftPos + 8, this.topPos + 20, this.panelWidth - 16);

        // Se decide en cada fotograma: lo pone renderRows si el raton esta sobre una
        // fila con algo que contar.
        this.detailStack = ItemStack.EMPTY;
        this.renderRows(g, mouseX, mouseY);
        this.renderDetails(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * Ventana con los encantamientos, atributos y lore del item señalado.
     *
     * Va AL LADO de la lista y centrada en vertical, no pegada al raton como un
     * tooltip normal. Asi no tapa la fila que estas mirando ni se mueve mientras
     * recorres la lista: aparece siempre en el mismo sitio y se lee de un vistazo.
     *
     * El contenido se pide al propio item, o sea que sale igual que en el inventario
     * (encantamientos traducidos, modificadores, efectos, lore) y sin tener que
     * interpretar el NBT a mano aqui.
     */
    private void renderDetails(GuiGraphics g) {
        ItemStack stack = this.detailStack;
        if (stack == null || stack.isEmpty() || this.minecraft == null) {
            return;
        }

        List<Component> raw = stack.getTooltipLines(this.minecraft.player, TooltipFlag.Default.NORMAL);
        if (raw.isEmpty()) {
            return;
        }

        // Se parten las lineas largas para que la ventana no crezca a lo ancho.
        int maxText = 150;
        List<FormattedCharSequence> lines = new ArrayList<>();
        int textWidth = 0;
        for (Component line : raw) {
            for (FormattedCharSequence part : this.font.split(line, maxText)) {
                lines.add(part);
                textWidth = Math.max(textWidth, this.font.width(part));
            }
        }
        if (lines.isEmpty()) {
            return;
        }

        int padding = 8;
        int boxWidth = textWidth + padding * 2;
        int boxHeight = lines.size() * (this.font.lineHeight + 1) + padding * 2 - 1;

        // A la derecha de la lista si cabe; si no, a la izquierda. Y si tampoco,
        // pegada al borde sin salirse.
        int gap = 6;
        int x = this.leftPos + this.panelWidth + gap;
        if (x + boxWidth > this.width - 4) {
            x = this.leftPos - gap - boxWidth;
        }
        x = Math.max(4, Math.min(x, this.width - boxWidth - 4));

        // Centrada respecto a la ventana de recompensas, no respecto a la pantalla:
        // asi las dos quedan alineadas entre si.
        int y = this.topPos + (this.panelHeight - boxHeight) / 2;
        y = Math.max(4, Math.min(y, this.height - boxHeight - 4));

        FSGui.panel(g, x, y, boxWidth, boxHeight);

        int textY = y + padding;
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + padding, textY, 0xFFE6E6E6);
            textY += this.font.lineHeight + 1;
        }
    }

    /**
     * Linea de un pixel que se apaga hacia los dos lados.
     *
     * Se pinta por tramos porque el degradado de Minecraft solo va en vertical.
     * Con 24 tramos el apagado se ve continuo y son 24 rectangulos, nada.
     */
    private void fadeLine(GuiGraphics g, int x, int y, int width) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            int x1 = x + width * i / segments;
            int x2 = x + width * (i + 1) / segments;
            if (x2 <= x1) {
                continue;
            }
            // Maximo en el centro y cero en los extremos.
            float t = (i + 0.5F) / segments;
            float strength = 1.0F - Math.abs(t - 0.5F) * 2.0F;
            int alpha = (int) (46 * strength);
            if (alpha > 1) {
                g.fill(x1, y, x2, y + 1, alpha << 24 | 0xFFFFFF);
            }
        }
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
            if (hovered && hasDetails(row.icon())) {
                this.detailStack = row.icon();
            }
            if (hovered) {
                // La fila señalada se ACLARA en vez de oscurecerse, y el brillo se
                // apaga hacia la derecha. Oscurecerla, que es lo que se hacia
                // antes, sobre un fondo ya oscuro se nota apenas y ademas hunde la
                // fila en vez de destacarla.
                g.fillGradient(rowsLeft, rowY, rowsLeft + rowsWide, rowY + ROW_HEIGHT, 0x26FFFFFF, 0x0FFFFFFF);
                g.fill(rowsLeft, rowY, rowsLeft + 1, rowY + ROW_HEIGHT, 0x66D07CE8);
            } else if ((index & 1) == 1) {
                // Franjas alternas muy flojas: ayudan a seguir la fila sin
                // convertir la lista en un tablero de ajedrez.
                g.fill(rowsLeft, rowY, rowsLeft + rowsWide, rowY + ROW_HEIGHT, 0x0AFFFFFF);
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
