package com.lunaos.tutorialtracker.video;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lunaos.tutorialtracker.TutorialTracker;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * Plays a tutorial video inside the game. yt-dlp finds the video's streams, one ffmpeg decodes the picture into raw
 * RGBA frames and another the sound into PCM; the frames go into a texture drawn on the HUD or a screen, the sound
 * into Java Sound. The picture follows the sound's clock, so the two stay together.
 */
public final class VideoPlayer {

    public enum State {
        IDLE,
        LOADING,
        PLAYING,
        PAUSED,
        ENDED,
        ERROR
    }

    record Stream(String title, String videoUrl, String audioUrl, String headers, int width, int height,
                  double duration) {
    }

    private static final VideoPlayer INSTANCE = new VideoPlayer();
    public static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("tutorialtracker", "video_frame");

    private static final int FPS = 30;
    private static final int SAMPLE_RATE = 48000;

    private volatile State state = State.IDLE;
    private volatile String error;
    private volatile String url;
    private volatile Stream stream;
    private volatile Session session;
    /** Bumped on every open or stop, so a slow yt-dlp lookup that finishes late is dropped. */
    private volatile int generation;

    private final Object frameLock = new Object();
    private byte[] pendingFrame;
    private int pendingWidth;
    private int pendingHeight;
    private boolean frameDirty;
    private boolean hasFrame;

    private VideoTexture texture;

    public static VideoPlayer get() {
        return INSTANCE;
    }

