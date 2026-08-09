package dev.echopins.client.hud;

import dev.echopins.client.state.ClientPinState;
import dev.echopins.infrastructure.config.EchoPinsClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The recording indicator.
 *
 * <p>Deliberately small: a compact panel rather than a full-screen banner, because recording a
 * ten-second note should not take over the screen. It is still unmistakable - a red dot, the
 * word "recording", elapsed time and a progress bar - because a recording indicator that can be
 * missed is a privacy failure, not a cosmetic one.
 *
 * <p>Accessibility: state is carried by shape and text as well as colour, the pulse can be turned
 * off with {@code reduceMotion}, and a high-contrast outline is available. The panel is positioned
 * from the scaled screen size, so it lands correctly at any GUI scale and aspect ratio.
 */
public final class RecordingHud implements LayeredDraw.Layer {

    /**
     * Wide enough for the longest hint string in the shipped languages. The hint is the whole
     * point of the panel for push-to-talk users, so it must not be clipped.
     */
    private static final int PANEL_WIDTH = 150;
    private static final int PANEL_HEIGHT = 34;
    private static final int MARGIN = 8;
    private static final int PADDING = 6;

    private static final int COLOR_PANEL = 0xB4101418;
    private static final int COLOR_PANEL_OUTLINE = 0xFF2FB6C4;
    private static final int COLOR_TEXT = 0xFFE9F3F5;
    private static final int COLOR_MUTED = 0xFF9BB0B6;
    private static final int COLOR_RECORD = 0xFFE5484D;
    private static final int COLOR_BAR_BACKGROUND = 0xFF232B31;
    private static final int COLOR_BAR_FILL = 0xFF2FB6C4;
    private static final int COLOR_BAR_WARNING = 0xFFE5A03D;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ClientPinState.Recording recording = ClientPinState.INSTANCE.recording();
        if (!recording.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int x = anchorX(screenWidth) + EchoPinsClientConfig.HUD_OFFSET_X.get();
        int y = anchorY(screenHeight) + EchoPinsClientConfig.HUD_OFFSET_Y.get();

        // Clamp so a saved offset from a larger window can never push the panel off-screen.
        x = Math.max(0, Math.min(x, screenWidth - PANEL_WIDTH));
        y = Math.max(0, Math.min(y, screenHeight - PANEL_HEIGHT));

        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, COLOR_PANEL);
        if (EchoPinsClientConfig.HIGH_CONTRAST_RECORDING_INDICATOR.get()) {
            graphics.renderOutline(x, y, PANEL_WIDTH, PANEL_HEIGHT, COLOR_PANEL_OUTLINE);
        }

        Font font = minecraft.font;
        int textX = x + PADDING;
        int textY = y + PADDING;

        drawRecordDot(graphics, textX, textY + 1);

        graphics.drawString(font, Component.translatable("echopins.hud.recording"),
                textX + 12, textY, COLOR_TEXT, false);

        String elapsed = formatSeconds(recording.elapsedMillis());
        String total = formatSeconds(recording.maxMillis());
        String timer = elapsed + " / " + total;
        graphics.drawString(font, timer,
                x + PANEL_WIDTH - PADDING - font.width(timer), textY, COLOR_MUTED, false);

        int barY = y + PANEL_HEIGHT - PADDING - 8;
        drawProgressBar(graphics, textX, barY, PANEL_WIDTH - PADDING * 2, recording);

        Component hint = recording.receivingAudio()
                ? Component.translatable("echopins.hud.release_to_stop")
                // The single most useful thing to say when nothing is arriving: in push-to-talk
                // the player must also hold their voice chat key.
                : Component.translatable("echopins.hud.hold_push_to_talk");
        graphics.drawString(font, hint, textX, barY + 10,
                recording.receivingAudio() ? COLOR_MUTED : COLOR_BAR_WARNING, false);
    }

    private void drawRecordDot(GuiGraphics graphics, int x, int y) {
        boolean solid = true;
        if (!EchoPinsClientConfig.REDUCE_MOTION.get()) {
            // A slow blink; still visible at every phase because the square is drawn either way,
            // only its brightness changes.
            solid = (System.currentTimeMillis() / 500L) % 2L == 0L;
        }
        int colour = solid ? COLOR_RECORD : 0xFF8A2C30;
        graphics.fill(x + 1, y + 1, x + 7, y + 7, colour);
        graphics.fill(x + 2, y, x + 6, y + 8, colour);
        graphics.fill(x, y + 2, x + 8, y + 6, colour);
    }

    private void drawProgressBar(GuiGraphics graphics, int x, int y, int width,
                                 ClientPinState.Recording recording) {
        graphics.fill(x, y, x + width, y + 4, COLOR_BAR_BACKGROUND);
        if (recording.maxMillis() <= 0) {
            return;
        }
        float progress = Math.min(1.0F, (float) recording.elapsedMillis() / recording.maxMillis());
        int filled = Math.round(width * progress);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + 4, progress > 0.85F ? COLOR_BAR_WARNING : COLOR_BAR_FILL);
        }
    }

    private static int anchorX(int screenWidth) {
        return switch (EchoPinsClientConfig.HUD_POSITION.get()) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_CENTER, BOTTOM_CENTER -> (screenWidth - PANEL_WIDTH) / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - PANEL_WIDTH - MARGIN;
        };
    }

    private static int anchorY(int screenHeight) {
        return switch (EchoPinsClientConfig.HUD_POSITION.get()) {
            // Below the vanilla effect icons so it does not collide with potion effects.
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> MARGIN + 16;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenHeight - PANEL_HEIGHT - MARGIN - 40;
        };
    }

    /** Formats a duration as {@code m:ss}. Shared with the inbox and confirmation screens. */
    public static String formatSeconds(int millis) {
        int totalSeconds = Math.max(0, millis) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
