package dev.echopins.neoforge.config;

import dev.echopins.client.ClientSettings.HudPosition;
import dev.echopins.client.ClientSettings.OcclusionMode;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side presentation and accessibility preferences.
 *
 * <p>Nothing here can loosen a server rule. {@code markerRenderDistance} in particular is
 * clamped against the server's discovery radius at render time - a client that sets it to the
 * maximum still only ever sees the pins the server chose to send.
 */
public final class EchoPinsClientConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue SHOW_MARKERS;
    public static final ForgeConfigSpec.DoubleValue MARKER_OPACITY;
    public static final ForgeConfigSpec.DoubleValue MARKER_SCALE;
    public static final ForgeConfigSpec.DoubleValue MARKER_RENDER_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_RENDERED_MARKERS;
    public static final ForgeConfigSpec.EnumValue<OcclusionMode> OCCLUSION_MODE;
    public static final ForgeConfigSpec.BooleanValue SHOW_LABELS;

    public static final ForgeConfigSpec.EnumValue<HudPosition> HUD_POSITION;
    public static final ForgeConfigSpec.IntValue HUD_OFFSET_X;
    public static final ForgeConfigSpec.IntValue HUD_OFFSET_Y;

    public static final ForgeConfigSpec.BooleanValue AUTO_PLAY_NEARBY;
    public static final ForgeConfigSpec.BooleanValue NOTIFICATION_SOUNDS;
    public static final ForgeConfigSpec.BooleanValue RECORDING_CUES;

    public static final ForgeConfigSpec.BooleanValue REDUCE_MOTION;
    public static final ForgeConfigSpec.BooleanValue SHOW_CAPTIONS;
    public static final ForgeConfigSpec.BooleanValue HIGH_CONTRAST_RECORDING_INDICATOR;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.comment("How EchoPin markers are drawn in the world.").push("markers");
        SHOW_MARKERS = BUILDER
                .comment("Draw EchoPin markers in the world.")
                .translation("echopins.config.show_markers")
                .define("showMarkers", true);
        MARKER_OPACITY = BUILDER
                .comment("Marker opacity, from faint to fully opaque.")
                .translation("echopins.config.marker_opacity")
                .defineInRange("markerOpacity", 0.85D, 0.05D, 1.0D);
        MARKER_SCALE = BUILDER
                .comment("Marker size multiplier.")
                .translation("echopins.config.marker_scale")
                .defineInRange("markerScale", 1.0D, 0.25D, 2.5D);
        MARKER_RENDER_DISTANCE = BUILDER
                .comment("Stop drawing markers past this distance in blocks.",
                        "Always clamped to the server's discovery radius, so raising it cannot",
                        "reveal pins the server did not send.")
                .translation("echopins.config.marker_render_distance")
                .defineInRange("markerRenderDistance", 48.0D, 4.0D, 256.0D);
        MAX_RENDERED_MARKERS = BUILDER
                .comment("Hard cap on markers drawn in a single frame, nearest first.")
                .translation("echopins.config.max_rendered_markers")
                .defineInRange("maxRenderedMarkers", 32, 1, 256);
        OCCLUSION_MODE = BUILDER
                .comment("Whether markers show through terrain.",
                        "SHOW_THROUGH_WALLS_NEARBY is the conservative default: a pin is visible",
                        "through a wall only once you are close enough to interact with it.")
                .translation("echopins.config.occlusion_mode")
                .defineEnum("occlusionMode", OcclusionMode.SHOW_THROUGH_WALLS_NEARBY);
        SHOW_LABELS = BUILDER
                .comment("Show author, age and duration next to a nearby marker.")
                .translation("echopins.config.show_labels")
                .define("showLabels", true);
        BUILDER.pop();

        BUILDER.comment("Recording heads-up display.").push("hud");
        HUD_POSITION = BUILDER
                .comment("Where the recording indicator appears.")
                .translation("echopins.config.hud_position")
                .defineEnum("hudPosition", HudPosition.TOP_CENTER);
        HUD_OFFSET_X = BUILDER
                .comment("Horizontal nudge in pixels, applied after GUI scaling.")
                .translation("echopins.config.hud_offset_x")
                .defineInRange("hudOffsetX", 0, -400, 400);
        HUD_OFFSET_Y = BUILDER
                .comment("Vertical nudge in pixels, applied after GUI scaling.")
                .translation("echopins.config.hud_offset_y")
                .defineInRange("hudOffsetY", 0, -400, 400);
        BUILDER.pop();

        BUILDER.comment("Playback behaviour.").push("playback");
        AUTO_PLAY_NEARBY = BUILDER
                .comment("Automatically play an unheard pin when you walk up to it.",
                        "Off by default: unexpected audio is disruptive, and playing a message",
                        "should be the listener's choice.")
                .translation("echopins.config.auto_play_nearby")
                .define("autoPlayNearby", false);
        NOTIFICATION_SOUNDS = BUILDER
                .comment("Play a soft cue when a new EchoPin comes into range.")
                .translation("echopins.config.notification_sounds")
                .define("notificationSounds", true);
        RECORDING_CUES = BUILDER
                .comment("Play the short start and stop cues while recording.",
                        "The visual indicator stays regardless, so turning this off never leaves",
                        "recording state unsignalled.")
                .translation("echopins.config.recording_cues")
                .define("recordingCues", true);
        BUILDER.pop();

        BUILDER.comment("Accessibility.").push("accessibility");
        REDUCE_MOTION = BUILDER
                .comment("Disable pulsing, bobbing and fade animations.")
                .translation("echopins.config.reduce_motion")
                .define("reduceMotion", false);
        SHOW_CAPTIONS = BUILDER
                .comment("Show pin captions as on-screen subtitles during playback.")
                .translation("echopins.config.show_captions")
                .define("showCaptions", true);
        HIGH_CONTRAST_RECORDING_INDICATOR = BUILDER
                .comment("Draw the recording indicator with a high-contrast outline.")
                .translation("echopins.config.high_contrast_recording_indicator")
                .define("highContrastRecordingIndicator", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private EchoPinsClientConfig() {
    }
}
