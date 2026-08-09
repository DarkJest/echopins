package dev.echopins.fabric.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.echopins.application.EchoPinsServerDefaults;
import dev.echopins.client.ClientSettings;
import dev.echopins.client.EchoPinsDefaults;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for the Fabric build.
 *
 * <p>Fabric has no configuration system of its own, so this is a small JSON file read through
 * Gson, which Minecraft already ships. Every default comes from the shared tables
 * ({@link EchoPinsServerDefaults}, {@link EchoPinsDefaults}) rather than being written out again
 * here, so the Fabric and NeoForge builds cannot drift into behaving differently out of the box.
 *
 * <p>Unknown keys are ignored and missing ones fall back to the default, so a file written by an
 * older or newer version still loads. Values are clamped to the same bounds the NeoForge config
 * enforces, because a hand-edited file is exactly as untrustworthy as a hand-edited TOML.
 */
public final class FabricConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final JsonObject root;
    private final Path file;

    private FabricConfig(JsonObject root, Path file) {
        this.root = root;
        this.file = file;
    }

    /**
     * Loads a config file, creating it with defaults if absent.
     *
     * <p>A corrupt file is reported and replaced in memory by the defaults rather than failing the
     * load: a broken config should not stop the mod, and it should not silently overwrite whatever
     * the admin was trying to write either.
     */
    public static FabricConfig load(String name) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(name + ".json");
        JsonObject parsed = new JsonObject();
        if (Files.isRegularFile(path)) {
            try {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                parsed = JsonParser.parseString(text).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Could not read {}; falling back to defaults for this session. "
                        + "The file has been left untouched.", path, e);
                return new FabricConfig(new JsonObject(), null);
            }
        }
        FabricConfig config = new FabricConfig(parsed, path);
        config.writeIfChanged();
        return config;
    }

    /** Writes the file back so new options appear with their defaults and a fresh install has one. */
    private void writeIfChanged() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            String rendered = GSON.toJson(root) + "\n";
            if (!Files.exists(file) || !Files.readString(file, StandardCharsets.UTF_8).equals(rendered)) {
                Files.writeString(file, rendered, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", file, e);
        }
    }

    /** Records a default into the in-memory document so it lands in the written file. */
    private void remember(String key, Object value) {
        if (!root.has(key)) {
            if (value instanceof Boolean b) {
                root.addProperty(key, b);
            } else if (value instanceof Number n) {
                root.addProperty(key, n);
            } else {
                root.addProperty(key, String.valueOf(value));
            }
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        remember(key, fallback);
        try {
            return root.has(key) ? root.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public int getInt(String key, int fallback, int min, int max) {
        remember(key, fallback);
        try {
            return EchoPinsDefaults.clamp(root.has(key) ? root.get(key).getAsInt() : fallback, min, max);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public long getLong(String key, long fallback, long min, long max) {
        remember(key, fallback);
        try {
            long value = root.has(key) ? root.get(key).getAsLong() : fallback;
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public double getDouble(String key, double fallback, double min, double max) {
        remember(key, fallback);
        try {
            return EchoPinsDefaults.clamp(root.has(key) ? root.get(key).getAsDouble() : fallback, min, max);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public <E extends Enum<E>> E getEnum(String key, E fallback, Class<E> type) {
        remember(key, fallback.name());
        try {
            if (!root.has(key)) {
                return fallback;
            }
            return Enum.valueOf(type, root.get(key).getAsString());
        } catch (RuntimeException e) {
            LOGGER.warn("Unrecognised value for {} in the EchoPins config; using {}", key, fallback);
            return fallback;
        }
    }

    /** Persists any defaults that were filled in while reading. */
    public void flush() {
        writeIfChanged();
    }

    /** The two files EchoPins uses, named to mirror the NeoForge build's config files. */
    public static final class Files_ {
        public static final String SERVER = "echopins-server";
        public static final String CLIENT = "echopins-client";

        private Files_() {
        }
    }

    /** Makes {@link ClientSettings} available for the enum lookups above without a cast. */
    public ClientSettings.HudPosition hudPosition(String key) {
        return getEnum(key, ClientSettings.HudPosition.TOP_CENTER, ClientSettings.HudPosition.class);
    }

    public ClientSettings.OcclusionMode occlusionMode(String key) {
        return getEnum(key, ClientSettings.OcclusionMode.SHOW_THROUGH_WALLS_NEARBY,
                ClientSettings.OcclusionMode.class);
    }
}
