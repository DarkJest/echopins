package dev.echopins.integration.voicechat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import dev.echopins.EchoPins;

/**
 * The Simple Voice Chat plugin entry point.
 *
 * <p>Simple Voice Chat discovers this class by scanning Forge and NeoForge mod files for
 * {@link ForgeVoicechatPlugin} and instantiating it through its no-argument constructor. It is
 * therefore constructed by Simple Voice Chat, not by EchoPins, which is why all state lives in
 * {@link SimpleVoiceChatBackend} and this class is only a thin forwarder.
 *
 * <p>Events are registered here and nowhere else, as the API requires.
 */
@ForgeVoicechatPlugin
public class EchoPinsVoicechatPlugin implements VoicechatPlugin {

    /** Must be unique across all voice chat plugins. */
    public static final String PLUGIN_ID = EchoPins.MOD_ID;

    /**
     * Invoked reflectively by Simple Voice Chat.
     */
    public EchoPinsVoicechatPlugin() {
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        SimpleVoiceChatBackend.INSTANCE.onApiAvailable(api);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        SimpleVoiceChatBackend backend = SimpleVoiceChatBackend.INSTANCE;

        registration.registerEvent(VoicechatServerStartedEvent.class, backend::onVoiceServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, backend::onVoiceServerStopped);
        registration.registerEvent(PlayerDisconnectedEvent.class, backend::onPlayerDisconnected);

        // A high priority means EchoPins sees a microphone frame before plugins that only
        // observe it, which matters because cancelling here is what stops a message being
        // recorded from also being broadcast to nearby players.
        registration.registerEvent(MicrophonePacketEvent.class, backend::onMicrophonePacket, 100);
    }
}
