package dev.echopins.application.sync;

import dev.echopins.application.ServerLimits;
import dev.echopins.application.pin.AnchorResolver;
import dev.echopins.domain.anchor.DimensionId;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.event.DomainEventBus;
import dev.echopins.domain.event.DomainEvents;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinId;
import dev.echopins.domain.repository.PinRepository;
import dev.echopins.domain.repository.ReadStateRepository;
import dev.echopins.domain.sync.SubscriptionDiff;
import dev.echopins.domain.sync.SyncThrottle;
import dev.echopins.domain.visibility.AccessPolicy;
import dev.echopins.infrastructure.network.EchoPinsNetwork;
import dev.echopins.infrastructure.network.PinSummary;
import dev.echopins.infrastructure.network.payload.ClientboundPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides which pins each player knows about, and keeps that set current.
 *
 * <p>Three properties matter here:
 *
 * <ul>
 *   <li><b>Never send everything.</b> A player receives only pins within the discovery radius
 *       that they are allowed to see, capped at {@code maxSyncedPinsPerPlayer} and ordered
 *       nearest-first, so a server with thousands of pins costs the same per player as one with
 *       a dozen.</li>
 *   <li><b>Never resend the same state.</b> Recalculation is throttled and gated on the player
 *       actually having crossed a chunk boundary, and only the difference is transmitted.</li>
 *   <li><b>Never scan every pin.</b> Candidate lookup goes through the repository's spatial
 *       index.</li>
 * </ul>
 */
public final class PinSyncService {

    private static final long NEVER_SYNCED = SyncThrottle.NEVER_SYNCED;

    /** A player's current subscription. */
    private static final class Subscription {
        final Set<PinId> known = new LinkedHashSet<>();
        long lastSyncTick = NEVER_SYNCED;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        DimensionId lastDimension;
    }

    private final ServerLimits limits;
    private final PinRepository pins;
    private final ReadStateRepository readState;
    private final AccessPolicy accessPolicy;

    private final Map<UUID, Subscription> subscriptions = new HashMap<>();

    public PinSyncService(ServerLimits limits, PinRepository pins, ReadStateRepository readState,
                          AccessPolicy accessPolicy, DomainEventBus events) {
        this.limits = limits;
        this.pins = pins;
        this.readState = readState;
        this.accessPolicy = accessPolicy;

        // Reacting to domain events rather than being called by the pin service keeps creation
        // and deletion logic unaware that synchronisation exists at all.
        events.subscribe(DomainEvents.PinCreated.class, event -> markAllStale());
        events.subscribe(DomainEvents.PinRemoved.class, event -> onPinRemoved(event.id()));
        events.subscribe(DomainEvents.PinUpdated.class, event -> markAllStale());
    }

    public int subscriptionCount() {
        return subscriptions.size();
    }

    public int subscribedPinCount(UUID player) {
        Subscription subscription = subscriptions.get(player);
        return subscription == null ? 0 : subscription.known.size();
    }

    /** Sends the player their settings and a full snapshot. */
    public void onPlayerJoined(ServerPlayer player) {
        EchoPinsNetwork.sendTo(player, new ClientboundPayloads.ServerSettings(
                limits.discoveryRadius(),
                limits.interactionRadius(),
                limits.maxRecordingSeconds(),
                limits.minRecordingMillis(),
                limits.maxCaptionLength(),
                limits.maxPrivateRecipients(),
                limits.allowPermanentPins()));
        sendSnapshot(player, true);
    }

    public void onPlayerLeft(UUID player) {
        subscriptions.remove(player);
    }

    /** A dimension change invalidates everything, so the client gets a fresh snapshot. */
    public void onDimensionChanged(ServerPlayer player) {
        sendSnapshot(player, true);
    }

