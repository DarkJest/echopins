package dev.echopins.infrastructure.persistence;

import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinId;
import dev.echopins.domain.repository.InMemoryPinRepository;
import dev.echopins.domain.repository.InMemoryReadStateRepository;
import dev.echopins.infrastructure.persistence.migration.DataMigrationRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * World-level persistence for every pin and all read state.
 *
 * <p>Attached to the Overworld's data storage rather than to each dimension. Pins carry their own
 * dimension inside their anchor, so a single global store lets the inbox and the admin commands
 * answer questions across dimensions without loading every level.
 *
 * <p>Audio never lives here. Only the {@code audioId} handle is persisted; the bytes sit in the
 * audio store, which keeps this NBT file small enough to write on every autosave.
 */
public final class EchoPinsSavedData extends SavedData {

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins/Persistence");

    /** File name inside {@code <world>/data}. */
    public static final String DATA_NAME = "echopins";

    /** Current schema version. Bump together with a registered migration. */
    public static final int DATA_VERSION = 1;

    private static final String KEY_DATA_VERSION = "dataVersion";
    private static final String KEY_PINS = "pins";
    private static final String KEY_READ_STATE = "readState";
    private static final String KEY_PLAYER = "player";
    private static final String KEY_READ_PINS = "read";

    private static final DataMigrationRegistry MIGRATIONS = new DataMigrationRegistry(DATA_VERSION);
    // No migrations yet: v1 is the first released schema. New ones are registered here, and the
    // registry rejects any gap in the chain at startup rather than at load time.

    private final PinStore pins = new PinStore();
    private final ReadStateStore readState = new ReadStateStore();

    private EchoPinsSavedData() {
    }

    public static SavedData.Factory<EchoPinsSavedData> factory() {
        // The three-argument form is the vanilla one. NeoForge adds a two-argument overload with a
        // nullable data-fixer type, but Fabric does not, so the shared code uses what both have.
        return new SavedData.Factory<>(EchoPinsSavedData::new, EchoPinsSavedData::load,
                DataFixTypes.LEVEL);
    }

    /** Fetches (or creates) the global store, which always lives on the Overworld. */
    public static EchoPinsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public InMemoryPinRepository pins() {
        return pins;
    }

    public InMemoryReadStateRepository readState() {
        return readState;
    }

    private static EchoPinsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EchoPinsSavedData data = new EchoPinsSavedData();
        int fileVersion = tag.contains(KEY_DATA_VERSION, Tag.TAG_INT)
                ? tag.getInt(KEY_DATA_VERSION)
                : DATA_VERSION;

        if (fileVersion > DATA_VERSION) {
            // Refusing here would leave the player with an empty world and no explanation, and
            // silently continuing would drop whatever the newer version added. Loading what we
            // understand while shouting about it is the least-bad option, and the pins we cannot
            // parse are skipped individually below.
            LOGGER.error("EchoPins data was written by a newer version (schema v{} > v{}). "
                            + "Pins that this build cannot read will be skipped and will be lost if the "
                            + "world is saved. Back up your world and update EchoPins.",
                    fileVersion, DATA_VERSION);
        }

        int loaded = 0;
        int skipped = 0;
        int migrated = 0;

        ListTag pinList = tag.getList(KEY_PINS, Tag.TAG_COMPOUND);
        for (int i = 0; i < pinList.size(); i++) {
            CompoundTag pinTag = pinList.getCompound(i);
            int pinVersion = PinNbtCodec.schemaVersionOf(pinTag);
            try {
                if (pinVersion != DATA_VERSION) {
                    pinTag = MIGRATIONS.migrateToCurrent(pinTag, pinVersion);
                    migrated++;
                }
            } catch (DataMigrationRegistry.UnsupportedDataVersionException e) {
                LOGGER.warn("Skipping a pin that cannot be migrated: {}", e.getMessage());
                skipped++;
                continue;
            }

            Optional<EchoPin> pin = PinNbtCodec.decode(pinTag);
            if (pin.isEmpty()) {
                skipped++;
                continue;
            }
            data.pins.insertLoaded(pin.get());
            loaded++;
        }

        int readEntries = 0;
        ListTag readList = tag.getList(KEY_READ_STATE, Tag.TAG_COMPOUND);
        for (int i = 0; i < readList.size(); i++) {
            CompoundTag entry = readList.getCompound(i);
            if (!entry.hasUUID(KEY_PLAYER)) {
                continue;
            }
            UUID player = entry.getUUID(KEY_PLAYER);
            Set<PinId> read = new LinkedHashSet<>();
            ListTag ids = entry.getList(KEY_READ_PINS, Tag.TAG_INT_ARRAY);
            for (Tag element : ids) {
                try {
                    PinId pinId = PinId.of(NbtUtils.loadUUID(element));
                    // Drop read marks for pins that no longer exist, so this set cannot grow
                    // forever across restarts.
                    if (data.pins.find(pinId).isPresent()) {
                        read.add(pinId);
                    }
                } catch (RuntimeException ignored) {
                    // A malformed id is simply not remembered as read.
                }
            }
            if (!read.isEmpty()) {
                data.readState.rawState().put(player, read);
                readEntries += read.size();
            }
        }

        if (skipped > 0) {
            LOGGER.warn("Loaded {} EchoPin(s); skipped {} unreadable entr(ies)", loaded, skipped);
        } else {
            LOGGER.info("Loaded {} EchoPin(s) and {} read mark(s)", loaded, readEntries);
        }
        if (migrated > 0) {
            LOGGER.info("Migrated {} pin(s) to schema v{}", migrated, DATA_VERSION);
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(KEY_DATA_VERSION, DATA_VERSION);

        ListTag pinList = new ListTag();
        for (EchoPin pin : pins.all()) {
            pinList.add(PinNbtCodec.encode(pin, DATA_VERSION));
        }
        tag.put(KEY_PINS, pinList);

        ListTag readList = new ListTag();
        for (Map.Entry<UUID, Set<PinId>> entry : readState.rawState().entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(KEY_PLAYER, entry.getKey());
            ListTag ids = new ListTag();
            for (PinId pinId : entry.getValue()) {
                ids.add(NbtUtils.createUUID(pinId.value()));
            }
            playerTag.put(KEY_READ_PINS, ids);
            readList.add(playerTag);
        }
        tag.put(KEY_READ_STATE, readList);
        return tag;
    }

    /** Repository view that flags the world data dirty on every mutation. */
    private final class PinStore extends InMemoryPinRepository {
        @Override
        public void markDirty() {
            EchoPinsSavedData.this.setDirty();
        }

        /**
         * Inserts without dirtying. The protected hook is only reachable from a subclass body,
         * so it is re-exposed here for the loader.
         */
        void insertLoaded(EchoPin pin) {
            loadPin(pin);
        }
    }

    private final class ReadStateStore extends InMemoryReadStateRepository {
        @Override
        public void markDirty() {
            EchoPinsSavedData.this.setDirty();
        }

        @Override
        protected Map<UUID, Set<PinId>> rawState() {
            return super.rawState();
        }
    }
}
