package dev.echopins.application.pin;

import dev.echopins.application.ServerLimits;
import dev.echopins.application.recording.RecordingService;
import dev.echopins.domain.anchor.WorldAnchor;
import dev.echopins.domain.audio.AudioStore;
import dev.echopins.domain.error.EchoPinError;
import dev.echopins.domain.error.EchoPinException;
import dev.echopins.domain.event.DomainEventBus;
import dev.echopins.domain.event.DomainEvents;
import dev.echopins.domain.expiry.ExpiryChoice;
import dev.echopins.domain.expiry.ExpiryPolicy;
import dev.echopins.domain.limits.CooldownRateLimiter;
import dev.echopins.domain.pin.Caption;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinAuthor;
import dev.echopins.domain.pin.PinId;
import dev.echopins.domain.repository.PinRepository;
import dev.echopins.domain.repository.ReadStateRepository;
import dev.echopins.domain.visibility.AccessPolicy;
import dev.echopins.domain.visibility.Visibility;
import dev.echopins.infrastructure.concurrent.EchoPinsExecutors;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Creating, deleting and expiring pins.
 *
 * <p>Every limit is applied here rather than in the packet handlers, so the command path and the
 * network path cannot drift apart on what is allowed.
 */
public final class PinService {

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins/Pins");

    private final ServerLimits limits;
    private final PinRepository pins;
    private final ReadStateRepository readState;
    private final AudioStore audioStore;
    private final AccessPolicy accessPolicy;
    private final ExpiryPolicy expiryPolicy;
    private final DomainEventBus events;
    private final java.util.function.LongSupplier clock;
    private final CooldownRateLimiter<UUID> createCooldown;

    public PinService(ServerLimits limits, PinRepository pins, ReadStateRepository readState,
                      AudioStore audioStore, AccessPolicy accessPolicy, ExpiryPolicy expiryPolicy,
                      DomainEventBus events, java.util.function.LongSupplier clock) {
        this.limits = limits;
        this.pins = pins;
        this.readState = readState;
        this.audioStore = audioStore;
        this.accessPolicy = accessPolicy;
        this.expiryPolicy = expiryPolicy;
        this.events = events;
        this.clock = clock;
        this.createCooldown = new CooldownRateLimiter<>(() -> limits.createCooldownSeconds() * 1000);
    }

    /**
     * Turns a confirmed recording into a pin.
     *
     * @throws EchoPinException if any limit or validation rule refuses the request
     */
    public EchoPin createFromPending(ServerPlayer player,
                                     RecordingService.PendingRecording pending,
                                     Visibility visibility,
                                     Set<UUID> requestedRecipients,
                                     Optional<String> rawCaption,
                                     ExpiryChoice expiryChoice,
                                     boolean operator) {
        UUID author = player.getUUID();
        boolean bypass = operator && limits.operatorBypassLimits();
        long now = clock.getAsLong();

        if (!bypass && !createCooldown.tryAcquire(author, now)) {
            long wait = createCooldown.millisUntilAvailable(author, now);
            // The message has a placeholder for the remaining seconds, so the value must travel
            // with the error rather than being logged and dropped.
            throw new EchoPinException(EchoPinError.CREATE_COOLDOWN, wait,
                    "Create cooldown active for " + author + ", " + wait + "ms remaining");
        }
        if (!bypass && pins.countByAuthor(author) >= limits.maxPinsPerPlayer()) {
            throw new EchoPinException(EchoPinError.TOO_MANY_PINS_OWNED,
                    author + " owns the maximum of " + limits.maxPinsPerPlayer() + " pins");
        }
        if (!bypass && pins.totalCount() >= limits.maxTotalPins()) {
            throw new EchoPinException(EchoPinError.SERVER_PIN_LIMIT,
                    "Server pin limit of " + limits.maxTotalPins() + " reached");
        }

        WorldAnchor anchor = pending.anchor();
        if (!bypass) {
            int nearby = pins.findNearby(anchor.dimension(), anchor.renderPos(),
                    limits.interactionRadius()).size();
            if (nearby >= limits.maxPinsNearby()) {
                throw new EchoPinException(EchoPinError.TOO_MANY_PINS_NEARBY,
                        nearby + " pins already within the interaction radius");
            }
        }

        Set<UUID> recipients = normaliseRecipients(visibility, requestedRecipients, author, bypass);
        Optional<Caption> caption = Caption.ofNullable(rawCaption.orElse(null), limits.maxCaptionLength());
        long expiresAt = expiryPolicy.resolveExpiry(expiryChoice, now);

        EchoPin pin = new EchoPin(
                PinId.random(),
                new PinAuthor(author, player.getGameProfile().getName()),
                anchor,
                now,
                visibility,
                recipients,
                caption,
                pending.audio(),
                expiresAt);

        pins.save(pin);
        // The author has obviously heard their own message.
        readState.markRead(author, pin.id());
        events.publish(new DomainEvents.PinCreated(pin));
        LOGGER.debug("Created pin {} for {} ({} frames, {} bytes)",
                pin.id(), author, pin.audio().frameCount(), pin.audio().byteSize());
        return pin;
    }

