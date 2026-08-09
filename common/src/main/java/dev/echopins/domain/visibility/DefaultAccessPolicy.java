package dev.echopins.domain.visibility;

import dev.echopins.domain.pin.EchoPin;

import java.util.UUID;

/**
 * The v1 access rules.
 *
 * <p><b>Operator status grants no access to private recordings.</b> A private pin is readable by
 * its author and the people the author named, and by nobody else - including admins. That is what
 * the word "private" has to mean in a mod whose whole subject is people's voices; an operator
 * bypass here made private pins effectively public on any server where staff are online, and
 * completely meaningless in single player, where the host always holds permission level 4.
 *
 * <p>Moderation is still possible and is deliberately shaped as <em>removal</em>: an operator can
 * delete any pin without being able to listen to it. Where an admin genuinely must audit content,
 * they have direct access to the world files, which {@code PRIVACY.md} states plainly rather than
 * pretending otherwise.
 *
 * <p>Operators also cannot <em>edit</em> another player's pin: silently rewriting someone's
 * caption or visibility is a different kind of power again.
 */
public final class DefaultAccessPolicy implements AccessPolicy {

    public static final DefaultAccessPolicy INSTANCE = new DefaultAccessPolicy();

    @Override
    public boolean canDiscover(EchoPin pin, UUID viewer, boolean operator) {
        // `operator` is intentionally unused: see the class note.
        return isOwner(pin, viewer)
                || pin.visibility() == Visibility.PUBLIC
                || pin.recipients().contains(viewer);
    }

    @Override
    public boolean canPlay(EchoPin pin, UUID viewer, boolean operator) {
        return canDiscover(pin, viewer, operator);
    }

    @Override
    public boolean canDelete(EchoPin pin, UUID viewer, boolean operator) {
        return operator || isOwner(pin, viewer);
    }

    @Override
    public boolean canEdit(EchoPin pin, UUID viewer, boolean operator) {
        return isOwner(pin, viewer);
    }

    private static boolean isOwner(EchoPin pin, UUID viewer) {
        return pin.authorUuid().equals(viewer);
    }
}
