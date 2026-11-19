package com.flightring;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 云块 (cloud block): the soft outer skin of every cloud body in {@code simpleflightring:the_cloud}.
 * <p>
 * Modelled on vanilla cobweb: no collision, so an entity that steps on it slowly sinks into the block
 * ({@code makeStuckInBlock} drops the movement speed to 40% horizontally and 8% vertically) and finally
 * falls through. Because {@code makeStuckInBlock} also resets the fall distance, sinking into a cloud
 * never hurts - the player lands softly on the solid thick cloud core underneath.
 * <p>
 * <b>The flight ring exception</b> (user 2026-11-19): while the player carries <i>any</i> flight ring
 * that still has flight time left, the block is as solid as any normal block and can be walked on
 * without costing durability - the exact counterpart of vanilla's "leather boots on powder snow".
 * That is implemented the same way powder snow does it: {@link #getCollisionShape} only returns the
 * full box for an entity that may walk on it, and only when it is standing on top.
 */
public class CloudBlock extends Block {

    public static final MapCodec<CloudBlock> CODEC = simpleCodec(CloudBlock::new);

    /** Sink speed while inside: 40% horizontal, 8% vertical (cobweb is 25% / 5%). */
    private static final Vec3 SINK_SPEED = new Vec3(0.4D, 0.08D, 0.4D);

    public CloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * Solid only for a player who carries a usable flight ring, and only while that player stands on
     * top of the block (the powder-snow rule: no climbing the sides, no pushing into the block).
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity instanceof Player player
                    && FlightHandler.hasUsableRing(player)
                    && context.isAbove(Shapes.block(), pos, false)
                    && !context.isDescending()) {
                return super.getCollisionShape(state, level, pos, context);
            }
        }
        return Shapes.empty();
    }

    /** Everyone else slowly sinks through, exactly like cobweb (which also resets the fall distance). */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity instanceof Player player && FlightHandler.hasUsableRing(player)) {
            return;
        }
        entity.makeStuckInBlock(state, SINK_SPEED);
    }
}
