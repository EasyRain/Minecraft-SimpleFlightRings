package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

/** 云木悬挂告示牌（吊顶式）。 */
public class CloudHangingSignBlock extends CeilingHangingSignBlock {

    public CloudHangingSignBlock(WoodType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CloudHangingSignBlockEntity(pos, state);
    }
}