    /** Pushes settings to everyone, after a config reload. */
    public void broadcastSettings(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onPlayerJoined(player);
        }
    }

    /** Per-tick incremental synchronisation. */
    public void tick(MinecraftServer server, long tick) {
        int interval = limits.syncIntervalTicks();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Subscription subscription = subscriptions.computeIfAbsent(
                    player.getUUID(), uuid -> new Subscription());

            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            DimensionId dimension = AnchorResolver.dimensionOf(player);

            boolean movedChunk = chunkX != subscription.lastChunkX
                    || chunkZ != subscription.lastChunkZ
                    || !dimension.equals(subscription.lastDimension);

            if (!SyncThrottle.shouldRecalculate(subscription.lastSyncTick, tick, interval, movedChunk)) {
                continue;
            }

            subscription.lastSyncTick = tick;
            subscription.lastChunkX = chunkX;
            subscription.lastChunkZ = chunkZ;
            subscription.lastDimension = dimension;
            sendDelta(player, subscription);
        }
    }

    /** Forces a recalculation on the next tick for every player. */
    private void markAllStale() {
        for (Subscription subscription : subscriptions.values()) {
            subscription.lastSyncTick = NEVER_SYNCED;
        }
    }

    private void onPinRemoved(PinId removed) {
        // Deliberately does NOT drop the id from `known`. sendDelta derives its removal list from
        // exactly that difference - ids the client was told about which are no longer visible -
        // so forgetting the pin here would guarantee the client is never told it disappeared.
        // That is what left expired pins rendering on screen until the player relogged: expiry,
        // unlike an explicit delete, has no separate broadcast to fall back on.
        for (Subscription subscription : subscriptions.values()) {
            subscription.lastSyncTick = NEVER_SYNCED;
        }
    }

    /** Tells everyone who knew about a pin that it is gone. */
    public void broadcastRemoval(MinecraftServer server, PinId removed) {
        ClientboundPayloads.PinsDelta delta =
                new ClientboundPayloads.PinsDelta(List.of(), List.of(removed));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            EchoPinsNetwork.sendTo(player, delta);
        }
    }

    private void sendSnapshot(ServerPlayer player, boolean resetSubscription) {
        Subscription subscription = subscriptions.computeIfAbsent(
                player.getUUID(), uuid -> new Subscription());
        if (resetSubscription) {
            subscription.known.clear();
        }

        List<EchoPin> visible = visiblePinsFor(player);
        List<PinSummary> summaries = new ArrayList<>(visible.size());
        for (EchoPin pin : visible) {
            subscription.known.add(pin.id());
            summaries.add(PinSummary.of(pin, !readState.isRead(player.getUUID(), pin.id())));
        }

        subscription.lastChunkX = player.chunkPosition().x;
        subscription.lastChunkZ = player.chunkPosition().z;
        subscription.lastDimension = AnchorResolver.dimensionOf(player);
        EchoPinsNetwork.sendTo(player, new ClientboundPayloads.PinsSnapshot(summaries));
    }

    private void sendDelta(ServerPlayer player, Subscription subscription) {
        List<EchoPin> visible = visiblePinsFor(player);
        Map<PinId, EchoPin> byId = new LinkedHashMap<>(visible.size());
        for (EchoPin pin : visible) {
            byId.put(pin.id(), pin);
        }

        SubscriptionDiff.Result<PinId> diff =
                SubscriptionDiff.reconcile(subscription.known, byId.keySet());
        if (diff.isEmpty()) {
            return;
        }

        List<PinSummary> added = new ArrayList<>(diff.added().size());
        for (PinId id : diff.added()) {
            EchoPin pin = byId.get(id);
            added.add(PinSummary.of(pin, !readState.isRead(player.getUUID(), id)));
        }
        EchoPinsNetwork.sendTo(player, new ClientboundPayloads.PinsDelta(added, diff.removed()));
    }

    /**
     * The pins a player may currently see: within the discovery radius, permitted by the access
     * policy, nearest first, capped.
     */
    public List<EchoPin> visiblePinsFor(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean operator = player.hasPermissions(limits.operatorPermissionLevel());
        DimensionId dimension = AnchorResolver.dimensionOf(player);
        WorldPos eye = new WorldPos(player.getX(), player.getEyeY(), player.getZ());

        List<EchoPin> candidates = pins.findNearby(dimension, eye, limits.discoveryRadius());
        List<EchoPin> allowed = new ArrayList<>(Math.min(candidates.size(), limits.maxSyncedPinsPerPlayer()));
        for (EchoPin pin : candidates) {
            if (accessPolicy.canDiscover(pin, uuid, operator)) {
                allowed.add(pin);
            }
        }
        // Sorting after filtering means the cap keeps the closest permitted pins rather than
        // whichever happened to come out of the index first.
        allowed.sort(Comparator.comparingDouble(
                pin -> pin.anchor().renderPos().distanceSquaredTo(eye)));
        if (allowed.size() > limits.maxSyncedPinsPerPlayer()) {
            return allowed.subList(0, limits.maxSyncedPinsPerPlayer());
        }
        return allowed;
    }

    public void shutdown() {
        subscriptions.clear();
    }
}
