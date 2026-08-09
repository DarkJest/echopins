package dev.echopins.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's key bindings.
 *
 * <p>Both are registered through the vanilla controls screen, so players remap them the same way
 * they remap anything else. The defaults were picked to avoid vanilla and to avoid the keys
 * Simple Voice Chat claims by default ({@code V} and {@code CAPS_LOCK}).
 */
public final class EchoPinsKeybinds {

    public static final String CATEGORY = "key.categories.echopins";

    /** Held to record: press to start, release to stop. */
    public static final KeyMapping CREATE_PIN = new KeyMapping(
            "key.echopins.create_pin",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);

    /** Opens the EchoPins inbox. */
    public static final KeyMapping OPEN_INBOX = new KeyMapping(
            "key.echopins.open_inbox",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY);

    /**
     * Plays the pin you are looking at, or the nearest one in range.
     *
     * <p>Bound by default: the pin label on screen tells the player which key to press, so
     * leaving this unbound made that hint read "Not bound to play". {@code R} is free in vanilla,
     * and is not one of the keys Simple Voice Chat claims ({@code V} and {@code CAPS_LOCK}).
     */
    public static final KeyMapping PLAY_NEAREST = new KeyMapping(
            "key.echopins.play_nearest",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY);

    private EchoPinsKeybinds() {
    }
}
