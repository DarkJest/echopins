package dev.echopins.fabric.client;

import dev.echopins.client.ClientSettings;
import dev.echopins.client.EchoPinsClientCore;
import dev.echopins.client.hud.FocusedPinHud;
import dev.echopins.client.hud.NowPlayingHud;
import dev.echopins.client.hud.RecordingHud;
import dev.echopins.client.keybind.EchoPinsKeybinds;
import dev.echopins.client.state.ClientPinState;
import dev.echopins.fabric.config.FabricClientSettings;
import dev.echopins.fabric.network.FabricNetworking;
import dev.echopins.infrastructure.network.ClientPayloadReceiver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.DeltaTracker;

/**
 * Fabric client entry point.
 *
 * <p>Registration only. Everything the client actually does lives in {@link EchoPinsClientCore},
 * shared with the NeoForge build.
 */
public final class EchoPinsFabricClient implements ClientModInitializer {

    private final FocusedPinHud focusedPinHud = new FocusedPinHud();
    private final NowPlayingHud nowPlayingHud = new NowPlayingHud();
    private final RecordingHud recordingHud = new RecordingHud();

    @Override
    public void onInitializeClient() {
        ClientSettings.Holder.install(FabricClientSettings.INSTANCE);
        ClientPayloadReceiver.Holder.install(new EchoPinsClientCore.Receiver());
        FabricNetworking.registerClientHandlers();

        KeyBindingHelper.registerKeyBinding(EchoPinsKeybinds.CREATE_PIN);
        KeyBindingHelper.registerKeyBinding(EchoPinsKeybinds.OPEN_INBOX);
        KeyBindingHelper.registerKeyBinding(EchoPinsKeybinds.PLAY_NEAREST);

        ClientTickEvents.END_CLIENT_TICK.register(client -> EchoPinsClientCore.onClientTick());

        // AFTER_ENTITIES is the closest Fabric stage to NeoForge's AFTER_PARTICLES: the world is
        // drawn and the camera is set up, and the HUD still goes on top afterwards.
        WorldRenderEvents.AFTER_ENTITIES.register(context ->
                EchoPinsClientCore.onRenderLevel(context.matrixStack(), context.camera()));

        // Fabric has one HUD callback rather than named layers, so the three panels are drawn in
        // the same order NeoForge stacks them.
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            DeltaTracker delta = net.minecraft.client.Minecraft.getInstance().getTimer();
            focusedPinHud.render(graphics, delta);
            nowPlayingHud.render(graphics, delta);
            recordingHud.render(graphics, delta);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientPinState.INSTANCE.reset());
    }
}
