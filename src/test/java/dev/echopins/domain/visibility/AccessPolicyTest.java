package dev.echopins.domain.visibility;

import dev.echopins.domain.pin.EchoPin;
import dev.echopins.testsupport.Pins;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessPolicyTest {

    private static final AccessPolicy POLICY = DefaultAccessPolicy.INSTANCE;

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID FRIEND = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    @Test
    @DisplayName("A public pin is visible and playable by anyone")
    void publicPinIsOpen() {
        EchoPin pin = Pins.publicPin(AUTHOR, 0, 64, 0);

        assertTrue(POLICY.canDiscover(pin, STRANGER, false));
        assertTrue(POLICY.canPlay(pin, STRANGER, false));
    }

    @Test
    @DisplayName("A private pin is hidden from players who are not recipients")
    void privatePinHidesFromStrangers() {
        EchoPin pin = Pins.privatePin(AUTHOR, Set.of(FRIEND));

        assertFalse(POLICY.canDiscover(pin, STRANGER, false));
        assertFalse(POLICY.canPlay(pin, STRANGER, false));
    }

    @Test
    @DisplayName("A private pin is available to its author and to listed recipients")
    void privatePinAllowsAuthorAndRecipients() {
        EchoPin pin = Pins.privatePin(AUTHOR, Set.of(FRIEND));

        assertTrue(POLICY.canDiscover(pin, AUTHOR, false));
        assertTrue(POLICY.canPlay(pin, AUTHOR, false));
        assertTrue(POLICY.canDiscover(pin, FRIEND, false));
        assertTrue(POLICY.canPlay(pin, FRIEND, false));
    }

    @Test
    @DisplayName("Only the author or an operator may delete a pin")
    void deletionIsRestricted() {
        EchoPin pin = Pins.publicPin(AUTHOR, 0, 64, 0);

        assertTrue(POLICY.canDelete(pin, AUTHOR, false));
        assertFalse(POLICY.canDelete(pin, STRANGER, false));
        assertTrue(POLICY.canDelete(pin, STRANGER, true));
    }

    @Test
    @DisplayName("Operators can moderate private pins but cannot rewrite them")
    void operatorCanModerateButNotEdit() {
        EchoPin pin = Pins.privatePin(AUTHOR, Set.of(FRIEND));

        assertTrue(POLICY.canDiscover(pin, STRANGER, true));
        assertTrue(POLICY.canPlay(pin, STRANGER, true));
        assertTrue(POLICY.canDelete(pin, STRANGER, true));
        assertFalse(POLICY.canEdit(pin, STRANGER, true), "editing someone else's pin is not moderation");
    }

    @Test
    @DisplayName("Only the author may edit a pin")
    void onlyAuthorEdits() {
        EchoPin pin = Pins.publicPin(AUTHOR, 0, 64, 0);

        assertTrue(POLICY.canEdit(pin, AUTHOR, false));
        assertFalse(POLICY.canEdit(pin, FRIEND, false));
    }

    @Test
    @DisplayName("Marking a pin public discards any recipient list")
    void publicPinNormalisesRecipients() {
        EchoPin pin = Pins.pin(AUTHOR,
                Pins.publicPin(AUTHOR, 0, 64, 0).anchor(),
                Visibility.PUBLIC,
                Set.of(FRIEND, STRANGER),
                EchoPin.NEVER_EXPIRES);

        assertTrue(pin.recipients().isEmpty(),
                "a public pin must not carry a recipient list into ACL checks");
    }

    @Test
    @DisplayName("A corrupted visibility id falls back to private, never to public")
    void unknownVisibilityFailsClosed() {
        assertTrue(Visibility.byId(99) == Visibility.PRIVATE);
        assertTrue(Visibility.byId(-1) == Visibility.PRIVATE);
    }
}
