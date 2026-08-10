package dev.echopins;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LoaderMetadataTest {

    @Test
    void declaresVoiceChatApiAndLoaderSpecificPluginDiscovery() throws IOException {
        ClassLoader loader = LoaderMetadataTest.class.getClassLoader();
        InputStream fabricMetadata = loader.getResourceAsStream("fabric.mod.json");
        InputStream forgeMetadata = loader.getResourceAsStream("META-INF/mods.toml");

        assertNotEquals(fabricMetadata == null, forgeMetadata == null,
                "exactly one loader metadata file must be present");

        if (fabricMetadata != null) {
            String metadata = read(fabricMetadata);
            assertTrue(metadata.contains("\"voicechat_api\""));
            assertTrue(metadata.contains("\"voicechat\""));
            assertTrue(metadata.contains("dev.echopins.fabric.voicechat.EchoPinsFabricVoicechatPlugin"));
            assertFalse(metadata.contains("\"voicechat_plugins\""),
                    "Simple Voice Chat only discovers the 'voicechat' Fabric entrypoint");
            return;
        }

        assertNotNull(forgeMetadata);
        String metadata = read(forgeMetadata);
        assertTrue(metadata.contains("modId=\"voicechat_api\""));
        assertTrue(metadata.contains("modId=\"echopins\""));
    }

    private static String read(InputStream input) throws IOException {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
