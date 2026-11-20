package com.flightring;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 云门 —— 建在厚云砖框里的传送门方块。
 * <p>
 * 行为照原版下界门：站在里面一小会儿就被送走；进的是 {@code simpleflightring:the_cloud}，
 * 从云维度进去则送回主世界。
 * <p>
 * 与下界门不同的是**不自我校验**：拆掉框不会让门消失（框本来就是装饰性的）。
 * 这样"到达点"只需要放两个门方块就能用，返程不必每次都搭一座 14 块厚云砖的框。
 */
public class CloudPortalBlock extends Block implements Portal {

    public static final MapCodec<CloudPortalBlock> CODEC = simpleCodec(CloudPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    protected static final VoxelShape X_AXIS_AABB = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    protected static final VoxelShape Z_AXIS_AABB = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    public CloudPortalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    public MapCodec<CloudPortalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? X_AXIS_AABB : Z_AXIS_AABB;
    }

    /**
     * 拆掉门框或任意一格门，整座门就消失 —— 和原版下界门一致。
     * <p>
     * 判定复用 {@link BrokenPortalFrames}（厚云砖通过 {@code isPortalFrame} 被认成框架）：
     * 量出门洞矩形后，只要框不完整或门洞没被填满，这一格就变回空气，其余格子会因为各自的
     * 邻居更新跟着消失。
     */
    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level,
                                     net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbourState,
                                     net.minecraft.util.RandomSource random) {
        if (BrokenPortalFrames.isBulkEditing()) {
            return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
        }
        BrokenPortalFrames.Rect rect = BrokenPortalFrames.measure(level, pos, state.getValue(AXIS));
        if (rect == null) {
            // 还在被点亮的过程中（还没量出完整门洞）：先留着
            return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
        }
        return BrokenPortalFrames.isComplete(level, rect)
                ? super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random)
                : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                net.minecraft.world.entity.InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player player
                ? Math.max(1, level.getGameRules().get(player.getAbilities().invulnerable
                        ? net.minecraft.world.level.gamerules.GameRules.PLAYERS_NETHER_PORTAL_CREATIVE_DELAY
                        : net.minecraft.world.level.gamerules.GameRules.PLAYERS_NETHER_PORTAL_DEFAULT_DELAY))
                : 0;
    }

    @Nullable
    @Override
    public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        return level.dimension().equals(CloudPortalTravel.CLOUD_DIMENSION)
                ? CloudPortalTravel.backToOrigin(level, entity, pos)
                : CloudPortalTravel.toCloud(level, entity, pos);
    }

    @Override
    public Portal.Transition getLocalTransition() {
        return Portal.Transition.CONFUSION;
    }

}
