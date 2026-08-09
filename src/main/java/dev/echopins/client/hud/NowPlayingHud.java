package dev.echopins.client.hud;

import dev.echopins.client.keybind.EchoPinsKeybinds;
import dev.echopins.client.state.ClientPinState;
import dev.echopins.domain.pin.PinId;
import dev.echopins.infrastructure.config.EchoPinsClientConfig;
import dev.echopins.infrastructure.network.PinSummary;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shows what is currently playing and how much of it is left.
 *
 * <p>Without this the only way to tell which message you were hearing was to be aiming at its
 * marker, and there was no indication of how long it would run. Both matter as soon as more than
 * one pin is nearby: locational audio tells you roughly where a voice is coming from, not which
 * of two adjacent markers it belongs to.
 *
 * <p>Sits below the recording indicator so the two never overlap - they cannot both be active,
 * but the positions are configurable and a player could anchor them to the same corner.
 */
public final class NowPlayingHud implements LayeredDraw.Layer {

    private static final int PADDING = 5;
    private static final int ROW_HEIGHT = 11;
    private static final int MARGIN = 8;
    /**
     * Column reserved for the speaker dot, left of the text. Previously the dot was placed at the
     * right edge of a panel whose width was exactly the text width, so it always landed on top of
     * the countdown.
     */
    private static final int DOT_COLUMN_WIDTH = 12;

    private static final int COLOR_PANEL = 0xB4101418;
    private static final int COLOR_TITLE = 0xFFE9F3F5;
    private static final int COLOR_ACCENT = 0xFF2FB6C4;
    private static final int COLOR_MUTED = 0xFF9BB0B6;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        ClientPinState state = ClientPinState.INSTANCE;
        List<PinId> playing = state.playingPins();
        if (playing.isEmpty()) {
            return;
        }

        Font font = minecraft.font;
        Component stopHint = Component.translatable("echopins.hud.press_to_stop",
                EchoPinsKeybinds.PLAY_NEAREST.getTranslatedKeyMessage());

        // Measure first: panel width follows the content so a long author name or a translated
        // hint cannot spill past the edge.
        int contentWidth = font.width(stopHint);
        for (PinId id : playing) {
            contentWidth = Math.max(contentWidth, font.width(rowFor(state, id)));
        }
        int panelWidth = contentWidth + PADDING * 2 + DOT_COLUMN_WIDTH;
        int panelHeight = PADDING * 2 + playing.size() * ROW_HEIGHT + ROW_HEIGHT;

        int x = anchorX(graphics.guiWidth(), panelWidth);
        int y = anchorY(graphics.guiHeight(), panelHeight);
        x = Math.max(0, Math.min(x, graphics.guiWidth() - panelWidth));
        y = Math.max(0, Math.min(y, graphics.guiHeight() - panelHeight));

        graphics.fill(x, y, x + panelWidth, y + panelHeight, COLOR_PANEL);

        int textX = x + PADDING + DOT_COLUMN_WIDTH;
        int rowY = y + PADDING;
        drawSpeakerDot(graphics, x + PADDING, rowY + 1);
        for (PinId id : playing) {
            graphics.drawString(font, rowFor(state, id), textX, rowY, COLOR_TITLE, false);
            rowY += ROW_HEIGHT;
        }
        graphics.drawString(font, stopHint, textX, rowY, COLOR_MUTED, false);
    }

    /** "▶ Alex · 0:05 left" — author, so two adjacent pins are distinguishable, plus a countdown. */
    private static Component rowFor(ClientPinState state, PinId id) {
        String author = state.pin(id).map(PinSummary::authorName).orElse("?");
        long remaining = state.remainingMillis(id);
        return Component.translatable("echopins.hud.now_playing",
                author, RecordingHud.formatSeconds((int) remaining));
    }

    private static void drawSpeakerDot(GuiGraphics graphics, int x, int y) {
        // A small pulsing block, so the indicator is not carried by colour alone.
        boolean bright = EchoPinsClientConfig.REDUCE_MOTION.get()
                || (System.currentTimeMillis() / 400L) % 2L == 0L;
        int colour = bright ? COLOR_ACCENT : 0xFF1B6A72;
        graphics.fill(x, y + 2, x + 2, y + 6, colour);
        graphics.fill(x + 2, y, x + 6, y + 8, colour);
    }

    private static int anchorX(int screenWidth, int panelWidth) {
        return switch (EchoPinsClientConfig.HUD_POSITION.get()) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_CENTER, BOTTOM_CENTER -> (screenWidth - panelWidth) / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - panelWidth - MARGIN;
        } + EchoPinsClientConfig.HUD_OFFSET_X.get();
    }

    private static int anchorY(int screenHeight, int panelHeight) {
        return switch (EchoPinsClientConfig.HUD_POSITION.get()) {
            // Offset past the recording panel's slot so the two never sit on top of each other.
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> MARGIN + 16 + 40;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT ->
                    screenHeight - panelHeight - MARGIN - 40 - 44;
        } + EchoPinsClientConfig.HUD_OFFSET_Y.get();
    }
}
