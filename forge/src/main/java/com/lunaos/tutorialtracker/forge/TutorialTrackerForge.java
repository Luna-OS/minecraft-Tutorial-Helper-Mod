package com.lunaos.tutorialtracker.forge;

import com.lunaos.tutorialtracker.TrackerKeys;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.gui.HudRenderer;
import com.lunaos.tutorialtracker.gui.SettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** Client only (clientSideOnly in mods.toml). */
@Mod(TutorialTracker.MOD_ID)
public final class TutorialTrackerForge {

    public TutorialTrackerForge(FMLJavaModLoadingContext context) {
        TutorialTracker.init(FMLPaths.GAMEDIR.get());

        RegisterKeyMappingsEvent.BUS.addListener(event -> {
            for (KeyMapping key : TrackerKeys.ALL) {
                event.register(key);
            }
        });
        AddGuiOverlayLayersEvent.BUS.addListener(event -> event.getLayeredDraw()
            .add(Identifier.fromNamespaceAndPath(TutorialTracker.MOD_ID, "hud"), HudRenderer::render));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> TutorialTracker.onEndClientTick(Minecraft.getInstance()));
        ScreenEvent.Init.Post.BUS.addListener(event -> TutorialTracker.onScreenInit(event.getScreen(), event::addListener));
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new SettingsScreen(parent)));
    }
}
