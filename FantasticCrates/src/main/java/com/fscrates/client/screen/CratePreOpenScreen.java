package com.fscrates.client.screen;

import com.fscrates.client.color.FSText;
import com.fscrates.client.color.FSTextStyle;
import com.fscrates.client.media.CrateMedia;
import com.fscrates.client.widget.FSButton;
import com.fscrates.config.CrateConfig;
import com.fscrates.crate.LootEngine;
import com.fscrates.item.CrateItems;
import com.fscrates.network.FSNetwork;
import com.fscrates.network.RequestOpenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Sala de espera que se muestra ANTES de la cinematica de apertura.
 *
 * Fondo con video en bucle, musica, control de audio compacto arriba a la
 * derecha y dos botones: ABRIR (lanza la cinematica) y VER RECOMPENSAS (abre
 * {@link CratePoolScreen} encima sin cortar la reproduccion).
 *
 * La caja SOLO se abre con el boton: salir con ESC no la abre.
 */
public class CratePreOpenScreen extends Screen {
    /** Volumen en unidades enteras (0..N). Persiste entre aperturas. */
    static int masterVolume = 100;
    /** Silencio manual. Persiste entre aperturas. */
    static boolean muted = false;

    private static boolean volumeLoaded;

    /** Control de audio: un cuadradito y nada mas. */
    private static final int AUDIO_SIZE = 14;
    /**
     * Fundido de entrada: la escena aparece desde negro al hacer click en la caja,
     * en vez de saltar de golpe.
     */
    private static final float FADE_IN_MS = 900.0F;

    private final CrateConfig config;
    private final BlockPos pos;

    private boolean handedOff;
    private long openedAt;

    private int audioX;
    private int audioY;

    /** Los dos botones de abajo, escondidos hasta que la escena esta lista. */
    private FSButton openButton;
    private FSButton poolButton;

    /**
     * Tope de espera para mostrar la interfaz aunque la media no llegue.
     *
     * Los casos de fallo ya cuentan como listo, asi que esto solo salta si una
     * descarga se queda colgada sin dar error. Sin este tope el jugador se
     * quedaria mirando una pantalla sin botones.
     */
    private static final long UI_SAFETY_MS = 15_000L;

    /** Embed de confirmacion "¿consumir 1 llave?" visible. */
    private boolean confirming;
    private int confirmAcceptX;
    private int confirmRejectX;
    private int confirmButtonY;
    private int confirmButtonW;
    /** Aviso temporal (por ejemplo, que falta la llave). */
    private String notice = "";
    private long noticeUntil;

    public CratePreOpenScreen(CrateConfig config, BlockPos pos) {
        super(Component.literal("Pre-apertura"));
        this.config = config == null ? new CrateConfig() : config;
        this.pos = pos;
        loadVolumeState();
    }

    /** La llave que serviria para esta caja, o EMPTY si no hay ninguna. */
    private ItemStack usableKey() {
        return this.minecraft == null || this.minecraft.player == null
            ? ItemStack.EMPTY
            : CrateItems.findUsableKey(this.minecraft.player, this.config);
    }

