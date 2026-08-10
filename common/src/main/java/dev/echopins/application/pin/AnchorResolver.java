package dev.echopins.application.pin;

import dev.echopins.application.ServerLimits;
import dev.echopins.domain.anchor.BlockAnchor;
import dev.echopins.domain.anchor.BlockFace;
import dev.echopins.domain.anchor.DimensionId;
import dev.echopins.domain.anchor.PositionAnchor;
import dev.echopins.domain.anchor.WorldAnchor;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.error.EchoPinError;
import dev.echopins.domain.error.EchoPinException;
import dev.echopins.infrastructure.network.payload.ServerboundPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

/**
 * Turns a client's "I am looking at this" hint into a validated anchor.
 *
 * <p>The client's suggestion is treated purely as a hint. The dimension always comes from the
 * player's actual level, the distance is always re-measured against the player's actual position,
 * and a block target that is out of reach is rejected rather than clamped. That is what stops a
 * modified client from planting a pin in another dimension or a hundred thousand blocks away.
 */
public final class AnchorResolver {

    /** How far in front of the player a free-floating pin is placed when no block was hit. */
    private static final double FREE_PLACEMENT_DISTANCE = 2.0D;

    private final ServerLimits limits;

    public AnchorResolver(ServerLimits limits) {
        this.limits = limits;
    }

    /**
     * @param player      the requesting player, the sole source of dimension and position truth
     * @param blockTarget the client's hint, if any
     * @throws EchoPinException if the requested anchor is not usable
     */
    public WorldAnchor resolve(ServerPlayer player, Optional<ServerboundPayloads.BlockTarget> blockTarget) {
        DimensionId dimension = dimensionOf(player);
        Vec3 eye = player.getEyePosition();

        if (blockTarget.isPresent()) {
            ServerboundPayloads.BlockTarget target = blockTarget.get();
            BlockPos pos = target.pos();

            HitResult serverHit = player.pick(limits.maxCreationDistance(), 0.0F, false);
            if (!(serverHit instanceof BlockHitResult blockHit)
                    || serverHit.getType() != HitResult.Type.BLOCK
                    || !blockHit.getBlockPos().equals(pos)) {
                throw new EchoPinException(EchoPinError.CANNOT_CREATE_HERE,
                        "Client block target " + pos + " did not match the server ray trace");
            }

            if (!player.level().isInWorldBounds(pos)) {
                throw new EchoPinException(EchoPinError.CANNOT_CREATE_HERE,
                        "Block target " + pos + " is outside world bounds");
            }

            double distance = Math.sqrt(pos.distToCenterSqr(eye));
            if (distance > limits.maxCreationDistance()) {
                throw new EchoPinException(EchoPinError.CANNOT_CREATE_HERE,
                        "Block target is " + String.format("%.1f", distance)
                                + " blocks away, limit is " + limits.maxCreationDistance());
            }

            // An anchor on a block that is not there would render floating in mid-air and could
            // not be found again, so an empty target is treated as no target at all.
            if (player.level().getBlockState(pos).isAir()) {
                throw new EchoPinException(EchoPinError.CANNOT_CREATE_HERE,
                        "Block target " + pos + " is air");
            }

            BlockFace face = toBlockFace(blockHit.getDirection());
            try {
                return new BlockAnchor(dimension, pos.getX(), pos.getY(), pos.getZ(), face);
            } catch (IllegalArgumentException e) {
                throw new EchoPinException(EchoPinError.CANNOT_CREATE_HERE,
                        "Rejected block anchor: " + e.getMessage());
            }
        }

        Vec3 look = player.getLookAngle().normalize();
        Vec3 placement = eye.add(look.scale(FREE_PLACEMENT_DISTANCE));
        try {
            return new PositionAnchor(dimension, new WorldPos(placement.x, placement.y, placement.z));
        } catch (IllegalArgumentException e) {
            throw new EchoPinException(EchoPinError.CANNOT_CREATE_HERE,
                    "Rejected position anchor: " + e.getMessage());
        }
    }

    /** Whether the player is close enough to the anchor to interact with it. */
    public boolean isWithinInteractionRange(ServerPlayer player, WorldAnchor anchor) {
        if (!dimensionOf(player).equals(anchor.dimension())) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        WorldPos target = anchor.renderPos();
        double dx = eye.x - target.x();
        double dy = eye.y - target.y();
        double dz = eye.z - target.z();
        double radius = limits.interactionRadius();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public static DimensionId dimensionOf(ServerPlayer player) {
        ResourceLocation location = player.level().dimension().location();
        return DimensionId.of(location.getNamespace(), location.getPath());
    }

    private static BlockFace toBlockFace(Direction direction) {
        return switch (direction) {
            case DOWN -> BlockFace.DOWN;
            case UP -> BlockFace.UP;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            case EAST -> BlockFace.EAST;
        };
    }
}
