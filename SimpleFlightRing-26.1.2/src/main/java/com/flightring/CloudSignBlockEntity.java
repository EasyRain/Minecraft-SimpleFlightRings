package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 云木告示牌的方块实体。
 * <p>
 * 原版 {@code BlockEntityType.SIGN} 只登记了原版的那些告示牌，把我们的方块塞进去会在
 * {@code BlockEntity.validateBlockState} 里直接抛 {@code IllegalStateException}（放下就崩）。
 * 所以云木告示牌用自己的 {@code BlockEntityType}，渲染仍复用原版的 {@code SignRenderer}。
 */
public class CloudSignBlockEntity extends SignBlockEntity {

    public CloudSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLOUD_SIGN.get(), pos, state);
    }
}
