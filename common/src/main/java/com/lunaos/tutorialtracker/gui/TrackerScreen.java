package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TrackerConfig;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.inventory.ContainerTracker;
import com.lunaos.tutorialtracker.materials.ItemNameIndex;
import com.lunaos.tutorialtracker.project.Project;
import com.lunaos.tutorialtracker.video.VideoPlayer;
import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The main screen: the tutorial link, its material list with what you have of each, the video, and the project's
 * containers. Opened with B. The world keeps running behind it.
 */
public class TrackerScreen extends Screen {

    private static final int PAD = 8;
    private static final int ROW = 20;

    private final Screen parent;
    private Project project;

    private EditBox urlBox;
    private EditBox amountBox;
    private Button removeButton;
    private Button playButton;
    private String urlText;
    private Component status = Component.empty();
    private int statusColor = McStyle.LABEL;
    private boolean loading;

    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private double scroll;
    private int selected = -1;

    private int videoX;
    private int videoY;
    private int videoWidth;
    private int videoHeight;
    private int infoY;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int unmatchedX;
    private int unmatchedY;

    public TrackerScreen(Screen parent) {
        super(Component.translatable("screen.tutorialtracker.title"));
        this.parent = parent;
        this.project = TutorialTracker.activeProject();
        this.urlText = this.project != null ? this.project.url : "";
    }

    @Override
    protected void init() {
        this.project = TutorialTracker.activeProject();
        this.panelWidth = Math.min(this.width - 8, 600);
        this.panelHeight = this.height - 8;
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = 4;
        int left = this.panelX + PAD;
        int right = this.panelX + this.panelWidth - PAD;
        int innerWidth = right - left;
        int rightWidth = Math.clamp((int) (this.panelWidth * 0.42), 150, 280);

        int rowTwo = this.panelY + this.panelHeight - PAD - 20;
        int rowOne = rowTwo - 22;
        int editY = rowOne - 24;

        this.listX = left + 1;
        this.listY = this.panelY + 57;
        this.listWidth = innerWidth - rightWidth - 8 - 2;
        this.listHeight = Math.max(ROW, editY - 6 - this.listY);

        // Link row.
        int loadWidth = 70;
        this.urlBox = new EditBox(this.font, left, this.panelY + 18, innerWidth - loadWidth - 4, 20,
            Component.translatable("screen.tutorialtracker.url"));
        this.urlBox.setMaxLength(2048);
        this.urlBox.setHint(Component.translatable("screen.tutorialtracker.url.hint"));
        this.urlBox.setValue(this.urlText);
        this.urlBox.setResponder(value -> this.urlText = value);
        this.addRenderableWidget(this.urlBox);
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.load"), b -> load())
            .bounds(right - loadWidth, this.panelY + 18, loadWidth, 20).build());

