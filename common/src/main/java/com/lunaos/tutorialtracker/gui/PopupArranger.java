package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TrackerConfig;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.video.VideoPlayer;
import net.minecraft.client.input.KeyEvent;

/**
 * Moving and resizing the windows with the mouse or the keyboard, in the coordinates of the game's screen. Shared by
 * the full screen editor ({@link PopupEditScreen}) and the small preview in the settings ({@link SettingsScreen}),
 * which hands in its mouse positions scaled up to the screen it stands for.
 */
final class PopupArranger {

    private enum Drag {
        MOVE,
        RESIZE
    }

    /** What a press inside a window did besides starting a drag, so a screen can update its buttons. */
    enum Press {
        NONE,
        DRAG,
        CLOSED,
        VIDEO_CONTROL
    }

    /** How far the frame drawn around a window reaches past it. */
    private static final int FRAME = 4;

    private Popups.Kind dragging;
    private Drag dragMode;
    private double grabX;
    private double grabY;
    /** The window the keyboard moves: the last one clicked. */
    private Popups.Kind selected;

    Popups.Kind dragging() {
        return this.dragging;
    }

    Popups.Kind selected() {
        return this.selected;
    }

    void deselect() {
        this.selected = null;
    }

    static Popups.Rect rect(Popups.Kind kind, int screenWidth, int screenHeight) {
        return kind == Popups.Kind.LIST
            ? Popups.listRect(screenWidth, screenHeight, TutorialTracker.activeProject(), true)
            : Popups.videoRect(screenWidth, screenHeight);
    }

    static boolean shown(Popups.Kind kind) {
        TrackerConfig config = TutorialTracker.config();
        return kind == Popups.Kind.LIST ? config.hudVisible : config.videoInHud;
    }

