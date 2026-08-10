package dev.echopins.application.playback;

import dev.echopins.application.ServerLimits;
import dev.echopins.application.pin.AnchorResolver;
import dev.echopins.application.voice.VoiceBackend;
import dev.echopins.domain.audio.AudioStore;
import dev.echopins.domain.audio.VoiceRecording;
import dev.echopins.domain.error.EchoPinError;
import dev.echopins.domain.error.EchoPinException;
import dev.echopins.domain.limits.CooldownRateLimiter;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinId;
import dev.echopins.domain.repository.PinRepository;
import dev.echopins.domain.repository.ReadStateRepository;
import dev.echopins.domain.visibility.AccessPolicy;
import dev.echopins.infrastructure.audio.epv.EpvFormatException;
import dev.echopins.infrastructure.concurrent.EchoPinsExecutors;
import dev.echopins.infrastructure.network.EchoPinsNetwork;
import dev.echopins.infrastructure.network.payload.ClientboundPayloads;
import dev.echopins.infrastructure.network.payload.ClientboundPayloads.PlaybackPhase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plays stored recordings back into the world.
 *
 * <p>Access is re-checked at the moment of playback, not merely at discovery: a pin's visibility
 * or a player's operator status can change between seeing a marker and pressing play. The check
 * is then applied a second time by the audio channel's own per-listener filter, so a pin that
 * becomes private mid-playback stops being audible to people who lost access.
 */
public final class PlaybackService {

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins/Playback");

    private record ActivePlayback(PinId pin, UUID listener, VoiceBackend.VoicePlayback handle) {
    }

    private final ServerLimits limits;
    private final PinRepository pins;
    private final ReadStateRepository readState;
    private final AudioStore audioStore;
    private final AccessPolicy accessPolicy;
    private final AnchorResolver anchorResolver;
    private final VoiceBackend voice;
    private final java.util.function.LongSupplier clock;

    private final Map<UUID, ActivePlayback> byChannel = new ConcurrentHashMap<>();

    /**
     * Requests accepted but not yet playing, counted per listener.
     *
     * <p>Audio is loaded off-thread, so between accepting a request and registering the channel
     * there is a window in which {@code byChannel} still shows nothing. Without counting these,
     * a player could hold the play key and queue an unbounded number of playbacks: every request
     * saw zero active and passed the limit.
     */
    private final Map<UUID, Set<UUID>> starting = new ConcurrentHashMap<>();

    private final CooldownRateLimiter<UUID> cooldown;

    public PlaybackService(ServerLimits limits, PinRepository pins, ReadStateRepository readState,
                           AudioStore audioStore, AccessPolicy accessPolicy,
                           AnchorResolver anchorResolver, VoiceBackend voice,
                           java.util.function.LongSupplier clock) {
        this.limits = limits;
        this.pins = pins;
        this.readState = readState;
        this.audioStore = audioStore;
        this.accessPolicy = accessPolicy;
        this.anchorResolver = anchorResolver;
        this.voice = voice;
        this.clock = clock;
        this.cooldown = new CooldownRateLimiter<>(limits::playbackCooldownMillis);
    }

    public int activePlaybackCount() {
        return byChannel.size();
    }

