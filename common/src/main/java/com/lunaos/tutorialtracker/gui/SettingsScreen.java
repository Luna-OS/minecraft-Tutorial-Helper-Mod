package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TrackerConfig;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.inventory.ContainerTracker;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The HUD and video settings, and the way to the video player's programs. */
public class SettingsScreen extends Screen {

    private final Screen parent;
    private int panelY;
    private int panelHeight;

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
        int width = 150;
        int left = this.width / 2 - width - 2;
        int right = this.width / 2 + 2;
        // The window fits its contents, centred: title, five rows of options, the tools button and "Done".
        this.panelHeight = 22 + 5 * 24 + 6 + 24 + 6 + 28;
        this.panelY = Math.max(4, (this.height - this.panelHeight) / 2);
        int y = this.panelY + 22;

        option(left, y, () -> Component.translatable("settings.tutorialtracker.hud", onOff(config.hudVisible)),
            b -> config.hudVisible = !config.hudVisible);
        option(right, y, () -> Component.translatable("settings.tutorialtracker.hud_side",
                Component.translatable(config.hudRight ? "settings.tutorialtracker.right" : "settings.tutorialtracker.left")),
            b -> config.hudRight = !config.hudRight);
        y += 24;
        option(left, y, () -> Component.translatable("settings.tutorialtracker.hide_done", onOff(config.hideDone)),
            b -> config.hideDone = !config.hideDone);
        option(right, y, () -> Component.translatable("settings.tutorialtracker.rows", config.hudRows),
            b -> config.hudRows = config.hudRows >= 20 ? 5 : config.hudRows + 5);
        y += 24;
        option(left, y, () -> Component.translatable("settings.tutorialtracker.count_inventory", onOff(config.countInventory)),
            b -> {
                config.countInventory = !config.countInventory;
                ContainerTracker.recount();
            });
        option(right, y, () -> Component.translatable("settings.tutorialtracker.video_hud", onOff(config.videoInHud)),
            b -> config.videoInHud = !config.videoInHud);
        y += 24;
        option(left, y, () -> Component.translatable("settings.tutorialtracker.video_corner",
                Component.translatable("settings.tutorialtracker.corner." + config.videoCorner)),
            b -> config.videoCorner = (config.videoCorner + 1) % 4);
        option(right, y, () -> Component.translatable("settings.tutorialtracker.video_size",
                Component.translatable("settings.tutorialtracker.size." + config.videoSize)),
            b -> config.videoSize = (config.videoSize + 1) % 3);
        y += 24;
        option(left, y, () -> Component.translatable("settings.tutorialtracker.quality", config.videoQuality),
            b -> config.videoQuality = config.videoQuality == 360 ? 480 : config.videoQuality == 480 ? 720 : 360);
        option(right, y, () -> Component.translatable("screen.tutorialtracker.video.volume", Math.round(config.volume * 100)),
            b -> {
                float next = config.volume - 0.25f;
                config.volume = next < -0.01f ? 1.0f : Math.max(0, next);
            });
        y += 30;
        this.addRenderableWidget(Button.builder(Component.translatable("settings.tutorialtracker.tools"),
                b -> this.minecraft.gui.setScreen(new ToolsScreen(this)))
            .bounds(left, y, width * 2 + 4, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
            .bounds(this.width / 2 - 100, y + 30, 200, 20).build());
    }

    private void option(int x, int y, Supplier<Component> label, Consumer<Button> change) {
        this.addRenderableWidget(Button.builder(label.get(), b -> {
            change.accept(b);
            TutorialTracker.config().save();
            b.setMessage(label.get());
        }).bounds(x, y, 150, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        McStyle.panel(graphics, this.width / 2 - 162, this.panelY, 324, this.panelHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        McStyle.label(graphics, this.font, this.title, this.width / 2 - 152, this.panelY + 8);
        super.extractRenderState(graphics, mouseX, mouseY, a);
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
