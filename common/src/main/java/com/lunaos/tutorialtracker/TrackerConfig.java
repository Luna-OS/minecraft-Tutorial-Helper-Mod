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
    /** Where the material list window is, in GUI coordinates; -1 until the player moves it. */
    public int listX = -1;
    public int listY = -1;
    public int listWidth = 170;
    public boolean hideDone = false;
    /** How many materials the list window shows. */
    public int hudRows = 10;
    /** Also count what the player carries, not only the assigned containers. */
    public boolean countInventory = true;
    public boolean videoInHud = true;
    /** Where the video window is, in GUI coordinates; -1 until the player moves it. */
    public int videoX = -1;
    public int videoY = -1;
    public int videoWidth = 192;
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
        config.volume = Math.clamp(config.volume, 0.0f, 1.0f);
        return config;
    }

    /** Puts both windows back where they start out. */
    public void resetWindows() {
        TrackerConfig defaults = new TrackerConfig();
        this.listX = defaults.listX;
        this.listY = defaults.listY;
        this.listWidth = defaults.listWidth;
        this.hudRows = defaults.hudRows;
        this.videoX = defaults.videoX;
        this.videoY = defaults.videoY;
        this.videoWidth = defaults.videoWidth;
        this.hudVisible = true;
        this.videoInHud = true;
    }

    /** Saved in the background: see {@link DiskWriter}. */
    public void save() {
        DiskWriter.write(this.file, GSON.toJson(this));
    }
}
