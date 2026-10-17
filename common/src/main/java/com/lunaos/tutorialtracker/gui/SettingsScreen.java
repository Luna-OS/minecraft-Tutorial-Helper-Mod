package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TrackerConfig;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.Updater;
import com.lunaos.tutorialtracker.inventory.ContainerTracker;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The mod's settings, also what Mod Menu and the NeoForge and Forge mod lists open: the HUD and video options, the way
 * to the video player's programs, and a small picture of the screen in which the material list and the video window
 * are dragged where they belong and resized at their bottom right corner.
 */
public class SettingsScreen extends Screen {

    private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
    private static final Identifier CROSSHAIR = Identifier.withDefaultNamespace("hud/crosshair");
    private static final int COLUMN = 150;
    private static final int STEP = 22;

    private final Screen parent;
    private final PopupArranger arranger = new PopupArranger();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    /** The small picture of the game's screen the windows are arranged in, and its scale. */
    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;
    private float scale;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("screen.tutorialtracker.settings"));
        this.parent = parent;
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }

    @Override
    protected void init() {
        TrackerConfig config = TutorialTracker.config();
        // Options on the left, the windows' preview on the right, "Done" under both. The preview has the shape of
        // the screen and takes what room is left, as much as fits in height too.
        this.panelWidth = Math.min(this.width - 8, 480);
        int maxPreviewWidth = this.panelWidth - 16 - COLUMN - 8;
        int maxPreviewHeight = this.height - 8 - 22 - 12 - 24 - 30;
        this.previewWidth = Math.max(40, Math.min(maxPreviewWidth, maxPreviewHeight * this.width / this.height));
        this.previewHeight = this.previewWidth * this.height / this.width;
        this.scale = this.previewWidth / (float) this.width;
        int column = 8 * STEP - 2;
        int right = 12 + this.previewHeight + 24;
        this.panelHeight = 22 + Math.max(column, right) + 30;
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = Math.max(4, (this.height - this.panelHeight) / 2);
        int left = this.panelX + 8;
        int y = this.panelY + 22;
        this.previewX = left + COLUMN + 8 + (maxPreviewWidth - this.previewWidth) / 2;
        this.previewY = y + 12;

        option(left, y, () -> Component.translatable("settings.tutorialtracker.theme",
                Component.translatable(config.darkMode ? "settings.tutorialtracker.theme.dark"
                    : "settings.tutorialtracker.theme.light")),
            b -> {
                config.darkMode = !config.darkMode;
                McStyle.applyTheme(config.darkMode);
            });
        y += STEP;
        option(left, y, () -> Component.translatable("settings.tutorialtracker.hud", onOff(config.hudVisible)),
            b -> config.hudVisible = !config.hudVisible);
        y += STEP;
        option(left, y, () -> Component.translatable("settings.tutorialtracker.video_hud", onOff(config.videoInHud)),
            b -> config.videoInHud = !config.videoInHud);
        y += STEP;
        option(left, y, () -> Component.translatable("settings.tutorialtracker.hide_done", onOff(config.hideDone)),
            b -> config.hideDone = !config.hideDone);
        y += STEP;
        option(left, y, () -> Component.translatable("settings.tutorialtracker.count_inventory", onOff(config.countInventory)),
            b -> {
                config.countInventory = !config.countInventory;
                ContainerTracker.recount();
            });
        y += STEP;
        option(left, y, () -> Component.translatable("settings.tutorialtracker.quality", config.videoQuality),
            b -> config.videoQuality = config.videoQuality == 360 ? 480 : config.videoQuality == 480 ? 720 : 360);
        y += STEP;
        option(left, y, () -> Component.translatable("screen.tutorialtracker.video.volume", Math.round(config.volume * 100)),
            b -> {
                float next = config.volume - 0.25f;
                config.volume = next < -0.01f ? 1.0f : Math.max(0, next);
            });
        y += STEP;
        this.addRenderableWidget(Button.builder(Component.translatable("settings.tutorialtracker.tools"),
                b -> this.minecraft.gui.setScreen(new ToolsScreen(this)))
            .tooltip(Tooltip.create(Component.literal("yt-dlp · ffmpeg · Deno")))
            .bounds(left, y, COLUMN, 20).build());

        // Under the preview: back to the corners, or arrange at full size over the game.
        int buttonY = this.previewY + this.previewHeight + 4;
        int half = (this.previewWidth - 2) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.arrange.reset"), b -> {
            config.resetWindows();
            config.save();
        }).bounds(this.previewX, buttonY, half, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("settings.tutorialtracker.arrange"),
                b -> this.minecraft.gui.setScreen(new PopupEditScreen(this)))
            .tooltip(Tooltip.create(Component.translatable("screen.tutorialtracker.arrange.tooltip")))
            .bounds(this.previewX + this.previewWidth - half, buttonY, half, 20).build());

        // The bottom row: updates on or off, and done.
        int bottom = this.panelY + this.panelHeight - 26;
        int halfRow = (this.panelWidth - 16 - 4) / 2;
        option(left, bottom, halfRow, () -> Component.translatable("settings.tutorialtracker.auto_update",
                onOff(config.autoUpdate)),
            b -> {
                config.autoUpdate = !config.autoUpdate;
                if (config.autoUpdate) {
                    Updater.check();
                }
            });
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
            .bounds(left + halfRow + 4, bottom, halfRow, 20).build());
    }

    private boolean inPreview(double x, double y) {
        return x >= this.previewX && x < this.previewX + this.previewWidth && y >= this.previewY
            && y < this.previewY + this.previewHeight;
    }

    /** From the preview to the screen it shows. */
    private double toScreenX(double x) {
        return (x - this.previewX) / this.scale;
    }

    private double toScreenY(double y) {
        return (y - this.previewY) / this.scale;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            this.arranger.deselect();
            return true;
        }
        if (event.button() != 1 || !inPreview(event.x(), event.y())) {
            this.arranger.deselect();
            return false;
        }
        // The resize corner is at least 6 pixels of the preview, however small it is drawn.
        this.arranger.press(toScreenX(event.x()), toScreenY(event.y()), this.width, this.height, false,
            Math.max(Popups.GRIP, 6 / this.scale));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.arranger.drag(toScreenX(event.x()), toScreenY(event.y()), this.width, this.height)
            || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return this.arranger.release(this.width, this.height) || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.arranger.key(event, this.width, this.height) || super.keyPressed(event);
    }

    private void option(int x, int y, Supplier<Component> label, Consumer<Button> change) {
        option(x, y, COLUMN, label, change);
    }

    private void option(int x, int y, int width, Supplier<Component> label, Consumer<Button> change) {
        this.addRenderableWidget(Button.builder(label.get(), b -> {
            change.accept(b);
            TutorialTracker.config().save();
            b.setMessage(label.get());
        }).bounds(x, y, width, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        McStyle.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        McStyle.label(graphics, this.font, this.title, this.panelX + 8, this.panelY + 8);
        // How the updater is doing, at the right of the title.
        Component update = switch (Updater.state()) {
            case READY -> Updater.lastFailure() != null
                ? Component.translatable("settings.tutorialtracker.update.retry", Updater.newVersion())
                : Component.translatable("settings.tutorialtracker.update.ready", Updater.newVersion());
            case DOWNLOADING -> Component.translatable("settings.tutorialtracker.update.downloading",
                Updater.newVersion());
            case CHECKING -> Component.translatable("settings.tutorialtracker.update.checking");
            case UP_TO_DATE -> Component.translatable("settings.tutorialtracker.update.current", Updater.version());
            case FAILED -> Component.translatable("settings.tutorialtracker.update.failed", Updater.error());
            case OFF -> Component.translatable("settings.tutorialtracker.update.version", Updater.version());
        };
        if (update != null) {
            int titleEnd = this.panelX + 8 + this.font.width(this.title) + 12;
            int room = this.panelX + this.panelWidth - 8 - titleEnd;
            String text = HudRenderer.ellipsize(this.font, update.getString(), room);
            int color = Updater.state() == Updater.State.READY && Updater.lastFailure() == null ? McStyle.DARK_GREEN
                : Updater.state() == Updater.State.FAILED || Updater.lastFailure() != null ? McStyle.DARK_RED
                : McStyle.LABEL_DISABLED;
            McStyle.label(graphics, this.font, text, this.panelX + this.panelWidth - 8 - this.font.width(text),
                this.panelY + 8, color);
        }
        McStyle.label(graphics, this.font, HudRenderer.ellipsize(this.font,
                Component.translatable("settings.tutorialtracker.windows").getString(), this.previewWidth),
            this.previewX, this.previewY - 11, McStyle.LABEL);
        extractPreview(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /** The screen in small: hotbar and crosshair for orientation, and the two windows where they are in the game. */
    private void extractPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.previewX;
        int y = this.previewY;
        McStyle.inset(graphics, x, y, this.previewWidth, this.previewHeight, McStyle.PREVIEW);
        graphics.enableScissor(x, y, x + this.previewWidth, y + this.previewHeight);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(this.scale, this.scale);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, (this.width - 182) / 2, this.height - 22, 182, 22);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CROSSHAIR, (this.width - 15) / 2, (this.height - 15) / 2,
            15, 15);
        int screenMouseX = (int) Math.floor(toScreenX(mouseX));
        int screenMouseY = (int) Math.floor(toScreenY(mouseY));
        TrackerConfig config = TutorialTracker.config();
        if (config.hudVisible) {
            Popups.drawList(graphics, this.font, TutorialTracker.activeProject(),
                PopupArranger.rect(Popups.Kind.LIST, this.width, this.height), true, false, screenMouseX, screenMouseY);
        }
        if (config.videoInHud) {
            Popups.drawVideo(graphics, this.font, PopupArranger.rect(Popups.Kind.VIDEO, this.width, this.height), true,
                false, screenMouseX, screenMouseY);
        }
        graphics.pose().popMatrix();
        // The outline of the window that will move goes on top at the preview's own scale, so it stays visible.
        Popups.Kind dragging = this.arranger.dragging();
        Popups.Kind hovered = inPreview(mouseX, mouseY)
            ? PopupArranger.windowAt(screenMouseX, screenMouseY, this.width, this.height) : null;
        for (Popups.Kind kind : Popups.Kind.values()) {
            if (PopupArranger.shown(kind) && (dragging == kind || this.arranger.selected() == kind
                || (dragging == null && hovered == kind))) {
                Popups.Rect rect = PopupArranger.rect(kind, this.width, this.height);
                int rx = x + Math.round((rect.x() - 4) * this.scale);
                int ry = y + Math.round((rect.y() - 4) * this.scale);
                graphics.outline(rx, ry, Math.round((rect.width() + 8) * this.scale) + 1,
                    Math.round((rect.height() + 8) * this.scale) + 1, 0xE0FFFFFF);
            }
        }
        graphics.disableScissor();
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
