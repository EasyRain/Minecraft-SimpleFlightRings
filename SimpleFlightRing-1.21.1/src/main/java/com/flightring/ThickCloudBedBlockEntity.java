package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 厚云床的方块实体。
 * <p>
 * 为什么必须覆写 {@code getType()}：{@code BedBlockEntity} 的两个构造函数都写死了
 * {@code BlockEntityType.BED}，没有接收自定义类型的构造；而方块实体类型必须"认领"自己的方块
 * （{@code LevelChunk} 会用 {@code type.isValid(state)} 校验，不认的会被丢掉）。
 * <p>
 * 原版 {@code BlockEntity#getType()} 不是 final，NeoForge 还专门在注释里写了
 * "always use getType() so the correct type is checked for modded subclasses"，
 * 所以这里先按原版构造初始化（顺带从方块取到颜色），再让 {@code getType()} 返回我们自己的类型。
 */
public class ThickCloudBedBlockEntity extends BedBlockEntity {

    public ThickCloudBedBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        this.setColor(((BedBlock) state.getBlock()).getColor());
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntities.THICK_CLOUD_BED.get();
    }
}
