package dev.echopins.integration.voicechat;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import dev.echopins.application.voice.VoiceBackend;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.audio.AudioConstants;
import dev.echopins.domain.audio.VoiceRecording;
import dev.echopins.infrastructure.concurrent.EchoPinsExecutors;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Adapts Simple Voice Chat to {@link VoiceBackend}.
 *
 * <p>Two design points worth stating plainly:
 *
 * <p><b>Recording rides the existing transmission pipeline.</b> The public API exposes microphone
 * audio only as {@link MicrophonePacketEvent}, which fires when Simple Voice Chat is already
 * transmitting. There is no supported way to switch someone's microphone on, and reaching into
 * Simple Voice Chat's internals to do so would be both fragile and a privacy problem. So a
 * player in push-to-talk mode must hold their normal voice chat key while recording; the HUD
 * says so, and the server reports whether audio is actually arriving.
 *
 * <p><b>Playback re-sends the stored Opus frames untouched.</b>
 * {@code AudioChannel.send(byte[])} accepts encoded Opus directly, so a recording never has to be
 * decoded to PCM and re-encoded. That avoids a generation of quality loss and keeps playback cost
 * close to zero.
 */
public final class SimpleVoiceChatBackend implements VoiceBackend {

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins/Voice");

    /** Must be 1-16 characters of lowercase a-z and underscore, per the API. */
    public static final String VOLUME_CATEGORY_ID = "echopins";

    public static final SimpleVoiceChatBackend INSTANCE = new SimpleVoiceChatBackend();

    private volatile VoicechatApi api;
    private volatile VoicechatServerApi serverApi;
    private volatile MicrophoneCapture capture;
    private volatile java.util.function.Consumer<UUID> voiceDisconnectListener;

    private final Map<UUID, ChannelPlayback> activePlaybacks = new ConcurrentHashMap<>();

    private SimpleVoiceChatBackend() {
    }

    void onApiAvailable(VoicechatApi api) {
        this.api = api;
        LOGGER.info("Simple Voice Chat API detected");
    }

    void onVoiceServerStarted(VoicechatServerStartedEvent event) {
        this.serverApi = event.getVoicechat();
        registerVolumeCategory(event.getVoicechat());
        LOGGER.info("Voice chat server started; EchoPins playback is available");
    }

    void onVoiceServerStopped(VoicechatServerStoppedEvent event) {
        stopAllPlaybacks();
        this.serverApi = null;
        LOGGER.info("Voice chat server stopped; EchoPins playback is unavailable");
    }

    void onPlayerDisconnected(PlayerDisconnectedEvent event) {
        // This fires when voice chat drops, which is not the same as leaving the server - the
        // player may still be standing in the world. Anything they were recording can no longer
        // receive audio, so the server is told immediately instead of waiting for the timeout.
        java.util.function.Consumer<UUID> listener = this.voiceDisconnectListener;
        if (listener == null) {
            return;
        }
        try {
            listener.accept(event.getPlayerUuid());
        } catch (RuntimeException e) {
            LOGGER.error("Voice disconnect handler failed for {}", event.getPlayerUuid(), e);
        }
    }

    /**
     * Feeds a microphone frame to the recording session manager.
     *
     * <p>Runs on Simple Voice Chat's networking thread, so it must stay cheap and must not touch
     * world state.
     */
    void onMicrophonePacket(MicrophonePacketEvent event) {
        MicrophoneCapture sink = this.capture;
        if (sink == null) {
            return;
        }
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) {
            return;
        }
        UUID speaker = sender.getPlayer().getUuid();
        byte[] opus = event.getPacket().getOpusEncodedData();
        if (opus == null || opus.length == 0) {
            return;
        }

        boolean suppressBroadcast;
        try {
            suppressBroadcast = sink.onMicrophoneFrame(speaker, opus);
        } catch (RuntimeException e) {
            // Never let a bug here break someone's normal proximity voice chat.
            LOGGER.error("Microphone capture failed for {}", speaker, e);
            return;
        }

