package com.fscrates.client.screen;

import com.fscrates.animation.AnimationRegistry;
import com.fscrates.animation.CrateAnimation;
import com.fscrates.client.RegistryLists;
import com.fscrates.client.color.FSTextStyleScreen;
import com.fscrates.client.media.MediaCache;
import com.fscrates.client.render.CrateStyles;
import com.fscrates.client.widget.ScrollSelector;
import com.fscrates.config.CrateConfig;
import com.fscrates.config.ParticleLayer;
import com.fscrates.config.ParticleNames;
import com.fscrates.config.Rarity;
import com.fscrates.config.RewardEntry;
import com.fscrates.item.KeyModels;
import com.fscrates.network.FSNetwork;
import com.fscrates.network.SaveCratePacket;
import com.fscrates.registry.ModRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CrateEditorScreen extends Screen {
    private final CrateConfig config;
    private final BlockPos boundPos;
    private CrateEditorScreen.Tab activeTab = CrateEditorScreen.Tab.INFO;
    private final List<CrateEditorScreen.Label> labels = new ArrayList<>();
    private final List<CrateEditorScreen.TooltipZone> tooltipZones = new ArrayList<>();
    private String helpLine = "";
    private int leftPos;
    private int topPos;
    private int panelWidth;
    private int panelHeight;
    private RewardEntry selectedReward;
    private ParticleLayer selectedLayer;
    private int probScroll;
    private static final String COLOR_CHARS = "f7e6cab9d5234180";
    /** Pestanas por fila del encabezado. */
    private static final int TABS_PER_ROW = 6;
    /** Alto de una fila de pestanas (boton + separacion). */
    private static final int TAB_ROW_HEIGHT = 20;
    /** URL seleccionada en las pestanas de Videos / Musica. */
    private String selectedMediaUrl;

    public CrateEditorScreen(CrateConfig config) {
        this(config, null);
    }

    public CrateEditorScreen(CrateConfig config, BlockPos boundPos) {
        super(Component.literal("Editor de Crate"));
        this.config = config == null ? new CrateConfig() : config;
        this.boundPos = boundPos;
    }

    protected void init() {
        this.panelWidth = Math.min(this.width - 16, 540);
        // Con mas de una fila de pestanas el panel crece para no comerse el cuerpo.
        this.panelHeight = Math.min(this.height - 16, 320 + (headerRows() - 1) * TAB_ROW_HEIGHT);
        this.leftPos = (this.width - this.panelWidth) / 2;
        this.topPos = (this.height - this.panelHeight) / 2;
        this.labels.clear();
        this.tooltipZones.clear();
        this.initHeader();
        this.initFooter();
        switch (this.activeTab) {
            case INFO:
                this.initInfo();
                break;
            case REWARDS:
                this.initRewards();
                break;
            case PROBABILITY:
                this.initProbability();
                break;
            case APPEARANCE:
                this.initAppearance();
                break;
            case STYLE:
                this.initStyle();
                break;
            case PARTICLES:
                this.initParticles();
                break;
            case KEY:
                this.initKey();
                break;
            case KEYMODEL:
                this.initKeyModel();
                break;
            case SETTINGS:
                this.initSettings();
                break;
            case VIDEOS:
                this.initVideos();
                break;
            case MUSICA:
                this.initMusica();
        }
    }

    /** Cuantas filas de pestanas hacen falta (max 6 por fila). */
    private static int headerRows() {
        int tabs = CrateEditorScreen.Tab.values().length;
        return (tabs + TABS_PER_ROW - 1) / TABS_PER_ROW;
    }

    private int bodyX() {
        return this.leftPos + 8;
    }

    private int bodyY() {
        return this.topPos + 62 + (headerRows() - 1) * TAB_ROW_HEIGHT;
    }

    private int bodyW() {
        return this.panelWidth - 16;
    }

    private int bodyH() {
        return this.panelHeight - 62 - 28 - (headerRows() - 1) * TAB_ROW_HEIGHT;
    }

    private void initHeader() {
        CrateEditorScreen.Tab[] tabs = CrateEditorScreen.Tab.values();
        int rows = headerRows();
        int perRow = (tabs.length + rows - 1) / rows;
        int available = this.panelWidth - 16;

        for (int row = 0; row < rows; row++) {
            int from = row * perRow;
            int to = Math.min(tabs.length, from + perRow);
            int count = to - from;
            if (count <= 0) {
                break;
            }

            int tabW = (available - 2 * (count - 1)) / count;
            int x = this.leftPos + 8;
            int y = this.topPos + 24 + row * TAB_ROW_HEIGHT;

            for (int i = from; i < to; i++) {
                CrateEditorScreen.Tab tab = tabs[i];
                boolean active = tab == this.activeTab;
                String text = (active ? "\u00a7f\u00a7l" : "\u00a77") + tab.label;
                this.addRenderableWidget(Button.builder(Component.literal(text), b -> {
                    this.activeTab = tab;
                    this.rebuildWidgets();
                }).bounds(x, y, tabW, 18).build());
                x += tabW + 2;
            }
        }
    }

    private void initFooter() {
        int w = 150;
        this.addRenderableWidget(Button.builder(Component.literal(this.boundPos != null ? "\u00a7aGuardar cambios" : "\u00a7aGuardar y Obtener"), b -> {
            FSNetwork.sendToServer(new SaveCratePacket(this.config.save(), this.boundPos));
            this.onClose();
        }).bounds(this.leftPos + this.panelWidth - 150 - 8, this.topPos + this.panelHeight - 24, 150, 18).build());
        this.addRenderableWidget(
            Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds(this.leftPos + 8, this.topPos + this.panelHeight - 24, 80, 18)
                .build()
        );
    }

    private void initInfo() {
        this.helpLine = "Datos b\u00e1sicos: ID, nombre, tier y tiradas por apertura.";
        int x = this.bodyX();
        int y = this.bodyY();
        EditBox id = new EditBox(this.font, x + 170, y, 200, 16, Component.empty());
        id.setMaxLength(48);
        id.setValue(this.config.id);
        id.setResponder(s -> this.config.id = s.trim().toLowerCase().replace(' ', '_'));
        this.addRenderableWidget(id);
        this.addLabel(
            "ID de la crate:", x, y + 4, desc("Identificador \u00fanico (sin espacios).", "Se usa en /fscrate give, edit, delete.", "Ej: cofre_legendario")
        );
        String rawName = this.config.displayName == null ? "" : this.config.displayName;
        String plainName = stripCodes(rawName);
        int nameX = x + 170;
        int nameAvail = x + this.bodyW() - nameX;
        int styleBtnW = Math.max(84, Math.min(112, nameAvail - 150));
        int nameBoxW = Math.max(110, nameAvail - styleBtnW - 6);
        EditBox name = new EditBox(this.font, nameX, y + 24, nameBoxW, 16, Component.empty());
        name.setMaxLength(200);
        name.setValue(plainName);
        name.setResponder(s -> this.config.displayName = buildLegacy(s, colorOf(rawName), flagsOf(rawName)));
        this.addRenderableWidget(name);
        this.addLabel(
            "Nombre visible:",
            x,
            y + 28,
            desc(
                "Escribe el nombre; pulsa \u00abColor/estilo\u00bb para el MISMO editor de color",
                "y formato que usa el NBT de los items (rueda, RGB, hex, paleta)."
            )
        );
        this.addRenderableWidget(
            Button.builder(
                    Component.literal("\u00a7bColor/estilo \u00bb"),
                    b -> {
                        if (this.minecraft != null) {
                            String plain = stripCodes(this.config.displayName);
                            int rgb0 = colorOf(this.config.displayName);
                            boolean[] flags0 = flagsOf(this.config.displayName);
                            this.minecraft
                                .setScreen(
                                    new FSTextStyleScreen(
                                        "Color y estilo del nombre",
                                        plain,
                                        rgb0,
                                        flags0,
                                        (col, bold, italic, underline, strike, obf) -> this.config.displayName = buildLegacy(
                                                plain, col, new boolean[]{bold, italic, underline, strike, obf}
                                            ),
                                        () -> this.minecraft.setScreen(this)
                                    )
                                );
                        }
                    }
                )
                .bounds(nameX + nameBoxW + 6, y + 24, styleBtnW, 16)
                .build()
        );
        this.addIntField(
            x + 170,
            y + 48,
            60,
            this.config.rolls,
            v -> this.config.rolls = Math.max(1, v),
            "Tiradas por apertura:",
            x,
            y + 52,
            desc("Cu\u00e1ntas recompensas (por probabilidad) se entregan.", "Las garantizadas se suman aparte.")
        );
        this.addLabel(
            "\u00a78La caja tiene TODAS las rarezas (config. en \u00abRarezas\u00bb y por item en \u00abPremios\u00bb).",
            x,
            y + 74,
            desc(
                "Ya no hay 'rareza base': la rareza se decide al abrir",
                "seg\u00fan la tabla de la pesta\u00f1a \u00abRarezas\u00bb, y cada item",
                "tiene su propia rareza en \u00abPremios\u00bb."
            )
        );
        this.addLabel(
            "\u00a78Estilo: \u00a7b" + CrateStyles.displayName(this.config.styleId) + " \u00a77\u2192 pesta\u00f1a \u00abDise\u00f1o\u00bb",
            x,
            y + 96,
            desc("Dise\u00f1o visual de la crate (independiente del tier).", "C\u00e1mbialo en la pesta\u00f1a \u00abDise\u00f1o\u00bb.")
        );
        this.addLabel("\u00a78Animaci\u00f3n: \u00a7f" + AnimationRegistry.get(this.config.animationId).displayName(), x, y + 120, null);
        this.addLabel(
            "\u00a78Recompensas: \u00a7f" + this.config.rewards.size() + "  \u00a78Capas de part\u00edculas: \u00a7f" + this.config.particleLayers.size(),
            x,
            y + 132,
            null
        );
    }

    private void initRewards() {
        this.helpLine = "Izquierda: busca y clic en un item. Derecha: lista (scroll) y editor de la seleccionada.";
        int x = this.bodyX();
        int y = this.bodyY();
        int colW = (this.bodyW() - 8) / 2;
        int rightX = x + colW + 8;
        EditBox search = new EditBox(this.font, x, y, colW, 16, Component.empty());
        boolean editingEffect = this.selectedReward != null
            && this.config.rewards.contains(this.selectedReward)
            && this.selectedReward.type == RewardEntry.Type.EFFECT;
        if (editingEffect) {
            RewardEntry er = this.selectedReward;
            search.setHint(Component.literal("Buscar efecto..."));
            this.addRenderableWidget(search);
            ScrollSelector<ResourceLocation> effects = new ScrollSelector<>(
                x,
                y + 20,
                colW,
                this.bodyH() - 22,
                16,
                rl -> (rl.toString().equals(er.effectId) ? "\u00a7a\u2714 " : "\u00a7f") + RegistryLists.effectName(rl),
                rl -> RegistryLists.effectName(rl) + " " + rl,
                rl -> ItemStack.EMPTY
            );
            effects.setItems(RegistryLists.effects());
            effects.onSelect(rl -> {
                er.effectId = rl.toString();
                this.rebuildWidgets();
            });
            search.setResponder(effects::setQuery);
            this.addRenderableWidget(effects);
        } else {
            search.setHint(Component.literal("Buscar item..."));
            this.addRenderableWidget(search);
            ScrollSelector<Item> items = new ScrollSelector<>(
                x,
                y + 20,
                colW,
                this.bodyH() - 22,
                18,
                RegistryLists::itemName,
                it -> RegistryLists.itemName(it) + " " + RegistryLists.itemId(it),
                it -> new ItemStack(it)
            );
            items.setItems(RegistryLists.items());
            items.onSelect(it -> {
                RewardEntry r2 = new RewardEntry(RewardEntry.Type.ITEM);
                r2.item = new ItemStack(it);
                r2.label = RegistryLists.itemName(it);
                r2.chance = 10.0;
                this.config.rewards.add(r2);
                this.selectedReward = r2;
                this.rebuildWidgets();
            });
            search.setResponder(items::setQuery);
            this.addRenderableWidget(items);
        }

        Object selR = null;
        ScrollSelector<RewardEntry> current = new ScrollSelector<>(
            rightX,
            y,
            colW,
            this.bodyH() - 98,
            16,
            rx -> (rx == this.selectedReward ? "\u00a7e\u25b6 " : "\u00a7f")
                    + rx.describe()
                    + " "
                    + rx.effectiveRarity(this.config.rarity).color()
                    + "["
                    + rx.effectiveRarity(this.config.rarity).displayName()
                    + "] \u00a77("
                    + fmt(this.config.normalizedPercentInPool(rx))
                    + "%)",
            RewardEntry::describe,
            rx -> rx.type == RewardEntry.Type.ITEM ? rx.item : ItemStack.EMPTY
        );
        current.setItems(new ArrayList<>(this.config.rewards));
        current.onSelect(rx -> {
            this.selectedReward = rx;
            this.rebuildWidgets();
        });
        this.addRenderableWidget(current);
        int addY = y + this.bodyH() - 94;
        this.addRenderableWidget(Button.builder(Component.literal("+ XP"), b -> {
            RewardEntry rx = new RewardEntry(RewardEntry.Type.XP);
            rx.xp = 100;
            this.config.rewards.add(rx);
            this.rebuildWidgets();
        }).bounds(rightX, addY, colW / 2 - 2, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("+ Efecto"), b -> {
            this.config.rewards.add(new RewardEntry(RewardEntry.Type.EFFECT));
            this.rebuildWidgets();
        }).bounds(rightX + colW / 2, addY, colW / 2 - 2, 16).build());
        if (this.selectedReward != null && this.config.rewards.contains(this.selectedReward)) {
            RewardEntry r = this.selectedReward;
            int fy = y + this.bodyH() - 72;
            this.addDoubleField(
                rightX + 70,
                fy,
                50,
                r.chance,
                v -> r.chance = Math.max(0.0, v),
                "Prob. (%)",
                rightX,
                fy + 4,
                desc("Probabilidad en %. Se normaliza con las dem\u00e1s para sumar 100%.")
            );
            this.addIntField(
                rightX + 150, fy, 36, r.minAmount, v -> r.minAmount = Math.max(1, v), "Min", rightX + 122, fy + 4, desc("Cantidad m\u00ednima entregada.")
            );
            this.addIntField(
                rightX + 235, fy, 36, r.maxAmount, v -> r.maxAmount = Math.max(1, v), "Max", rightX + 200, fy + 4, desc("Cantidad m\u00e1xima entregada.")
            );
            this.addToggle(rightX, fy + 22, colW - 70, r.guaranteed ? "Garantizada: S\u00ed" : "Garantizada: No", r.guaranteed, () -> {
                r.guaranteed = !r.guaranteed;
                this.rebuildWidgets();
            }, desc("Si est\u00e1 activo, SIEMPRE se entrega (100%)."));
            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cQuitar"), b -> {
                this.config.rewards.remove(r);
                this.selectedReward = null;
                this.rebuildWidgets();
            }).bounds(rightX + colW - 64, fy + 22, 64, 16).build());
            if (r.type == RewardEntry.Type.XP) {
                this.addIntField(rightX + 40, fy + 44, 80, r.xp, v -> r.xp = Math.max(0, v), "XP", rightX, fy + 48, desc("Puntos de experiencia entregados."));
            } else if (r.type == RewardEntry.Type.EFFECT) {
                this.addIntField(
                    rightX + 44,
                    fy + 44,
                    40,
                    r.effectAmplifier + 1,
                    v -> r.effectAmplifier = Math.max(0, v - 1),
                    "Nivel",
                    rightX,
                    fy + 48,
                    desc("Nivel del efecto (1 = nivel I, 2 = nivel II...).", "Aumenta la potencia del efecto.")
                );
                this.addSecondsField(
                    rightX + 175,
                    fy + 44,
                    50,
                    r.effectDuration,
                    v -> r.effectDuration = Math.max(1, v),
                    "Duraci\u00f3n (s)",
                    rightX + 96,
                    fy + 48,
                    desc("Duraci\u00f3n del efecto en segundos.", "Elige el efecto en la lista de la izquierda.")
                );
            } else if (r.type == RewardEntry.Type.ITEM) {
                int halfBtn = (colW - 4) / 2;
                Rarity itemR = r.effectiveRarity(this.config.rarity);
                String rarLabel = r.rarity != null && !r.rarity.isBlank() ? "Rareza: " + itemR.color() + itemR.displayName() : "\u00a77Rareza: Auto";
                this.addRenderableWidget(Button.builder(Component.literal(rarLabel), b -> {
                    r.rarity = cycleItemRarity(r.rarity);
                    this.rebuildWidgets();
                }).bounds(rightX, fy + 44, halfBtn, 16).build());
                this.tooltipZones
                    .add(
                        new CrateEditorScreen.TooltipZone(
                            rightX,
                            fy + 44,
                            halfBtn,
                            16,
                            desc(
                                "Rareza de ESTE item del pool.",
                                "Define el color de la luz, el sonido y las part\u00edculas",
                                "cuando este item es el premio. Auto = usa el tier de la crate."
                            )
                        )
                    );
                this.addRenderableWidget(Button.builder(Component.literal("\u00a7b\u270e NBT del item"), b -> {
                    if (r.item != null && !r.item.isEmpty()) {
                        this.minecraft.setScreen(new NbtEditorScreen(this, r.item));
                    }
                }).bounds(rightX + halfBtn + 4, fy + 44, colW - halfBtn - 4, 16).build());
                this.tooltipZones
                    .add(
                        new CrateEditorScreen.TooltipZone(
                            rightX + halfBtn + 4,
                            fy + 44,
                            colW - halfBtn - 4,
                            16,
                            desc(
                                "Editor de NBT: nombre, lore con color,",
                                "encantamientos, atributos, irrompible, CustomModelData...",
                                "Todo manual, sin pegar comandos."
                            )
                        )
                    );
            }
        }
    }

    private void initProbability() {
        this.helpLine = "Items AGRUPADOS por rareza. El % es DENTRO del pool de su rareza (cada rareza es independiente y full configurable).";
        int x = this.bodyX();
        int y = this.bodyY();
        List<Object> rows = this.probRows();
        int total = rows.size();
        int visibleRows = Math.max(1, this.bodyH() / 22);
        int maxScroll = Math.max(0, total - visibleRows);
        if (this.probScroll > maxScroll) {
            this.probScroll = maxScroll;
        }

        if (this.probScroll < 0) {
            this.probScroll = 0;
        }

        int end = Math.min(total, this.probScroll + visibleRows);

        for (int i = this.probScroll; i < end; i++) {
            Object row = rows.get(i);
            int ry = y + (i - this.probScroll) * 22;
            if (row instanceof RewardEntry) {
                RewardEntry r = (RewardEntry)row;
                if (!r.guaranteed) {
                    this.addDoubleField(
                        x + 150,
                        ry,
                        50,
                        r.chance,
                        v -> r.chance = Math.max(0.0, v),
                        null,
                        0,
                        0,
                        desc("Peso del item DENTRO del pool de su rareza.", "Se normaliza SOLO con los demas items de ESA misma rareza.")
                    );
                }
            }
        }

        this.addRenderableWidget(Button.builder(Component.literal("Igualar por rareza"), b -> {
            for (Rarity rar : Rarity.values()) {
                int n = 0;

                for (RewardEntry rx : this.config.rewards) {
                    if (!rx.guaranteed && rx.effectiveRarity(this.config.rarity) == rar) {
                        n++;
                    }
                }

                if (n > 0) {
                    double each = 100.0 / (double)n;

                    for (RewardEntry r2 : this.config.rewards) {
                        if (!r2.guaranteed && r2.effectiveRarity(this.config.rarity) == rar) {
                            r2.chance = each;
                        }
                    }
                }
            }

            this.rebuildWidgets();
        }).bounds(this.leftPos + 92, this.topPos + this.panelHeight - 24, 130, 18).build());
    }

    private List<Object> probRows() {
        ArrayList<Object> rows = new ArrayList<>();

        for (Rarity rar : Rarity.values()) {
            boolean header = false;

            for (RewardEntry r : this.config.rewards) {
                if (r.effectiveRarity(this.config.rarity) == rar) {
                    if (!header) {
                        rows.add(rar);
                        header = true;
                    }

                    rows.add(r);
                }
            }
        }

        return rows;
    }

    private void initAnimation() {
        this.helpLine = "Elige la animaci\u00f3n del cofre. Ocurre EN el cofre, en el mundo, con tensi\u00f3n antes del premio.";
        int x = this.bodyX();
        int y = this.bodyY();
        int colW = this.bodyW();
        ScrollSelector<CrateAnimation> list = new ScrollSelector<>(
            x,
            y,
            colW,
            this.bodyH() - 28,
            14,
            a -> (a.id().equals(this.config.animationId) ? "\u00a7a\u2714 " : "\u00a7f")
                    + a.displayName()
                    + " \u00a78("
                    + (double)a.durationTicks() / 20.0
                    + "s)",
            a -> a.displayName() + " " + a.id(),
            null
        );
        list.setItems(AnimationRegistry.all());
        list.onSelect(a -> {
            this.config.animationId = a.id();
            this.rebuildWidgets();
        });
        this.addRenderableWidget(list);
        CrateAnimation sel = AnimationRegistry.get(this.config.animationId);
        this.addLabel("\u00a7e" + sel.displayName() + ": \u00a77" + sel.description(), x, y + this.bodyH() - 22, null);
    }

    private void initStyle() {
        this.helpLine = "Elige el DISE\u00d1O visual del cofre (independiente del tier). Escribe para buscar entre todos los modelos.";
        int x = this.bodyX();
        int y = this.bodyY();
        int colW = this.bodyW();
        List<String> ids = CrateStyles.cycleIds();
        ScrollSelector<String> list = new ScrollSelector<>(
            x,
            y,
            colW,
            this.bodyH() - 28,
            14,
            id -> (id.equals(this.config.styleId) ? "\u00a7a\u2714 " : "\u00a7f") + CrateStyles.displayName(id),
            id -> CrateStyles.displayName(id) + " " + id,
            null
        );
        list.setItems(ids);
        list.onSelect(id -> {
            this.config.styleId = id;
            this.rebuildWidgets();
        });
        this.addRenderableWidget(list);
        this.addLabel(
            "\u00a7eDise\u00f1o actual: \u00a7b" + CrateStyles.displayName(this.config.styleId) + " \u00a77(" + ids.size() + " dise\u00f1os)",
            x,
            y + this.bodyH() - 22,
            null
        );
    }

    private void initAppearance() {
        this.helpLine = "Brillo, part\u00edculas on/off, nombre flotante, color del nombre y texto flotante (color por l\u00ednea).";
        int x = this.bodyX();
        int y = this.bodyY();
        int colW = (this.bodyW() - 10) / 2;
        this.addToggle(x, y, colW, this.config.glow ? "Brillo del item: Activado" : "Brillo del item: Desactivado", this.config.glow, () -> {
            this.config.glow = !this.config.glow;
            this.rebuildWidgets();
        }, desc("El item de crate brilla como encantado."));
        this.addToggle(x, y + 22, colW, this.config.particles ? "Part\u00edculas: Activado" : "Part\u00edculas: Desactivado", this.config.particles, () -> {
            this.config.particles = !this.config.particles;
            this.rebuildWidgets();
        }, desc("Part\u00edculas de reposo alrededor de la crate."));
        this.addToggle(x, y + 44, colW, this.config.floatingName ? "Nombre flotante: S\u00ed" : "Nombre flotante: No", this.config.floatingName, () -> {
            this.config.floatingName = !this.config.floatingName;
            this.rebuildWidgets();
        }, desc("Muestra el nombre flotando sobre la crate."));
        EditBox hex = new EditBox(this.font, x + 70, y + 70, 110, 16, Component.empty());
        hex.setMaxLength(7);
        hex.setValue(this.config.nameColorHexOverride);
        hex.setHint(Component.literal("#RRGGBB"));
        hex.setResponder(s -> this.config.nameColorHexOverride = s.trim());
        this.addRenderableWidget(hex);
        this.addLabel("Color:", x, y + 74, desc("Color del nombre (#RRGGBB). Vac\u00edo = color del tier."));
        this.addToggle(x, y + 92, colW, this.config.showOdds ? "Mostrar % encima: S\u00ed" : "Mostrar % encima: No", this.config.showOdds, () -> {
            this.config.showOdds = !this.config.showOdds;
            this.rebuildWidgets();
        }, desc("Muestra la probabilidad de cada recompensa flotando sobre el cofre.", "\u00datil para que los jugadores vean las posibilidades."));
        int tx = x + colW + 10;
        this.addLabel(
            "\u00a7eTexto flotante \u00a77(clic para editar, \u25a0 = color):",
            tx,
            y - 2,
            desc(
                "Haz CLIC en una linea para escribir/editar.",
                "El boton \u25a0 cambia el color de ESA linea.",
                "Tambi\u00e9n aceptas c\u00f3digos & dentro del texto."
            )
        );
        int maxLines = 8;
        char[] lineColors = new char[maxLines];
        String[] lineTexts = new String[maxLines];

        for (int i = 0; i < maxLines; i++) {
            String raw = i < this.config.floatingText.size() ? this.config.floatingText.get(i) : "";
            char col = 'f';
            String txt = raw;
            if (raw.length() >= 2 && (raw.charAt(0) == '&' || raw.charAt(0) == 167) && "f7e6cab9d5234180".indexOf(raw.charAt(1)) >= 0) {
                col = raw.charAt(1);
                txt = raw.substring(2);
            }

            lineColors[i] = col;
            lineTexts[i] = txt;
        }

        Runnable sync = () -> {
            ArrayList<String> out = new ArrayList<>();

            for (int k = 0; k < maxLines; k++) {
                out.add(lineTexts[k].isEmpty() ? "" : "&" + lineColors[k] + lineTexts[k]);
            }

            this.config.setFloatingText(String.join("\n", out));
        };

        for (int j = 0; j < maxLines; j++) {
            int idx = j;
            int ry = y + 12 + j * 20;
            this.addRenderableWidget(Button.builder(Component.literal("\u00a7" + lineColors[j] + "\u25a0"), b -> {
                int pos = "f7e6cab9d5234180".indexOf(lineColors[idx]);
                lineColors[idx] = "f7e6cab9d5234180".charAt((pos + 1) % "f7e6cab9d5234180".length());
                sync.run();
                this.rebuildWidgets();
            }).bounds(tx, ry, 18, 16).build());
            EditBox line = new EditBox(this.font, tx + 22, ry, colW - 22, 16, Component.empty());
            line.setMaxLength(128);
            line.setEditable(true);
            line.setBordered(true);
            line.setValue(lineTexts[j]);
            line.setHint(Component.literal("L\u00ednea " + (j + 1) + "\u2026"));
            line.setResponder(s -> {
                lineTexts[idx] = s;
                sync.run();
            });
            this.addRenderableWidget(line);
        }
    }

    private void initParticles() {
        this.helpLine = "Capas ilimitadas. Anillo/Halo/Espiral/V\u00f3rtice se adaptan al cofre.";
        int x = this.bodyX();
        int y = this.bodyY();
        int listW = 118;
        int midW = 126;
        int midX = x + 118 + 6;
        int rx = midX + 126 + 8;
        int fw = this.leftPos + this.panelWidth - 8 - rx;
        if (this.selectedLayer != null && !this.config.particleLayers.contains(this.selectedLayer)) {
            this.selectedLayer = null;
        }

        ScrollSelector<ParticleLayer> layers = new ScrollSelector<>(
            x,
            y,
            118,
            this.bodyH() - 20,
            22,
            pl -> (pl == this.selectedLayer ? "\u00a7e\u25b6 " : "") + pl.shortLabel(),
            ParticleLayer::shortLabel,
            pl -> ItemStack.EMPTY
        );
        layers.setItems(new ArrayList<>(this.config.particleLayers));
        layers.onSelect(pl -> {
            this.selectedLayer = pl;
            this.rebuildWidgets();
        });
        this.addRenderableWidget(layers);
        this.addRenderableWidget(Button.builder(Component.literal("\u00a7a+ Capa"), b -> {
            ParticleLayer newL = new ParticleLayer();
            this.config.particleLayers.add(newL);
            this.selectedLayer = newL;
            this.rebuildWidgets();
        }).bounds(x, y + this.bodyH() - 18, 118, 16).build());
        EditBox search = new EditBox(this.font, midX, y, 126, 16, Component.empty());
        search.setHint(Component.literal("Buscar part\u00edcula..."));
        this.addRenderableWidget(search);
        ScrollSelector<ResourceLocation> types = new ScrollSelector<>(
            midX,
            y + 20,
            126,
            this.bodyH() - 22,
            13,
            rl -> (this.selectedLayer != null && rl.toString().equals(this.selectedLayer.particleId) ? "\u00a7a\u2714 " : "\u00a7f")
                    + ParticleNames.spanish(rl.getPath()),
            rl -> ParticleNames.spanish(rl.getPath()) + " " + rl,
            rl -> ItemStack.EMPTY
        );
        types.setItems(RegistryLists.particles());
        types.onSelect(rl -> {
            if (this.selectedLayer != null) {
                this.selectedLayer.particleId = rl.toString();
                this.rebuildWidgets();
            }
        });
        search.setResponder(types::setQuery);
        this.addRenderableWidget(types);
        if (this.selectedLayer == null) {
            this.addLabel("\u00a77Selecciona o", rx, y + 4, null);
            this.addLabel("\u00a77crea una capa \u2190", rx, y + 16, null);
        } else {
            ParticleLayer l = this.selectedLayer;
            ParticleLayer layerVar = this.selectedLayer;
            int half = fw / 2;
            int fieldW = 42;
            this.addLabel(
                "\u00a7e" + ParticleNames.spanish(l.particleId.contains(":") ? l.particleId.substring(l.particleId.indexOf(58) + 1) : l.particleId),
                rx,
                y,
                null
            );
            this.addRenderableWidget(Button.builder(Component.literal("Fase: \u00a7e" + l.phase.label), b -> {
                l.phase = l.phase.next();
                this.rebuildWidgets();
            }).bounds(rx, y + 12, fw, 16).build());
            this.tooltipZones
                .add(new CrateEditorScreen.TooltipZone(rx, y + 12, fw, 16, desc("Cuando emite:", "Reposo, Tensi\u00f3n, Apertura, Revelaci\u00f3n, Final.")));
            this.addRenderableWidget(Button.builder(Component.literal("Forma: \u00a7b" + l.shape.label), b -> {
                l.shape = l.shape.next();
                l.applyShapeDefaults();
                this.rebuildWidgets();
            }).bounds(rx, y + 32, fw, 16).build());
            this.tooltipZones
                .add(
                    new CrateEditorScreen.TooltipZone(
                        rx,
                        y + 32,
                        fw,
                        16,
                        desc(
                            "Forma/movimiento: Halo, Anillo, Espiral, V\u00f3rtice, Fuente...",
                            "Anillo/Halo/Espiral/V\u00f3rtice ENVUELVEN el cofre seg\u00fan su TAMA\u00d1O",
                            "real (un cofre legendario/m\u00edtico es m\u00e1s grande -> radio mayor)."
                        )
                    )
                );
            int r1 = y + 54;
            int r2 = y + 74;
            int r3 = y + 94;
            int fw2 = 40;
            int f1x = rx + half - fw2 - 4;
            int f2x = rx + fw - fw2;
            this.addIntField(f1x, r1, fw2, l.count, v -> l.count = Math.max(1, v), "Cant.", rx, r1 + 4, desc("Part\u00edculas por emisi\u00f3n."));
            this.addDoubleField(f2x, r1, fw2, l.speed, v -> l.speed = Math.max(0.0, v), "Vel.", rx + half, r1 + 4, desc("Empuje de las part\u00edculas."));
            this.addDoubleField(f1x, r2, fw2, l.spread, v -> l.spread = Math.max(0.0, v), "Disp.", rx, r2 + 4, desc("Apertura aleatoria."));
            this.addDoubleField(
                f2x,
                r2,
                fw2,
                l.radius,
                v -> l.radius = Math.max(0.0, v),
                "Radio",
                rx + half,
                r2 + 4,
                desc(
                    "Radio del anillo/halo/\u00f3rbita/espiral. ~0.85 rodea el cofre.",
                    "Se MULTIPLICA por el tama\u00f1o del cofre (legendario/m\u00edtico m\u00e1s grande)."
                )
            );
            this.addDoubleField(
                f1x,
                r3,
                fw2,
                l.yOffset,
                v -> l.yOffset = v,
                "Alt.",
                rx,
                r3 + 4,
                desc("Altura sobre el bloque (se escala con el tama\u00f1o del cofre).", "~0.2 al ras del suelo, ~1.1 a la altura de la tapa.")
            );
            this.addIntField(
                f2x, r3, fw2, l.interval, v -> l.interval = Math.max(1, v), "Int.", rx + half, r3 + 4, desc("Solo en Reposo: emite cada N ticks (20 = 1s).")
            );
            int cy = y + 116;
            this.addToggle(rx, cy, fw, l.useRarityColor ? "Color: tier" : "Color: hex", l.useRarityColor, () -> {
                l.useRarityColor = !l.useRarityColor;
                this.rebuildWidgets();
            }, desc("Solo afecta a 'Polvo de color'. Tier = color de la rareza."));
            cy += 20;
            if (!l.useRarityColor) {
                EditBox hex = new EditBox(this.font, rx + 36, cy, fw - 36, 16, Component.empty());
                hex.setMaxLength(7);
                hex.setValue(l.colorHex);
                hex.setHint(Component.literal("#RRGGBB"));
                hex.setResponder(s -> l.colorHex = s.trim());
                this.addRenderableWidget(hex);
                this.addLabel("Hex:", rx, cy + 4, null);
                cy += 20;
            }

            this.addRenderableWidget(Button.builder(Component.literal("\u00a7cQuitar capa"), b -> {
                this.config.particleLayers.remove(l);
                this.selectedLayer = null;
                this.rebuildWidgets();
            }).bounds(rx, cy, fw, 16).build());
        }
    }

    private void initKey() {
        this.helpLine = "Fantastic Key (universal). Izq: probabilidad de cada RAREZA al abrir. Der: PITY (rareza asegurada) y llave.";
        int x = this.bodyX();
        int y = this.bodyY();
        int bw = this.bodyW();
        int gap = 10;
        int colW = (bw - gap) / 2;
        int lx = x;
        int rx2 = x + colW + gap;
        int leftFieldW = Math.max(38, Math.min(52, colW / 3));
        int rightFieldW = Math.max(34, Math.min(46, colW / 4));
        this.addLabel(
            "\u00a7ePROB. DE RAREZA \u00a77(peso \u2192 100%)",
            x,
            y,
            desc("Al abrir, la crate tira UNA rareza seg\u00fan estos pesos,", "y gira/entrega un item del POOL de esa rareza (pesta\u00f1a Premios).")
        );
        int ly = y + 16;

        for (Rarity r : Rarity.values()) {
            int poolN = this.config.rewardCountForRarity(r);
            String lbl = r.color() + r.displayName() + " \u00a77(" + fmt(this.config.rarityChancePercent(r)) + "%, " + poolN + ")";
            this.addLabel(
                lbl,
                lx,
                ly + 4,
                desc(
                    poolN == 0
                        ? "\u00a7cSin items: si sale esta rareza, la ruleta usa la rareza con items mas cercana."
                        : "\u00a77" + poolN + " item(s) en el pool de " + r.displayName() + "."
                )
            );
            this.addDoubleField(
                lx + colW - leftFieldW,
                ly,
                leftFieldW,
                this.config.rarityChance(r),
                v -> this.config.rarityChances.put(r, Double.valueOf(Math.max(0.0, v))),
                null,
                0,
                0,
                desc("Peso relativo de la rareza " + r.displayName() + ". (El % se recalcula al reabrir la pesta\u00f1a.)")
            );
            ly += 20;
        }

        int halfL = (colW - 4) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("\u00a77Igualar"), b -> {
            double each = 100.0 / (double)Rarity.values().length;

            for (Rarity r2 : Rarity.values()) {
                this.config.rarityChances.put(r2, Double.valueOf(each));
            }

            this.rebuildWidgets();
        }).bounds(lx, ly + 2, halfL, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("\u00a77Preset 60/25/10/4/1"), b -> {
            this.config.rarityChances.clear();
            this.config.rarityChances.putAll(CrateConfig.defaultRarityChances());
            this.rebuildWidgets();
        }).bounds(lx + halfL + 4, ly + 2, colW - halfL - 4, 16).build());
        this.addLabel(
            "\u00a76\u2726 PITY \u00a77(rareza asegurada)",
            rx2,
            y,
            desc("Cada N aperturas (por jugador y por crate) se GARANTIZA", "la rareza elegida, ignorando la tirada normal.")
        );
        this.addToggle(rx2, y + 16, colW, this.config.pityEnabled ? "Pity: Activado" : "Pity: Desactivado", this.config.pityEnabled, () -> {
            this.config.pityEnabled = !this.config.pityEnabled;
            this.rebuildWidgets();
        }, desc("Activa/desactiva la rareza asegurada por aperturas."));
        this.addIntField(
            rx2 + colW - rightFieldW,
            y + 38,
            rightFieldW,
            this.config.pityInterval,
            v -> this.config.pityInterval = Math.max(1, v),
            "Cada (aperturas):",
            rx2,
            y + 42,
            desc("N\u00famero de aperturas para garantizar la rareza (m\u00ednimo 1).")
        );
        Rarity pr = this.config.pityRarity == null ? Rarity.LEGENDARY : this.config.pityRarity;
        this.addRenderableWidget(Button.builder(Component.literal("Rareza: " + pr.color() + pr.displayName()), b -> {
            this.config.pityRarity = pr.next();
            this.rebuildWidgets();
        }).bounds(rx2, y + 60, colW, 16).build());
        this.addToggle(rx2, y + 84, colW, this.config.consumeKey ? "Consumir llave: S\u00ed" : "Consumir llave: No", this.config.consumeKey, () -> {
            this.config.consumeKey = !this.config.consumeKey;
            this.rebuildWidgets();
        }, desc("Si est\u00e1 activo, la llave se gasta al abrir (universal o \u00fanica)."));
    }

    private void initKeyModel() {
        this.helpLine = "Llave \u00fanica exclusiva de esta caja. La Fantastic Key universal SIEMPRE abre; se entrega al crear/dar la caja.";
        int x = this.bodyX();
        int y = this.bodyY();
        int bw = this.bodyW();
        this.addToggle(
            x,
            y,
            bw,
            this.config.uniqueKeyEnabled ? "Llave \u00fanica: ACTIVADA" : "Llave \u00fanica: Desactivada",
            this.config.uniqueKeyEnabled,
            () -> {
                this.config.uniqueKeyEnabled = !this.config.uniqueKeyEnabled;
                if (this.config.uniqueKeyEnabled && (this.config.uniqueKeyModel == null || this.config.uniqueKeyModel.isBlank())) {
                    KeyModels.Entry f = KeyModels.first();
                    if (f != null) {
                        this.config.uniqueKeyModel = f.id;
                    }
                }

                this.rebuildWidgets();
            },
            desc(
                "ACTIVADA: esta caja tiene una llave \u00fanica propia (se entrega al crearla/darla).",
                "La \u00a7d\u2726 Fantastic Key \u2726\u00a77 universal SIEMPRE abre cualquier caja.",
                "Desactivada: solo se usa la Fantastic Key universal."
            )
        );
        if (!this.config.uniqueKeyEnabled) {
            this.addLabel("\u00a77Esta caja se abre solo con la \u00a7d\u2726 Fantastic Key \u2726\u00a77 universal.", x, y + 24, null);
            this.addLabel("\u00a78Act\u00edvala para darle a esta caja su propia llave (uno de " + KeyModels.ALL.size() + " modelos).", x, y + 40, null);
        } else {
            int colW = (bw - 12) / 2;
            int rx2 = x + colW + 12;
            int headerY = y + 24;
            int rowY = y + 38;
            this.addLabel("\u00a7eModelo \u00a77(" + KeyModels.ALL.size() + "):", x, headerY, desc("Elige el modelo de la llave. Escribe para buscar."));
            ScrollSelector<KeyModels.Entry> list = new ScrollSelector<>(
                x,
                rowY,
                colW,
                this.bodyH() - 40,
                18,
                e -> (e.id.equals(this.config.uniqueKeyModel) ? "\u00a7a\u2714 " : "\u00a7f") + e.defaultName,
                e -> e.defaultName + " " + e.group + " " + e.id,
                e -> this.previewKey(e)
            );
            list.setItems(KeyModels.ALL);
            list.onSelect(e -> {
                this.config.uniqueKeyModel = e.id;
                this.rebuildWidgets();
            });
            this.addRenderableWidget(list);
            KeyModels.Entry sel = KeyModels.byId(this.config.uniqueKeyModel);
            String defName = sel != null ? sel.defaultName : "\u2726 Llave de Crate \u2726";
            this.addLabel(
                "\u00a7eNombre de la llave:",
                rx2,
                headerY,
                desc(
                    "Nombre visible de la llave (editable).",
                    "\u00abColor/estilo\u00bb abre el editor completo (rueda, RGB, hex).",
                    "Vac\u00edo = nombre por defecto del modelo."
                )
            );
            String rawName = this.config.uniqueKeyName == null ? "" : this.config.uniqueKeyName;
            String plainName = stripCodes(rawName);
            int styleBtnW = Math.max(80, Math.min(100, colW / 3));
            int nameBoxW = Math.max(70, colW - styleBtnW - 6);
            EditBox nameBox = new EditBox(this.font, rx2, rowY, nameBoxW, 16, Component.empty());
            nameBox.setMaxLength(64);
            nameBox.setValue(plainName);
            nameBox.setHint(Component.literal(defName));
            nameBox.setResponder(s -> {
                if (s != null && !s.isBlank()) {
                    this.config.uniqueKeyName = buildLegacy(s, colorOf(this.config.uniqueKeyName), flagsOf(this.config.uniqueKeyName));
                } else {
                    this.config.uniqueKeyName = "";
                }
            });
            this.addRenderableWidget(nameBox);
            this.addRenderableWidget(
                Button.builder(
                        Component.literal("\u00a7bColor/estilo \u00bb"),
                        b -> {
                            if (this.minecraft != null) {
                                String plainN = stripCodes(this.config.uniqueKeyName);
                                String sample = plainN.isEmpty() ? defName : plainN;
                                int rgb0 = colorOf(this.config.uniqueKeyName);
                                boolean[] flags0 = flagsOf(this.config.uniqueKeyName);
                                this.minecraft
                                    .setScreen(
                                        new FSTextStyleScreen(
                                            "Color y estilo de la llave", sample, rgb0, flags0, (col, bold, italic, underline, strike, obf) -> {
                                                String plainNow = stripCodes(this.config.uniqueKeyName);
                                                if (!plainNow.isBlank()) {
                                                    this.config.uniqueKeyName = buildLegacy(plainNow, col, new boolean[]{bold, italic, underline, strike, obf});
                                                }
                                            }, () -> this.minecraft.setScreen(this)
                                        )
                                    );
                            }
                        }
                    )
                    .bounds(rx2 + nameBoxW + 6, rowY, styleBtnW, 16)
                    .build()
            );
            String modelLbl = "\u00a78Modelo: \u00a7f" + (sel != null ? sel.defaultName : "-");
            this.addLabel(this.font.plainSubstrByWidth(modelLbl, colW), rx2, rowY + 24, null);
            this.addLabel("\u00a78Se entrega al crear/dar la caja.", rx2, rowY + 38, null);
            this.addToggle(rx2, rowY + 52, colW, this.config.consumeKey ? "Consumir llave: S\u00ed" : "Consumir llave: No", this.config.consumeKey, () -> {
                this.config.consumeKey = !this.config.consumeKey;
                this.rebuildWidgets();
            }, desc("Si est\u00e1 activo, la llave se gasta al abrir."));
        }
    }

    private ItemStack previewKey(KeyModels.Entry e) {
        ItemStack s = new ItemStack(ModRegistry.uniqueKey());
        if (e != null) {
            s.getOrCreateTag().putInt("CustomModelData", e.cmd);
        }

        return s;
    }

    private void initSettings() {
        this.helpLine = "Izq: cooldown, anuncio, saltar y permiso. Der: tama\u00f1o, altura, orientaci\u00f3n (barritas) y apertura \u00fanica por jugador.";
        int x = this.bodyX();
        int y = this.bodyY();
        int colW = (this.bodyW() - 12) / 2;
        int fieldW = 56;
        this.addIntField(
            x + colW - fieldW,
            y,
            fieldW,
            this.config.cooldownSeconds,
            v -> this.config.cooldownSeconds = Math.max(0, v),
            "Cooldown por jugador (seg):",
            x,
            y + 4,
            desc("Espera individual para reabrir ESTA crate. 0 = sin cooldown.")
        );
        this.addSecondsField(
            x + colW - fieldW,
            y + 22,
            fieldW,
            this.config.openDelayTicks,
            v -> this.config.openDelayTicks = Math.max(0, v),
            "Retraso de apertura (seg):",
            x,
            y + 26,
            desc("Espera antifraude. 0 = inmediato.")
        );
        this.addToggle(x, y + 44, colW, this.config.broadcast ? "Anuncio global: Activado" : "Anuncio global: Desactivado", this.config.broadcast, () -> {
            this.config.broadcast = !this.config.broadcast;
            this.rebuildWidgets();
        }, desc("Anuncia a todo el servidor cuando alguien gana."));
        this.addToggle(x, y + 66, colW, this.config.allowSkip ? "Saltar con SHIFT: Permitido" : "Saltar con SHIFT: Bloqueado", this.config.allowSkip, () -> {
            this.config.allowSkip = !this.config.allowSkip;
            this.rebuildWidgets();
        }, desc("Permite saltar la animaci\u00f3n abriendo con SHIFT."));
        this.addLabel("Permiso requerido (opcional):", x, y + 92, desc("Nodo de permiso extra. Vac\u00edo = nada adicional."));
        EditBox perm = new EditBox(this.font, x, y + 104, colW, 16, Component.empty());
        perm.setMaxLength(64);
        perm.setValue(this.config.requiredPermission);
        perm.setHint(Component.literal("(opcional)"));
        perm.setResponder(s -> this.config.requiredPermission = s.trim());
        this.addRenderableWidget(perm);
        int tx = x + colW + 12;
        this.addLabel("\u00a7e\u2726 Tama\u00f1o y posici\u00f3n de la caja", tx, y, null);
        this.addSlider(
            tx,
            y + 12,
            colW,
            0.3,
            3.0,
            (double)this.config.sizeScale,
            2,
            "Tama\u00f1o",
            "x",
            v -> this.config.sizeScale = (float)v,
            desc("Escala de la caja (1.00x = tama\u00f1o por defecto).", "Arrastra la barrita. No deforma el modelo.")
        );
        this.addSlider(
            tx,
            y + 34,
            colW,
            -0.5,
            3.0,
            (double)this.config.yOffset,
            2,
            "Altura",
            " bloques",
            v -> this.config.yOffset = (float)v,
            desc("Sube o baja la caja (0.00 = sobre el bloque).", "\u00datil para flotarla o hundirla.")
        );
        this.addSlider(
            tx,
            y + 56,
            colW,
            0.0,
            360.0,
            (double)this.config.yawOffset,
            0,
            "Orientaci\u00f3n",
            "\u00b0",
            v -> this.config.yawOffset = (float)v,
            desc("Rotaci\u00f3n extra en grados sobre la direcci\u00f3n de colocaci\u00f3n.")
        );
        this.addRenderableWidget(Button.builder(Component.literal("\u00a77Restablecer tama\u00f1o/posici\u00f3n"), b -> {
            this.config.sizeScale = 1.0F;
            this.config.yOffset = 0.0F;
            this.config.yawOffset = 0.0F;
            this.rebuildWidgets();
        }).bounds(tx, y + 78, colW, 16).build());
        this.addToggle(
            tx,
            y + 100,
            colW,
            this.config.openOncePerPlayer ? "Apertura \u00fanica por jugador: S\u00ed" : "Apertura \u00fanica por jugador: No",
            this.config.openOncePerPlayer,
            () -> {
                this.config.openOncePerPlayer = !this.config.openOncePerPlayer;
                this.rebuildWidgets();
            },
            desc(
                "Si est\u00e1 activo, CADA jugador solo puede abrir",
                "esta caja UNA vez. Otros jugadores a\u00fan pueden",
                "abrirla su propia vez. (Se recuerda por caja colocada.)"
            )
        );
        this.addToggle(
            tx,
            y + 122,
            colW,
            this.config.showOdds ? "Mostrar probabilidades: S\u00ed" : "Mostrar probabilidades: No",
            this.config.showOdds,
            () -> {
                this.config.showOdds = !this.config.showOdds;
                this.rebuildWidgets();
            },
            desc(
                "Si est\u00e1 activado, los jugadores ver\u00e1n el % de probabilidad",
                "de cada item en la pantalla de pool de recompensas."
            )
        );
    }

    private void initVideos() {
        this.initMediaTab(
            this.config.videos,
            "Videos de la pantalla de pre-apertura",
            "V\u00eddeo",
            desc(
                "Links DIRECTOS de descarga (Google Drive, etc.).",
                "Formato recomendado: \u00a7fMP4 con v\u00eddeo H.264\u00a77.",
                "\u00a7cOJO: los .webm (VP8/VP9) no se pueden reproducir.",
                "Si hay varios, en cada apertura sale uno al azar."
            )
        );
    }

    private void initMusica() {
        this.initMediaTab(
            this.config.music,
            "M\u00fasica de la pantalla de pre-apertura",
            "Canci\u00f3n",
            desc(
                "Links DIRECTOS de descarga (Google Drive, etc.).",
                "Formatos soportados: \u00a7fMP3, OGG y WAV\u00a77.",
                "El audio del v\u00eddeo se ignora: la m\u00fasica sale de aqu\u00ed.",
                "Si hay varias, en cada apertura suena una al azar."
            )
        );
    }

    /**
     * Pestana de lista de URLs (compartida por Videos y Musica): lista
     * scrolleable + campo para anadir + eliminar + copiar al portapapeles.
     */
    private void initMediaTab(List<String> urls, String title, String noun, List<Component> tooltip) {
        this.helpLine = "A\u00f1ade links directos. Si la caja tiene media propia, NO se usa la del mod.";
        int x = this.bodyX();
        int y = this.bodyY();
        int w = this.bodyW();
        int listH = Math.max(40, this.bodyH() - 74);

        this.addLabel("\u00a7e\u2726 " + title + " \u00a77(" + urls.size() + ")", x, y, tooltip);

        // Sin icono: las URLs se alinean a la izquierda y se aprovecha el ancho.
        ScrollSelector<String> list = new ScrollSelector<>(
            x, y + 12, w, listH, 14, url -> url, url -> url, null
        );
        list.setItems(new ArrayList<>(urls));
        list.onSelect(url -> this.selectedMediaUrl = url);
        this.addRenderableWidget(list);

        int fieldY = y + 12 + listH + 4;
        EditBox input = new EditBox(this.font, x, fieldY, w - 96, 16, Component.empty());
        input.setMaxLength(512);
        input.setHint(Component.literal("\u00a78https://... (link directo)"));
        this.addRenderableWidget(input);

        this.addRenderableWidget(Button.builder(Component.literal("\u00a7aA\u00f1adir"), b -> {
            String value = input.getValue() == null ? "" : input.getValue().trim();
            if (value.isEmpty()) {
                return;
            }
            if (!MediaCache.isValidUrl(value)) {
                this.helpLine = "\u00a7cEse link no es v\u00e1lido: debe empezar por http:// o https://";
                return;
            }
            if (!urls.contains(value)) {
                urls.add(value);
            }
            input.setValue("");
            this.selectedMediaUrl = null;
            this.rebuildWidgets();
        }).bounds(x + w - 92, fieldY, 92, 16).build());

        int actionsY = fieldY + 20;
        int half = (w - 4) / 2;

        this.addRenderableWidget(
            Button.builder(
                    Component.literal(this.selectedMediaUrl == null ? "\u00a78Eliminar (elige uno)" : "\u00a7cEliminar seleccionado"),
                    b -> {
                        if (this.selectedMediaUrl != null) {
                            urls.remove(this.selectedMediaUrl);
                            this.selectedMediaUrl = null;
                            this.rebuildWidgets();
                        }
                    }
                )
                .bounds(x, actionsY, half, 16)
                .build()
        );

        this.addRenderableWidget(
            Button.builder(
                    Component.literal(this.selectedMediaUrl == null ? "\u00a78Copiar (elige uno)" : "\u00a7bCopiar al portapapeles"),
                    b -> {
                        if (this.selectedMediaUrl != null && this.minecraft != null) {
                            this.minecraft.keyboardHandler.setClipboard(this.selectedMediaUrl);
                            this.helpLine = "\u00a7aLink copiado al portapapeles.";
                        }
                    }
                )
                .bounds(x + half + 4, actionsY, half, 16)
                .build()
        );

        if (this.selectedMediaUrl != null) {
            this.addLabel(
                "\u00a77Seleccionado: \u00a7f" + this.font.plainSubstrByWidth(this.selectedMediaUrl, w - 90),
                x,
                actionsY + 20,
                null
            );
        } else {
            this.addLabel("\u00a78Haz click en un " + noun.toLowerCase(Locale.ROOT) + " de la lista para elegirlo.", x, actionsY + 20, null);
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String stripCodes(String s) {
        if (s == null) {
            return "";
        } else {
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ((c == '&' || c == 167) && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
                    i++;
                } else {
                    sb.append(c);
                }
            }

            return sb.toString();
        }
    }

    private static int colorOf(String s) {
        if (s != null) {
            for (int i = 0; i + 1 < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '&' || c == 167) {
                    ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(s.charAt(i + 1)));
                    if (f != null && f.isColor() && f.getColor() != null) {
                        return f.getColor();
                    }
                }
            }
        }

        return 16777215;
    }

    private static boolean[] flagsOf(String s) {
        boolean[] fl = new boolean[5];
        if (s != null) {
            for (int i = 0; i + 1 < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '&' || c == 167) {
                    switch (Character.toLowerCase(s.charAt(i + 1))) {
                        case 'k':
                            fl[4] = true;
                            break;
                        case 'l':
                            fl[0] = true;
                            break;
                        case 'm':
                            fl[3] = true;
                            break;
                        case 'n':
                            fl[2] = true;
                            break;
                        case 'o':
                            fl[1] = true;
                    }
                }
            }
        }

        return fl;
    }

    private static String buildLegacy(String plain, int rgb, boolean[] flags) {
        StringBuilder sb = new StringBuilder();
        sb.append('&').append(nearestColorChar(rgb));
        if (flags != null && flags.length >= 5) {
            if (flags[0]) {
                sb.append("&l");
            }

            if (flags[1]) {
                sb.append("&o");
            }

            if (flags[2]) {
                sb.append("&n");
            }

            if (flags[3]) {
                sb.append("&m");
            }

            if (flags[4]) {
                sb.append("&k");
            }
        }

        sb.append(plain == null ? "" : plain);
        return sb.toString();
    }

    private static char nearestColorChar(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        char best = 'f';
        long bestD = Long.MAX_VALUE;

        for (ChatFormatting f : ChatFormatting.values()) {
            if (f.isColor() && f.getColor() != null) {
                int col = f.getColor();
                int cr = col >> 16 & 0xFF;
                int cg = col >> 8 & 0xFF;
                int cb = col & 0xFF;
                long d = (long)((r - cr) * (r - cr)) + (long)((g - cg) * (g - cg)) + (long)((b - cb) * (b - cb));
                if (d < bestD) {
                    bestD = d;
                    best = f.getChar();
                }
            }
        }

        return best;
    }

    private static String cycleItemRarity(String current) {
        if (current != null && !current.isBlank()) {
            Rarity r = Rarity.byName(current);
            return r == Rarity.MYTHIC ? "" : r.next().name();
        } else {
            return Rarity.COMMON.name();
        }
    }

    private static List<Component> desc(String... lines) {
        ArrayList<Component> out = new ArrayList<>();

        for (String s : lines) {
            out.add(Component.literal(s));
        }

        return out;
    }

    private void addLabel(String text, int x, int y, List<Component> tooltip) {
        this.labels.add(new CrateEditorScreen.Label(text, x, y, 14737632));
        if (tooltip != null) {
            this.tooltipZones.add(new CrateEditorScreen.TooltipZone(x, y - 2, Math.max(200, this.font.width(text) + 8), 14, tooltip));
        }
    }

    private void addIntField(int x, int y, int w, int value, IntConsumer setter, String label, int labelX, int labelY, List<Component> tooltip) {
        EditBox box = new EditBox(this.font, x, y, w, 16, Component.empty());
        box.setMaxLength(10);
        box.setValue(Integer.toString(value));
        box.setResponder(s -> {
            try {
                setter.accept(Integer.parseInt(s.trim()));
            } catch (NumberFormatException var3x) {
            }
        });
        this.addRenderableWidget(box);
        if (label != null) {
            this.labels.add(new CrateEditorScreen.Label(label, labelX, labelY, 14737632));
            if (tooltip != null) {
                this.tooltipZones.add(new CrateEditorScreen.TooltipZone(labelX, labelY - 2, x + w - labelX, 14, tooltip));
            }
        }
    }

    private void addDoubleField(int x, int y, int w, double value, DoubleConsumer setter, String label, int labelX, int labelY, List<Component> tooltip) {
        EditBox box = new EditBox(this.font, x, y, w, 16, Component.empty());
        box.setMaxLength(8);
        box.setValue(fmt(value));
        box.setResponder(s -> {
            try {
                setter.accept(Double.parseDouble(s.trim()));
            } catch (NumberFormatException var3x) {
            }
        });
        this.addRenderableWidget(box);
        if (label != null) {
            this.labels.add(new CrateEditorScreen.Label(label, labelX, labelY, 14737632));
        }

        if (tooltip != null) {
            this.tooltipZones.add(new CrateEditorScreen.TooltipZone(x, y, w, 16, tooltip));
        }
    }

    private void addSecondsField(int x, int y, int w, int ticks, IntConsumer setterTicks, String label, int labelX, int labelY, List<Component> tooltip) {
        EditBox box = new EditBox(this.font, x, y, w, 16, Component.empty());
        box.setMaxLength(8);
        box.setValue(Long.toString(Math.round((double)ticks / 20.0)));
        box.setResponder(s -> {
            String t = s.trim();
            if (!t.isEmpty()) {
                try {
                    double seconds = Double.parseDouble(t);
                    setterTicks.accept((int)Math.round(Math.max(0.0, seconds) * 20.0));
                } catch (NumberFormatException var5x) {
                }
            }
        });
        this.addRenderableWidget(box);
        this.labels.add(new CrateEditorScreen.Label(label, labelX, labelY, 14737632));
        if (tooltip != null) {
            this.tooltipZones.add(new CrateEditorScreen.TooltipZone(labelX, labelY - 2, x + w - labelX, 14, tooltip));
        }
    }

    private void addSlider(
        int x, int y, int w, double min, double max, double value, int decimals, String label, String suffix, DoubleConsumer setter, List<Component> tooltip
    ) {
        this.addRenderableWidget(new CrateEditorScreen.FSSlider(x, y, w, min, max, value, decimals, label, suffix, setter));
        if (tooltip != null) {
            this.tooltipZones.add(new CrateEditorScreen.TooltipZone(x, y, w, 18, tooltip));
        }
    }

    private void addToggle(int x, int y, int w, String text, boolean state, Runnable onToggle, List<Component> tooltip) {
        String prefix = state ? "\u00a7a" : "\u00a77";
        this.addRenderableWidget(Button.builder(Component.literal(prefix + text), b -> onToggle.run()).bounds(x, y, w, 16).build());
        if (tooltip != null) {
            this.tooltipZones.add(new CrateEditorScreen.TooltipZone(x, y, w, 16, tooltip));
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.fill(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + this.panelHeight, -535291870);
        g.fill(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + 20, -14408646);
        g.fill(this.leftPos, this.topPos + this.panelHeight - 1, this.leftPos + this.panelWidth, this.topPos + this.panelHeight, -12961206);
        g.fill(this.leftPos + 6, this.topPos + 46, this.leftPos + this.panelWidth - 6, this.topPos + 47, -12961206);
        g.drawString(
            this.font,
            "\u00a7d\u2726 \u00a7fFantastic Crates \u00a7d\u2726 \u00a77- " + this.config.rarity.color() + this.config.rarity.displayName(),
            this.leftPos + 8,
            this.topPos + 6,
            16777215,
            false
        );
        if (this.helpLine != null && !this.helpLine.isEmpty()) {
            String trimmed = this.font.plainSubstrByWidth("\u00a77" + this.helpLine, this.panelWidth - 16);
            g.drawString(this.font, trimmed, this.leftPos + 8, this.topPos + 50, 10133680, false);
        }

        if (this.activeTab == CrateEditorScreen.Tab.PROBABILITY) {
            this.renderProbabilityBars(g);
        }

        super.render(g, mouseX, mouseY, partialTick);

        for (CrateEditorScreen.Label l : this.labels) {
            g.drawString(this.font, l.text(), l.x(), l.y(), l.color(), false);
        }

        for (CrateEditorScreen.TooltipZone z : this.tooltipZones) {
            if (mouseX >= z.x() && mouseX < z.x() + z.w() && mouseY >= z.y() && mouseY < z.y() + z.h()) {
                g.renderComponentTooltip(this.font, z.lines(), mouseX, mouseY);
                break;
            }
        }
    }

    private void renderProbabilityBars(GuiGraphics g) {
        int x = this.bodyX();
        int y = this.bodyY();
        List<Object> rows = this.probRows();
        int total = rows.size();
        if (total == 0) {
            g.drawString(this.font, "\u00a77No hay recompensas. A\u00f1\u00e1delas en Premios.", x, y, 9474192, false);
        } else {
            int maxBar = this.bodyW() - 250;
            int visibleRows = Math.max(1, this.bodyH() / 22);
            int maxScroll = Math.max(0, total - visibleRows);
            int scroll = Math.max(0, Math.min(this.probScroll, maxScroll));
            int end = Math.min(total, scroll + visibleRows);

            for (int i = scroll; i < end; i++) {
                Object row = rows.get(i);
                int ry = y + (i - scroll) * 22;
                if (row instanceof Rarity rar) {
                    int n = this.config.rewardCountForRarity(rar);
                    g.drawString(
                        this.font,
                        rar.color() + "\u25c6 " + rar.displayName().toUpperCase() + " \u00a78(" + n + " items en el pool)",
                        x,
                        ry + 5,
                        16777215,
                        false
                    );
                    g.fill(x, ry + 17, x + this.bodyW() - 16, ry + 18, 1090519039);
                } else {
                    RewardEntry r = (RewardEntry)row;
                    double pct = this.config.normalizedPercentInPool(r);
                    int barLen = (int)((double)maxBar * pct / 100.0);
                    int color = r.guaranteed ? -11141291 : -13800225;
                    String nameStr = this.font.plainSubstrByWidth(r.describe(), 132);
                    g.drawString(this.font, nameStr, x + 10, ry + 4, 14737632, false);
                    int barX = x + 210;
                    g.fill(barX, ry + 2, barX + Math.max(2, barLen), ry + 14, color);
                    String pctStr = r.guaranteed ? "\u00a7a100% fija" : fmt(pct);
                    g.drawString(this.font, pctStr, barX + Math.max(2, barLen) + 4, ry + 4, 16777215, false);
                }
            }

            if (maxScroll > 0) {
                int trackX = this.leftPos + this.panelWidth - 12;
                int trackH = visibleRows * 22;
                g.fill(trackX, y, trackX + 4, y + trackH, 1610612736);
                int thumbH = Math.max(10, trackH * visibleRows / total);
                int thumbY = y + (trackH - thumbH) * scroll / maxScroll;
                g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, -8355680);
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (super.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        } else {
            if (this.activeTab == CrateEditorScreen.Tab.PROBABILITY) {
                int total = this.probRows().size();
                int maxScroll = Math.max(0, total - Math.max(1, this.bodyH() / 22));
                if (maxScroll > 0) {
                    int before = this.probScroll;
                    this.probScroll = Math.max(0, Math.min(maxScroll, this.probScroll - (int)Math.signum(delta)));
                    if (this.probScroll != before) {
                        this.rebuildWidgets();
                    }

                    return true;
                }
            }

            return false;
        }
    }

    public boolean isPauseScreen() {
        return false;
    }

    private static final class FSSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final int decimals;
        private final String label;
        private final String suffix;
        private final DoubleConsumer setter;

        FSSlider(int x, int y, int w, double min, double max, double value, int decimals, String label, String suffix, DoubleConsumer setter) {
            super(x, y, w, 18, Component.empty(), max > min ? (value - min) / (max - min) : 0.0);
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.label = label;
            this.suffix = suffix;
            this.setter = setter;
            this.updateMessage();
        }

        private double current() {
            return this.min + this.value * (this.max - this.min);
        }

        protected void updateMessage() {
            this.setMessage(Component.literal(this.label + ": \u00a7a" + String.format(Locale.ROOT, "%." + this.decimals + "f", this.current()) + this.suffix));
        }

        protected void applyValue() {
            this.setter.accept(this.current());
        }
    }

    static record Label(String text, int x, int y, int color) {
    }

    private static enum Tab {
        INFO("Info"),
        REWARDS("Premios"),
        PROBABILITY("Prob."),
        APPEARANCE("Aspecto"),
        STYLE("Dise\u00f1o"),
        PARTICLES("Part."),
        KEY("Rarezas"),
        KEYMODEL("Llave"),
        SETTINGS("Ajustes"),
        VIDEOS("Videos"),
        MUSICA("M\u00fasica");

        final String label;

        private Tab(String label) {
            this.label = label;
        }
    }

    static record TooltipZone(int x, int y, int w, int h, List<Component> lines) {
    }
}
