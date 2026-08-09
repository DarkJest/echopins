package dev.echopins.client;

/**
 * Default values and validation bounds for every client option.
 *
 * <p>Single source of truth shared by both loaders. NeoForge stores configuration through its own
 * config system and Fabric through a JSON file; keeping the numbers here means the two can never
 * drift into disagreeing about what "default" means, which is exactly the kind of difference that
 * would only surface as "it behaves differently on Fabric".
 */
public final class EchoPinsDefaults {

    public static final boolean SHOW_MARKERS = true;

    public static final double MARKER_OPACITY = 0.85D;
    public static final double MARKER_OPACITY_MIN = 0.05D;
    public static final double MARKER_OPACITY_MAX = 1.0D;

    public static final double MARKER_SCALE = 1.0D;
    public static final double MARKER_SCALE_MIN = 0.25D;
    public static final double MARKER_SCALE_MAX = 2.5D;

    public static final double MARKER_RENDER_DISTANCE = 48.0D;
    public static final double MARKER_RENDER_DISTANCE_MIN = 4.0D;
    public static final double MARKER_RENDER_DISTANCE_MAX = 256.0D;

    public static final int MAX_RENDERED_MARKERS = 32;
    public static final int MAX_RENDERED_MARKERS_MIN = 1;
    public static final int MAX_RENDERED_MARKERS_MAX = 256;

    public static final boolean SHOW_LABELS = true;

    public static final int HUD_OFFSET_MIN = -400;
    public static final int HUD_OFFSET_MAX = 400;

    public static final boolean AUTO_PLAY_NEARBY = false;
    public static final boolean NOTIFICATION_SOUNDS = true;
    public static final boolean RECORDING_CUES = true;

    public static final boolean REDUCE_MOTION = false;
    public static final boolean SHOW_CAPTIONS = true;
    public static final boolean HIGH_CONTRAST_RECORDING_INDICATOR = false;

    private EchoPinsDefaults() {
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
