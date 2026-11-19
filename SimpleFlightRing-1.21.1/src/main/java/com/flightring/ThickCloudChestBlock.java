package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 厚云箱子方块：普通箱子的一切行为（双箱合并、被猫/方块挡住时打不开、水logged、比较器输出）
 * 都沿用原版 {@link ChestBlock}，只把方块实体换成会播我们自己开关声的
 * {@link ThickCloudChestBlockEntity}。
 */
public class ThickCloudChestBlock extends ChestBlock {

    public ThickCloudChestBlock(Properties properties) {
        super(properties, () -> ModBlockEntities.THICK_CLOUD_CHEST.get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThickCloudChestBlockEntity(pos, state);
    }
}
