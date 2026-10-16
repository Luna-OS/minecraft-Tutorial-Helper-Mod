package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TrackerConfig;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.project.Project;
import com.lunaos.tutorialtracker.video.VideoPlayer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** The material list and the video windows (see {@link Popups}) drawn over the game while building. */
public final class HudRenderer {

    private HudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        TrackerConfig config = TutorialTracker.config();
        if (config == null || minecraft.player == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        Screen screen = minecraft.gui.screen();
        // Our own screens show the list and the video themselves; the arranging screen draws them with its controls.
        if (screen instanceof TrackerScreen || screen instanceof VideoScreen || screen instanceof PopupEditScreen) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Project project = TutorialTracker.activeProject();
        if (config.hudVisible && project != null && !project.materials.isEmpty()) {
            Popups.drawList(graphics, minecraft.font, project, Popups.listRect(width, height, project, false), false,
                false, -1, -1);
        }
        VideoPlayer player = VideoPlayer.get();
        if (config.videoInHud && (player.isActive() || player.state() == VideoPlayer.State.ERROR)) {
            Popups.drawVideo(graphics, minecraft.font, Popups.videoRect(width, height), false, false, -1, -1);
        }
    }

    /** One material: small icon, "have/needed", name. Green when there is enough. */
    static void drawRow(GuiGraphicsExtractor graphics, Font font, ListView.Row row, int x, int y, int width) {
        if (row.icon != null) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(0.625f, 0.625f);
            graphics.item(row.icon, 0, 0);
            graphics.pose().popMatrix();
        }
        int textX = x + 13;
        graphics.text(font, row.amount, textX, y + 1, row.color, true);
        int nameX = textX + font.width(row.amount) + 4;
        graphics.text(font, row.fittedName(font, x + width - nameX), nameX, y + 1, McStyle.WHITE, true);
    }

    /** In place of a time for a live stream. */
    static final String LIVE = "● LIVE";

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
