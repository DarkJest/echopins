package dev.echopins.domain.repository;

import dev.echopins.domain.anchor.BlockFace;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinId;
import dev.echopins.testsupport.Pins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPinRepositoryTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID OTHER_AUTHOR = UUID.randomUUID();

    private InMemoryPinRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPinRepository();
    }

    @Test
    @DisplayName("A saved pin can be found by id")
    void saveAndFind() {
        EchoPin pin = Pins.publicPin(AUTHOR, 10, 64, 10);
        repository.save(pin);

        assertEquals(pin, repository.find(pin.id()).orElseThrow());
        assertEquals(1, repository.totalCount());
    }

    @Test
    @DisplayName("findNearby returns pins inside the radius and excludes those outside it")
    void findNearbyRespectsRadius() {
        EchoPin near = Pins.publicPin(AUTHOR, 5, 64, 5);
        EchoPin far = Pins.publicPin(AUTHOR, 500, 64, 500);
        repository.save(near);
        repository.save(far);

        List<EchoPin> found = repository.findNearby(Pins.OVERWORLD, new WorldPos(0, 64, 0), 48);

        assertEquals(1, found.size());
        assertEquals(near.id(), found.get(0).id());
    }

    @Test
    @DisplayName("The radius check is a true sphere, not the chunk bounding box")
    void findNearbyFiltersExactly() {
        // Sits inside the searched chunk span but outside the requested radius.
        EchoPin justOutside = Pins.publicPin(AUTHOR, 40, 64, 40);
        repository.save(justOutside);

        assertTrue(repository.findNearby(Pins.OVERWORLD, new WorldPos(0, 64, 0), 50).isEmpty(),
                "distance is ~56.6, so a radius of 50 must exclude it");
        assertEquals(1, repository.findNearby(Pins.OVERWORLD, new WorldPos(0, 64, 0), 60).size());
    }

    @Test
    @DisplayName("Vertical distance counts towards the radius")
    void findNearbyConsidersHeight() {
        EchoPin high = Pins.publicPin(AUTHOR, 0, 300, 0);
        repository.save(high);

        assertTrue(repository.findNearby(Pins.OVERWORLD, new WorldPos(0, 64, 0), 100).isEmpty());
        assertEquals(1, repository.findNearby(Pins.OVERWORLD, new WorldPos(0, 64, 0), 250).size());
    }

    @Test
    @DisplayName("A search in one dimension never returns pins from another")
    void dimensionsAreIsolated() {
        repository.save(Pins.publicPin(AUTHOR, Pins.OVERWORLD, 0, 64, 0));
        repository.save(Pins.publicPin(AUTHOR, Pins.NETHER, 0, 64, 0));

        assertEquals(1, repository.findNearby(Pins.OVERWORLD, new WorldPos(0, 64, 0), 64).size());
        assertEquals(1, repository.findNearby(Pins.NETHER, new WorldPos(0, 64, 0), 64).size());
        assertEquals(1, repository.countInDimension(Pins.OVERWORLD));
        assertEquals(1, repository.countInDimension(Pins.NETHER));
    }

    @Test
    @DisplayName("Negative coordinates index and query correctly")
    void handlesNegativeCoordinates() {
        EchoPin pin = Pins.publicPin(AUTHOR, -100.5, 64, -100.5);
        repository.save(pin);

        assertEquals(1, repository.findNearby(Pins.OVERWORLD, new WorldPos(-95, 64, -95), 32).size());
        assertTrue(repository.findNearby(Pins.OVERWORLD, new WorldPos(100, 64, 100), 32).isEmpty());
    }

    @Test
    @DisplayName("Removing a pin also removes it from the spatial and author indexes")
    void removeCleansIndexes() {
        EchoPin pin = Pins.publicPin(AUTHOR, 5, 64, 5);
        repository.save(pin);
        repository.remove(pin.id());

        assertTrue(repository.findNearby(Pins.OVERWORLD, new WorldPos(0, 64, 0), 64).isEmpty());
        assertEquals(0, repository.countByAuthor(AUTHOR));
        assertEquals(0, repository.countInDimension(Pins.OVERWORLD));
        assertEquals(0, repository.spatialIndex().bucketCount(), "empty buckets must be pruned");
    }

    @Test
    @DisplayName("Removing a pin twice is safe")
    void removeIsIdempotent() {
        EchoPin pin = Pins.publicPin(AUTHOR, 5, 64, 5);
        repository.save(pin);

        assertTrue(repository.remove(pin.id()).isPresent());
        assertFalse(repository.remove(pin.id()).isPresent());
        assertFalse(repository.remove(PinId.random()).isPresent());
    }

    @Test
    @DisplayName("Re-saving a pin at a new location does not leave a ghost at the old one")
    void resaveMovesIndexEntry() {
        EchoPin pin = Pins.blockPin(AUTHOR, 5, 64, 5, BlockFace.UP);
        repository.save(pin);

        EchoPin moved = Pins.pin(AUTHOR,
                new dev.echopins.domain.anchor.BlockAnchor(Pins.OVERWORLD, 900, 64, 900, BlockFace.UP),
                pin.visibility(), pin.recipients(), pin.expiresAt());
        // Same id, different anchor.
        EchoPin sameIdMoved = new EchoPin(pin.id(), pin.author(), moved.anchor(), pin.createdAt(),
                pin.visibility(), pin.recipients(), pin.caption(), pin.audio(), pin.expiresAt());
        repository.save(sameIdMoved);

        assertEquals(1, repository.totalCount());
        assertTrue(repository.findNearby(Pins.OVERWORLD, new WorldPos(5, 64, 5), 32).isEmpty(),
                "the old index entry must be gone");
        assertEquals(1, repository.findNearby(Pins.OVERWORLD, new WorldPos(900, 64, 900), 32).size());
    }

    @Test
    @DisplayName("Pins are indexed by author")
    void authorIndex() {
        repository.save(Pins.publicPin(AUTHOR, 0, 64, 0));
        repository.save(Pins.publicPin(AUTHOR, 1, 64, 1));
        repository.save(Pins.publicPin(OTHER_AUTHOR, 2, 64, 2));

        assertEquals(2, repository.countByAuthor(AUTHOR));
        assertEquals(1, repository.countByAuthor(OTHER_AUTHOR));
        assertEquals(2, repository.findByAuthor(AUTHOR).size());
        assertEquals(0, repository.countByAuthor(UUID.randomUUID()));
    }

    @Test
    @DisplayName("findExpired returns only expired pins and honours its limit")
    void expirySweep() {
        repository.save(Pins.expiringPin(AUTHOR, 1_000L));
        repository.save(Pins.expiringPin(AUTHOR, 2_000L));
        repository.save(Pins.expiringPin(AUTHOR, 50_000L));
        repository.save(Pins.publicPin(AUTHOR, 0, 64, 0));

        assertTrue(repository.findExpired(500L, 10).isEmpty(), "nothing has expired yet");
        assertEquals(2, repository.findExpired(5_000L, 10).size());
        assertEquals(1, repository.findExpired(5_000L, 1).size(), "limit must be respected");
        assertEquals(3, repository.findExpired(60_000L, 10).size());
    }

    @Test
    @DisplayName("A permanent pin never expires")
    void permanentPinsNeverExpire() {
        EchoPin permanent = Pins.publicPin(AUTHOR, 0, 64, 0);
        assertTrue(permanent.isPermanent());
        assertFalse(permanent.isExpiredAt(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("Thousands of pins stay queryable without scanning them all")
    void scalesToManyPins() {
        for (int i = 0; i < 5_000; i++) {
            repository.save(Pins.publicPin(AUTHOR, i * 20.0D, 64, 0));
        }
        assertEquals(5_000, repository.totalCount());

        List<EchoPin> nearby = repository.findNearby(Pins.OVERWORLD, new WorldPos(0, 64, 0), 48);
        assertEquals(3, nearby.size(), "only pins at x=0, 20 and 40 are within 48 blocks");
    }
}
