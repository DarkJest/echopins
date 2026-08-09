package dev.echopins.neoforge.network;

import dev.echopins.infrastructure.network.EchoPinsNetwork;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Binds the shared protocol to NeoForge's networking.
 *
 * <p>Handlers run on the main thread, which is NeoForge's default, so they may touch world state
 * directly. Routing and validation live in {@link EchoPinsNetwork}; this class only connects it.
 */
public final class NeoForgeNetworking {

    private NeoForgeNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EchoPinsNetwork.PROTOCOL_VERSION);

        for (EchoPinsNetwork.PayloadSpec<?> spec : EchoPinsNetwork.serverbound()) {
            registerServerbound(registrar, spec);
        }
        for (EchoPinsNetwork.PayloadSpec<?> spec : EchoPinsNetwork.clientbound()) {
            registerClientbound(registrar, spec);
        }

        EchoPinsNetwork.installTransport(new EchoPinsNetwork.Transport() {
            @Override
            public void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
                PacketDistributor.sendToPlayer(player, payload);
            }

            @Override
            public void toServer(CustomPacketPayload payload) {
                PacketDistributor.sendToServer(payload);
            }
        });
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            PayloadRegistrar registrar, EchoPinsNetwork.PayloadSpec<T> spec) {
        registrar.playToServer(spec.type(), spec.codec(), (payload, context) -> {
            // The sender comes from the connection, never from the payload.
            if (context.player() instanceof ServerPlayer sender) {
                EchoPinsNetwork.handleServerbound(sender, payload);
            }
        });
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadRegistrar registrar, EchoPinsNetwork.PayloadSpec<T> spec) {
        registrar.playToClient(spec.type(), spec.codec(),
                (payload, context) -> EchoPinsNetwork.handleClientbound(payload));
    }
}
