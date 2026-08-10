package dev.echopins.client.screen;

import dev.echopins.client.state.ClientPinState;
import dev.echopins.infrastructure.network.payload.ClientboundPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Chooses who may hear a private pin.
 *
 * <p>Lists only players the server told this client about: whoever is online, plus the authors of
 * pins the player can already see. The client never invents entries, and selection is by UUID -
 * names shown here are labels only.
 */
public final class RecipientPickerScreen extends Screen {

    private static final int PANEL_WIDTH = 220;
    private static final int ROWS_PER_PAGE = 7;
    private static final int COLOR_PANEL = 0xC8101418;
    private static final int COLOR_TITLE = 0xFFE9F3F5;
    private static final int COLOR_MUTED = 0xFF9BB0B6;

    private final ConfirmPinScreen parent;
    private final Set<UUID> selected;

    private int page;

    RecipientPickerScreen(ConfirmPinScreen parent, Set<UUID> selected) {
        super(Component.translatable("echopins.screen.recipients.title"));
        this.parent = parent;
        this.selected = selected;
    }

    @Override
    protected void init() {
        List<ClientboundPayloads.KnownPlayer> players = ClientPinState.INSTANCE.knownPlayers();
        int maxRecipients = ClientPinState.INSTANCE.settings().maxPrivateRecipients();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = this.height / 2 - 90;

        int totalPages = Math.max(1, (players.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.min(page, totalPages - 1);

        int from = page * ROWS_PER_PAGE;
        int to = Math.min(players.size(), from + ROWS_PER_PAGE);

        int y = top + 28;
        for (int i = from; i < to; i++) {
            ClientboundPayloads.KnownPlayer player = players.get(i);
            addRenderableWidget(Button.builder(rowLabel(player), button -> {
                if (selected.contains(player.uuid())) {
                    selected.remove(player.uuid());
                } else if (selected.size() < maxRecipients) {
                    selected.add(player.uuid());
                }
                button.setMessage(rowLabel(player));
            }).bounds(left + 10, y, PANEL_WIDTH - 20, 18).build());
            y += 20;
        }

        if (totalPages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                page = Math.max(0, page - 1);
                rebuild();
            }).bounds(left + 10, top + 168, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                page = Math.min(totalPages - 1, page + 1);
                rebuild();
            }).bounds(left + PANEL_WIDTH - 30, top + 168, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("echopins.screen.recipients.done"),
                        button -> this.onClose())
                .bounds(left + 40, top + 168, PANEL_WIDTH - 80, 20)
                .build());
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    private Component rowLabel(ClientboundPayloads.KnownPlayer player) {
        Component name = Component.literal(player.name());
        if (!player.online()) {
            name = name.copy().withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable(
                selected.contains(player.uuid())
                        ? "echopins.screen.recipients.selected"
                        : "echopins.screen.recipients.unselected",
                name);
    }

    @Override
    public void onClose() {
        parent.refreshRecipients();
        this.minecraft.setScreen(parent);
    }

    /** Panel goes behind the widgets; see the note in {@code InboxScreen}. */
    @Override
    public void renderBackground(GuiGraphics graphics) {
        super.renderBackground(graphics);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = this.height / 2 - 90;
        graphics.fill(left, top, left + PANEL_WIDTH, top + 194, COLOR_PANEL);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 8, COLOR_TITLE);

        if (ClientPinState.INSTANCE.knownPlayers().isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("echopins.screen.recipients.empty"),
                    this.width / 2, top + 40, COLOR_MUTED);
        } else {
            graphics.drawCenteredString(this.font,
                    Component.translatable("echopins.screen.recipients.count",
                            selected.size(), ClientPinState.INSTANCE.settings().maxPrivateRecipients()),
                    this.width / 2, top + 176 - 158, COLOR_MUTED);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
