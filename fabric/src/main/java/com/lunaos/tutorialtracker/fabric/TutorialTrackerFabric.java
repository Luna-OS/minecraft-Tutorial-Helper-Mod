package com.lunaos.tutorialtracker.fabric;

import com.lunaos.tutorialtracker.TrackerKeys;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.gui.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class TutorialTrackerFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TutorialTracker.init(FabricLoader.getInstance().getGameDir());
        for (KeyMapping key : TrackerKeys.ALL) {
            KeyMappingHelper.registerKeyMapping(key);
        }
        ClientTickEvents.END_CLIENT_TICK.register(TutorialTracker::onEndClientTick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TutorialTracker.MOD_ID, "hud"), HudRenderer::render);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
            TutorialTracker.onScreenInit(screen, widget -> Screens.getWidgets(screen).add(widget)));
    }
}
