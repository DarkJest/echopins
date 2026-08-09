package dev.echopins.testsupport;

import dev.echopins.domain.anchor.BlockAnchor;
import dev.echopins.domain.anchor.BlockFace;
import dev.echopins.domain.anchor.DimensionId;
import dev.echopins.domain.anchor.PositionAnchor;
import dev.echopins.domain.anchor.WorldAnchor;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.audio.AudioRef;
import dev.echopins.domain.pin.Caption;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinAuthor;
import dev.echopins.domain.pin.PinId;
import dev.echopins.domain.visibility.Visibility;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Builders that keep the tests readable instead of repeating ten-argument constructors. */
public final class Pins {

    public static final DimensionId OVERWORLD = DimensionId.parse("minecraft:overworld");
    public static final DimensionId NETHER = DimensionId.parse("minecraft:the_nether");

    private Pins() {
    }

    public static EchoPin publicPin(UUID author, double x, double y, double z) {
        return pin(author, new PositionAnchor(OVERWORLD, new WorldPos(x, y, z)),
                Visibility.PUBLIC, Set.of(), EchoPin.NEVER_EXPIRES);
    }

    public static EchoPin publicPin(UUID author, DimensionId dimension, double x, double y, double z) {
        return pin(author, new PositionAnchor(dimension, new WorldPos(x, y, z)),
                Visibility.PUBLIC, Set.of(), EchoPin.NEVER_EXPIRES);
    }

    public static EchoPin privatePin(UUID author, Set<UUID> recipients) {
        return pin(author, new PositionAnchor(OVERWORLD, new WorldPos(0, 64, 0)),
                Visibility.PRIVATE, recipients, EchoPin.NEVER_EXPIRES);
    }

    public static EchoPin expiringPin(UUID author, long expiresAt) {
        return pin(author, new PositionAnchor(OVERWORLD, new WorldPos(0, 64, 0)),
                Visibility.PUBLIC, Set.of(), expiresAt);
    }

    public static EchoPin blockPin(UUID author, int x, int y, int z, BlockFace face) {
        return pin(author, new BlockAnchor(OVERWORLD, x, y, z, face),
                Visibility.PUBLIC, Set.of(), EchoPin.NEVER_EXPIRES);
    }

    public static EchoPin pin(UUID author, WorldAnchor anchor, Visibility visibility,
                              Set<UUID> recipients, long expiresAt) {
        return new EchoPin(
                PinId.random(),
                new PinAuthor(author, "Tester"),
                anchor,
                1_000L,
                visibility,
                recipients,
                Optional.of(new Caption("hello")),
                new AudioRef(UUID.randomUUID(), 512L, 50),
                expiresAt);
    }
}