    /**
     * Starts playback of a pin for a player.
     *
     * @throws EchoPinException with the reason if playback is refused
     */
    public void requestPlayback(ServerPlayer player, PinId pinId, boolean operator) {
        UUID listener = player.getUUID();

        EchoPin pin = pins.find(pinId).orElseThrow(() -> new EchoPinException(
                EchoPinError.PIN_NOT_FOUND, "Pin " + pinId + " does not exist"));

        if (!accessPolicy.canPlay(pin, listener, operator)) {
            // Deliberately the same error a missing pin would produce would leak less, but a
            // player who can see a marker and is then told "no access" is the clearer outcome,
            // and discovery already prevents them from seeing pins they cannot play.
            throw new EchoPinException(EchoPinError.NO_ACCESS,
                    listener + " may not play pin " + pinId);
        }
        if (!anchorResolver.isWithinInteractionRange(player, pin.anchor())) {
            // Not CANNOT_CREATE_HERE: that message talks about placing a pin, which made a
            // failed playback read as "you can't put an EchoPin here".
            throw new EchoPinException(EchoPinError.TOO_FAR_AWAY,
                    listener + " is out of range of pin " + pinId);
        }
        if (!voice.isAvailable() || !voice.isPlayerConnected(listener)) {
            throw new EchoPinException(EchoPinError.VOICE_CHAT_NOT_CONNECTED,
                    "Voice chat is unavailable for " + listener);
        }
        if (countFor(listener) >= limits.maxConcurrentPlaybacksPerPlayer()) {
            throw new EchoPinException(EchoPinError.TOO_MANY_PLAYBACKS,
                    listener + " already has the maximum number of playbacks running");
        }
        long now = clock.getAsLong();
        if (!cooldown.tryAcquire(listener, now)) {
            throw new EchoPinException(EchoPinError.PLAYBACK_COOLDOWN,
                    "Playback cooldown active for " + listener);
        }

        MinecraftServer server = player.getServer();
        EchoPinsExecutors executors = EchoPinsExecutors.current();
        if (server == null || executors == null) {
            throw new EchoPinException(EchoPinError.INTERNAL_ERROR, "Server is shutting down");
        }

        ServerLevel level = levelFor(server, pin);
        if (level == null) {
            throw new EchoPinException(EchoPinError.PIN_NOT_FOUND,
                    "Dimension " + pin.anchor().dimension() + " is not loaded");
        }

        // Claim the slot before going off-thread, so concurrent requests see it.
        UUID requestId = UUID.randomUUID();
        reserve(listener, requestId);

        // Decoding happens off the server thread; only the start of playback comes back to it.
        boolean accepted = executors.submitIo(() -> {
            Optional<VoiceRecording> loaded;
            try {
                loaded = audioStore.load(pin.audio().audioId());
            } catch (EpvFormatException e) {
                LOGGER.warn("Audio for pin {} is damaged: {}", pinId, e.getMessage());
                server.execute(() -> failStarting(
                        server, listener, requestId, EchoPinError.AUDIO_DAMAGED));
                return;
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Could not read audio for pin {}", pinId, e);
                server.execute(() -> failStarting(
                        server, listener, requestId, EchoPinError.INTERNAL_ERROR));
                return;
            }

            if (loaded.isEmpty() || loaded.get().isEmpty()) {
                LOGGER.warn("Pin {} references audio {} that is missing", pinId, pin.audio().audioId());
                server.execute(() -> failStarting(
                        server, listener, requestId, EchoPinError.AUDIO_DAMAGED));
                return;
            }

            VoiceRecording recording = loaded.get();
            server.execute(() -> startPlayback(
                    server, listener, pinId, recording, operator, requestId));
        });

        if (!accepted) {
            release(listener, requestId);
            throw new EchoPinException(EchoPinError.INTERNAL_ERROR, "IO queue is saturated");
        }
    }

    private void startPlayback(MinecraftServer server, UUID listener, PinId pinId,
                               VoiceRecording recording, boolean operator, UUID requestId) {
        // Disconnect, shutdown, or cancellation removes the exact request id. A later reconnect
        // therefore cannot accidentally revive an IO completion from the previous connection.
        if (!release(listener, requestId)) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(listener);
        if (player == null) {
            return;
        }

        // Everything that may have changed while the file was loading is checked again here.
        EchoPin pin = pins.find(pinId).orElse(null);
        if (pin == null) {
            sendError(player, EchoPinError.PIN_NOT_FOUND);
            return;
        }
        if (!accessPolicy.canPlay(pin, listener, operator)) {
            sendError(player, EchoPinError.NO_ACCESS);
            return;
        }
        if (!anchorResolver.isWithinInteractionRange(player, pin.anchor())) {
            sendError(player, EchoPinError.TOO_FAR_AWAY);
            return;
        }
        if (!voice.isAvailable() || !voice.isPlayerConnected(listener)) {
            sendError(player, EchoPinError.VOICE_CHAT_NOT_CONNECTED);
            return;
        }
        ServerLevel level = levelFor(server, pin);
        if (level == null) {
            sendError(player, EchoPinError.PIN_NOT_FOUND);
            return;
        }

        UUID channelId = UUID.randomUUID();
        AtomicBoolean completed = new AtomicBoolean();

        Optional<VoiceBackend.VoicePlayback> handle = voice.startLocationalPlayback(
                level,
                pin.anchor().renderPos(),
                (float) limits.playbackAudioDistance(),
                channelId,
                // Evaluated per listener by the voice system.
                //
                // The operator bypass applies ONLY to the player who deliberately pressed play.
                // Granting it to bystanders meant any operator standing in earshot heard every
                // private message automatically - and since a single-player host always has
                // permission level 4, private pins were effectively public there. Moderation is
                // an explicit act, not something that leaks to whoever happens to be nearby.
                candidate -> candidate.equals(listener)
                        ? accessPolicy.canPlay(pin, candidate, operator)
                        : accessPolicy.canPlay(pin, candidate, false),
                recording,
                () -> {
                    completed.set(true);
                    onPlaybackFinished(server, channelId);
                });

        if (handle.isEmpty()) {
            sendError(player, EchoPinError.INTERNAL_ERROR);
            return;
        }

        ActivePlayback active = new ActivePlayback(pin.id(), listener, handle.get());
        byChannel.put(channelId, active);
        readState.markRead(listener, pin.id());
        EchoPinsNetwork.sendTo(player, new ClientboundPayloads.PlaybackState(
                pin.id(), PlaybackPhase.STARTED, recording.durationMillis()));
        // A one-frame recording or an immediately closed voice channel can finish on the audio
        // thread before the handle is registered above. Close that race deterministically.
        if (completed.get() || handle.get().isFinished()) {
            if (byChannel.remove(channelId, active)) {
                EchoPinsNetwork.sendTo(player, new ClientboundPayloads.PlaybackState(
                        pin.id(), PlaybackPhase.FINISHED, 0));
            }
        }
        LOGGER.debug("Started playback of {} for {} on channel {}", pin.id(), listener, channelId);
    }