    @Override
    protected void init() {
        if (this.openedAt == 0L) {
            this.openedAt = System.currentTimeMillis();
        }

        // La sesion de media es estatica: no se reinicia al redimensionar ni al
        // volver del pool de recompensas.
        if (!CrateMedia.isActive()) {
            CrateMedia.begin(this.config);
        }
        applyVolume();

        // Los dos botones con el MISMO ancho y el grupo centrado: queda simetrico.
        int buttonWidth = Math.max(
            120,
            Math.max(this.font.width("ABRIR"), this.font.width("VER RECOMPENSAS")) + 28
        );
        int gap = 10;
        int startX = (this.width - (buttonWidth * 2 + gap)) / 2;
        int buttonsY = this.height - 42;

        this.openButton = this.addRenderableWidget(
            new FSButton(
                startX,
                buttonsY,
                buttonWidth,
                20,
                Component.literal("ABRIR"),
                FSGui.ACCENT_GREEN,
                this::onOpenPressed
            )
        );

        this.poolButton = this.addRenderableWidget(
            new FSButton(
                startX + buttonWidth + gap,
                buttonsY,
                buttonWidth,
                20,
                Component.literal("VER RECOMPENSAS"),
                FSGui.ACCENT_BLUE,
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new CratePoolScreen(this.config, this));
                    }
                }
            )
        );

        // Arrancan escondidos: aparecen cuando la escena esta lista. Si se vuelve
        // del pool de recompensas la media ya estaba cargada, asi que se calcula
        // en el momento y no parpadean.
        boolean ready = this.sceneReady();
        this.openButton.visible = ready;
        this.poolButton.visible = ready;

        this.audioX = this.width - 8 - AUDIO_SIZE;
        this.audioY = 8;
    }

    /**
     * ABRIR: comprueba que haya llave y pide confirmacion antes de gastarla.
     * Si no hay llave no deja seguir.
     */
    private void onOpenPressed() {
        if (this.handedOff) {
            return;
        }

        if (this.usableKey().isEmpty()) {
            // Se dice el nombre exacto de la llave que falta.
            this.notice = "\u00a7cTe falta la llave: " + CrateItems.requiredKeyName(this.config);
            this.noticeUntil = System.currentTimeMillis() + 4000L;
            this.confirming = false;
            return;
        }

        // Si la caja no gasta llave, no hay nada que confirmar.
        if (!this.config.consumeKey) {
            this.requestOpen();
            return;
        }

        this.confirming = true;
    }

    /** Pide al servidor abrir de verdad. El servidor vuelve a validar todo. */
    private void requestOpen() {
        if (this.handedOff) {
            return;
        }
        this.handedOff = true;
        this.confirming = false;

        boolean skip = this.config.allowSkip && this.minecraft != null && this.minecraft.player != null
            && this.minecraft.player.isShiftKeyDown();

        CrateMedia.stop();
        FSNetwork.sendToServer(new RequestOpenPacket(this.pos, skip));
        // La cinematica llegara desde el servidor (PlayAnimationPacket).
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        CrateMedia.renderBackground(g, this.width, this.height);

        // Nada de interfaz hasta que el video este pintando y la musica cargada:
        // asi no se ven los botones y el texto flotando sobre el fondo negro
        // mientras se descarga.
        boolean ready = this.sceneReady();
        if (this.openButton != null) {
            this.openButton.visible = ready;
        }
        if (this.poolButton != null) {
            this.poolButton.visible = ready;
        }

        if (ready) {
            // Los degradados existen para que el texto se lea sobre cualquier
            // video, asi que sin texto tampoco pintan nada: solo serian dos
            // bandas oscuras encima de la imagen.
            g.fillGradient(0, 0, this.width, 56, 0xB0000000, 0x00000000);
            g.fillGradient(0, this.height - 76, this.width, this.height, 0x00000000, 0xC0000000);

            this.renderTexts(g);
            this.renderAudioControl(g, mouseX, mouseY);
        }

        if (CrateMedia.isLoading()) {
            this.renderLoadingOverlay(g);
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (this.confirming) {
            this.renderConfirm(g, mouseX, mouseY);
        }
        this.renderNotice(g);
        this.renderTransitions(g);
    }

    /** Embed sutil de confirmacion: "¿consumir 1 llave?" */
    private void renderConfirm(GuiGraphics g, int mouseX, int mouseY) {
        int boxW = Math.min(this.width - 40, 226);
        int boxH = 62;
        int x = (this.width - boxW) / 2;
        int y = (this.height - boxH) / 2;

        // Oscurece un poco el resto sin tapar la escena del todo.
        g.fill(0, 0, this.width, this.height, 0x66000000);
        FSGui.panel(g, x, y, boxW, boxH);

        g.drawCenteredString(this.font, "\u00a7f\u00bfConsumir 1 llave?", x + boxW / 2, y + 10, 0xFFFFFFFF);
        ItemStack key = this.usableKey();
        String keyName = key.isEmpty() ? "-" : key.getHoverName().getString();
        g.drawCenteredString(
            this.font,
            "\u00a77" + this.font.plainSubstrByWidth(keyName, boxW - 20),
            x + boxW / 2,
            y + 23,
            0xFFAAAAAA
        );

        int bw = (boxW - 30) / 2;
        int by = y + boxH - 26;
        this.confirmAcceptX = x + 10;
        this.confirmRejectX = x + 20 + bw;
        this.confirmButtonY = by;
        this.confirmButtonW = bw;

        drawMiniButton(g, this.font, this.confirmAcceptX, by, bw, "Aceptar", FSGui.ACCENT_GREEN, mouseX, mouseY);
        drawMiniButton(g, this.font, this.confirmRejectX, by, bw, "Rechazar", FSGui.ACCENT_RED, mouseX, mouseY);
    }

    private static void drawMiniButton(
        GuiGraphics g,
        net.minecraft.client.gui.Font font,
        int x,
        int y,
        int w,
        String label,
        int accent,
        int mouseX,
        int mouseY
    ) {
        int h = 18;
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hovered ? 0xFF9098A6 : 0xFF6E6E6E);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hovered ? 0xFFB6BECB : 0xFF8C8C8C);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF3F3F3F);
        if (hovered) {
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, accent);
        }
        g.drawCenteredString(font, label, x + w / 2, y + (h - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    /** Aviso temporal en la parte de abajo. */
    private void renderNotice(GuiGraphics g) {
        if (this.notice.isEmpty() || System.currentTimeMillis() > this.noticeUntil) {
            return;
        }
        int textWidth = this.font.width(this.notice);
        int x = (this.width - textWidth) / 2;
        int y = this.height - 74;
        g.fill(x - 6, y - 4, x + textWidth + 6, y + 12, 0xB0000000);
        g.drawString(this.font, this.notice, x, y, 0xFFFFFFFF, false);
    }

    /**
     * Fundido de entrada: al hacer click en la caja la escena se revela desde
     * negro poco a poco, en vez de aparecer de golpe.
     */
    private void renderTransitions(GuiGraphics g) {
        float elapsed = System.currentTimeMillis() - this.openedAt;
        if (elapsed >= FADE_IN_MS) {
            return;
        }

        float progress = elapsed / FADE_IN_MS;
        // Ease-out cubica: arranca oscuro y va aclarando cada vez mas despacio.
        float remaining = 1.0F - progress;
        float eased = remaining * remaining * remaining;
        int alpha = (int) (eased * 255.0F) & 0xFF;
        g.fill(0, 0, this.width, this.height, alpha << 24);
    }

    /**
     * Textos de la escena, todos configurables desde la pestana Mensajes del
     * editor: cabecera (encima del nombre), nombre de la crate, subtitulo y
     * lineas libres.
     */
    private void renderTexts(GuiGraphics g) {
        int centerX = this.width / 2;
        int y = 12;
        long now = System.currentTimeMillis();

        y = this.drawStyled(g, this.config.sceneHeader, centerX, y, now, 12);

        // El nombre de la caja: el texto sale de displayName y el estilo de
        // nameStyle, asi el campo del editor solo lleva texto.
        FSTextStyle name = this.config.nameStyle == null ? new FSTextStyle() : this.config.nameStyle.copy();
        name.text = LootEngine.colorize(this.config.displayName == null ? "" : this.config.displayName);
        if (!name.isBlank()) {
            g.drawCenteredString(this.font, name.toComponent(now), centerX, y, 0xFFFFFFFF);
            y += 13;
        }

        y = this.drawStyled(g, this.config.sceneSubtitle, centerX, y, now, 11);

        for (FSTextStyle line : this.config.sceneLines) {
            if (line == null || line.isBlank()) {
                y += 5;
                continue;
            }
            y = this.drawStyled(g, line, centerX, y, now, 10);
        }
    }

    /** Dibuja una linea de la escena con su estilo. */
    private int drawStyled(GuiGraphics g, FSTextStyle style, int centerX, int y, long now, int advance) {
        if (style == null || style.isBlank()) {
            return y;
        }
        g.drawCenteredString(this.font, style.toComponent(now), centerX, y, 0xFFFFFFFF);
        return y + advance;
    }

    /**
     * Control de audio compacto: solo un icono. La rueda del raton encima sube y
     * baja el volumen, y el click lo silencia. Sin numeros ni botones de mas.
     */
    private void renderAudioControl(GuiGraphics g, int mouseX, int mouseY) {
        boolean hovered = this.isOverAudio(mouseX, mouseY);
        int x = this.audioX;
        int y = this.audioY;

        g.fill(x, y, x + AUDIO_SIZE, y + AUDIO_SIZE, hovered ? 0xB0000000 : 0x70000000);
        if (hovered) {
            g.fill(x, y, x + AUDIO_SIZE, y + 1, 0x60FFFFFF);
            g.fill(x, y + AUDIO_SIZE - 1, x + AUDIO_SIZE, y + AUDIO_SIZE, 0x60FFFFFF);
            g.fill(x, y, x + 1, y + AUDIO_SIZE, 0x60FFFFFF);
            g.fill(x + AUDIO_SIZE - 1, y, x + AUDIO_SIZE, y + AUDIO_SIZE, 0x60FFFFFF);
        }

        // Nota musical, en gris cuando esta en silencio.
        String icon = muted ? "\u00a78\u266a" : "\u00a7f\u266a";
        g.drawCenteredString(this.font, icon, x + AUDIO_SIZE / 2, y + 3, 16777215);

        if (muted) {
            // Tachado en diagonal para que se lea "sin sonido" de un vistazo.
            for (int i = 2; i < AUDIO_SIZE - 2; i++) {
                g.fill(x + i, y + AUDIO_SIZE - 2 - i, x + i + 1, y + AUDIO_SIZE - 1 - i, 0xFFD5544F);
            }
            return;
        }

        // Nivel de volumen como una barrita fina debajo del icono.
        int trackWidth = AUDIO_SIZE - 4;
        int filled = Math.max(1, Math.min(trackWidth, Math.round(trackWidth * Math.min(masterVolume, 100) / 100.0F)));
        g.fill(x + 2, y + AUDIO_SIZE - 3, x + 2 + trackWidth, y + AUDIO_SIZE - 2, 0x60FFFFFF);
        g.fill(x + 2, y + AUDIO_SIZE - 3, x + 2 + filled, y + AUDIO_SIZE - 2, 0xFF4CC46A);
    }

    /** Aviso de carga discreto. No bloquea los botones. */
    private void renderLoadingOverlay(GuiGraphics g) {
        int boxW = 132;
        int boxH = 26;
        int x = (this.width - boxW) / 2;
        int y = this.height / 2 - boxH / 2;

        g.fill(x, y, x + boxW, y + boxH, 0xC0000000);
        g.fill(x, y, x + boxW, y + 1, 0x50FFFFFF);
        g.fill(x, y + boxH - 1, x + boxW, y + boxH, 0x50FFFFFF);

        long now = System.currentTimeMillis();
        int dots = (int) (now / 350L % 4L);
        StringBuilder text = new StringBuilder("Cargando media");
        for (int i = 0; i < dots; i++) {
            text.append('.');
        }
        g.drawCenteredString(this.font, "\u00a7f" + text, x + boxW / 2, y + 5, 16777215);

        int trackX = x + 12;
        int trackW = boxW - 24;
        int trackY = y + 17;
        g.fill(trackX, trackY, trackX + trackW, trackY + 3, 0x60000000);
        int sweepW = Math.max(12, trackW / 4);
        int offset = (int) (now / 6L % (trackW + sweepW)) - sweepW;
        int from = Math.max(trackX, trackX + offset);
        int to = Math.min(trackX + trackW, trackX + offset + sweepW);
        if (to > from) {
            g.fill(from, trackY, to, trackY + 3, 0xFF4AA8E0);
        }
    }

    // ------------------------------------------------------------------- input

    /**
     * true cuando ya se puede mostrar la interfaz.
     *
     * Es la media lista, o que se ha agotado la espera de seguridad.
     */
    private boolean sceneReady() {
        if (CrateMedia.isSceneReady()) {
            return true;
        }
        return this.openedAt != 0L && System.currentTimeMillis() - this.openedAt >= UI_SAFETY_MS;
    }

    private boolean isOverAudio(double mouseX, double mouseY) {
        // Escondido no se puede pulsar: si no, el control de volumen seguiria
        // respondiendo en una zona donde no se ve nada.
        if (!this.sceneReady()) {
            return false;
        }
        return mouseX >= this.audioX
            && mouseX < this.audioX + AUDIO_SIZE
            && mouseY >= this.audioY
            && mouseY < this.audioY + AUDIO_SIZE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // El embed de confirmacion se come los clicks mientras esta abierto.
        if (this.confirming) {
            if (button == 0 && mouseY >= this.confirmButtonY && mouseY < this.confirmButtonY + 18) {
                if (mouseX >= this.confirmAcceptX && mouseX < this.confirmAcceptX + this.confirmButtonW) {
                    this.requestOpen();
                    return true;
                }
                if (mouseX >= this.confirmRejectX && mouseX < this.confirmRejectX + this.confirmButtonW) {
                    this.confirming = false;
                    return true;
                }
            }
            return true;
        }

        if (button == 0 && this.isOverAudio(mouseX, mouseY)) {
            muted = !muted;
            applyVolume();
            saveVolumeState();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.isOverAudio(mouseX, mouseY) && delta != 0.0) {
            // Una unidad por tick de rueda. Minimo 0, sin maximo forzado.
            masterVolume = Math.max(0, masterVolume + (delta > 0.0 ? 1 : -1));
            applyVolume();
            saveVolumeState();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        // Con el embed abierto, ESC solo cancela la confirmacion.
        if (this.confirming) {
            this.confirming = false;
            return;
        }

        // ESC solo sale: la caja se abre unicamente con el boton ABRIR.
        CrateMedia.stop();
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ volumen

    private static void applyVolume() {
        CrateMedia.applyVolume(muted ? 0.0F : masterVolume / 100.0F);
    }

    private static Path volumeFile() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve("fscrates").resolve("media.properties");
        } catch (Throwable t) {
            return Path.of("config", "fscrates", "media.properties");
        }
    }

    private static synchronized void loadVolumeState() {
        if (volumeLoaded) {
            return;
        }
        volumeLoaded = true;
        try {
            Path file = volumeFile();
            if (Files.exists(file)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(file)) {
                    props.load(in);
                }
                masterVolume = Math.max(0, Integer.parseInt(props.getProperty("volumen", "100").trim()));
                muted = Boolean.parseBoolean(props.getProperty("silenciado", "false").trim().toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
            masterVolume = 100;
            muted = false;
        }
    }

    private static void saveVolumeState() {
        try {
            Path file = volumeFile();
            Files.createDirectories(file.getParent());
            Properties props = new Properties();
            props.setProperty("volumen", Integer.toString(masterVolume));
            props.setProperty("silenciado", Boolean.toString(muted));
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Fantastic Crates - audio de la pantalla de pre-apertura");
            }
        } catch (Exception ignored) {
        }
    }
}
