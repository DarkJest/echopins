package dev.echopins.client.screen;

import dev.echopins.client.EchoPinsClient;
import dev.echopins.client.hud.FocusedPinHud;
import dev.echopins.client.hud.RecordingHud;
import dev.echopins.client.state.ClientPinState;
import dev.echopins.domain.visibility.Visibility;
import dev.echopins.infrastructure.network.EchoPinsNetwork;
import dev.echopins.infrastructure.network.PinSummary;
import dev.echopins.infrastructure.network.payload.ClientboundPayloads;
import dev.echopins.infrastructure.network.payload.ServerboundPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The EchoPins inbox.
 *
 * <p>Four tabs, a page of entries, and per-entry actions. Deliberately a small utility screen -
 * a list of voice notes with somewhere to play them - rather than a social feed.
 *
 * <p>Paging is server-side: the client asks for a page and renders what comes back, so the screen
 * never holds more than {@code INBOX_PAGE_SIZE} entries regardless of how many pins exist.
 */
public final class InboxScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 196;
    private static final int ENTRY_HEIGHT = 18;
    private static final int COLOR_PANEL = 0xC8101418;
    private static final int COLOR_TITLE = 0xFFE9F3F5;
    private static final int COLOR_MUTED = 0xFF9BB0B6;
    private static final int COLOR_UNREAD = 0xFFE5A03D;

    private ServerboundPayloads.InboxTab tab = ServerboundPayloads.InboxTab.NEARBY;
    private int page;

    /** Set while widgets are rebuilt from an arriving page, so the rebuild does not re-request. */
    private boolean suppressRequest;

    public InboxScreen() {
        super(Component.translatable("echopins.screen.inbox.title"));
    }

    @Override
    protected void init() {
        if (!suppressRequest) {
            request();
        }

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        ServerboundPayloads.InboxTab[] tabs = ServerboundPayloads.InboxTab.values();
        int tabWidth = (PANEL_WIDTH - 20) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            ServerboundPayloads.InboxTab candidate = tabs[i];
            Button button = Button.builder(tabLabel(candidate), b -> {
                tab = candidate;
                page = 0;
                rebuild();
            }).bounds(left + 10 + i * tabWidth, top + 22, tabWidth - 2, 18).build();
            button.active = candidate != tab;
            addRenderableWidget(button);
        }

        currentPage().ifPresent(this::addEntryButtons);

        int totalPages = currentPage()
                .map(ClientboundPayloads.InboxPage::totalPages).orElse(1);
        if (totalPages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = Math.max(0, page - 1);
                rebuild();
            }).bounds(left + 10, top + PANEL_HEIGHT - 26, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = Math.min(totalPages - 1, page + 1);
                rebuild();
            }).bounds(left + PANEL_WIDTH - 30, top + PANEL_HEIGHT - 26, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(left + PANEL_WIDTH / 2 - 40, top + PANEL_HEIGHT - 26, 80, 20)
                .build());
    }

    private void addEntryButtons(ClientboundPayloads.InboxPage inbox) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        List<PinSummary> entries = inbox.entries();

        int y = top + 46;
        for (PinSummary entry : entries) {
            addRenderableWidget(Button.builder(Component.translatable("echopins.screen.inbox.play"),
                            b -> EchoPinsClient.requestPlayback(entry.id()))
                    .bounds(left + PANEL_WIDTH - 96, y, 40, ENTRY_HEIGHT - 2)
                    .build());

            boolean owned = this.minecraft != null && this.minecraft.player != null
                    && entry.authorId().equals(this.minecraft.player.getUUID());
            Button delete = Button.builder(Component.translatable("echopins.screen.inbox.delete"),
                            b -> {
                                EchoPinsNetwork.sendToServer(new ServerboundPayloads.DeletePin(entry.id()));
                                rebuild();
                            })
                    .bounds(left + PANEL_WIDTH - 52, y, 42, ENTRY_HEIGHT - 2)
                    .build();
            // Deleting someone else's pin is refused server-side anyway; greying it out here just
            // avoids offering an action that would fail.
            delete.active = owned;
            addRenderableWidget(delete);

            y += ENTRY_HEIGHT;
        }
    }

    private void request() {
        EchoPinsNetwork.sendToServer(new ServerboundPayloads.RequestInbox(tab, page));
    }

    /**
     * The cached page, but only if it belongs to the tab currently being shown.
     *
     * <p>Pages arrive asynchronously, so after switching tabs the cache still holds the previous
     * tab's results for a moment. Rendering those - or worse, wiring Play and Delete buttons to
     * them - would act on entries the player is no longer looking at.
     */
    private Optional<ClientboundPayloads.InboxPage> currentPage() {
        return ClientPinState.INSTANCE.inboxPage().filter(inbox -> inbox.tab() == tab);
    }

    /**
     * Called when a page arrives from the server.
     *
     * <p>Entry buttons are built in {@link #init()}, which runs before the request has been
     * answered. Without this the first page rendered its rows but had no Play or Delete buttons
     * at all, because there were no entries to build them from when the screen opened.
     */
    public void onPageReceived() {
        rebuildWidgetsOnly();
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    /** Rebuilds widgets without re-requesting, so a refresh cannot loop request-render-request. */
    private void rebuildWidgetsOnly() {
        boolean previous = suppressRequest;
        suppressRequest = true;
        try {
            rebuild();
        } finally {
            suppressRequest = previous;
        }
    }

    private static Component tabLabel(ServerboundPayloads.InboxTab tab) {
        return Component.translatable(switch (tab) {
            case NEARBY -> "echopins.screen.inbox.tab.nearby";
            case MINE -> "echopins.screen.inbox.tab.mine";
            case PRIVATE -> "echopins.screen.inbox.tab.private";
            case UNREAD -> "echopins.screen.inbox.tab.unread";
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, COLOR_PANEL);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 8, COLOR_TITLE);

        ClientboundPayloads.InboxPage inbox = currentPage().orElse(null);
        if (inbox == null || inbox.entries().isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("echopins.screen.inbox.empty"),
                    this.width / 2, top + 80, COLOR_MUTED);
            return;
        }

        int y = top + 46;
        for (PinSummary entry : inbox.entries()) {
            Component title = Component.literal(entry.authorName());
            if (entry.visibility() == Visibility.PRIVATE) {
                title = title.copy().append(Component.literal(" "))
                        .append(Component.translatable("echopins.hud.private_marker")
                                .withStyle(ChatFormatting.GRAY));
            }
            graphics.drawString(this.font, title, left + 10, y + 1,
                    entry.unread() ? COLOR_UNREAD : COLOR_TITLE, false);

            String meta = String.format(Locale.ROOT, "%s  %s  %s",
                    FocusedPinHud.relativeAge(entry.createdAt()),
                    RecordingHud.formatSeconds(entry.durationMillis()),
                    shortLocation(entry));
            graphics.drawString(this.font, meta, left + 108, y + 1, COLOR_MUTED, false);

            y += ENTRY_HEIGHT;
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("echopins.screen.inbox.page",
                        inbox.page() + 1, inbox.totalPages()),
                this.width / 2, top + PANEL_HEIGHT - 40, COLOR_MUTED);
    }

    private static String shortLocation(PinSummary entry) {
        return String.format(Locale.ROOT, "%d, %d, %d",
                (int) entry.anchor().renderPos().x(),
                (int) entry.anchor().renderPos().y(),
                (int) entry.anchor().renderPos().z());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
