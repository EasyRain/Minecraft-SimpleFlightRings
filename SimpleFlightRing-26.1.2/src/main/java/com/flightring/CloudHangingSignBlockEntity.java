package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 云木悬挂告示牌的方块实体。
 * <p>
 * 26.x 的 {@code HangingSignBlockEntity} 同样只有 {@code (BlockPos, BlockState)} 构造、类型写死，
 * 所以覆写 {@code getType()} 换成自己的类型（和 1.21.1 一致）。
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
