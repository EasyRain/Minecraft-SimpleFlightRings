package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.DyeColor;

/**
 * 厚云床方块：睡觉、爆炸、村民认床等行为全部沿用原版 {@link BedBlock}，
 * 只把方块实体换成 {@link ThickCloudBedBlockEntity}。
 * <p>
 * 颜色固定用白色 —— 贴图只有一张（厚云），颜色只影响原版的染色材质选择。
 */
public class ThickCloudBedBlock extends BedBlock {

    public ThickCloudBedBlock(Properties properties) {
        super(DyeColor.WHITE, properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThickCloudBedBlockEntity(pos, state);
    }
}
