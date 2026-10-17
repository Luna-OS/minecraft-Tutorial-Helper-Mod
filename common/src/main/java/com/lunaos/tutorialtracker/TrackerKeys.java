package com.lunaos.tutorialtracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** The mod's key bindings, in their own section of the Controls screen. Each loader registers them. */
public final class TrackerKeys {

    public static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tutorialtracker", "tutorialtracker"));

    public static final KeyMapping OPEN = new KeyMapping("key.tutorialtracker.open", InputConstants.KEY_B, CATEGORY);
    public static final KeyMapping TOGGLE_HUD =
        new KeyMapping("key.tutorialtracker.toggle_hud", InputConstants.KEY_H, CATEGORY);
    public static final KeyMapping ASSIGN_CONTAINER =
        new KeyMapping("key.tutorialtracker.assign_container", InputConstants.KEY_K, CATEGORY);
    public static final KeyMapping PLAY_PAUSE =
        new KeyMapping("key.tutorialtracker.play_pause", InputConstants.KEY_J, CATEGORY);
    public static final KeyMapping ARRANGE =
        new KeyMapping("key.tutorialtracker.arrange", InputConstants.KEY_U, CATEGORY);
    public static final KeyMapping SEEK_BACK =
        new KeyMapping("key.tutorialtracker.seek_back", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping SEEK_FORWARD =
        new KeyMapping("key.tutorialtracker.seek_forward", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping VIDEO_SCREEN =
        new KeyMapping("key.tutorialtracker.video_screen", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping TOGGLE_VIDEO =
        new KeyMapping("key.tutorialtracker.toggle_video", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping ARCHIVE =
        new KeyMapping("key.tutorialtracker.archive", InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static final KeyMapping[] ALL = {OPEN, TOGGLE_HUD, ASSIGN_CONTAINER, PLAY_PAUSE, ARRANGE, SEEK_BACK,
        SEEK_FORWARD, VIDEO_SCREEN, TOGGLE_VIDEO, ARCHIVE};

    private TrackerKeys() {
    }
}
