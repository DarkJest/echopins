package dev.echopins.infrastructure.network;

import dev.echopins.infrastructure.network.payload.ClientboundPayloads;
import dev.echopins.infrastructure.network.payload.ServerboundPayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.BiConsumer;

/**
 * Registers every EchoPins payload and provides the send helpers.
 *
 * <p>The protocol carries an explicit version. NeoForge refuses a connection whose EchoPins
 * protocol version differs, which turns a mismatched client/server pair into a clear "wrong
 * version" message instead of a decoding failure somewhere deep in a payload.
 *
 * <p>Handlers run on the main thread (NeoForge's default), so server handlers can touch world
 * state directly.
 */
public final class EchoPinsNetwork {

    /** Bump whenever a payload's wire shape changes incompatibly. */
    public static final String PROTOCOL_VERSION = "1";

    /**
     * Server-side request handling, implemented by the server package. Injected so that the
     * registration code does not depend on the service layer's concrete types.
     */
    public interface ServerRequestHandler {
        void onBeginRecording(ServerPlayer player, ServerboundPayloads.BeginRecording payload);

        void onFinishRecording(ServerPlayer player);

        void onCancelRecording(ServerPlayer player);

        void onCreatePin(ServerPlayer player, ServerboundPayloads.CreatePin payload);

        void onRequestPlayback(ServerPlayer player, ServerboundPayloads.RequestPlayback payload);

        void onStopPlayback(ServerPlayer player, ServerboundPayloads.StopPlayback payload);

        void onDeletePin(ServerPlayer player, ServerboundPayloads.DeletePin payload);

        void onRequestInbox(ServerPlayer player, ServerboundPayloads.RequestInbox payload);

        void onMarkRead(ServerPlayer player, ServerboundPayloads.MarkRead payload);
    }

    private static volatile ServerRequestHandler serverHandler;

    private EchoPinsNetwork() {
    }

    public static void installServerHandler(ServerRequestHandler handler) {
        serverHandler = handler;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar
                .playToServer(ServerboundPayloads.BeginRecording.TYPE,
                        ServerboundPayloads.BeginRecording.CODEC,
                        server(ServerRequestHandler::onBeginRecording))
                .playToServer(ServerboundPayloads.FinishRecording.TYPE,
                        ServerboundPayloads.FinishRecording.CODEC,
                        server((handler, player, payload) -> handler.onFinishRecording(player)))
                .playToServer(ServerboundPayloads.CancelRecording.TYPE,
                        ServerboundPayloads.CancelRecording.CODEC,
                        server((handler, player, payload) -> handler.onCancelRecording(player)))
                .playToServer(ServerboundPayloads.CreatePin.TYPE,
                        ServerboundPayloads.CreatePin.CODEC,
                        server(ServerRequestHandler::onCreatePin))
                .playToServer(ServerboundPayloads.RequestPlayback.TYPE,
                        ServerboundPayloads.RequestPlayback.CODEC,
                        server(ServerRequestHandler::onRequestPlayback))
                .playToServer(ServerboundPayloads.StopPlayback.TYPE,
                        ServerboundPayloads.StopPlayback.CODEC,
                        server(ServerRequestHandler::onStopPlayback))
                .playToServer(ServerboundPayloads.DeletePin.TYPE,
                        ServerboundPayloads.DeletePin.CODEC,
                        server(ServerRequestHandler::onDeletePin))
                .playToServer(ServerboundPayloads.RequestInbox.TYPE,
                        ServerboundPayloads.RequestInbox.CODEC,
                        server(ServerRequestHandler::onRequestInbox))
                .playToServer(ServerboundPayloads.MarkRead.TYPE,
                        ServerboundPayloads.MarkRead.CODEC,
                        server(ServerRequestHandler::onMarkRead));

        registrar
                .playToClient(ClientboundPayloads.ServerSettings.TYPE,
                        ClientboundPayloads.ServerSettings.CODEC,
                        client(ClientPayloadReceiver::onServerSettings))
                .playToClient(ClientboundPayloads.PinsSnapshot.TYPE,
                        ClientboundPayloads.PinsSnapshot.CODEC,
                        client(ClientPayloadReceiver::onSnapshot))
                .playToClient(ClientboundPayloads.PinsDelta.TYPE,
                        ClientboundPayloads.PinsDelta.CODEC,
                        client(ClientPayloadReceiver::onDelta))
                .playToClient(ClientboundPayloads.RecordingState.TYPE,
                        ClientboundPayloads.RecordingState.CODEC,
                        client(ClientPayloadReceiver::onRecordingState))
                .playToClient(ClientboundPayloads.PlaybackState.TYPE,
                        ClientboundPayloads.PlaybackState.CODEC,
                        client(ClientPayloadReceiver::onPlaybackState))
                .playToClient(ClientboundPayloads.ErrorMessage.TYPE,
                        ClientboundPayloads.ErrorMessage.CODEC,
                        client(ClientPayloadReceiver::onError))
                .playToClient(ClientboundPayloads.InboxPage.TYPE,
                        ClientboundPayloads.InboxPage.CODEC,
                        client(ClientPayloadReceiver::onInboxPage))
                .playToClient(ClientboundPayloads.KnownPlayers.TYPE,
                        ClientboundPayloads.KnownPlayers.CODEC,
                        client(ClientPayloadReceiver::onKnownPlayers));
    }

    @FunctionalInterface
    private interface ServerAction<T> {
        void run(ServerRequestHandler handler, ServerPlayer player, T payload);
    }

    /**
     * Wraps a server-side action, resolving the sender from the connection rather than from the
     * payload. This is the single point where "who sent this" is decided, so no handler can be
     * written that trusts a client-supplied identity.
     */
    private static <T extends CustomPacketPayload> IPayloadHandler<T>
    server(ServerAction<T> action) {
        return (payload, context) -> {
            ServerRequestHandler handler = serverHandler;
            if (handler == null || !(context.player() instanceof ServerPlayer sender)) {
                return;
            }
            action.run(handler, sender, payload);
        };
    }

    private static <T extends CustomPacketPayload> IPayloadHandler<T>
    client(BiConsumer<ClientPayloadReceiver, T> action) {
        return (payload, context) -> action.accept(ClientPayloadReceiver.Holder.get(), payload);
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
