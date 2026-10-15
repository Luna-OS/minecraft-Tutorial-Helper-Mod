package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TrackerConfig;
import com.lunaos.tutorialtracker.TrackerKeys;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.project.Project;
import com.lunaos.tutorialtracker.video.VideoPlayer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Arranging the windows: the game shows through, the mouse is free, and the list and video windows can be dragged by
 * their title bar, resized at their bottom right corner and closed with their X. The video window also has its
 * controls here. Opened with U, from the main screen or the settings; Escape (or U again) goes back to the game.
 */
public class PopupEditScreen extends Screen {

    private enum Drag {
        MOVE,
        RESIZE
    }

    private final Screen parent;
    private Popups.Kind dragging;
    private Drag dragMode;
    private double grabX;
    private double grabY;
    private Button listToggle;
    private Button videoToggle;

    public PopupEditScreen(Screen parent) {
        super(Component.translatable("screen.tutorialtracker.arrange"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TrackerConfig config = TutorialTracker.config();
        int buttonWidth = 100;
        int total = buttonWidth * 4 + 6;
        int x = (this.width - total) / 2;
        int y = this.height - 26;
        this.listToggle = this.addRenderableWidget(Button.builder(listLabel(), b -> {
            config.hudVisible = !config.hudVisible;
            config.save();
            b.setMessage(listLabel());
        }).bounds(x, y, buttonWidth, 20).build());
        this.videoToggle = this.addRenderableWidget(Button.builder(videoLabel(), b -> {
            config.videoInHud = !config.videoInHud;
            config.save();
            b.setMessage(videoLabel());
        }).bounds(x + buttonWidth + 2, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.arrange.reset"), b -> {
            config.resetWindows();
            config.save();
            this.listToggle.setMessage(listLabel());
            this.videoToggle.setMessage(videoLabel());
        }).bounds(x + (buttonWidth + 2) * 2, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
            .bounds(x + (buttonWidth + 2) * 3, y, buttonWidth, 20).build());
    }

    private static Component listLabel() {
        return Component.translatable("screen.tutorialtracker.arrange.list",
            Component.translatable(TutorialTracker.config().hudVisible ? "options.on" : "options.off"));
    }

    private static Component videoLabel() {
        return Component.translatable("screen.tutorialtracker.arrange.video",
            Component.translatable(TutorialTracker.config().videoInHud ? "options.on" : "options.off"));
    }

    private Popups.Rect listRect() {
        return Popups.listRect(this.width, this.height, TutorialTracker.activeProject(), true);
    }

    private Popups.Rect videoRect() {
        return Popups.videoRect(this.width, this.height);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 1) {
            return false;
        }
        TrackerConfig config = TutorialTracker.config();
        double x = event.x();
        double y = event.y();
        // The video window is drawn over the list, so it gets the click first.
        if (config.videoInHud && handleClick(Popups.Kind.VIDEO, videoRect(), x, y)) {
            return true;
        }
        return config.hudVisible && handleClick(Popups.Kind.LIST, listRect(), x, y);
    }

    private boolean handleClick(Popups.Kind kind, Popups.Rect rect, double x, double y) {
        TrackerConfig config = TutorialTracker.config();
        if (Popups.closeButton(rect).contains(x, y)) {
            if (kind == Popups.Kind.LIST) {
                config.hudVisible = false;
                this.listToggle.setMessage(listLabel());
            } else {
                config.videoInHud = false;
                this.videoToggle.setMessage(videoLabel());
            }
            config.save();
            return true;
        }
        if (kind == Popups.Kind.VIDEO) {
            VideoPlayer player = VideoPlayer.get();
            if (Popups.videoButton(rect, 0).contains(x, y)) {
                player.seekRelative(-10);
                return true;
            }
            if (Popups.videoButton(rect, 1).contains(x, y)) {
                TutorialTracker.playOrPause();
                return true;
            }
            if (Popups.videoButton(rect, 2).contains(x, y)) {
                player.seekRelative(10);
                return true;
            }
        }
        if (Popups.grip(rect).contains(x, y)) {
            startDrag(kind, Drag.RESIZE, x - (rect.x() + rect.width()), y - (rect.y() + rect.height()));
            return true;
        }
        if (x >= rect.x() && x < rect.x() + rect.width() && y >= rect.y() && y < rect.y() + Popups.TITLE) {
            startDrag(kind, Drag.MOVE, x - rect.x(), y - rect.y());
            return true;
        }
        if (rect.contains(x, y)) {
            // A click in the body: the video plays or pauses, and either window can be dragged from anywhere.
            if (kind == Popups.Kind.VIDEO && y < rect.y() + rect.height() - 7) {
                TutorialTracker.playOrPause();
                return true;
            }
            startDrag(kind, Drag.MOVE, x - rect.x(), y - rect.y());
            return true;
        }
        return false;
    }

    private void startDrag(Popups.Kind kind, Drag mode, double grabX, double grabY) {
        this.dragging = kind;
        this.dragMode = mode;
        this.grabX = grabX;
        this.grabY = grabY;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging == null) {
            return super.mouseDragged(event, dx, dy);
        }
        TrackerConfig config = TutorialTracker.config();
        if (this.dragging == Popups.Kind.LIST) {
            Popups.Rect rect = listRect();
            if (this.dragMode == Drag.MOVE) {
                config.listX = (int) Math.round(event.x() - this.grabX);
                config.listY = (int) Math.round(event.y() - this.grabY);
            } else {
                int width = (int) Math.round(event.x() - this.grabX - rect.x());
                int height = (int) Math.round(event.y() - this.grabY - rect.y());
                config.listWidth = Math.clamp(width, Popups.MIN_LIST_WIDTH, Popups.MAX_LIST_WIDTH);
                config.hudRows = Math.clamp((height - Popups.TITLE - 2 + Popups.ROW / 2) / Popups.ROW,
                    Popups.MIN_ROWS, Popups.MAX_ROWS);
                // Resizing never moves the window: pin it where it is.
                config.listX = rect.x();
                config.listY = rect.y();
            }
        } else {
            Popups.Rect rect = videoRect();
            if (this.dragMode == Drag.MOVE) {
                config.videoX = (int) Math.round(event.x() - this.grabX);
                config.videoY = (int) Math.round(event.y() - this.grabY);
            } else {
                int width = (int) Math.round(event.x() - this.grabX - rect.x());
                config.videoWidth = Math.clamp(width, Popups.MIN_VIDEO_WIDTH, Math.max(Popups.MIN_VIDEO_WIDTH,
                    this.width - rect.x() - 6));
                config.videoX = rect.x();
                config.videoY = rect.y();
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging != null) {
            // Store where the windows really are, after being kept on screen.
            TrackerConfig config = TutorialTracker.config();
            Popups.Rect list = listRect();
            Popups.Rect video = videoRect();
            if (this.dragging == Popups.Kind.LIST) {
                config.listX = list.x();
                config.listY = list.y();
            } else {
                config.videoX = video.x();
                config.videoY = video.y();
            }
            this.dragging = null;
            config.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (TrackerKeys.ARRANGE.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // No dimming or blur: the windows are arranged over the game as it looks while playing.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        TrackerConfig config = TutorialTracker.config();
        Project project = TutorialTracker.activeProject();
        if (config.hudVisible) {
            Popups.Rect list = listRect();
            highlight(graphics, list, Popups.Kind.LIST, mouseX, mouseY);
            Popups.drawList(graphics, this.font, project, list, true, mouseX, mouseY);
        }
        if (config.videoInHud) {
            Popups.Rect video = videoRect();
            highlight(graphics, video, Popups.Kind.VIDEO, mouseX, mouseY);
            Popups.drawVideo(graphics, this.font, video, true, mouseX, mouseY);
        }
        // The hint at the top, in the tooltip's frame like the windows.
        Component hint = Component.translatable("screen.tutorialtracker.arrange.hint");
        int hintWidth = Math.min(this.width - 20, this.font.width(hint));
        int hintX = (this.width - hintWidth) / 2;
        int hintY = this.height - 44;
        McStyle.tooltipBox(graphics, hintX, hintY, hintWidth, 9);
        graphics.text(this.font, HudRenderer.ellipsize(this.font, hint.getString(), hintWidth), hintX, hintY,
            McStyle.WHITE, true);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /** A light outline around the window being dragged or under the mouse, so it is clear what will move. */
    private void highlight(GuiGraphicsExtractor graphics, Popups.Rect rect, Popups.Kind kind, int mouseX, int mouseY) {
        boolean active = this.dragging == kind || (this.dragging == null && rect.contains(mouseX, mouseY));
        if (active) {
            graphics.outline(rect.x() - 5, rect.y() - 5, rect.width() + 10, rect.height() + 10, 0xC0FFFFFF);
        }
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
        TutorialTracker.config().save();
        this.minecraft.gui.setScreen(this.parent);
    }
}
