package dev.echopins.fabric.config;

import dev.echopins.client.ClientSettings;
import dev.echopins.client.EchoPinsDefaults;

/**
 * {@link ClientSettings} backed by {@code config/echopins-client.json}.
 *
 * <p>Read once at startup and held, since these are queried every frame and Fabric offers no
 * reload event to invalidate them on.
 */
public final class FabricClientSettings implements ClientSettings {

    public static final FabricClientSettings INSTANCE = new FabricClientSettings();

    private final boolean showMarkers;
    private final double markerOpacity;
    private final double markerScale;
    private final double markerRenderDistance;
    private final int maxRenderedMarkers;
    private final OcclusionMode occlusionMode;
    private final boolean showLabels;
    private final HudPosition hudPosition;
    private final int hudOffsetX;
    private final int hudOffsetY;
    private final boolean autoPlayNearby;
    private final boolean notificationSounds;
    private final boolean recordingCues;
    private final boolean reduceMotion;
    private final boolean showCaptions;
    private final boolean highContrast;

    private FabricClientSettings() {
        FabricConfig c = FabricConfig.load(FabricConfig.Files_.CLIENT);

        showMarkers = c.getBoolean("showMarkers", EchoPinsDefaults.SHOW_MARKERS);
        markerOpacity = c.getDouble("markerOpacity", EchoPinsDefaults.MARKER_OPACITY,
                EchoPinsDefaults.MARKER_OPACITY_MIN, EchoPinsDefaults.MARKER_OPACITY_MAX);
        markerScale = c.getDouble("markerScale", EchoPinsDefaults.MARKER_SCALE,
                EchoPinsDefaults.MARKER_SCALE_MIN, EchoPinsDefaults.MARKER_SCALE_MAX);
        markerRenderDistance = c.getDouble("markerRenderDistance",
                EchoPinsDefaults.MARKER_RENDER_DISTANCE,
                EchoPinsDefaults.MARKER_RENDER_DISTANCE_MIN,
                EchoPinsDefaults.MARKER_RENDER_DISTANCE_MAX);
        maxRenderedMarkers = c.getInt("maxRenderedMarkers", EchoPinsDefaults.MAX_RENDERED_MARKERS,
                EchoPinsDefaults.MAX_RENDERED_MARKERS_MIN, EchoPinsDefaults.MAX_RENDERED_MARKERS_MAX);
        occlusionMode = c.occlusionMode("occlusionMode");
        showLabels = c.getBoolean("showLabels", EchoPinsDefaults.SHOW_LABELS);

        hudPosition = c.hudPosition("hudPosition");
        hudOffsetX = c.getInt("hudOffsetX", 0,
                EchoPinsDefaults.HUD_OFFSET_MIN, EchoPinsDefaults.HUD_OFFSET_MAX);
        hudOffsetY = c.getInt("hudOffsetY", 0,
                EchoPinsDefaults.HUD_OFFSET_MIN, EchoPinsDefaults.HUD_OFFSET_MAX);

        autoPlayNearby = c.getBoolean("autoPlayNearby", EchoPinsDefaults.AUTO_PLAY_NEARBY);
        notificationSounds = c.getBoolean("notificationSounds", EchoPinsDefaults.NOTIFICATION_SOUNDS);
        recordingCues = c.getBoolean("recordingCues", EchoPinsDefaults.RECORDING_CUES);

        reduceMotion = c.getBoolean("reduceMotion", EchoPinsDefaults.REDUCE_MOTION);
        showCaptions = c.getBoolean("showCaptions", EchoPinsDefaults.SHOW_CAPTIONS);
        highContrast = c.getBoolean("highContrastRecordingIndicator",
                EchoPinsDefaults.HIGH_CONTRAST_RECORDING_INDICATOR);

        c.flush();
    }

    @Override
    public boolean showMarkers() {
        return showMarkers;
    }

    @Override
    public double markerOpacity() {
        return markerOpacity;
    }

    @Override
    public double markerScale() {
        return markerScale;
    }

    @Override
    public double markerRenderDistance() {
        return markerRenderDistance;
    }

    @Override
    public int maxRenderedMarkers() {
        return maxRenderedMarkers;
    }

    @Override
    public OcclusionMode occlusionMode() {
        return occlusionMode;
    }

    @Override
    public boolean showLabels() {
        return showLabels;
    }

    @Override
    public HudPosition hudPosition() {
        return hudPosition;
    }

    @Override
    public int hudOffsetX() {
        return hudOffsetX;
    }

    @Override
    public int hudOffsetY() {
        return hudOffsetY;
    }

    @Override
    public boolean autoPlayNearby() {
        return autoPlayNearby;
    }

    @Override
    public boolean notificationSounds() {
        return notificationSounds;
    }

    @Override
    public boolean recordingCues() {
        return recordingCues;
    }

    @Override
    public boolean reduceMotion() {
        return reduceMotion;
    }

    @Override
    public boolean showCaptions() {
        return showCaptions;
    }

    @Override
    public boolean highContrastRecordingIndicator() {
        return highContrast;
    }
}
