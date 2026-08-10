package dev.echopins.infrastructure.network.payload;

import dev.echopins.EchoPins;
import dev.echopins.domain.error.EchoPinError;
import dev.echopins.domain.pin.PinId;
import dev.echopins.infrastructure.network.NetCodecs;
import dev.echopins.infrastructure.network.PinSummary;
import dev.echopins.infrastructure.network.PacketCodec;
import dev.echopins.infrastructure.network.EchoPinsPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Everything the server tells a client. */
public final class ClientboundPayloads {

    /**
     * Hard ceiling on how many summaries fit in one payload, independent of config. Keeps a
     * single packet well inside Minecraft's frame limit even at the largest configured
     * subscription size.
     */
    public static final int MAX_SUMMARIES_PER_PACKET = 512;

    private ClientboundPayloads() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(EchoPins.MOD_ID, path);
    }

    /**
     * The server-enforced values a client needs in order to render honestly and to avoid sending
     * requests that would only be rejected. Sent once on join and again after a config reload.
     */
    public record ServerSettings(double discoveryRadius, double interactionRadius,
                                 int maxRecordingSeconds, int minRecordingMillis,
                                 int maxCaptionLength, int maxPrivateRecipients,
                                 boolean allowPermanentPins) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ClientboundPayloads.id("server_settings");

        public static final PacketCodec<ServerSettings> CODEC =
                PacketCodec.of(
                        (buf, payload) -> {
                            buf.writeDouble(payload.discoveryRadius);
                            buf.writeDouble(payload.interactionRadius);
                            buf.writeVarInt(payload.maxRecordingSeconds);
                            buf.writeVarInt(payload.minRecordingMillis);
                            buf.writeVarInt(payload.maxCaptionLength);
                            buf.writeVarInt(payload.maxPrivateRecipients);
                            buf.writeBoolean(payload.allowPermanentPins);
                        },
                        buf -> new ServerSettings(
                                buf.readDouble(), buf.readDouble(),
                                buf.readVarInt(), buf.readVarInt(),
                                buf.readVarInt(), buf.readVarInt(),
                                buf.readBoolean()));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** Replaces the client's whole set of known pins. Sent on join and on dimension change. */
    public record PinsSnapshot(List<PinSummary> pins) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ClientboundPayloads.id("pins_snapshot");

        public static final PacketCodec<PinsSnapshot> CODEC =
                PacketCodec.of(
                        (buf, payload) -> NetCodecs.writeBoundedList(buf, payload.pins,
                                MAX_SUMMARIES_PER_PACKET,
                                (b, pin) -> PinSummary.STREAM_CODEC.encode(b, pin)),
                        buf -> new PinsSnapshot(NetCodecs.readBoundedList(buf,
                                MAX_SUMMARIES_PER_PACKET,
                                b -> PinSummary.STREAM_CODEC.decode(b))));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /**
     * Incremental subscription update. Sent instead of a snapshot while a player moves around,
     * so routine movement costs a few bytes rather than the whole visible set.
     */
    public record PinsDelta(List<PinSummary> added, List<PinId> removed) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ClientboundPayloads.id("pins_delta");

        public static final PacketCodec<PinsDelta> CODEC =
                PacketCodec.of(
                        (buf, payload) -> {
                            NetCodecs.writeBoundedList(buf, payload.added, MAX_SUMMARIES_PER_PACKET,
                                    (b, pin) -> PinSummary.STREAM_CODEC.encode(b, pin));
                            NetCodecs.writeBoundedList(buf, payload.removed, MAX_SUMMARIES_PER_PACKET,
                                    NetCodecs::writePinId);
                        },
                        buf -> new PinsDelta(
                                NetCodecs.readBoundedList(buf, MAX_SUMMARIES_PER_PACKET,
                                        b -> PinSummary.STREAM_CODEC.decode(b)),
                                NetCodecs.readBoundedList(buf, MAX_SUMMARIES_PER_PACKET,
                                        NetCodecs::readPinId)));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** Where a recording session is in its lifecycle. */
    public enum RecordingPhase {
        /** Capture is running. */
        RECORDING(0),
        /** Capture finished and audio is stored; the client should offer save or discard. */
        AWAITING_CONFIRMATION(1),
        /** No session; the client should clear its HUD. */
        IDLE(2);

        private final int id;

        RecordingPhase(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static RecordingPhase byId(int id) {
            for (RecordingPhase phase : values()) {
                if (phase.id == id) {
                    return phase;
                }
            }
            return IDLE;
        }
    }

    /**
     * Recording progress.
     *
     * @param receivingAudio whether any voice has actually arrived yet. Drives the
     *                       "hold your push-to-talk key" hint, which is the difference between a
     *                       player thinking the mod is broken and understanding what to do.
     */
    public record RecordingState(RecordingPhase phase, int elapsedMillis, int maxMillis,
                                 boolean receivingAudio) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ClientboundPayloads.id("recording_state");

        public static final PacketCodec<RecordingState> CODEC =
                PacketCodec.of(
                        (buf, payload) -> {
                            buf.writeByte(payload.phase.id());
                            buf.writeVarInt(payload.elapsedMillis);
                            buf.writeVarInt(payload.maxMillis);
                            buf.writeBoolean(payload.receivingAudio);
                        },
                        buf -> new RecordingState(
                                RecordingPhase.byId(buf.readByte()),
                                buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** Playback lifecycle for one pin. */
    public enum PlaybackPhase {
        STARTED(0),
        FINISHED(1),
        STOPPED(2);

        private final int id;

        PlaybackPhase(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static PlaybackPhase byId(int id) {
            for (PlaybackPhase phase : values()) {
                if (phase.id == id) {
                    return phase;
                }
            }
            return FINISHED;
        }
    }

    /** Tells the client a pin started or stopped playing, so it can show progress. */
    public record PlaybackState(PinId pin, PlaybackPhase phase,
                                int durationMillis) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ClientboundPayloads.id("playback_state");

        public static final PacketCodec<PlaybackState> CODEC =
                PacketCodec.of(
                        (buf, payload) -> {
                            NetCodecs.writePinId(buf, payload.pin);
                            buf.writeByte(payload.phase.id());
                            buf.writeVarInt(payload.durationMillis);
                        },
                        buf -> new PlaybackState(
                                NetCodecs.readPinId(buf),
                                PlaybackPhase.byId(buf.readByte()),
                                buf.readVarInt()));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /**
     * A failure the player should see.
     *
     * @param error    the reason; the client resolves it to a localized message
     * @param argument optional numeric detail, for example the seconds left on a cooldown
     */
    public record ErrorMessage(EchoPinError error, Optional<Long> argument) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ClientboundPayloads.id("error");

        public static final PacketCodec<ErrorMessage> CODEC =
                PacketCodec.of(
                        (buf, payload) -> {
                            buf.writeVarInt(payload.error.id());
                            buf.writeBoolean(payload.argument.isPresent());
                            payload.argument.ifPresent(buf::writeVarLong);
                        },
                        buf -> new ErrorMessage(
                                EchoPinError.byId(buf.readVarInt()),
                                buf.readBoolean() ? Optional.of(buf.readVarLong()) : Optional.empty()));

        public static ErrorMessage of(EchoPinError error) {
            return new ErrorMessage(error, Optional.empty());
        }

        public static ErrorMessage of(EchoPinError error, long argument) {
            return new ErrorMessage(error, Optional.of(argument));
        }

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** One page of inbox results. */
    public record InboxPage(ServerboundPayloads.InboxTab tab, int page, int totalPages,
                            List<PinSummary> entries) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ClientboundPayloads.id("inbox_page");

        public static final PacketCodec<InboxPage> CODEC =
                PacketCodec.of(
                        (buf, payload) -> {
                            buf.writeByte(payload.tab.id());
                            buf.writeVarInt(payload.page);
                            buf.writeVarInt(payload.totalPages);
                            NetCodecs.writeBoundedList(buf, payload.entries, MAX_SUMMARIES_PER_PACKET,
                                    (b, pin) -> PinSummary.STREAM_CODEC.encode(b, pin));
                        },
                        buf -> new InboxPage(
                                ServerboundPayloads.InboxTab.byId(buf.readByte()),
                                buf.readVarInt(), buf.readVarInt(),
                                NetCodecs.readBoundedList(buf, MAX_SUMMARIES_PER_PACKET,
                                        b -> PinSummary.STREAM_CODEC.decode(b))));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** A player the server knows about, offered as a recipient choice. */
    public record KnownPlayer(java.util.UUID uuid, String name, boolean online) {
    }

    /**
     * The address book used by the private-pin recipient picker.
     *
     * <p>Only players the server itself already knows are ever listed, and the client is told
     * nothing beyond a name and an online flag.
     */
    public record KnownPlayers(List<KnownPlayer> players) implements EchoPinsPayload {

        public static final int MAX_PLAYERS = 256;

        public static final ResourceLocation TYPE = ClientboundPayloads.id("known_players");

        public static final PacketCodec<KnownPlayers> CODEC =
                PacketCodec.of(
                        (buf, payload) -> NetCodecs.writeBoundedList(buf, payload.players, MAX_PLAYERS,
                                (b, player) -> {
                                    NetCodecs.writeUuid(b, player.uuid());
                                    NetCodecs.writeBoundedString(b, player.name(),
                                            dev.echopins.domain.pin.PinAuthor.MAX_NAME_LENGTH);
                                    b.writeBoolean(player.online());
                                }),
                        buf -> new KnownPlayers(NetCodecs.readBoundedList(buf, MAX_PLAYERS,
                                b -> new KnownPlayer(
                                        NetCodecs.readUuid(b),
                                        NetCodecs.readBoundedString(b,
                                                dev.echopins.domain.pin.PinAuthor.MAX_NAME_LENGTH),
                                        b.readBoolean()))));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }
}
