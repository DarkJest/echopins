package dev.echopins.fabric.network;

import dev.echopins.infrastructure.network.EchoPinsNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Binds the shared protocol to Fabric's networking.
 *
 * <p>Payload types are registered from the same {@link EchoPinsNetwork} lists the NeoForge build
 * uses, so both loaders speak an identical protocol by construction rather than by two people
 * remembering to keep two lists in step.
 */
public final class FabricNetworking {

    private FabricNetworking() {
    }

    /** Registers payload types. Must run on both sides, before any handler is attached. */
    public static void registerPayloads() {
        for (EchoPinsNetwork.PayloadSpec<?> spec : EchoPinsNetwork.serverbound()) {
            registerServerboundType(spec);
        }
        for (EchoPinsNetwork.PayloadSpec<?> spec : EchoPinsNetwork.clientbound()) {
            registerClientboundType(spec);
        }
    }

    /** Server side: attach handlers and the outgoing transport. */
    public static void registerServerHandlers() {
        for (EchoPinsNetwork.PayloadSpec<?> spec : EchoPinsNetwork.serverbound()) {
            attachServerHandler(spec);
        }
        EchoPinsNetwork.installTransport(new EchoPinsNetwork.Transport() {
            @Override
            public void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
                ServerPlayNetworking.send(player, payload);
            }

            @Override
            public void toServer(CustomPacketPayload payload) {
                // Reached only on a client; a dedicated server never sends to itself.
                ClientPlayNetworking.send(payload);
            }
        });
    }

    /** Client side: attach handlers for everything the server may send. */
    public static void registerClientHandlers() {
        for (EchoPinsNetwork.PayloadSpec<?> spec : EchoPinsNetwork.clientbound()) {
            attachClientHandler(spec);
        }
    }

    private static <T extends CustomPacketPayload> void registerServerboundType(
            EchoPinsNetwork.PayloadSpec<T> spec) {
        PayloadTypeRegistry.playC2S().register(spec.type(), spec.codec());
    }

    private static <T extends CustomPacketPayload> void registerClientboundType(
            EchoPinsNetwork.PayloadSpec<T> spec) {
        PayloadTypeRegistry.playS2C().register(spec.type(), spec.codec());
    }

    private static <T extends CustomPacketPayload> void attachServerHandler(
            EchoPinsNetwork.PayloadSpec<T> spec) {
        // Fabric runs the handler on the server thread, so world state may be touched directly.
        // The sender comes from the connection context, never from the payload.
        ServerPlayNetworking.registerGlobalReceiver(spec.type(),
                (payload, context) -> EchoPinsNetwork.handleServerbound(context.player(), payload));
    }

    private static <T extends CustomPacketPayload> void attachClientHandler(
            EchoPinsNetwork.PayloadSpec<T> spec) {
        ClientPlayNetworking.registerGlobalReceiver(spec.type(),
                (payload, context) -> EchoPinsNetwork.handleClientbound(payload));
    }
}
