package dev.echopins.fabric;

import dev.echopins.EchoPins;
import dev.echopins.fabric.config.FabricServerLimits;
import dev.echopins.fabric.network.FabricNetworking;
import dev.echopins.integration.voicechat.SimpleVoiceChatBackend;
import dev.echopins.server.EchoPinsServer;
import dev.echopins.server.command.EchoPinsCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Fabric entry point.
 *
 * <p>Wiring only: it maps Fabric's events onto the same loader-agnostic services the NeoForge
 * build uses. Nothing here decides behaviour.
 */
public final class EchoPinsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricNetworking.registerPayloads();
        FabricNetworking.registerServerHandlers();

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                EchoPinsServer.start(server, FabricServerLimits.get(),
                        SimpleVoiceChatBackend.INSTANCE));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> EchoPinsServer.stop());
        ServerTickEvents.END_SERVER_TICK.register(server ->
                EchoPinsServer.current().ifPresent(EchoPinsServer::tick));

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                EchoPinsCommand.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                EchoPinsServer.current().ifPresent(echoPins -> echoPins.onPlayerJoined(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                EchoPinsServer.current().ifPresent(echoPins -> echoPins.onPlayerLeft(handler.player)));

        // Fabric reports a dimension change and a respawn through the same callback; the boolean
        // says which, and both mean the player is no longer where their recording was anchored.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                EchoPinsServer.current().ifPresent(echoPins -> {
                    echoPins.onPlayerDied(newPlayer);
                    echoPins.sync().onDimensionChanged(newPlayer);
                }));

        EchoPins.logger().info("EchoPins loaded (Fabric)");
    }
}
