package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 云木悬挂告示牌的方块实体。
 * <p>
 * 1.21.1 的 {@code HangingSignBlockEntity} 只有 {@code (BlockPos, BlockState)} 构造，
 * 类型是写死的 {@code BlockEntityType.HANGING_SIGN} —— 和告示牌一样只认原版方块，
 * 所以这里覆写 {@code getType()} 换成我们自己的类型，并把 {@code isValidBlockState}
 * 交给它判断（参考永恒星光 mod 的 ESSignBlockEntity 做法）。
 */
public class CloudHangingSignBlockEntity extends HangingSignBlockEntity {

    public CloudHangingSignBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntities.CLOUD_HANGING_SIGN.get();
    }

    @Override
    public boolean isValidBlockState(BlockState state) {
        return getType().isValid(state);
    }
}
