package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TrackerConfig;
import com.lunaos.tutorialtracker.TrackerKeys;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.project.Project;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Arranging the windows: the game shows through, the mouse is free, and the list and video windows can be dragged by
 * their title bar, resized at their bottom right corner and closed with their X. The video window also has its
 * controls here, and the arrow keys move the clicked window exactly (with Alt, or Option on a Mac, they resize it).
 * Opened with U, from the main screen or the settings; Escape (or U again) goes back to the game.
 */
public class PopupEditScreen extends Screen {

    private final Screen parent;
    private final PopupArranger arranger = new PopupArranger();
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 1) {
            return false;
        }
        PopupArranger.Press press = this.arranger.press(event.x(), event.y(), this.width, this.height, true,
            Popups.GRIP);
        if (press == PopupArranger.Press.CLOSED) {
            this.listToggle.setMessage(listLabel());
            this.videoToggle.setMessage(videoLabel());
        }
        return press != PopupArranger.Press.NONE;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.arranger.drag(event.x(), event.y(), this.width, this.height)
            || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return this.arranger.release(this.width, this.height) || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (TrackerKeys.ARRANGE.matches(event)) {
            this.onClose();
            return true;
        }
        return this.arranger.key(event, this.width, this.height) || super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // No dimming or blur: the windows are arranged over the game as it looks while playing. Without a world
        // (opened from the mod list in the main menu) the menu's background stands in for it.
        if (this.minecraft.level == null) {
            super.extractBackground(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        TrackerConfig config = TutorialTracker.config();
        Project project = TutorialTracker.activeProject();
        if (config.hudVisible) {
            Popups.Rect list = PopupArranger.rect(Popups.Kind.LIST, this.width, this.height);
            highlight(graphics, list, Popups.Kind.LIST, mouseX, mouseY);
            Popups.drawList(graphics, this.font, project, list, true, true, mouseX, mouseY);
        }
        if (config.videoInHud) {
            Popups.Rect video = PopupArranger.rect(Popups.Kind.VIDEO, this.width, this.height);
            highlight(graphics, video, Popups.Kind.VIDEO, mouseX, mouseY);
            Popups.drawVideo(graphics, this.font, video, true, true, mouseX, mouseY);
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
        Popups.Kind dragging = this.arranger.dragging();
        boolean active = dragging == kind || this.arranger.selected() == kind
            || (dragging == null && PopupArranger.windowAt(mouseX, mouseY, this.width, this.height) == kind);
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
        return this.minecraft.level != null;
    }

    @Override
    public void onClose() {
        TutorialTracker.config().save();
        this.minecraft.gui.setScreen(this.parent);
    }
}
