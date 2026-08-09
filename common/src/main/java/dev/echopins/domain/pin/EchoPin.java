package dev.echopins.domain.pin;

import dev.echopins.domain.anchor.WorldAnchor;
import dev.echopins.domain.audio.AudioRef;
import dev.echopins.domain.visibility.Visibility;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A voice message anchored to a place in the world.
 *
 * <p>Immutable. All timestamps are epoch milliseconds; the codebase uses {@code long} epoch
 * millis everywhere rather than mixing in {@code Instant}, because these values are written to
 * NBT and network payloads on every sync.
 *
 * @param id          identity
 * @param author      creator; only {@code author.uuid()} is authoritative
 * @param anchor      where the pin lives
 * @param createdAt   creation time, epoch millis
 * @param visibility  who may see and play it
 * @param recipients  extra allowed players when {@link Visibility#PRIVATE}; always empty for
 *                    {@link Visibility#PUBLIC}
 * @param caption     optional short text label
 * @param audio       handle to the stored recording
 * @param expiresAt   expiry time in epoch millis, or {@link #NEVER_EXPIRES} for a permanent pin
 */
public record EchoPin(
        PinId id,
        PinAuthor author,
        WorldAnchor anchor,
        long createdAt,
        Visibility visibility,
        Set<UUID> recipients,
        Optional<Caption> caption,
        AudioRef audio,
        long expiresAt) {

    /** Sentinel {@link #expiresAt} value meaning the pin never expires on its own. */
    public static final long NEVER_EXPIRES = 0L;

    public EchoPin {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(recipients, "recipients");
        Objects.requireNonNull(caption, "caption");
        Objects.requireNonNull(audio, "audio");
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt must not be negative");
        }
        if (expiresAt < 0) {
            throw new IllegalArgumentException("expiresAt must not be negative");
        }
        // A public pin with a recipient list would be a confusing half-state that every ACL
        // check would then have to reason about, so it is normalised away here.
        recipients = visibility == Visibility.PUBLIC
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(recipients));
    }

    public UUID authorUuid() {
        return author.uuid();
    }

    public boolean isPermanent() {
        return expiresAt == NEVER_EXPIRES;
    }

    public boolean isExpiredAt(long nowEpochMillis) {
        return !isPermanent() && nowEpochMillis >= expiresAt;
    }

    public int durationMillis() {
        return audio.durationMillis();
    }

    public EchoPin withAuthorName(String newName) {
        if (author.lastKnownName().equals(newName)) {
            return this;
        }
        return new EchoPin(id, author.withName(newName), anchor, createdAt,
                visibility, recipients, caption, audio, expiresAt);
    }

    public EchoPin withCaption(Optional<Caption> newCaption) {
        return new EchoPin(id, author, anchor, createdAt,
                visibility, recipients, newCaption, audio, expiresAt);
    }

    public EchoPin withExpiry(long newExpiresAt) {
        return new EchoPin(id, author, anchor, createdAt,
                visibility, recipients, caption, audio, newExpiresAt);
    }
}
