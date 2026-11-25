package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 云层花草（云绒草 / 云絮花 / 极光花）的共用方块。
 * <p>
 * 只有一条特殊行为：**能长在云层的地表上**（厚云块、云块），以及原版的各种泥土 ——
 * 判定统一走 {@link ModTags#CLOUD_SOIL}，两版的标签差异放在 JSON 里。
 */
public class CloudPlantBlock extends BushBlock {

    public CloudPlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModTags.CLOUD_SOIL);
    }
}
