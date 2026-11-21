package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

/** 云木墙式告示牌：方块实体换成 {@link CloudSignBlockEntity}。 */
public class CloudWallSignBlock extends WallSignBlock {

    public CloudWallSignBlock(WoodType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CloudSignBlockEntity(pos, state);
    }
}
