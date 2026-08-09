package dev.echopins.domain.anchor;

import java.util.Objects;

/**
 * A pin floating at a world position, used when the player was not looking at a usable block.
 *
 * @param dimension the dimension the position is in
 * @param position  the world position
 */
public record PositionAnchor(DimensionId dimension, WorldPos position) implements WorldAnchor {

    public PositionAnchor {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
    }

    @Override
    public Kind kind() {
        return Kind.POSITION;
    }

    @Override
    public WorldPos renderPos() {
        return position;
    }
}
