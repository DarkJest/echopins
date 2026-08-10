package dev.echopins.server;

import dev.echopins.application.ServerLimits;
import dev.echopins.application.pin.AnchorResolver;
import dev.echopins.application.pin.PinService;
import dev.echopins.application.playback.PlaybackService;
import dev.echopins.application.recording.RecordingService;
import dev.echopins.application.sync.PinSyncService;
import dev.echopins.application.voice.VoiceBackend;
import dev.echopins.domain.audio.AudioStore;
import dev.echopins.domain.error.EchoPinError;
import dev.echopins.domain.error.EchoPinException;
import dev.echopins.domain.event.DomainEventBus;
import dev.echopins.domain.expiry.ConfiguredExpiryPolicy;
import dev.echopins.domain.limits.TokenBucketRateLimiter;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinId;
import dev.echopins.domain.repository.PinRepository;
import dev.echopins.domain.repository.ReadStateRepository;
import dev.echopins.domain.visibility.DefaultAccessPolicy;
import dev.echopins.domain.visibility.Visibility;
import dev.echopins.infrastructure.audio.FileAudioStore;
import dev.echopins.infrastructure.concurrent.EchoPinsExecutors;
import dev.echopins.infrastructure.network.EchoPinsNetwork;
import dev.echopins.infrastructure.network.PinSummary;
import dev.echopins.infrastructure.network.payload.ClientboundPayloads;
import dev.echopins.infrastructure.network.payload.ServerboundPayloads;
import dev.echopins.infrastructure.persistence.EchoPinsSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The server-side composition root.
 *
 * <p>Everything is built here when a world loads and torn down when it unloads, so no state
 * survives from one world into the next - the bug class where a single-player session leaks pins
 * into the next world you open.
 *
 * <p>Also the single implementation of {@link EchoPinsNetwork.ServerRequestHandler}. Each handler
 * follows the same shape: rate-limit, delegate to a service, translate any
 * {@link EchoPinException} into a localized error for the player and a technical line for the
 * log.
 */
