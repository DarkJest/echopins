package dev.echopins.infrastructure.persistence.migration;

import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Applies the chain of {@link DataMigration}s needed to bring a persisted pin up to the current
 * schema version.
 *
 * <p>Registered migrations must form an unbroken chain; a gap is a programming error and is
 * rejected at registration rather than discovered when someone's world fails to load.
 */
public final class DataMigrationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins/Migrations");

    private final Map<Integer, DataMigration> byFromVersion = new HashMap<>();
    private final int currentVersion;

    public DataMigrationRegistry(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public DataMigrationRegistry register(DataMigration migration) {
        int from = migration.fromVersion();
        if (from < 1 || from >= currentVersion) {
            throw new IllegalArgumentException(
                    "Migration from version " + from + " is outside 1.." + (currentVersion - 1));
        }
        DataMigration existing = byFromVersion.put(from, migration);
        if (existing != null) {
            throw new IllegalStateException("Two migrations registered for version " + from);
        }
        return this;
    }

    public int currentVersion() {
        return currentVersion;
    }

    /**
     * Migrates a tag up to {@link #currentVersion()}.
     *
     * @param storedVersion the version the tag was written at
     * @return the migrated tag
     * @throws UnsupportedDataVersionException if the version is from the future or a migration is
     *                                         missing
     */
    public CompoundTag migrateToCurrent(CompoundTag tag, int storedVersion) {
        if (storedVersion == currentVersion) {
            return tag;
        }
        if (storedVersion > currentVersion) {
            // Loading a newer world with an older mod. Guessing here would silently drop the
            // fields the newer version added, so refuse instead.
            throw new UnsupportedDataVersionException(
                    "Data was written by a newer EchoPins (schema v" + storedVersion
                            + ", this build understands v" + currentVersion + ")");
        }
        if (storedVersion < 1) {
            throw new UnsupportedDataVersionException("Invalid schema version: " + storedVersion);
        }

        CompoundTag current = tag;
        for (int version = storedVersion; version < currentVersion; version++) {
            DataMigration migration = byFromVersion.get(version);
            if (migration == null) {
                throw new UnsupportedDataVersionException(
                        "No migration registered from schema v" + version);
            }
            LOGGER.info("Migrating EchoPins data v{} -> v{}: {}",
                    version, version + 1, migration.description());
            current = migration.migrate(current);
        }
        return current;
    }

    /** Thrown when data cannot be brought to the current schema. */
    public static final class UnsupportedDataVersionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UnsupportedDataVersionException(String message) {
            super(message);
        }
    }
}
