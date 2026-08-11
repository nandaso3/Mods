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

    /** Ancho que se le reserva a la ventana de detalles, o 0 si no cabe. */
    private int detailWidth;

    /** Ancho ideal de la ventana de detalles, incluido su relleno. */
    private static final int DETAIL_WIDTH = 166;
    /** Separacion entre la lista y la ventana. */
    private static final int DETAIL_GAP = 6;
    /** Por debajo de este ancho la ventana no se muestra. */
    private static final int MIN_DETAIL_WIDTH = 104;

    /** Lo que tarda la lista en apartarse o volver al centro. */
    private static final float SHIFT_MS = 170.0F;

    /** Sitio de la lista centrada, y el que ocupa cuando se aparta. */
    private int centeredLeft;
    private int shiftedLeft;

    /** 0 = centrada, 1 = apartada. Se anima entre las dos. */
    private float shift;
    private long lastFrameMs = System.currentTimeMillis();

    /** El boton de cerrar, que tiene que seguir a la lista al moverse. */
    private FSButton closeButton;

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
        this.panelHeight = Math.min(this.height - 20, 220);
        this.topPos = (this.height - this.panelHeight) / 2;

        // La lista mantiene su tamaño y su sitio: centrada, como siempre.
        this.panelWidth = Math.min(this.width - 20, 330);
        this.centeredLeft = (this.width - this.panelWidth) / 2;

        // La ventana de detalles no tiene sitio propio reservado. Lo que se calcula
        // aqui son las DOS posiciones de la lista: la de siempre y la que ocupa
        // cuando se aparta para dejar ver los detalles. Entre esas dos se anima.
        int room = this.width - this.panelWidth - DETAIL_GAP - 8;
        this.detailWidth = room >= MIN_DETAIL_WIDTH ? Math.min(DETAIL_WIDTH, room) : 0;
        this.shiftedLeft = this.detailWidth > 0
            ? Math.max(4, (this.width - (this.panelWidth + DETAIL_GAP + this.detailWidth)) / 2)
            : this.centeredLeft;

        this.leftPos = Math.round(this.centeredLeft + (this.shiftedLeft - this.centeredLeft) * this.shift);

        // Boton del mod y no el de vanilla: el de vanilla es la textura gris de
        // siempre y desentonaba con el resto de la ventana, ademas de quedarse sin
        // el brillo y el sonido de los demas.
        int closeWidth = Math.max(70, this.font.width("Cerrar") + 30);
        this.closeButton = this.addRenderableWidget(
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
        // Antes de dibujar nada: se mira si hay algo que contar, se avanza la
        // animacion y se recoloca la lista y su boton.
        this.detailStack = this.pickDetail(mouseX, mouseY);
        this.updateShift(!this.detailStack.isEmpty());
        this.leftPos = Math.round(this.centeredLeft + (this.shiftedLeft - this.centeredLeft) * this.shift);
        if (this.closeButton != null) {
            this.closeButton.setX(this.leftPos + (this.panelWidth - this.closeButton.getWidth()) / 2);
        }

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

        this.renderRows(g, mouseX, mouseY);
        this.renderDetails(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * Item señalado por el raton, si tiene algo que contar.
     *
     * La fila se decide SOLO por la altura del raton, y a lo ancho se acepta
     * cualquier punto entre la posicion centrada y la apartada.
     *
     * Eso ultimo es lo que evita un pique: al apartarse, la lista se mueve 86
     * pixeles: si el raton estaba cerca de su borde derecho, dejaria de estar encima,
     * los detalles desapareceran, la lista volveria al centro, el raton volveria a
     * estar encima... y se quedaria temblando. Aceptando toda la franja por la que la
     * lista puede pasar, el raton nunca se queda fuera por el propio movimiento.
     */
    private ItemStack pickDetail(int mouseX, int mouseY) {
        if (this.detailWidth <= 0 || this.rows.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int listTop = this.listTop();
        if (mouseY < listTop || mouseY >= listTop + this.listHeight()) {
            return ItemStack.EMPTY;
        }

        int bandLeft = Math.min(this.centeredLeft, this.shiftedLeft);
        int bandRight = Math.max(this.centeredLeft, this.shiftedLeft) + this.panelWidth;
        if (mouseX < bandLeft || mouseX >= bandRight) {
            return ItemStack.EMPTY;
        }

        int index = this.scroll + (mouseY - listTop) / ROW_HEIGHT;
        if (index < 0 || index >= this.rows.size()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = this.rows.get(index).icon();
        return hasDetails(stack) ? stack : ItemStack.EMPTY;
    }

    /** Avanza la animacion de apartarse, en tiempo real y no en ticks. */
    private void updateShift(boolean wantsDetails) {
        long now = System.currentTimeMillis();
        float step = Math.min(1.0F, Math.max(0.0F, (now - this.lastFrameMs) / SHIFT_MS));
        this.lastFrameMs = now;
        this.shift = Math.max(0.0F, Math.min(1.0F, this.shift + (wantsDetails ? step : -step)));
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

        // Mientras la lista aun se esta apartando no se dibuja: asi la ventana no
        // aparece encima de ella, sino en el hueco que acaba de dejar.
        if (this.detailWidth <= 0 || this.shift < 0.55F) {
            return;
        }

        List<Component> raw = stack.getTooltipLines(this.minecraft.player, TooltipFlag.Default.NORMAL);
        if (raw.isEmpty()) {
            return;
        }

        int padding = 7;
        int step = this.font.lineHeight + 1;
        int maxText = this.detailWidth - padding * 2;

        // Tope de alto: hay items con tooltips larguisimos (el de la captura tenia
        // trece lineas antes de partirlas) y la ventana se salia de la pantalla.
        int maxLines = Math.max(4, (this.height - 16 - padding * 2) / step);

        List<FormattedCharSequence> lines = new ArrayList<>();
        boolean cut = false;
        for (Component line : raw) {
            for (FormattedCharSequence part : this.font.split(line, maxText)) {
                if (lines.size() >= maxLines) {
                    cut = true;
                    break;
                }
                lines.add(part);
            }
            if (cut) {
                break;
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        if (cut) {
            // Se avisa de que hay mas, en vez de cortar sin decir nada.
            lines.set(lines.size() - 1, Component.literal("\u00a78...").getVisualOrderText());
        }

        int boxWidth = this.detailWidth;
        int boxHeight = lines.size() * step + padding * 2 - 1;
        int x = this.leftPos + this.panelWidth + DETAIL_GAP;

        // Centrada respecto a la ventana de recompensas, no respecto a la pantalla:
        // asi las dos quedan alineadas entre si.
        int y = this.topPos + (this.panelHeight - boxHeight) / 2;
        y = Math.max(4, Math.min(y, this.height - boxHeight - 4));

        // Se dibuja por encima de todo lo demas.
        //
        // Hacia falta porque los iconos de los items se dibujan a una profundidad
        // de 150, y esta ventana, aun pintandose despues, quedaba por debajo: en la
        // captura se veian los iconos y los nombres de la lista ATRAVESANDO la
        // ventana. Subiendo la profundidad a 400 (la que usan los tooltips de
        // vanilla) queda delante, y el flush deja el dibujo cerrado antes de
        // devolver la profundidad a su sitio.
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);

        FSGui.panel(g, x, y, boxWidth, boxHeight);
        int textY = y + padding;
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + padding, textY, 0xFFE6E6E6);
            textY += step;
        }

        g.flush();
        g.pose().popPose();
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
