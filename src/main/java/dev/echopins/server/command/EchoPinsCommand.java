package dev.echopins.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinId;
import dev.echopins.infrastructure.network.payload.ServerboundPayloads;
import dev.echopins.server.EchoPinsServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code /echopins} command tree.
 *
 * <p>Ordinary play never needs these; they exist for players who want a list without opening a
 * screen, and for admins who need to moderate or clean up. Admin subcommands are gated on the
 * configured vanilla permission level.
 *
 * <p>Every message is a translation key, so command output is localized like the rest of the UI.
 */
public final class EchoPinsCommand {

    private EchoPinsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("echopins")
                .then(Commands.literal("list").executes(ctx -> listNearby(ctx.getSource())))
                .then(Commands.literal("mine").executes(ctx -> listMine(ctx.getSource())))
                .then(Commands.literal("unread").executes(ctx -> listUnread(ctx.getSource())))
                .then(Commands.literal("stats").executes(ctx -> stats(ctx.getSource(), false)))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> delete(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(adminBranch())
                .executes(ctx -> help(ctx.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminBranch() {
        return Commands.literal("admin")
                .requires(EchoPinsCommand::isAdmin)
                .then(Commands.literal("stats").executes(ctx -> stats(ctx.getSource(), true)))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("cleanup").executes(ctx -> cleanup(ctx.getSource())))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> adminDelete(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("purge")
                        .then(Commands.literal("expired")
                                .executes(ctx -> purgeExpired(ctx.getSource())))
                        .then(Commands.literal("player")
                                .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                        .executes(ctx -> purgePlayer(ctx.getSource(),
                                                GameProfileArgument.getGameProfiles(ctx, "target"))))));
    }

    private static boolean isAdmin(CommandSourceStack source) {
        // Falls back to the vanilla default when EchoPins is not running, so the branch is still
        // hidden from ordinary players rather than exposed.
        int level = EchoPinsServer.current()
                .map(server -> server.limits().operatorPermissionLevel())
                .orElse(2);
        return source.hasPermission(level);
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("echopins.command.help"), false);
        return 1;
    }

    private static int listNearby(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return sendList(source, withServer(source)
                .map(server -> server.inboxQuery(player, ServerboundPayloads.InboxTab.NEARBY))
                .orElse(List.of()), "echopins.command.list.nearby");
    }

    private static int listMine(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return sendList(source, withServer(source)
                .map(server -> server.inboxQuery(player, ServerboundPayloads.InboxTab.MINE))
                .orElse(List.of()), "echopins.command.list.mine");
    }

    private static int listUnread(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return sendList(source, withServer(source)
                .map(server -> server.inboxQuery(player, ServerboundPayloads.InboxTab.UNREAD))
                .orElse(List.of()), "echopins.command.list.unread");
    }