public final class EchoPinsServer implements EchoPinsNetwork.ServerRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins/Server");

    /** How many pins one inbox page holds. Must match the row count the inbox screen draws. */
    public static final int INBOX_PAGE_SIZE = 6;

    private static volatile EchoPinsServer instance;

    private final MinecraftServer server;
    private final ServerLimits limits;
    private final EchoPinsSavedData savedData;
    private final AudioStore audioStore;
    private final VoiceBackend voice;
    private final DomainEventBus events;

    private final AnchorResolver anchorResolver;
    private final PinService pinService;
    private final RecordingService recordingService;
    private final PlaybackService playbackService;
    private final PinSyncService syncService;
    private final TokenBucketRateLimiter<UUID> requestLimiter;

    private long tickCounter;
    private long lastExpirySweepMillis;
    private long lastOrphanSweepMillis;

    private EchoPinsServer(MinecraftServer server, ServerLimits limits, VoiceBackend voice,
                           AudioStore audioStore) {
        this.server = server;
        this.limits = limits;
        this.voice = voice;
        this.audioStore = audioStore;
        this.savedData = EchoPinsSavedData.get(server);
        this.events = new DomainEventBus((event, error) ->
                LOGGER.error("An EchoPins event listener failed handling {}", event, error));

        this.anchorResolver = new AnchorResolver(limits);
        this.pinService = new PinService(limits, savedData.pins(), savedData.readState(),
                audioStore, DefaultAccessPolicy.INSTANCE,
                new ConfiguredExpiryPolicy(limits::defaultExpiryHours, limits::allowPermanentPins),
                events, System::currentTimeMillis);
        this.recordingService = new RecordingService(limits, audioStore, anchorResolver,
                System::currentTimeMillis);
        this.playbackService = new PlaybackService(limits, savedData.pins(), savedData.readState(),
                audioStore, DefaultAccessPolicy.INSTANCE, anchorResolver, voice,
                System::currentTimeMillis);
        this.syncService = new PinSyncService(limits, savedData.pins(), savedData.readState(),
                DefaultAccessPolicy.INSTANCE, events);
        this.requestLimiter = new TokenBucketRateLimiter<>(
                limits::requestBurstCapacity, limits::requestRefillPerSecond);
    }

    /** Builds and installs the server-side stack. */
    public static void start(MinecraftServer server, ServerLimits limits, VoiceBackend voice) {
        EchoPinsExecutors.start();

        Path audioRoot = server.getWorldPath(LevelResource.ROOT)
                .resolve("echopins")
                .resolve("audio");
        AudioStore audioStore;
        try {
            audioStore = new FileAudioStore(audioRoot);
        } catch (IOException e) {
            LOGGER.error("Could not open the EchoPins audio store at {}. EchoPins is disabled "
                    + "for this session.", audioRoot, e);
            EchoPinsExecutors.stop();
            return;
        }

        EchoPinsServer created = new EchoPinsServer(server, limits, voice, audioStore);
        instance = created;
        voice.setMicrophoneCapture(created.recordingService);
        // Runs on the voice system's thread, so hop to the server thread before touching state.
        voice.setVoiceDisconnectListener(uuid -> server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && created.recordingService.isRecording(uuid)) {
                created.recordingService.cancel(player);
                sendError(player, EchoPinError.VOICE_CHAT_NOT_CONNECTED);
            } else {
                created.recordingService.endFor(uuid);
            }
        }));
        EchoPinsNetwork.installServerHandler(created);
        LOGGER.info("EchoPins ready: {} pin(s) loaded, {} bytes of audio",
                created.savedData.pins().totalCount(), audioStore.totalBytes());
    }

    /** Tears everything down. Safe to call when never started. */
    public static void stop() {
        EchoPinsServer current = instance;
        instance = null;
        EchoPinsNetwork.installServerHandler(null);
        if (current != null) {
            current.recordingService.shutdown();
            current.playbackService.shutdown();
            current.syncService.shutdown();
            current.events.clear();
            current.voice.shutdown();
        }
        EchoPinsExecutors.stop();
        LOGGER.info("EchoPins stopped");
    }

    public static Optional<EchoPinsServer> current() {
        return Optional.ofNullable(instance);
    }

    public PinService pins() {
        return pinService;
    }

    public RecordingService recording() {
        return recordingService;
    }

    public PlaybackService playback() {
        return playbackService;
    }

    public PinSyncService sync() {
        return syncService;
    }

    public AudioStore audioStore() {
        return audioStore;
    }

    public PinRepository repository() {
        return savedData.pins();
    }

    public ReadStateRepository readState() {
        return savedData.readState();
    }

    public ServerLimits limits() {
        return limits;
    }

    public MinecraftServer server() {
        return server;
    }

    // ------------------------------------------------------------------ lifecycle

    public void onPlayerJoined(ServerPlayer player) {
        if (!limits.enabled()) {
            return;
        }
        // Names change; the pins keep working because access is by UUID, but the label should
        // still show what the player is called now.
        pinService.refreshAuthorName(player.getUUID(), player.getGameProfile().getName());
        syncService.onPlayerJoined(player);
    }

    public void onPlayerLeft(ServerPlayer player) {
        UUID uuid = player.getUUID();
        recordingService.endFor(uuid);
        playbackService.stopPlaybacksFor(uuid);
        pinService.onPlayerDisconnected(uuid);
        syncService.onPlayerLeft(uuid);
        requestLimiter.forget(uuid);
    }

    /** A dimension change ends any recording: the anchor would no longer be where the player is. */
    public void onDimensionChanged(ServerPlayer player) {
        if (recordingService.hasRecordingState(player.getUUID())) {
            recordingService.cancel(player);
            sendError(player, EchoPinError.CANNOT_CREATE_HERE);
        }
        syncService.onDimensionChanged(player);
    }

    /** Death ends a recording too, since the player is about to be somewhere else entirely. */
    public void onPlayerDied(ServerPlayer player) {
        if (recordingService.hasRecordingState(player.getUUID())) {
            recordingService.cancel(player);
        }
    }

    public void tick() {
        if (!limits.enabled()) {
            return;
        }
        tickCounter++;
        recordingService.tick(server);
        syncService.tick(server, tickCounter);

        long now = System.currentTimeMillis();
        if (now - lastExpirySweepMillis >= limits.expiredPinCleanupIntervalSeconds() * 1000L) {
            lastExpirySweepMillis = now;
            pinService.removeExpiredBatch();
        }
        if (limits.orphanCleanup()
                && now - lastOrphanSweepMillis >= limits.orphanCleanupIntervalMinutes() * 60_000L) {
            lastOrphanSweepMillis = now;
            sweepOrphanAudio();
        }
        if (tickCounter % 6_000 == 0) {
            // Every five minutes, drop limiter state for players who have been idle for an hour.
            requestLimiter.pruneIdle(now, 3_600_000L);
        }
    }

    /**
     * Deletes audio files no pin references.
     *
     * <p>The set of referenced ids is captured on the server thread and the directory walk
     * happens on the IO pool, so the sweep never blocks a tick. A file created after the snapshot
     * is taken is simply left for the next sweep rather than being deleted out from under a pin
     * that is mid-creation.
     */
    public void sweepOrphanAudio() {
        Set<UUID> referenced = new java.util.HashSet<>();
        for (EchoPin pin : savedData.pins().all()) {
            referenced.add(pin.audio().audioId());
        }
        // Audio for recordings that are stored but not yet confirmed is referenced too, even
        // though no pin points at it yet.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            recordingService.pendingFor(player.getUUID())
                    .ifPresent(pending -> referenced.add(pending.audio().audioId()));
        }

        EchoPinsExecutors executors = EchoPinsExecutors.current();
        if (executors == null) {
            return;
        }
        executors.submitIo(() -> {
            try {
                int removed = 0;
                for (UUID audioId : audioStore.listAudioIds()) {
                    if (!referenced.contains(audioId) && audioStore.delete(audioId)) {
                        removed++;
                    }
                }
                if (removed > 0) {
                    LOGGER.info("Orphan sweep removed {} unreferenced audio file(s)", removed);
                }
            } catch (IOException e) {
                LOGGER.warn("Orphan audio sweep failed", e);
            }
        });
    }

    // ------------------------------------------------------------------ request handling

    @Override
    public void onBeginRecording(ServerPlayer player, ServerboundPayloads.BeginRecording payload) {
        guarded(player, () -> {
            recordingService.begin(player, payload.blockTarget(), voice);
            // Refresh the address book now: the confirmation screen appears seconds from here and
            // its recipient picker has nothing else to populate itself from. Previously this was
            // only sent alongside an inbox response, so a player who had not opened the inbox this
            // session saw an empty picker and could not address a private pin at all.
            EchoPinsNetwork.sendTo(player, new ClientboundPayloads.KnownPlayers(knownPlayers(player)));
        });
    }

    @Override
    public void onFinishRecording(ServerPlayer player) {
        guarded(player, () -> recordingService.finish(player));
    }

    @Override
    public void onCancelRecording(ServerPlayer player) {
        guarded(player, () -> recordingService.cancel(player));
    }

    @Override
    public void onCreatePin(ServerPlayer player, ServerboundPayloads.CreatePin payload) {
        guarded(player, () -> {
            RecordingService.PendingRecording pending = recordingService
                    .takePending(player.getUUID())
                    .orElseThrow(() -> new EchoPinException(EchoPinError.NOTHING_RECORDED,
                            "No pending recording for " + player.getUUID()));
            try {
                EchoPin pin = pinService.createFromPending(player, pending,
                        payload.visibility(), payload.recipients(), payload.caption(),
                        payload.expiry(), isOperator(player));
                EchoPinsNetwork.sendTo(player, new ClientboundPayloads.RecordingState(
                        ClientboundPayloads.RecordingPhase.IDLE, 0,
                        limits.maxRecordingSeconds() * 1000, false));
                LOGGER.debug("{} created pin {}", player.getGameProfile().getName(), pin.id());
            } catch (EchoPinException e) {
                if (e.error() == EchoPinError.CREATE_COOLDOWN) {
                    // Purely a matter of waiting a few seconds. Destroying a message the player
                    // has already spoken because they were briefly too quick would be a poor
                    // trade, so it is put back and they can press Save again.
                    recordingService.restorePending(player.getUUID(), pending);
                } else {
                    // Nothing the player can do about this one, so release the audio and close
                    // their confirmation screen rather than leaving it open over nothing.
                    // discardPending would be a no-op here: takePending already emptied the slot.
                    recordingService.discardTaken(pending);
                    EchoPinsNetwork.sendTo(player, new ClientboundPayloads.RecordingState(
                            ClientboundPayloads.RecordingPhase.IDLE, 0,
                            limits.maxRecordingSeconds() * 1000, false));
                }
                throw e;
            }
        });
    }

    @Override
    public void onRequestPlayback(ServerPlayer player, ServerboundPayloads.RequestPlayback payload) {
        guarded(player, () ->
                playbackService.requestPlayback(player, payload.pin(), isOperator(player)));
    }

    @Override
    public void onStopPlayback(ServerPlayer player, ServerboundPayloads.StopPlayback payload) {
        guarded(player, () -> playbackService.stopPlayback(player, payload.pin()));
    }

    @Override
    public void onDeletePin(ServerPlayer player, ServerboundPayloads.DeletePin payload) {
        guarded(player, () -> {
            EchoPin deleted = pinService.delete(player.getUUID(), payload.pin(), isOperator(player));
            playbackService.stopPlaybacksOf(deleted.id());
            syncService.broadcastRemoval(server, deleted.id());
        });
    }

    @Override
    public void onRequestInbox(ServerPlayer player, ServerboundPayloads.RequestInbox payload) {
        guarded(player, () -> {
            List<EchoPin> matching = inboxQuery(player, payload.tab());
            int totalPages = Math.max(1, (matching.size() + INBOX_PAGE_SIZE - 1) / INBOX_PAGE_SIZE);
            int page = Math.min(payload.page(), totalPages - 1);
            int from = page * INBOX_PAGE_SIZE;
            int to = Math.min(matching.size(), from + INBOX_PAGE_SIZE);

            List<PinSummary> entries = new ArrayList<>();
            for (EchoPin pin : matching.subList(Math.min(from, matching.size()), to)) {
                entries.add(PinSummary.of(pin, !savedData.readState().isRead(player.getUUID(), pin.id())));
            }
            EchoPinsNetwork.sendTo(player,
                    new ClientboundPayloads.InboxPage(payload.tab(), page, totalPages, entries));
            EchoPinsNetwork.sendTo(player, new ClientboundPayloads.KnownPlayers(knownPlayers(player)));
        });
    }

    @Override
    public void onMarkRead(ServerPlayer player, ServerboundPayloads.MarkRead payload) {
        guarded(player, () -> {
            EchoPin pin = savedData.pins().find(payload.pin()).orElseThrow(() ->
                    new EchoPinException(EchoPinError.PIN_NOT_FOUND, "Unknown pin"));
            if (!DefaultAccessPolicy.INSTANCE.canDiscover(pin, player.getUUID(), isOperator(player))) {
                throw new EchoPinException(EchoPinError.NO_ACCESS, "Cannot mark an invisible pin as read");
            }
            savedData.readState().markRead(player.getUUID(), payload.pin());
        });
    }

    /** Builds one inbox tab's contents, already filtered by what the player may see. */
    public List<EchoPin> inboxQuery(ServerPlayer player, ServerboundPayloads.InboxTab tab) {
        UUID uuid = player.getUUID();
        boolean operator = isOperator(player);

        List<EchoPin> result = switch (tab) {
            case NEARBY -> new ArrayList<>(syncService.visiblePinsFor(player));
            case MINE -> new ArrayList<>(savedData.pins().findByAuthor(uuid));
            case PRIVATE -> {
                List<EchoPin> privatePins = new ArrayList<>();
                for (EchoPin pin : savedData.pins().all()) {
                    if (pin.visibility() == Visibility.PRIVATE
                            && DefaultAccessPolicy.INSTANCE.canDiscover(pin, uuid, false)) {
                        privatePins.add(pin);
                    }
                }
                yield privatePins;
            }
            case UNREAD -> {
                List<EchoPin> unread = new ArrayList<>();
                for (EchoPin pin : savedData.pins().all()) {
                    if (DefaultAccessPolicy.INSTANCE.canDiscover(pin, uuid, operator)
                            && !savedData.readState().isRead(uuid, pin.id())) {
                        unread.add(pin);
                    }
                }
                yield unread;
            }
        };
        result.sort(Comparator.comparingLong(EchoPin::createdAt).reversed());
        return result;
    }

    /**
     * The address book for the recipient picker: everyone online, plus the authors of pins this
     * player can already see. No player the viewer has no way of knowing about is ever revealed.
     */
    public List<ClientboundPayloads.KnownPlayer> knownPlayers(ServerPlayer viewer) {
        Map<UUID, ClientboundPayloads.KnownPlayer> byUuid = new LinkedHashMap<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (byUuid.size() >= ClientboundPayloads.KnownPlayers.MAX_PLAYERS) {
                break;
            }
            if (!online.getUUID().equals(viewer.getUUID())) {
                byUuid.put(online.getUUID(), new ClientboundPayloads.KnownPlayer(
                        online.getUUID(), online.getGameProfile().getName(), true));
            }
        }
        for (EchoPin pin : savedData.pins().all()) {
            UUID author = pin.authorUuid();
            if (author.equals(viewer.getUUID()) || byUuid.containsKey(author)) {
                continue;
            }
            if (DefaultAccessPolicy.INSTANCE.canDiscover(pin, viewer.getUUID(), false)) {
                byUuid.put(author, new ClientboundPayloads.KnownPlayer(
                        author, pin.author().lastKnownName(), false));
            }
            if (byUuid.size() >= ClientboundPayloads.KnownPlayers.MAX_PLAYERS) {
                break;
            }
        }
        return new ArrayList<>(byUuid.values());
    }

    public boolean isOperator(ServerPlayer player) {
        return player.hasPermissions(limits.operatorPermissionLevel());
    }

    /**
     * Applies the shared guard rails to a request: the master switch, the per-player rate limit,
     * and error translation.
     */
    private void guarded(ServerPlayer player, Runnable action) {
        if (!limits.enabled()) {
            sendError(player, EchoPinError.DISABLED);
            return;
        }
        long now = System.currentTimeMillis();
        if (!requestLimiter.tryAcquire(player.getUUID(), now)) {
            LOGGER.debug("Rate limited a request from {}", player.getGameProfile().getName());
            // Not the create cooldown: this limiter covers every request, so telling a player who
            // pressed play that they must wait before "creating another pin" was simply wrong.
            sendError(player, EchoPinError.RATE_LIMITED);
            return;
        }
        try {
            action.run();
        } catch (EchoPinException e) {
            LOGGER.debug("Refused an EchoPins request from {}: {}",
                    player.getGameProfile().getName(), e.getMessage());
            if (e.hasArgument()) {
                sendError(player, e.error(), e.argument());
            } else {
                sendError(player, e.error());
            }
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected failure handling an EchoPins request from {}",
                    player.getGameProfile().getName(), e);
            sendError(player, EchoPinError.INTERNAL_ERROR);
        }
    }

    private static void sendError(ServerPlayer player, EchoPinError error) {
        EchoPinsNetwork.sendTo(player, ClientboundPayloads.ErrorMessage.of(error));
    }

    private static void sendError(ServerPlayer player, EchoPinError error, long argument) {
        EchoPinsNetwork.sendTo(player, ClientboundPayloads.ErrorMessage.of(error, argument));
    }

    /** Snapshot of the numbers the admin stats command reports. */
    public record Metrics(int totalPins, int pinsInDimension, int activeRecordings,
                          int pendingRecordings, int activePlaybacks, int subscriptions,
                          long audioBytes, int readStateEntries, int spatialBuckets) {
    }

    public Metrics metrics(ServerPlayer viewer) {
        int inDimension = viewer == null ? 0
                : savedData.pins().countInDimension(AnchorResolver.dimensionOf(viewer));
        return new Metrics(
                savedData.pins().totalCount(),
                inDimension,
                recordingService.activeSessionCount(),
                recordingService.pendingCount(),
                playbackService.activePlaybackCount(),
                syncService.subscriptionCount(),
                audioStore.totalBytes(),
                savedData.readState().totalEntries(),
                savedData.pins().spatialIndex().bucketCount());
    }
}