        if (suppressBroadcast) {
            event.cancel();
        }
    }

    private void registerVolumeCategory(VoicechatServerApi serverApi) {
        VoicechatApi currentApi = this.api;
        if (currentApi == null) {
            return;
        }
        try {
            VolumeCategory category = currentApi.volumeCategoryBuilder()
                    .setId(VOLUME_CATEGORY_ID)
                    .setName("EchoPins")
                    .setNameTranslationKey("echopins.volume_category.name")
                    .setDescription("Volume of EchoPin voice messages")
                    .setDescriptionTranslationKey("echopins.volume_category.description")
                    .setIcon(VolumeCategoryIcon.create())
                    .build();
            serverApi.registerVolumeCategory(category);
            LOGGER.debug("Registered the EchoPins volume category");
        } catch (RuntimeException e) {
            // A missing volume slider is a cosmetic loss, not a reason to break playback.
            LOGGER.warn("Could not register the EchoPins volume category", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return serverApi != null;
    }

    @Override
    public boolean isPlayerConnected(UUID playerUuid) {
        VoicechatServerApi current = serverApi;
        if (current == null) {
            return false;
        }
        VoicechatConnection connection = current.getConnectionOf(playerUuid);
        return connection != null && connection.isConnected() && !connection.isDisabled();
    }

    @Override
    public void setMicrophoneCapture(MicrophoneCapture capture) {
        this.capture = capture;
    }

    @Override
    public void setVoiceDisconnectListener(java.util.function.Consumer<UUID> listener) {
        this.voiceDisconnectListener = listener;
    }

    @Override
    public Optional<VoicePlayback> startLocationalPlayback(ServerLevel level,
                                                           WorldPos position,
                                                           float distance,
                                                           UUID channelId,
                                                           Predicate<UUID> audience,
                                                           VoiceRecording recording,
                                                           Runnable onFinished) {
        VoicechatServerApi current = serverApi;
        VoicechatApi currentApi = this.api;
        EchoPinsExecutors executors = EchoPinsExecutors.current();
        if (current == null || currentApi == null || executors == null || recording.isEmpty()) {
            return Optional.empty();
        }

        try {
            Position pos = currentApi.createPosition(position.x(), position.y(), position.z());
            LocationalAudioChannel channel = current.createLocationalAudioChannel(
                    channelId, currentApi.fromServerLevel(level), pos);
            if (channel == null) {
                return Optional.empty();
            }
            channel.setCategory(VOLUME_CATEGORY_ID);
            channel.setDistance(distance);
            // The filter runs per listener inside the voice system, so a private pin's access
            // rule is enforced at the point audio would actually be delivered.
            channel.setFilter(player -> audience.test(player.getUuid()));

            ChannelPlayback playback = new ChannelPlayback(channelId, channel, recording,
                    executors.audio(), onFinished, this::onPlaybackEnded);
            activePlaybacks.put(channelId, playback);
            playback.start();
            return Optional.of(playback);
        } catch (RuntimeException e) {
            LOGGER.error("Could not start EchoPin playback on channel {}", channelId, e);
            return Optional.empty();
        }
    }

    private void onPlaybackEnded(UUID channelId) {
        activePlaybacks.remove(channelId);
    }

    /** Stops every playback. Used on voice server stop and on mod shutdown. */
    public void stopAllPlaybacks() {
        for (ChannelPlayback playback : activePlaybacks.values()) {
            playback.stop();
        }
        activePlaybacks.clear();
    }

    @Override
    public void shutdown() {
        stopAllPlaybacks();
        capture = null;
        voiceDisconnectListener = null;
        serverApi = null;
    }

    /**
     * Streams one recording's frames into a channel at real-time pace.
     *
     * <p>Frames are 20 ms apart, so the task reschedules itself on the shared audio scheduler
     * rather than owning a thread. Many pins can play at once without the thread count moving.
     */
    private static final class ChannelPlayback implements VoicePlayback {

        private final UUID channelId;
        private final LocationalAudioChannel channel;
        private final VoiceRecording recording;
        private final ScheduledExecutorService scheduler;
        private final Runnable onFinished;
        private final java.util.function.Consumer<UUID> onEnded;
        private final AtomicBoolean finished = new AtomicBoolean();

        private volatile ScheduledFuture<?> task;
        private int nextFrame;

        ChannelPlayback(UUID channelId, LocationalAudioChannel channel, VoiceRecording recording,
                        ScheduledExecutorService scheduler, Runnable onFinished,
                        java.util.function.Consumer<UUID> onEnded) {
            this.channelId = channelId;
            this.channel = channel;
            this.recording = recording;
            this.scheduler = scheduler;
            this.onFinished = onFinished;
            this.onEnded = onEnded;
        }

        void start() {
            task = scheduler.scheduleAtFixedRate(this::sendNextFrame, 0L,
                    AudioConstants.FRAME_DURATION_MILLIS, TimeUnit.MILLISECONDS);
        }

        private void sendNextFrame() {
            if (finished.get()) {
                return;
            }
            try {
                if (channel.isClosed() || nextFrame >= recording.frameCount()) {
                    complete();
                    return;
                }
                channel.send(recording.frame(nextFrame++));
            } catch (RuntimeException e) {
                LOGGER.error("EchoPin playback failed on channel {}", channelId, e);
                complete();
            }
        }

        private void complete() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> current = task;
            if (current != null) {
                current.cancel(false);
            }
            try {
                if (!channel.isClosed()) {
                    channel.flush();
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Flushing channel {} failed", channelId, e);
            }
            onEnded.accept(channelId);
            try {
                onFinished.run();
            } catch (RuntimeException e) {
                LOGGER.error("EchoPin playback completion handler failed", e);
            }
        }

        @Override
        public UUID channelId() {
            return channelId;
        }

        @Override
        public boolean isFinished() {
            return finished.get();
        }

        @Override
        public void stop() {
            complete();
        }
    }
}
