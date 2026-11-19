package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 厚云床的方块实体：构造照原版（拿颜色），{@code getType()} 换成我们自己的类型。
 * <p>
 * 原因同 1.21.1：{@code BedBlockEntity} 的构造写死了 {@code BlockEntityType.BED}，
 * 而方块实体类型必须认领自己的方块（{@code LevelChunk} 会用 {@code type.isValid(state)} 校验）。
 * {@code BlockEntity#getType()} 不是 final，覆写即可。
 */
public class ThickCloudBedBlockEntity extends BedBlockEntity {

    public ThickCloudBedBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);   // 原版构造会从方块上取颜色，26.x 没有 setColor，无需再设
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntities.THICK_CLOUD_BED.get();
    }
}
