package dev.echopins.neoforge;

import dev.echopins.EchoPins;
import dev.echopins.integration.voicechat.SimpleVoiceChatBackend;
import dev.echopins.neoforge.config.ConfigServerLimits;
import dev.echopins.neoforge.config.EchoPinsClientConfig;
import dev.echopins.neoforge.config.EchoPinsServerConfig;
import dev.echopins.neoforge.network.NeoForgeNetworking;
import dev.echopins.server.EchoPinsServer;
import dev.echopins.server.command.EchoPinsCommand;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * NeoForge entry point.
 *
 * <p>Wiring only: it maps NeoForge's events onto the loader-agnostic services. Nothing here
 * decides behaviour.
 */
@Mod(EchoPins.MOD_ID)
public final class NeoForgeMod {

    public NeoForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, EchoPinsServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, EchoPinsClientConfig.SPEC);

        modEventBus.addListener(RegisterPayloadHandlersEvent.class, NeoForgeNetworking::register);

        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event ->
                EchoPinsServer.start(event.getServer(), ConfigServerLimits.INSTANCE,
                        SimpleVoiceChatBackend.INSTANCE));
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> EchoPinsServer.stop());
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event ->
                EchoPinsServer.current().ifPresent(EchoPinsServer::tick));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event ->
                EchoPinsCommand.register(event.getDispatcher()));

        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EchoPinsServer.current().ifPresent(server -> server.onPlayerJoined(player));
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EchoPinsServer.current().ifPresent(server -> server.onPlayerLeft(player));
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EchoPinsServer.current().ifPresent(server -> server.onDimensionChanged(player));
            }
        });
        // Covers respawn after death: the player is somewhere else now, so an open recording is
        // no longer anchored to anything meaningful.
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EchoPinsServer.current().ifPresent(server -> {
                    server.onPlayerDied(player);
                    server.sync().onDimensionChanged(player);
                });
            }
        });

        EchoPins.logger().info("EchoPins loaded (NeoForge)");
    }
}
