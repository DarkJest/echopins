package dev.echopins;

import dev.echopins.infrastructure.config.ConfigServerLimits;
import dev.echopins.infrastructure.config.EchoPinsClientConfig;
import dev.echopins.infrastructure.config.EchoPinsServerConfig;
import dev.echopins.infrastructure.network.EchoPinsNetwork;
import dev.echopins.integration.voicechat.SimpleVoiceChatBackend;
import dev.echopins.server.EchoPinsServer;
import dev.echopins.server.command.EchoPinsCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entry point and common (client + server) wiring.
 *
 * <p>Holds no client-only references. Everything that needs rendering lives under
 * {@code dev.echopins.client} and is reached only through the dist-guarded client initialiser, so
 * a dedicated server never loads a rendering class.
 */
@Mod(EchoPins.MOD_ID)
public final class EchoPins {

    public static final String MOD_ID = "echopins";

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins");

    public EchoPins(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, EchoPinsServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, EchoPinsClientConfig.SPEC);

        modEventBus.addListener(RegisterPayloadHandlersEvent.class, EchoPinsNetwork::register);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                ServerStartedEvent.class, EchoPins::onServerStarted);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                ServerStoppingEvent.class, EchoPins::onServerStopping);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                ServerTickEvent.Post.class, EchoPins::onServerTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                RegisterCommandsEvent.class, EchoPins::onRegisterCommands);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                PlayerEvent.PlayerLoggedInEvent.class, EchoPins::onPlayerLoggedIn);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                PlayerEvent.PlayerLoggedOutEvent.class, EchoPins::onPlayerLoggedOut);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                PlayerEvent.PlayerChangedDimensionEvent.class, EchoPins::onPlayerChangedDimension);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                PlayerEvent.PlayerRespawnEvent.class, EchoPins::onPlayerRespawn);

        LOGGER.info("EchoPins loaded");
    }

    public static Logger logger() {
        return LOGGER;
    }

    private static void onServerStarted(ServerStartedEvent event) {
        EchoPinsServer.start(event.getServer(), ConfigServerLimits.INSTANCE,
                SimpleVoiceChatBackend.INSTANCE);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        EchoPinsServer.stop();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        EchoPinsServer.current().ifPresent(EchoPinsServer::tick);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        EchoPinsCommand.register(event.getDispatcher());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            EchoPinsServer.current().ifPresent(server -> server.onPlayerJoined(player));
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            EchoPinsServer.current().ifPresent(server -> server.onPlayerLeft(player));
        }
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            EchoPinsServer.current().ifPresent(server -> server.onDimensionChanged(player));
        }
    }

    /**
     * Covers respawn after death: the player is somewhere else now, so any open recording is no
     * longer anchored to anything meaningful.
     */
    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            EchoPinsServer.current().ifPresent(server -> {
                server.onPlayerDied(player);
                server.sync().onDimensionChanged(player);
            });
        }
    }
}
