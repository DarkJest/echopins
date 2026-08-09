package dev.echopins.client.screen;

import dev.echopins.client.hud.RecordingHud;
import dev.echopins.client.state.ClientPinState;
import dev.echopins.domain.expiry.ExpiryChoice;
import dev.echopins.domain.visibility.Visibility;
import dev.echopins.infrastructure.network.EchoPinsNetwork;
import dev.echopins.infrastructure.network.payload.ServerboundPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Preview and confirmation shown after a recording stops.
 *
 * <p>Nothing is published until the player presses Save. Closing the screen any other way -
 * Escape, or the server timing the pending recording out - discards the audio, so a recording can
 * never become a pin by accident.
 */
public final class ConfirmPinScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int ROW_HEIGHT = 22;
    private static final int COLOR_PANEL = 0xC8101418;
    private static final int COLOR_TITLE = 0xFFE9F3F5;
    private static final int COLOR_MUTED = 0xFF9BB0B6;

    private final int durationMillis;

    private Visibility visibility = Visibility.PUBLIC;
    private ExpiryChoice expiry = ExpiryChoice.DEFAULT;
    private final Set<UUID> recipients = new LinkedHashSet<>();

    private EditBox captionBox;
    private Button visibilityButton;
    private Button expiryButton;
    private Button recipientsButton;
    private Button saveButton;

    /** True between pressing Save and the server answering, so Save cannot be double-fired. */
    private boolean submitting;

    /**
     * Survives {@link #init()} being run again on a window resize, which rebuilds every widget.
     * Without this, resizing the window silently threw away whatever caption had been typed.
     */
    private String captionDraft = "";

    public ConfirmPinScreen(int durationMillis) {
        super(Component.translatable("echopins.screen.confirm.title"));
        this.durationMillis = durationMillis;
    }

    @Override
    protected void init() {
        ClientPinState.Settings settings = ClientPinState.INSTANCE.settings();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = this.height / 2 - 70;

        captionBox = new EditBox(this.font, left + 10, top + 44, PANEL_WIDTH - 20, 18,
                Component.translatable("echopins.screen.confirm.caption"));
        captionBox.setMaxLength(Math.max(1, settings.maxCaptionLength()));
        captionBox.setHint(Component.translatable("echopins.screen.confirm.caption_hint"));
        captionBox.setValue(captionDraft);
        captionBox.setResponder(text -> captionDraft = text);
        if (settings.maxCaptionLength() > 0) {
            addRenderableWidget(captionBox);
        }

        visibilityButton = Button.builder(visibilityLabel(), button -> toggleVisibility())
                .bounds(left + 10, top + 70, PANEL_WIDTH - 20, 20)
                .build();
        addRenderableWidget(visibilityButton);

        recipientsButton = Button.builder(recipientsLabel(),
                        button -> this.minecraft.setScreen(new RecipientPickerScreen(this, recipients)))
                .bounds(left + 10, top + 70 + ROW_HEIGHT, PANEL_WIDTH - 20, 20)
                .build();
        recipientsButton.active = visibility == Visibility.PRIVATE;
        addRenderableWidget(recipientsButton);

        expiryButton = Button.builder(expiryLabel(), button -> cycleExpiry())
                .bounds(left + 10, top + 70 + ROW_HEIGHT * 2, PANEL_WIDTH - 20, 20)
                .build();
        addRenderableWidget(expiryButton);

        int buttonWidth = (PANEL_WIDTH - 24) / 2;
        saveButton = Button.builder(Component.translatable("echopins.screen.confirm.save"),
                        button -> save())
                .bounds(left + 10, top + 70 + ROW_HEIGHT * 3 + 6, buttonWidth, 20)
                .build();
        saveButton.active = !submitting;
        addRenderableWidget(saveButton);
        addRenderableWidget(Button.builder(Component.translatable("echopins.screen.confirm.discard"),
                        button -> this.onClose())
                .bounds(left + 14 + buttonWidth, top + 70 + ROW_HEIGHT * 3 + 6, buttonWidth, 20)
                .build());
    }

    private void toggleVisibility() {
        visibility = visibility == Visibility.PUBLIC ? Visibility.PRIVATE : Visibility.PUBLIC;
        visibilityButton.setMessage(visibilityLabel());
        recipientsButton.active = visibility == Visibility.PRIVATE;
        recipientsButton.setMessage(recipientsLabel());
    }

    private void cycleExpiry() {
        boolean allowPermanent = ClientPinState.INSTANCE.settings().allowPermanentPins();
        expiry = switch (expiry) {
            case SHORT -> ExpiryChoice.DEFAULT;
            // Skip the permanent option entirely when the server forbids it, rather than offering
            // a choice that would be silently overridden.
            case DEFAULT -> allowPermanent ? ExpiryChoice.PERMANENT : ExpiryChoice.SHORT;
            case PERMANENT -> ExpiryChoice.SHORT;
        };
        expiryButton.setMessage(expiryLabel());
    }

    private Component visibilityLabel() {
        return Component.translatable("echopins.screen.confirm.visibility",
                Component.translatable(visibility == Visibility.PUBLIC
                        ? "echopins.visibility.public"
                        : "echopins.visibility.private"));
    }

    private Component recipientsLabel() {
        return Component.translatable("echopins.screen.confirm.recipients", recipients.size());
    }

    private Component expiryLabel() {
        String key = switch (expiry) {
            case SHORT -> "echopins.expiry.short";
            case DEFAULT -> "echopins.expiry.default";
            case PERMANENT -> "echopins.expiry.permanent";
        };
        return Component.translatable("echopins.screen.confirm.expiry", Component.translatable(key));
    }

    void refreshRecipients() {
        recipientsButton.setMessage(recipientsLabel());
    }

    /**
     * Sends the pin and waits.
     *
     * <p>The screen deliberately stays open until the server confirms by moving the recording
     * state to idle. Closing optimistically meant that a rejected save - hitting the create
     * cooldown, say - dropped the player back into the world with an error and no way to retry,
     * having already spoken their message.
     */
    private void save() {
        String caption = captionBox == null ? "" : captionBox.getValue().trim();
        submitting = true;
        saveButton.active = false;
        EchoPinsNetwork.sendToServer(new ServerboundPayloads.CreatePin(
                visibility,
                visibility == Visibility.PRIVATE ? recipients : Set.of(),
                caption.isEmpty() ? Optional.empty() : Optional.of(caption),
                expiry));
    }

    /** Re-arms the Save button after the server refused, so a retry is possible. */
    public void onSaveRejected() {
        submitting = false;
        if (saveButton != null) {
            saveButton.active = true;
        }
    }

    public boolean isSubmitting() {
        return submitting;
    }

    @Override
    public void onClose() {
        // Only reached when the player closes the screen themselves, with Escape or Discard: a
        // successful save is closed by the receiver through setScreen(null), which calls
        // removed() rather than onClose(). So this always means "throw the recording away".
        EchoPinsNetwork.sendToServer(new ServerboundPayloads.CancelRecording());
        super.onClose();
    }

    /** Panel and labels go behind the widgets; see the note in {@code InboxScreen}. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = this.height / 2 - 70;
        graphics.fill(left, top, left + PANEL_WIDTH, top + 176, COLOR_PANEL);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 10, COLOR_TITLE);
        graphics.drawCenteredString(this.font,
                Component.translatable("echopins.screen.confirm.duration",
                        RecordingHud.formatSeconds(durationMillis)),
                this.width / 2, top + 24, COLOR_MUTED);

        if (ClientPinState.INSTANCE.settings().maxCaptionLength() > 0) {
            graphics.drawString(this.font,
                    Component.translatable("echopins.screen.confirm.caption"),
                    left + 10, top + 34, COLOR_MUTED, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
