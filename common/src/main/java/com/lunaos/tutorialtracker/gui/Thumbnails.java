package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.source.TutorialFetcher;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * The archive's preview pictures: downloaded once, kept in {@code tutorialtracker/thumbs}, and read with the stb
 * decoder the game ships (the game's own image reader takes PNG only, the pictures are mostly JPEG). Loading happens
 * in the background; until a picture is there, or when there is none, nothing is drawn and the card shows its frame.
 */
final class Thumbnails {

    private static final class Entry {
        volatile Identifier texture;
        volatile int width;
        volatile int height;
    }

    /** A picture drawn at any size, smoothly. */
    private static final class Texture extends DynamicTexture {
        Texture(String name, int width, int height) {
            super(name, width, height, true);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    /** Pictures bigger than this are not previews. */
    private static final int MAX_BYTES = 4 << 20;
    private static final Map<String, Entry> LOADED = new ConcurrentHashMap<>();
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "TutorialTracker thumbnails");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private Thumbnails() {
    }

    /** The picture's address: the list's own, or for a YouTube link the one YouTube always has. */
    static String address(String thumbnail, String url) {
        if (!thumbnail.isBlank()) {
            return thumbnail;
        }
        return TutorialFetcher.youtubeThumbnail(TutorialFetcher.youtubeId(url));
    }

    /**
     * Draws the picture filling the box, cut to its shape rather than squeezed. Returns false while there is no
     * picture (still loading, or none to be had).
     */
    static boolean draw(GuiGraphicsExtractor graphics, String thumbnail, String url, int x, int y, int width,
                        int height) {
        String address = address(thumbnail, url);
        if (address.isEmpty()) {
            return false;
        }
        Entry entry = LOADED.computeIfAbsent(address, Thumbnails::start);
        Identifier texture = entry.texture;
        if (texture == null) {
            return false;
        }
        int textureWidth = entry.width;
        int textureHeight = entry.height;
        // The largest part of the picture with the box's shape, centred.
        int regionWidth = textureWidth;
        int regionHeight = Math.round(textureWidth * height / (float) width);
        if (regionHeight > textureHeight) {
            regionHeight = textureHeight;
            regionWidth = Math.round(textureHeight * width / (float) height);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (textureWidth - regionWidth) / 2f,
            (textureHeight - regionHeight) / 2f, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
        return true;
    }

    private static Entry start(String address) {
        Entry entry = new Entry();
        LOADER.execute(() -> {
            try {
                byte[] bytes = bytes(address);
                if (bytes != null) {
                    decode(address, bytes, entry);
                }
            } catch (Exception e) {
                TutorialTracker.LOGGER.debug("No preview picture from {}: {}", address, e.toString());
            }
        });
        return entry;
    }

    /** From the cache, or downloaded into it. */
    private static byte[] bytes(String address) throws IOException, InterruptedException {
        Path file = TutorialTracker.dataDir().resolve("thumbs").resolve(hash(address));
        if (Files.isRegularFile(file)) {
            return Files.readAllBytes(file);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(address))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "TutorialTracker (Minecraft mod)")
            .GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                return null;
            }
            bytes = body.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) {
            return null;
        }
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        return bytes;
    }

    /** Decodes on this thread; the texture is made on the game's. */
    private static void decode(String address, byte[] bytes, Entry entry) {
        ByteBuffer input = MemoryUtil.memAlloc(bytes.length);
        ByteBuffer pixels;
        int width;
        int height;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            input.put(bytes).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            pixels = STBImage.stbi_load_from_memory(input, w, h, channels, 4);
            width = w.get(0);
            height = h.get(0);
        } finally {
            MemoryUtil.memFree(input);
        }
        if (pixels == null) {
            // A format stb does not read (WebP, say): the card stays without a picture.
            return;
        }
        byte[] rgba = new byte[width * height * 4];
        pixels.get(rgba);
        STBImage.stbi_image_free(pixels);
        Minecraft.getInstance().execute(() -> {
            Identifier id = Identifier.fromNamespaceAndPath(TutorialTracker.MOD_ID, "thumb/" + hash(address));
            Texture texture = new Texture("tutorialtracker thumbnail", width, height);
            ByteBuffer target = MemoryUtil.memByteBuffer(texture.getPixels().getPointer(), rgba.length);
            target.put(0, rgba);
            texture.upload();
            Minecraft.getInstance().getTextureManager().register(id, texture);
            entry.width = width;
            entry.height = height;
            entry.texture = id;
        });
    }

    private static String hash(String address) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(address.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 10);
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(address.hashCode());
        }
    }
}
