package com.fscrates.client.screen;

import com.fscrates.client.media.CrateMedia;
import com.fscrates.config.CrateConfig;
import com.fscrates.crate.LootEngine;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Sala de espera que se muestra ANTES de la cinematica de apertura.
 *
 * Fondo con video en bucle, musica de fondo, controles de audio arriba a la
 * derecha y dos botones: ABRIR (lanza la cinematica de siempre) y VER POOL DE
 * RECOMPENSAS (abre {@link CratePoolScreen} encima sin cortar la reproduccion).
 */
public class CratePreOpenScreen extends Screen {
    /** Volumen en unidades enteras (0..N). Persiste entre aperturas. */
    static int masterVolume = 100;
    /** Silencio manual. Persiste entre aperturas. */
    static boolean muted = false;

    private static boolean volumeLoaded;

    // Geometria de los controles de audio (para el hover y la rueda del raton).
    private static final int CONTROL_HEIGHT = 16;
    private static final int MUTE_WIDTH = 18;
    private static final int METER_WIDTH = 16;

    private final CrateConfig config;
    private final Runnable openAction;
    private boolean handedOff;

    private int muteX;
    private int muteY;
    private int volumeX;
    private int volumeWidth;

    public CratePreOpenScreen(CrateConfig config, Runnable openAction) {
        super(Component.literal("Pre-apertura"));
        this.config = config == null ? new CrateConfig() : config;
        this.openAction = openAction;
        loadVolumeState();
    }

