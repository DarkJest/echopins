package dev.echopins.neoforge.gametest;

import dev.echopins.EchoPins;
import dev.echopins.domain.anchor.BlockAnchor;
import dev.echopins.domain.anchor.BlockFace;
import dev.echopins.domain.anchor.DimensionId;
import dev.echopins.domain.anchor.PositionAnchor;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.audio.AudioRef;
import dev.echopins.domain.pin.Caption;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinAuthor;
import dev.echopins.domain.pin.PinId;
import dev.echopins.domain.visibility.Visibility;
import dev.echopins.infrastructure.persistence.EchoPinsSavedData;
import dev.echopins.infrastructure.persistence.PinNbtCodec;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * GameTests for the parts that need a real Minecraft runtime.
 *
 * <p>The pure logic — access control, expiry, indexing, the audio container — is covered by plain
 * unit tests, which are faster and do not need a server. What is tested here is specifically the
 * code that touches Minecraft types and therefore cannot be reached from a plain JVM: the NBT
 * codec, the {@link SavedData} round trip, and dimension mapping against a live level.
 *
 * <p>There are deliberately no GameTests for recording or playback. Both require a real player
 * with a live voice chat connection, which the GameTest harness cannot provide; pretending to
 * test them here would produce tests that pass without exercising anything. Those paths are in
 * the manual matrix in {@code docs/TESTING.md} instead.
 */
