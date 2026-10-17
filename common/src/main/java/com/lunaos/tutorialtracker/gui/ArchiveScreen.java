package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.inventory.ContainerTracker;
import com.lunaos.tutorialtracker.materials.ItemNameIndex;
import com.lunaos.tutorialtracker.project.Project;
import com.lunaos.tutorialtracker.project.ShareCode;
import com.lunaos.tutorialtracker.source.TutorialFetcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Every project as a card: the video's picture, the farm's name, the Minecraft version and how many materials it has.
 * A project can be opened again, renamed, given its version, deleted, or copied as a short code to send over Discord
 * and pasted back in on the other side.
 */
public class ArchiveScreen extends Screen {

    private static final int THUMB_WIDTH = 120;
    private static final int THUMB_HEIGHT = 68;
    private static final int CARD_WIDTH = THUMB_WIDTH + 4;
    private static final int CARD_HEIGHT = THUMB_HEIGHT + 4 + 22;
    private static final int GAP = 6;
    private static final int PAD = 8;

    private final Screen parent;
    /** Newest first. */
    private List<Project> projects = List.of();
    private Project selected;
    private int scroll;
    private Component status = Component.empty();
    private int statusColor;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int gridX;
    private int gridY;
    private int gridHeight;
    private int columns;
    private EditBox nameBox;
    private EditBox versionBox;
    private Button openButton;
    private Button exportButton;
    private Button deleteButton;

    public ArchiveScreen(Screen parent) {
        super(Component.translatable("screen.tutorialtracker.archive"));
        this.parent = parent;
        this.selected = TutorialTracker.activeProject();
    }