    @Override
    protected void init() {
        // La sesion de media es estatica: no se reinicia al redimensionar ni al
        // volver del pool de recompensas.
        if (!CrateMedia.isActive()) {
            CrateMedia.begin(this.config);
        }
        applyVolume();

        int openWidth = 130;
        int poolWidth = 210;
        int gap = 8;
        int totalWidth = openWidth + gap + poolWidth;
        int startX = (this.width - totalWidth) / 2;
        int buttonsY = this.height - 46;

        this.addRenderableWidget(
            Button.builder(Component.literal("\u00a7a\u00a7l\u25b6 ABRIR"), b -> this.proceed())
                .bounds(startX, buttonsY, openWidth, 20)
                .build()
        );

        this.addRenderableWidget(
            Button.builder(
                    Component.literal("\u00a7bVER POOL DE RECOMPENSAS"),
                    b -> {
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new CratePoolScreen(this.config, this));
                        }
                    }
                )
                .bounds(startX + openWidth + gap, buttonsY, poolWidth, 20)
                .build()
        );

        // Controles de audio arriba a la derecha.
        int volumeTextWidth = Math.max(20, this.font.width(String.valueOf(Math.max(999, masterVolume))) + 6);
        this.volumeWidth = volumeTextWidth + 2 + METER_WIDTH;
        this.muteX = this.width - 8 - MUTE_WIDTH - 4 - this.volumeWidth;
        this.muteY = 8;
        this.volumeX = this.muteX + MUTE_WIDTH + 4;
    }

    /** Cierra la pre-apertura y lanza la cinematica (igual que antes de este cambio). */
    private void proceed() {
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
        g.fillGradient(0, 0, this.width, 60, -1442840576, 0);
        g.fillGradient(0, this.height - 80, this.width, this.height, 0, -1442840576);

        String name = LootEngine.colorize(this.config.displayName == null ? "" : this.config.displayName);
        g.drawCenteredString(this.font, name, this.width / 2, 22, 16777215);
        g.drawCenteredString(
            this.font,
            "\u00a77Prep\u00e1rate para abrir tu caja",
            this.width / 2,
            36,
            11184810
        );

        this.renderAudioControls(g, mouseX, mouseY);

        if (CrateMedia.isLoading()) {
            this.renderLoadingOverlay(g);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Icono de mute + valor numerico + medidor. */
    private void renderAudioControls(GuiGraphics g, int mouseX, int mouseY) {
        boolean overVolume = this.isOverVolume(mouseX, mouseY);

        // Fondo del grupo de controles.
        int groupX = this.muteX - 3;
        int groupW = this.width - 8 - groupX;
        g.fill(groupX, this.muteY - 3, groupX + groupW, this.muteY + CONTROL_HEIGHT + 3, overVolume ? -1873784752 : -1878982656);

        // Boton de mute.
        boolean overMute = mouseX >= this.muteX
            && mouseX < this.muteX + MUTE_WIDTH
            && mouseY >= this.muteY
            && mouseY < this.muteY + CONTROL_HEIGHT;
        g.fill(this.muteX, this.muteY, this.muteX + MUTE_WIDTH, this.muteY + CONTROL_HEIGHT, overMute ? 1157627903 : 570425344);
        String muteIcon = muted ? "\u00a7c\u266a" : "\u00a7a\u266a";
        g.drawCenteredString(this.font, muteIcon, this.muteX + MUTE_WIDTH / 2, this.muteY + 4, 16777215);
        if (muted) {
            // Tachado sobre el icono para que se vea que esta en silencio.
            g.fill(this.muteX + 3, this.muteY + CONTROL_HEIGHT / 2, this.muteX + MUTE_WIDTH - 3, this.muteY + CONTROL_HEIGHT / 2 + 1, -2354116);
        }

        // Valor numerico: atenuado y tachado cuando esta muteado.
        String value = (muted ? "\u00a78\u00a7m" : "\u00a7f") + masterVolume;
        g.drawString(this.font, value, this.volumeX, this.muteY + 4, 16777215, false);

        // Medidor de nivel (3 barritas) a la derecha del numero.
        int meterX = this.volumeX + this.volumeWidth - METER_WIDTH;
        int filled = muted ? 0 : Math.min(3, (masterVolume + 33) / 34);
        for (int i = 0; i < 3; i++) {
            int barHeight = 4 + i * 3;
            int barX = meterX + i * 5;
            int barY = this.muteY + CONTROL_HEIGHT - 2 - barHeight;
            g.fill(barX, barY, barX + 4, barY + barHeight, i < filled ? -5313024 : -12303292);
        }

        if (overVolume) {
            g.drawString(
                this.font,
                "\u00a77Rueda: \u00a7f\u00b11",
                groupX - 4 - this.font.width("Rueda: \u00b11"),
                this.muteY + 4,
                11184810,
                false
            );
        }
    }

    /**
     * Pantallita de carga discreta y centrada. No bloquea los botones: es solo
     * un aviso mientras la media se descarga en segundo plano.
     */
    private void renderLoadingOverlay(GuiGraphics g) {
        int boxW = 150;
        int boxH = 32;
        int x = (this.width - boxW) / 2;
        int y = this.height / 2 - boxH / 2;

        g.fill(x, y, x + boxW, y + boxH, -1878982656);
        g.fill(x, y, x + boxW, y + 1, -9408400);
        g.fill(x, y + boxH - 1, x + boxW, y + boxH, -9408400);

        long now = System.currentTimeMillis();
        int dots = (int) (now / 350L % 4L);
        StringBuilder text = new StringBuilder("Cargando media");
        for (int i = 0; i < dots; i++) {
            text.append('.');
        }
        g.drawCenteredString(this.font, "\u00a7f" + text, x + boxW / 2, y + 6, 16777215);

        // Barra indeterminada.
        int trackX = x + 12;
        int trackW = boxW - 24;
        int trackY = y + 20;
        g.fill(trackX, trackY, trackX + trackW, trackY + 4, -14671840);
        int sweepW = Math.max(12, trackW / 4);
        int offset = (int) (now / 6L % (trackW + sweepW)) - sweepW;
        int from = Math.max(trackX, trackX + offset);
        int to = Math.min(trackX + trackW, trackX + offset + sweepW);
        if (to > from) {
            g.fill(from, trackY, to, trackY + 4, -5313024);
        }
    }

    // ------------------------------------------------------------------- input

    private boolean isOverVolume(double mouseX, double mouseY) {
        // Solo el grupo icono + numero + medidor es sensible a la rueda.
        return mouseX >= this.muteX
            && mouseX <= this.volumeX + this.volumeWidth
            && mouseY >= this.muteY - 3
            && mouseY <= this.muteY + CONTROL_HEIGHT + 3;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
            && mouseX >= this.muteX
            && mouseX < this.muteX + MUTE_WIDTH
            && mouseY >= this.muteY
            && mouseY < this.muteY + CONTROL_HEIGHT) {
            muted = !muted;
            applyVolume();
            saveVolumeState();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.isOverVolume(mouseX, mouseY) && delta != 0.0) {
            // Exactamente 1 unidad por tick de rueda. Minimo 0, sin maximo forzado.
            int step = delta > 0.0 ? 1 : -1;
            masterVolume = Math.max(0, masterVolume + step);
            applyVolume();
            saveVolumeState();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        // ESC no debe hacer perder la revelacion: se comporta como ABRIR.
        this.proceed();
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
            return FMLPaths.CONFIGDIR.get().resolve("fscrates-media.properties");
        } catch (Throwable t) {
            return Path.of("config", "fscrates-media.properties");
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
                masterVolume = Math.max(0, Integer.parseInt(props.getProperty("masterVolume", "100").trim()));
                muted = Boolean.parseBoolean(props.getProperty("muted", "false").trim().toLowerCase(Locale.ROOT));
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
            props.setProperty("masterVolume", Integer.toString(masterVolume));
            props.setProperty("muted", Boolean.toString(muted));
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Fantastic Crates - ajustes de media de la pantalla de pre-apertura");
            }
        } catch (Exception ignored) {
        }
    }
}
