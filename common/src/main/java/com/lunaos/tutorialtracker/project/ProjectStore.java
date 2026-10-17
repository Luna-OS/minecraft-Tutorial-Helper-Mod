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

/** The saved projects, one JSON file each in {@code config/tutorialtracker/projects}. */
public final class ProjectStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path dir;
    private final List<Project> projects = new ArrayList<>();

    public ProjectStore(Path dir) {
        this.dir = dir;
        load();
    }

    private void load() {
        try {
            Files.createDirectories(this.dir);
            try (Stream<Path> files = Files.list(this.dir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                    try {
                        Project project = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Project.class);
                        if (project != null && project.id != null) {
                            sanitize(project);
                            this.projects.add(project);
                        }
                    } catch (Exception e) {
                        TutorialTracker.LOGGER.warn("Skipping unreadable project {}", file, e);
                    }
                }
            }
        } catch (IOException e) {
            TutorialTracker.LOGGER.warn("Could not read the projects", e);
        }
        this.projects.sort(Comparator.comparingLong(p -> p.created));
    }

    /** Fields an older or hand-edited file may lack. */
    private static void sanitize(Project project) {
        if (project.name == null) {
            project.name = "";
        }
        if (project.url == null) {
            project.url = "";
        }
        if (project.version == null) {
            project.version = "";
        }
        if (project.thumbnail == null) {
            project.thumbnail = "";
        }
        if (project.materials == null) {
            project.materials = new ArrayList<>();
        }
        project.materials.removeIf(m -> m == null || m.item == null);
        if (project.unmatched == null) {
            project.unmatched = new ArrayList<>();
        }
        if (project.containers == null) {
            project.containers = new ArrayList<>();
        }
        project.containers.removeIf(c -> c == null || c.world == null);
        for (Project.TrackedContainer container : project.containers) {
            if (container.contents == null) {
                container.contents = new java.util.LinkedHashMap<>();
            }
            if (container.dimension == null) {
                container.dimension = "";
            }
        }
        if (project.name == null) {
            project.name = "";
        }
        if (project.url == null) {
            project.url = "";
        }
    }

    public List<Project> all() {
        return this.projects;
    }

    public Project byId(String id) {
        for (Project project : this.projects) {
            if (project.id.equals(id)) {
                return project;
            }
        }
        return null;
    }

    public Project create() {
        Project project = new Project();
        project.id = UUID.randomUUID().toString().substring(0, 8);
        project.created = System.currentTimeMillis();
        this.projects.add(project);
        save(project);
        return project;
    }

    /** The project of an archive card, made again under the card's id so the two stay matched. */
    public Project restore(ArchiveEntry entry) {
        Project project = new Project();
        project.id = entry.id;
        project.created = System.currentTimeMillis();
        entry.applyTo(project);
        this.projects.add(project);
        save(project);
        return project;
    }

    public void delete(Project project) {
        this.projects.remove(project);
        DiskWriter.delete(file(project));
    }

    /** Saved in the background: see {@link DiskWriter}. */
    public void save(Project project) {
        DiskWriter.write(file(project), GSON.toJson(project));
    }

    private Path file(Project project) {
        return this.dir.resolve(project.id.replaceAll("[^A-Za-z0-9_-]", "_") + ".json");
    }
}
