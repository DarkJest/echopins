package dev.echopins.fabric.voicechat;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import dev.echopins.integration.voicechat.EchoPinsVoicechatPlugin;

/**
 * Simple Voice Chat plugin for the Fabric build.
 *
 * <p>Fabric discovers plugins through the {@code voicechat} entry point in
 * {@code fabric.mod.json} rather than through an annotation, which is the only difference from the
 * Forge-family side. Registration is delegated so all loaders share one implementation.
 */
public final class EchoPinsFabricVoicechatPlugin implements VoicechatPlugin {

    private final EchoPinsVoicechatPlugin delegate = new EchoPinsVoicechatPlugin();

    @Override
    public String getPluginId() {
        return delegate.getPluginId();
    }

    @Override
    public void initialize(VoicechatApi api) {
        delegate.initialize(api);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        delegate.registerEvents(registration);
    }
}
