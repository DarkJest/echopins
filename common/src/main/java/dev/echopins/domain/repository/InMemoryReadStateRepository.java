package dev.echopins.domain.repository;

import dev.echopins.domain.pin.PinId;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player read state held in memory.
 *
 * <p>Sets are insertion-ordered and capped: if a player somehow accumulates more read ids than
 * {@link #MAX_ENTRIES_PER_PLAYER}, the oldest are dropped first. In practice the cap is never
 * reached because {@link #forgetPin} removes ids as pins expire, but it guarantees that a single
 * player's read state can never grow without bound even if a future bug leaks entries.
 */
public class InMemoryReadStateRepository implements ReadStateRepository {

    /**
     * Comfortably above any realistic {@code maxTotalPins}, so the cap only ever acts as a
     * backstop rather than silently marking old pins unread again.
     */
    public static final int MAX_ENTRIES_PER_PLAYER = 20_000;

    private final Map<UUID, Set<PinId>> readByPlayer = new HashMap<>();

    @Override
    public boolean isRead(UUID player, PinId pin) {
        Set<PinId> read = readByPlayer.get(player);
        return read != null && read.contains(pin);
    }

    @Override
    public void markRead(UUID player, PinId pin) {
        Set<PinId> read = readByPlayer.computeIfAbsent(player, k -> new LinkedHashSet<>());
        if (read.add(pin)) {
            if (read.size() > MAX_ENTRIES_PER_PLAYER) {
                Iterator<PinId> oldest = read.iterator();
                oldest.next();
                oldest.remove();
            }
            markDirty();
        }
    }

    @Override
    public int countUnread(UUID player, Collection<PinId> candidates) {
        Set<PinId> read = readByPlayer.get(player);
        if (read == null || read.isEmpty()) {
            return candidates.size();
        }
        int unread = 0;
        for (PinId candidate : candidates) {
            if (!read.contains(candidate)) {
                unread++;
            }
        }
        return unread;
    }

    @Override
    public void forgetPin(PinId pin) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, Set<PinId>>> it = readByPlayer.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Set<PinId>> entry = it.next();
            if (entry.getValue().remove(pin)) {
                changed = true;
                if (entry.getValue().isEmpty()) {
                    it.remove();
                }
            }
        }
        if (changed) {
            markDirty();
        }
    }

    @Override
    public void forgetPlayer(UUID player) {
        if (readByPlayer.remove(player) != null) {
            markDirty();
        }
    }

    @Override
    public void markDirty() {
        // No-op in memory; persistence overrides.
    }

    protected Map<UUID, Set<PinId>> rawState() {
        return readByPlayer;
    }

    /** Total tracked (player, pin) pairs. Exposed for admin stats. */
    public int totalEntries() {
        int total = 0;
        for (Set<PinId> set : readByPlayer.values()) {
            total += set.size();
        }
        return total;
    }
}
