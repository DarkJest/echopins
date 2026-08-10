package dev.echopins.neoforge;

import dev.echopins.EchoPins;
import dev.echopins.integration.voicechat.SimpleVoiceChatBackend;
import dev.echopins.neoforge.client.NeoForgeClient;
import dev.echopins.neoforge.config.ConfigServerLimits;
import dev.echopins.neoforge.config.EchoPinsClientConfig;
import dev.echopins.neoforge.config.EchoPinsServerConfig;
import dev.echopins.neoforge.network.NeoForgeNetworking;
import dev.echopins.server.EchoPinsServer;
import dev.echopins.server.command.EchoPinsCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Entry point shared by Forge and the original NeoForge 1.20.1 line. */
@Mod(EchoPins.MOD_ID)
public final class NeoForgeMod {

    public NeoForgeMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, EchoPinsServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, EchoPinsClientConfig.SPEC);
        NeoForgeNetworking.register();

        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                EchoPinsServer.start(event.getServer(), ConfigServerLimits.INSTANCE,
                        SimpleVoiceChatBackend.INSTANCE));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> EchoPinsServer.stop());
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                EchoPinsServer.current().ifPresent(EchoPinsServer::tick);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                EchoPinsCommand.register(event.getDispatcher()));

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EchoPinsServer.current().ifPresent(server -> server.onPlayerJoined(player));
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EchoPinsServer.current().ifPresent(server -> server.onPlayerLeft(player));
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EchoPinsServer.current().ifPresent(server -> server.onDimensionChanged(player));
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EchoPinsServer.current().ifPresent(server -> {
                    server.onPlayerDied(player);
                    server.sync().onDimensionChanged(player);
                });
            }
        });

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> NeoForgeClient.initialize(modEventBus));
        EchoPins.logger().info("EchoPins loaded (Forge/NeoForge 1.20.1)");
    }
}
