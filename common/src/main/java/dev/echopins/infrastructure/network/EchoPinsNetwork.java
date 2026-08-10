package dev.echopins.infrastructure.network;

import dev.echopins.infrastructure.network.payload.ClientboundPayloads;
import dev.echopins.infrastructure.network.payload.ServerboundPayloads;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The protocol: payload types, their codecs, and where an arriving payload goes.
 *
 * <p>Loader-agnostic on purpose. Packet transport differs between Forge-family loaders and
 * Fabric, so each loader supplies a {@link Transport} and walks {@link #serverbound()} and
 * {@link #clientbound()} to register the same types through its own API. Everything else - which
 * payloads exist, how they encode, and what happens when one arrives - lives here once, so the two
 * loader builds cannot drift into speaking slightly different protocols.
 *
 * <p>{@link #PROTOCOL_VERSION} is enforced only where the loader can enforce it. Forge and early
 * NeoForge negotiate it during login, so a mismatched client is refused. Fabric has no
 * equivalent negotiation, so there a mismatch surfaces later as a decode failure. This is why the
 * version is bumped rather than reused: on Fabric it is the payload identifiers themselves that
 * have to change for an incompatible client to be rejected cleanly.
 */
public final class EchoPinsNetwork {

    /** Bump whenever a payload's wire shape changes incompatibly. */
    public static final String PROTOCOL_VERSION = "1";

    /** One registrable payload: its type and the codec that reads and writes it. */
    public record PayloadSpec<T extends EchoPinsPayload>(
            ResourceLocation type,
            Class<T> payloadClass,
            PacketCodec<T> codec) {
    }

    /** How packets actually leave this side. Supplied by the loader. */
    public interface Transport {
        void toPlayer(ServerPlayer player, EchoPinsPayload payload);

        void toServer(EchoPinsPayload payload);
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
                new PayloadSpec<>(ServerboundPayloads.BeginRecording.TYPE, ServerboundPayloads.BeginRecording.class,
                        ServerboundPayloads.BeginRecording.CODEC),
                new PayloadSpec<>(ServerboundPayloads.FinishRecording.TYPE, ServerboundPayloads.FinishRecording.class,
                        ServerboundPayloads.FinishRecording.CODEC),
                new PayloadSpec<>(ServerboundPayloads.CancelRecording.TYPE, ServerboundPayloads.CancelRecording.class,
                        ServerboundPayloads.CancelRecording.CODEC),
                new PayloadSpec<>(ServerboundPayloads.CreatePin.TYPE, ServerboundPayloads.CreatePin.class,
                        ServerboundPayloads.CreatePin.CODEC),
                new PayloadSpec<>(ServerboundPayloads.RequestPlayback.TYPE, ServerboundPayloads.RequestPlayback.class,
                        ServerboundPayloads.RequestPlayback.CODEC),
                new PayloadSpec<>(ServerboundPayloads.StopPlayback.TYPE, ServerboundPayloads.StopPlayback.class,
                        ServerboundPayloads.StopPlayback.CODEC),
                new PayloadSpec<>(ServerboundPayloads.DeletePin.TYPE, ServerboundPayloads.DeletePin.class,
                        ServerboundPayloads.DeletePin.CODEC),
                new PayloadSpec<>(ServerboundPayloads.RequestInbox.TYPE, ServerboundPayloads.RequestInbox.class,
                        ServerboundPayloads.RequestInbox.CODEC),
                new PayloadSpec<>(ServerboundPayloads.MarkRead.TYPE, ServerboundPayloads.MarkRead.class,
                        ServerboundPayloads.MarkRead.CODEC));
    }

    /** Every payload the server may send. */
    public static List<PayloadSpec<?>> clientbound() {
        return List.of(
                new PayloadSpec<>(ClientboundPayloads.ServerSettings.TYPE, ClientboundPayloads.ServerSettings.class,
                        ClientboundPayloads.ServerSettings.CODEC),
                new PayloadSpec<>(ClientboundPayloads.PinsSnapshot.TYPE, ClientboundPayloads.PinsSnapshot.class,
                        ClientboundPayloads.PinsSnapshot.CODEC),
                new PayloadSpec<>(ClientboundPayloads.PinsDelta.TYPE, ClientboundPayloads.PinsDelta.class,
                        ClientboundPayloads.PinsDelta.CODEC),
                new PayloadSpec<>(ClientboundPayloads.RecordingState.TYPE, ClientboundPayloads.RecordingState.class,
                        ClientboundPayloads.RecordingState.CODEC),
                new PayloadSpec<>(ClientboundPayloads.PlaybackState.TYPE, ClientboundPayloads.PlaybackState.class,
                        ClientboundPayloads.PlaybackState.CODEC),
                new PayloadSpec<>(ClientboundPayloads.ErrorMessage.TYPE, ClientboundPayloads.ErrorMessage.class,
                        ClientboundPayloads.ErrorMessage.CODEC),
                new PayloadSpec<>(ClientboundPayloads.InboxPage.TYPE, ClientboundPayloads.InboxPage.class,
                        ClientboundPayloads.InboxPage.CODEC),
                new PayloadSpec<>(ClientboundPayloads.KnownPlayers.TYPE, ClientboundPayloads.KnownPlayers.class,
                        ClientboundPayloads.KnownPlayers.CODEC));
    }

    /**
     * Routes a payload that arrived from a client.
     *
     * <p>The sender is resolved by the loader from the connection and passed in. This is the
     * single point where "who sent this" is decided, so no handler can be written that trusts a
     * client-supplied identity.
     */
    public static void handleServerbound(ServerPlayer sender, EchoPinsPayload payload) {
        ServerRequestHandler handler = serverHandler;
        if (handler == null || sender == null) {
            return;
        }
        if (payload instanceof ServerboundPayloads.BeginRecording p) {
            handler.onBeginRecording(sender, p);
        } else if (payload instanceof ServerboundPayloads.FinishRecording) {
            handler.onFinishRecording(sender);
        } else if (payload instanceof ServerboundPayloads.CancelRecording) {
            handler.onCancelRecording(sender);
        } else if (payload instanceof ServerboundPayloads.CreatePin p) {
            handler.onCreatePin(sender, p);
        } else if (payload instanceof ServerboundPayloads.RequestPlayback p) {
            handler.onRequestPlayback(sender, p);
        } else if (payload instanceof ServerboundPayloads.StopPlayback p) {
            handler.onStopPlayback(sender, p);
        } else if (payload instanceof ServerboundPayloads.DeletePin p) {
            handler.onDeletePin(sender, p);
        } else if (payload instanceof ServerboundPayloads.RequestInbox p) {
            handler.onRequestInbox(sender, p);
        } else if (payload instanceof ServerboundPayloads.MarkRead p) {
            handler.onMarkRead(sender, p);
        }
    }

    /** Routes a payload that arrived from the server. */
    public static void handleClientbound(EchoPinsPayload payload) {
        ClientPayloadReceiver receiver = ClientPayloadReceiver.Holder.get();
        if (payload instanceof ClientboundPayloads.ServerSettings p) {
            receiver.onServerSettings(p);
        } else if (payload instanceof ClientboundPayloads.PinsSnapshot p) {
            receiver.onSnapshot(p);
        } else if (payload instanceof ClientboundPayloads.PinsDelta p) {
            receiver.onDelta(p);
        } else if (payload instanceof ClientboundPayloads.RecordingState p) {
            receiver.onRecordingState(p);
        } else if (payload instanceof ClientboundPayloads.PlaybackState p) {
            receiver.onPlaybackState(p);
        } else if (payload instanceof ClientboundPayloads.ErrorMessage p) {
            receiver.onError(p);
        } else if (payload instanceof ClientboundPayloads.InboxPage p) {
            receiver.onInboxPage(p);
        } else if (payload instanceof ClientboundPayloads.KnownPlayers p) {
            receiver.onKnownPlayers(p);
        }
    }

    public static void sendTo(ServerPlayer player, EchoPinsPayload payload) {
        Transport current = transport;
        if (current != null) {
            current.toPlayer(player, payload);
        }
    }

    public static void sendToServer(EchoPinsPayload payload) {
        Transport current = transport;
        if (current != null) {
            current.toServer(payload);
        }
    }
}
