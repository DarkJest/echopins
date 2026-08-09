package dev.echopins.infrastructure.persistence.migration;

import net.minecraft.nbt.CompoundTag;

/**
 * Upgrades a single persisted pin from one schema version to the next.
 *
 * <p>The framework exists from v1 even though there is nothing to migrate yet. Adding it later
 * would mean the first release's worlds have no version marker to migrate <em>from</em>, so
 * every future change would have to guess at the shape of old data. Writing a version on every
 * pin now costs four bytes and keeps that door open.
 *
 * <p>Migrations must be pure and total: given any tag previously written at
 * {@link #fromVersion()}, produce a tag valid at {@code fromVersion() + 1}. They must not throw
 * on unexpected input - a migration that cannot make sense of a pin should leave it in a state
 * the loader will reject and log, so one bad pin does not abort loading the world.
 */
public interface DataMigration {

    /** The version this migration reads. It produces {@code fromVersion() + 1}. */
    int fromVersion();

    /** Human-readable description, logged when the migration runs. */
    String description();

    /**
     * @param pin the pin tag at {@link #fromVersion()}; may be mutated in place
     * @return the migrated tag
     */
    CompoundTag migrate(CompoundTag pin);
}
