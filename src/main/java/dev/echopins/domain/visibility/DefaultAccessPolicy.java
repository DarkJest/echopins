package dev.echopins.domain.visibility;

import dev.echopins.domain.pin.EchoPin;

import java.util.UUID;

/**
 * The v1 access rules.
 *
 * <p>Operators can discover, play and delete anything, because a server admin already has full
 * access to the world files and needs to be able to moderate abusive recordings. Operators
 * deliberately cannot <em>edit</em> another player's pin: silently rewriting someone's caption
 * or visibility is a different kind of power from moderation, and deleting is the honest tool
 * for that job.
 */
public final class DefaultAccessPolicy implements AccessPolicy {

    public static final DefaultAccessPolicy INSTANCE = new DefaultAccessPolicy();

    @Override
    public boolean canDiscover(EchoPin pin, UUID viewer, boolean operator) {
        if (operator) {
            return true;
        }
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
