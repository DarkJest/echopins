package dev.echopins.infrastructure.persistence;

import dev.echopins.domain.anchor.BlockAnchor;
import dev.echopins.domain.anchor.BlockFace;
import dev.echopins.domain.anchor.DimensionId;
import dev.echopins.domain.anchor.PositionAnchor;
import dev.echopins.domain.anchor.WorldAnchor;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.audio.AudioRef;
import dev.echopins.domain.pin.Caption;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinAuthor;
import dev.echopins.domain.pin.PinId;
import dev.echopins.domain.visibility.Visibility;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Converts pins to and from NBT.
 *
 * <p>Every pin carries its own {@code v} field. Storing the schema version per pin rather than
 * only once for the whole file means a future migration can be applied lazily and a single
 * unreadable pin can be skipped without condemning the rest of the world's data.
 *
 * <p>Decoding is total: it returns {@link Optional#empty()} for anything malformed instead of
 * throwing, because one corrupt entry must never stop a world from loading.
 */
public final class PinNbtCodec {

    static final String KEY_VERSION = "v";
    private static final String KEY_ID = "id";
    private static final String KEY_AUTHOR_ID = "author";
    private static final String KEY_AUTHOR_NAME = "authorName";
    private static final String KEY_ANCHOR_KIND = "anchorKind";
    private static final String KEY_DIMENSION = "dim";
    private static final String KEY_BLOCK_X = "bx";
    private static final String KEY_BLOCK_Y = "by";
    private static final String KEY_BLOCK_Z = "bz";
    private static final String KEY_FACE = "face";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_CREATED = "created";
    private static final String KEY_VISIBILITY = "vis";
    private static final String KEY_RECIPIENTS = "to";
    private static final String KEY_CAPTION = "caption";
    private static final String KEY_AUDIO_ID = "audioId";
    private static final String KEY_AUDIO_BYTES = "audioBytes";
    private static final String KEY_FRAMES = "frames";
    private static final String KEY_EXPIRES = "expires";

    private PinNbtCodec() {
    }

    public static CompoundTag encode(EchoPin pin, int schemaVersion) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_VERSION, schemaVersion);
        tag.putUUID(KEY_ID, pin.id().value());
        tag.putUUID(KEY_AUTHOR_ID, pin.author().uuid());
        tag.putString(KEY_AUTHOR_NAME, pin.author().lastKnownName());

        WorldAnchor anchor = pin.anchor();
        tag.putByte(KEY_ANCHOR_KIND, (byte) anchor.kind().id());
        tag.putString(KEY_DIMENSION, anchor.dimension().toString());
        switch (anchor) {
            case BlockAnchor block -> {
                tag.putInt(KEY_BLOCK_X, block.blockX());
                tag.putInt(KEY_BLOCK_Y, block.blockY());
                tag.putInt(KEY_BLOCK_Z, block.blockZ());
                tag.putByte(KEY_FACE, (byte) block.face().id());
            }
            case PositionAnchor position -> {
                tag.putDouble(KEY_X, position.position().x());
                tag.putDouble(KEY_Y, position.position().y());
                tag.putDouble(KEY_Z, position.position().z());
            }
        }

        tag.putLong(KEY_CREATED, pin.createdAt());
        tag.putByte(KEY_VISIBILITY, (byte) pin.visibility().id());

        if (!pin.recipients().isEmpty()) {
            ListTag recipients = new ListTag();
            for (UUID recipient : pin.recipients()) {
                recipients.add(NbtUtils.createUUID(recipient));
            }
            tag.put(KEY_RECIPIENTS, recipients);
        }

        pin.caption().ifPresent(caption -> tag.putString(KEY_CAPTION, caption.text()));

        tag.putUUID(KEY_AUDIO_ID, pin.audio().audioId());
        tag.putLong(KEY_AUDIO_BYTES, pin.audio().byteSize());
        tag.putInt(KEY_FRAMES, pin.audio().frameCount());
        tag.putLong(KEY_EXPIRES, pin.expiresAt());
        return tag;
    }

    /**
     * Decodes a pin that has already been migrated to the current schema.
     *
     * @return empty if any field is missing or out of range
     */
    public static Optional<EchoPin> decode(CompoundTag tag) {
        try {
            if (!tag.hasUUID(KEY_ID) || !tag.hasUUID(KEY_AUTHOR_ID) || !tag.hasUUID(KEY_AUDIO_ID)) {
                return Optional.empty();
            }
            PinId id = PinId.of(tag.getUUID(KEY_ID));
            PinAuthor author = new PinAuthor(tag.getUUID(KEY_AUTHOR_ID), tag.getString(KEY_AUTHOR_NAME));

            Optional<WorldAnchor> anchor = decodeAnchor(tag);
            if (anchor.isEmpty()) {
                return Optional.empty();
            }

            long created = tag.getLong(KEY_CREATED);
            Visibility visibility = Visibility.byId(tag.getByte(KEY_VISIBILITY));

            Set<UUID> recipients = new LinkedHashSet<>();
            if (tag.contains(KEY_RECIPIENTS, Tag.TAG_LIST)) {
                ListTag list = tag.getList(KEY_RECIPIENTS, Tag.TAG_INT_ARRAY);
                for (Tag element : list) {
                    recipients.add(NbtUtils.loadUUID(element));
                }
            }

            Optional<Caption> caption = tag.contains(KEY_CAPTION, Tag.TAG_STRING)
                    ? Caption.ofNullable(tag.getString(KEY_CAPTION), Caption.HARD_MAX_LENGTH)
                    : Optional.empty();

            AudioRef audio = new AudioRef(
                    tag.getUUID(KEY_AUDIO_ID),
                    tag.getLong(KEY_AUDIO_BYTES),
                    tag.getInt(KEY_FRAMES));

            long expires = tag.getLong(KEY_EXPIRES);

            return Optional.of(new EchoPin(id, author, anchor.get(), created,
                    visibility, recipients, caption, audio, expires));
        } catch (RuntimeException e) {
            // Any malformed value - an out-of-range coordinate, a bad dimension id, a negative
            // frame count - lands here and the pin is skipped rather than taking the load down.
            return Optional.empty();
        }
    }

    private static Optional<WorldAnchor> decodeAnchor(CompoundTag tag) {
        DimensionId dimension;
        try {
            dimension = DimensionId.parse(tag.getString(KEY_DIMENSION));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        WorldAnchor.Kind kind;
        try {
            kind = WorldAnchor.Kind.byId(tag.getByte(KEY_ANCHOR_KIND));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return switch (kind) {
            case BLOCK -> Optional.of(new BlockAnchor(
                    dimension,
                    tag.getInt(KEY_BLOCK_X),
                    tag.getInt(KEY_BLOCK_Y),
                    tag.getInt(KEY_BLOCK_Z),
                    BlockFace.byId(tag.getByte(KEY_FACE))));
            case POSITION -> Optional.of(new PositionAnchor(dimension, new WorldPos(
                    tag.getDouble(KEY_X),
                    tag.getDouble(KEY_Y),
                    tag.getDouble(KEY_Z))));
        };
    }

    /** Reads the schema version a pin tag was written at, defaulting to 1 for the first release. */
    public static int schemaVersionOf(CompoundTag tag) {
        return tag.contains(KEY_VERSION, Tag.TAG_INT) ? tag.getInt(KEY_VERSION) : 1;
    }
}
