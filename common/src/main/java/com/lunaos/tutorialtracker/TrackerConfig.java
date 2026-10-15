package com.lunaos.tutorialtracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The player's settings, in {@code config/tutorialtracker/settings.json}. */
public final class TrackerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean hudVisible = true;
    /** Material list on the right side of the screen instead of the left. */
    public boolean hudRight = false;
    public boolean hideDone = false;
    public int hudRows = 10;
    /** Also count what the player carries, not only the assigned containers. */
    public boolean countInventory = true;
    public boolean videoInHud = true;
    /** 0 top left, 1 top right, 2 bottom left, 3 bottom right. */
    public int videoCorner = 1;
    /** 0 small, 1 medium, 2 large. */
    public int videoSize = 1;
    /** Highest video height fetched: 360, 480, 720. */
    public int videoQuality = 480;
    public float volume = 0.8f;
    public String activeProject = "";

    private transient Path file;

    public static TrackerConfig load(Path file) {
        TrackerConfig config = null;
        try {
            if (Files.exists(file)) {
                config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TrackerConfig.class);
            }
        } catch (Exception e) {
            TutorialTracker.LOGGER.warn("Could not read {}, using defaults", file, e);
        }
        if (config == null) {
            config = new TrackerConfig();
        }
        config.file = file;
        if (config.activeProject == null) {
            config.activeProject = "";
        }
        config.hudRows = Math.clamp(config.hudRows, 3, 30);
        config.videoCorner = Math.clamp(config.videoCorner, 0, 3);
        config.videoSize = Math.clamp(config.videoSize, 0, 2);
        config.volume = Math.clamp(config.volume, 0.0f, 1.0f);
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(this.file.getParent());
            Files.writeString(this.file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TutorialTracker.LOGGER.warn("Could not save {}", this.file, e);
        }
    }
}
