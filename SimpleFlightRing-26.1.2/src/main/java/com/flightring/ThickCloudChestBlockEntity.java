package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 厚云箱子的方块实体。
 * <p>
 * 26.x 的 {@code ChestBlock} 构造函数直接接收开关音效（{@code openSound}/{@code closeSound}），
 * 所以这里不需要像 1.21.1 那样重写开合计数器 —— 只需要一个绑定到我们自己 {@code BlockEntityType}
 * 的箱子实体即可（原版 {@code ChestBlockEntity} 的公开构造固定用 {@code CHEST} 类型）。
 */
public class ThickCloudChestBlockEntity extends ChestBlockEntity {

    public ThickCloudChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THICK_CLOUD_CHEST.get(), pos, state);
    }
}
