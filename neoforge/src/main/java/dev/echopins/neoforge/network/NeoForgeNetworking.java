package dev.echopins.neoforge.network;

import dev.echopins.EchoPins;
import dev.echopins.infrastructure.network.EchoPinsNetwork;
import dev.echopins.infrastructure.network.EchoPinsPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Shared Forge/early-NeoForge 1.20.1 SimpleChannel adapter. */
public final class NeoForgeNetworking {

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EchoPins.MOD_ID, "main"),
            () -> EchoPinsNetwork.PROTOCOL_VERSION,
            EchoPinsNetwork.PROTOCOL_VERSION::equals,
            EchoPinsNetwork.PROTOCOL_VERSION::equals);

    private static int discriminator;

    private NeoForgeNetworking() {
    }

    public static void register() {
        for (EchoPinsNetwork.PayloadSpec<?> spec : EchoPinsNetwork.serverbound()) {
            registerServerbound(spec);
        }
        for (EchoPinsNetwork.PayloadSpec<?> spec : EchoPinsNetwork.clientbound()) {
            registerClientbound(spec);
        }

        EchoPinsNetwork.installTransport(new EchoPinsNetwork.Transport() {
            @Override
            public void toPlayer(ServerPlayer player, EchoPinsPayload payload) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
            }

            @Override
            public void toServer(EchoPinsPayload payload) {
                CHANNEL.sendToServer(payload);
            }
        });
    }

    private static <T extends EchoPinsPayload> void registerServerbound(
            EchoPinsNetwork.PayloadSpec<T> spec) {
        CHANNEL.messageBuilder(spec.payloadClass(), discriminator++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buffer) -> spec.codec().encode(buffer, payload))
                .decoder(spec.codec()::decode)
                .consumerMainThread((payload, context) -> handleServerbound(payload, context))
                .add();
    }

    private static <T extends EchoPinsPayload> void registerClientbound(
            EchoPinsNetwork.PayloadSpec<T> spec) {
        CHANNEL.messageBuilder(spec.payloadClass(), discriminator++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buffer) -> spec.codec().encode(buffer, payload))
                .decoder(spec.codec()::decode)
                .consumerMainThread((payload, context) -> handleClientbound(payload, context))
                .add();
    }

    private static void handleServerbound(EchoPinsPayload payload,
                                          Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            EchoPinsNetwork.handleServerbound(sender, payload);
        }
        context.setPacketHandled(true);
    }

    private static void handleClientbound(EchoPinsPayload payload,
                                          Supplier<NetworkEvent.Context> contextSupplier) {
        EchoPinsNetwork.handleClientbound(payload);
        contextSupplier.get().setPacketHandled(true);
    }
}