    /** The topmost shown window at a point: the video is drawn over the list. */
    static Popups.Kind windowAt(double x, double y, int screenWidth, int screenHeight) {
        for (Popups.Kind kind : new Popups.Kind[] {Popups.Kind.VIDEO, Popups.Kind.LIST}) {
            if (shown(kind) && rect(kind, screenWidth, screenHeight).contains(x, y)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * A left click. With {@code controls} the title bar buttons work (close, and the video's back, play and forward);
     * the preview leaves them out, they are too small there. {@code grip} is the size of the resize corner, larger in
     * the preview so it can still be hit.
     */
    Press press(double x, double y, int screenWidth, int screenHeight, boolean controls, double grip) {
        // The corner first: it can be grabbed on the frame drawn around the window too.
        for (Popups.Kind kind : new Popups.Kind[] {Popups.Kind.VIDEO, Popups.Kind.LIST}) {
            if (shown(kind)) {
                Popups.Rect rect = rect(kind, screenWidth, screenHeight);
                int right = rect.x() + rect.width();
                int bottom = rect.y() + rect.height();
                if (x >= right - grip && x < right + FRAME && y >= bottom - grip && y < bottom + FRAME) {
                    this.selected = kind;
                    start(kind, Drag.RESIZE, x - right, y - bottom);
                    return Press.DRAG;
                }
            }
        }
        Popups.Kind kind = windowAt(x, y, screenWidth, screenHeight);
        this.selected = kind;
        if (kind == null) {
            return Press.NONE;
        }
        Popups.Rect rect = rect(kind, screenWidth, screenHeight);
        TrackerConfig config = TutorialTracker.config();
        if (controls) {
            if (Popups.closeButton(rect).contains(x, y)) {
                if (kind == Popups.Kind.LIST) {
                    config.hudVisible = false;
                } else {
                    config.videoInHud = false;
                }
                config.save();
                this.selected = null;
                return Press.CLOSED;
            }
            if (kind == Popups.Kind.VIDEO) {
                VideoPlayer player = VideoPlayer.get();
                if (Popups.videoButton(rect, 0).contains(x, y)) {
                    player.seekRelative(-10);
                    return Press.VIDEO_CONTROL;
                }
                if (Popups.videoButton(rect, 1).contains(x, y)) {
                    TutorialTracker.playOrPause();
                    return Press.VIDEO_CONTROL;
                }
                if (Popups.videoButton(rect, 2).contains(x, y)) {
                    player.seekRelative(10);
                    return Press.VIDEO_CONTROL;
                }
            }
        }
        if (controls && kind == Popups.Kind.VIDEO && y >= rect.y() + Popups.TITLE
            && y < rect.y() + rect.height() - 7) {
            // A click on the picture plays or pauses; the title bar moves the window.
            TutorialTracker.playOrPause();
            return Press.VIDEO_CONTROL;
        }
        // The title bar or the body: either moves the window.
        start(kind, Drag.MOVE, x - rect.x(), y - rect.y());
        return Press.DRAG;
    }

    private void start(Popups.Kind kind, Drag mode, double grabX, double grabY) {
        this.dragging = kind;
        this.dragMode = mode;
        this.grabX = grabX;
        this.grabY = grabY;
    }

    /** The mouse moved with the button held: the window follows it, or its corner does. */
    boolean drag(double x, double y, int screenWidth, int screenHeight) {
        if (this.dragging == null) {
            return false;
        }
        Popups.Rect rect = rect(this.dragging, screenWidth, screenHeight);
        if (this.dragMode == Drag.MOVE) {
            moveTo(this.dragging, (int) Math.round(x - this.grabX), (int) Math.round(y - this.grabY));
        } else {
            resizeTo(this.dragging, rect, (int) Math.round(x - this.grabX - rect.x()),
                (int) Math.round(y - this.grabY - rect.y()), screenWidth);
        }
        return true;
    }

    /** The button was let go: store where the window really is, after being kept on screen. */
    boolean release(int screenWidth, int screenHeight) {
        if (this.dragging == null) {
            return false;
        }
        pin(this.dragging, rect(this.dragging, screenWidth, screenHeight));
        this.dragging = null;
        TutorialTracker.config().save();
        return true;
    }

    /**
     * The arrow keys move the selected window, by 1 or with Shift by 10; with Alt (Option on a Mac) they make it
     * larger or smaller instead. For trackpads and anyone who wants it exact.
     */
    boolean key(KeyEvent event, int screenWidth, int screenHeight) {
        if (this.selected == null || !shown(this.selected)
            || !(event.isLeft() || event.isRight() || event.isUp() || event.isDown())) {
            return false;
        }
        int step = event.hasShiftDown() ? 10 : 1;
        int dx = event.isLeft() ? -step : event.isRight() ? step : 0;
        int dy = event.isUp() ? -step : event.isDown() ? step : 0;
        Popups.Rect rect = rect(this.selected, screenWidth, screenHeight);
        if (event.hasAltDown()) {
            // Rows of the list change one at a time, whatever the step.
            int rowStep = this.selected == Popups.Kind.LIST ? Integer.signum(dy) * Popups.ROW : dy;
            resizeTo(this.selected, rect, rect.width() + dx, rect.height() + rowStep, screenWidth);
        } else {
            moveTo(this.selected, rect.x() + dx, rect.y() + dy);
        }
        pin(this.selected, rect(this.selected, screenWidth, screenHeight));
        TutorialTracker.config().save();
        return true;
    }

    private static void moveTo(Popups.Kind kind, int x, int y) {
        TrackerConfig config = TutorialTracker.config();
        if (kind == Popups.Kind.LIST) {
            config.listX = x;
            config.listY = y;
        } else {
            config.videoX = x;
            config.videoY = y;
        }
    }

    private static void resizeTo(Popups.Kind kind, Popups.Rect rect, int width, int height, int screenWidth) {
        TrackerConfig config = TutorialTracker.config();
        if (kind == Popups.Kind.LIST) {
            config.listWidth = Math.clamp(width, Popups.MIN_LIST_WIDTH, Popups.MAX_LIST_WIDTH);
            config.hudRows = Math.clamp((height - Popups.TITLE - 2 + Popups.ROW / 2) / Popups.ROW, Popups.MIN_ROWS,
                Popups.MAX_ROWS);
        } else {
            // The video keeps its shape: only the width counts.
            config.videoWidth = Math.clamp(width, Popups.MIN_VIDEO_WIDTH,
                Math.max(Popups.MIN_VIDEO_WIDTH, screenWidth - rect.x() - 6));
        }
        // Resizing never moves the window: pin it where it is.
        pin(kind, rect);
    }

    private static void pin(Popups.Kind kind, Popups.Rect rect) {
        moveTo(kind, rect.x(), rect.y());
    }
}
