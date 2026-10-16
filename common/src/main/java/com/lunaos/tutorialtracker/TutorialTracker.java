package com.lunaos.tutorialtracker;

import com.lunaos.tutorialtracker.gui.ContainerButton;
import com.lunaos.tutorialtracker.gui.TrackerScreen;
import com.lunaos.tutorialtracker.gui.VideoScreen;
import com.lunaos.tutorialtracker.inventory.ContainerTracker;
import com.lunaos.tutorialtracker.materials.ItemNameIndex;
import com.lunaos.tutorialtracker.materials.MaterialParser;
import com.lunaos.tutorialtracker.project.Project;
import com.lunaos.tutorialtracker.project.ProjectStore;
import com.lunaos.tutorialtracker.source.TutorialFetcher;
import com.lunaos.tutorialtracker.video.VideoPlayer;
import com.lunaos.tutorialtracker.video.VideoTools;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/**
 * Tutorial Tracker: paste a tutorial link, get its material list, see how much of it is in the chests and shulker
 * boxes you assign, and watch the video in game while building. Client only; each loader calls {@link #init},
 * {@link #onEndClientTick} and the HUD and screen hooks.
 */
public final class TutorialTracker {

    public static final String MOD_ID = "tutorialtracker";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static TrackerConfig config;
    private static ProjectStore store;
    private static boolean dirty;
    private static long lastSave;
    private static long lastPositionSave;

    private TutorialTracker() {
    }

    /**
     * Called by each loader at start: the game folder, the loader's name, the mod's version and the jar it was loaded
     * from (null when unknown, as in a development setup).
     */
    public static void init(Path gameDir, String loader, String version, Path jar) {
        Path configDir = gameDir.resolve("config").resolve(MOD_ID);
        config = TrackerConfig.load(configDir.resolve("settings.json"));
        com.lunaos.tutorialtracker.gui.McStyle.applyTheme(config.darkMode);
        store = new ProjectStore(configDir.resolve("projects"));
        VideoTools.init(gameDir.resolve(MOD_ID).resolve("bin"));
        Updater.init(loader, version, jar, config.autoUpdate);
        LOGGER.info("Tutorial Tracker {} loaded, {} project(s)", version, store.all().size());
    }

    public static TrackerConfig config() {
        return config;
    }

    public static ProjectStore store() {
        return store;
    }

    // --- Projects -----------------------------------------------------------------------------------------------

    /** The project the HUD shows; the most recent one when none was chosen. */
    public static Project activeProject() {
        Project project = store.byId(config.activeProject);
        if (project == null && !store.all().isEmpty()) {
            project = store.all().get(store.all().size() - 1);
            config.activeProject = project.id;
        }
        return project;
    }

    public static void setActiveProject(Project project) {
        if (project != activeProject()) {
            VideoPlayer.get().stop();
        }
        config.activeProject = project == null ? "" : project.id;
        config.save();
        ContainerTracker.recount();
    }

    public static Project activeOrNewProject() {
        Project project = activeProject();
        if (project == null) {
            project = store.create();
            setActiveProject(project);
        }
        return project;
    }

    public static void saveProject(Project project) {
        store.save(project);
    }

    /** Something in the active project changed; saved within a couple of seconds. */
    public static void markDirty() {
        dirty = true;
    }

