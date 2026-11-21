package com.flightring;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
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

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(ModBlocks.THICK_CLOUD_BLOCK.get());
    }
}
