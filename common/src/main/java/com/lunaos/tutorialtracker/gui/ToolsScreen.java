package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.video.VideoTools;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The video player's programs: which are found, and downloading the missing ones (only when the player clicks to).
 */
public class ToolsScreen extends Screen {

    private final Screen parent;
    private VideoTools.Progress progress;
    private boolean wasRunning;

    public ToolsScreen(Screen parent) {
        super(Component.translatable("screen.tutorialtracker.tools"));
        this.parent = parent;
        this.progress = VideoTools.running();
    }

    @Override
    protected void init() {
        int width = Math.min(320, this.width - 20);
        int x = (this.width - width) / 2;
        boolean busy = this.progress != null && !this.progress.finished;
        this.wasRunning = busy;
        int y = this.height - 76;
        Button download = this.addRenderableWidget(Button.builder(
            Component.translatable("screen.tutorialtracker.tools.download", VideoTools.downloadMegabytes()), b -> {
                this.progress = VideoTools.installMissing();
                this.rebuildWidgets();
            }).bounds(x, y, width, 20).build());
        download.active = !busy && !VideoTools.allPresent();
        Button update = this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.tools.update"),
            b -> {
                this.progress = VideoTools.updateYtDlp();
                this.rebuildWidgets();
            }).bounds(x, y + 24, width, 20).build());
        update.active = !busy && VideoTools.ytDlp() != null;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
            .bounds(x, y + 48, width, 20).build());
    }

    @Override
    public void tick() {
        boolean busy = this.progress != null && !this.progress.finished;
        if (busy != this.wasRunning) {
            this.rebuildWidgets();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int width = Math.min(320, this.width - 20);
        int x = (this.width - width) / 2;
        McStyle.label(graphics, this.font, this.title, x, 12);
        graphics.textWithWordWrap(this.font, Component.translatable("screen.tutorialtracker.tools.message"), x, 28, width,
            McStyle.LABEL, false);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        VideoTools.Progress current = this.progress;
        Component line = null;
        int color = McStyle.LABEL;
        int below = 0;
        if (current != null) {
            if (current.error != null) {
                line = Component.translatable("screen.tutorialtracker.tools.failed", current.error);
                color = McStyle.DARK_RED;
                // The whole message, wrapped, and what to do about a file another program holds.
                below = this.font.wordWrapHeight(line, width) + (current.locked
                    ? this.font.wordWrapHeight(Component.translatable("screen.tutorialtracker.tools.locked"), width) + 2
                    : 0);
            } else if (current.finished) {
                line = Component.translatable("screen.tutorialtracker.tools.finished");
                color = McStyle.DARK_GREEN;
                below = 9;
            } else {
                line = Component.translatable("screen.tutorialtracker.tools.progress", current.stage,
                    current.done / 1_000_000, Math.max(0, current.total) / 1_000_000);
                below = 16;
            }
        }
        // The tool lines and the message sit right above the buttons, pushed up by a longer message.
        int y = this.height - 80 - Math.max(0, below - 26) - 3 * 12 - 4 - 26;
        y = toolLine(graphics, x, y, "yt-dlp", VideoTools.ytDlp());
        y = toolLine(graphics, x, y, "ffmpeg", VideoTools.ffmpeg());
        y = toolLine(graphics, x, y, "Deno", VideoTools.deno());

        if (current != null) {
            y += 4;
            if (current.error != null) {
                graphics.textWithWordWrap(this.font, line, x, y, width, color, false);
                if (current.locked) {
                    graphics.textWithWordWrap(this.font, Component.translatable("screen.tutorialtracker.tools.locked"),
                        x, y + this.font.wordWrapHeight(line, width) + 2, width, McStyle.LABEL, false);
                }
            } else {
                McStyle.label(graphics, this.font, HudRenderer.ellipsize(this.font, line.getString(), width), x, y,
                    color);
            }
            if (!current.finished) {
                McStyle.xpBar(graphics, x, y + 11, width, current.fraction());
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int width = Math.min(320, this.width - 20);
        McStyle.panel(graphics, (this.width - width) / 2 - 8, 4, width + 16, this.height - 8);
    }

    private int toolLine(GuiGraphicsExtractor graphics, int x, int y, String name, Path path) {
        Component state = path == null ? Component.translatable("screen.tutorialtracker.tools.missing")
            : Component.translatable(VideoTools.isOwnCopy(path) ? "screen.tutorialtracker.tools.downloaded"
                : "screen.tutorialtracker.tools.system");
        McStyle.label(graphics, this.font, name + ":", x, y, McStyle.LABEL);
        graphics.text(this.font, state, x + 50, y, path == null ? McStyle.DARK_RED : McStyle.DARK_GREEN, false);
        return y + 12;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return this.minecraft.level != null;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