    private static int sendList(CommandSourceStack source, List<EchoPin> pins, String headerKey) {
        if (pins.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("echopins.command.list.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(headerKey, pins.size()), false);
        int shown = 0;
        for (EchoPin pin : pins) {
            if (shown++ >= 20) {
                int remaining = pins.size() - 20;
                source.sendSuccess(() ->
                        Component.translatable("echopins.command.list.truncated", remaining), false);
                break;
            }
            source.sendSuccess(() -> Component.translatable("echopins.command.list.entry",
                    pin.author().lastKnownName(),
                    formatDuration(pin.durationMillis()),
                    pin.anchor().dimension().toString(),
                    (int) pin.anchor().renderPos().x(),
                    (int) pin.anchor().renderPos().y(),
                    (int) pin.anchor().renderPos().z(),
                    pin.id().toString()), false);
        }
        return pins.size();
    }

    private static int delete(CommandSourceStack source, String rawId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<PinId> pinId = parsePinId(rawId);
        if (pinId.isEmpty()) {
            source.sendFailure(Component.translatable("echopins.command.invalid_id"));
            return 0;
        }
        return withServer(source).map(server -> {
            try {
                EchoPin deleted = server.pins().delete(player.getUUID(), pinId.get(),
                        server.isOperator(player));
                server.playback().stopPlaybacksOf(deleted.id());
                server.sync().broadcastRemoval(server.server(), deleted.id());
                source.sendSuccess(() -> Component.translatable("echopins.command.deleted"), false);
                return 1;
            } catch (dev.echopins.domain.error.EchoPinException e) {
                source.sendFailure(Component.translatable(e.error().translationKey()));
                return 0;
            }
        }).orElse(0);
    }

    private static int adminDelete(CommandSourceStack source, String rawId) {
        Optional<PinId> pinId = parsePinId(rawId);
        if (pinId.isEmpty()) {
            source.sendFailure(Component.translatable("echopins.command.invalid_id"));
            return 0;
        }
        return withServer(source).map(server -> {
            Optional<EchoPin> pin = server.repository().find(pinId.get());
            if (pin.isEmpty()) {
                source.sendFailure(Component.translatable("echopins.error.pin_not_found"));
                return 0;
            }
            server.pins().deleteUnchecked(pin.get());
            server.playback().stopPlaybacksOf(pin.get().id());
            server.sync().broadcastRemoval(server.server(), pin.get().id());
            source.sendSuccess(() -> Component.translatable("echopins.command.deleted"), true);
            return 1;
        }).orElse(0);
    }

    private static int purgeExpired(CommandSourceStack source) {
        return withServer(source).map(server -> {
            int total = 0;
            // Repeated bounded batches rather than one unbounded sweep, so a huge backlog cannot
            // stall the server thread inside a single command.
            for (int pass = 0; pass < 64; pass++) {
                int removed = server.pins().removeExpiredBatch();
                total += removed;
                if (removed == 0) {
                    break;
                }
            }
            int finalTotal = total;
            source.sendSuccess(() ->
                    Component.translatable("echopins.command.purged", finalTotal), true);
            return finalTotal;
        }).orElse(0);
    }

    private static int purgePlayer(CommandSourceStack source,
                                   Collection<com.mojang.authlib.GameProfile> targets) {
        return withServer(source).map(server -> {
            int removed = 0;
            for (com.mojang.authlib.GameProfile profile : targets) {
                UUID uuid = profile.getId();
                if (uuid == null) {
                    continue;
                }
                // Copy first: deleting mutates the repository's author index while iterating.
                for (EchoPin pin : new ArrayList<>(server.repository().findByAuthor(uuid))) {
                    server.pins().deleteUnchecked(pin);
                    server.playback().stopPlaybacksOf(pin.id());
                    server.sync().broadcastRemoval(server.server(), pin.id());
                    removed++;
                }
            }
            int finalRemoved = removed;
            source.sendSuccess(() ->
                    Component.translatable("echopins.command.purged", finalRemoved), true);
            return finalRemoved;
        }).orElse(0);
    }

    private static int cleanup(CommandSourceStack source) {
        return withServer(source).map(server -> {
            server.sweepOrphanAudio();
            source.sendSuccess(() -> Component.translatable("echopins.command.cleanup_started"), true);
            return 1;
        }).orElse(0);
    }

    private static int reload(CommandSourceStack source) {
        return withServer(source).map(server -> {
            // Config values are read live, so a reload only needs to re-push the derived values
            // that clients cache.
            server.sync().broadcastSettings(server.server());
            source.sendSuccess(() -> Component.translatable("echopins.command.reloaded"), true);
            return 1;
        }).orElse(0);
    }

    private static int stats(CommandSourceStack source, boolean admin) {
        return withServer(source).map(server -> {
            ServerPlayer viewer = source.getPlayer();
            EchoPinsServer.Metrics metrics = server.metrics(viewer);

            source.sendSuccess(() -> Component.translatable("echopins.command.stats.header"), false);
            source.sendSuccess(() -> Component.translatable("echopins.command.stats.total",
                    metrics.totalPins()), false);
            source.sendSuccess(() -> Component.translatable("echopins.command.stats.dimension",
                    metrics.pinsInDimension()), false);
            if (viewer != null) {
                source.sendSuccess(() -> Component.translatable("echopins.command.stats.mine",
                        server.repository().countByAuthor(viewer.getUUID())), false);
            }
            if (admin) {
                source.sendSuccess(() -> Component.translatable("echopins.command.stats.recordings",
                        metrics.activeRecordings(), metrics.pendingRecordings()), false);
                source.sendSuccess(() -> Component.translatable("echopins.command.stats.playbacks",
                        metrics.activePlaybacks()), false);
                source.sendSuccess(() -> Component.translatable("echopins.command.stats.subscriptions",
                        metrics.subscriptions()), false);
                source.sendSuccess(() -> Component.translatable("echopins.command.stats.storage",
                        formatBytes(metrics.audioBytes())), false);
                source.sendSuccess(() -> Component.translatable("echopins.command.stats.index",
                        metrics.spatialBuckets(), metrics.readStateEntries()), false);
            }
            return 1;
        // withServer already reported the failure; repeating it here showed the player the same
        // message twice.
        }).orElse(0);
    }

    private static Optional<EchoPinsServer> withServer(CommandSourceStack source) {
        Optional<EchoPinsServer> server = EchoPinsServer.current();
        if (server.isEmpty()) {
            source.sendFailure(Component.translatable("echopins.error.disabled"));
        }
        return server;
    }

    private static Optional<PinId> parsePinId(String raw) {
        try {
            return Optional.of(PinId.of(UUID.fromString(raw.trim())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String formatDuration(int millis) {
        int totalSeconds = Math.max(0, millis) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0D);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0D * 1024.0D));
        }
        return String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0D * 1024.0D * 1024.0D));
    }
}
