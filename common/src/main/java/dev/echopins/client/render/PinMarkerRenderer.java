package dev.echopins.client.render;

import dev.echopins.client.ClientSettings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.echopins.EchoPins;
import dev.echopins.client.state.ClientPinState;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.visibility.Visibility;
import dev.echopins.infrastructure.network.PinSummary;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws EchoPin markers in the world.
 *
 * <p>Kept intentionally quiet: a small billboarded icon that fades in as you approach and never
 * grows into a beacon. Three things keep it cheap and unobtrusive:
 *
 * <ul>
 *   <li>Only pins the server already sent are considered, and they are sorted by distance and
 *       capped, so the number of quads drawn has a hard ceiling regardless of world size.</li>
 *   <li>The icon scales with distance so it stays legible without ever dominating the view.</li>
 *   <li>Alpha falls off towards the render limit rather than markers popping out of existence.</li>
 * </ul>
 */
public final class PinMarkerRenderer {

    private static final ResourceLocation MARKER_TEXTURE =
            new ResourceLocation(EchoPins.MOD_ID, "textures/gui/pin_marker.png");
    private static final ResourceLocation MARKER_PRIVATE_TEXTURE =
            new ResourceLocation(EchoPins.MOD_ID, "textures/gui/pin_marker_private.png");

    /** Base half-size of the billboard, in blocks. */
    private static final float BASE_HALF_SIZE = 0.22F;

    /** Distance at which a marker is fully opaque. */
    private static final double FULL_OPACITY_DISTANCE = 12.0D;

    /** Within this distance a marker may be drawn through terrain, if configured. */
    private static final double SEE_THROUGH_DISTANCE = 10.0D;

    private static final float MIN_ALPHA = 0.06F;

    /** One full ripple cycle while a pin is playing, in milliseconds. */
    private static final long PLAYBACK_RIPPLE_PERIOD = 1100L;
    /** How far the ripple expands past the marker at the end of a cycle. */
    private static final float PLAYBACK_RIPPLE_SCALE = 1.9F;
    /** Constant enlargement applied while playing, so the state reads without motion too. */
    private static final float PLAYBACK_MARKER_SCALE = 1.18F;

