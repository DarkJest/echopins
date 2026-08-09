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
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client entry point.
 *
 * <p>Registration only. Everything the client actually does lives in
 * {@link EchoPinsClientCore}, shared with the Fabric build.
 *
 * <p>Annotated {@code dist = Dist.CLIENT}, so the mod loader never constructs it on a dedicated
 * server and none of the rendering classes it touches are ever loaded there.
 */
@Mod(value = EchoPins.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {

    public NeoForgeClient(IEventBus modEventBus, ModContainer modContainer) {
        ClientSettings.Holder.install(NeoForgeClientSettings.INSTANCE);
        ClientPayloadReceiver.Holder.install(new EchoPinsClientCore.Receiver());

        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(EchoPinsKeybinds.CREATE_PIN);
            event.register(EchoPinsKeybinds.OPEN_INBOX);
            event.register(EchoPinsKeybinds.PLAY_NEAREST);
        });

        modEventBus.addListener(RegisterGuiLayersEvent.class, event -> {
            event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                    ResourceLocation.fromNamespaceAndPath(EchoPins.MOD_ID, "focused_pin"),
                    new FocusedPinHud());
            event.registerAboveAll(
                    ResourceLocation.fromNamespaceAndPath(EchoPins.MOD_ID, "now_playing"),
                    new NowPlayingHud());
            event.registerAboveAll(
                    ResourceLocation.fromNamespaceAndPath(EchoPins.MOD_ID, "recording"),
                    new RecordingHud());
        });

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> EchoPinsClientCore.onClientTick());
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                EchoPinsClientCore.onRenderLevel(event.getPoseStack(), event.getCamera());
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
                event -> ClientPinState.INSTANCE.reset());
    }
}
