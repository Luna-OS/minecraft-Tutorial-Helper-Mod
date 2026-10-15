package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TrackerConfig;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.inventory.ContainerTracker;
import com.lunaos.tutorialtracker.materials.ItemNameIndex;
import com.lunaos.tutorialtracker.project.Project;
import com.lunaos.tutorialtracker.video.VideoPlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The material list and the small video player drawn over the game while building. */
public final class HudRenderer {

    private static final int ROW = 11;
    private static final int LIST_WIDTH = 170;
    private static final int[] VIDEO_WIDTHS = {128, 192, 256};

    private HudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        TrackerConfig config = TutorialTracker.config();
        if (config == null || minecraft.player == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        Screen screen = minecraft.gui.screen();
        // Our own screens show the list and the video themselves.
        boolean ownScreen = screen instanceof TrackerScreen || screen instanceof VideoScreen;
        Project project = TutorialTracker.activeProject();
        if (config.hudVisible && project != null && !project.materials.isEmpty() && !ownScreen) {
            renderList(graphics, minecraft.font, project, config);
        }
        VideoPlayer player = VideoPlayer.get();
        if (config.videoInHud && !ownScreen && (player.isActive() || player.state() == VideoPlayer.State.ERROR)) {
            renderVideo(graphics, minecraft.font, player, config);
        }
    }

    private static void renderList(GuiGraphicsExtractor graphics, Font font, Project project, TrackerConfig config) {
        List<Project.Material> open = new ArrayList<>();
        List<Project.Material> done = new ArrayList<>();
        for (Project.Material material : project.materials) {
            (ContainerTracker.have(material.item) >= material.needed ? done : open).add(material);
        }
        List<Project.Material> rows = new ArrayList<>(open);
        if (!config.hideDone) {
            rows.addAll(done);
        }
        int shown = Math.min(rows.size(), config.hudRows);
        int height = 12 + Math.max(1, shown) * ROW + (rows.size() > shown ? ROW : 0) - 1;
        // Inside the tooltip frame, which takes 4 pixels around the box.
        int x = config.hudRight ? graphics.guiWidth() - LIST_WIDTH - 6 : 6;
        // Below the status effect icons on the right.
        int y = config.hudRight ? 30 : 6;
        McStyle.tooltipBox(graphics, x, y, LIST_WIDTH, height);

        String title = project.name.isBlank() ? Component.translatable("hud.tutorialtracker.materials").getString()
            : project.name;
        String counter = " " + done.size() + "/" + project.materials.size();
        int titleWidth = LIST_WIDTH - font.width(counter);
        graphics.text(font, ellipsize(font, title, titleWidth), x, y, McStyle.YELLOW, true);
        graphics.text(font, counter, x + LIST_WIDTH - font.width(counter), y,
            done.size() == project.materials.size() ? McStyle.GREEN : McStyle.GRAY, true);

        int rowY = y + 12;
        if (shown == 0) {
            graphics.text(font, Component.translatable("hud.tutorialtracker.all_done"), x, rowY + 1, McStyle.GREEN, true);
        }
        for (int i = 0; i < shown; i++) {
            drawRow(graphics, font, rows.get(i), x, rowY, LIST_WIDTH);
            rowY += ROW;
        }
        if (rows.size() > shown) {
            graphics.text(font, Component.translatable("hud.tutorialtracker.more", rows.size() - shown), x, rowY + 1,
                McStyle.GRAY, true);
        }
    }

    /** One material: small icon, "have/needed", name. Green when there is enough. */
    static void drawRow(GuiGraphicsExtractor graphics, Font font, Project.Material material, int x, int y, int width) {
        Item item = ItemNameIndex.item(material.item);
        int have = ContainerTracker.have(material.item);
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(0.625f, 0.625f);
            graphics.item(stack, 0, 0);
            graphics.pose().popMatrix();
        }
        int color = McStyle.amountColor(have, material.needed);
        String amount = format(have) + "/" + format(material.needed);
        int textX = x + 13;
        graphics.text(font, amount, textX, y + 1, color, true);
        int nameX = textX + font.width(amount) + 4;
        String name = item != null ? new ItemStack(item).getHoverName().getString() : material.item;
        graphics.text(font, ellipsize(font, name, x + width - nameX), nameX, y + 1, McStyle.WHITE, true);
    }

    private static void renderVideo(GuiGraphicsExtractor graphics, Font font, VideoPlayer player, TrackerConfig config) {
        int width = Math.min(VIDEO_WIDTHS[config.videoSize], graphics.guiWidth() / 2);
        int height = Math.round(width / player.aspect());
        boolean right = config.videoCorner == 1 || config.videoCorner == 3;
        boolean bottom = config.videoCorner >= 2;
        int x = right ? graphics.guiWidth() - width - 6 : 6;
        int y = bottom ? graphics.guiHeight() - height - 13 - (right ? 0 : 40) : 6;
        if (!bottom && right && config.hudVisible && config.hudRight) {
            // Under the material list when both are on the right.
            y = Math.min(graphics.guiHeight() - height - 13, 30 + 12 + (config.hudRows + 1) * ROW + 12);
        }
        if (!bottom && !right && config.hudVisible && !config.hudRight) {
            y = Math.min(graphics.guiHeight() - height - 13, 6 + 12 + (config.hudRows + 1) * ROW + 12);
        }
        McStyle.tooltipBox(graphics, x, y, width, height + 7);
        player.draw(graphics, x, y, width, height, true);
        // Progress under the picture, as the experience bar.
        double duration = player.duration();
        McStyle.xpBar(graphics, x, y + height + 2, width, duration > 0 ? player.position() / duration : 0);
    }

    static String format(int count) {
        return count >= 10_000 ? (count / 1000) + "k" : Integer.toString(count);
    }

    static String time(double seconds) {
        int total = (int) Math.max(0, seconds);
        int hours = total / 3600;
        int minutes = total / 60 % 60;
        int secs = total % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, secs) : String.format("%d:%02d", minutes, secs);
    }

    static String ellipsize(Font font, String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        String dots = "…";
        int available = width - font.width(dots);
        String cut = font.plainSubstrByWidth(text, Math.max(0, available));
        return cut + dots;
    }
}