    private PinMarkerRenderer() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Camera camera) {
        if (!ClientSettings.Holder.get().showMarkers()) {
            return;
        }
        ClientPinState state = ClientPinState.INSTANCE;
        if (state.pinCount() == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options.hideGui) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        // The client's own render distance is a preference, but it can never exceed what the
        // server was willing to tell us about.
        double maxDistance = Math.min(
                ClientSettings.Holder.get().markerRenderDistance(),
                state.settings().discoveryRadius());
        double maxDistanceSq = maxDistance * maxDistance;

        List<PinSummary> visible = new ArrayList<>();
        for (PinSummary pin : state.pins()) {
            WorldPos pos = pin.anchor().renderPos();
            double dx = pos.x() - cameraPos.x;
            double dy = pos.y() - cameraPos.y;
            double dz = pos.z() - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz <= maxDistanceSq) {
                visible.add(pin);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        visible.sort(Comparator.comparingDouble(pin -> distanceSq(pin, cameraPos)));
        int limit = Math.min(visible.size(), ClientSettings.Holder.get().maxRenderedMarkers());

        float opacity = (float) ClientSettings.Holder.get().markerOpacity();
        float scale = (float) ClientSettings.Holder.get().markerScale();
        Quaternionf billboard = camera.rotation();

        for (int i = 0; i < limit; i++) {
            PinSummary pin = visible.get(i);
            double distance = Math.sqrt(distanceSq(pin, cameraPos));
            float alpha = alphaFor(distance, maxDistance) * opacity;
            if (alpha <= MIN_ALPHA) {
                continue;
            }
            drawMarker(poseStack, buffers, pin, cameraPos, billboard, distance, scale, alpha);
        }
        buffers.endBatch();
    }

    private static void drawMarker(PoseStack poseStack, MultiBufferSource buffers, PinSummary pin,
                                   Vec3 cameraPos, Quaternionf billboard, double distance,
                                   float scale, float alpha) {
        WorldPos pos = pin.anchor().renderPos();

        poseStack.pushPose();
        poseStack.translate(pos.x() - cameraPos.x, pos.y() - cameraPos.y, pos.z() - cameraPos.z);

        if (!ClientSettings.Holder.get().reduceMotion() && pin.unread()) {
            // A very small bob, only on messages you have not heard yet, so an unheard pin draws
            // the eye without anything on screen ever being busy.
            float bob = Mth.sin((System.currentTimeMillis() % 2400L) / 2400.0F * Mth.TWO_PI) * 0.03F;
            poseStack.translate(0.0D, bob, 0.0D);
        }

        poseStack.mulPose(billboard);
        // Grow slightly with distance so a far marker stays readable without a near one becoming
        // huge; the square root keeps the growth gentle.
        float sizeFactor = (float) (1.0D + Math.sqrt(Math.max(0.0D, distance)) * 0.16D);
        boolean playing = ClientPinState.INSTANCE.isPlaying(pin.id());
        if (playing) {
            // A size change as well as the ripple, so "this one is talking" is not carried by
            // animation alone - it still reads with reduceMotion on, and without relying on colour.
            sizeFactor *= PLAYBACK_MARKER_SCALE;
        }
        float half = BASE_HALF_SIZE * scale * sizeFactor;
        // Uniformly positive on purpose. Scaling a single axis negatively flips the determinant,
        // which reverses triangle winding; the text render type does not disable culling, so the
        // quad would silently become back-facing and be discarded with no error anywhere. The
        // texture is oriented by the UVs below instead.
        poseStack.scale(half, half, half);

        ResourceLocation texture = pin.visibility() == Visibility.PRIVATE
                ? MARKER_PRIVATE_TEXTURE
                : MARKER_TEXTURE;
        boolean seeThrough = shouldSeeThrough(distance);
        RenderType renderType = seeThrough
                ? RenderType.textSeeThrough(texture)
                : RenderType.text(texture);

        VertexConsumer consumer = buffers.getBuffer(renderType);
        Matrix4f matrix = poseStack.last().pose();
        int packedAlpha = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        int colour = (packedAlpha << 24) | 0x00FFFFFF;
        int light = 0x00F0_00F0;

        if (playing && !ClientSettings.Holder.get().reduceMotion()) {
            // An expanding, fading copy of the marker behind it: a sound ripple. Drawn first so
            // the marker itself stays crisp on top.
            float phase = (System.currentTimeMillis() % PLAYBACK_RIPPLE_PERIOD)
                    / (float) PLAYBACK_RIPPLE_PERIOD;
            float rippleScale = 1.0F + phase * (PLAYBACK_RIPPLE_SCALE - 1.0F);
            float rippleAlpha = alpha * (1.0F - phase) * 0.5F;
            if (rippleAlpha > 0.01F) {
                poseStack.pushPose();
                poseStack.scale(rippleScale, rippleScale, 1.0F);
                int rippleColour = ((int) (rippleAlpha * 255.0F) << 24) | 0x00FFFFFF;
                quad(consumer, poseStack.last().pose(), rippleColour, light);
                poseStack.popPose();
            }
        }

        quad(consumer, matrix, colour, light);
        poseStack.popPose();
    }

    /**
     * Emits the billboard quad.
     *
     * <p>Vertices run anticlockwise as seen from the camera, which keeps the quad front-facing
     * under the text render type's culling. UVs put v=1 at the bottom so the icon is upright
     * without needing a negative scale.
     *
     * <p>Only the elements the {@code POSITION_COLOR_TEX_LIGHTMAP} format actually has are
     * written. {@code setOverlay} and {@code setNormal} would silently no-op here - the buffer
     * skips elements the format lacks - so writing them just implied a lighting model that does
     * not exist.
     */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, int colour, int light) {
        int alpha = colour >>> 24;
        int red = colour >>> 16 & 0xFF;
        int green = colour >>> 8 & 0xFF;
        int blue = colour & 0xFF;
        consumer.vertex(matrix, -1.0F, -1.0F, 0.0F).color(red, green, blue, alpha)
                .uv(0.0F, 1.0F).uv2(light).endVertex();
        consumer.vertex(matrix, 1.0F, -1.0F, 0.0F).color(red, green, blue, alpha)
                .uv(1.0F, 1.0F).uv2(light).endVertex();
        consumer.vertex(matrix, 1.0F, 1.0F, 0.0F).color(red, green, blue, alpha)
                .uv(1.0F, 0.0F).uv2(light).endVertex();
        consumer.vertex(matrix, -1.0F, 1.0F, 0.0F).color(red, green, blue, alpha)
                .uv(0.0F, 0.0F).uv2(light).endVertex();
    }

    private static boolean shouldSeeThrough(double distance) {
        return switch (ClientSettings.Holder.get().occlusionMode()) {
            case ALWAYS_OCCLUDE -> false;
            case NEVER_OCCLUDE -> true;
            // The conservative default: a pin behind a wall is only revealed once you are close
            // enough to interact with it anyway, so markers never act as an x-ray tool.
            case SHOW_THROUGH_WALLS_NEARBY -> distance <= SEE_THROUGH_DISTANCE;
        };
    }

    private static float alphaFor(double distance, double maxDistance) {
        if (distance <= FULL_OPACITY_DISTANCE) {
            return 1.0F;
        }
        double span = Math.max(1.0D, maxDistance - FULL_OPACITY_DISTANCE);
        double faded = 1.0D - (distance - FULL_OPACITY_DISTANCE) / span;
        return (float) Mth.clamp(faded, 0.0D, 1.0D);
    }

    private static double distanceSq(PinSummary pin, Vec3 cameraPos) {
        WorldPos pos = pin.anchor().renderPos();
        double dx = pos.x() - cameraPos.x;
        double dy = pos.y() - cameraPos.y;
        double dz = pos.z() - cameraPos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * How far the crosshair may miss a marker's centre and still count as aiming at it, in blocks.
     * Roughly the visual radius of a marker plus a little forgiveness.
     */
    private static final double AIM_TOLERANCE = 0.75D;

    /**
     * The pin the crosshair is on, used for the label and the play action.
     *
     * <p>Selection is by perpendicular distance from the view ray, not by angle. A fixed angular
     * cone behaves backwards up close: standing right next to a marker, a few centimetres of aim
     * error becomes a large angle, so the pin you are practically touching becomes the hardest one
     * to select. Measuring distance from the ray keeps the tolerance constant in world space,
     * which is what a player actually expects.
     */
    public static PinSummary focusedPin(Camera camera, Vec3 lookDirection, double maxDistance) {
        ClientPinState state = ClientPinState.INSTANCE;
        Vec3 cameraPos = camera.getPosition();
        PinSummary best = null;
        double bestPerpendicular = AIM_TOLERANCE;

        for (PinSummary pin : state.pins()) {
            WorldPos pos = pin.anchor().renderPos();
            Vec3 toPin = new Vec3(pos.x() - cameraPos.x, pos.y() - cameraPos.y, pos.z() - cameraPos.z);
            double length = toPin.length();
            if (length > maxDistance || length < 1.0E-4D) {
                continue;
            }
            double along = toPin.dot(lookDirection);
            if (along <= 0.0D) {
                // Behind the camera.
                continue;
            }
            double perpendicular = toPin.subtract(lookDirection.scale(along)).length();
            if (perpendicular < bestPerpendicular) {
                bestPerpendicular = perpendicular;
                best = pin;
            }
        }
        return best;
    }
}
