package com.flightring;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
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

    /**
     * 26.x 把 {@code #minecraft:dirt} 缩成了「泥土/砂土/缠根泥土」三种，灰化土、草方块、菌丝、苔藓、泥
     * 全被移走；原版树苗跟着改用 {@code #minecraft:supports_vegetation}（= 上述泥土全家 + 耕地）。
     * 我们照原版的走法，再加上厚云块（用户 2026-11-25）。
     */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.SUPPORTS_VEGETATION) || state.is(ModBlocks.THICK_CLOUD_BLOCK.get());
    }
}
