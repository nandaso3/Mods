package com.fscrates.client.screen;

import com.fscrates.client.media.CrateMedia;
import com.fscrates.client.widget.FSButton;
import com.fscrates.config.CrateConfig;
import com.fscrates.crate.LootEngine;
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
    /** Duracion del fundido de entrada, en milisegundos. */
    private static final float FADE_IN_MS = 260.0F;
    /** Duracion del fundido a negro antes de la cinematica. */
    private static final float FADE_OUT_MS = 420.0F;

    private final CrateConfig config;
    private final Runnable openAction;

    private boolean handedOff;
    private long openedAt;
    /** Momento en que se pulso ABRIR; 0 = todavia no. */
    private long fadeOutStartedAt;

    private int audioX;
    private int audioY;

    public CratePreOpenScreen(CrateConfig config, Runnable openAction) {
        super(Component.literal("Pre-apertura"));
        this.config = config == null ? new CrateConfig() : config;
        this.openAction = openAction;
        loadVolumeState();
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

        int openWidth = Math.max(110, this.font.width("ABRIR") + 60);
        int poolWidth = Math.max(130, this.font.width("VER RECOMPENSAS") + 40);
        int gap = 8;
        int startX = (this.width - (openWidth + gap + poolWidth)) / 2;
        int buttonsY = this.height - 44;

        this.addRenderableWidget(
            new FSButton(
                startX,
                buttonsY,
                openWidth,
                22,
                Component.literal("ABRIR"),
                FSGui.ACCENT_GREEN,
                this::proceed
            )
        );

        this.addRenderableWidget(
            new FSButton(
                startX + openWidth + gap,
                buttonsY,
                poolWidth,
                22,
                Component.literal("VER RECOMPENSAS"),
                FSGui.ACCENT_BLUE,
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new CratePoolScreen(this.config, this));
                    }
                }
            )
        );

        this.audioX = this.width - 8 - AUDIO_SIZE;
        this.audioY = 8;
    }

    /** Arranca el fundido a negro; la cinematica se abre al terminar. */
    private void proceed() {
        if (this.handedOff || this.fadeOutStartedAt != 0L) {
            return;
        }
        this.fadeOutStartedAt = System.currentTimeMillis();
    }

    /** Salto de verdad a la cinematica, ya con la pantalla en negro. */
    private void handOff() {
        if (this.handedOff) {
            return;
        }
        this.handedOff = true;
        CrateMedia.stop();
        if (this.openAction != null) {
            this.openAction.run();
        } else if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        CrateMedia.renderBackground(g, this.width, this.height);

        // Degradados arriba y abajo para que el texto se lea sobre cualquier video.
        g.fillGradient(0, 0, this.width, 56, 0xB0000000, 0x00000000);
        g.fillGradient(0, this.height - 76, this.width, this.height, 0x00000000, 0xC0000000);

        String name = LootEngine.colorize(this.config.displayName == null ? "" : this.config.displayName);
        g.drawCenteredString(this.font, name, this.width / 2, 20, 16777215);
        g.drawCenteredString(this.font, "\u00a77Prep\u00e1rate para abrir tu caja", this.width / 2, 33, 11184810);

        this.renderAudioControl(g, mouseX, mouseY);

        if (CrateMedia.isLoading()) {
            this.renderLoadingOverlay(g);
        }

        super.render(g, mouseX, mouseY, partialTick);

        this.renderTransitions(g);
    }

    /**
     * Fundido de entrada al abrir y fundido a negro al pulsar ABRIR, para que la
     * cinematica no aparezca de golpe.
     */
    private void renderTransitions(GuiGraphics g) {
        long now = System.currentTimeMillis();

        if (this.fadeOutStartedAt != 0L) {
            float progress = Math.min(1.0F, (now - this.fadeOutStartedAt) / FADE_OUT_MS);
            // Curva suave (ease-in) para que el oscurecimiento no sea lineal y seco.
            float eased = progress * progress;
            int alpha = (int) (eased * 255.0F) & 0xFF;
            g.fill(0, 0, this.width, this.height, alpha << 24);
            if (progress >= 1.0F) {
                this.handOff();
            }
            return;
        }

        float elapsed = now - this.openedAt;
        if (elapsed < FADE_IN_MS) {
            int alpha = (int) ((1.0F - elapsed / FADE_IN_MS) * 255.0F) & 0xFF;
            g.fill(0, 0, this.width, this.height, alpha << 24);
        }
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

    private boolean isOverAudio(double mouseX, double mouseY) {
        return mouseX >= this.audioX
            && mouseX < this.audioX + AUDIO_SIZE
            && mouseY >= this.audioY
            && mouseY < this.audioY + AUDIO_SIZE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
