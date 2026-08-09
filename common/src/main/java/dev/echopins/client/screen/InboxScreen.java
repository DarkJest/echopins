package dev.echopins.client.screen;

import dev.echopins.client.EchoPinsClientCore;
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
 *
 * <p>The layout is column-based and the panel is sized from the row height and page size rather
 * than being a guessed constant. An earlier fixed panel was too short for a full page, so the last
 * rows were drawn on top of the pager and the Done button - which also swallowed their clicks and
 * made paging look broken.
 */
public final class InboxScreen extends Screen {

    private static final int PANEL_WIDTH = 440;
    /** Two lines per entry: author and metadata on top, the caption underneath. */
    private static final int ROW_HEIGHT = 26;
    /**
     * Must match {@code EchoPinsServer.INBOX_PAGE_SIZE}. Six rather than eight because the panel
     * has to fit at GUI scale 4, where a 1080p screen is only 270 logical pixels tall.
     */
    private static final int ROWS = 6;

    private static final int HEADER_HEIGHT = 46;
    private static final int FOOTER_HEIGHT = 52;
    private static final int PANEL_HEIGHT = HEADER_HEIGHT + ROWS * ROW_HEIGHT + FOOTER_HEIGHT;

    // Text starts at the left edge and runs up to the buttons. Everything is truncated to fit, so
    // a long name, caption or distant coordinate can never run into a button.
    private static final int COL_TEXT = 10;
    private static final int COL_PLAY = 336;
    private static final int COL_PLAY_W = 46;
    private static final int COL_DELETE = 386;
    private static final int COL_DELETE_W = 48;
    /** Space available for text on either line. */
    private static final int TEXT_WIDTH = COL_PLAY - COL_TEXT - 8;

