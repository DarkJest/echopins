package dev.echopins.client;

/**
 * Client-side presentation and accessibility preferences, as the rendering code sees them.
 *
 * <p>An interface because the loaders store configuration differently: Forge and NeoForge use
 * their config system, while Fabric uses a JSON file. Everything that draws reads through here,
 * so neither the HUDs, the marker renderer nor the screens contain a loader-specific reference.
 *
 * <p>Defaults live in {@link EchoPinsDefaults} so all loaders agree on them without any one
 * being the source of truth.
 */
public interface ClientSettings {

    /** Where the recording indicator is drawn. */
    enum HudPosition {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }

    /** How markers behave when a solid block is in the way. */
    enum OcclusionMode {
        /** Never drawn through terrain. */
        ALWAYS_OCCLUDE,
        /** Drawn through terrain only when close, so a pin behind a chest is still findable. */
        SHOW_THROUGH_WALLS_NEARBY,
        /** Always drawn. */
        NEVER_OCCLUDE
    }

    boolean showMarkers();

    double markerOpacity();

    double markerScale();

    double markerRenderDistance();

    int maxRenderedMarkers();

    OcclusionMode occlusionMode();

    boolean showLabels();

    HudPosition hudPosition();

    int hudOffsetX();

    int hudOffsetY();

    boolean autoPlayNearby();

    boolean notificationSounds();

    boolean recordingCues();

    boolean reduceMotion();

    boolean showCaptions();

    boolean highContrastRecordingIndicator();

    /**
     * The settings currently in force.
     *
     * <p>Falls back to the defaults until a loader installs its own implementation, so rendering
     * can never fail merely because configuration has not loaded yet.
     */
    final class Holder {

        private static volatile ClientSettings current = defaults();

        private Holder() {
        }

        public static void install(ClientSettings settings) {
            current = settings;
        }

        public static ClientSettings get() {
            return current;
        }

        public static ClientSettings defaults() {
            return new ClientSettings() {
                @Override
                public boolean showMarkers() {
                    return EchoPinsDefaults.SHOW_MARKERS;
                }

                @Override
                public double markerOpacity() {
                    return EchoPinsDefaults.MARKER_OPACITY;
                }

                @Override
                public double markerScale() {
                    return EchoPinsDefaults.MARKER_SCALE;
                }

                @Override
                public double markerRenderDistance() {
                    return EchoPinsDefaults.MARKER_RENDER_DISTANCE;
                }

                @Override
                public int maxRenderedMarkers() {
                    return EchoPinsDefaults.MAX_RENDERED_MARKERS;
                }

                @Override
                public OcclusionMode occlusionMode() {
                    return OcclusionMode.SHOW_THROUGH_WALLS_NEARBY;
                }

                @Override
                public boolean showLabels() {
                    return EchoPinsDefaults.SHOW_LABELS;
                }

                @Override
                public HudPosition hudPosition() {
                    return HudPosition.TOP_CENTER;
                }

                @Override
                public int hudOffsetX() {
                    return 0;
                }

                @Override
                public int hudOffsetY() {
                    return 0;
                }

                @Override
                public boolean autoPlayNearby() {
                    return EchoPinsDefaults.AUTO_PLAY_NEARBY;
                }

                @Override
                public boolean notificationSounds() {
                    return EchoPinsDefaults.NOTIFICATION_SOUNDS;
                }

                @Override
                public boolean recordingCues() {
                    return EchoPinsDefaults.RECORDING_CUES;
                }

                @Override
                public boolean reduceMotion() {
                    return EchoPinsDefaults.REDUCE_MOTION;
                }

                @Override
                public boolean showCaptions() {
                    return EchoPinsDefaults.SHOW_CAPTIONS;
                }

                @Override
                public boolean highContrastRecordingIndicator() {
                    return EchoPinsDefaults.HIGH_CONTRAST_RECORDING_INDICATOR;
                }
            };
        }
    }
}
