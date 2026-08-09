package dev.echopins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod identity, shared by every loader.
 *
 * <p>Holds no loader types on purpose: the mod id is used to build resource locations, payload
 * ids and the Simple Voice Chat plugin id, all of which live in common code. Each loader has its
 * own entry point that does the registering.
 */
public final class EchoPins {

    public static final String MOD_ID = "echopins";

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins");

    private EchoPins() {
    }

    public static Logger logger() {
        return LOGGER;
    }
}
