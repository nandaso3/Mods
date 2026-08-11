package com.fscrates.client.render;

import com.fscrates.animation.CrateAnimation;
import com.fscrates.block.CrateBlock;
import com.fscrates.block.CrateBlockEntity;
import com.fscrates.client.color.FSText;
import com.fscrates.client.color.FSTextStyle;
import com.fscrates.config.CrateConfig;
import com.fscrates.config.Rarity;
import com.fscrates.config.RewardEntry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class CrateRenderer implements BlockEntityRenderer<CrateBlockEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("fscrates", "textures/entity/crate/crate.png");
    private final CrateModel model;
    private final Font font;
    private static final Map<BakedModel, float[]> CENTER_CACHE = new IdentityHashMap<>();

    public CrateRenderer(Context ctx) {
        this.model = new CrateModel(ctx.bakeLayer(CrateModel.LAYER));
        this.font = ctx.getFont();
    }

    public void render(CrateBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CrateConfig cfg = be.getConfig();
        Rarity rarity = cfg.rarity;
        CrateAnimation anim = be.getAnimation();
        CrateAnimation.Style style = anim.style();
        float p = be.progress();
        float rot = facingYRot(be);
        float lidAngle = be.lidOpen(partialTick) * (float) (Math.PI / 2);
        float shake = be.shake(partialTick);
        float hop = this.chestHop(be, partialTick);
        float bob = (float)Math.sin((double)((be.ambientTime + partialTick) * 0.1F)) * 0.02F;
        float sc = this.chestScale(be, partialTick);
        float wob = this.chestWobble(be, partialTick);
        pose.pushPose();
        pose.translate(0.5, (double)(bob + hop + cfg.yOffset), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-rot + 180.0F + cfg.yawOffset));
        if (wob != 0.0F) {
            pose.mulPose(Axis.ZP.rotationDegrees(wob));
        }

        pose.translate(shake, 0.0F, 0.0F);
        float baseScale = CrateBakedModels.scaleFor(cfg) * Math.max(0.05F, cfg.sizeScale);
        float cineY = this.cineStretchY(be, partialTick);
        float cineXZ = 1.0F / (float)Math.sqrt((double)cineY);
        float S = sc * baseScale;
        pose.scale(S * cineXZ, S * cineY, S * cineXZ);
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        BlockState state = be.getBlockState();
        ModelBlockRenderer modelRenderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        int crateLight = 15728880;
        BakedModel baseModel = CrateBakedModels.baseModel(cfg);
        float[] ctr = footprintCenter(baseModel);
        pose.translate((double)(-ctr[0]), 0.0, (double)(-ctr[1]));
        modelRenderer.renderModel(pose.last(), vc, state, baseModel, 1.0F, 1.0F, 1.0F, 15728880, overlay);
        BakedModel lidModel = CrateBakedModels.lidModel(cfg);
        if (lidModel != null) {
            float[] h = CrateBakedModels.hingeFor(cfg);
            CrateStyles.Style st = CrateStyles.get(cfg.styleId);
            boolean cine = st != null && st.isCinematic();
            float lidDeg = be.lidOpen(partialTick) * 100.0F;
            float pivotZ = cine ? 1.0F - h[2] : h[2];
            float lidRot = cine ? -lidDeg : lidDeg;
            pose.pushPose();
            pose.translate(h[0], h[1], pivotZ);
            pose.mulPose(Axis.XP.rotationDegrees(lidRot));
            pose.translate(-h[0], -h[1], -pivotZ);
            modelRenderer.renderModel(pose.last(), vc, state, lidModel, 1.0F, 1.0F, 1.0F, 15728880, overlay);
            pose.popPose();
        }

        pose.popPose();
        float szc = Math.max(0.05F, cfg.sizeScale);
        float crateTop = cfg.yOffset + 1.15F * szc;
        if (be.animating && !be.sceneLidMode && style != CrateAnimation.Style.INSTANT && be.animTick >= be.getSpiralEndTick()) {
            this.renderBeam(be, pose, buffers, partialTick, cfg.yOffset, szc);
        }

        if (be.animating && !be.sceneLidMode && style != CrateAnimation.Style.INSTANT && be.animTick >= be.getOpenEndTick()) {
            this.renderReel(be, false, partialTick, pose, buffers, light, overlay, crateTop, szc);
        } else if (be.animating && style == CrateAnimation.Style.INSTANT && !be.getCandidates().isEmpty()) {
            float camYaw = Minecraft.getInstance().getEntityRenderDispatcher().camera.getYRot();
            pose.pushPose();
            pose.translate(0.5, (double)(crateTop + 0.35F), 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-camYaw));
            pose.scale(szc, szc, szc);
            this.renderItem(be, be.getCandidates().get(be.getWinnerIndex()), pose, buffers, light, overlay, 0.0F, 0.0F, 0.0F, 0.9F, 0.0F);
            pose.popPose();
        }

        this.renderHolograms(be, cfg, rarity, pose, buffers, light, crateTop);
    }

    private float chestHop(CrateBlockEntity be, float partial) {
        if (!be.animating) {
            return 0.0F;
        } else {
            float t = (float)be.animTick + partial;
            float openStart = (float)be.getSpiralEndTick();
            float u = t - openStart;
            if (u < -8.0F) {
                return 0.0F;
            } else if (u < 0.0F) {
                float a = (u + 8.0F) / 8.0F;
                return -0.05F * a;
            } else {
                float JUMP_TICKS = 11.0F;
                if (u < JUMP_TICKS) {
                    float p = u / JUMP_TICKS;
                    return (float)Math.sin((double)p * Math.PI) * 0.85F;
                } else {
                    float d = (u - JUMP_TICKS) / 10.0F;
                    float env = (float)Math.exp((double)(-3.0F * d));
                    return (float)Math.abs(Math.sin((double)d * 3.0 * Math.PI)) * 0.12F * env;
                }
            }
        }
    }

    private float chestScale(CrateBlockEntity be, float partial) {
        if (!be.animating) {
            return 1.0F;
        } else {
            float t = (float)be.animTick + partial;
            if (t < (float)be.getSpiralEndTick()) {
                return 1.0F + (float)Math.sin((double)(t * 1.6F)) * 0.05F;
            } else {
                float fp = be.finaleProgress(partial);
                return fp > 0.0F ? 1.0F + (float)Math.sin((double)fp * Math.PI) * 0.18F : 1.0F + (float)Math.sin((double)(t * 0.2F)) * 0.02F;
            }
        }
    }

    private float cineStretchY(CrateBlockEntity be, float partial) {
        if (!be.animating) {
            return 1.0F;
        } else {
            float t = (float)be.animTick + partial;
            float openStart = (float)be.getSpiralEndTick();
            float u = t - openStart;
            if (u < -8.0F) {
                return 1.0F;
            } else if (u < 0.0F) {
                float a = (u + 8.0F) / 8.0F;
                return 1.0F - 0.16F * a;
            } else {
                float d = u / 9.0F;
                float env = (float)Math.exp((double)(-2.0F * d));
                float osc = (float)Math.sin(Math.PI * (0.5 + (double)(d * 2.0F)));
                return 1.0F + 0.3F * env * osc;
            }
        }
    }

    private float chestWobble(CrateBlockEntity be, float partial) {
        if (be.animating && !be.isInstant()) {
            float t = (float)be.animTick + partial;
            int spiralEnd = be.getSpiralEndTick();
            if (t >= (float)spiralEnd) {
                return 0.0F;
            } else {
                float intensity = ((float)spiralEnd - t) / Math.max(1.0F, (float)spiralEnd);
                return (float)Math.sin((double)(t * 2.0F)) * 6.0F * intensity;
            }
        } else {
            return 0.0F;
        }
    }

    private void renderReel(
        CrateBlockEntity be,
        boolean vertical,
        float partial,
        PoseStack pose,
        MultiBufferSource buffers,
        int light,
        int overlay,
        float crateTop,
        float sizeScale
    ) {
        List<ItemStack> cands = be.getCandidates();
        if (!cands.isEmpty()) {
            int n = cands.size();
            int winner = Math.max(0, Math.min(n - 1, be.getWinnerIndex()));
            float rp = be.revealProgress(partial);
            float fp = be.finaleProgress(partial);
            float cp = be.closeProgress(partial);
            float ce = cp * cp;
            float camYaw = Minecraft.getInstance().getEntityRenderDispatcher().camera.getYRot();
            pose.pushPose();
            pose.translate(0.5, (double)(crateTop + 0.35F) - (double)(ce * 1.05F * sizeScale), 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-camYaw));
            pose.scale(sizeScale, sizeScale, sizeScale);
            float spacing = 0.55F;
            float maxTravel = CrateBlockEntity.reelTravel(n, winner);
            float scroll = CrateBlockEntity.easeOutReel(Math.min(1.0F, rp)) * maxTravel;
            boolean stopped = rp >= 1.0F;
            int window = 7;
            if (n <= 7) {
                for (int i = 0; i < n; i++) {
                    float d = wrapSigned((float)i - scroll, n);
                    float off = d * 0.55F * (1.0F - cp);
                    boolean center = Math.abs(d) < 0.5F;
                    float scale = 0.66F - Math.abs(d) * 0.14F;
                    if (stopped && center) {
                        scale += pulse(fp, be.animTick, partial) * 0.5F;
                    }

                    if (cp > 0.0F) {
                        scale *= center ? 1.0F - 0.7F * ce : Math.max(0.0F, 1.0F - 2.2F * cp);
                    }

                    if (scale > 0.02F) {
                        float x = vertical ? 0.0F : off;
                        float y = vertical ? off : 0.0F;
                        float yaw = center ? ((float)be.animTick + partial) * 2.0F : 0.0F;
                        if (center && cp > 0.0F) {
                            yaw += ce * 360.0F;
                        }

                        this.renderItem(be, cands.get(i), pose, buffers, light, overlay, x, y, 0.0F, Math.max(0.02F, scale), yaw);
                    }
                }
            } else {
                int base = (int)Math.floor((double)scroll);
                float frac = scroll - (float)base;

                for (int k = -3; k <= 3; k++) {
                    int idx = Math.floorMod(base + k, n);
                    float off2 = ((float)k - frac) * 0.55F;
                    if (Math.abs(off2) <= 1.75F) {
                        boolean center2 = Math.abs(off2) < 0.2475F;
                        float scale2 = 0.66F - Math.abs(off2) * 0.17F;
                        if (stopped && center2) {
                            scale2 += pulse(fp, be.animTick, partial) * 0.5F;
                        }

                        float off3 = off2 * (1.0F - cp);
                        if (cp > 0.0F) {
                            float var48;
                            scale2 = center2 ? (var48 = scale2 * (1.0F - 0.7F * ce)) : scale2 * Math.max(0.0F, 1.0F - 2.2F * cp);
                        }

                        if (scale2 > 0.02F) {
                            float x2 = vertical ? 0.0F : off3;
                            float y2 = vertical ? off3 : 0.0F;
                            float yaw2 = center2 ? ((float)be.animTick + partial) * 2.0F : 0.0F;
                            if (center2 && cp > 0.0F) {
                                yaw2 += ce * 360.0F;
                            }

                            this.renderItem(be, cands.get(idx), pose, buffers, light, overlay, x2, y2, 0.0F, Math.max(0.02F, scale2), yaw2);
                        }
                    }
                }
            }

            Matrix4f pm = pose.last().pose();
            VertexConsumer pvc = buffers.getBuffer(RenderType.lightning());
            float pw = 0.12F;
            float yIn = 0.4F;
            float yOut = 0.6F;
            float pa = 0.95F * (1.0F - cp);
            triangle(pvc, pm, 0.0F, 0.4F, -0.12F, 0.6F, 0.12F, 0.6F, 1.0F, 1.0F, 1.0F, pa);
            triangle(pvc, pm, 0.0F, -0.4F, -0.12F, -0.6F, 0.12F, -0.6F, 1.0F, 1.0F, 1.0F, pa);
            pose.popPose();
        }
    }

    private void renderBeam(CrateBlockEntity be, PoseStack pose, MultiBufferSource buffers, float partial, float yOff, float sizeScale) {
        float t = (float)be.animTick + partial;
        int spiralEnd = be.getSpiralEndTick();
        int openEnd = be.getOpenEndTick();
        int holdEnd = be.getHoldEndTick();
        int total = be.animTotal;
        float grow = t < (float)spiralEnd
            ? 0.0F
            : (
                t < (float)openEnd
                    ? (t - (float)spiralEnd) / Math.max(1.0F, (float)(openEnd - spiralEnd))
                    : (t < (float)holdEnd ? 1.0F : 1.0F - (t - (float)holdEnd) / Math.max(1.0F, (float)(total - holdEnd)))
            );
        if (!((grow = Math.max(0.0F, Math.min(1.0F, grow))) <= 0.01F)) {
            int color = be.getAnimColor();
            List<ItemStack> cands = be.getCandidates();
            int[] rar = be.getCandidateRarities();
            if (!cands.isEmpty() && rar.length > 0) {
                int n = cands.size();
                int winner = Math.max(0, Math.min(n - 1, be.getWinnerIndex()));
                float rp = be.revealProgress(partial);
                float maxTravel = CrateBlockEntity.reelTravel(n, winner);
                float scroll = CrateBlockEntity.easeOutReel(Math.min(1.0F, rp)) * maxTravel;
                int centerIdx = Math.floorMod(Math.round(scroll), n);
                if (centerIdx < rar.length) {
                    Rarity[] rv = Rarity.values();
                    color = rv[Math.max(0, Math.min(rv.length - 1, rar[centerIdx]))].rgb();
                }
            }

            float rr = (float)(color >> 16 & 0xFF) / 255.0F;
            float gg = (float)(color >> 8 & 0xFF) / 255.0F;
            float bb = (float)(color & 0xFF) / 255.0F;
            float hr = rr + (1.0F - rr) * 0.5F;
            float hg = gg + (1.0F - gg) * 0.5F;
            float hb = bb + (1.0F - bb) * 0.5F;
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            pose.pushPose();
            pose.translate(0.5, (double)yOff, 0.5);
            pose.scale(sizeScale, sizeScale, sizeScale);
            pose.translate(-0.5, 0.0, -0.5);
            Matrix4f m = pose.last().pose();
            float cx = 0.5F;
            float cz = 0.5F;
            float bottom = 0.55F;
            float top = 0.55F + grow * 0.62F;
            float pulse = 0.03F * (float)Math.sin((double)(((float)be.animTick + partial) * 0.35F));
            float halfBot = 0.3F + pulse;
            float halfTop = 0.4F + pulse;
            beamCone(vc, m, 0.5F, 0.5F, halfBot, halfTop, 0.55F, top, rr, gg, bb, 0.55F * grow, 0.0F);
            beamCone(vc, m, 0.5F, 0.5F, halfBot * 0.7F, halfTop * 0.62F, 0.55F, top, rr, gg, bb, 0.78F * grow, 0.06F * grow);
            beamCone(vc, m, 0.5F, 0.5F, halfBot * 0.42F, halfTop * 0.34F, 0.55F, top, rr, gg, bb, 0.95F * grow, 0.12F * grow);
            beamCone(vc, m, 0.5F, 0.5F, halfBot * 0.2F, halfTop * 0.16F, 0.55F, top, hr, hg, hb, 0.9F * grow, 0.18F * grow);
            beamDisc(vc, m, 0.5F, 0.5F, halfBot * 1.1F, 0.56F, rr, gg, bb, 0.6F * grow);
            beamDisc(vc, m, 0.5F, 0.5F, halfBot * 0.55F, 0.57F, hr, hg, hb, 0.55F * grow);
            pose.popPose();
        }
    }

    private static void beamCone(
        VertexConsumer vc,
        Matrix4f m,
        float cx,
        float cz,
        float halfBot,
        float halfTop,
        float bottom,
        float top,
        float r,
        float g,
        float b,
        float aBot,
        float aTop
    ) {
        float[][] cb = new float[][]{{cx - halfBot, cz - halfBot}, {cx + halfBot, cz - halfBot}, {cx + halfBot, cz + halfBot}, {cx - halfBot, cz + halfBot}};
        float[][] ct = new float[][]{{cx - halfTop, cz - halfTop}, {cx + halfTop, cz - halfTop}, {cx + halfTop, cz + halfTop}, {cx - halfTop, cz + halfTop}};

        for (int i = 0; i < 4; i++) {
            float[] b2 = cb[i];
            float[] b3 = cb[(i + 1) % 4];
            float[] t2 = ct[i];
            float[] t3 = ct[(i + 1) % 4];
            vert(vc, m, b2[0], bottom, b2[1], r, g, b, aBot);
            vert(vc, m, b3[0], bottom, b3[1], r, g, b, aBot);
            vert(vc, m, t3[0], top, t3[1], r, g, b, aTop);
            vert(vc, m, t2[0], top, t2[1], r, g, b, aTop);
        }
    }

    private static void beamDisc(VertexConsumer vc, Matrix4f m, float cx, float cz, float half, float y, float r, float g, float b, float a) {
        vert(vc, m, cx - half, y, cz - half, r, g, b, a);
        vert(vc, m, cx + half, y, cz - half, r, g, b, a);
        vert(vc, m, cx + half, y, cz + half, r, g, b, a);
        vert(vc, m, cx - half, y, cz + half, r, g, b, a);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).endVertex();
    }

    private static void triangle(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float cx, float cy, float r, float g, float b, float a) {
        vert(vc, m, ax, ay, 0.0F, r, g, b, a);
        vert(vc, m, bx, by, 0.0F, r, g, b, a);
        vert(vc, m, cx, cy, 0.0F, r, g, b, a);
        vert(vc, m, cx, cy, 0.0F, r, g, b, a);
    }

    private void renderItem(
        CrateBlockEntity be,
        ItemStack stack,
        PoseStack pose,
        MultiBufferSource buffers,
        int light,
        int overlay,
        float x,
        float y,
        float z,
        float scale,
        float yaw
    ) {
        if (stack != null && !stack.isEmpty()) {
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.scale(scale, scale, scale);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, 15728880, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }
    }

    private static float wrapSigned(float d, int n) {
        float m = d % (float)n;
        if (m < 0.0F) {
            m += (float)n;
        }

        if (m > (float)n / 2.0F) {
            m -= (float)n;
        }

        return m;
    }

    private static float pulse(float fp, int tick, float partial) {
        return fp <= 0.0F ? 0.0F : (float)(Math.sin((double)fp * Math.PI * 2.0 + (double)(((float)tick + partial) * 0.4F)) * 0.08F * (double)(1.0F - fp));
    }

    private void renderHolograms(CrateBlockEntity be, CrateConfig cfg, Rarity rarity, PoseStack pose, MultiBufferSource buffers, int light, float crateTop) {
        Vec3 camPos = Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition();
        if (!(be.getBlockPos().getCenter().distanceToSqr(camPos) > 576.0)) {
            ArrayList<MutableComponent> lines = new ArrayList<>();
            if (cfg.floatingName && cfg.displayName != null && !cfg.displayName.isEmpty()) {
                // Si el nombre tiene estilo propio (color exacto, arcoiris o
                // degradado) se pinta letra a letra; si no, se deja el camino de
                // siempre para que el color de rareza siga aplicandose.
                FSTextStyle nameStyle = cfg.nameStyle;
                if (nameStyle != null && (nameStyle.rainbow || nameStyle.gradient || nameStyle.bold
                    || nameStyle.italic || nameStyle.underline || nameStyle.strikethrough
                    || !"#FFFFFF".equalsIgnoreCase(nameStyle.color))) {
                    FSTextStyle styled = nameStyle.copy();
                    styled.text = colorize(cfg.displayName);
                    lines.add(styled.toComponent(System.currentTimeMillis()));
                } else {
                    lines.add(Component.literal(colorize(cfg.displayName)).withStyle(rarity.color()));
                }
            }

            for (String string : cfg.floatingText) {
                if (string != null && !string.isEmpty()) {
                    lines.add(Component.literal(colorize(string)));
                }
            }

            if (cfg.showOdds && !cfg.rewards.isEmpty()) {
                lines.add(Component.literal("\u00a77\u00a7l\u2014 Probabilidades \u2014"));
                int shown = 0;

                for (RewardEntry rw : cfg.rewards) {
                    if (shown >= 8) {
                        lines.add(Component.literal("\u00a78... y m\u00e1s"));
                        break;
                    }

                    String pct = rw.guaranteed ? "\u00a7a100%" : "\u00a7f" + fmt1(cfg.normalizedPercent(rw));
                    lines.add(Component.literal("\u00a77" + trim(rw.describe(), 22) + " " + pct));
                    shown++;
                }
            }

            if (!lines.isEmpty()) {
                Minecraft mc = Minecraft.getInstance();
                float f = be.animating ? Math.max(2.45F, crateTop + 1.3F) : Math.max(1.4F, crateTop + 0.25F);
                float lineH = 0.26F;

                for (int i = 0; i < lines.size(); i++) {
                    Component line = lines.get(i);
                    pose.pushPose();
                    pose.translate(0.5, (double)(f + (float)(lines.size() - 1 - i) * 0.26F), 0.5);
                    pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                    pose.scale(-0.025F, -0.025F, 0.025F);
                    Matrix4f mat = pose.last().pose();
                    float bgOpacity = mc.options.getBackgroundOpacity(0.25F);
                    int bg = (int)(bgOpacity * 255.0F) << 24;
                    float x = (float)(-this.font.width(line)) / 2.0F;
                    this.font.drawInBatch(line, x, 0.0F, -1, false, mat, buffers, DisplayMode.NORMAL, bg, light);
                    pose.popPose();
                }
            }
        }
    }

    private static String colorize(String s) {
        if (s != null && s.indexOf(38) >= 0) {
            char[] c = s.toCharArray();

            for (int i = 0; i < c.length - 1; i++) {
                if (c[i] == '&' && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c[i + 1]) >= 0) {
                    c[i] = 167;
                }
            }

            return new String(c);
        } else {
            return s;
        }
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        } else {
            return s.length() <= max ? s : s.substring(0, max - 1);
        }
    }

    private static float[] footprintCenter(BakedModel base) {
        if (base == null) {
            return new float[]{0.5F, 0.5F};
        } else {
            float[] cached = CENTER_CACHE.get(base);
            if (cached != null) {
                return cached;
            } else {
                float minX = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE;
                float minZ = Float.MAX_VALUE;
                float maxZ = -Float.MAX_VALUE;
                RandomSource rnd = RandomSource.create(42L);
                Direction[] sides = new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

                for (Direction side : sides) {
                    for (BakedQuad q : base.getQuads(null, side, rnd)) {
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

                float[] r = minX > maxX ? new float[]{0.5F, 0.5F} : new float[]{(minX + maxX) * 0.5F, (minZ + maxZ) * 0.5F};
                CENTER_CACHE.put(base, r);
                return r;
            }
        }
    }

    private static float facingYRot(CrateBlockEntity be) {
        try {
            Direction d = (Direction)be.getBlockState().getValue(CrateBlock.FACING);
            return d.toYRot();
        } catch (Exception var21) {
            return 0.0F;
        }
    }

    public boolean shouldRenderOffScreen(CrateBlockEntity be) {
        return true;
    }

    public int getViewDistance() {
        return 64;
    }
}
