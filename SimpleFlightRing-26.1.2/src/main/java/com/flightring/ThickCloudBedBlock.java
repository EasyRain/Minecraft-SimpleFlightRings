package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 厚云床方块：睡觉、爆炸、村民认床等行为全部沿用原版 {@link BedBlock}，只换方块实体。
 * 颜色固定白色（贴图只有一张厚云版）。
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
