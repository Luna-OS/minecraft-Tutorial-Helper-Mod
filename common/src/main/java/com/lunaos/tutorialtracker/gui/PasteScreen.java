package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.project.Project;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** For a material list that is not in the description: pasted from a comment, a website or typed by hand. */
public class PasteScreen extends Screen {

    private final Screen parent;
    private final Project project;
    private MultiLineEditBox textBox;
    private String text = "";

    public PasteScreen(Screen parent, Project project) {
        super(Component.translatable("screen.tutorialtracker.paste.title"));
        this.parent = parent;
        this.project = project;
    }

    @Override
    protected void init() {
        int boxWidth = Math.min(400, this.width - 20);
        int boxHeight = this.height - 86;
        this.textBox = MultiLineEditBox.builder()
            .setX((this.width - boxWidth) / 2)
            .setY(28)
            .setPlaceholder(Component.translatable("screen.tutorialtracker.paste.placeholder"))
            .build(this.font, boxWidth, boxHeight, this.title);
        this.textBox.setCharacterLimit(20000);
        this.textBox.setValue(this.text);
        this.textBox.setValueListener(value -> this.text = value);
        this.addRenderableWidget(this.textBox);
        this.setInitialFocus(this.textBox);

        int buttonWidth = (boxWidth - 4) / 3;
        int x = (this.width - boxWidth) / 2;
        int y = this.height - 54;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.paste.add"), b -> apply(false))
            .bounds(x, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.paste.replace"), b -> apply(true))
            .bounds(x + buttonWidth + 2, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
            .bounds(x + (buttonWidth + 2) * 2, y, boxWidth - (buttonWidth + 2) * 2, 20).build());
    }

    private void apply(boolean replace) {
        int found = TutorialTracker.applyText(this.project, this.text, replace);
        TutorialTracker.message(Component.translatable("message.tutorialtracker.pasted", found));
        this.onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int boxWidth = Math.min(400, this.width - 20);
        McStyle.panel(graphics, (this.width - boxWidth) / 2 - 8, 6, boxWidth + 16, this.height - 12);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int boxWidth = Math.min(400, this.width - 20);
        int x = (this.width - boxWidth) / 2;
        McStyle.label(graphics, this.font, this.title, x, 16);
        McStyle.label(graphics, this.font, HudRenderer.ellipsize(this.font,
            Component.translatable("screen.tutorialtracker.paste.help").getString(), boxWidth), x, this.height - 26,
            McStyle.LABEL);
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
