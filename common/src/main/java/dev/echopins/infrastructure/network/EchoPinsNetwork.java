package dev.echopins.infrastructure.network;

import dev.echopins.infrastructure.network.payload.ClientboundPayloads;
import dev.echopins.infrastructure.network.payload.ServerboundPayloads;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The protocol: payload types, their codecs, and where an arriving payload goes.
 *
 * <p>Loader-agnostic on purpose. Payload registration and packet transport differ between NeoForge
 * and Fabric, so each loader supplies a {@link Transport} and walks {@link #serverbound()} and
 * {@link #clientbound()} to register the same types through its own API. Everything else - which
 * payloads exist, how they encode, and what happens when one arrives - lives here once, so the two
 * loaders cannot drift into speaking slightly different protocols.
 *
 * <p>The protocol carries an explicit version, which both loaders check, turning a mismatched
 * client and server into a clear message rather than a decode failure deep inside a payload.
 */
public final class EchoPinsNetwork {

    /** Bump whenever a payload's wire shape changes incompatibly. */
    public static final String PROTOCOL_VERSION = "1";

    /** One registrable payload: its type and the codec that reads and writes it. */
    public record PayloadSpec<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec) {
    }

    /** How packets actually leave this side. Supplied by the loader. */
    public interface Transport {
        void toPlayer(ServerPlayer player, CustomPacketPayload payload);

        void toServer(CustomPacketPayload payload);
    }

    /**
     * Server-side request handling, implemented by the server package. Injected so this class does
     * not depend on the service layer's concrete types.
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
    private static volatile Transport transport;

    private EchoPinsNetwork() {
    }

    public static void installServerHandler(ServerRequestHandler handler) {
        serverHandler = handler;
    }

    public static void installTransport(Transport newTransport) {
        transport = newTransport;
    }

    /** Every payload a client may send. */
    public static List<PayloadSpec<?>> serverbound() {
        return List.of(
                new PayloadSpec<>(ServerboundPayloads.BeginRecording.TYPE,
                        ServerboundPayloads.BeginRecording.CODEC),
                new PayloadSpec<>(ServerboundPayloads.FinishRecording.TYPE,
                        ServerboundPayloads.FinishRecording.CODEC),
                new PayloadSpec<>(ServerboundPayloads.CancelRecording.TYPE,
                        ServerboundPayloads.CancelRecording.CODEC),
                new PayloadSpec<>(ServerboundPayloads.CreatePin.TYPE,
                        ServerboundPayloads.CreatePin.CODEC),
                new PayloadSpec<>(ServerboundPayloads.RequestPlayback.TYPE,
                        ServerboundPayloads.RequestPlayback.CODEC),
                new PayloadSpec<>(ServerboundPayloads.StopPlayback.TYPE,
                        ServerboundPayloads.StopPlayback.CODEC),
                new PayloadSpec<>(ServerboundPayloads.DeletePin.TYPE,
                        ServerboundPayloads.DeletePin.CODEC),
                new PayloadSpec<>(ServerboundPayloads.RequestInbox.TYPE,
                        ServerboundPayloads.RequestInbox.CODEC),
                new PayloadSpec<>(ServerboundPayloads.MarkRead.TYPE,
                        ServerboundPayloads.MarkRead.CODEC));
    }

    /** Every payload the server may send. */
    public static List<PayloadSpec<?>> clientbound() {
        return List.of(
                new PayloadSpec<>(ClientboundPayloads.ServerSettings.TYPE,
                        ClientboundPayloads.ServerSettings.CODEC),
                new PayloadSpec<>(ClientboundPayloads.PinsSnapshot.TYPE,
                        ClientboundPayloads.PinsSnapshot.CODEC),
                new PayloadSpec<>(ClientboundPayloads.PinsDelta.TYPE,
                        ClientboundPayloads.PinsDelta.CODEC),
                new PayloadSpec<>(ClientboundPayloads.RecordingState.TYPE,
                        ClientboundPayloads.RecordingState.CODEC),
                new PayloadSpec<>(ClientboundPayloads.PlaybackState.TYPE,
                        ClientboundPayloads.PlaybackState.CODEC),
                new PayloadSpec<>(ClientboundPayloads.ErrorMessage.TYPE,
                        ClientboundPayloads.ErrorMessage.CODEC),
                new PayloadSpec<>(ClientboundPayloads.InboxPage.TYPE,
                        ClientboundPayloads.InboxPage.CODEC),
                new PayloadSpec<>(ClientboundPayloads.KnownPlayers.TYPE,
                        ClientboundPayloads.KnownPlayers.CODEC));
    }

    /**
     * Routes a payload that arrived from a client.
     *
     * <p>The sender is resolved by the loader from the connection and passed in. This is the
     * single point where "who sent this" is decided, so no handler can be written that trusts a
     * client-supplied identity.
     */
    public static void handleServerbound(ServerPlayer sender, CustomPacketPayload payload) {
        ServerRequestHandler handler = serverHandler;
        if (handler == null || sender == null) {
            return;
        }
        switch (payload) {
            case ServerboundPayloads.BeginRecording p -> handler.onBeginRecording(sender, p);
            case ServerboundPayloads.FinishRecording ignored -> handler.onFinishRecording(sender);
            case ServerboundPayloads.CancelRecording ignored -> handler.onCancelRecording(sender);
            case ServerboundPayloads.CreatePin p -> handler.onCreatePin(sender, p);
            case ServerboundPayloads.RequestPlayback p -> handler.onRequestPlayback(sender, p);
            case ServerboundPayloads.StopPlayback p -> handler.onStopPlayback(sender, p);
            case ServerboundPayloads.DeletePin p -> handler.onDeletePin(sender, p);
            case ServerboundPayloads.RequestInbox p -> handler.onRequestInbox(sender, p);
            case ServerboundPayloads.MarkRead p -> handler.onMarkRead(sender, p);
            default -> {
                // Unknown payloads can only mean a protocol change, which the version guard
                // should already have refused, so there is nothing useful to do here.
            }
        }
    }

    /** Routes a payload that arrived from the server. */
    public static void handleClientbound(CustomPacketPayload payload) {
        ClientPayloadReceiver receiver = ClientPayloadReceiver.Holder.get();
        switch (payload) {
            case ClientboundPayloads.ServerSettings p -> receiver.onServerSettings(p);
            case ClientboundPayloads.PinsSnapshot p -> receiver.onSnapshot(p);
            case ClientboundPayloads.PinsDelta p -> receiver.onDelta(p);
            case ClientboundPayloads.RecordingState p -> receiver.onRecordingState(p);
            case ClientboundPayloads.PlaybackState p -> receiver.onPlaybackState(p);
            case ClientboundPayloads.ErrorMessage p -> receiver.onError(p);
            case ClientboundPayloads.InboxPage p -> receiver.onInboxPage(p);
            case ClientboundPayloads.KnownPlayers p -> receiver.onKnownPlayers(p);
            default -> {
                // See above.
            }
        }
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        Transport current = transport;
        if (current != null) {
            current.toPlayer(player, payload);
        }
    }

    public static void sendToServer(CustomPacketPayload payload) {
        Transport current = transport;
        if (current != null) {
            current.toServer(payload);
        }
    }
}