    private Set<UUID> normaliseRecipients(Visibility visibility, Set<UUID> requested,
                                          UUID author, boolean bypass) {
        if (visibility != Visibility.PRIVATE) {
            return Set.of();
        }
        Set<UUID> recipients = new LinkedHashSet<>(requested);
        // The author always has access through ownership, so listing them is redundant and would
        // waste one of the recipient slots.
        recipients.remove(author);
        if (!bypass && recipients.size() > limits.maxPrivateRecipients()) {
            throw new EchoPinException(EchoPinError.TOO_MANY_RECIPIENTS,
                    recipients.size() + " recipients requested, limit is "
                            + limits.maxPrivateRecipients());
        }
        return recipients;
    }

    /**
     * Deletes a pin on a player's request.
     *
     * @throws EchoPinException if the pin is gone or the player may not delete it
     */
    public EchoPin delete(UUID actor, PinId pinId, boolean operator) {
        EchoPin pin = pins.find(pinId).orElseThrow(() -> new EchoPinException(
                EchoPinError.PIN_NOT_FOUND, "Pin " + pinId + " does not exist"));
        if (!accessPolicy.canDelete(pin, actor, operator)) {
            throw new EchoPinException(EchoPinError.NO_ACCESS,
                    actor + " may not delete pin " + pinId);
        }
        removeInternal(pin);
        return pin;
    }

    /** Deletes without an ACL check. For expiry and admin cleanup only. */
    public void deleteUnchecked(EchoPin pin) {
        removeInternal(pin);
    }

    /**
     * Removes a pin, its read marks and its audio.
     *
     * <p>Metadata goes first. If the process dies between the two steps the audio file is left
     * behind as an orphan, which the sweep collects; the reverse order could leave a pin whose
     * audio no longer exists, which the player would experience as a broken message.
     */
    private void removeInternal(EchoPin pin) {
        Optional<EchoPin> removed = pins.remove(pin.id());
        if (removed.isEmpty()) {
            // Already gone. Deletion is idempotent.
            return;
        }
        readState.forgetPin(pin.id());
        events.publish(new DomainEvents.PinRemoved(
                pin.id(), pin.authorUuid(), pin.anchor().dimension(),
                pin.anchor().renderPos(), pin.audio().audioId()));

        UUID audioId = pin.audio().audioId();
        EchoPinsExecutors executors = EchoPinsExecutors.current();
        if (executors == null || !executors.submitIo(() -> audioStore.delete(audioId))) {
            audioStore.delete(audioId);
        }
        LOGGER.debug("Deleted pin {}", pin.id());
    }

    /**
     * Removes a bounded batch of expired pins.
     *
     * @return how many were removed
     */
    public int removeExpiredBatch() {
        if (!limits.expiredPinCleanup()) {
            return 0;
        }
        List<EchoPin> expired = pins.findExpired(clock.getAsLong(), limits.expiredPinCleanupBatch());
        for (EchoPin pin : expired) {
            removeInternal(pin);
        }
        if (!expired.isEmpty()) {
            LOGGER.info("Removed {} expired EchoPin(s)", expired.size());
        }
        return expired.size();
    }

    /** Refreshes the stored display name for an author who has just been seen. */
    public void refreshAuthorName(UUID author, String currentName) {
        for (EchoPin pin : pins.findByAuthor(author)) {
            EchoPin updated = pin.withAuthorName(currentName);
            if (updated != pin) {
                pins.save(updated);
                events.publish(new DomainEvents.PinUpdated(updated));
            }
        }
    }

    public PinRepository repository() {
        return pins;
    }

    public AccessPolicy accessPolicy() {
        return accessPolicy;
    }

    public void onPlayerDisconnected(UUID player) {
        createCooldown.forget(player);
    }
}
