package com.flightring;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

/**
 * 厚云块。
 * <p>
 * 平常就是个实心方块，唯一的特殊行为是 {@code onTreeGrow}：原版 {@code TrunkPlacer#setDirtAt}
 * 在种树时会把树干下方的方块换成 {@code dirt_provider}（默认是泥土），
 * 于是每棵云树下面都会多出一块"土"。这里直接认领这个位置，让树**直接长在厚云块上**，
 * 不再额外放任何方块（用户 2026-11-21）。
 */
public class ThickCloudBlock extends Block {

    public ThickCloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onTreeGrow(BlockState state, LevelReader level, BiConsumer<BlockPos, BlockState> posSetter,
                              RandomSource random, BlockPos pos, TreeConfiguration config) {
        return true;   // 这个位置已经合适，不需要替换成 dirt_provider
    }
}
