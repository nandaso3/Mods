package com.fscrates.client.screen;

import com.fscrates.client.render.CrateBakedModels;
import com.fscrates.client.render.CrateStyles;
import com.fscrates.config.CrateConfig;
import com.fscrates.config.Rarity;
import com.fscrates.registry.ModRegistry;
import com.fscrates.util.CrateSfx;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class CrateCinematicScreen extends Screen {
    private static final ResourceLocation GLOW_TEX = new ResourceLocation("fscrates", "textures/gui/glow.png");
    private static final int TOTAL = 400;
    private static final int LAND = 24;
    private static final int LID_START = 56;
    private static final int BURST = 76;
    private static final int LID_END = 82;
    private static final int ROLL_START = 88;
    private static final int ROLL_END = 288;
    private static final int REVEAL = 294;
    private static final int REEL_EXTRA_LOOPS = 16;
    private static final float REEL_BREAK = 0.55F;
    private static final float LID_OPEN = 20.0F;
    private final CrateConfig cfg;
    private final int rarityColor;
    private final Rarity winnerRarity;
    private final int winnerIndex;
    private final List<ItemStack> candidates;
    private final int[] candidateRarities;
    private int ticks = 0;
    private boolean finished = false;
    private int[] reelStrip = null;
    private int reelLandingIndex = 0;
    private static boolean crateRenderFaulted = false;
    private int soundStage = 0;
    private int lastRiseTick = -100;
    private boolean peakPlayed = false;
    private int lastReelIndex = -1;
    private int winTick = -1;
    private boolean tailPlayed = false;
    private int lastSoundTick = -1;
    private int lastPulseTick = -100;
    private final CrateSfx.Sink sfxSink = (ev, vol, pitch) -> this.playUi(ev, pitch, vol);
    private boolean geomReady = false;
    private boolean cIsCineStyle = false;
    private BakedModel cBase;
    private BakedModel cLid;
    private BlockState cState;
    private float cBaseScale;
    private float cCenterY;
    private float cCenterX = 0.5F;
    private float cCenterZ = 0.5F;
    private float cScaledH;
    private float cPx;
    private float[] cHinge;
    private float cUnitPx;
    private long dbgLastNanos = 0L;
    private double dbgFrameMs = 0.0;
    private double dbgCrateMs = 0.0;
    private double dbgReelMs = 0.0;
    private double dbgFxMs = 0.0;
    private long lastTickNanos = 0L;
    private long openNanos = 0L;
    private float dbgPassedPT = 0.0F;
    private float dbgRealPT = 0.0F;
    private double dbgMaxFrameMs = 0.0;
    private float dbgTickDelta = 0.0F;

    public CrateCinematicScreen(CrateConfig cfg, int rarityColor, int winnerRarity, int winnerIndex, List<ItemStack> candidates, int[] candidateRarities) {
        super(Component.literal("Cinem\u00e1tica de cofre"));
        this.cfg = cfg == null ? new CrateConfig() : cfg;
        this.rarityColor = rarityColor;
        Rarity[] rv = Rarity.values();
        this.winnerRarity = rv[Math.max(0, Math.min(rv.length - 1, winnerRarity))];
        this.candidates = candidates;
        this.winnerIndex = candidates != null && !candidates.isEmpty() ? Math.max(0, Math.min(candidates.size() - 1, winnerIndex)) : 0;
        this.candidateRarities = candidateRarities == null ? new int[0] : candidateRarities;
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void tick() {
        this.ticks++;
        this.lastTickNanos = System.nanoTime();
        if (this.ticks >= 400 && !this.finished) {
            this.finished = true;
            this.onClose();
        }
    }

    private boolean canSkip() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(2);
    }

    public boolean shouldCloseOnEsc() {
        return this.canSkip();
    }

    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 32 || key == 257 || key == 256) && this.canSkip()) {
            this.onClose();
            return true;
        } else {
            return key == 256 ? true : super.keyPressed(key, scan, mods);
        }
    }

    private void playAtmosphere(int t) {
        switch (t) {
            case 2:
                this.playUi(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.3F);
                this.playUi(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.5F, 1.2F);
                break;
            case 24:
                this.playUi(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 0.7F);
                this.playUi(SoundEvents.GENERIC_BIG_FALL, 0.8F, 0.72F);
                break;
            case 56:
                this.playUi(SoundEvents.WOOD_HIT, 0.4F, 0.7F);
                break;
            case 68:
                this.playUi(SoundEvents.CONDUIT_ACTIVATE, 0.4F, 1.6F);
                this.playUi(SoundEvents.BEACON_POWER_SELECT, 0.4F, 1.7F);
        }
    }

    private void advanceRaritySounds(int t) {
        Rarity buildupRarity = this.winnerRarity;
        if (this.soundStage == 0 && t == 2) {
            CrateSfx.unlock(this.sfxSink, buildupRarity);
            this.soundStage = 1;
        }

        if (this.soundStage == 1 && t == 6) {
            CrateSfx.spiralCharge(this.sfxSink, buildupRarity);
        }

        float p;
        if (this.soundStage == 1
            && t > 6
            && t < 64
            && t - this.lastRiseTick >= Math.max(2, Math.round(10.0F - (p = Math.min(1.0F, (float)(t - 6) / (float)Math.max(1, 70))) * 8.0F))) {
            this.lastRiseTick = t;
            CrateSfx.spiralRise(this.sfxSink, buildupRarity, p);
            this.lastPulseTick = t;
        }

        if (this.soundStage == 1 && !this.peakPlayed && t >= 64) {
            this.peakPlayed = true;
            CrateSfx.spiralPeak(this.sfxSink, buildupRarity);
        }

        if (this.soundStage == 1 && t >= 76) {
            CrateSfx.openAccent(this.sfxSink, buildupRarity);
            this.lastPulseTick = t;
            this.soundStage = 2;
        }

        if (this.soundStage >= 2 && this.soundStage < 60) {
            if (t == 92 || t == 108 || t == 124) {
                float sp = Math.min(1.0F, (float)(t - 76) / 52.0F);
                CrateSfx.openSustain(this.sfxSink, this.winnerRarity, sp);
            } else if (t == 156) {
                this.playUi((SoundEvent)SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), 0.9F, 0.65F);
            } else if (t == 196) {
                this.playUi((SoundEvent)SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(), 0.88F, 0.7F);
            } else if (t == 236) {
                this.playUi((SoundEvent)SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), 0.85F, 0.75F);
            } else if (t == 272) {
                this.playUi((SoundEvent)SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(), 0.85F, 0.8F);
            }
        }

        if (t >= 294 && this.soundStage >= 2 && this.soundStage < 60) {
            CrateSfx.win(this.sfxSink, this.winnerRarity);
            this.soundStage = 60;
            this.winTick = t;
        }

        if (this.soundStage == 60 && !this.tailPlayed && t - this.winTick >= 4) {
            this.tailPlayed = true;
            CrateSfx.winTail(this.sfxSink, this.winnerRarity);
        }
    }

    private void playUi(SoundEvent ev, float pitch, float volume) {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
        }
    }

    private void updateReelClicks(float t) {
        if (this.candidates != null && !this.candidates.isEmpty() && !(t < 88.0F) && !(t >= 288.0F)) {
            float rp = Math.max(0.0F, Math.min(1.0F, (t - 88.0F) / 200.0F));
            int n = this.candidates.size();
            float maxTravel = this.reelTravelFast(n);
            int idx = (int)Math.floor((double)(reelPosFrac(rp) * maxTravel));
            if (this.lastReelIndex < 0) {
                this.lastReelIndex = idx;
            } else {
                if (idx > this.lastReelIndex) {
                    this.lastReelIndex = idx;
                    float pitch = 0.85F + rp * 0.45F;
                    this.playUi(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, pitch, 0.28F);
                }
            }
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long dbgNow = System.nanoTime();
        if (this.openNanos == 0L) {
            this.openNanos = dbgNow;
        }

        if (this.dbgLastNanos != 0L) {
            double dms = (double)(dbgNow - this.dbgLastNanos) / 1000000.0;
            this.dbgFrameMs = this.dbgFrameMs <= 0.0 ? dms : this.dbgFrameMs * 0.9 + dms * 0.1;
            this.dbgMaxFrameMs = dms > this.dbgMaxFrameMs ? dms : this.dbgMaxFrameMs * 0.96 + dms * 0.04;
        }

        this.dbgLastNanos = dbgNow;
        double dbgCrateRaw = 0.0;
        double dbgReelRaw = 0.0;
        double dbgFxRaw = 0.0;
        float t = (float)((double)(dbgNow - this.openNanos) / 5.0E7);
        this.dbgPassedPT = partialTick;
        this.dbgRealPT = t - (float)Math.floor((double)t);
        this.dbgTickDelta = t - (float)this.ticks;
        int soundTick = (int)Math.floor((double)t);

        while (this.lastSoundTick < soundTick) {
            this.lastSoundTick++;
            this.playAtmosphere(this.lastSoundTick);
            this.advanceRaritySounds(this.lastSoundTick);
        }

        int w = this.width;
        int h = this.height;
        int cx = w / 2;
        int cy = h / 2;
        int crateCY = cy + 26;
        int rouletteY = cy - 86;
        this.ensureGeom();
        this.updateReelClicks(t);
        this.renderSceneBackground(g, w, h, cx, crateCY, t);
        int shakeX = 0;
        int shakeY = 0;
        float amp = 0.0F;
        if (t >= 24.0F && t < 34.0F) {
            float d = t - 24.0F;
            amp = (1.0F - d / 10.0F) * 8.0F;
            shakeX = (int)(Math.sin((double)(d * 2.7F)) * (double)amp);
            shakeY = (int)(Math.cos((double)(d * 3.3F)) * (double)amp * 0.5);
        } else if (t >= 34.0F && t < 76.0F) {
            float rp = (t - 34.0F) / 42.0F;
            amp = 0.5F + rp * rp * 3.2F;
            shakeX = (int)(Math.sin((double)(t * 1.9F)) * (double)amp);
            shakeY = (int)(Math.cos((double)(t * 1.4F)) * (double)amp * 0.35);
        } else if (t >= 76.0F && t < 90.0F) {
            float d = t - 76.0F;
            amp = (1.0F - d / 14.0F) * 9.0F;
            shakeX = (int)(Math.sin((double)(d * 2.7F)) * (double)amp);
            shakeY = (int)(Math.cos((double)(d * 3.3F)) * (double)amp * 0.5);
        }

        long dbgS = System.nanoTime();
        if (!crateRenderFaulted && t < 294.0F) {
            g.flush();

            try {
                this.renderCrate(g, cx + shakeX, crateCY + shakeY, t);
            } catch (Throwable var29) {
                crateRenderFaulted = true;
                LogUtils.getLogger().error("[FSCrates] cinematic crate 3D render failed - disabling it for this session", var29);
            }
        }

        dbgCrateRaw += (double)(System.nanoTime() - dbgS) / 1000000.0;
        dbgS = System.nanoTime();
        PoseStack fxPose = g.pose();
        fxPose.pushPose();
        fxPose.translate(0.0F, 0.0F, 300.0F);
        if (t < 294.0F) {
            this.renderChargeFx(g, cx, crateCY, t);
            this.renderMouthGlow(g, cx, crateCY, t);
            this.renderSparks(g, cx, crateCY, t);
        }

        fxPose.popPose();
        dbgFxRaw += (double)(System.nanoTime() - dbgS) / 1000000.0;
        dbgS = System.nanoTime();
        if (this.candidates != null && !this.candidates.isEmpty()) {
            if (t >= 88.0F && t < 294.0F) {
                this.renderRoulettePanel(g, cx, rouletteY, w, t);
                this.renderRoulette(g, cx, rouletteY, w, t);
            } else if (t >= 294.0F) {
                this.renderRevealBurst(g, cx, crateCY, t - 294.0F);
                this.renderShockwaveRing(g, cx, crateCY, t - 294.0F);
                this.renderReveal(g, cx, cy, t);
            }
        }

        dbgReelRaw += (double)(System.nanoTime() - dbgS) / 1000000.0;
        float barsP = Math.min(1.0F, t / 8.0F);
        if (t > 390.0F) {
            barsP = Math.max(0.0F, (400.0F - t) / 10.0F);
        }

        int barH = (int)((float)h * 0.12F * barsP);
        g.fill(0, 0, w, barH, -16777216);
        g.fill(0, h - barH, w, h, -16777216);
    }

    private float rarityIntensity() {
        int n = Rarity.values().length - 1;
        return n <= 0 ? 0.0F : (float)this.winnerRarity.ordinal() / (float)n;
    }

    private void renderChargeFx(GuiGraphics g, int cx, int crateCY, float t) {
        if (!(t < 20.0F) && !(t >= 84.0F)) {
            int color = 16777215 & this.rarityColor;
            float rarityI = this.rarityIntensity();
            float u = this.cUnitPx > 0.0F ? this.cUnitPx : 40.0F;
            float cy = (float)crateCY - 4.0F;
            float charge = Math.max(0.0F, Math.min(1.0F, (t - 24.0F) / 52.0F));
            float chargeE = charge * charge;
            float pulse = this.lastPulseTick > 0 ? Math.max(0.0F, 1.0F - (t - (float)this.lastPulseTick) / 6.0F) : 0.0F;
            pulse *= pulse;
            float outFade = t <= 76.0F ? 1.0F : Math.max(0.0F, 1.0F - (t - 76.0F) / 8.0F);
            float auraA = (0.1F + 0.34F * chargeE) * (0.72F + 0.28F * pulse) * (0.85F + rarityI * 0.15F) * outFade;
            float auraSz = u * (1.6F + chargeE * 1.4F + pulse * 0.5F);
            drawGlowTex(g, (float)cx, cy, auraSz, auraSz * 0.85F, color, auraA);
            drawGlowTex(
                g,
                (float)cx,
                cy,
                u * (0.8F + chargeE * 0.7F + pulse * 0.4F),
                u * (0.7F + chargeE * 0.6F),
                16777202,
                (0.05F + 0.26F * chargeE) * (0.7F + 0.3F * pulse) * outFade
            );
            int inCount = 20 + Math.round(chargeE * 30.0F + rarityI * 14.0F);

            for (int i = 0; i < inCount; i++) {
                float seed = (float)i * 6.37F + 1.1F;
                float ang = frac((float)Math.sin((double)seed) * 4310.0F) * 6.2832F;
                float life = frac(
                    t * (0.012F + 0.02F * chargeE) * (0.6F + frac((float)Math.sin((double)(seed + 2.1F)) * 221.7F))
                        + frac((float)Math.sin((double)(seed + 5.3F)) * 137.9F)
                );
                float startR = u * (2.4F + frac((float)Math.sin((double)(seed + 3.7F)) * 920.0F) * 1.6F);
                float rad = startR * (1.0F - life);
                float a = life * (1.0F - life) * 4.0F;
                if (!(a <= 0.03F)) {
                    float x = (float)cx + (float)Math.cos((double)ang) * rad;
                    float y = cy + (float)Math.sin((double)ang) * rad * 0.7F;
                    int col = i % 4 == 0 ? 16777202 : color;
                    drawSoftDot(g, x, y, 1.0F + life * 1.6F, col, a * (0.26F + 0.4F * chargeE) * outFade);
                }
            }

            if (this.lastPulseTick > 0) {
                float since = t - (float)this.lastPulseTick;
                if (since >= 0.0F && since < 7.0F) {
                    float pr = since / 7.0F;
                    float ring = u * (0.6F + pr * 2.2F);
                    float ra = (1.0F - pr) * (0.32F + 0.3F * chargeE);
                    int rdots = 24;

                    for (int ix = 0; ix < rdots; ix++) {
                        float ang = (float)((double)ix * ((Math.PI * 2) / (double)rdots));
                        float x = (float)cx + (float)Math.cos((double)ang) * ring;
                        float y = cy + (float)Math.sin((double)ang) * ring * 0.7F;
                        drawSoftDot(g, x, y, 1.6F - pr * 0.9F, color, ra * 0.7F * outFade);
                    }
                }
            }
        }
    }

    private void renderMouthGlow(GuiGraphics g, int cx, int crateCY, float t) {
        if (!(t < 56.0F) && !(this.cUnitPx <= 0.0F)) {
            float open = Math.min(1.0F, (t - 56.0F) / 26.0F);
            float fade = t >= 294.0F ? Math.max(0.0F, 1.0F - (t - 294.0F) / 10.0F) : 1.0F;
            if (!(fade <= 0.02F)) {
                int color = 16777215 & this.rarityColor;
                float rarityI = this.rarityIntensity();
                float crateScreenH = this.cScaledH * this.cPx;
                float mouthY = (float)crateCY - crateScreenH * 0.2F;
                float pulse = 0.9F + 0.1F * (float)Math.sin((double)t * 0.35) + (0.03F + rarityI * 0.05F) * (float)Math.sin((double)t * 1.15 + 1.7);
                float a = open * fade * pulse;
                float originY = mouthY - crateScreenH * 0.12F;
                float u = this.cUnitPx;
                int count = 48 + Math.round(rarityI * 36.0F);

                for (int i = 0; i < count; i++) {
                    float seed = (float)i * 9.17F + 3.0F;
                    float rx = frac((float)Math.sin((double)seed) * 43758.547F);
                    float spd = 0.45F + frac((float)Math.sin((double)(seed + 1.3F)) * 22578.11F) * 0.7F;
                    float phase;
                    float life = frac((t - 56.0F) * 0.02F * spd + (phase = frac((float)Math.sin((double)(seed + 3.1F)) * 13795.77F)));
                    float env = smoothstep(0.0F, 0.22F, life) * (1.0F - smoothstep(0.72F, 1.0F, life));
                    float pa = env * open * fade;
                    if (!(pa <= 0.04F)) {
                        float riseEase = 1.0F - (1.0F - life) * (1.0F - life);
                        float rise = riseEase * (crateScreenH * 1.05F + u * 0.4F);
                        float swirl = life * 5.5F + phase * 6.2832F;
                        float swirlR = u * (0.06F + life * 0.3F) * (0.7F + rarityI * 0.5F);
                        float x = (float)cx + (rx - 0.5F) * u * 0.9F + (float)Math.sin((double)swirl) * swirlR;
                        float y = originY - rise;
                        float size = u * (0.024F + rarityI * 0.012F + frac((float)Math.sin((double)(seed + 7.7F)) * 5678.1F) * 0.016F) * (0.75F + 0.5F * env);
                        int col = i % 3 == 0 ? 16777202 : color;
                        drawSoftDot(g, x, y, size, col, pa * 0.7F);
                    }
                }
            }
        }
    }

    private static float smoothstep(float e0, float e1, float x) {
        if (e0 == e1) {
            return x < e0 ? 0.0F : 1.0F;
        } else {
            float u = (x - e0) / (e1 - e0);
            if (u < 0.0F) {
                u = 0.0F;
            }

            if (u > 1.0F) {
                u = 1.0F;
            }

            return u * u * (3.0F - 2.0F * u);
        }
    }

    private static void drawTriangleFill(GuiGraphics g, float ax, float ay, float bx, float by, float cx2, float cy2, int color) {
        int steps = 5;

        for (int i = 0; i < steps; i++) {
            float t0 = (float)i / (float)steps;
            float t1 = (float)(i + 1) / (float)steps;
            float x0a = ax + (cx2 - ax) * t0;
            float y0a = ay + (cy2 - ay) * t0;
            float x0b = bx + (cx2 - bx) * t0;
            float y0b = by + (cy2 - by) * t0;
            float x1a = ax + (cx2 - ax) * t1;
            float y1a = ay + (cy2 - ay) * t1;
            float x1b = bx + (cx2 - bx) * t1;
            float y1b = by + (cy2 - by) * t1;
            int minX = (int)Math.floor((double)Math.min(Math.min(x0a, x0b), Math.min(x1a, x1b)));
            int maxX = (int)Math.ceil((double)Math.max(Math.max(x0a, x0b), Math.max(x1a, x1b)));
            int minY = (int)Math.floor((double)Math.min(Math.min(y0a, y0b), Math.min(y1a, y1b)));
            int maxY = (int)Math.ceil((double)Math.max(Math.max(y0a, y0b), Math.max(y1a, y1b)));
            if (maxX > minX && maxY > minY) {
                g.fill(minX, minY, maxX, maxY, color);
            }
        }
    }

    private void renderRoulettePanel(GuiGraphics g, int cx, int cy, int w, float t) {
        float in = Math.min(1.0F, (t - 88.0F) / 6.0F);
        int half = (int)((float)(w / 2) * in);
        int top = cy - 30;
        int bot = cy + 30;
        int rgb = 16777215 & this.rarityColor;
        int rc = 0xFF000000 | rgb;
        float appear = Math.min(1.0F, Math.max(0.0F, (t - 88.0F) / 16.0F));
        float flash = t < 106.0F ? Math.max(0.0F, 1.0F - (t - 88.0F) / 18.0F) : 0.0F;
        if (flash > 0.01F) {
            drawGlowTex(g, (float)cx, (float)cy, (float)(half + 46) * 2.0F, 130.0F, rgb, flash * 0.5F);
            drawGlowTex(g, (float)cx, (float)cy, (float)(half + 12) * 2.0F, 74.0F, mix(rgb, 16777215, 0.5F), flash * 0.42F);
        }

        g.fill(cx - half, top, cx + half, bot, -972617454);
        int bcol = flash > 0.01F ? 0xFF000000 | mix(rgb, 16777215, Math.min(0.85F, flash * 0.9F)) : rc;
        g.fill(cx - half, top, cx + half, top + 2, bcol);
        g.fill(cx - half, bot - 2, cx + half, bot, bcol);
        if (appear < 1.0F && half > 4) {
            float sweepX = (float)(cx - half) + (float)(2 * half) * appear;
            drawGlowTex(g, sweepX, (float)cy, 42.0F, 78.0F, 16777215, (1.0F - appear) * 0.6F);
        }

        int mw = 24;
        g.fill(cx - mw, top - 3, cx - mw + 2, bot + 3, rc);
        g.fill(cx + mw - 2, top - 3, cx + mw, bot + 3, rc);
        g.fill(cx - 1, top - 6, cx + 1, top, -1);
        g.fill(cx - 1, bot, cx + 1, bot + 6, -1);
        if (flash > 0.01F) {
            drawGlowTex(g, (float)cx, (float)cy, 58.0F, 92.0F, mix(rgb, 16777215, 0.6F), flash * 0.5F);
        }
    }

    private void ensureGeom() {
        if (!this.geomReady) {
            this.geomReady = true;
            CrateStyles.Style style = this.cfg == null ? null : CrateStyles.get(this.cfg.styleId);
            this.cIsCineStyle = style != null && style.isCinematic();
            this.cBaseScale = CrateBakedModels.scaleFor(this.cfg) * Math.max(0.05F, this.cfg.sizeScale);
            this.cState = ModRegistry.CRATE_BLOCK.get().defaultBlockState();
            this.cBase = CrateBakedModels.baseModel(this.cfg);
            this.cLid = CrateBakedModels.lidModel(this.cfg);
            this.cHinge = CrateBakedModels.hingeFor(this.cfg);
            float[] xz = modelXZCenter(this.cBase, this.cState);
            this.cCenterX = xz[0];
            this.cCenterZ = xz[1];
            float[] yr = modelYRange(this.cBase, this.cLid, this.cState);
            float rawCentre = (yr[0] + yr[1]) * 0.5F;
            float rawHeight = Math.max(0.1F, yr[1] - yr[0]);
            this.cCenterY = this.cBaseScale * rawCentre;
            this.cScaledH = this.cBaseScale * rawHeight;
            float target = (float)this.height * 0.3F;
            this.cPx = Math.max(1.0F, target / this.cScaledH);
            this.cUnitPx = this.cPx * this.cBaseScale;
        }
    }

    private void renderCrate(GuiGraphics g, int cx, int cy, float t) {
        Minecraft mc = Minecraft.getInstance();
        this.ensureGeom();
        float baseScale = this.cBaseScale;
        BlockState state = this.cState;
        BakedModel base = this.cBase;
        BakedModel lidModel = this.cLid;
        float centerY = this.cCenterY;
        float scaledH = this.cScaledH;
        float px = this.cPx;
        float dropUnits;
        if (t < 24.0F) {
            float p = t / 24.0F;
            dropUnits = 3.4F * scaledH * (1.0F - p * p);
        } else {
            float b = t - 24.0F;
            dropUnits = (float)Math.abs(Math.sin((double)b * 0.5)) * 0.1F * scaledH * (float)Math.exp(-0.18 * (double)b);
        }

        float lid;
        if (t < 56.0F) {
            lid = 0.0F;
        } else if (t < 82.0F) {
            float p = (t - 56.0F) / 26.0F;
            lid = p * p * 22.0F;
        } else {
            lid = 22.0F;
        }

        if (this.lastPulseTick > 0 && t < 82.0F) {
            float throb = Math.max(0.0F, 1.0F - (t - (float)this.lastPulseTick) / 6.0F);
            px *= 1.0F + throb * throb * 0.05F;
        }

        float yaw = 180.0F + (this.cIsCineStyle ? 180.0F : 0.0F);
        float pitch = 26.0F;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate((double)cx, (double)cy, 250.0);
        pose.scale(px, -px, px);
        pose.translate(0.0F, dropUnits, 0.0F);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5F, -centerY, -0.5F);
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.scale(baseScale, baseScale, baseScale);
        pose.translate(-this.cCenterX, 0.0F, -this.cCenterZ);
        Lighting.setupForFlatItems();
        RenderSystem.enableDepthTest();
        BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.cutout());
        ModelBlockRenderer mr = mc.getBlockRenderer().getModelRenderer();
        int fullBright = LightTexture.pack(15, 15);
        mr.renderModel(pose.last(), vc, state, base, 1.0F, 1.0F, 1.0F, fullBright, OverlayTexture.NO_OVERLAY);
        if (lidModel != null) {
            float[] hinge = this.cHinge;
            float pivotZ = this.cIsCineStyle ? 1.0F - hinge[2] : hinge[2];
            float lidRot = this.cIsCineStyle ? -lid : lid;
            pose.pushPose();
            pose.translate(hinge[0], hinge[1], pivotZ);
            pose.mulPose(Axis.XP.rotationDegrees(lidRot));
            pose.translate(-hinge[0], -hinge[1], -pivotZ);
            mr.renderModel(pose.last(), vc, state, lidModel, 1.0F, 1.0F, 1.0F, fullBright, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }

        buf.endBatch();
        pose.popPose();
        Lighting.setupFor3DItems();
    }

    private static float[] modelXZCenter(BakedModel base, BlockState state) {
        if (base == null) {
            return new float[]{0.5F, 0.5F};
        } else {
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            RandomSource rnd = RandomSource.create(42L);
            Direction[] sides = new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

            for (Direction side : sides) {
                for (BakedQuad q : base.getQuads(state, side, rnd)) {
                    int[] verts = q.getVertices();
                    int stride = verts.length / 4;

                    for (int i = 0; i < 4; i++) {
                        float x = Float.intBitsToFloat(verts[i * stride]);
                        float z = Float.intBitsToFloat(verts[i * stride + 2]);
                        if (x < minX) {
                            minX = x;
                        }

                        if (x > maxX) {
                            maxX = x;
                        }

                        if (z < minZ) {
                            minZ = z;
                        }

                        if (z > maxZ) {
                            maxZ = z;
                        }
                    }
                }
            }

            return minX > maxX ? new float[]{0.5F, 0.5F} : new float[]{(minX + maxX) * 0.5F, (minZ + maxZ) * 0.5F};
        }
    }

    private static float[] modelYRange(BakedModel base, BakedModel lid, BlockState state) {
        float[] yr = new float[]{Float.MAX_VALUE, -Float.MAX_VALUE};
        accumY(base, state, yr);
        accumY(lid, state, yr);
        return yr[0] > yr[1] ? new float[]{0.0F, 1.0F} : yr;
    }

    private static void accumY(BakedModel model, BlockState state, float[] yr) {
        if (model != null) {
            RandomSource rnd = RandomSource.create(42L);
            ArrayList<BakedQuad> quads = new ArrayList<>(model.getQuads(state, null, rnd));

            for (Direction d : Direction.values()) {
                quads.addAll(model.getQuads(state, d, rnd));
            }

            for (BakedQuad q : quads) {
                int[] v = q.getVertices();
                int stride = v.length / 4;

                for (int i = 0; i < 4; i++) {
                    float y = Float.intBitsToFloat(v[i * stride + 1]);
                    if (y < yr[0]) {
                        yr[0] = y;
                    }

                    if (y > yr[1]) {
                        yr[1] = y;
                    }
                }
            }
        }
    }

    private float reelTravelFast(int n) {
        return 165.0F;
    }

    private void ensureReelStrip() {
        if (this.reelStrip == null) {
            int n = this.candidates == null ? 0 : this.candidates.size();
            if (n <= 0) {
                this.reelStrip = new int[0];
            } else {
                int landing = (int)Math.floor((double)this.reelTravelFast(n));
                int len = landing + 8;
                int[] strip = new int[len];
                Random rnd = new Random(24301L + (long)this.winnerIndex * 31L + (long)n * 131L);
                int prev = -1;

                for (int i = 0; i < len; i++) {
                    int v = rnd.nextInt(n);
                    if (n > 1 && v == prev) {
                        v = (v + 1 + rnd.nextInt(n - 1)) % n;
                    }

                    strip[i] = v;
                    prev = v;
                }

                int win = Math.max(0, Math.min(n - 1, this.winnerIndex));
                if (landing >= 0 && landing < len) {
                    strip[landing] = win;
                    if (n > 1) {
                        if (landing - 1 >= 0 && strip[landing - 1] == win) {
                            int far = landing - 2 >= 0 ? strip[landing - 2] : -1;
                            strip[landing - 1] = otherThan(n, win, far);
                        }

                        if (landing + 1 < len && strip[landing + 1] == win) {
                            int far = landing + 2 < len ? strip[landing + 2] : -1;
                            strip[landing + 1] = otherThan(n, win, far);
                        }
                    }
                }

                if (n > 1 && len > 1 && strip[len - 1] == strip[0]) {
                    strip[len - 1] = otherThan(n, strip[0], strip[len - 2]);
                }

                this.reelLandingIndex = landing;
                this.reelStrip = strip;
            }
        }
    }

    private static int otherThan(int n, int a, int b) {
        for (int k = 0; k < n; k++) {
            if (k != a && k != b) {
                return k;
            }
        }

        for (int kx = 0; kx < n; kx++) {
            if (kx != a) {
                return kx;
            }
        }

        return a;
    }

    private static float reelPosFrac(float p) {
        p = Math.max(0.0F, Math.min(1.0F, p));
        float fastTime = 0.5F;
        float fastShare = 0.8F;
        if (p <= fastTime) {
            return fastShare * (p / fastTime);
        } else {
            float local = (p - fastTime) / (1.0F - fastTime);
            float x = 1.0F - local;
            float easeOutCubic = 1.0F - x * x * x;
            return fastShare + (1.0F - fastShare) * easeOutCubic;
        }
    }

    private void renderRoulette(GuiGraphics g, int cx, int cy, int w, float t) {
        this.ensureReelStrip();
        if (this.reelStrip != null && this.reelStrip.length != 0) {
            int n = this.candidates.size();
            int stripLen = this.reelStrip.length;
            float p = Math.max(0.0F, Math.min(1.0F, (t - 88.0F) / 200.0F));
            float maxTravel = this.reelTravelFast(n);
            float centerPos = reelPosFrac(p) * maxTravel;
            int baseIdx = (int)Math.floor((double)centerPos);
            float frac = centerPos - (float)baseIdx;
            float slotW = 48.0F;
            float centerScale = 1.7F;
            int top = cy - 30;
            int bot = cy + 30;
            int kMax = (int)Math.ceil((double)((float)(w / 2 + 48) / slotW)) + 1;
            PoseStack pose = g.pose();

            for (int k = -kMax; k <= kMax; k++) {
                int stripPos = Math.floorMod(baseIdx + k, stripLen);
                int idx = this.reelStrip[stripPos];
                float off = ((float)k - frac) * slotW;
                float ax = (float)cx + off;
                if (!(ax < -slotW) && !(ax > (float)w + slotW)) {
                    float dist = Math.abs(off) / slotW;
                    float scale = centerScale * Math.max(0.55F, 1.0F - dist * 0.045F);
                    ItemStack st = this.candidates.get(idx);
                    if (st != null && !st.isEmpty()) {
                        pose.pushPose();
                        pose.translate(ax, (float)cy, 0.0F);
                        pose.scale(scale, scale, 1.0F);
                        g.renderItem(st, -8, -8);
                        pose.popPose();
                    }
                }
            }

            int fadeW = Math.max(40, w / 7);
            int steps = 12;

            for (int i = 0; i < steps; i++) {
                float f = 1.0F - (float)i / (float)steps;
                int a = (int)(198.0F * f * f) << 24;
                int x0 = i * fadeW / steps;
                int x1 = (i + 1) * fadeW / steps + 1;
                g.fill(x0, top, x1, bot, a);
                g.fill(w - x1, top, w - x0, bot, a);
            }
        }
    }

    private void renderReveal(GuiGraphics g, int cx, int cy, float t) {
        ItemStack win = this.candidates.get(this.winnerIndex);
        if (win != null && !win.isEmpty()) {
            float since = t - 294.0F;
            float pop = Math.min(1.0F, since / 6.0F);
            float scale = 2.7F + (1.0F - (1.0F - pop) * (1.0F - pop)) * 0.8F;
            int iy = cy - 2;
            float flashA = Math.max(0.0F, 1.0F - since / 4.0F);
            if (flashA > 0.02F) {
                int fa = (int)(flashA * flashA * 235.0F) << 24;
                g.fill(0, 0, this.width, this.height, fa | 16777215);
            }

            this.renderRevealBurst(g, cx, iy, since);
            int rc = 16777215 & this.winnerRarity.rgb();
            float ringA = Math.max(0.0F, 1.0F - since / 10.0F) * 0.6F;
            float halo = 1.0F + (1.0F - (1.0F - pop) * (1.0F - pop)) * 0.3F;
            drawRadialGlow(g, (float)cx, (float)iy, 95.0F * halo, 95.0F * halo, rc, ringA);
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate((float)cx, (float)iy, 0.0F);
            pose.scale(scale, scale, 1.0F);
            g.renderItem(win, -8, -8);
            pose.popPose();
            String name = win.getHoverName().getString();
            g.drawCenteredString(this.font, "\u00a77Has recibido", cx, cy + 36, -1);
            g.drawCenteredString(this.font, "\u00a7l" + name, cx, cy + 48, this.winnerRarity.rgb() | 0xFF000000);
            g.drawCenteredString(this.font, this.winnerRarity.color() + this.winnerRarity.displayName(), cx, cy + 62, -1);
        }
    }

    private static float frac(float x) {
        return x - (float)Math.floor((double)x);
    }

    private static int color(int rgb) {
        return 16777215 & rgb;
    }

    private static int mix(int base, int rgb, float f) {
        if (f < 0.0F) {
            f = 0.0F;
        }

        if (f > 1.0F) {
            f = 1.0F;
        }

        int br = base >> 16 & 0xFF;
        int bg = base >> 8 & 0xFF;
        int bb = base & 0xFF;
        int rr = rgb >> 16 & 0xFF;
        int rg = rgb >> 8 & 0xFF;
        int rb = rgb & 0xFF;
        int or = (int)((float)br + ((float)rr - (float)br) * f);
        int og = (int)((float)bg + ((float)rg - (float)bg) * f);
        int ob = (int)((float)bb + ((float)rb - (float)bb) * f);
        return (or & 0xFF) << 16 | (og & 0xFF) << 8 | ob & 0xFF;
    }

    private static void fillEllipse(GuiGraphics g, float ecx, float ecy, float rx, float ry, int argb, int step) {
        if (!(rx < 0.6F) && !(ry < 0.6F)) {
            if (step < 1) {
                step = 1;
            }

            int y0 = (int)Math.floor((double)(ecy - ry));
            int y1 = (int)Math.ceil((double)(ecy + ry));

            for (int y = y0; y < y1; y += step) {
                float vy = ((float)y + 0.5F * (float)step - ecy) / ry;
                if (!(vy < -1.0F) && !(vy > 1.0F)) {
                    float half = rx * (float)Math.sqrt(Math.max(0.0, 1.0 - (double)(vy * vy)));
                    int x0 = Math.round(ecx - half);
                    int x1 = Math.round(ecx + half);
                    if (x1 > x0) {
                        g.fill(x0, y, x1, y + step, argb);
                    }
                }
            }
        }
    }

    private static void drawGlowTex(GuiGraphics g, float cx, float cy, float w, float h, int rgb, float alpha) {
        if (!(alpha <= 0.004F) && !(w < 1.0F) && !(h < 1.0F)) {
            if (alpha > 1.0F) {
                alpha = 1.0F;
            }

            float r = (float)(rgb >> 16 & 0xFF) / 255.0F;
            float gg = (float)(rgb >> 8 & 0xFF) / 255.0F;
            float b = (float)(rgb & 0xFF) / 255.0F;
            int iw = Math.round(w);
            int ih = Math.round(h);
            int x = Math.round(cx - w * 0.5F);
            int y = Math.round(cy - h * 0.5F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(r, gg, b, alpha);
            g.blit(GLOW_TEX, x, y, iw, ih, 0.0F, 0.0F, 128, 128, 128, 128);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void drawRadialGlow(GuiGraphics g, float ecx, float ecy, float rx, float ry, int rgb, float peakAlpha) {
        drawGlowTex(g, ecx, ecy, rx * 2.0F, ry * 2.0F, rgb, peakAlpha);
    }

    private static void drawSoftDot(GuiGraphics g, float px, float py, float radius, int rgb, float alpha) {
        drawGlowTex(g, px, py, radius * 3.4F, radius * 3.4F, rgb, alpha);
    }

    private void renderSceneBackground(GuiGraphics g, int w, int h, int cx, int crateCY, float t) {
        int color = 16777215 & this.rarityColor;
        float rarityI = this.rarityIntensity();
        float baseTint = 0.14F + rarityI * 0.08F;
        int topBase = mix(658708, color, baseTint);
        int botBase = mix(197642, color, baseTint * 0.55F);
        g.fillGradient(0, 0, w, h, 0xFF000000 | topBase, 0xFF000000 | botBase);
        float[] nebBaseX = new float[]{0.28F, 0.72F, 0.5F};
        float[] nebBaseY = new float[]{0.3F, 0.38F, 0.72F};
        int[] nebTint = new int[]{color, mix(color, 9063679, 0.55F), mix(color, 2806015, 0.45F)};

        for (int nb = 0; nb < 3; nb++) {
            float nx = (float)w * nebBaseX[nb] + (float)Math.sin((double)(t * 0.006F) + (double)nb * 2.1) * (float)w * 0.04F;
            float ny = (float)h * nebBaseY[nb] + (float)Math.cos((double)(t * 0.005F) + (double)nb * 1.7) * (float)h * 0.04F;
            float nsz = (float)w * (0.42F + 0.12F * (float)nb);
            drawGlowTex(g, nx, ny, nsz, nsz * 0.78F, nebTint[nb], 0.12F + rarityI * 0.09F);
        }

        float amb = Math.min(1.0F, Math.max(0.0F, (t - 26.0F) / 44.0F));
        amb *= 0.86F + 0.14F * (float)Math.sin((double)t * 0.11);
        if (t >= 294.0F) {
            amb = Math.max(amb, Math.max(0.0F, 1.0F - (t - 294.0F) / 34.0F));
        }

        float ar = this.cUnitPx > 0.0F ? this.cUnitPx * 2.0F : (float)w * 0.26F;
        float ambScale = 1.0F + rarityI * 0.35F;
        drawGlowTex(g, (float)cx, (float)crateCY - 6.0F, ar * 3.6F * ambScale, ar * 2.85F * ambScale, color, amb * (0.26F + rarityI * 0.15F));
        drawGlowTex(g, (float)cx, (float)crateCY - 4.0F, ar * 2.0F * ambScale, ar * 1.62F * ambScale, color, amb * (0.44F + rarityI * 0.26F));
        float gy = (float)crateCY - 6.0F;
        float galAng = t * 0.01F;
        float corePulse = 0.78F + 0.22F * (float)Math.sin((double)t * 0.08);
        drawGlowTex(g, (float)cx, gy, ar * 1.35F * ambScale, ar * 0.95F * ambScale, color, corePulse * (0.18F + rarityI * 0.12F));
        drawGlowTex(g, (float)cx, gy, ar * 0.72F * ambScale, ar * 0.5F * ambScale, mix(color, 16777215, 0.55F), corePulse * (0.3F + rarityI * 0.16F));
        drawGlowTex(g, (float)cx, gy, ar * 0.32F * ambScale, ar * 0.24F * ambScale, mix(color, 16777215, 0.92F), corePulse * (0.55F + rarityI * 0.2F));
        int galArms = 2;
        int armDots = 44 + Math.round(rarityI * 16.0F);

        for (int arm = 0; arm < galArms; arm++) {
            float armOff = (float)arm * 3.14159F;

            for (int d = 1; d <= armDots; d++) {
                float fr = (float)d / (float)armDots;
                float rad = ar * (0.24F + fr * (2.5F + rarityI * 0.9F)) * ambScale;
                float ang = galAng + armOff + fr * 6.0F;
                float gpx = (float)cx + (float)Math.cos((double)ang) * rad;
                float gpy = gy + (float)Math.sin((double)ang) * rad * 0.42F;
                int gcol = fr < 0.28F ? mix(color, 16777215, 0.7F) : color;
                float br = (0.15F + rarityI * 0.09F) * (1.0F - fr * 0.5F);
                drawSoftDot(g, gpx, gpy, 1.05F * (1.0F - fr * 0.3F), gcol, br);
            }
        }

        int bgRays = 14 + Math.round(rarityI * 10.0F);
        float baseAng = t * 0.02F;
        float rayLen = ar * (2.0F + rarityI * 1.1F) * ambScale;

        for (int ri = 0; ri < bgRays; ri++) {
            float ang = baseAng + (float)((double)ri * Math.PI * 2.0 / (double)bgRays);
            float dx = (float)Math.cos((double)ang);
            float dy = (float)Math.sin((double)ang) * 0.72F;

            for (int j = 1; j <= 5; j++) {
                float d = rayLen * (float)j / 5.0F;
                float px = (float)cx + dx * d;
                float py = (float)crateCY - 4.0F + dy * d;
                drawSoftDot(g, px, py, 2.3F - (float)j * 0.3F, color, amb * (0.13F + rarityI * 0.09F) * (1.0F - (float)j / 6.0F));
            }
        }

        int motes = 42 + Math.round(rarityI * 20.0F);

        for (int i = 0; i < motes; i++) {
            float seed = (float)i * 3.71F + 1.3F;
            float mx = frac((float)Math.sin((double)seed) * 43758.547F);
            float my0 = frac((float)Math.sin((double)(seed + 2.1F)) * 22578.11F);
            float spd = 0.15F + frac((float)Math.sin((double)(seed + 4.7F)) * 9124.3F) * 0.45F;
            float my = frac(my0 - t * 0.0014F * spd);
            float twk = 0.35F + 0.65F * (float)Math.abs(Math.sin((double)t * 0.05 + (double)seed));
            float x = (mx + (float)Math.sin((double)t * 0.02 + (double)seed) * 0.02F) * (float)w;
            float y = my * (float)h;
            int mcol = i % 4 == 0 ? color : 16777215;
            drawSoftDot(g, x, y, 0.9F + twk * 0.7F, mcol, (i % 4 == 0 ? 0.09F : 0.06F) * twk);
        }

        int stars = 60 + Math.round(rarityI * 30.0F);

        for (int i = 0; i < stars; i++) {
            float seed = (float)i * 5.17F + 0.7F;
            float sx = frac((float)Math.sin((double)seed) * 43758.547F) * (float)w;
            float sy = frac((float)Math.sin((double)(seed + 2.7F)) * 22578.11F) * (float)h;
            float ph = frac((float)Math.sin((double)(seed + 5.1F)) * 9124.3F) * 6.2832F;
            float tw = 0.5F + 0.5F * (float)Math.sin((double)(t * 0.14F) + (double)ph);
            tw *= tw;
            boolean big = i % 5 == 0;
            float rad = (big ? 1.7F : 0.95F) * (0.6F + tw * 0.9F);
            int scol = i % 3 == 0 ? color : 16777215;
            drawSoftDot(g, sx, sy, rad, scol, (big ? 0.7F : 0.4F) * tw);
        }

        for (int sf = 0; sf < 3; sf++) {
            float period = 120.0F + (float)sf * 53.0F;
            float local = (t + (float)sf * 41.0F) % period;
            if (!(local >= 24.0F)) {
                float sp = local / 24.0F;
                float sseed = (float)sf * 13.7F + 2.0F;
                float sx0 = frac((float)Math.sin((double)sseed) * 4310.0F) * (float)w;
                float sy0 = (float)h * (0.06F + 0.28F * frac((float)Math.sin((double)(sseed + 1.3F)) * 220.7F));
                float dist = sp * (float)w * 0.6F;
                float hx = sx0 + dist;
                float hy = sy0 + dist * 0.5F;
                float sa = (float)Math.sin((double)sp * Math.PI);

                for (int tp = 0; tp < 6; tp++) {
                    float td = (float)tp * 6.0F;
                    drawSoftDot(g, hx - td, hy - td * 0.5F, 1.4F - (float)tp * 0.18F, 16777215, sa * (0.55F - (float)tp * 0.08F));
                }
            }
        }

        float impact = 0.0F;
        if (t >= 76.0F) {
            impact = Math.max(impact, Math.max(0.0F, 1.0F - (t - 76.0F) / 20.0F));
        }

        if (t >= 294.0F) {
            impact = Math.max(impact, Math.max(0.0F, 1.0F - (t - 294.0F) / 16.0F));
        }

        if (impact > 0.01F) {
            float ie = impact * impact;
            g.fillGradient(0, 0, w, h, (int)(ie * (102.0F + rarityI * 96.0F)) << 24 | topBase, 0);
            drawGlowTex(g, (float)cx, (float)crateCY, (float)w * (1.0F + ie * 0.8F), (float)h * (1.0F + ie * 0.8F), color, ie * (0.48F + rarityI * 0.32F));
        }

        if (t >= 76.0F && t < 98.0F) {
            float lf = Math.max(0.0F, 1.0F - (t - 76.0F) / 22.0F);
            lf *= lf;
            g.fillGradient(0, 0, w, h, (int)(lf * (78.0F + rarityI * 70.0F)) << 24 | 16777202, 0);
            drawGlowTex(
                g, (float)cx, (float)crateCY - 4.0F, (float)w * (0.85F + lf * 0.7F), (float)h * (0.8F + lf * 0.6F), 16777202, lf * (0.4F + rarityI * 0.28F)
            );
        }

        float divOpen = Math.min(1.0F, Math.max(0.0F, (t - 56.0F) / 26.0F));
        float divFlash = 0.0F;
        if (t >= 72.0F && t < 130.0F) {
            float dd = t - 72.0F;
            divFlash = dd < 6.0F ? dd / 6.0F : Math.max(0.0F, 1.0F - (dd - 6.0F) / 52.0F);
        }

        float divFade = t >= 294.0F ? Math.max(0.0F, 1.0F - (t - 294.0F) / 40.0F) : 1.0F;
        float divA = (0.35F * divOpen + 0.75F * divFlash) * divFade;
        if (divA > 0.02F) {
            float dcy = (float)crateCY - 6.0F;
            float dw = ar * (1.3F + divOpen * 0.6F + divFlash * 0.7F) * ambScale;
            drawGlowTex(g, (float)cx, dcy, dw * 2.4F, dw * 1.9F, color, divA * (0.3F + rarityI * 0.14F));
            drawGlowTex(g, (float)cx, dcy, dw * 1.35F, dw * 1.05F, mix(color, 16777215, 0.6F), divA * (0.42F + rarityI * 0.16F));
            drawGlowTex(g, (float)cx, dcy, dw * 0.62F, dw * 0.5F, mix(color, 16777215, 0.9F), Math.min(1.0F, divA * (0.7F + rarityI * 0.2F)));
            int drays = 8;
            float dlen = (float)h * (0.5F + divOpen * 0.4F) * (0.9F + rarityI * 0.4F);

            for (int rr = 0; rr < drays; rr++) {
                float fr = (float)rr / (float)(drays - 1);
                float ang = -1.5708F + (fr - 0.5F) * 1.7F;
                ang += 0.06F * (float)Math.sin((double)t * 0.1 + (double)rr);

                for (int sgi = 1; sgi <= 6; sgi++) {
                    float sfp = (float)sgi / 6.0F;
                    float rxp = (float)cx + (float)Math.cos((double)ang) * dlen * sfp;
                    float ryp = dcy + (float)Math.sin((double)ang) * dlen * sfp;
                    float rw = ar * (0.18F + rarityI * 0.06F) * (1.0F - sfp * 0.5F);
                    drawGlowTex(g, rxp, ryp, rw * 2.2F, rw * 2.2F, mix(color, 16777215, 0.5F), divA * (0.12F + rarityI * 0.05F) * (1.0F - sfp * 0.5F));
                }
            }

            if (divFlash > 0.04F) {
                int sb = 20 + Math.round(rarityI * 8.0F);
                float sblen = ar * (1.5F + divFlash * 2.4F) * ambScale;

                for (int ib = 0; ib < sb; ib++) {
                    float ab = (float)ib / (float)sb * 6.2832F + t * 0.02F;

                    for (int sg = 1; sg <= 4; sg++) {
                        float sfb = (float)sg / 4.0F;
                        float bxp = (float)cx + (float)Math.cos((double)ab) * sblen * sfb;
                        float byp = dcy + (float)Math.sin((double)ab) * sblen * sfb;
                        float bw = ar * (0.1F + rarityI * 0.05F) * (1.0F - sfb * 0.55F);
                        drawGlowTex(g, bxp, byp, bw * 2.0F, bw * 2.0F, mix(color, 16777215, 0.72F), divFlash * (0.45F + rarityI * 0.15F) * (1.0F - sfb * 0.6F));
                    }
                }

                drawGlowTex(
                    g,
                    (float)cx,
                    dcy,
                    ar * (1.0F + divFlash * 1.2F) * ambScale,
                    ar * (0.8F + divFlash) * ambScale,
                    16777215,
                    divFlash * (0.35F + rarityI * 0.15F)
                );
            }
        }

        g.fillGradient(0, 0, w, (int)((float)h * 0.3F), -1728053248, 0);
        g.fillGradient(0, (int)((float)h * 0.7F), w, h, 0, -1728053248);
        int vw = Math.max(30, w / 8);
        int steps = 8;

        for (int i = 0; i < steps; i++) {
            float f = 1.0F - (float)i / (float)steps;
            int al = (int)(102.0F * f * f) << 24;
            int x0 = i * vw / steps;
            int x1 = (i + 1) * vw / steps + 1;
            g.fill(x0, 0, x1, h, al);
            g.fill(w - x1, 0, w - x0, h, al);
        }
    }

    private void renderSparks(GuiGraphics g, int cx, int cy, float t) {
        int color = 16777215 & this.rarityColor;
        int count = 28 + this.winnerRarity.ordinal() * 7;

        for (int i = 0; i < count; i++) {
            float seed = (float)i * 12.9898F + 4.233F;
            float rx = frac((float)Math.sin((double)seed) * 43758.547F);
            float spd = 0.5F + frac((float)Math.sin((double)(seed + 1.7F)) * 22578.11F) * 0.9F;
            float phase;
            float life = frac(t * 0.009F * spd + (phase = frac((float)Math.sin((double)(seed + 5.3F)) * 13795.77F)));
            float a = smoothstep(0.0F, 0.18F, life) * (1.0F - smoothstep(0.7F, 1.0F, life));
            if (!(a <= 0.02F)) {
                float u = this.cUnitPx > 0.0F ? this.cUnitPx : 40.0F;
                float dir = i % 2 == 0 ? 1.0F : -1.0F;
                float swirl = life * (4.0F + spd * 2.0F) + phase * 6.2832F;
                float swirlR = u * (0.14F + life * 0.55F) * (0.6F + rx * 0.8F);
                float drift = (float)Math.cos((double)swirl) * swirlR * dir;
                float arcFall = life > 0.62F ? (life - 0.62F) * (life - 0.62F) * u * 1.6F : 0.0F;
                float x = (float)cx + (rx - 0.5F) * u * 3.2F + drift;
                float y = (float)cy + u * 2.4F - life * u * 6.8F + arcFall;
                float twk = 0.85F + 0.15F * (float)Math.sin((double)(t * 0.4F) + (double)seed);
                float rad = u * (0.018F + (1.0F - life) * 0.022F) * (0.8F + frac((float)Math.sin((double)(seed + 9.1F)) * 3456.7F) * 0.5F) * twk;
                int col = i % 4 == 0 ? 16777202 : color;
                drawSoftDot(g, x, y, rad, col, a * 0.5F);
            }
        }
    }

    private void renderShockwaveRing(GuiGraphics g, int cx, int cy, float since) {
        if (!(since > 20.0F)) {
            int bc = 16777215 & this.winnerRarity.rgb();
            int dots = 52;
            float p1 = Math.min(1.0F, since / 15.0F);
            float ease1 = 1.0F - (1.0F - p1) * (1.0F - p1) * (1.0F - p1);
            float radius = ease1 * 236.0F;
            float ba = Math.max(0.0F, 1.0F - p1) * Math.min(1.0F, since / 1.2F);
            if (ba > 0.02F) {
                float sz = 2.0F - ease1 * 1.1F;

                for (int i = 0; i < dots; i++) {
                    float ang = (float)((double)i * ((Math.PI * 2) / (double)dots));
                    float x = (float)cx + (float)Math.cos((double)ang) * radius;
                    float y = (float)cy + (float)Math.sin((double)ang) * radius * 0.7F;
                    drawSoftDot(g, x, y, sz, 16777202, ba * 0.85F);
                }
            }

            float d2 = since - 2.5F;
            if (d2 > 0.0F) {
                float p2 = Math.min(1.0F, d2 / 13.0F);
                float ease2 = 1.0F - (1.0F - p2) * (1.0F - p2) * (1.0F - p2);
                float radius2 = ease2 * 198.0F;
                float ba2 = Math.max(0.0F, 1.0F - p2);
                float sz2 = 2.1F - ease2 * 1.0F;

                for (int i = 0; i < dots; i++) {
                    float ang = (float)((double)i * ((Math.PI * 2) / (double)dots)) + 0.08F;
                    float x = (float)cx + (float)Math.cos((double)ang) * radius2;
                    float y = (float)cy + (float)Math.sin((double)ang) * radius2 * 0.7F;
                    drawSoftDot(g, x, y, sz2, bc, ba2 * 0.7F);
                }
            }

            float d3 = since - 5.0F;
            if (d3 > 0.0F) {
                float p3 = Math.min(1.0F, d3 / 15.0F);
                float ease3 = 1.0F - (1.0F - p3) * (1.0F - p3) * (1.0F - p3);
                float radius3 = ease3 * 272.0F;
                float ba3 = Math.max(0.0F, 1.0F - p3) * 0.55F;
                float sz3 = 1.9F - ease3 * 1.0F;

                for (int i = 0; i < dots; i++) {
                    float ang = (float)((double)i * ((Math.PI * 2) / (double)dots)) + 0.16F;
                    float x = (float)cx + (float)Math.cos((double)ang) * radius3;
                    float y = (float)cy + (float)Math.sin((double)ang) * radius3 * 0.7F;
                    drawSoftDot(g, x, y, sz3, 16777202, ba3);
                }
            }
        }
    }

    private void renderRevealBurst(GuiGraphics g, int cx, int cy, float since) {
        int bc = 16777215 & this.winnerRarity.rgb();
        float ba = Math.max(0.0F, 1.0F - since / 12.0F);
        if (!(ba <= 0.02F)) {
            float flash = Math.max(0.0F, 1.0F - since / 3.0F);
            if (flash > 0.02F) {
                float fe = flash * flash;
                drawGlowTex(g, (float)cx, (float)cy, 312.0F * (0.7F + since * 0.12F), 312.0F * (0.7F + since * 0.12F), bc, fe * 0.6F);
                drawGlowTex(g, (float)cx, (float)cy, 156.0F, 156.0F, 16777202, fe * 0.85F);
            }

            float p = Math.min(1.0F, since / 9.0F);
            float easedR = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
            float br = easedR * 124.0F;
            int burst = 56;

            for (int i = 0; i < burst; i++) {
                float ang = (float)((double)i * ((Math.PI * 2) / (double)burst));
                float lenVar = 0.72F + frac((float)Math.sin((double)i * 3.3) * 43758.5F) * 0.6F;
                if (i % 2 == 0) {
                    lenVar += 0.18F;
                }

                float rr = br * lenVar;
                float x = (float)cx + (float)Math.cos((double)ang) * rr;
                float y = (float)cy + (float)Math.sin((double)ang) * rr;
                float sz = (1.9F + ba * 1.8F) * (i % 2 == 0 ? 1.15F : 0.8F);
                drawSoftDot(g, x, y, sz, i % 4 == 0 ? 16777202 : bc, ba * 0.92F);
            }

            if (this.winnerRarity.ordinal() >= 0) {
                int sparkle = 16 + this.winnerRarity.ordinal() * 4;

                for (int i = 0; i < sparkle; i++) {
                    float seed = (float)i * 7.13F + 2.0F;
                    float ang = frac((float)Math.sin((double)seed) * 43758.547F) * 6.2832F;
                    float rr = (50.0F + frac((float)Math.sin((double)(seed + 1.9F)) * 12345.6F) * 92.0F) * Math.min(1.0F, since / 6.0F);
                    float twinkle = 0.5F + 0.5F * (float)Math.sin((double)(since * 9.0F + seed * 3.0F));
                    float x = (float)cx + (float)Math.cos((double)ang) * rr;
                    float y = (float)cy + (float)Math.sin((double)ang) * rr;
                    drawSoftDot(g, x, y, 1.1F + twinkle * 0.6F, 16777202, ba * twinkle * 0.6F);
                }
            }
        }
    }

    private static String stripAmp(String s) {
        return s == null ? "" : s.replace('&', '\u00a7');
    }
}
