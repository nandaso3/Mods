package com.fscrates.client.media;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/**
 * Textura destinada a recibir un fotograma nuevo cada frame.
 *
 * No usamos NativeImage + DynamicTexture porque escribir pixel por pixel
 * (setPixelRGBA) para 1600x900 son 1.44 millones de llamadas por fotograma y a
 * 24 fps eso funde el hilo de render. Aqui subimos el buffer completo de una
 * sola vez con glTexSubImage2D, que es lo que hace el propio Minecraft.
 */
public final class VideoTexture extends AbstractTexture {
    private int width;
    private int height;

    /** Sube un fotograma RGBA. Debe llamarse en el hilo de render. */
    public void upload(ByteBuffer rgba, int frameWidth, int frameHeight) {
        RenderSystem.assertOnRenderThread();

        if (this.width != frameWidth || this.height != frameHeight) {
            // (re)asigna el almacenamiento de la textura
            TextureUtil.prepareImage(this.getId(), frameWidth, frameHeight);
            this.width = frameWidth;
            this.height = frameHeight;
            this.setFilter(true, false);
        }

        this.bind();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GlStateManager._texSubImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            0,
            0,
            frameWidth,
            frameHeight,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            MemoryUtil.memAddress(rgba)
        );
    }

    public int frameWidth() {
        return this.width;
    }

    public int frameHeight() {
        return this.height;
    }

    public boolean hasFrame() {
        return this.width > 0 && this.height > 0;
    }

    @Override
    public void load(ResourceManager resourceManager) {
        // Nada que cargar: los pixeles llegan desde el decodificador.
    }
}
