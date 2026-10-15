package com.lunaos.tutorialtracker.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One tutorial being built: its link, what it needs, and the chests and shulker boxes its materials are kept in. */
public final class Project {

    public String id;
    public String name = "";
    public String url = "";
    public long created;
    /** Where the video was left, so it carries on from there. */
    public double videoPosition;
    public List<Material> materials = new ArrayList<>();
    /** Lines of the description that had an amount but no item the parser knew. */
    public List<String> unmatched = new ArrayList<>();
    public List<TrackedContainer> containers = new ArrayList<>();

    public static final class Material {
        public String item;
        public int needed;

        public Material() {
        }

        public Material(String item, int needed) {
            this.item = item;
            this.needed = needed;
        }
    }

    /**
     * A chest, barrel or shulker box assigned to the project, with what was in it the last time it was open. The
     * client only learns a container's contents while it is open, so the count is as of the last look inside.
     */
    public static final class TrackedContainer {
        /** The world or server, see {@code ContainerTracker.worldKey}. */
        public String world;
        public String dimension;
        public int x;
        public int y;
        public int z;
        /** The ender chest: one inventory per player, wherever it is opened. */
        public boolean ender;
        /** The block's name when it was assigned, for the list. */
        public String label = "";
        /** False until it has been opened once since it was assigned. */
        public boolean seen;
        public Map<String, Integer> contents = new LinkedHashMap<>();

        public boolean matches(String world, String dimension, int x, int y, int z, boolean ender) {
            if (!world.equals(this.world)) {
                return false;
            }
            if (ender || this.ender) {
                return ender && this.ender;
            }
            return dimension.equals(this.dimension) && this.x == x && this.y == y && this.z == z;
        }
    }

    public Material material(String item) {
        for (Material material : this.materials) {
            if (material.item.equals(item)) {
                return material;
            }
        }
        return null;
    }

    /** Adds to what an item needs, or lists it. */
    public void addMaterial(String item, int amount) {
        Material material = material(item);
        if (material == null) {
            this.materials.add(new Material(item, amount));
        } else {
            material.needed += amount;
        }
    }

    public void setMaterials(Map<String, Integer> items) {
        this.materials.clear();
        items.forEach(this::addMaterial);
    }
}
