package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

/** 云木悬挂告示牌（墙式）。 */
public class CloudWallHangingSignBlock extends WallHangingSignBlock {

    public CloudWallHangingSignBlock(WoodType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CloudHangingSignBlockEntity(pos, state);
    }
}
