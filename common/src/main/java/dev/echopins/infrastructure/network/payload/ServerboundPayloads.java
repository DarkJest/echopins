package dev.echopins.infrastructure.network.payload;

import dev.echopins.EchoPins;
import dev.echopins.domain.expiry.ExpiryChoice;
import dev.echopins.domain.pin.Caption;
import dev.echopins.domain.pin.PinId;
import dev.echopins.domain.visibility.Visibility;
import dev.echopins.infrastructure.network.NetCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import dev.echopins.infrastructure.network.PacketCodec;
import dev.echopins.infrastructure.network.EchoPinsPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a client may ask the server to do.
 *
 * <p>These payloads are requests, never statements of fact. Nothing here carries an author, a
 * dimension or a timestamp: the server takes those from the connection and its own clock, so a
 * client cannot claim to be someone else, to be somewhere else, or to have created a pin in the
 * past. The one piece of client-supplied world state, the block a player is looking at, is
 * re-validated against the player's real position and reach before it is used.
 */
public final class ServerboundPayloads {

    /** Absolute cap on recipients accepted off the wire, independent of config. */
    public static final int MAX_RECIPIENTS_WIRE_LIMIT = 64;

    private ServerboundPayloads() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(EchoPins.MOD_ID, path);
    }

    /**
     * Asks the server to start capturing the player's voice.
     *
     * @param blockTarget the block face the player is looking at, if any. Only a hint: the server
     *                    re-traces reach and uses the player's own dimension regardless.
     */
    public record BeginRecording(Optional<BlockTarget> blockTarget) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ServerboundPayloads.id("begin_recording");

        public static final PacketCodec<BeginRecording> CODEC =
                PacketCodec.of(
                        (buf, payload) -> {
                            buf.writeBoolean(payload.blockTarget.isPresent());
                            payload.blockTarget.ifPresent(target -> BlockTarget.write(buf, target));
                        },
                        buf -> new BeginRecording(buf.readBoolean()
                                ? Optional.of(BlockTarget.read(buf))
                                : Optional.empty()));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** A block position and the face that was hit. */
    public record BlockTarget(BlockPos pos, int faceId) {

        static void write(FriendlyByteBuf buf, BlockTarget target) {
            buf.writeBlockPos(target.pos);
            buf.writeByte(target.faceId);
        }

        static BlockTarget read(FriendlyByteBuf buf) {
            return new BlockTarget(buf.readBlockPos(), buf.readByte());
        }
    }

    /** Stops capture and asks the server to keep what was recorded, pending confirmation. */
    public record FinishRecording() implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ServerboundPayloads.id("finish_recording");

        public static final PacketCodec<FinishRecording> CODEC =
                PacketCodec.unit(new FinishRecording());

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** Abandons the recording and any audio already written for it. */
    public record CancelRecording() implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ServerboundPayloads.id("cancel_recording");

        public static final PacketCodec<CancelRecording> CODEC =
                PacketCodec.unit(new CancelRecording());

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** Confirms a pending recording and supplies the pin's settings. */
    public record CreatePin(Visibility visibility, Set<UUID> recipients,
                            Optional<String> caption, ExpiryChoice expiry) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ServerboundPayloads.id("create_pin");

        public static final PacketCodec<CreatePin> CODEC =
                PacketCodec.of(
                        (buf, payload) -> {
                            buf.writeByte(payload.visibility.id());
                            NetCodecs.writeUuidSet(buf, payload.recipients, MAX_RECIPIENTS_WIRE_LIMIT);
                            buf.writeBoolean(payload.caption.isPresent());
                            payload.caption.ifPresent(text ->
                                    NetCodecs.writeBoundedString(buf, text, Caption.HARD_MAX_LENGTH));
                            buf.writeByte(payload.expiry.id());
                        },
                        buf -> new CreatePin(
                                Visibility.byId(buf.readByte()),
                                NetCodecs.readBoundedUuidSet(buf, MAX_RECIPIENTS_WIRE_LIMIT),
                                buf.readBoolean()
                                        ? Optional.of(NetCodecs.readBoundedString(buf, Caption.HARD_MAX_LENGTH))
                                        : Optional.empty(),
                                ExpiryChoice.byId(buf.readByte())));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** Asks to hear a pin. The server re-checks access and distance before playing anything. */
    public record RequestPlayback(PinId pin) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ServerboundPayloads.id("request_playback");

        public static final PacketCodec<RequestPlayback> CODEC =
                PacketCodec.of(
                        (buf, payload) -> NetCodecs.writePinId(buf, payload.pin),
                        buf -> new RequestPlayback(NetCodecs.readPinId(buf)));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** Stops a playback this player started. */
    public record StopPlayback(PinId pin) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ServerboundPayloads.id("stop_playback");

        public static final PacketCodec<StopPlayback> CODEC =
                PacketCodec.of(
                        (buf, payload) -> NetCodecs.writePinId(buf, payload.pin),
                        buf -> new StopPlayback(NetCodecs.readPinId(buf)));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** Asks to delete a pin. Authorised server-side; ownership is never taken from the client. */
    public record DeletePin(PinId pin) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ServerboundPayloads.id("delete_pin");

        public static final PacketCodec<DeletePin> CODEC =
                PacketCodec.of(
                        (buf, payload) -> NetCodecs.writePinId(buf, payload.pin),
                        buf -> new DeletePin(NetCodecs.readPinId(buf)));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** Which inbox list the client wants. */
    public enum InboxTab {
        NEARBY(0),
        MINE(1),
        PRIVATE(2),
        UNREAD(3);

        private final int id;

        InboxTab(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static InboxTab byId(int id) {
            for (InboxTab tab : values()) {
                if (tab.id == id) {
                    return tab;
                }
            }
            return NEARBY;
        }
    }

    /** Requests one page of the inbox. */
    public record RequestInbox(InboxTab tab, int page) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ServerboundPayloads.id("request_inbox");

        public static final PacketCodec<RequestInbox> CODEC =
                PacketCodec.of(
                        (buf, payload) -> {
                            buf.writeByte(payload.tab.id());
                            buf.writeVarInt(payload.page);
                        },
                        buf -> {
                            InboxTab tab = InboxTab.byId(buf.readByte());
                            int page = buf.readVarInt();
                            if (page < 0 || page > 1024) {
                                throw new NetCodecs.MalformedPayloadException("Page out of range: " + page);
                            }
                            return new RequestInbox(tab, page);
                        });

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }

    /** Marks a pin as listened to. */
    public record MarkRead(PinId pin) implements EchoPinsPayload {

        public static final ResourceLocation TYPE = ServerboundPayloads.id("mark_read");

        public static final PacketCodec<MarkRead> CODEC =
                PacketCodec.of(
                        (buf, payload) -> NetCodecs.writePinId(buf, payload.pin),
                        buf -> new MarkRead(NetCodecs.readPinId(buf)));

        @Override
        public ResourceLocation id() {
            return TYPE;
        }
    }
}
