package dev.echopins.client.state;

import dev.echopins.domain.pin.PinId;
import dev.echopins.infrastructure.network.PinSummary;
import dev.echopins.infrastructure.network.payload.ClientboundPayloads;
import dev.echopins.infrastructure.network.payload.ClientboundPayloads.RecordingPhase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the client knows about EchoPins right now.
 *
 * <p>Purely a cache of what the server has said. Nothing here is authoritative: hiding a pin from
 * this map does not hide it from the player in any security sense, because the server never sent
 * pins the player is not allowed to see in the first place.
 *
 * <p>Touched only from the client thread.
 */
public final class ClientPinState {

    public static final ClientPinState INSTANCE = new ClientPinState();

    /** Values the server enforces, mirrored so the UI can show honest limits. */
    public record Settings(double discoveryRadius, double interactionRadius,
                           int maxRecordingSeconds, int minRecordingMillis,
                           int maxCaptionLength, int maxPrivateRecipients,
                           boolean allowPermanentPins) {

        static Settings defaults() {
            return new Settings(56.0D, 6.0D, 30, 700, 96, 16, true);
        }
    }

    /** Live recording progress as reported by the server. */
    public record Recording(RecordingPhase phase, int elapsedMillis, int maxMillis,
                            boolean receivingAudio) {

        static Recording idle() {
            return new Recording(RecordingPhase.IDLE, 0, 30_000, false);
        }

        public boolean isActive() {
            return phase == RecordingPhase.RECORDING;
        }

        public boolean isAwaitingConfirmation() {
            return phase == RecordingPhase.AWAITING_CONFIRMATION;
        }
    }

    private final Map<PinId, PinSummary> pins = new LinkedHashMap<>();
    private final Map<PinId, Long> playingUntilMillis = new LinkedHashMap<>();

    private Settings settings = Settings.defaults();
    private Recording recording = Recording.idle();
    private List<ClientboundPayloads.KnownPlayer> knownPlayers = List.of();
    private ClientboundPayloads.InboxPage inboxPage;

    private ClientPinState() {
    }

    public Settings settings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public Recording recording() {
        return recording;
    }

    public void setRecording(Recording recording) {
        this.recording = recording;
    }

    public Collection<PinSummary> pins() {
        return pins.values();
    }

    public Optional<PinSummary> pin(PinId id) {
        return Optional.ofNullable(pins.get(id));
    }

    public int pinCount() {
        return pins.size();
    }

    public void replaceAll(List<PinSummary> snapshot) {
        pins.clear();
        for (PinSummary pin : snapshot) {
            pins.put(pin.id(), pin);
        }
    }

    public void applyDelta(List<PinSummary> added, List<PinId> removed) {
        for (PinId id : removed) {
            pins.remove(id);
            playingUntilMillis.remove(id);
        }
        for (PinSummary pin : added) {
            pins.put(pin.id(), pin);
        }
    }

    /** Marks a pin as playing until the given time, which drives the progress indicator. */
    public void setPlaying(PinId id, int durationMillis) {
        playingUntilMillis.put(id, System.currentTimeMillis() + durationMillis);
    }

    /** Pins currently playing, oldest first. Drives the "now playing" indicator. */
    public List<PinId> playingPins() {
        long now = System.currentTimeMillis();
        playingUntilMillis.entrySet().removeIf(e -> now >= e.getValue());
        return new ArrayList<>(playingUntilMillis.keySet());
    }

    /** Milliseconds left on a playback, or 0 if it is not playing. */
    public long remainingMillis(PinId id) {
        Long until = playingUntilMillis.get(id);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public boolean isAnythingPlaying() {
        return !playingPins().isEmpty();
    }

    public void clearPlaying(PinId id) {
        playingUntilMillis.remove(id);
    }

    public boolean isPlaying(PinId id) {
        Long until = playingUntilMillis.get(id);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            playingUntilMillis.remove(id);
            return false;
        }
        return true;
    }

    /** Marks a pin read locally so the unread badge clears without waiting for a resync. */
    public void markReadLocally(PinId id) {
        PinSummary existing = pins.get(id);
        if (existing != null && existing.unread()) {
            pins.put(id, new PinSummary(existing.id(), existing.authorId(), existing.authorName(),
                    existing.anchor(), existing.createdAt(), existing.durationMillis(),
                    existing.visibility(), existing.caption(), false));
        }
    }

    public List<ClientboundPayloads.KnownPlayer> knownPlayers() {
        return knownPlayers;
    }

    public void setKnownPlayers(List<ClientboundPayloads.KnownPlayer> players) {
        this.knownPlayers = new ArrayList<>(players);
    }

    public Optional<ClientboundPayloads.InboxPage> inboxPage() {
        return Optional.ofNullable(inboxPage);
    }

    public void setInboxPage(ClientboundPayloads.InboxPage page) {
        this.inboxPage = page;
    }

    /** Wipes everything. Called on disconnect so nothing leaks into the next server. */
    public void reset() {
        pins.clear();
        playingUntilMillis.clear();
        knownPlayers = List.of();
        inboxPage = null;
        recording = Recording.idle();
        settings = Settings.defaults();
    }
}
