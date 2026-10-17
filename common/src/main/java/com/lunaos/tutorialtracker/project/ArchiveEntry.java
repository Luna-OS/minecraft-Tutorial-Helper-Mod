package com.lunaos.tutorialtracker.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A list kept in the archive: what a project had (name, link, version, picture, materials), without its chests. It
 * lives on when the project is deleted, and opening it makes the project again. It carries the id of the project it
 * belongs to, so the two stay matched while both exist.
 */
public final class ArchiveEntry {

    public String id;
    public String name = "";
    public String url = "";
    public String version = "";
    public String thumbnail = "";
    public long created;
    public List<Project.Material> materials = new ArrayList<>();

    /** A snapshot of a project's list, under the project's id. */
    public static ArchiveEntry of(Project project) {
        ArchiveEntry entry = new ArchiveEntry();
        entry.id = project.id;
        entry.created = project.created;
        entry.copyFrom(project);
        return entry;
    }

    void copyFrom(Project project) {
        this.name = project.name;
        this.url = project.url;
        this.version = project.version;
        this.thumbnail = project.thumbnail;
        this.materials = new ArrayList<>();
        for (Project.Material material : project.materials) {
            this.materials.add(new Project.Material(material.item, material.needed));
        }
    }

    /** Whether the entry shows the same list as the project. */
    boolean sameAs(Project project) {
        if (!this.name.equals(project.name) || !this.url.equals(project.url) || !this.version.equals(project.version)
            || !this.thumbnail.equals(project.thumbnail) || this.materials.size() != project.materials.size()) {
            return false;
        }
        for (int i = 0; i < this.materials.size(); i++) {
            Project.Material a = this.materials.get(i);
            Project.Material b = project.materials.get(i);
            if (!Objects.equals(a.item, b.item) || a.needed != b.needed) {
                return false;
            }
        }
        return true;
    }

    /** Puts the list into a project (a new one, or the one it belongs to). */
    public void applyTo(Project project) {
        project.name = this.name;
        project.url = this.url;
        project.version = this.version;
        project.thumbnail = this.thumbnail;
        project.materials = new ArrayList<>();
        for (Project.Material material : this.materials) {
            project.materials.add(new Project.Material(material.item, material.needed));
        }
    }

    /** As a project for sharing and drawing; not stored anywhere. */
    public Project asProject() {
        Project project = new Project();
        project.id = this.id;
        project.created = this.created;
        applyTo(project);
        return project;
    }
}
