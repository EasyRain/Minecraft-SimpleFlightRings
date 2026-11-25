package com.flightring;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * 云树苗。
 * <p>
 * 和其它树苗的唯一区别：**既能种在土上，也能直接种在厚云块上**（用户 2026-11-21）。
 * 长出来的就是 {@code simpleflightring:cloud_tree}。
 */
public class CloudSaplingBlock extends SaplingBlock {

    public static final ResourceKey<ConfiguredFeature<?, ?>> CLOUD_TREE = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "cloud_tree"));

    /** 用云树的 configured feature 作为生长目标；不设置副树与花树。 */
    public static final TreeGrower GROWER = new TreeGrower("cloud",
            Optional.empty(), Optional.of(CLOUD_TREE), Optional.empty());

    public CloudSaplingBlock(Properties properties) {
        super(GROWER, properties);
    }

    /**
     * 1.21.1 的 {@code #minecraft:dirt} 已经包含全部"泥土家族"（草方块/灰化土/砂土/菌丝/缠根泥土/苔藓/泥），
     * 这里再加耕地（原版树苗也认）和厚云块（用户 2026-11-25）。
     */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND) || state.is(ModBlocks.THICK_CLOUD_BLOCK.get());
    }
}
