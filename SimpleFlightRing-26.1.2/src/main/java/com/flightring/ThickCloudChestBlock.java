package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 厚云箱子方块：双箱合并、被挡住打不开、比较器输出等行为都沿用原版 {@link ChestBlock}，
 * 只把方块实体换成 {@link ThickCloudChestBlockEntity}，并把开关盖的声音换成我们自己的
 * （26.x 的 ChestBlock 构造函数直接收这两个 SoundEvent）。
 */
public class ThickCloudChestBlock extends ChestBlock {

    public ThickCloudChestBlock(Properties properties) {
        super(() -> ModBlockEntities.THICK_CLOUD_CHEST.get(),
                net.minecraft.sounds.SoundEvents.CHEST_OPEN, net.minecraft.sounds.SoundEvents.CHEST_CLOSE, properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThickCloudChestBlockEntity(pos, state);
    }
}
