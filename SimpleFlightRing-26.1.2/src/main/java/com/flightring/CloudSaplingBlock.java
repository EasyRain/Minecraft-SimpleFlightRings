package com.flightring;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * 云树苗：既能种在土上，也能直接种在厚云块上（用户 2026-11-21）。
 * 长出来的就是 {@code simpleflightring:cloud_tree}。
 */
public class CloudSaplingBlock extends SaplingBlock {

    public static final ResourceKey<ConfiguredFeature<?, ?>> CLOUD_TREE = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "cloud_tree"));

    public static final TreeGrower GROWER = new TreeGrower("cloud",
            Optional.empty(), Optional.of(CLOUD_TREE), Optional.empty());

    public CloudSaplingBlock(Properties properties) {
        super(GROWER, properties);
    }

    /** 能种的地表统一走 {@link ModTags#CLOUD_SOIL}（云块/厚云块 + 各版泥土，差异在那个标签的 JSON 里）。 */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModTags.CLOUD_SOIL);
    }
}
