package dev.echopins.infrastructure.network;

import dev.echopins.domain.anchor.WorldAnchor;
import dev.echopins.domain.pin.Caption;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinId;
import dev.echopins.domain.visibility.Visibility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * What a client is told about a pin it can see.
 *
 * <p>Deliberately not the whole {@link EchoPin}. The recipient list of a private pin is never
 * sent - a client only ever learns that a pin is private, not who else can hear it, so the
 * client cannot be used to enumerate a player's private contacts.
 *
 * @param id           pin identity
 * @param authorId     author UUID, so the client can grey out delete for pins it does not own
 * @param authorName   display name
 * @param anchor       where to draw the marker
 * @param createdAt    creation time in epoch millis, for the "12s ago" label
 * @param durationMillis playback length
 * @param visibility   public or private, for the lock icon
 * @param caption      optional text label
 * @param unread       whether this viewer has not played it yet
 */
public record PinSummary(
        PinId id,
        UUID authorId,
        String authorName,
        WorldAnchor anchor,
        long createdAt,
        int durationMillis,
        Visibility visibility,
        Optional<String> caption,
        boolean unread) {

    public static final StreamCodec<RegistryFriendlyByteBuf, PinSummary> STREAM_CODEC =
            StreamCodec.of(PinSummary::write, PinSummary::read);

    /** Builds the view of {@code pin} shown to {@code viewer}. */
    public static PinSummary of(EchoPin pin, boolean unread) {
        return new PinSummary(
                pin.id(),
                pin.authorUuid(),
                pin.author().lastKnownName(),
                pin.anchor(),
                pin.createdAt(),
                pin.durationMillis(),
                pin.visibility(),
                pin.caption().map(Caption::text),
                unread);
    }

    private static void write(FriendlyByteBuf buf, PinSummary summary) {
        NetCodecs.writePinId(buf, summary.id);
        NetCodecs.writeUuid(buf, summary.authorId);
        NetCodecs.writeBoundedString(buf, summary.authorName, dev.echopins.domain.pin.PinAuthor.MAX_NAME_LENGTH);
        NetCodecs.writeAnchor(buf, summary.anchor);
        buf.writeLong(summary.createdAt);
        buf.writeVarInt(summary.durationMillis);
        buf.writeByte(summary.visibility.id());
        buf.writeBoolean(summary.caption.isPresent());
        summary.caption.ifPresent(text ->
                NetCodecs.writeBoundedString(buf, text, Caption.HARD_MAX_LENGTH));
        buf.writeBoolean(summary.unread);
    }

    private static PinSummary read(FriendlyByteBuf buf) {
        PinId id = NetCodecs.readPinId(buf);
        UUID authorId = NetCodecs.readUuid(buf);
        String authorName = NetCodecs.readBoundedString(buf, dev.echopins.domain.pin.PinAuthor.MAX_NAME_LENGTH);
        WorldAnchor anchor = NetCodecs.readAnchor(buf);
        long createdAt = buf.readLong();
        int durationMillis = buf.readVarInt();
        Visibility visibility = Visibility.byId(buf.readByte());
        Optional<String> caption = buf.readBoolean()
                ? Optional.of(NetCodecs.readBoundedString(buf, Caption.HARD_MAX_LENGTH))
                : Optional.empty();
        boolean unread = buf.readBoolean();

        if (durationMillis < 0) {
            throw new NetCodecs.MalformedPayloadException("Negative duration");
        }
        return new PinSummary(id, authorId, authorName, anchor, createdAt,
                durationMillis, visibility, caption, unread);
    }
}
