package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TrackerConfig;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.inventory.ContainerTracker;
import com.lunaos.tutorialtracker.materials.ItemNameIndex;
import com.lunaos.tutorialtracker.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The list window's rows, ready to draw: which materials show, in what order, with their icon, name, "have/needed"
 * and colour. Drawn every frame but only made again when something they show changed (a count, the list, a setting),
 * so the HUD costs next to nothing while playing.
 */
final class ListView {

    /** One material as the window shows it. */
    static final class Row {
        final ItemStack icon;
        final String name;
        final String amount;
        final int color;
        /** The name cut to the last width it was drawn at, kept until the width changes. */
        private int fittedWidth = -1;
        private String fitted;

        Row(ItemStack icon, String name, String amount, int color) {
            this.icon = icon;
            this.name = name;
            this.amount = amount;
            this.color = color;
        }

        String fittedName(Font font, int width) {
            if (width != this.fittedWidth) {
                this.fittedWidth = width;
                this.fitted = HudRenderer.ellipsize(font, this.name, width);
            }
            return this.fitted;
        }
    }

    private static ListView current;

    final List<Row> rows;
    final int done;
    final int total;
    private final Project project;
    private final int revision;
    private final int fingerprint;
    private final boolean hideDone;
    private final boolean stacks;
    private final String language;

    private ListView(Project project, int revision, int fingerprint, boolean hideDone, boolean stacks,
                     String language) {
        this.project = project;
        this.revision = revision;
        this.fingerprint = fingerprint;
        this.hideDone = hideDone;
        this.stacks = stacks;
        this.language = language;
        List<Row> open = new ArrayList<>();
        List<Row> finished = new ArrayList<>();
        int doneCount = 0;
        if (project != null) {
            for (Project.Material material : project.materials) {
                int have = ContainerTracker.have(material.item);
                boolean enough = have >= material.needed;
                if (enough) {
                    doneCount++;
                }
                (enough ? finished : open).add(row(material, have, stacks));
            }
        }
        // Open materials first, then (unless hidden) the ones there is enough of.
        if (!hideDone) {
            open.addAll(finished);
        }
        this.rows = open;
        this.done = doneCount;
        this.total = project == null ? 0 : project.materials.size();
    }

    private static Row row(Project.Material material, int have, boolean stacks) {
        Item item = ItemNameIndex.item(material.item);
        // In the main menu (the settings opened from the mod list) items have no components yet, so no stack can be
        // made: the row goes without its icon and takes the name from the language file.
        ItemStack icon = stacks && item != null ? new ItemStack(item) : null;
        String name = icon != null ? icon.getHoverName().getString()
            : item != null ? Component.translatable(item.getDescriptionId()).getString() : material.item;
        return new Row(icon, name, HudRenderer.format(have) + "/" + HudRenderer.format(material.needed),
            McStyle.amountColor(have, material.needed));
    }

    /** The rows for a project, made again only when they would come out different. */
    static ListView of(Project project) {
        TrackerConfig config = TutorialTracker.config();
        int revision = ContainerTracker.revision();
        int fingerprint = fingerprint(project);
        boolean stacks = Items.STONE.builtInRegistryHolder().areComponentsBound();
        String language = Minecraft.getInstance().options.languageCode;
        ListView view = current;
        if (view == null || view.project != project || view.revision != revision || view.fingerprint != fingerprint
            || view.hideDone != config.hideDone || view.stacks != stacks || !view.language.equals(language)) {
            view = new ListView(project, revision, fingerprint, config.hideDone, stacks, language);
            current = view;
        }
        return view;
    }

    /** Changes when a material is added, removed or its amount edited; cheap enough to work out every frame. */
    private static int fingerprint(Project project) {
        if (project == null) {
            return 0;
        }
        int hash = project.materials.size();
        for (Project.Material material : project.materials) {
            hash = hash * 31 + Objects.hashCode(material.item);
            hash = hash * 31 + material.needed;
        }
        return hash;
    }
}
