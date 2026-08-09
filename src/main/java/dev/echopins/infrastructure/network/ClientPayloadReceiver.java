package dev.echopins.infrastructure.network;

import dev.echopins.infrastructure.network.payload.ClientboundPayloads;

/**
 * Receives client-bound payloads.
 *
 * <p>This indirection is what keeps a dedicated server from ever touching a client class.
 * Payload registration is common code and must reference <em>something</em> to hand the payload
 * to; referencing the client handler directly - even inside a {@code dist.isClient()} branch -
 * puts that class in the constant pool of a common class and risks it being loaded on a server
 * that has no rendering classes at all.
 *
 * <p>The client installs {@link dev.echopins.client.EchoPinsClient}'s implementation during
 * client setup. On a dedicated server the no-op stays in place and nothing client-side is ever
 * resolved.
 */
public interface ClientPayloadReceiver {

    ClientPayloadReceiver NOOP = new ClientPayloadReceiver() {
    };

    default void onServerSettings(ClientboundPayloads.ServerSettings payload) {
    }

    default void onSnapshot(ClientboundPayloads.PinsSnapshot payload) {
    }

    default void onDelta(ClientboundPayloads.PinsDelta payload) {
    }

    default void onRecordingState(ClientboundPayloads.RecordingState payload) {
    }

    default void onPlaybackState(ClientboundPayloads.PlaybackState payload) {
    }

    default void onError(ClientboundPayloads.ErrorMessage payload) {
    }

    default void onInboxPage(ClientboundPayloads.InboxPage payload) {
    }

    default void onKnownPlayers(ClientboundPayloads.KnownPlayers payload) {
    }

    /** Holder for the active receiver. */
    final class Holder {
        private static volatile ClientPayloadReceiver current = NOOP;

        private Holder() {
        }

        public static void install(ClientPayloadReceiver receiver) {
            current = receiver;
        }

        public static ClientPayloadReceiver get() {
            return current;
        }
    }
}
