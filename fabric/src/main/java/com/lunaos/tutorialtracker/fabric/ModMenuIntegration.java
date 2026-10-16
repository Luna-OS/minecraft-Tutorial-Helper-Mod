package com.lunaos.tutorialtracker.fabric;

import com.lunaos.tutorialtracker.gui.SettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * The config button in Mod Menu's list: it opens the settings, where the windows are arranged by dragging. Only
 * loaded when Mod Menu is installed.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
