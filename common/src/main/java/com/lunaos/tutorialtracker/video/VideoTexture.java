package com.lunaos.tutorialtracker.video;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.system.MemoryUtil;

/** The video's current frame as a texture. Smoothly filtered, since it is drawn at any size. */
final class VideoTexture extends DynamicTexture {

    final int width;
    final int height;

    VideoTexture(int width, int height) {
        super("tutorialtracker video", width, height, true);
        this.width = width;
        this.height = height;
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    /** Copies a frame of tightly packed RGBA bytes (ffmpeg's rgba) in and uploads it. Render thread only. */
    void setFrame(byte[] rgba) {
        ByteBuffer pixels = MemoryUtil.memByteBuffer(this.getPixels().getPointer(), this.width * this.height * 4);
        pixels.put(0, rgba, 0, Math.min(rgba.length, pixels.capacity()));
        this.upload();
    }
}
