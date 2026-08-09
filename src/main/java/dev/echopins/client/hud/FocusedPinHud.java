package dev.echopins.client.hud;

import dev.echopins.client.render.PinMarkerRenderer;
import dev.echopins.client.state.ClientPinState;
import dev.echopins.domain.visibility.Visibility;
import dev.echopins.infrastructure.config.EchoPinsClientConfig;
import dev.echopins.infrastructure.network.PinSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The compact label shown when the player looks at a nearby pin.
 *
 * <p>This is the whole "read" interaction: author, age, length, and how to play it. It appears
 * only when the crosshair is actually on a pin within interaction range, so it is absent the rest
 * of the time.
 */
public final class FocusedPinHud implements LayeredDraw.Layer {

    private static final int COLOR_BACKGROUND = 0xB4101418;
    private static final int COLOR_TITLE = 0xFFE9F3F5;
    private static final int COLOR_MUTED = 0xFF9BB0B6;
    private static final int COLOR_ACCENT = 0xFF2FB6C4;
    private static final int COLOR_UNREAD = 0xFFE5A03D;
    private static final int PADDING = 5;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        if (minecraft.screen != null || ClientPinState.INSTANCE.recording().isActive()) {
            return;
        }
        if (!EchoPinsClientConfig.SHOW_LABELS.get() || !EchoPinsClientConfig.SHOW_MARKERS.get()) {
            return;
        }

        double interactionRadius = ClientPinState.INSTANCE.settings().interactionRadius();
        Vec3 look = minecraft.player.getLookAngle().normalize();
        PinSummary focused = PinMarkerRenderer.focusedPin(
                minecraft.gameRenderer.getMainCamera(), look, interactionRadius);
        if (focused == null) {
            return;
        }

        Font font = minecraft.font;
        List<Component> lines = buildLines(focused, minecraft);

        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int height = lines.size() * (font.lineHeight + 1) - 1;

        int x = (graphics.guiWidth() - width) / 2 - PADDING;
        // Below the crosshair, clear of the hotbar at every GUI scale.
        int y = graphics.guiHeight() / 2 + 14;

        graphics.fill(x, y, x + width + PADDING * 2, y + height + PADDING * 2, COLOR_BACKGROUND);

        int lineY = y + PADDING;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x + PADDING, lineY,
                    i == 0 ? COLOR_TITLE : COLOR_MUTED, false);
            lineY += font.lineHeight + 1;
        }
    }

    private static List<Component> buildLines(PinSummary pin, Minecraft minecraft) {
        List<Component> lines = new ArrayList<>(4);

        Component author = Component.translatable("echopins.hud.pin_title", pin.authorName());
        if (pin.visibility() == Visibility.PRIVATE) {
            author = author.copy().append(Component.literal(" "))
                    .append(Component.translatable("echopins.hud.private_marker")
                            .withStyle(ChatFormatting.GRAY));
        }
        if (pin.unread()) {
            author = author.copy().append(Component.literal(" "))
                    .append(Component.translatable("echopins.hud.unread")
                            .withStyle(style -> style.withColor(COLOR_UNREAD)));
        }
        lines.add(author);

        lines.add(Component.translatable("echopins.hud.pin_meta",
                relativeAge(pin.createdAt()),
                RecordingHud.formatSeconds(pin.durationMillis())));

        // Captions double as the accessible alternative for players who cannot use the audio.
        if (EchoPinsClientConfig.SHOW_CAPTIONS.get()) {
            pin.caption().ifPresent(caption ->
                    lines.add(Component.literal(caption).withStyle(ChatFormatting.ITALIC)));
        }

        if (ClientPinState.INSTANCE.isPlaying(pin.id())) {
            lines.add(Component.translatable("echopins.hud.playing")
                    .withStyle(style -> style.withColor(COLOR_ACCENT)));
        } else {
            lines.add(Component.translatable("echopins.hud.press_to_play",
                    dev.echopins.client.keybind.EchoPinsKeybinds.PLAY_NEAREST.getTranslatedKeyMessage(),
                    dev.echopins.client.keybind.EchoPinsKeybinds.OPEN_INBOX.getTranslatedKeyMessage()));
        }
        return lines;
    }

    /** "12s ago", "5m ago", "3h ago", "2d ago". */
    public static String relativeAge(long createdAtEpochMillis) {
        long ageMillis = Math.max(0L, System.currentTimeMillis() - createdAtEpochMillis);
        long seconds = ageMillis / 1000L;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h";
        }
        return String.format(Locale.ROOT, "%dd", hours / 24);
    }
}
