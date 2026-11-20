package com.flightring;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;

/**
 * 云门：**直接继承 {@link BrokenPortalBlock}**，只把目的维度换成 {@code the_cloud}。
 * <p>
 * 用户 2026-11-20 的意见很对 —— 破碎门那套（量框、自我校验、找现有门、建门、坐标 1:1）已经跑通，
 * 云门没有任何理由重写一遍。所以这里只剩一个 {@code getPortalDestination} 的差别，
 * 其余行为（含"拆框就整座消失"）全部继承。
 */
public class CloudPortalBlock extends BrokenPortalBlock {

    public static final MapCodec<BrokenPortalBlock> CODEC = simpleCodec(CloudPortalBlock::new);

    public CloudPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<BrokenPortalBlock> codec() {
        return CODEC;
    }

    /**
     * 允许被流体替换 —— 原版下界门就是这样被水桶"顶掉"的：
     * 水替换掉一格门之后，门洞不再完整，其余门方块会因邻居更新整座消失。
     */
    @Override
    public boolean canBeReplaced(BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return true;
    }

    @Nullable
    @Override
    public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        return level.dimension().equals(BrokenPortalTravel.CLOUD_DIMENSION)
                ? BrokenPortalTravel.backFromCloud(level, entity, pos)
                : BrokenPortalTravel.toCloud(level, entity, pos);
    }
}
