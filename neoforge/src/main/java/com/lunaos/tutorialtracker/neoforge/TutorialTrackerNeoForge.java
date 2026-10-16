package com.lunaos.tutorialtracker.neoforge;

import com.lunaos.tutorialtracker.TrackerKeys;
import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.gui.HudRenderer;
import com.lunaos.tutorialtracker.gui.SettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = TutorialTracker.MOD_ID, dist = Dist.CLIENT)
public final class TutorialTrackerNeoForge {

    public TutorialTrackerNeoForge(IEventBus modBus, ModContainer container) {
        TutorialTracker.init(FMLPaths.GAMEDIR.get(), "neoforge", container.getModInfo().getVersion().toString(),
            container.getModInfo().getOwningFile().getFile().getFilePath());

        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            for (KeyMapping key : TrackerKeys.ALL) {
                event.register(key);
            }
        });
        modBus.addListener(RegisterGuiLayersEvent.class, event ->
            event.registerAboveAll(Identifier.fromNamespaceAndPath(TutorialTracker.MOD_ID, "hud"), HudRenderer::render));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
            event -> TutorialTracker.onEndClientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class,
            event -> TutorialTracker.onScreenInit(event.getScreen(), event::addListener));
        container.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, parent) -> new SettingsScreen(parent));
    }
}
