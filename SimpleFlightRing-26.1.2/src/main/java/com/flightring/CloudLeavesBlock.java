package com.flightring;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 云叶。
 * <p>
 * 26.x 的 {@link LeavesBlock} 是抽象类（多了 {@code codec()} 与 {@code spawnFallingLeavesParticle}），
 * 所以这里做一个最小实现：不生成飘落粒子（云叶要安静），其余（腐烂、剪切、含水）全部继承。
 */
public class CloudLeavesBlock extends LeavesBlock {

    public static final MapCodec<CloudLeavesBlock> CODEC = simpleCodec(CloudLeavesBlock::new);

    public CloudLeavesBlock(BlockBehaviour.Properties properties) {
        super(0.01F, properties);
    }

    @Override
    public MapCodec<? extends LeavesBlock> codec() {
        return CODEC;
    }

    @Override
    protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {
        // 云叶不飘落粒子：这个维度本来就该安静
    }
}