    /**
     * Reads a tutorial's link and fills the project's material list from it. The callback runs on the game thread
     * with null on success or the error message.
     */
    public static void loadTutorial(Project project, String url, Consumer<String> done) {
        String normalized = TutorialFetcher.normalizeUrl(url);
        project.url = normalized;
        saveProject(project);
        TutorialFetcher.fetch(normalized).whenComplete((tutorial, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                done.accept(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                return;
            }
            if (!tutorial.title().isBlank()) {
                project.name = tutorial.title();
            }
            MaterialParser.Result result = new MaterialParser(ItemNameIndex.get()).parse(tutorial.text());
            // Loading the same link again refreshes the list, but a page without one leaves it as it was.
            if (!result.items().isEmpty() || project.materials.isEmpty()) {
                project.setMaterials(result.items());
                project.unmatched = result.unmatched();
            }
            project.videoPosition = 0;
            saveProject(project);
            ContainerTracker.recount();
            done.accept(null);
        }));
    }

    /** Adds a pasted material list to the project, or replaces the list with it. Returns how many were found. */
    public static int applyText(Project project, String text, boolean replace) {
        MaterialParser.Result result = new MaterialParser(ItemNameIndex.get()).parse(text);
        if (replace) {
            project.setMaterials(result.items());
            project.unmatched = result.unmatched();
        } else {
            result.items().forEach(project::addMaterial);
            project.unmatched.addAll(result.unmatched());
        }
        saveProject(project);
        ContainerTracker.recount();
        return result.items().size();
    }

    // --- Video --------------------------------------------------------------------------------------------------

    /** Starts the project's video where it was left, or pauses and resumes it. */
    public static void playOrPause() {
        VideoPlayer player = VideoPlayer.get();
        Project project = activeProject();
        // Pause or resume only the project's own video: after a new link was loaded, the old one is replaced.
        boolean same = project == null || project.url.equals(player.url());
        if (same && (player.isActive() || player.state() == VideoPlayer.State.ENDED)) {
            player.togglePause();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (project == null || project.url.isBlank()) {
            message(Component.translatable("message.tutorialtracker.no_video"));
            return;
        }
        if (!VideoTools.ready()) {
            minecraft.gui.setScreen(new com.lunaos.tutorialtracker.gui.ToolsScreen(minecraft.gui.screen()));
            return;
        }
        player.open(project.url, project.videoPosition);
    }

    // --- Events -------------------------------------------------------------------------------------------------

    public static void onEndClientTick(Minecraft minecraft) {
        if (config == null) {
            return;
        }
        while (TrackerKeys.OPEN.consumeClick()) {
            minecraft.gui.setScreen(new TrackerScreen(null));
        }
        while (TrackerKeys.ARRANGE.consumeClick()) {
            minecraft.gui.setScreen(new com.lunaos.tutorialtracker.gui.PopupEditScreen(null));
        }
        while (TrackerKeys.TOGGLE_HUD.consumeClick()) {
            config.hudVisible = !config.hudVisible;
            config.save();
        }
        while (TrackerKeys.ASSIGN_CONTAINER.consumeClick()) {
            assignLookedAt();
        }
        while (TrackerKeys.PLAY_PAUSE.consumeClick()) {
            playOrPause();
        }
        while (TrackerKeys.SEEK_BACK.consumeClick()) {
            VideoPlayer.get().seekRelative(-10);
        }
        while (TrackerKeys.SEEK_FORWARD.consumeClick()) {
            VideoPlayer.get().seekRelative(10);
        }
        while (TrackerKeys.VIDEO_SCREEN.consumeClick()) {
            minecraft.gui.setScreen(new VideoScreen(null));
        }
        while (TrackerKeys.TOGGLE_VIDEO.consumeClick()) {
            config.videoInHud = !config.videoInHud;
            config.save();
        }

        ContainerTracker.tick(minecraft);
        Updater.announce(minecraft);

        long now = System.currentTimeMillis();
        Project project = activeProject();
        VideoPlayer player = VideoPlayer.get();
        if (project != null && player.isActive() && !player.isLive() && project.url.equals(player.url())
            && now - lastPositionSave > 5000) {
            lastPositionSave = now;
            project.videoPosition = player.position();
            dirty = true;
        }
        if (dirty && now - lastSave > 2000 && project != null) {
            dirty = false;
            lastSave = now;
            saveProject(project);
        }
    }

    private static void assignLookedAt() {
        ContainerTracker.Ref ref = ContainerTracker.lookedAt();
        if (ref == null) {
            message(Component.translatable("message.tutorialtracker.not_a_container"));
            return;
        }
        Project project = activeOrNewProject();
        boolean added = ContainerTracker.toggle(project, ref);
        Project.TrackedContainer tracked = ContainerTracker.find(project, ref);
        if (added) {
            message(Component.translatable(tracked != null && tracked.seen ? "message.tutorialtracker.container_added"
                : "message.tutorialtracker.container_added_open", ref.label()));
        } else {
            message(Component.translatable("message.tutorialtracker.container_removed", ref.label()));
        }
    }

    public static void message(Component text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(text);
        }
    }

    /** Adds the "count this container" button to chest, barrel and shulker box screens. */
    public static void onScreenInit(Screen screen, Consumer<AbstractWidget> addWidget) {
        if (config == null) {
            return;
        }
        AbstractWidget button = ContainerButton.create(screen);
        if (button != null) {
            addWidget.accept(button);
        }
    }
}
