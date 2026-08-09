package dev.echopins.domain.anchor;

/**
 * Where an EchoPin lives in the world.
 *
 * <p>A closed hierarchy: a pin is either attached to a block face (so it can follow sensible
 * rendering rules and be invalidated if the block is gone) or pinned to a free-floating
 * position. New variants would change persistence and network codecs, so the set is sealed
 * deliberately rather than left open.
 */
public sealed interface WorldAnchor permits BlockAnchor, PositionAnchor {

    /** Stable discriminator used by persistence and network codecs. Never renumber. */
    enum Kind {
        BLOCK(0),
        POSITION(1);

        private final int id;

        Kind(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Kind byId(int id) {
            for (Kind kind : values()) {
                if (kind.id == id) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Unknown anchor kind id: " + id);
        }
    }

    Kind kind();

    DimensionId dimension();

    /**
     * The point audio should appear to come from and markers should be drawn at. For a block
     * anchor this is offset off the attached face so the marker does not z-fight with the block.
     */
    WorldPos renderPos();
}
