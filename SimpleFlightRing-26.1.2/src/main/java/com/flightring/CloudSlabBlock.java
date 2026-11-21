package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 云块的半砖变体。
 * <p>
 * 形状用原版逻辑（半砖/阶梯/墙的拼装都在父类里），但**碰撞与下陷完全照云块**：
 * 没戴飞行戒指的玩家、或不在云维度的生物，会被当成没有碰撞体积而缓慢下陷 ——
 * 这样云块的"穿透"特性在变体上也保留（用户 2026-11-21）。
 */
public class CloudSlabBlock extends SlabBlock {

    public CloudSlabBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity != null && CloudBlock.mayStandOnCloud(level, entity)
                    && context.isAbove(Shapes.block(), pos, true) && !context.isDescending()) {
                return super.getCollisionShape(state, level, pos, context);
            }
        }
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                net.minecraft.world.entity.InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (CloudBlock.mayStandOnCloud(level, entity)) {
            return;
        }
        entity.makeStuckInBlock(state, net.minecraft.world.phys.Vec3.ZERO);
    }
}
