package com.lunaos.tutorialtracker.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Drawing in the game's own style, so the mod looks like part of Minecraft: the grey inventory window with its
 * bevelled edge, inventory slots, the experience bar as progress bar and the tooltip frame on the HUD. The windows
 * come in the game's light grey or, if chosen, in dark.
 */
public final class McStyle {

    /*
     * The colours below follow the chosen design, light (the game's own grey windows) or dark; see applyTheme. They
     * are read when drawing, so switching takes effect on the next frame.
     */

    /** The inventory window's colours. */
    public static int PANEL;
    public static int PANEL_LIGHT;
    public static int PANEL_SHADOW;
    public static final int OUTLINE = 0xFF000000;
    /** Slot and inset colours. */
    public static int SLOT;
    public static int SLOT_DARK;
    /** The bright lower right edge of a slot or inset. */
    public static int SLOT_LIGHT;
    /**
     * Labels on the window, like "Chest" and "Inventory". Not quite the game's 0x404040: some dark interface packs
     * come with mods that repaint text of exactly that colour light, which would leave it unreadable on our window.
     */
    public static int LABEL;
    /** A label for something that cannot be used right now. */
    public static int LABEL_DISABLED;

    /** Text on the window: darker versions of the chat colours, readable on grey without a shadow. */
    public static int DARK_GREEN;
    public static int DARK_GOLD;
    public static int DARK_RED;
    /** The empty area of the window arranging preview. */
    public static int PREVIEW;

    private static boolean dark;

    static {
        applyTheme(false);
    }

    /** Chat colours, for text with a shadow on dark backgrounds. */
    public static final int GREEN = 0xFF55FF55;
    public static final int YELLOW = 0xFFFFFF55;
    public static final int RED = 0xFFFF5555;
    public static final int GRAY = 0xFFAAAAAA;
    public static final int WHITE = 0xFFFFFFFF;

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier XP_BACKGROUND = Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final Identifier XP_PROGRESS = Identifier.withDefaultNamespace("hud/experience_bar_progress");

    private McStyle() {
    }

    /** Light: the game's grey windows. Dark: near-black windows with light text, easier on the eyes at night. */
    public static void applyTheme(boolean darkTheme) {
        dark = darkTheme;
        if (darkTheme) {
            PANEL = 0xFF2E2E30;
            PANEL_LIGHT = 0xFF4A4A4E;
            PANEL_SHADOW = 0xFF161618;
            SLOT = 0xFF1E1E20;
            SLOT_DARK = 0xFF0C0C0D;
            SLOT_LIGHT = 0xFF505054;
            LABEL = 0xFFE6E6E6;
            LABEL_DISABLED = 0xFF7A7A7A;
            DARK_GREEN = 0xFF5CD65C;
            DARK_GOLD = 0xFFFFC23D;
            DARK_RED = 0xFFFF6666;
            PREVIEW = 0xFF1F3550;
        } else {
            PANEL = 0xFFC6C6C6;
            PANEL_LIGHT = 0xFFFFFFFF;
            PANEL_SHADOW = 0xFF555555;
            SLOT = 0xFF8B8B8B;
            SLOT_DARK = 0xFF373737;
            SLOT_LIGHT = 0xFFFFFFFF;
            LABEL = 0xFF3F3F3F;
            LABEL_DISABLED = 0xFF8B8B8B;
            DARK_GREEN = 0xFF00AA00;
            DARK_GOLD = 0xFFAA6E00;
            DARK_RED = 0xFFAA0000;
            PREVIEW = 0xFF3A5F8A;
        }
    }

    public static boolean isDark() {
        return dark;
    }