    private VideoPlayer() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopSession, "TutorialTracker video shutdown"));
    }

    public State state() {
        return this.state;
    }

    public String error() {
        return this.error;
    }

    public String url() {
        return this.url;
    }

    public boolean isActive() {
        return this.state == State.LOADING || this.state == State.PLAYING || this.state == State.PAUSED;
    }

    public double position() {
        Session current = this.session;
        return current != null ? current.clock() : 0.0;
    }

    public double duration() {
        Stream current = this.stream;
        return current != null ? current.duration : 0.0;
    }

    public String title() {
        Stream current = this.stream;
        return current != null ? current.title : "";
    }

    /** Starts the video at a link, from a position in seconds. */
    public synchronized void open(String pageUrl, double startAt) {
        stopSession();
        int myGeneration = ++this.generation;
        this.url = pageUrl;
        this.stream = null;
        this.error = null;
        this.state = State.LOADING;
        clearFrame();
        int quality = TutorialTracker.config().videoQuality;
        Thread resolver = new Thread(() -> {
            try {
                Stream resolved = resolve(pageUrl, quality);
                synchronized (this) {
                    if (myGeneration != this.generation) {
                        return;
                    }
                    this.stream = resolved;
                    double start = resolved.duration > 0 && startAt > resolved.duration - 2 ? 0 : Math.max(0, startAt);
                    startSession(start, false);
                }
            } catch (Exception e) {
                TutorialTracker.LOGGER.warn("Could not open video {}", pageUrl, e);
                synchronized (this) {
                    if (myGeneration == this.generation) {
                        this.error = e.getMessage() == null ? e.toString() : e.getMessage();
                        this.state = State.ERROR;
                    }
                }
            }
        }, "TutorialTracker video lookup");
        resolver.setDaemon(true);
        resolver.start();
    }

    public synchronized void stop() {
        this.generation++;
        stopSession();
        this.state = State.IDLE;
        this.stream = null;
        clearFrame();
    }

    public synchronized void togglePause() {
        if (this.state == State.PLAYING) {
            pause();
        } else if (this.state == State.PAUSED) {
            resume();
        } else if (this.state == State.ENDED && this.stream != null) {
            startSession(0, false);
        }
    }

    public synchronized void pause() {
        Session current = this.session;
        if (current != null && this.state == State.PLAYING) {
            current.setPaused(true);
            this.state = State.PAUSED;
        }
    }

    public synchronized void resume() {
        Session current = this.session;
        if (current != null && this.state == State.PAUSED) {
            current.setPaused(false);
            this.state = State.PLAYING;
        }
    }

    public synchronized void seek(double seconds) {
        if (this.stream == null) {
            return;
        }
        double target = Math.max(0, seconds);
        if (this.stream.duration > 0) {
            target = Math.min(target, Math.max(0, this.stream.duration - 1));
        }
        boolean paused = this.state == State.PAUSED;
        startSession(target, paused);
    }

    public void seekRelative(double seconds) {
        seek(position() + seconds);
    }

    private void startSession(double start, boolean paused) {
        stopSession();
        Session next = new Session(this.stream, start);
        this.session = next;
        this.state = paused ? State.PAUSED : State.PLAYING;
        next.start(paused);
    }

    private void stopSession() {
        Session current = this.session;
        this.session = null;
        if (current != null) {
            current.close();
        }
    }

    /** From a session's decoder thread: the picture stream ran out. */
    private synchronized void onSessionEnded(Session ended, int frames, String stderr, double at) {
        if (ended != this.session) {
            return;
        }
        if (frames == 0) {
            this.error = stderr.isBlank() ? "ffmpeg could not read the video" : VideoTools.lastLine(stderr);
            this.state = State.ERROR;
            stopSession();
            return;
        }
        double duration = this.stream.duration;
        if (duration > 0 && at < duration - 3 && ended.restarts < 3) {
            // The connection dropped (a long pause does that): carry on from where it stopped.
            Session next = new Session(this.stream, at);
            next.restarts = ended.restarts + 1;
            stopSession();
            this.session = next;
            next.start(this.state == State.PAUSED);
            return;
        }
        stopSession();
        this.state = State.ENDED;
    }

    private void publishFrame(Session from, byte[] frame, int width, int height) {
        if (from != this.session) {
            return;
        }
        synchronized (this.frameLock) {
            if (this.pendingFrame == null || this.pendingFrame.length != frame.length) {
                this.pendingFrame = new byte[frame.length];
            }
            System.arraycopy(frame, 0, this.pendingFrame, 0, frame.length);
            this.pendingWidth = width;
            this.pendingHeight = height;
            this.frameDirty = true;
            this.hasFrame = true;
        }
    }

    private void clearFrame() {
        synchronized (this.frameLock) {
            this.frameDirty = false;
            this.hasFrame = false;
        }
    }

    public boolean hasFrame() {
        synchronized (this.frameLock) {
            return this.hasFrame;
        }
    }

    /** Width over height of the picture, 16:9 until a frame says otherwise. */
    public float aspect() {
        synchronized (this.frameLock) {
            if (this.pendingWidth > 0 && this.pendingHeight > 0) {
                return this.pendingWidth / (float) this.pendingHeight;
            }
        }
        return 16.0f / 9.0f;
    }

    /** Moves the newest decoded frame into the texture. Render thread only. */
    private void uploadFrame() {
        synchronized (this.frameLock) {
            if (!this.frameDirty) {
                return;
            }
            this.frameDirty = false;
            if (this.texture == null || this.texture.width != this.pendingWidth || this.texture.height != this.pendingHeight) {
                Minecraft minecraft = Minecraft.getInstance();
                if (this.texture != null) {
                    minecraft.getTextureManager().release(TEXTURE_ID);
                }
                this.texture = new VideoTexture(this.pendingWidth, this.pendingHeight);
                minecraft.getTextureManager().register(TEXTURE_ID, this.texture);
            }
            this.texture.setFrame(this.pendingFrame);
        }
    }

    /**
     * Draws the video into a box, letterboxed to keep its shape, with its state written over it while there is no
     * picture yet. Returns the area the picture took.
     */
    public int[] draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean showState) {
        graphics.fill(x, y, x + width, y + height, 0xFF000000);
        int[] area = {x, y, width, height};
        if (hasFrame()) {
            uploadFrame();
            if (this.texture != null) {
                float aspect = this.texture.width / (float) this.texture.height;
                int drawWidth = width;
                int drawHeight = Math.round(width / aspect);
                if (drawHeight > height) {
                    drawHeight = height;
                    drawWidth = Math.round(height * aspect);
                }
                int drawX = x + (width - drawWidth) / 2;
                int drawY = y + (height - drawHeight) / 2;
                graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, drawX, drawY, 0, 0, drawWidth, drawHeight,
                    this.texture.width, this.texture.height, this.texture.width, this.texture.height);
                area = new int[]{drawX, drawY, drawWidth, drawHeight};
            }
        }
        if (showState) {
            String key = switch (this.state) {
                case LOADING -> "video.tutorialtracker.loading";
                case ERROR -> "video.tutorialtracker.error";
                case ENDED -> "video.tutorialtracker.ended";
                case PAUSED -> "video.tutorialtracker.paused";
                case IDLE -> "video.tutorialtracker.idle";
                default -> null;
            };
            if (key != null && (this.state != State.PAUSED || !hasFrame())) {
                var font = Minecraft.getInstance().font;
                var text = net.minecraft.network.chat.Component.translatable(key);
                graphics.centeredText(font, text, x + width / 2, y + height / 2 - 4, 0xFFFFFFFF);
                if (this.state == State.ERROR && this.error != null) {
                    graphics.textWithWordWrap(font, net.minecraft.network.chat.Component.literal(this.error),
                        x + 4, y + height / 2 + 8, width - 8, 0xFFFF8080);
                }
            } else if (this.state == State.PAUSED) {
                graphics.fill(x + width / 2 - 7, y + height / 2 - 8, x + width / 2 - 3, y + height / 2 + 8, 0xC0FFFFFF);
                graphics.fill(x + width / 2 + 3, y + height / 2 - 8, x + width / 2 + 7, y + height / 2 + 8, 0xC0FFFFFF);
            }
        }
        return area;
    }

    // --- Finding the streams ------------------------------------------------------------------------------------

    private static Stream resolve(String pageUrl, int quality) throws IOException, InterruptedException {
        if (VideoTools.ytDlp() == null || VideoTools.ffmpeg() == null) {
            throw new IOException("yt-dlp / ffmpeg missing");
        }
        // H.264 decodes cheapest; anything else up to the chosen height will do.
        String format = "bv*[height<=" + quality + "][vcodec^=avc1]+ba/bv*[height<=" + quality + "]+ba/b[height<="
            + quality + "]/bv*+ba/b";
        VideoTools.Result result = VideoTools.run(VideoTools.ytDlpCommand("-J", "-f", format, pageUrl), 120);
        if (result.exitCode() != 0) {
            throw new IOException(VideoTools.lastLine(result.stderr()));
        }
        JsonObject info = JsonParser.parseString(result.stdout()).getAsJsonObject();
        String title = string(info, "title");
        double duration = info.has("duration") && info.get("duration").isJsonPrimitive()
            ? info.get("duration").getAsDouble() : 0;

        String videoUrl = null;
        String audioUrl = null;
        String headers = headers(info);
        int width = integer(info, "width");
        int height = integer(info, "height");
        JsonArray requested = info.has("requested_formats") && info.get("requested_formats").isJsonArray()
            ? info.getAsJsonArray("requested_formats") : null;
        if (requested != null && !requested.isEmpty()) {
            for (JsonElement element : requested) {
                JsonObject format1 = element.getAsJsonObject();
                boolean hasVideo = !"none".equals(string(format1, "vcodec"));
                boolean hasAudio = !"none".equals(string(format1, "acodec"));
                if (hasVideo && videoUrl == null) {
                    videoUrl = string(format1, "url");
                    width = integer(format1, "width");
                    height = integer(format1, "height");
                    if (format1.has("http_headers")) {
                        headers = headers(format1);
                    }
                    if (hasAudio && audioUrl == null) {
                        audioUrl = videoUrl;
                    }
                } else if (hasAudio && audioUrl == null) {
                    audioUrl = string(format1, "url");
                }
            }
        } else {
            videoUrl = string(info, "url");
            if (!"none".equals(string(info, "acodec"))) {
                audioUrl = videoUrl;
            }
        }
        if (videoUrl == null || videoUrl.isEmpty()) {
            throw new IOException("no video stream found");
        }
        return new Stream(title, videoUrl, audioUrl == null || audioUrl.isEmpty() ? null : audioUrl, headers,
            width, height, duration);
    }

    private static String headers(JsonObject object) {
        if (!object.has("http_headers") || !object.get("http_headers").isJsonObject()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("http_headers").entrySet()) {
            out.append(entry.getKey()).append(": ").append(entry.getValue().getAsString()).append("\r\n");
        }
        return out.toString();
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static int integer(JsonObject object, String key) {
        JsonElement element = object.get(key);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // --- Playing ------------------------------------------------------------------------------------------------

    /** One run of the decoders from a start position; seeking starts a new one. */
    private final class Session {
        final Stream stream;
        final double start;
        final int width;
        final int height;
        int restarts;

        private volatile boolean closed;
        private volatile boolean paused;
        private final Object pauseLock = new Object();
        private Process videoProcess;
        private Process audioProcess;
        private volatile SourceDataLine line;
        /** True while the sound plays: its line is then the clock. */
        private volatile boolean audioClock;
        private volatile long audioFramesBefore;
        /** The wall clock, used without sound: seconds counted up to the last resume, and when that was. */
        private double wallBase;
        private long wallResumedAt = -1;

        Session(Stream stream, double start) {
            this.stream = stream;
            this.start = start;
            this.wallBase = start;
            int maxHeight = TutorialTracker.config().videoQuality;
            float aspect = stream.width > 0 && stream.height > 0 ? stream.width / (float) stream.height : 16.0f / 9.0f;
            int h = stream.height > 0 ? Math.min(stream.height, maxHeight) : maxHeight;
            int w = Math.round(h * aspect);
            this.width = Math.max(2, w & ~1);
            this.height = Math.max(2, h & ~1);
        }

        synchronized double clock() {
            SourceDataLine current = this.line;
            if (this.audioClock && current != null) {
                return this.start + (this.audioFramesBefore + current.getLongFramePosition()) / (double) SAMPLE_RATE;
            }
            double running = this.wallResumedAt >= 0 ? (System.nanoTime() - this.wallResumedAt) / 1e9 : 0;
            return this.wallBase + running;
        }

        /** Hands the clock to the wall: no sound, or the sound ended, failed or never came. */
        private synchronized void switchToWallClock() {
            if (this.wallStarted && !this.audioClock) {
                return;
            }
            double now = clock();
            this.audioClock = false;
            this.wallStarted = true;
            this.wallBase = now;
            this.wallResumedAt = this.paused ? -1 : System.nanoTime();
        }

        synchronized void setPaused(boolean pause) {
            if (pause == this.paused) {
                return;
            }
            if (this.wallStarted && !this.audioClock) {
                if (pause && this.wallResumedAt >= 0) {
                    this.wallBase += (System.nanoTime() - this.wallResumedAt) / 1e9;
                    this.wallResumedAt = -1;
                } else if (!pause) {
                    this.wallResumedAt = System.nanoTime();
                }
            }
            this.paused = pause;
            SourceDataLine current = this.line;
            if (current != null) {
                if (pause) {
                    current.stop();
                } else {
                    current.start();
                }
            }
            synchronized (this.pauseLock) {
                this.pauseLock.notifyAll();
            }
        }

        private volatile boolean wallStarted;
        private volatile long firstFrameAt = -1;

        /**
         * After the first frame: without sound the wall clock starts now; with sound the picture waits for it, since
         * its first sample belongs to the first frame.
         */
        private synchronized void onFirstFrame() {
            this.firstFrameAt = System.nanoTime();
            if (this.stream.audioUrl == null) {
                switchToWallClock();
            }
        }

        void start(boolean startPaused) {
            this.paused = startPaused;
            try {
                this.videoProcess = launch(videoCommand());
                Thread video = new Thread(this::runVideo, "TutorialTracker video");
                video.setDaemon(true);
                video.start();
                if (this.stream.audioUrl != null) {
                    this.audioProcess = launch(audioCommand());
                    Thread audio = new Thread(this::runAudio, "TutorialTracker audio");
                    audio.setDaemon(true);
                    audio.start();
                }
            } catch (IOException e) {
                TutorialTracker.LOGGER.warn("Could not start ffmpeg", e);
                synchronized (VideoPlayer.this) {
                    VideoPlayer.this.error = e.getMessage();
                    VideoPlayer.this.state = State.ERROR;
                }
                close();
            }
        }

        private List<String> input(String url) {
            List<String> command = new ArrayList<>();
            command.add(VideoTools.ffmpeg().toString());
            command.addAll(List.of("-hide_banner", "-loglevel", "error", "-nostdin"));
            if (url.startsWith("http")) {
                if (!this.stream.headers.isEmpty()) {
                    command.add("-headers");
                    command.add(this.stream.headers);
                }
                command.addAll(List.of("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5"));
            }
            if (this.start > 0.05) {
                command.add("-ss");
                command.add(String.format(java.util.Locale.ROOT, "%.3f", this.start));
            }
            // Two decoding threads are plenty for these sizes; ffmpeg would otherwise take one per core and compete
            // with the game's own threads for them.
            command.addAll(List.of("-threads", "2", "-i", url));
            return command;
        }

        private List<String> videoCommand() {
            List<String> command = input(this.stream.videoUrl);
            command.addAll(List.of("-an", "-sn", "-filter_threads", "1", "-vf",
                "scale=" + this.width + ":" + this.height + ":flags=bilinear,fps=" + FPS,
                "-pix_fmt", "rgba", "-f", "rawvideo", "pipe:1"));
            return command;
        }

        private List<String> audioCommand() {
            List<String> command = input(this.stream.audioUrl);
            command.addAll(List.of("-vn", "-sn", "-ac", "2", "-ar", Integer.toString(SAMPLE_RATE),
                "-f", "s16le", "pipe:1"));
            return command;
        }

        private Process launch(List<String> command) throws IOException {
            Process process = VideoTools.processBuilder(command).start();
            process.getOutputStream().close();
            return process;
        }

        private void runVideo() {
            Process process = this.videoProcess;
            ByteArrayOutputStream stderr = drain(process.getErrorStream());
            int frameSize = this.width * this.height * 4;
            byte[] frame = new byte[frameSize];
            int frames = 0;
            long lastShown = 0;
            try (InputStream in = process.getInputStream()) {
                while (!this.closed) {
                    if (in.readNBytes(frame, 0, frameSize) < frameSize) {
                        break;
                    }
                    double pts = this.start + frames / (double) FPS;
                    frames++;
                    if (frames == 1) {
                        // Shown at once, even paused: after a seek the picture should change.
                        publishFrame(this, frame, this.width, this.height);
                        onFirstFrame();
                        lastShown = System.nanoTime();
                        continue;
                    }
                    if (!waitUntil(pts)) {
                        break;
                    }
                    // Behind (a slow machine or a stall): skip frames to catch up, but never freeze the picture.
                    boolean late = clock() - pts > 0.2;
                    if (!late || System.nanoTime() - lastShown > 500_000_000L) {
                        publishFrame(this, frame, this.width, this.height);
                        lastShown = System.nanoTime();
                    }
                }
            } catch (IOException e) {
                // Closed under us: a seek or stop.
            }
            if (!this.closed) {
                try {
                    process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                onSessionEnded(this, frames, stderr.toString(StandardCharsets.UTF_8), clock());
            }
        }

        /** Waits for the clock to reach a frame's time; false when the session closed meanwhile. */
        private boolean waitUntil(double pts) {
            while (!this.closed) {
                if (this.paused) {
                    synchronized (this.pauseLock) {
                        try {
                            this.pauseLock.wait(100);
                        } catch (InterruptedException e) {
                            return false;
                        }
                    }
                    continue;
                }
                if (!this.audioClock && !this.wallStarted && this.firstFrameAt > 0
                    && System.nanoTime() - this.firstFrameAt > 6_000_000_000L) {
                    // The sound never started: play on without it rather than stand still.
                    switchToWallClock();
                }
                double ahead = pts - clock();
                if (ahead <= 0.004) {
                    return true;
                }
                try {
                    Thread.sleep(Math.max(1, Math.min(20, (long) (ahead * 1000))));
                } catch (InterruptedException e) {
                    return false;
                }
            }
            return false;
        }

        private void runAudio() {
            Process process = this.audioProcess;
            drain(process.getErrorStream());
            SourceDataLine opened = null;
            try {
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
                opened = AudioSystem.getSourceDataLine(format);
                opened.open(format, SAMPLE_RATE * 4 / 4);
            } catch (Exception e) {
                TutorialTracker.LOGGER.warn("No sound for the video: {}", e.toString());
                if (opened != null) {
                    opened.close();
                }
                process.destroy();
                switchToWallClock();
                return;
            }
            byte[] buffer = new byte[SAMPLE_RATE / 25 * 4];
            boolean started = false;
            try (InputStream in = process.getInputStream()) {
                while (!this.closed) {
                    int n = in.readNBytes(buffer, 0, buffer.length);
                    if (n <= 0) {
                        break;
                    }
                    n &= ~3;
                    applyVolume(buffer, n);
                    if (!started) {
                        started = true;
                        synchronized (this) {
                            // Normally zero; when the sound came so late that the wall clock took over, it plays on
                            // from where the clock is.
                            this.audioFramesBefore = this.wallStarted
                                ? Math.round((clock() - this.start) * SAMPLE_RATE) : 0;
                            this.line = opened;
                            this.audioClock = true;
                            if (!this.paused) {
                                opened.start();
                            }
                        }
                    }
                    int written = 0;
                    while (written < n && !this.closed) {
                        written += opened.write(buffer, written, n - written);
                        if (written < n && this.paused) {
                            synchronized (this.pauseLock) {
                                this.pauseLock.wait(50);
                            }
                        }
                    }
                }
                if (!this.closed && started) {
                    opened.drain();
                }
            } catch (IOException | InterruptedException e) {
                // Closed under us.
            } finally {
                if (!this.closed) {
                    switchToWallClock();
                }
                synchronized (this) {
                    if (this.line == opened) {
                        this.line = null;
                    }
                }
                opened.stop();
                opened.flush();
                opened.close();
            }
        }

        private void applyVolume(byte[] buffer, int length) {
            Minecraft minecraft = Minecraft.getInstance();
            float master = minecraft != null ? minecraft.options.getSoundSourceVolume(SoundSource.MASTER) : 1.0f;
            float volume = TutorialTracker.config().volume * master;
            if (volume >= 0.999f) {
                return;
            }
            for (int i = 0; i + 1 < length; i += 2) {
                int sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                sample = Math.round(sample * volume);
                buffer[i] = (byte) sample;
                buffer[i + 1] = (byte) (sample >> 8);
            }
        }

        private ByteArrayOutputStream drain(InputStream errors) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thread thread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                try (errors) {
                    int n;
                    while ((n = errors.read(buffer)) > 0) {
                        synchronized (out) {
                            if (out.size() < 16384) {
                                out.write(buffer, 0, n);
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // Process ended.
                }
            }, "TutorialTracker ffmpeg log");
            thread.setDaemon(true);
            thread.start();
            return out;
        }

        void close() {
            this.closed = true;
            synchronized (this.pauseLock) {
                this.pauseLock.notifyAll();
            }
            if (this.videoProcess != null) {
                this.videoProcess.destroyForcibly();
            }
            if (this.audioProcess != null) {
                this.audioProcess.destroyForcibly();
            }
            SourceDataLine current = this.line;
            if (current != null) {
                current.stop();
                current.flush();
            }
        }
    }
}
