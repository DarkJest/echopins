package dev.echopins.fabric.network;

import dev.echopins.infrastructure.network.EchoPinsNetwork;
import dev.echopins.infrastructure.network.EchoPinsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Binds the shared protocol to Fabric's 1.20.1 channel networking API. */
public final class FabricNetworking {

    private FabricNetworking() {
    }

    /** Kept as a no-op so common initialization remains explicit across loader generations. */
    public static void registerPayloads() {
    }

    public static void registerServerHandlers() {
        for (EchoPinsNetwork.PayloadSpec<?> spec : EchoPinsNetwork.serverbound()) {
            attachServerHandler(spec);
        }
        EchoPinsNetwork.installTransport(new EchoPinsNetwork.Transport() {
            @Override
            public void toPlayer(ServerPlayer player, EchoPinsPayload payload) {
                FriendlyByteBuf buffer = PacketByteBufs.create();
                encode(EchoPinsNetwork.clientbound(), payload, buffer);
                ServerPlayNetworking.send(player, payload.id(), buffer);
            }

            @Override
            public void toServer(EchoPinsPayload payload) {
                FriendlyByteBuf buffer = PacketByteBufs.create();
                encode(EchoPinsNetwork.serverbound(), payload, buffer);
                ClientPlayNetworking.send(payload.id(), buffer);
            }
        });
    }

    public static void registerClientHandlers() {
        for (EchoPinsNetwork.PayloadSpec<?> spec : EchoPinsNetwork.clientbound()) {
            attachClientHandler(spec);
        }
    }

    private static <T extends EchoPinsPayload> void attachServerHandler(
            EchoPinsNetwork.PayloadSpec<T> spec) {
        ServerPlayNetworking.registerGlobalReceiver(spec.type(),
                (server, player, handler, buffer, responseSender) -> {
                    T payload = spec.codec().decode(buffer);
                    server.execute(() -> EchoPinsNetwork.handleServerbound(player, payload));
                });
    }

    private static <T extends EchoPinsPayload> void attachClientHandler(
            EchoPinsNetwork.PayloadSpec<T> spec) {
        ClientPlayNetworking.registerGlobalReceiver(spec.type(),
                (client, handler, buffer, responseSender) -> {
                    T payload = spec.codec().decode(buffer);
                    client.execute(() -> EchoPinsNetwork.handleClientbound(payload));
                });
    }

    private static void encode(Iterable<EchoPinsNetwork.PayloadSpec<?>> specs,
                               EchoPinsPayload payload, FriendlyByteBuf buffer) {
        for (EchoPinsNetwork.PayloadSpec<?> spec : specs) {
            if (spec.type().equals(payload.id())) {
                encodeUnchecked(spec, payload, buffer);
                return;
            }
        }
        throw new IllegalArgumentException("Unregistered EchoPins payload: " + payload.id());
    }

    @SuppressWarnings("unchecked")
    private static <T extends EchoPinsPayload> void encodeUnchecked(
            EchoPinsNetwork.PayloadSpec<T> spec, EchoPinsPayload payload, FriendlyByteBuf buffer) {
        spec.codec().encode(buffer, (T) payload);
    }
}