@GameTestHolder(EchoPins.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EchoPinsGameTests {

    /**
     * Resolved against the holder's namespace, so this must stay unqualified — writing
     * {@code echopins:empty} here produces {@code echopins:echopins:empty}.
     */
    private static final String TEMPLATE = "empty";

    private EchoPinsGameTests() {
    }

    private static EchoPin samplePin(dev.echopins.domain.anchor.WorldAnchor anchor,
                                     Visibility visibility, Set<UUID> recipients, long expiresAt) {
        return new EchoPin(
                PinId.random(),
                new PinAuthor(UUID.randomUUID(), "GameTester"),
                anchor,
                1_700_000_000_000L,
                visibility,
                recipients,
                Optional.of(new Caption("hello from a gametest")),
                new AudioRef(UUID.randomUUID(), 4_096L, 120),
                expiresAt);
    }

    @GameTest(template = TEMPLATE)
    public static void blockAnchorSurvivesNbtRoundTrip(GameTestHelper helper) {
        EchoPin original = samplePin(
                new BlockAnchor(DimensionId.parse("minecraft:overworld"), 120, 64, -304, BlockFace.EAST),
                Visibility.PUBLIC, Set.of(), EchoPin.NEVER_EXPIRES);

        CompoundTag encoded = PinNbtCodec.encode(original, EchoPinsSavedData.DATA_VERSION);
        EchoPin decoded = PinNbtCodec.decode(encoded)
                .orElseThrow(() -> new AssertionError("block anchor pin failed to decode"));

        assertEquals(helper, original, decoded, "round-tripped block pin");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void positionAnchorSurvivesNbtRoundTrip(GameTestHelper helper) {
        EchoPin original = samplePin(
                new PositionAnchor(DimensionId.parse("minecraft:the_nether"),
                        new WorldPos(-12.5D, 71.25D, 402.75D)),
                Visibility.PUBLIC, Set.of(), 1_800_000_000_000L);

        EchoPin decoded = PinNbtCodec.decode(PinNbtCodec.encode(original, EchoPinsSavedData.DATA_VERSION))
                .orElseThrow(() -> new AssertionError("position anchor pin failed to decode"));

        assertEquals(helper, original, decoded, "round-tripped position pin");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void privateRecipientsSurviveNbtRoundTrip(GameTestHelper helper) {
        UUID friendA = UUID.randomUUID();
        UUID friendB = UUID.randomUUID();
        EchoPin original = samplePin(
                new PositionAnchor(DimensionId.parse("minecraft:overworld"), new WorldPos(0, 64, 0)),
                Visibility.PRIVATE, Set.of(friendA, friendB), EchoPin.NEVER_EXPIRES);

        EchoPin decoded = PinNbtCodec.decode(PinNbtCodec.encode(original, EchoPinsSavedData.DATA_VERSION))
                .orElseThrow(() -> new AssertionError("private pin failed to decode"));

        if (!decoded.recipients().equals(Set.of(friendA, friendB))) {
            helper.fail("Recipient list did not survive persistence: " + decoded.recipients());
        }
        if (decoded.visibility() != Visibility.PRIVATE) {
            helper.fail("Visibility did not survive persistence");
        }
        helper.succeed();
    }

    /**
     * A pin whose NBT is damaged must be skipped, not thrown from. Loading a world must never fail
     * because of one bad entry.
     */
    @GameTest(template = TEMPLATE)
    public static void corruptPinIsSkippedRatherThanThrown(GameTestHelper helper) {
        CompoundTag broken = PinNbtCodec.encode(
                samplePin(new PositionAnchor(DimensionId.parse("minecraft:overworld"),
                        new WorldPos(0, 64, 0)), Visibility.PUBLIC, Set.of(), EchoPin.NEVER_EXPIRES),
                EchoPinsSavedData.DATA_VERSION);
        broken.putString("dim", "NOT A VALID DIMENSION");

        if (PinNbtCodec.decode(broken).isPresent()) {
            helper.fail("A pin with an invalid dimension should not decode");
        }

        CompoundTag empty = new CompoundTag();
        if (PinNbtCodec.decode(empty).isPresent()) {
            helper.fail("An empty tag should not decode into a pin");
        }
        helper.succeed();
    }

    /**
     * The full persistence path: save through {@link EchoPinsSavedData}, reload from the produced
     * tag, and confirm the repository and its indexes come back intact.
     */
    @GameTest(template = TEMPLATE)
    public static void savedDataRoundTripRebuildsRepositoryAndIndex(GameTestHelper helper) {
        EchoPinsSavedData data = EchoPinsSavedData.empty();
        EchoPin near = samplePin(
                new PositionAnchor(DimensionId.parse("minecraft:overworld"), new WorldPos(10, 64, 10)),
                Visibility.PUBLIC, Set.of(), EchoPin.NEVER_EXPIRES);
        EchoPin far = samplePin(
                new PositionAnchor(DimensionId.parse("minecraft:overworld"), new WorldPos(5000, 64, 5000)),
                Visibility.PUBLIC, Set.of(), EchoPin.NEVER_EXPIRES);
        data.pins().save(near);
        data.pins().save(far);
        data.readState().markRead(near.authorUuid(), near.id());

        CompoundTag saved = data.save(new CompoundTag());
        EchoPinsSavedData reloaded = EchoPinsSavedData.fromTag(saved);

        if (reloaded.pins().totalCount() != 2) {
            helper.fail("Expected 2 pins after reload, got " + reloaded.pins().totalCount());
        }
        if (reloaded.pins().find(near.id()).isEmpty()) {
            helper.fail("The nearby pin did not survive the save/load cycle");
        }
        if (!reloaded.readState().isRead(near.authorUuid(), near.id())) {
            helper.fail("Read state did not survive the save/load cycle");
        }

        // The spatial index is rebuilt on load rather than persisted, so a proximity query is the
        // real proof that reloading produced a usable repository and not just a bag of pins.
        var nearby = reloaded.pins().findNearby(
                DimensionId.parse("minecraft:overworld"), new WorldPos(0, 64, 0), 48.0D);
        if (nearby.size() != 1 || !nearby.get(0).id().equals(near.id())) {
            helper.fail("Spatial index was not rebuilt correctly on load; got " + nearby.size() + " result(s)");
        }
        helper.succeed();
    }

    /**
     * The dimension of a live level must map onto a {@link DimensionId} that matches what the
     * codec writes, otherwise pins would be stored against a key that never matches at lookup.
     */
    @GameTest(template = TEMPLATE)
    public static void liveLevelMapsToDimensionId(GameTestHelper helper) {
        ResourceLocation location = helper.getLevel().dimension().location();
        DimensionId dimension = DimensionId.of(location.getNamespace(), location.getPath());

        if (!dimension.toString().equals(location.toString())) {
            helper.fail("Dimension mapping lost information: " + location + " -> " + dimension);
        }
        // Must survive the same parse the network and NBT layers perform.
        if (!DimensionId.parse(dimension.toString()).equals(dimension)) {
            helper.fail("Dimension id did not survive a parse round trip");
        }
        helper.succeed();
    }

    private static void assertEquals(GameTestHelper helper, EchoPin expected, EchoPin actual,
                                     String what) {
        if (!expected.equals(actual)) {
            helper.fail(what + " differed.\n  expected: " + expected + "\n  actual:   " + actual);
        }
    }
}
