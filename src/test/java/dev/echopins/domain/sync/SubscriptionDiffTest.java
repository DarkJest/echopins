package dev.echopins.domain.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for subscription reconciliation.
 *
 * <p>The bug this guards against: something outside this method removed an id from {@code known}
 * when a pin was deleted. The removal list is derived from the difference between {@code known}
 * and what is visible, so pre-emptively forgetting the id made it impossible to report. Expired
 * pins were therefore never retracted and stayed drawn on the client until it reconnected.
 */
class SubscriptionDiffTest {

    private static Set<String> known(String... ids) {
        return new LinkedHashSet<>(List.of(ids));
    }

    @Test
    @DisplayName("A pin that disappeared is reported as removed")
    void reportsDisappearance() {
        Set<String> known = known("a", "b", "c");

        var diff = SubscriptionDiff.reconcile(known, List.of("a", "c"));

        assertEquals(List.of(), diff.added());
        assertEquals(List.of("b"), diff.removed());
        assertEquals(known("a", "c"), known);
    }

    @Test
    @DisplayName("A pin the client has not seen is reported as added")
    void reportsAddition() {
        Set<String> known = known("a");

        var diff = SubscriptionDiff.reconcile(known, List.of("a", "b"));

        assertEquals(List.of("b"), diff.added());
        assertEquals(List.of(), diff.removed());
        assertEquals(known("a", "b"), known);
    }

    @Test
    @DisplayName("Additions and removals are reported together")
    void reportsBoth() {
        Set<String> known = known("a", "b");

        var diff = SubscriptionDiff.reconcile(known, List.of("b", "c"));

        assertEquals(List.of("c"), diff.added());
        assertEquals(List.of("a"), diff.removed());
        assertEquals(known("b", "c"), known);
    }

    @Test
    @DisplayName("No change produces an empty result, so no packet is sent")
    void unchangedIsEmpty() {
        Set<String> known = known("a", "b");

        var diff = SubscriptionDiff.reconcile(known, List.of("a", "b"));

        assertTrue(diff.isEmpty());
        assertEquals(known("a", "b"), known);
    }

    @Test
    @DisplayName("Everything disappearing retracts everything")
    void allGone() {
        Set<String> known = known("a", "b", "c");

        var diff = SubscriptionDiff.reconcile(known, List.of());

        assertEquals(List.of("a", "b", "c"), diff.removed());
        assertTrue(known.isEmpty());
        assertFalse(diff.isEmpty());
    }

    @Test
    @DisplayName("An id already forgotten from `known` can never be retracted")
    void forgettingEarlyMakesRetractionImpossible() {
        // This is the shape of the original bug, asserted so nobody reintroduces the shortcut.
        Set<String> known = known("a", "b");
        known.remove("b");

        var diff = SubscriptionDiff.reconcile(known, List.of("a"));

        assertTrue(diff.removed().isEmpty(),
                "with the id dropped in advance there is nothing left to tell the client about");
    }

    @Test
    @DisplayName("Reconciling twice is stable")
    void isIdempotent() {
        Set<String> known = known("a");

        SubscriptionDiff.reconcile(known, List.of("b", "c"));
        var second = SubscriptionDiff.reconcile(known, List.of("b", "c"));

        assertTrue(second.isEmpty());
        assertEquals(known("b", "c"), known);
    }

    @Test
    @DisplayName("Starting from nothing reports every visible pin as added")
    void freshSubscription() {
        Set<String> known = known();

        var diff = SubscriptionDiff.reconcile(known, List.of("a", "b"));

        assertEquals(List.of("a", "b"), diff.added());
        assertEquals(List.of(), diff.removed());
    }
}