    /**
     * Runs on the audio thread when a channel drains or is cut short.
     *
     * <p>Tells the listener it finished. Without this the client only ever learned that playback
     * started and fell back to a local timer, so a playback that ended early - a closed channel, a
     * deleted pin - left "Playing…" on screen for the full original duration.
     */
    private void onPlaybackFinished(MinecraftServer server, UUID channelId) {
        ActivePlayback finished = byChannel.remove(channelId);
        if (finished == null) {
            return;
        }
        LOGGER.debug("Playback finished on channel {}", channelId);
        if (server == null) {
            return;
        }
        // Hop back to the server thread before touching the player list or sending anything.
        server.execute(() -> {
            ServerPlayer listener = server.getPlayerList().getPlayer(finished.listener());
            if (listener != null) {
                EchoPinsNetwork.sendTo(listener, new ClientboundPayloads.PlaybackState(
                        finished.pin(), PlaybackPhase.FINISHED, 0));
            }
        });
    }

    /** Stops a playback the player started. */
    public void stopPlayback(ServerPlayer player, PinId pinId) {
        UUID listener = player.getUUID();
        byChannel.values().stream()
                .filter(active -> active.listener().equals(listener) && active.pin().equals(pinId))
                .findFirst()
                .ifPresent(active -> {
                    active.handle().stop();
                    EchoPinsNetwork.sendTo(player,
                            new ClientboundPayloads.PlaybackState(pinId, PlaybackPhase.STOPPED, 0));
                });
    }

    /** Stops every playback of a pin. Called when a pin is deleted mid-playback. */
    public void stopPlaybacksOf(PinId pinId) {
        for (Map.Entry<UUID, ActivePlayback> entry : byChannel.entrySet()) {
            if (entry.getValue().pin().equals(pinId)) {
                entry.getValue().handle().stop();
            }
        }
    }

    /** Stops every playback a player started. Called on disconnect. */
    public void stopPlaybacksFor(UUID listener) {
        for (ActivePlayback active : byChannel.values()) {
            if (active.listener().equals(listener)) {
                active.handle().stop();
            }
        }
        cooldown.forget(listener);
        starting.remove(listener);
    }

    public void shutdown() {
        for (ActivePlayback active : byChannel.values()) {
            active.handle().stop();
        }
        byChannel.clear();
        starting.clear();
    }

    private void reserve(UUID listener, UUID requestId) {
        starting.computeIfAbsent(listener, ignored -> ConcurrentHashMap.newKeySet()).add(requestId);
    }

    private boolean release(UUID listener, UUID requestId) {
        Set<UUID> requests = starting.get(listener);
        if (requests == null || !requests.remove(requestId)) {
            return false;
        }
        if (requests.isEmpty()) {
            starting.remove(listener, requests);
        }
        return true;
    }

    private int countFor(UUID listener) {
        Set<UUID> requests = starting.get(listener);
        int count = requests == null ? 0 : requests.size();
        for (ActivePlayback active : byChannel.values()) {
            if (active.listener().equals(listener) && !active.handle().isFinished()) {
                count++;
            }
        }
        return count;
    }

    private void failStarting(MinecraftServer server, UUID listener, UUID requestId,
                              EchoPinError error) {
        if (!release(listener, requestId)) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(listener);
        if (player != null) {
            sendError(player, error);
        }
    }

    private static ServerLevel levelFor(MinecraftServer server, EchoPin pin) {
        ResourceLocation location = new ResourceLocation(
                pin.anchor().dimension().namespace(), pin.anchor().dimension().path());
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(location)) {
                return level;
            }
        }
        return null;
    }

    private static void sendError(ServerPlayer player, EchoPinError error) {
        EchoPinsNetwork.sendTo(player, ClientboundPayloads.ErrorMessage.of(error));
    }

    /** Exposed for the admin stats command. */
    public Collection<PinId> activePins() {
        return byChannel.values().stream().map(ActivePlayback::pin).toList();
    }
}
