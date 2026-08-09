package dev.echopins.neoforge.config;

import dev.echopins.client.ClientSettings;

/**
 * Reads {@link ClientSettings} from NeoForge's config system.
 *
 * <p>Values are read on every call rather than cached, so editing the file takes effect without
 * rebuilding anything.
 */
public final class NeoForgeClientSettings implements ClientSettings {

    public static final NeoForgeClientSettings INSTANCE = new NeoForgeClientSettings();

    private NeoForgeClientSettings() {
    }

    @Override
    public boolean showMarkers() {
        return EchoPinsClientConfig.SHOW_MARKERS.get();
    }

    @Override
    public double markerOpacity() {
        return EchoPinsClientConfig.MARKER_OPACITY.get();
    }

    @Override
    public double markerScale() {
        return EchoPinsClientConfig.MARKER_SCALE.get();
    }

    @Override
    public double markerRenderDistance() {
        return EchoPinsClientConfig.MARKER_RENDER_DISTANCE.get();
    }

    @Override
    public int maxRenderedMarkers() {
        return EchoPinsClientConfig.MAX_RENDERED_MARKERS.get();
    }

    @Override
    public OcclusionMode occlusionMode() {
        return EchoPinsClientConfig.OCCLUSION_MODE.get();
    }

    @Override
    public boolean showLabels() {
        return EchoPinsClientConfig.SHOW_LABELS.get();
    }

    @Override
    public HudPosition hudPosition() {
        return EchoPinsClientConfig.HUD_POSITION.get();
    }

    @Override
    public int hudOffsetX() {
        return EchoPinsClientConfig.HUD_OFFSET_X.get();
    }

    @Override
    public int hudOffsetY() {
        return EchoPinsClientConfig.HUD_OFFSET_Y.get();
    }

    @Override
    public boolean autoPlayNearby() {
        return EchoPinsClientConfig.AUTO_PLAY_NEARBY.get();
    }

    @Override
    public boolean notificationSounds() {
        return EchoPinsClientConfig.NOTIFICATION_SOUNDS.get();
    }

    @Override
    public boolean recordingCues() {
        return EchoPinsClientConfig.RECORDING_CUES.get();
    }

    @Override
    public boolean reduceMotion() {
        return EchoPinsClientConfig.REDUCE_MOTION.get();
    }

    @Override
    public boolean showCaptions() {
        return EchoPinsClientConfig.SHOW_CAPTIONS.get();
    }

    @Override
    public boolean highContrastRecordingIndicator() {
        return EchoPinsClientConfig.HIGH_CONTRAST_RECORDING_INDICATOR.get();
    }
}
