package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TrackerConfig;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.project.Project;
import com.lunaos.tutorialtracker.video.VideoPlayer;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The two windows over the game, the material list and the video: placed and sized by the player (see
 * {@link PopupEditScreen}), drawn with a title bar in the tooltip's frame. Positions are GUI coordinates kept in the
 * config; a window that no longer fits the screen is pushed back onto it.
 */
public final class Popups {

    public enum Kind {
        LIST,
        VIDEO
    }

    /** A window's outer area: title bar and body, without the frame drawn around it. */
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= this.x && px < this.x + this.width && py >= this.y && py < this.y + this.height;
        }
    }

    static final int TITLE = 12;
    static final int ROW = 11;
    /** Room the tooltip frame takes around a window. */
    private static final int MARGIN = 6;
    /** The square at the bottom right corner that resizes a window. */
    static final int GRIP = 8;
    static final int BUTTON = 10;

    public static final int MIN_LIST_WIDTH = 110;
    public static final int MAX_LIST_WIDTH = 400;
    public static final int MIN_ROWS = 3;
    public static final int MAX_ROWS = 30;
    public static final int MIN_VIDEO_WIDTH = 96;

    private Popups() {
    }

    // --- Where the windows are ----------------------------------------------------------------------------------

    /** The list window. In edit mode it is as tall as its rows allow, otherwise only as tall as its contents. */
    public static Rect listRect(int screenWidth, int screenHeight, Project project, boolean edit) {
        TrackerConfig config = TutorialTracker.config();
        int width = Math.clamp(config.listWidth, MIN_LIST_WIDTH, Math.max(MIN_LIST_WIDTH, screenWidth - 2 * MARGIN));
        int rows = config.hudRows;
        if (!edit && project != null) {
            int content = ListView.of(project).rows.size();
            // One more line for "... and N more" when not all fit.
            rows = content > config.hudRows ? config.hudRows + 1 : Math.max(1, content);
        }
        int height = TITLE + rows * ROW + 2;
        int x = config.listX >= 0 ? config.listX : MARGIN;
        int y = config.listY >= 0 ? config.listY : MARGIN;
        return clampToScreen(x, y, width, height, screenWidth, screenHeight);
    }

    /** The video window: title bar, the picture at the video's shape, and the progress bar under it. */
    public static Rect videoRect(int screenWidth, int screenHeight) {
        TrackerConfig config = TutorialTracker.config();
        int width = Math.clamp(config.videoWidth, MIN_VIDEO_WIDTH, Math.max(MIN_VIDEO_WIDTH, screenWidth - 2 * MARGIN));
        int pictureHeight = Math.round(width / VideoPlayer.get().aspect());
        int height = TITLE + pictureHeight + 7;
        // Top right, below the status effect icons, until the player moves it.
        int x = config.videoX >= 0 ? config.videoX : screenWidth - width - MARGIN;
        int y = config.videoY >= 0 ? config.videoY : 30;
        return clampToScreen(x, y, width, height, screenWidth, screenHeight);
    }

    private static Rect clampToScreen(int x, int y, int width, int height, int screenWidth, int screenHeight) {
        int maxX = Math.max(MARGIN, screenWidth - width - MARGIN);
        int maxY = Math.max(MARGIN, screenHeight - height - MARGIN);
        return new Rect(Math.clamp(x, MARGIN, maxX), Math.clamp(y, MARGIN, maxY), width, height);
    }

    // --- Drawing ------------------------------------------------------------------------------------------------

    /** The frame and title bar every window has; in edit mode also the resize grip. */
    private static void frame(GuiGraphicsExtractor graphics, Font font, Rect rect, String title, int titleColor,
                              String right, int rightColor, boolean edit, int controlsWidth) {
        McStyle.tooltipBox(graphics, rect.x(), rect.y(), rect.width(), rect.height());
        // Title bar, a shade darker than the body, with a line under it.
        graphics.fill(rect.x() - 1, rect.y() - 1, rect.x() + rect.width() + 1, rect.y() + TITLE - 1, 0x40000000);
        graphics.fill(rect.x(), rect.y() + TITLE - 1, rect.x() + rect.width(), rect.y() + TITLE, 0x605A2E9E);
        int rightEdge = rect.x() + rect.width() - controlsWidth;
        int rightWidth = right.isEmpty() ? 0 : font.width(right) + 3;
        if (!right.isEmpty()) {
            graphics.text(font, right, rightEdge - font.width(right), rect.y() + 1, rightColor, true);
        }
        graphics.text(font, HudRenderer.ellipsize(font, title, rightEdge - rightWidth - rect.x() - 1), rect.x() + 1,
            rect.y() + 1, titleColor, true);
        if (edit) {
            // The grip: three steps of dots in the bottom right corner.
            int gx = rect.x() + rect.width();
            int gy = rect.y() + rect.height();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j <= i; j++) {
                    graphics.fill(gx - 2 - 2 * (i - j) - 1, gy - 2 - 2 * j - 1, gx - 2 - 2 * (i - j), gy - 2 - 2 * j,
                        0xFFC0C0C0);
                }
            }
        }
    }

    /** A small button in a title bar. */
    private static void titleButton(GuiGraphicsExtractor graphics, Font font, int x, int y, String label,
                                    boolean hovered, int color) {
        graphics.fill(x, y, x + BUTTON, y + BUTTON, hovered ? 0xA0FFFFFF : 0x50FFFFFF);
        graphics.outline(x, y, BUTTON, BUTTON, 0xFF000000);
        graphics.centeredText(font, label, x + BUTTON / 2 + 1, y + 1, color);
    }

    /** Where a window's close button is (with controls). */
    static Rect closeButton(Rect rect) {
        return new Rect(rect.x() + rect.width() - BUTTON - 1, rect.y() + 1, BUTTON, BUTTON);
    }

    /** The video window's back, play/pause and forward buttons (with controls), left of the close button. */
    static Rect videoButton(Rect rect, int index) {
        Rect close = closeButton(rect);
        return new Rect(close.x() - (3 - index) * (BUTTON + 2), close.y(), BUTTON, BUTTON);
    }

    /**
     * The list window. {@code edit} adds the resize grip, {@code controls} the close button (the settings' small
     * preview has the grip but no buttons, they would be too small to hit there).
     */
    public static void drawList(GuiGraphicsExtractor graphics, Font font, Project project, Rect rect, boolean edit,
                                boolean controls, int mouseX, int mouseY) {
        String title = project == null || project.name.isBlank()
            ? Component.translatable("hud.tutorialtracker.materials").getString() : project.name;
        ListView view = ListView.of(project);
        List<ListView.Row> rows = view.rows;
        int done = view.done;
        int total = view.total;
        String counter = total == 0 ? "" : done + "/" + total;
        frame(graphics, font, rect, title, McStyle.YELLOW, counter,
            total > 0 && done == total ? McStyle.GREEN : McStyle.GRAY, edit, controls ? BUTTON + 3 : 0);
        if (controls) {
            Rect close = closeButton(rect);
            titleButton(graphics, font, close.x(), close.y(), "✕", close.contains(mouseX, mouseY), McStyle.RED);
        }

        int rowY = rect.y() + TITLE + 1;
        int bodyRows = (rect.height() - TITLE - 2) / ROW;
        if (total == 0) {
            graphics.textWithWordWrap(font, Component.translatable("popup.tutorialtracker.no_materials"), rect.x() + 1,
                rowY + 1, rect.width() - 2, McStyle.GRAY, true);
            return;
        }
        if (rows.isEmpty()) {
            graphics.text(font, Component.translatable("hud.tutorialtracker.all_done"), rect.x() + 1, rowY + 1,
                McStyle.GREEN, true);
            return;
        }
        boolean more = rows.size() > bodyRows;
        int shown = more ? bodyRows - 1 : rows.size();
        for (int i = 0; i < shown; i++) {
            HudRenderer.drawRow(graphics, font, rows.get(i), rect.x() + 1, rowY, rect.width() - 2);
            rowY += ROW;
        }
        if (more) {
            graphics.text(font, Component.translatable("hud.tutorialtracker.more", rows.size() - shown), rect.x() + 1,
                rowY + 1, McStyle.GRAY, true);
        }
    }

    /** The video window; {@code edit} and {@code controls} as for {@link #drawList}. */
    public static void drawVideo(GuiGraphicsExtractor graphics, Font font, Rect rect, boolean edit, boolean controls,
                                 int mouseX, int mouseY) {
        VideoPlayer player = VideoPlayer.get();
        String title = player.title().isEmpty() ? Component.translatable("screen.tutorialtracker.video").getString()
            : player.title();
        String time = player.duration() > 0
            ? HudRenderer.time(player.position()) + "/" + HudRenderer.time(player.duration()) : "";
        int controlsWidth = controls ? 4 * (BUTTON + 2) + 1 : 0;
        frame(graphics, font, rect, title, McStyle.YELLOW, controls ? "" : time, McStyle.GRAY, edit, controlsWidth);
        if (controls) {
            boolean playing = player.state() == VideoPlayer.State.PLAYING || player.state() == VideoPlayer.State.LOADING;
            String[] labels = {"«", playing ? "⏸" : "▶", "»"};
            for (int i = 0; i < 3; i++) {
                Rect button = videoButton(rect, i);
                titleButton(graphics, font, button.x(), button.y(), labels[i], button.contains(mouseX, mouseY),
                    McStyle.WHITE);
            }
            Rect close = closeButton(rect);
            titleButton(graphics, font, close.x(), close.y(), "✕", close.contains(mouseX, mouseY), McStyle.RED);
        }
        int pictureY = rect.y() + TITLE;
        int pictureHeight = rect.height() - TITLE - 7;
        player.draw(graphics, rect.x(), pictureY, rect.width(), pictureHeight, true);
        double duration = player.duration();
        McStyle.xpBar(graphics, rect.x(), pictureY + pictureHeight + 2, rect.width(),
            duration > 0 ? player.position() / duration : 0);
    }
}
