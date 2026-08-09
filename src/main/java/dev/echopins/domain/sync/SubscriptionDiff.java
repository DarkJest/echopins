package dev.echopins.domain.sync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reconciles what a client has been told with what it should now see.
 *
 * <p>Pure, and in the domain layer, because this is where the subtle bugs live. The removal list
 * is derived from the difference between the two sets, which means anything that quietly drops an
 * id from {@code known} outside this method makes that id undeliverable: the client is never told
 * the pin went away and keeps rendering it. That is exactly how expired pins ended up stuck on
 * screen until a relog.
 */
public final class SubscriptionDiff {

    /**
     * What changed.
     *
     * @param added   ids the client has not been told about yet
     * @param removed ids the client knows about but should no longer see
     */
    public record Result<T>(List<T> added, List<T> removed) {

        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty();
        }
    }

    private SubscriptionDiff() {
    }

    /**
     * Brings {@code known} in line with {@code visibleNow} and reports the difference.
     *
     * @param known      the client's current knowledge; mutated to match {@code visibleNow}
     * @param visibleNow everything the client should see right now
     */
    public static <T> Result<T> reconcile(Set<T> known, Collection<T> visibleNow) {
        Set<T> visible = visibleNow instanceof Set<T> set ? set : new LinkedHashSet<>(visibleNow);

        List<T> added = new ArrayList<>();
        for (T id : visible) {
            if (known.add(id)) {
                added.add(id);
            }
        }

        List<T> removed = new ArrayList<>();
        known.removeIf(id -> {
            if (visible.contains(id)) {
                return false;
            }
            removed.add(id);
            return true;
        });

        return new Result<>(added, removed);
    }
}
