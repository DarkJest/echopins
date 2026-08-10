package dev.echopins.neoforge.client;

import dev.echopins.EchoPins;
import dev.echopins.client.ClientSettings;
import dev.echopins.client.EchoPinsClientCore;
import dev.echopins.client.hud.FocusedPinHud;
import dev.echopins.client.hud.NowPlayingHud;
import dev.echopins.client.hud.RecordingHud;
import dev.echopins.client.keybind.EchoPinsKeybinds;
import dev.echopins.client.state.ClientPinState;
import dev.echopins.infrastructure.network.ClientPayloadReceiver;
import dev.echopins.neoforge.config.NeoForgeClientSettings;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client registration shared by Forge and early NeoForge 1.20.1. */
public final class NeoForgeClient {

    private NeoForgeClient() {
    }

    public static void initialize(IEventBus modEventBus) {
        ClientSettings.Holder.install(NeoForgeClientSettings.INSTANCE);
        ClientPayloadReceiver.Holder.install(new EchoPinsClientCore.Receiver());

        modEventBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(EchoPinsKeybinds.CREATE_PIN);
            event.register(EchoPinsKeybinds.OPEN_INBOX);
            event.register(EchoPinsKeybinds.PLAY_NEAREST);
        });

        modEventBus.addListener((RegisterGuiOverlaysEvent event) -> {
            FocusedPinHud focused = new FocusedPinHud();
            NowPlayingHud playing = new NowPlayingHud();
            RecordingHud recording = new RecordingHud();
            event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "focused_pin",
                    (gui, graphics, partialTick, width, height) -> focused.render(graphics, partialTick));
            event.registerAboveAll("now_playing",
                    (gui, graphics, partialTick, width, height) -> playing.render(graphics, partialTick));
            event.registerAboveAll("recording",
                    (gui, graphics, partialTick, width, height) -> recording.render(graphics, partialTick));
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                EchoPinsClientCore.onClientTick();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                EchoPinsClientCore.onRenderLevel(event.getPoseStack(), event.getCamera());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                ClientPinState.INSTANCE.reset());

        EchoPins.logger().debug("EchoPins client initialized");
    }
}