    @Override
    protected void init() {
        this.projects = new ArrayList<>(TutorialTracker.store().all());
        Collections.reverse(this.projects);
        if (this.selected != null && !this.projects.contains(this.selected)) {
            this.selected = null;
        }
        this.panelWidth = Math.min(this.width - 8, 600);
        this.panelHeight = this.height - 8;
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = 4;
        int left = this.panelX + PAD;
        int right = this.panelX + this.panelWidth - PAD;
        int innerWidth = right - left;
        int buttonsY = this.panelY + this.panelHeight - PAD - 20;
        int editY = buttonsY - 24;

        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.archive.import"),
                b -> importFromClipboard())
            .tooltip(Tooltip.create(Component.translatable("screen.tutorialtracker.archive.import.tooltip")))
            .bounds(right - 110, this.panelY + 4, 110, 20).build());

        // The cards fill the middle, as many to a row as fit, centred.
        this.columns = Math.max(1, (innerWidth + GAP) / (CARD_WIDTH + GAP));
        this.gridX = left + (innerWidth - (this.columns * (CARD_WIDTH + GAP) - GAP)) / 2;
        this.gridY = this.panelY + 30;
        this.gridHeight = Math.max(CARD_HEIGHT, editY - 6 - this.gridY);
        this.scroll = Math.clamp(this.scroll, 0, maxScroll());

        // The selected project's name and version.
        Component nameLabel = Component.translatable("screen.tutorialtracker.archive.name");
        Component versionLabel = Component.translatable("screen.tutorialtracker.archive.version");
        int nameX = left + this.font.width(nameLabel) + 4;
        int versionWidth = 60;
        int versionX = right - versionWidth;
        this.nameBox = new EditBox(this.font, nameX, editY, versionX - this.font.width(versionLabel) - 12 - nameX, 20,
            nameLabel);
        this.nameBox.setMaxLength(200);
        this.versionBox = new EditBox(this.font, versionX, editY, versionWidth, 20, versionLabel);
        this.versionBox.setMaxLength(20);
        this.versionBox.setHint(Component.literal("1.21"));
        fillEditors();
        this.nameBox.setResponder(value -> {
            if (this.selected != null && !value.equals(this.selected.name)) {
                this.selected.name = value;
                TutorialTracker.saveProject(this.selected);
            }
        });
        this.versionBox.setResponder(value -> {
            if (this.selected != null && !value.equals(this.selected.version)) {
                this.selected.version = value.strip();
                TutorialTracker.saveProject(this.selected);
            }
        });
        this.addRenderableWidget(this.nameBox);
        this.addRenderableWidget(this.versionBox);

        int quarter = (innerWidth - 6) / 4;
        this.openButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.tutorialtracker.archive.open"), b -> openSelected())
            .bounds(left, buttonsY, quarter, 20).build());
        this.exportButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.tutorialtracker.archive.export"), b -> exportSelected())
            .tooltip(Tooltip.create(Component.translatable("screen.tutorialtracker.archive.export.tooltip")))
            .bounds(left + quarter + 2, buttonsY, quarter, 20).build());
        this.deleteButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.tutorialtracker.archive.delete"), b -> deleteSelected())
            .bounds(left + (quarter + 2) * 2, buttonsY, quarter, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
            .bounds(left + (quarter + 2) * 3, buttonsY, right - (left + (quarter + 2) * 3), 20).build());
        updateButtons();
    }

    private void fillEditors() {
        this.nameBox.setValue(this.selected == null ? "" : this.selected.name);
        this.versionBox.setValue(this.selected == null ? "" : this.selected.version);
        this.nameBox.setEditable(this.selected != null);
        this.versionBox.setEditable(this.selected != null);
    }

    private void updateButtons() {
        boolean any = this.selected != null;
        this.openButton.active = any;
        this.exportButton.active = any;
        this.deleteButton.active = any;
    }

    private void select(Project project) {
        this.selected = project;
        fillEditors();
        updateButtons();
    }

    private int rows() {
        return (this.projects.size() + this.columns - 1) / this.columns;
    }

    private int visibleRows() {
        return Math.max(1, (this.gridHeight + GAP) / (CARD_HEIGHT + GAP));
    }

    private int maxScroll() {
        return Math.max(0, rows() - visibleRows());
    }

    /** The card under a point, or null. */
    private Project cardAt(double x, double y) {
        if (x < this.gridX || y < this.gridY || y >= this.gridY + this.gridHeight) {
            return null;
        }
        int column = (int) ((x - this.gridX) / (CARD_WIDTH + GAP));
        int row = (int) ((y - this.gridY) / (CARD_HEIGHT + GAP));
        if (column >= this.columns || (x - this.gridX) % (CARD_WIDTH + GAP) >= CARD_WIDTH
            || (y - this.gridY) % (CARD_HEIGHT + GAP) >= CARD_HEIGHT) {
            return null;
        }
        int index = (row + this.scroll) * this.columns + column;
        return row < visibleRows() && index < this.projects.size() ? this.projects.get(index) : null;
    }

    // --- Actions ------------------------------------------------------------------------------------------------

    private void openSelected() {
        if (this.selected == null) {
            return;
        }
        TutorialTracker.setActiveProject(this.selected);
        this.minecraft.gui.setScreen(new TrackerScreen(null));
    }

    private void exportSelected() {
        if (this.selected == null) {
            return;
        }
        String derived = TutorialFetcher.youtubeThumbnail(TutorialFetcher.youtubeId(this.selected.url));
        String code = ShareCode.export(this.selected, derived);
        this.minecraft.keyboardHandler.setClipboard(code);
        setStatus(Component.translatable("screen.tutorialtracker.archive.copied", code.length()), McStyle.DARK_GREEN);
    }

    private void importFromClipboard() {
        ShareCode.Shared shared = ShareCode.find(this.minecraft.keyboardHandler.getClipboard());
        if (shared == null) {
            setStatus(Component.translatable("screen.tutorialtracker.archive.no_code"), McStyle.DARK_RED);
            return;
        }
        Project project = TutorialTracker.store().create();
        project.name = shared.name();
        project.url = shared.url();
        project.version = shared.version();
        project.thumbnail = shared.thumbnail();
        project.setMaterials(shared.materials());
        TutorialTracker.saveProject(project);
        ContainerTracker.recount();
        this.selected = project;
        this.scroll = 0;
        setStatus(Component.translatable("screen.tutorialtracker.archive.imported", displayName(project)),
            McStyle.DARK_GREEN);
        this.rebuildWidgets();
    }

    private void deleteSelected() {
        Project project = this.selected;
        if (project == null) {
            return;
        }
        this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                boolean wasActive = project == TutorialTracker.activeProject();
                TutorialTracker.store().delete(project);
                if (wasActive) {
                    TutorialTracker.setActiveProject(null);
                }
                this.selected = null;
                setStatus(Component.translatable("screen.tutorialtracker.archive.deleted", displayName(project)),
                    McStyle.LABEL);
            }
            this.minecraft.gui.setScreen(this);
        }, Component.translatable("screen.tutorialtracker.delete_project"),
            Component.translatable("screen.tutorialtracker.delete_project.confirm", displayName(project))));
    }

    private void setStatus(Component text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    private static Component displayName(Project project) {
        return project.name.isBlank() ? Component.translatable("screen.tutorialtracker.unnamed")
            : Component.literal(project.name);
    }

    // --- Input --------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        Project project = cardAt(event.x(), event.y());
        if (project == null) {
            return false;
        }
        if (doubleClick && project == this.selected) {
            openSelected();
        } else {
            select(project);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (y >= this.gridY && y < this.gridY + this.gridHeight) {
            this.scroll = Math.clamp(this.scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // --- Drawing ------------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        McStyle.panel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = this.panelX + PAD;
        Component heading = Component.translatable("screen.tutorialtracker.archive.count", this.projects.size());
        McStyle.label(graphics, this.font, heading, left, this.panelY + 10);
        // The last action's result, between the heading and the import button.
        int statusX = left + this.font.width(heading) + 10;
        int statusRoom = this.panelX + this.panelWidth - PAD - 110 - 6 - statusX;
        McStyle.label(graphics, this.font, HudRenderer.ellipsize(this.font, this.status.getString(), statusRoom),
            statusX, this.panelY + 10, this.statusColor);

        if (this.projects.isEmpty()) {
            graphics.textWithWordWrap(this.font, Component.translatable("screen.tutorialtracker.archive.empty"), left,
                this.gridY + 4, this.panelWidth - 2 * PAD, McStyle.LABEL, false);
        }
        Project hovered = cardAt(mouseX, mouseY);
        Project active = TutorialTracker.activeProject();
        int shown = visibleRows() * this.columns;
        for (int i = 0; i < shown; i++) {
            int index = this.scroll * this.columns + i;
            if (index >= this.projects.size()) {
                break;
            }
            int x = this.gridX + (i % this.columns) * (CARD_WIDTH + GAP);
            int y = this.gridY + (i / this.columns) * (CARD_HEIGHT + GAP);
            drawCard(graphics, this.projects.get(index), x, y, this.projects.get(index) == active);
        }
        if (maxScroll() > 0) {
            // Where in the list the view is, as a thin bar at the right edge.
            int barX = this.panelX + this.panelWidth - PAD + 2;
            int barHeight = Math.max(10, this.gridHeight * visibleRows() / rows());
            int barY = this.gridY + (this.gridHeight - barHeight) * this.scroll / maxScroll();
            graphics.fill(barX, barY, barX + 3, barY + barHeight, McStyle.PANEL_SHADOW);
        }
        Component nameLabel = Component.translatable("screen.tutorialtracker.archive.name");
        Component versionLabel = Component.translatable("screen.tutorialtracker.archive.version");
        McStyle.label(graphics, this.font, nameLabel.getString(), left, this.nameBox.getY() + 6,
            this.selected != null ? McStyle.LABEL : McStyle.LABEL_DISABLED);
        McStyle.label(graphics, this.font, versionLabel.getString(),
            this.versionBox.getX() - this.font.width(versionLabel) - 4, this.versionBox.getY() + 6,
            this.selected != null ? McStyle.LABEL : McStyle.LABEL_DISABLED);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (hovered != null && !hovered.url.isBlank()) {
            graphics.setTooltipForNextFrame(this.font, List.of(displayName(hovered).getVisualOrderText(),
                Component.literal(hovered.url).withColor(McStyle.GRAY).getVisualOrderText()), mouseX, mouseY);
        }
    }

    /** Picture, name, then version and material count; a frame for the selected card, a dot for the open project. */
    private void drawCard(GuiGraphicsExtractor graphics, Project project, int x, int y, boolean active) {
        boolean isSelected = project == this.selected;
        if (isSelected) {
            graphics.fill(x - 1, y - 1, x + CARD_WIDTH + 1, y + CARD_HEIGHT + 1, McStyle.LABEL);
        }
        McStyle.inset(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, McStyle.SLOT);
        int thumbX = x + 2;
        int thumbY = y + 2;
        graphics.fill(thumbX, thumbY, thumbX + THUMB_WIDTH, thumbY + THUMB_HEIGHT, 0xFF101010);
        if (!Thumbnails.draw(graphics, project, thumbX, thumbY, THUMB_WIDTH, THUMB_HEIGHT)) {
            // No picture (yet): the first material's item stands in, large.
            if (!project.materials.isEmpty()) {
                Item item = ItemNameIndex.item(project.materials.get(0).item);
                if (item != null && item.builtInRegistryHolder().areComponentsBound()) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(thumbX + THUMB_WIDTH / 2f - 16, thumbY + THUMB_HEIGHT / 2f - 16);
                    graphics.pose().scale(2, 2);
                    graphics.item(new ItemStack(item), 0, 0);
                    graphics.pose().popMatrix();
                }
            }
        }
        if (active) {
            String mark = "● " + Component.translatable("screen.tutorialtracker.archive.active").getString();
            int markWidth = this.font.width(mark) + 4;
            graphics.fill(thumbX, thumbY, thumbX + markWidth, thumbY + 11, 0xA0000000);
            graphics.text(this.font, mark, thumbX + 2, thumbY + 2, McStyle.GREEN, false);
        }
        String name = displayName(project).getString();
        graphics.text(this.font, HudRenderer.ellipsize(this.font, name, CARD_WIDTH - 4), x + 2, thumbY + THUMB_HEIGHT + 3,
            McStyle.WHITE, true);
        String version = project.version.isBlank()
            ? Component.translatable("screen.tutorialtracker.archive.version_unknown").getString()
            : "MC " + project.version;
        String info = version + " · "
            + Component.translatable("screen.tutorialtracker.archive.items", project.materials.size()).getString();
        graphics.text(this.font, HudRenderer.ellipsize(this.font, info, CARD_WIDTH - 4), x + 2,
            thumbY + THUMB_HEIGHT + 13, isSelected ? McStyle.YELLOW : McStyle.GRAY, true);
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