        // Editing the selected material.
        int amountLabel = this.font.width(Component.translatable("screen.tutorialtracker.amount")) + 4;
        this.amountBox = new EditBox(this.font, left + amountLabel, editY, 56, 20,
            Component.translatable("screen.tutorialtracker.amount"));
        this.amountBox.setResponder(this::amountChanged);
        this.addRenderableWidget(this.amountBox);
        this.removeButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.remove"),
            b -> removeSelected()).bounds(left + amountLabel + 60, editY, 70, 20).build());
        this.unmatchedX = left + amountLabel + 136;
        this.unmatchedY = editY + 6;
        updateSelection();

        // Video panel.
        this.videoX = right - rightWidth;
        this.videoY = this.listY;
        this.videoWidth = rightWidth;
        // 16:9, but never so tall that the container info under the buttons runs into the row below.
        this.videoHeight = Math.max(60, Math.min(rightWidth * 9 / 16, editY - 52 - (this.videoY + 52)));
        int buttonY = this.videoY + this.videoHeight + 4;
        int quarter = (rightWidth - 6) / 4;
        this.playButton = this.addRenderableWidget(Button.builder(playLabel(), b -> {
            TutorialTracker.playOrPause();
            b.setMessage(playLabel());
        }).bounds(this.videoX, buttonY, quarter, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("-10s"), b -> VideoPlayer.get().seekRelative(-10))
            .bounds(this.videoX + quarter + 2, buttonY, quarter, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+10s"), b -> VideoPlayer.get().seekRelative(10))
            .bounds(this.videoX + (quarter + 2) * 2, buttonY, quarter, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.video.big"),
                b -> this.minecraft.gui.setScreen(new VideoScreen(this)))
            .bounds(this.videoX + (quarter + 2) * 3, buttonY, rightWidth - (quarter + 2) * 3, 20).build());
        int half = (rightWidth - 2) / 2;
        TrackerConfig config = TutorialTracker.config();
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.arrange"),
                b -> this.minecraft.gui.setScreen(new PopupEditScreen(this)))
            .bounds(this.videoX, buttonY + 22, half, 20)
            .tooltip(Tooltip.create(Component.translatable("screen.tutorialtracker.arrange.tooltip"))).build());
        this.addRenderableWidget(Button.builder(volumeLabel(), b -> {
            float next = config.volume - 0.25f;
            config.volume = next < -0.01f ? 1.0f : Math.max(0, next);
            config.save();
            b.setMessage(volumeLabel());
        }).bounds(this.videoX + half + 2, buttonY + 22, rightWidth - half - 2, 20).build());
        this.infoY = buttonY + 48;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.containers.clear"), b -> {
            if (this.project != null && !this.project.containers.isEmpty()) {
                this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
                    if (yes && this.project != null) {
                        this.project.containers.clear();
                        TutorialTracker.saveProject(this.project);
                        ContainerTracker.recount();
                    }
                    this.minecraft.gui.setScreen(this);
                }, Component.translatable("screen.tutorialtracker.containers.clear"),
                    Component.translatable("screen.tutorialtracker.containers.clear.confirm")));
            }
        }).bounds(this.videoX, editY, rightWidth, 20).build());

        // Bottom rows.
        int third = (innerWidth - 4) / 3;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.paste"),
                b -> this.minecraft.gui.setScreen(new PasteScreen(this, project())))
            .bounds(left, rowOne, third, 20)
            .tooltip(Tooltip.create(Component.translatable("screen.tutorialtracker.paste.tooltip"))).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.add_item"),
                b -> this.minecraft.gui.setScreen(new AddItemScreen(this, project())))
            .bounds(left + third + 2, rowOne, third, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.settings"),
                b -> this.minecraft.gui.setScreen(new SettingsScreen(this)))
            .bounds(left + (third + 2) * 2, rowOne, innerWidth - (third + 2) * 2, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> switchProject(-1))
            .bounds(left, rowTwo, 20, 20)
            .tooltip(Tooltip.create(Component.translatable("screen.tutorialtracker.previous_project"))).build());
        this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> switchProject(1))
            .bounds(left + 22, rowTwo, 20, 20)
            .tooltip(Tooltip.create(Component.translatable("screen.tutorialtracker.next_project"))).build());
        int fifth = (innerWidth - 44 - 4) / 3;
        int x = left + 44;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.new_project"), b -> {
            Project created = TutorialTracker.store().create();
            TutorialTracker.setActiveProject(created);
            this.urlText = "";
            this.selected = -1;
            this.scroll = 0;
            this.setStatus(Component.translatable("screen.tutorialtracker.status.new"), McStyle.LABEL);
            this.rebuildWidgets();
        }).bounds(x, rowTwo, fifth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.delete_project"), b -> {
            if (this.project == null) {
                return;
            }
            this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
                if (yes && this.project != null) {
                    TutorialTracker.store().delete(this.project);
                    TutorialTracker.setActiveProject(null);
                    Project next = TutorialTracker.activeProject();
                    this.urlText = next != null ? next.url : "";
                    this.selected = -1;
                    this.scroll = 0;
                }
                this.minecraft.gui.setScreen(this);
            }, Component.translatable("screen.tutorialtracker.delete_project"),
                Component.translatable("screen.tutorialtracker.delete_project.confirm", projectName(this.project))));
        }).bounds(x + fifth + 2, rowTwo, fifth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
            .bounds(x + (fifth + 2) * 2, rowTwo, right - (x + (fifth + 2) * 2), 20).build());
    }

    private Project project() {
        if (this.project == null) {
            this.project = TutorialTracker.activeOrNewProject();
        }
        return this.project;
    }

    private static Component projectName(Project project) {
        return project == null || project.name.isBlank()
            ? Component.translatable("screen.tutorialtracker.unnamed") : Component.literal(project.name);
    }

    private void switchProject(int direction) {
        List<Project> all = TutorialTracker.store().all();
        if (all.isEmpty()) {
            return;
        }
        int index = Math.max(0, all.indexOf(this.project));
        Project next = all.get(Math.floorMod(index + direction, all.size()));
        TutorialTracker.setActiveProject(next);
        this.urlText = next.url;
        this.selected = -1;
        this.scroll = 0;
        this.status = Component.empty();
        this.rebuildWidgets();
    }

    private void load() {
        String url = this.urlText.trim();
        if (url.isEmpty() || this.loading) {
            return;
        }
        Project target = project();
        if (!target.materials.isEmpty() && !url.equals(target.url)) {
            // A different tutorial gets its own project; the old one stays as it was.
            target = TutorialTracker.store().create();
            TutorialTracker.setActiveProject(target);
            this.project = target;
        }
        Project loadingProject = target;
        this.loading = true;
        setStatus(Component.translatable("screen.tutorialtracker.status.loading"), McStyle.LABEL);
        TutorialTracker.loadTutorial(loadingProject, url, error -> {
            this.loading = false;
            if (com.lunaos.tutorialtracker.source.TutorialFetcher.BLOCKED.equals(error)) {
                setStatus(Component.translatable("screen.tutorialtracker.status.blocked"), McStyle.DARK_RED);
            } else if (error != null) {
                setStatus(Component.translatable("screen.tutorialtracker.status.failed", error), McStyle.DARK_RED);
            } else if (loadingProject.materials.isEmpty()) {
                setStatus(Component.translatable("screen.tutorialtracker.status.none"), McStyle.DARK_GOLD);
            } else {
                setStatus(Component.translatable("screen.tutorialtracker.status.found", loadingProject.materials.size()),
                    McStyle.DARK_GREEN);
            }
            this.urlText = loadingProject.url;
            this.selected = -1;
            this.scroll = 0;
            if (this.minecraft.gui.screen() == this) {
                this.rebuildWidgets();
            }
        });
    }

    private void setStatus(Component text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    private Component playLabel() {
        VideoPlayer.State state = VideoPlayer.get().state();
        return Component.literal(state == VideoPlayer.State.PLAYING || state == VideoPlayer.State.LOADING ? "⏸" : "▶");
    }

    private static Component volumeLabel() {
        return Component.translatable("screen.tutorialtracker.video.volume",
            Math.round(TutorialTracker.config().volume * 100));
    }

    // --- Material list ------------------------------------------------------------------------------------------

    private List<Project.Material> materials() {
        return this.project != null ? this.project.materials : List.of();
    }

    private void updateSelection() {
        List<Project.Material> materials = materials();
        boolean valid = this.selected >= 0 && this.selected < materials.size();
        if (!valid) {
            this.selected = -1;
        }
        this.amountBox.active = valid;
        this.amountBox.setEditable(valid);
        this.removeButton.active = valid;
        this.amountBox.setValue(valid ? Integer.toString(materials.get(this.selected).needed) : "");
    }

    private void amountChanged(String value) {
        List<Project.Material> materials = materials();
        if (this.selected < 0 || this.selected >= materials.size() || !value.matches("\\d{1,7}")) {
            return;
        }
        try {
            int amount = Integer.parseInt(value);
            if (amount > 0 && materials.get(this.selected).needed != amount) {
                materials.get(this.selected).needed = amount;
                TutorialTracker.markDirty();
            }
        } catch (NumberFormatException ignored) {
            // Not a number (yet): left as it was.
        }
    }

    private void removeSelected() {
        List<Project.Material> materials = materials();
        if (this.selected >= 0 && this.selected < materials.size()) {
            materials.remove(this.selected);
            TutorialTracker.saveProject(this.project);
            this.selected = -1;
            updateSelection();
        }
    }

    private int maxScroll() {
        return Math.max(0, materials().size() * ROW - this.listHeight);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Enter in the link field loads it.
        if (this.getFocused() == this.urlBox
            && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
            load();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (x >= this.listX && x < this.listX + this.listWidth && y >= this.listY && y < this.listY + this.listHeight) {
            this.scroll = Math.clamp(this.scroll - scrollY * ROW * 2, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double x = event.x();
        double y = event.y();
        if (x >= this.listX && x < this.listX + this.listWidth && y >= this.listY && y < this.listY + this.listHeight) {
            int index = (int) ((y - this.listY + this.scroll) / ROW);
            if (index >= 0 && index < materials().size()) {
                this.selected = index;
                updateSelection();
                this.setFocused(this.amountBox);
                return true;
            }
        }
        if (x >= this.videoX && x < this.videoX + this.videoWidth && y >= this.videoY
            && y < this.videoY + this.videoHeight) {
            if (VideoPlayer.get().isActive()) {
                this.minecraft.gui.setScreen(new VideoScreen(this));
            } else {
                TutorialTracker.playOrPause();
            }
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        this.playButton.setMessage(playLabel());
        if (this.project != TutorialTracker.activeProject()) {
            this.rebuildWidgets();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        McStyle.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = this.panelX + PAD;
        McStyle.label(graphics, this.font, this.title, left, this.panelY + 6);

        // Project name and status.
        List<Project> all = TutorialTracker.store().all();
        String counter = this.project == null ? "" : " (" + (all.indexOf(this.project) + 1) + "/" + all.size() + ")";
        String header = projectName(this.project).getString() + counter;
        McStyle.label(graphics, this.font, HudRenderer.ellipsize(this.font, header, this.listWidth), left,
            this.panelY + 44, McStyle.LABEL);
        if (!this.status.getString().isEmpty()) {
            McStyle.label(graphics, this.font, HudRenderer.ellipsize(this.font, this.status.getString(), this.videoWidth),
                this.videoX, this.panelY + 44, this.statusColor);
        }

        extractList(graphics, mouseX, mouseY);
        McStyle.label(graphics, this.font, Component.translatable("screen.tutorialtracker.amount").getString(), left,
            this.amountBox.getY() + 6, this.selected >= 0 ? McStyle.LABEL : McStyle.LABEL_DISABLED);

        McStyle.inset(graphics, this.videoX, this.videoY, this.videoWidth, this.videoHeight, 0xFF000000);
        VideoPlayer.get().draw(graphics, this.videoX, this.videoY, this.videoWidth, this.videoHeight, true);
        extractVideoTime(graphics);
        extractInfo(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void extractVideoTime(GuiGraphicsExtractor graphics) {
        VideoPlayer player = VideoPlayer.get();
        if (player.isLive()) {
            int liveX = this.videoX + this.videoWidth - this.font.width(HudRenderer.LIVE) - 3;
            graphics.text(this.font, HudRenderer.LIVE, liveX, this.videoY + this.videoHeight - 16, McStyle.RED, true);
        } else if (player.duration() > 0) {
            String time = HudRenderer.time(player.position()) + " / " + HudRenderer.time(player.duration());
            graphics.text(this.font, time, this.videoX + this.videoWidth - this.font.width(time) - 3,
                this.videoY + this.videoHeight - 16, McStyle.WHITE, true);
            McStyle.xpBar(graphics, this.videoX + 2, this.videoY + this.videoHeight - 6, this.videoWidth - 4,
                player.position() / player.duration());
        }
    }

    private void extractList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x0 = this.listX;
        int y0 = this.listY;
        int x1 = this.listX + this.listWidth;
        int y1 = this.listY + this.listHeight;
        McStyle.inset(graphics, x0, y0, this.listWidth, this.listHeight, McStyle.SLOT);
        List<Project.Material> materials = materials();
        if (materials.isEmpty()) {
            graphics.textWithWordWrap(this.font, Component.translatable("screen.tutorialtracker.empty"), x0 + 6, y0 + 6,
                this.listWidth - 12, McStyle.WHITE, true);
            return;
        }
        this.scroll = Math.clamp(this.scroll, 0, maxScroll());
        graphics.enableScissor(x0, y0, x1, y1);
        Project.Material hovered = null;
        for (int i = 0; i < materials.size(); i++) {
            int y = y0 + 1 + i * ROW - (int) this.scroll;
            if (y + ROW < y0 || y > y1) {
                continue;
            }
            Project.Material material = materials.get(i);
            int have = ContainerTracker.have(material.item);
            boolean isHovered = mouseX >= x0 && mouseX < x1 && mouseY >= Math.max(y, y0) && mouseY < Math.min(y + ROW, y1);
            if (i == this.selected) {
                graphics.fill(x0, y - 1, x1, y + ROW - 1, 0x70FFFFFF);
            } else if (isHovered) {
                graphics.fill(x0, y - 1, x1, y + ROW - 1, 0x30FFFFFF);
            }
            if (isHovered) {
                hovered = material;
            }
            McStyle.slot(graphics, x0 + 1, y);
            Item item = ItemNameIndex.item(material.item);
            if (item != null) {
                graphics.item(new ItemStack(item), x0 + 2, y + 1);
            }
            String name = item != null ? new ItemStack(item).getHoverName().getString() : material.item;
            String amount = HudRenderer.format(have) + " / " + HudRenderer.format(material.needed);
            int amountX = x1 - 5 - this.font.width(amount);
            graphics.text(this.font, HudRenderer.ellipsize(this.font, name, amountX - x0 - 28), x0 + 23, y + 2,
                McStyle.WHITE, true);
            graphics.text(this.font, amount, amountX, y + 2, McStyle.amountColor(have, material.needed), true);
            McStyle.xpBar(graphics, x0 + 23, y + 12, this.listWidth - 28, have / (double) Math.max(1, material.needed));
        }
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int barHeight = Math.max(10, this.listHeight * this.listHeight / (materials.size() * ROW));
            int barY = y0 + (int) ((this.listHeight - barHeight) * (this.scroll / maxScroll()));
            graphics.fill(x1 - 3, barY, x1, barY + barHeight, McStyle.PANEL);
            graphics.fill(x1 - 3, barY + barHeight - 1, x1, barY + barHeight, McStyle.PANEL_SHADOW);
        }
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            Item item = ItemNameIndex.item(hovered.item);
            lines.add(item != null ? new ItemStack(item).getHoverName() : Component.literal(hovered.item));
            int inventory = ContainerTracker.inInventory(hovered.item);
            int total = ContainerTracker.have(hovered.item);
            int inContainers = TutorialTracker.config().countInventory ? total - inventory : total;
            lines.add(Component.translatable("screen.tutorialtracker.tooltip.needed", hovered.needed)
                .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.tutorialtracker.tooltip.containers", inContainers)
                .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("screen.tutorialtracker.tooltip.inventory", inventory)
                .withStyle(ChatFormatting.GRAY));
            int missing = hovered.needed - total;
            if (missing > 0) {
                int stack = item != null ? Math.max(1, item.getDefaultMaxStackSize()) : 64;
                lines.add(Component.translatable("screen.tutorialtracker.tooltip.missing", missing,
                    String.format("%.1f", missing / (double) stack)).withStyle(ChatFormatting.RED));
            } else {
                lines.add(Component.translatable("screen.tutorialtracker.tooltip.done").withStyle(ChatFormatting.GREEN));
            }
            graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
        }
    }

    private void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = this.infoY;
        int count = this.project == null ? 0 : this.project.containers.size();
        long unseen = this.project == null ? 0 : this.project.containers.stream().filter(c -> !c.seen).count();
        McStyle.label(graphics, this.font, Component.translatable("screen.tutorialtracker.containers", count),
            this.videoX, y);
        Component hint = unseen > 0 ? Component.translatable("screen.tutorialtracker.containers.unseen", unseen)
            : Component.translatable("screen.tutorialtracker.containers.hint");
        graphics.textWithWordWrap(this.font, hint, this.videoX, y + 11, this.videoWidth,
            unseen > 0 ? McStyle.DARK_GOLD : McStyle.LABEL, false);

        if (this.project != null && !this.project.unmatched.isEmpty()) {
            Component text = Component.translatable("screen.tutorialtracker.unmatched", this.project.unmatched.size());
            int textX = this.unmatchedX;
            int textY = this.unmatchedY;
            int room = this.listX + this.listWidth - textX;
            if (room > 20) {
                String shown = HudRenderer.ellipsize(this.font, text.getString(), room);
                McStyle.label(graphics, this.font, shown, textX, textY, McStyle.DARK_GOLD);
                if (mouseX >= textX && mouseX < textX + this.font.width(shown) && mouseY >= textY - 2
                    && mouseY < textY + 10) {
                    List<FormattedCharSequence> lines = new ArrayList<>();
                    lines.add(Component.translatable("screen.tutorialtracker.unmatched.tooltip")
                        .withStyle(ChatFormatting.YELLOW).getVisualOrderText());
                    for (String line : this.project.unmatched) {
                        lines.add(Component.literal("• " + line).withStyle(ChatFormatting.GRAY).getVisualOrderText());
                    }
                    graphics.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
                }
            }
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
    public void removed() {
        if (this.project != null) {
            TutorialTracker.saveProject(this.project);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
