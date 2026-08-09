package dev.echopins.domain.expiry;

import dev.echopins.domain.pin.EchoPin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredExpiryPolicyTest {

    private static final long NOW = 1_000_000L;

    @Test
    @DisplayName("The default choice uses the configured lifetime")
    void defaultLifetime() {
        var policy = new ConfiguredExpiryPolicy(() -> 72, () -> true);
        assertEquals(NOW + Duration.ofHours(72).toMillis(),
                policy.resolveExpiry(ExpiryChoice.DEFAULT, NOW));
    }

    @Test
    @DisplayName("A short pin lives for a fraction of the default, floored at an hour")
    void shortLifetime() {
        var policy = new ConfiguredExpiryPolicy(() -> 72, () -> true);
        assertEquals(NOW + Duration.ofHours(9).toMillis(),
                policy.resolveExpiry(ExpiryChoice.SHORT, NOW));

        var tinyDefault = new ConfiguredExpiryPolicy(() -> 2, () -> true);
        assertEquals(NOW + Duration.ofHours(1).toMillis(),
                tinyDefault.resolveExpiry(ExpiryChoice.SHORT, NOW),
                "a short pin never rounds down to zero");
    }

    @Test
    @DisplayName("Permanent pins are honoured when the server allows them")
    void permanentAllowed() {
        var policy = new ConfiguredExpiryPolicy(() -> 72, () -> true);
        assertTrue(policy.allowsPermanent());
        assertEquals(EchoPin.NEVER_EXPIRES, policy.resolveExpiry(ExpiryChoice.PERMANENT, NOW));
    }

    @Test
    @DisplayName("When permanent pins are disabled the recording is kept, not discarded")
    void permanentDeniedFallsBackToDefault() {
        var policy = new ConfiguredExpiryPolicy(() -> 48, () -> false);

        assertFalse(policy.allowsPermanent());
        assertEquals(NOW + Duration.ofHours(48).toMillis(),
                policy.resolveExpiry(ExpiryChoice.PERMANENT, NOW),
                "falling back beats throwing away what the player just recorded");
    }

    @Test
    @DisplayName("A default lifetime of zero means pins do not expire on their own")
    void zeroDefaultMeansNoExpiry() {
        var policy = new ConfiguredExpiryPolicy(() -> 0, () -> true);

        assertEquals(EchoPin.NEVER_EXPIRES, policy.resolveExpiry(ExpiryChoice.DEFAULT, NOW));
        assertEquals(EchoPin.NEVER_EXPIRES, policy.resolveExpiry(ExpiryChoice.PERMANENT, NOW));
        assertEquals(NOW + Duration.ofHours(1).toMillis(),
                policy.resolveExpiry(ExpiryChoice.SHORT, NOW),
                "an explicit short pin still expires");
    }

    @Test
    @DisplayName("Config changes take effect without rebuilding the policy")
    void rereadsConfig() {
        int[] hours = {24};
        var policy = new ConfiguredExpiryPolicy(() -> hours[0], () -> true);

        assertEquals(NOW + Duration.ofHours(24).toMillis(), policy.resolveExpiry(ExpiryChoice.DEFAULT, NOW));
        hours[0] = 6;
        assertEquals(NOW + Duration.ofHours(6).toMillis(), policy.resolveExpiry(ExpiryChoice.DEFAULT, NOW));
    }

    @Test
    @DisplayName("An unknown persisted choice id falls back to the default")
    void unknownChoiceFallsBack() {
        assertEquals(ExpiryChoice.DEFAULT, ExpiryChoice.byId(99));
        for (ExpiryChoice choice : ExpiryChoice.values()) {
            assertEquals(choice, ExpiryChoice.byId(choice.id()));
        }
    }
}
