package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.video.VideoPlayer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** The video large, with a seek bar. Space pauses, the arrow keys jump 5 seconds. The world keeps running. */
public class VideoScreen extends Screen {

    private final Screen parent;
    private Button playButton;
    private int videoX;
    private int videoY;
    private int videoWidth;
    private int videoHeight;
    private int barX;
    private int barY;
    private int barWidth;

    public VideoScreen(Screen parent) {
        super(Component.translatable("screen.tutorialtracker.video"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int controlsY = this.height - 4 - 8 - 20;
        this.videoX = 13;
        this.videoY = 20;
        this.videoWidth = this.width - 26;
        this.videoHeight = controlsY - 14 - this.videoY;

        int x = 12;
        this.playButton = this.addRenderableWidget(Button.builder(playLabel(), b -> {
            TutorialTracker.playOrPause();
            b.setMessage(playLabel());
        }).bounds(x, controlsY, 24, 20).build());
        x += 26;
        this.addRenderableWidget(Button.builder(Component.literal("-10s"), b -> VideoPlayer.get().seekRelative(-10))
            .bounds(x, controlsY, 36, 20).build());
        x += 38;
        this.addRenderableWidget(Button.builder(Component.literal("+10s"), b -> VideoPlayer.get().seekRelative(10))
            .bounds(x, controlsY, 36, 20).build());
        x += 38;
        this.addRenderableWidget(Button.builder(volumeLabel(), b -> {
            var config = TutorialTracker.config();
            float next = config.volume - 0.25f;
            config.volume = next < -0.01f ? 1.0f : Math.max(0, next);
            config.save();
            b.setMessage(volumeLabel());
        }).bounds(x, controlsY, 90, 20).build());
        x += 92;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.video.stop"), b -> {
            VideoPlayer.get().stop();
            this.playButton.setMessage(playLabel());
        }).bounds(x, controlsY, 50, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> this.onClose())
            .bounds(this.width - 12 - 70, controlsY, 70, 20).build());

        this.barX = 12;
        this.barY = controlsY - 9;
        this.barWidth = this.width - 24;
    }

    private static Component playLabel() {
        VideoPlayer.State state = VideoPlayer.get().state();
        return Component.literal(state == VideoPlayer.State.PLAYING || state == VideoPlayer.State.LOADING ? "⏸" : "▶");
    }

    private static Component volumeLabel() {
        return Component.translatable("screen.tutorialtracker.video.volume",
            Math.round(TutorialTracker.config().volume * 100));
    }

    @Override
    public void tick() {
        this.playButton.setMessage(playLabel());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE) {
            TutorialTracker.playOrPause();
            return true;
        }
        if (event.key() == InputConstants.KEY_LEFT) {
            VideoPlayer.get().seekRelative(-5);
            return true;
        }
        if (event.key() == InputConstants.KEY_RIGHT) {
            VideoPlayer.get().seekRelative(5);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        VideoPlayer player = VideoPlayer.get();
        if (event.y() >= this.barY - 3 && event.y() <= this.barY + 6 && event.x() >= this.barX
            && event.x() <= this.barX + this.barWidth && player.duration() > 0) {
            player.seek((event.x() - this.barX) / this.barWidth * player.duration());
            return true;
        }
        if (event.x() >= this.videoX && event.x() < this.videoX + this.videoWidth && event.y() >= this.videoY
            && event.y() < this.videoY + this.videoHeight) {
            TutorialTracker.playOrPause();
            return true;
        }
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        McStyle.panel(graphics, 4, 4, this.width - 8, this.height - 8);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        VideoPlayer player = VideoPlayer.get();
        String title = player.title();
        McStyle.label(graphics, this.font, HudRenderer.ellipsize(this.font,
            title.isEmpty() ? this.title.getString() : title, this.width - 24), 12, 10, McStyle.LABEL);
        McStyle.inset(graphics, this.videoX, this.videoY, this.videoWidth, this.videoHeight, 0xFF000000);
        player.draw(graphics, this.videoX, this.videoY, this.videoWidth, this.videoHeight, true);
        double duration = player.duration();
        double position = player.position();
        boolean live = player.isLive();
        McStyle.xpBar(graphics, this.barX, this.barY, this.barWidth, live ? 1 : duration > 0 ? position / duration : 0);
        if (live) {
            int liveX = this.width - 12 - 70 - 6 - this.font.width(HudRenderer.LIVE);
            McStyle.label(graphics, this.font, HudRenderer.LIVE, liveX, this.height - 4 - 8 - 14, McStyle.DARK_RED);
        } else if (duration > 0) {
            String time = HudRenderer.time(position) + " / " + HudRenderer.time(duration);
            int timeX = this.width - 12 - 70 - 6 - this.font.width(time);
            McStyle.label(graphics, this.font, time, timeX, this.height - 4 - 8 - 14, McStyle.LABEL);
            if (mouseY >= this.barY - 3 && mouseY <= this.barY + 6 && mouseX >= this.barX
                && mouseX <= this.barX + this.barWidth) {
                double at = (mouseX - this.barX) / (double) this.barWidth * duration;
                graphics.setTooltipForNextFrame(this.font, Component.literal(HudRenderer.time(at)), mouseX, mouseY);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
