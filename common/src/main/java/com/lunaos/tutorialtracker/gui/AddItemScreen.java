package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.inventory.ContainerTracker;
import com.lunaos.tutorialtracker.materials.ItemNameIndex;
import com.lunaos.tutorialtracker.project.Project;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Adds an item the description missed: search by name, click to add the amount typed. */
public class AddItemScreen extends Screen {

    private static final int CELL = 18;

    private final Screen parent;
    private final Project project;
    private EditBox searchBox;
    private EditBox amountBox;
    private String query = "";
    private String amount = "1";
    private List<Item> results = List.of();
    private int gridX;
    private int gridY;
    private int columns;
    private int rows;
    private int scrollRow;
    private Component status = Component.empty();
    private int panelX;
    private int panelWidth;

    public AddItemScreen(Screen parent, Project project) {
        super(Component.translatable("screen.tutorialtracker.add_item.title"));
        this.parent = parent;
        this.project = project;
    }

    @Override
    protected void init() {
        int width = Math.min(9 * CELL * 2, this.width - 36);
        int x = (this.width - width) / 2;
        this.panelX = x - 8;
        this.panelWidth = width + 16;
        this.searchBox = new EditBox(this.font, x, 20, width - 90, 20, Component.translatable("screen.tutorialtracker.search"));
        this.searchBox.setHint(Component.translatable("screen.tutorialtracker.search"));
        this.searchBox.setValue(this.query);
        this.searchBox.setResponder(value -> {
            this.query = value;
            this.scrollRow = 0;
            search();
        });
        this.addRenderableWidget(this.searchBox);
        this.amountBox = new EditBox(this.font, x + width - 86, 20, 86, 20, Component.translatable("screen.tutorialtracker.amount"));
        this.amountBox.setValue(this.amount);
        this.amountBox.setResponder(value -> this.amount = value);
        this.addRenderableWidget(this.amountBox);
        this.setInitialFocus(this.searchBox);

        this.columns = Math.max(1, width / CELL);
        this.gridX = x + (width - this.columns * CELL) / 2;
        this.gridY = 46;
        this.rows = Math.max(1, (this.height - 46 - 64) / CELL);

        int half = (width - 2) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.tutorialtracker.add_item.held"), b -> {
            if (this.minecraft.player != null && !this.minecraft.player.getMainHandItem().isEmpty()) {
                add(this.minecraft.player.getMainHandItem().getItem());
            }
        }).bounds(x, this.height - 54, half, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
            .bounds(x + half + 2, this.height - 54, width - half - 2, 20).build());
        search();
    }

    private void search() {
        this.results = ItemNameIndex.get().search(this.query, 2000);
    }

    private void add(Item item) {
        int count;
        try {
            count = Math.max(1, Integer.parseInt(this.amount));
        } catch (NumberFormatException e) {
            count = 1;
        }
        this.project.addMaterial(ItemNameIndex.id(item), count);
        TutorialTracker.saveProject(this.project);
        ContainerTracker.recount();
        this.status = Component.translatable("screen.tutorialtracker.add_item.added", count,
            new ItemStack(item).getHoverName());
    }

    private int maxScrollRow() {
        int totalRows = (this.results.size() + this.columns - 1) / this.columns;
        return Math.max(0, totalRows - this.rows);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        this.scrollRow = Math.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, maxScrollRow());
        return true;
    }

    private int indexAt(double x, double y) {
        if (x < this.gridX || y < this.gridY || x >= this.gridX + this.columns * CELL || y >= this.gridY + this.rows * CELL) {
            return -1;
        }
        int column = (int) ((x - this.gridX) / CELL);
        int row = (int) ((y - this.gridY) / CELL) + this.scrollRow;
        int index = row * this.columns + column;
        return index < this.results.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int index = indexAt(event.x(), event.y());
        if (index >= 0) {
            add(this.results.get(index));
            return true;
        }
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        McStyle.panel(graphics, this.panelX, 4, this.panelWidth, this.height - 8);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        McStyle.label(graphics, this.font, this.title, this.panelX + 8, 9);
        // Slots like the creative inventory.
        int start = this.scrollRow * this.columns;
        for (int i = 0; i < this.rows * this.columns; i++) {
            int x = this.gridX + (i % this.columns) * CELL;
            int y = this.gridY + (i / this.columns) * CELL;
            McStyle.slot(graphics, x, y);
            if (start + i < this.results.size()) {
                graphics.item(new ItemStack(this.results.get(start + i)), x + 1, y + 1);
            }
        }
        int hovered = indexAt(mouseX, mouseY);
        if (hovered >= 0) {
            int i = hovered - start;
            int x = this.gridX + (i % this.columns) * CELL;
            int y = this.gridY + (i / this.columns) * CELL;
            graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x80FFFFFF);
            graphics.setTooltipForNextFrame(this.font, new ItemStack(this.results.get(hovered)).getHoverName(), mouseX, mouseY);
        }
        String shown = HudRenderer.ellipsize(this.font, this.status.getString(), this.panelWidth - 16);
        McStyle.label(graphics, this.font, shown, this.panelX + 8, this.height - 28, McStyle.DARK_GREEN);
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
