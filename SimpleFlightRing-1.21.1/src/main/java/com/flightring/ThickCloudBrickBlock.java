package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 厚云砖块。
 * <p>
 * 除了是建筑方块，它还是**云门的门框**：覆写 NeoForge 的 {@code isPortalFrame}，
 * 让 {@link BrokenPortalFrames} 那套已经跑通的量框逻辑（原版下界门也是用它）把厚云砖认成框架，
 * 于是点火只需要复用现成代码，不必再手写一遍"2×3 空位"的几何判断。
 */
public class ThickCloudBrickBlock extends Block {

    public ThickCloudBrickBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isPortalFrame(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
