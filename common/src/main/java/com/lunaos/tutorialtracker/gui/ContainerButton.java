package com.lunaos.tutorialtracker.gui;

import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.inventory.ContainerTracker;
import com.lunaos.tutorialtracker.project.Project;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;

/** The button beside a chest, barrel or shulker box screen that assigns the container to the project. */
public final class ContainerButton {

    private static final int WIDTH = 100;
    private static final int GUI_WIDTH = 176;

    private ContainerButton() {
    }

    public static AbstractWidget create(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return null;
        }
        int guiHeight;
        if (containerScreen.getMenu() instanceof ChestMenu chest) {
            guiHeight = 114 + chest.getRowCount() * 18;
        } else if (containerScreen.getMenu() instanceof ShulkerBoxMenu) {
            guiHeight = 167;
        } else {
            return null;
        }
        ContainerTracker.Ref ref = ContainerTracker.lookedAt();
        if (ref == null) {
            return null;
        }
        int x = (screen.width + GUI_WIDTH) / 2 + 4;
        int y = (screen.height - guiHeight) / 2;
        if (x + WIDTH > screen.width - 2) {
            // No room on the right: above the container instead.
            x = (screen.width - GUI_WIDTH) / 2;
            y = Math.max(2, y - 22);
        }
        Button button = Button.builder(label(ref), b -> {
            Project project = TutorialTracker.activeOrNewProject();
            ContainerTracker.toggle(project, ref);
            b.setMessage(label(ref));
        }).bounds(x, y, WIDTH, 20).tooltip(Tooltip.create(Component.translatable("button.tutorialtracker.container.tooltip")))
            .build();
        return button;
    }

    private static Component label(ContainerTracker.Ref ref) {
        Project project = TutorialTracker.activeProject();
        boolean tracked = project != null && ContainerTracker.find(project, ref) != null;
        return Component.translatable(tracked ? "button.tutorialtracker.container.remove"
            : "button.tutorialtracker.container.add");
    }
}