    private static final int COLOR_PANEL = 0xC8101418;
    private static final int COLOR_ROW_ALT = 0x14FFFFFF;
    private static final int COLOR_TITLE = 0xFFE9F3F5;
    private static final int COLOR_MUTED = 0xFF9BB0B6;
    private static final int COLOR_UNREAD = 0xFFE5A03D;
    private static final int COLOR_PLAYING = 0xFF2FB6C4;
    private static final int COLOR_UNREACHABLE = 0xFF6E7F85;
    private static final int COLOR_CAPTION = 0xFFC4D3D7;

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
            }).bounds(left + 10 + i * tabWidth, top + 22, tabWidth - 3, 18).build();
            button.active = candidate != tab;
            addRenderableWidget(button);
        }

        currentPage().ifPresent(inbox -> addEntryButtons(left, top, inbox));

        int totalPages = currentPage().map(ClientboundPayloads.InboxPage::totalPages).orElse(1);
        int pagerY = top + PANEL_HEIGHT - 28;

        Button previous = Button.builder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            rebuild();
        }).bounds(left + 10, pagerY, 22, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal(">"), b -> {
            page = Math.min(totalPages - 1, page + 1);
            rebuild();
        }).bounds(left + PANEL_WIDTH - 32, pagerY, 22, 20).build();
        next.active = page < totalPages - 1;
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(left + PANEL_WIDTH / 2 - 40, pagerY, 80, 20)
                .build());
    }

    private void addEntryButtons(int left, int top, ClientboundPayloads.InboxPage inbox) {
        int y = top + HEADER_HEIGHT;
        for (PinSummary entry : inbox.entries()) {
            boolean playing = ClientPinState.INSTANCE.isPlaying(entry.id());
            boolean reachable = isWithinReach(entry);

            Button play = Button.builder(
                            Component.translatable(playing
                                    ? "echopins.screen.inbox.stop"
                                    : "echopins.screen.inbox.play"),
                            b -> {
                                if (ClientPinState.INSTANCE.isPlaying(entry.id())) {
                                    EchoPinsClientCore.requestStop(entry.id());
                                } else {
                                    EchoPinsClientCore.requestPlayback(entry.id());
                                }
                                rebuildWidgetsOnly();
                            })
                    .bounds(left + COL_PLAY, y + 3, COL_PLAY_W, 20)
                    .build();
            // Playback is range-limited server-side, so offering the button for a pin across the
            // map only produced a refusal. Stopping something already playing stays available.
            play.active = reachable || playing;
            if (!reachable && !playing) {
                play.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("echopins.screen.inbox.too_far_tooltip")));
            }
            addRenderableWidget(play);

            boolean owned = this.minecraft != null && this.minecraft.player != null
                    && entry.authorId().equals(this.minecraft.player.getUUID());
            Button delete = Button.builder(Component.translatable("echopins.screen.inbox.delete"),
                            b -> {
                                EchoPinsNetwork.sendToServer(new ServerboundPayloads.DeletePin(entry.id()));
                                rebuild();
                            })
                    .bounds(left + COL_DELETE, y + 3, COL_DELETE_W, 20)
                    .build();
            // Deleting someone else's pin is refused server-side anyway; greying it out here just
            // avoids offering an action that would fail.
            delete.active = owned;
            addRenderableWidget(delete);

            y += ROW_HEIGHT;
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
    public void onPageReceived(ClientboundPayloads.InboxPage inbox) {
        if (inbox.tab() == tab) {
            // The server clamps the requested page against the real total; adopt its answer so
            // the pager buttons agree with what is actually being shown.
            page = inbox.page();
        }
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

    /**
     * Draws the panel and the row text.
     *
     * <p>This is deliberately {@code renderBackground} and not {@code render}: Minecraft's
     * {@code Screen.render} draws the background first and every widget afterwards. Painting the
     * panel from {@code render} therefore covered all the buttons - they stayed clickable but
     * invisible, which is why tab and pager labels could not be seen and paging looked broken.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, COLOR_PANEL);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 8, COLOR_TITLE);

        ClientboundPayloads.InboxPage inbox = currentPage().orElse(null);
        if (inbox == null || inbox.entries().isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("echopins.screen.inbox.empty"),
                    this.width / 2, top + HEADER_HEIGHT + 40, COLOR_MUTED);
            return;
        }

        int y = top + HEADER_HEIGHT;
        int index = 0;
        for (PinSummary entry : inbox.entries()) {
            if (index++ % 2 == 1) {
                graphics.fill(left + 6, y, left + PANEL_WIDTH - 6, y + ROW_HEIGHT - 2, COLOR_ROW_ALT);
            }
            boolean playing = ClientPinState.INSTANCE.isPlaying(entry.id());
            boolean reachable = isWithinReach(entry);

            // Line one: who left it, and the facts that decide whether you can hear it.
            Component author = Component.literal(entry.authorName());
            if (entry.visibility() == Visibility.PRIVATE) {
                author = author.copy().append(Component.literal(" "))
                        .append(Component.translatable("echopins.hud.private_marker")
                                .withStyle(ChatFormatting.GRAY));
            }
            graphics.drawString(this.font, author, left + COL_TEXT, y + 3,
                    playing ? COLOR_PLAYING : entry.unread() ? COLOR_UNREAD : COLOR_TITLE, false);

            String meta = FocusedPinHud.relativeAge(entry.createdAt())
                    + "  " + RecordingHud.formatSeconds(entry.durationMillis())
                    + "  " + proximityLabel(entry);
            String metaTrimmed = truncate(meta, TEXT_WIDTH / 2);
            graphics.drawString(this.font, metaTrimmed,
                    left + COL_PLAY - 8 - this.font.width(metaTrimmed), y + 3,
                    reachable ? COLOR_MUTED : COLOR_UNREACHABLE, false);

            // Line two: the caption is the pin's name as far as a player is concerned. Without a
            // caption there is nothing to identify a message by, so fall back to its coordinates.
            String second = entry.caption().orElseGet(() -> coordinates(entry));
            graphics.drawString(this.font, truncate(second, TEXT_WIDTH),
                    left + COL_TEXT, y + 14,
                    entry.caption().isPresent() ? COLOR_CAPTION : COLOR_UNREACHABLE, false);

            y += ROW_HEIGHT;
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("echopins.screen.inbox.page",
                        inbox.page() + 1, inbox.totalPages()),
                this.width / 2, top + PANEL_HEIGHT - 44, COLOR_MUTED);
    }

    /** Cuts a string to fit a column, with an ellipsis so truncation is visible. */
    private String truncate(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("…")) + "…";
    }

    private static String coordinates(PinSummary entry) {
        return String.format(Locale.ROOT, "%d, %d, %d",
                (int) entry.anchor().renderPos().x(),
                (int) entry.anchor().renderPos().y(),
                (int) entry.anchor().renderPos().z());
    }

    /** How far away it is, or a note that it is in another world. */
    private String proximityLabel(PinSummary entry) {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
            return "";
        }
        if (!inCurrentDimension(entry)) {
            return Component.translatable("echopins.screen.inbox.other_dimension").getString();
        }
        long metres = Math.round(Math.sqrt(distanceSquared(entry)));
        return Component.translatable("echopins.screen.inbox.distance", metres).getString();
    }

    private boolean inCurrentDimension(PinSummary entry) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return false;
        }
        var here = this.minecraft.level.dimension().location();
        var there = entry.anchor().dimension();
        return here.getNamespace().equals(there.namespace())
                && here.getPath().equals(there.path());
    }

    private double distanceSquared(PinSummary entry) {
        var player = this.minecraft.player;
        double dx = entry.anchor().renderPos().x() - player.getX();
        double dy = entry.anchor().renderPos().y() - player.getEyeY();
        double dz = entry.anchor().renderPos().z() - player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Whether the server would accept a playback request for this pin.
     *
     * <p>Mirrors the server's range check so the button reflects reality. The server still
     * enforces it - this only stops the interface offering an action that is guaranteed to fail.
     */
    private boolean isWithinReach(PinSummary entry) {
        if (this.minecraft == null || this.minecraft.player == null || !inCurrentDimension(entry)) {
            return false;
        }
        double reach = ClientPinState.INSTANCE.settings().interactionRadius();
        return distanceSquared(entry) <= reach * reach;
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
