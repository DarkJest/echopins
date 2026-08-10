package dev.echopins.infrastructure.network;

import dev.echopins.domain.anchor.BlockAnchor;
import dev.echopins.domain.anchor.BlockFace;
import dev.echopins.domain.anchor.DimensionId;
import dev.echopins.domain.anchor.PositionAnchor;
import dev.echopins.domain.anchor.WorldAnchor;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.pin.PinId;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Bounded read helpers shared by every payload.
 *
 * <p>Each reader validates before it allocates. A payload arrives from a client that may be
 * hostile, so a length prefix is never trusted: collection sizes are checked against an explicit
 * cap and strings are read with a maximum length, which means a malformed packet costs a
 * rejected connection rather than server memory.
 */
public final class NetCodecs {

    private NetCodecs() {
    }

    /** Raised on any out-of-contract value; the network layer turns this into a disconnect. */
    public static final class MalformedPayloadException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MalformedPayloadException(String message) {
            super(message);
        }
    }

    public static void writeUuid(FriendlyByteBuf buf, UUID value) {
        buf.writeUUID(value);
    }

    public static UUID readUuid(FriendlyByteBuf buf) {
        return buf.readUUID();
    }

    public static void writePinId(FriendlyByteBuf buf, PinId id) {
        buf.writeUUID(id.value());
    }

    public static PinId readPinId(FriendlyByteBuf buf) {
        return PinId.of(buf.readUUID());
    }

    public static void writeBoundedString(FriendlyByteBuf buf, String value, int maxLength) {
        buf.writeUtf(value, maxLength);
    }

    public static String readBoundedString(FriendlyByteBuf buf, int maxLength) {
        return buf.readUtf(maxLength);
    }

    public static <T> void writeBoundedList(FriendlyByteBuf buf, List<T> values, int maxSize,
                                            java.util.function.BiConsumer<FriendlyByteBuf, T> writer) {
        if (values.size() > maxSize) {
            throw new MalformedPayloadException("Refusing to write " + values.size()
                    + " elements, limit is " + maxSize);
        }
        buf.writeVarInt(values.size());
        for (T value : values) {
            writer.accept(buf, value);
        }
    }

    public static <T> List<T> readBoundedList(FriendlyByteBuf buf, int maxSize,
                                              Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > maxSize) {
            throw new MalformedPayloadException("Collection size " + size + " exceeds limit " + maxSize);
        }
        // readableBytes() bounds the count by what is physically present, so a size that is
        // within the cap but still a lie cannot pre-allocate a large list.
        if (size > buf.readableBytes() + 1) {
            throw new MalformedPayloadException("Declared size " + size + " exceeds remaining bytes");
        }
        List<T> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(reader.apply(buf));
        }
        return values;
    }

    public static Set<UUID> readBoundedUuidSet(FriendlyByteBuf buf, int maxSize) {
        return new LinkedHashSet<>(readBoundedList(buf, maxSize, NetCodecs::readUuid));
    }

    public static void writeUuidSet(FriendlyByteBuf buf, Set<UUID> values, int maxSize) {
        writeBoundedList(buf, List.copyOf(values), maxSize, NetCodecs::writeUuid);
    }

    public static void writeAnchor(FriendlyByteBuf buf, WorldAnchor anchor) {
        buf.writeByte(anchor.kind().id());
        buf.writeUtf(anchor.dimension().toString(), DimensionId.MAX_LENGTH);
        if (anchor instanceof BlockAnchor block) {
            buf.writeVarInt(block.blockX());
            buf.writeVarInt(block.blockY());
            buf.writeVarInt(block.blockZ());
            buf.writeByte(block.face().id());
        } else if (anchor instanceof PositionAnchor position) {
            buf.writeDouble(position.position().x());
            buf.writeDouble(position.position().y());
            buf.writeDouble(position.position().z());
        } else {
            throw new IllegalArgumentException("Unsupported anchor type: " + anchor.getClass());
        }
    }

    /**
     * Reads an anchor, rejecting anything the domain would refuse. The domain's own constructors
     * do the range checking, so there is exactly one definition of a valid anchor.
     */
    public static WorldAnchor readAnchor(FriendlyByteBuf buf) {
        int kindId = buf.readByte();
        String rawDimension = buf.readUtf(DimensionId.MAX_LENGTH);
        try {
            WorldAnchor.Kind kind = WorldAnchor.Kind.byId(kindId);
            DimensionId dimension = DimensionId.parse(rawDimension);
            return switch (kind) {
                case BLOCK -> new BlockAnchor(dimension,
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        BlockFace.byId(buf.readByte()));
                case POSITION -> new PositionAnchor(dimension,
                        new WorldPos(buf.readDouble(), buf.readDouble(), buf.readDouble()));
            };
        } catch (IllegalArgumentException e) {
            throw new MalformedPayloadException("Invalid anchor: " + e.getMessage());
        }
    }
}