    /** The inventory window: grey, white top-left edge, dark bottom-right edge, black rounded outline. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int x1 = x + width;
        int y1 = y + height;
        // Black outline with cut corners.
        graphics.fill(x + 2, y, x1 - 3, y + 1, OUTLINE);
        graphics.fill(x + 3, y1 - 1, x1 - 2, y1, OUTLINE);
        graphics.fill(x, y + 2, x + 1, y1 - 3, OUTLINE);
        graphics.fill(x1 - 1, y + 3, x1, y1 - 2, OUTLINE);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, OUTLINE);
        graphics.fill(x1 - 3, y + 1, x1 - 2, y + 2, OUTLINE);
        graphics.fill(x1 - 2, y + 2, x1 - 1, y + 3, OUTLINE);
        graphics.fill(x + 1, y1 - 3, x + 2, y1 - 2, OUTLINE);
        graphics.fill(x + 2, y1 - 2, x + 3, y1 - 1, OUTLINE);
        graphics.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, OUTLINE);
        // Face.
        graphics.fill(x + 1, y + 2, x1 - 1, y1 - 2, PANEL);
        graphics.fill(x + 2, y + 1, x1 - 2, y1 - 1, PANEL);
        // Bevel.
        graphics.fill(x + 2, y + 1, x1 - 3, y + 3, PANEL_LIGHT);
        graphics.fill(x + 1, y + 2, x + 3, y1 - 3, PANEL_LIGHT);
        graphics.fill(x + 3, y1 - 3, x1 - 2, y1 - 1, PANEL_SHADOW);
        graphics.fill(x1 - 3, y + 3, x1 - 1, y1 - 2, PANEL_SHADOW);
        graphics.fill(x1 - 3, y + 2, x1 - 2, y + 3, PANEL);
        graphics.fill(x + 2, y1 - 3, x + 3, y1 - 2, PANEL);
    }

    /** A sunken area like a slot: dark top-left edge, white bottom-right edge. */
    public static void inset(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int face) {
        graphics.fill(x, y, x + width, y + height, face);
        graphics.fill(x - 1, y - 1, x + width, y, SLOT_DARK);
        graphics.fill(x - 1, y, x, y + height, SLOT_DARK);
        graphics.fill(x, y + height, x + width + 1, y + height + 1, SLOT_LIGHT);
        graphics.fill(x + width, y, x + width + 1, y + height, SLOT_LIGHT);
    }

    /** An inventory slot, 18 by 18 with the item drawn at +1, +1. */
    public static void slot(GuiGraphicsExtractor graphics, int x, int y) {
        if (dark) {
            // The slot sprite is light grey; drawn by hand in the dark colours, same shape.
            graphics.fill(x, y, x + 18, y + 18, SLOT_DARK);
            graphics.fill(x + 1, y + 1, x + 18, y + 18, SLOT_LIGHT);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
            return;
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x, y, 18, 18);
    }

    /** The experience bar, stretched to a width and filled to a fraction. */
    public static void xpBar(GuiGraphicsExtractor graphics, int x, int y, int width, double fraction) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, x, y, width, 5);
        int filled = (int) Math.round(width * Math.clamp(fraction, 0.0, 1.0));
        if (filled > 0) {
            graphics.enableScissor(x, y, x + filled, y + 5);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, x, y, width, 5);
            graphics.disableScissor();
        }
    }

    /** The tooltip's dark purple-edged box around an area, as the HUD's background. */
    public static void tooltipBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        TooltipRenderUtil.extractTooltipBackground(graphics, x, y, width, height, null);
    }

    /** A window label: dark grey (light in the dark design), no shadow, as the game writes "Chest" on a chest. */
    public static void label(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y) {
        graphics.text(font, text, x, y, LABEL, false);
    }

    public static void label(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, false);
    }

    /** The colour of an amount on the grey window: enough, some, none. */
    public static int amountColorOnPanel(int have, int needed) {
        return have >= needed ? DARK_GREEN : have > 0 ? DARK_GOLD : DARK_RED;
    }

    /** The colour of an amount on a dark background. */
    public static int amountColor(int have, int needed) {
        return have >= needed ? GREEN : have > 0 ? YELLOW : RED;
    }
}
