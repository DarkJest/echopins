package dev.echopins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.echopins.client.hud.FocusedPinHud;
import dev.echopins.client.keybind.EchoPinsKeybinds;
import dev.echopins.client.render.PinMarkerRenderer;
import dev.echopins.client.screen.ConfirmPinScreen;
import dev.echopins.client.screen.InboxScreen;
import dev.echopins.client.state.ClientPinState;
import dev.echopins.domain.pin.PinId;
import dev.echopins.infrastructure.network.ClientPayloadReceiver;
import dev.echopins.infrastructure.network.EchoPinsNetwork;
import dev.echopins.infrastructure.network.PinSummary;
import dev.echopins.infrastructure.network.payload.ClientboundPayloads;
import dev.echopins.infrastructure.network.payload.ServerboundPayloads;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Optional;

/**
 * All client behaviour that is the same on every loader.
 *
 * <p>Key handling, playback control and the incoming-payload receiver live here. Each loader keeps
 * only the few lines needed to register keybinds, HUD layers and the tick and render callbacks,
 * and then calls into this class - so there is one implementation of what the client actually
 * does, not one per loader.
 */
public final class EchoPinsClientCore {

    private EchoPinsClientCore() {
    }

    /** True while the record key is held, so release can be detected exactly once. */
    private static boolean recordKeyHeld;




    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            recordKeyHeld = false;
            return;
        }

        handleRecordKey(minecraft);

        while (EchoPinsKeybinds.OPEN_INBOX.consumeClick()) {
            minecraft.setScreen(new InboxScreen());
        }
        while (EchoPinsKeybinds.PLAY_NEAREST.consumeClick()) {
            playFocusedOrNearest();
        }
    }

    /**
     * Push-to-record: the message lasts exactly as long as the key is held.
     *
     * <p>Release is detected from the key's own down state rather than from click events, so a
     * key released while a screen was open still ends the recording instead of leaving it running.
     */
    private static void handleRecordKey(Minecraft minecraft) {
        boolean down = EchoPinsKeybinds.CREATE_PIN.isDown();

        if (down && !recordKeyHeld) {
            recordKeyHeld = true;
            // Drain any queued clicks so a press is not also handled as a separate event.
            while (EchoPinsKeybinds.CREATE_PIN.consumeClick()) {
                // discard
            }
            EchoPinsNetwork.sendToServer(new ServerboundPayloads.BeginRecording(currentBlockTarget()));
            playCue(1.4F);
        } else if (!down && recordKeyHeld) {
            recordKeyHeld = false;
            if (ClientPinState.INSTANCE.recording().isActive()) {
                EchoPinsNetwork.sendToServer(new ServerboundPayloads.FinishRecording());
                playCue(0.9F);
            }
        }
    }

    /** The block the player is looking at, if any, as a hint for the server. */
    private static Optional<ServerboundPayloads.BlockTarget> currentBlockTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            return Optional.of(new ServerboundPayloads.BlockTarget(pos, blockHit.getDirection().ordinal()));
        }
        return Optional.empty();
    }

    /**
     * Plays whatever the player is plainly asking for.
     *
     * <p>The pin under the crosshair wins, because that is the one whose label is on screen and
     * the label is what says "press this to play". Only if nothing is focused does this fall back
     * to the nearest pin in range.
     *
     * <p>Read state is a tiebreak, never a filter. Filtering on it meant a player could never
     * replay their own message: creating a pin marks it read for its author immediately, so the
     * author's own pin was invisible to this action.
     */
    private static void playFocusedOrNearest() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        double interaction = ClientPinState.INSTANCE.settings().interactionRadius();

        PinSummary focused = PinMarkerRenderer.focusedPin(
                minecraft.gameRenderer.getMainCamera(),
                minecraft.player.getLookAngle().normalize(),
                interaction);
        if (focused != null) {
            togglePlayback(focused.id());
            return;
        }

        // Nothing under the crosshair. If something is playing, the obvious meaning of the key is
        // "stop that", so it does not become a one-way action with no way back.
        List<PinId> playing = ClientPinState.INSTANCE.playingPins();
        if (!playing.isEmpty()) {
            requestStop(playing.get(playing.size() - 1));
            return;
        }

        PinSummary best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        boolean bestUnread = false;

        for (PinSummary pin : ClientPinState.INSTANCE.pins()) {
            double dx = pin.anchor().renderPos().x() - minecraft.player.getX();
            double dy = pin.anchor().renderPos().y() - minecraft.player.getEyeY();
            double dz = pin.anchor().renderPos().z() - minecraft.player.getZ();
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq > interaction * interaction) {
                continue;
            }
            // An unheard message beats a heard one; among equals, the closer one wins.
            boolean better = best == null
                    || (pin.unread() && !bestUnread)
                    || (pin.unread() == bestUnread && distanceSq < bestDistanceSq);
            if (better) {
                best = pin;
                bestDistanceSq = distanceSq;
                bestUnread = pin.unread();
            }
        }

        if (best != null) {
            togglePlayback(best.id());
        } else {
            // Saying nothing at all reads as a broken keybind, so be explicit.
            minecraft.player.displayClientMessage(
                    Component.translatable("echopins.hud.nothing_in_range"), true);
        }
    }

    public static void requestPlayback(PinId pin) {
        EchoPinsNetwork.sendToServer(new ServerboundPayloads.RequestPlayback(pin));
    }

    public static void requestStop(PinId pin) {
        EchoPinsNetwork.sendToServer(new ServerboundPayloads.StopPlayback(pin));
        // Clear locally too, so the indicator reacts immediately rather than after the round trip.
        ClientPinState.INSTANCE.clearPlaying(pin);
    }

    /** Plays the pin, or stops it if it is already playing. */
    private static void togglePlayback(PinId pin) {
        if (ClientPinState.INSTANCE.isPlaying(pin)) {
            requestStop(pin);
        } else {
            requestPlayback(pin);
        }
    }

    /**
     * Draws the world markers. Called by each loader from its own once-per-frame world render
     * hook, after particles, so markers sit on top of the world but behind the HUD.
     */
    public static void onRenderLevel(PoseStack poseStack, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PinMarkerRenderer.render(poseStack, buffers, camera);
    }

    private static void playCue(float pitch) {
        if (!ClientSettings.Holder.get().recordingCues()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            // Vanilla sounds only: a short, quiet pling that cannot be mistaken for a game event.
            minecraft.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.35F, pitch);
        }
    }

    private static void playNotification() {
        if (!ClientSettings.Holder.get().notificationSounds()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 0.22F, 1.7F);
        }
    }

    /** Applies server messages to the client cache and UI. */
    public static final class Receiver implements ClientPayloadReceiver {

        @Override
        public void onServerSettings(ClientboundPayloads.ServerSettings payload) {
            ClientPinState.INSTANCE.setSettings(new ClientPinState.Settings(
                    payload.discoveryRadius(), payload.interactionRadius(),
                    payload.maxRecordingSeconds(), payload.minRecordingMillis(),
                    payload.maxCaptionLength(), payload.maxPrivateRecipients(),
                    payload.allowPermanentPins()));
        }

        @Override
        public void onSnapshot(ClientboundPayloads.PinsSnapshot payload) {
            ClientPinState.INSTANCE.replaceAll(payload.pins());
        }

        @Override
        public void onDelta(ClientboundPayloads.PinsDelta payload) {
            boolean hadNewUnread = payload.added().stream().anyMatch(PinSummary::unread);
            ClientPinState.INSTANCE.applyDelta(payload.added(), payload.removed());
            if (hadNewUnread) {
                playNotification();
            }
        }

        @Override
        public void onRecordingState(ClientboundPayloads.RecordingState payload) {
            ClientPinState.Recording recording = new ClientPinState.Recording(
                    payload.phase(), payload.elapsedMillis(), payload.maxMillis(),
                    payload.receivingAudio());
            ClientPinState.INSTANCE.setRecording(recording);

            Minecraft minecraft = Minecraft.getInstance();
            if (recording.isAwaitingConfirmation() && !(minecraft.screen instanceof ConfirmPinScreen)) {
                minecraft.setScreen(new ConfirmPinScreen(recording.elapsedMillis()));
            } else if (recording.phase() == ClientboundPayloads.RecordingPhase.IDLE
                    && minecraft.screen instanceof ConfirmPinScreen) {
                // The server ended the pending recording (timeout, cancel, or a rejected save).
                minecraft.setScreen(null);
            }
        }

        @Override
        public void onPlaybackState(ClientboundPayloads.PlaybackState payload) {
            switch (payload.phase()) {
                case STARTED -> {
                    ClientPinState.INSTANCE.setPlaying(payload.pin(), payload.durationMillis());
                    ClientPinState.INSTANCE.markReadLocally(payload.pin());
                }
                case FINISHED, STOPPED -> ClientPinState.INSTANCE.clearPlaying(payload.pin());
            }
        }

        @Override
        public void onError(ClientboundPayloads.ErrorMessage payload) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            Component message = payload.argument()
                    .map(argument -> Component.translatable(payload.error().translationKey(),
                            Math.max(1L, argument / 1000L)))
                    .orElseGet(() -> Component.translatable(payload.error().translationKey()));
            // The action bar rather than chat: an EchoPins problem should not push someone's
            // conversation off the screen.
            minecraft.player.displayClientMessage(message, true);

            // A refused save leaves the confirmation screen open so the player can retry rather
            // than losing a message they already spoke.
            if (minecraft.screen instanceof ConfirmPinScreen confirm && confirm.isSubmitting()) {
                confirm.onSaveRejected();
            }
        }

        @Override
        public void onInboxPage(ClientboundPayloads.InboxPage payload) {
            ClientPinState.INSTANCE.setInboxPage(payload);
            // The screen builds its per-entry buttons from the cached page, so it has to be told
            // that a page arrived; otherwise the first page renders rows with no buttons.
            if (Minecraft.getInstance().screen instanceof InboxScreen inbox) {
                inbox.onPageReceived(payload);
            }
        }

        @Override
        public void onKnownPlayers(ClientboundPayloads.KnownPlayers payload) {
            ClientPinState.INSTANCE.setKnownPlayers(payload.players());
        }
    }
}
