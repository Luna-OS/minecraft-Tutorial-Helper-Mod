package com.lunaos.tutorialtracker.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunaos.tutorialtracker.DiskWriter;
import com.lunaos.tutorialtracker.TutorialTracker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The archive, one JSON file per list in {@code config/tutorialtracker/archive}. It is kept apart from the projects:
 * every project with a list is copied in here as it changes, and deleting a project leaves its list here. Only
 * deleting it in the archive removes it.
 */
public final class ArchiveStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path dir;
    private final List<ArchiveEntry> entries = new ArrayList<>();

    public ArchiveStore(Path dir) {
        this.dir = dir;
        load();
    }

    private void load() {
        try {
            Files.createDirectories(this.dir);
            try (Stream<Path> files = Files.list(this.dir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                    try {
                        ArchiveEntry entry = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                            ArchiveEntry.class);
                        if (entry != null && entry.id != null) {
                            sanitize(entry);
                            this.entries.add(entry);
                        }
                    } catch (Exception e) {
                        TutorialTracker.LOGGER.warn("Skipping unreadable archive entry {}", file, e);
                    }
                }
            }
        } catch (IOException e) {
            TutorialTracker.LOGGER.warn("Could not read the archive", e);
        }
        this.entries.sort(Comparator.comparingLong(e -> e.created));
    }

    private static void sanitize(ArchiveEntry entry) {
        entry.name = entry.name == null ? "" : entry.name;
        entry.url = entry.url == null ? "" : entry.url;
        entry.version = entry.version == null ? "" : entry.version;
        entry.thumbnail = entry.thumbnail == null ? "" : entry.thumbnail;
        if (entry.materials == null) {
            entry.materials = new ArrayList<>();
        }
        entry.materials.removeIf(m -> m == null || m.item == null);
    }

    /** Oldest first. */
    public List<ArchiveEntry> all() {
        return this.entries;
    }

    public ArchiveEntry byId(String id) {
        for (ArchiveEntry entry : this.entries) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Copies a project's list into the archive: a new entry the first time it has one (a link or materials), then
     * kept up to date. Projects without a list yet are left out.
     */
    public void record(Project project) {
        if (project.keepOutOfArchive || project.url.isBlank() && project.materials.isEmpty()) {
            return;
        }
        ArchiveEntry entry = byId(project.id);
        if (entry == null) {
            entry = ArchiveEntry.of(project);
            this.entries.add(entry);
        } else if (entry.sameAs(project)) {
            return;
        } else {
            entry.copyFrom(project);
        }
        save(entry);
    }

    /** A new entry, from a shared code. */
    public ArchiveEntry add(ShareCode.Shared shared) {
        ArchiveEntry entry = new ArchiveEntry();
        entry.id = UUID.randomUUID().toString().substring(0, 8);
        entry.created = System.currentTimeMillis();
        entry.name = shared.name();
        entry.url = shared.url();
        entry.version = shared.version();
        entry.thumbnail = shared.thumbnail();
        shared.materials().forEach((item, count) -> entry.materials.add(new Project.Material(item, count)));
        this.entries.add(entry);
        save(entry);
        return entry;
    }

    public void delete(ArchiveEntry entry) {
        this.entries.remove(entry);
        DiskWriter.delete(file(entry));
    }

    /** Saved in the background: see {@link DiskWriter}. */
    public void save(ArchiveEntry entry) {
        DiskWriter.write(file(entry), GSON.toJson(entry));
    }

    private Path file(ArchiveEntry entry) {
        return this.dir.resolve(entry.id.replaceAll("[^A-Za-z0-9_-]", "_") + ".json");
    }
}
