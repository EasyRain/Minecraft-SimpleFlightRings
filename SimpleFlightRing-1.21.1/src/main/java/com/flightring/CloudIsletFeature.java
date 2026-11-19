package com.flightring;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 云屿 —— 主世界高空的漂浮小岛，**整座只由云块组成**（用户 2026-11-19 定：不要厚云块），
 * 岛上放一个我们自己的厚云箱子当战利品。
 * <p>
 * 形状是一个压扁的椭球 + 底下几根垂下来的"云根"，全部只往空气里放方块，所以不会切进地形。
 * 箱子放在岛顶中心，面朝随机水平方向，战利品表给 {@code simpleflightring:chests/cloud_islet}
 * （云块 + 厚云砖 + 少量矿物：够玩家凑出第一座云门）。
 */
public class CloudIsletFeature extends Feature<NoneFeatureConfiguration> {

    private static final ResourceKey<LootTable> LOOT_TABLE = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "chests/cloud_islet"));

    public CloudIsletFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockState cloud = ModBlocks.CLOUD_BLOCK.get().defaultBlockState();

        // 只在空中生成：中心点下方不能有地形，免得云屿贴在山顶或树冠上
        if (!level.isEmptyBlock(origin)) {
            return false;
        }

        int radiusXZ = 3 + random.nextInt(3);       // 3~5
        int radiusY = 2 + random.nextInt(2);        // 2~3
        int placed = 0;

        for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
            for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                for (int dy = -radiusY; dy <= radiusY; dy++) {
                    double norm = (double) (dx * dx + dz * dz) / (radiusXZ * radiusXZ)
                            + (double) (dy * dy) / (radiusY * radiusY);
                    if (norm > 1.0D) {
                        continue;
                    }
                    // 边缘随机缺几块，别做成一个规整的球
                    if (norm > 0.72D && random.nextFloat() < 0.25F) {
                        continue;
                    }
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.isEmptyBlock(pos) && level.setBlock(pos, cloud, Block.UPDATE_CLIENTS)) {
                        placed++;
                    }
                }
            }
        }

        if (placed < 24) {
            return false;
        }

        // 底下垂 2~4 根"云根"，更像漂浮岛
        int roots = 2 + random.nextInt(3);
        for (int i = 0; i < roots; i++) {
            int dx = random.nextInt(radiusXZ * 2 + 1) - radiusXZ;
            int dz = random.nextInt(radiusXZ * 2 + 1) - radiusXZ;
            int length = 2 + random.nextInt(3);
            for (int k = 1; k <= length; k++) {
                BlockPos pos = origin.offset(dx, -radiusY - k, dz);
                if (level.isEmptyBlock(pos)) {
                    level.setBlock(pos, cloud, Block.UPDATE_CLIENTS);
                }
            }
        }

        placeChest(level, random, origin.above(radiusY + 1));
        return true;
    }

    /** 岛上唯一的"人造物"：我们的厚云箱子。 */
    private static void placeChest(WorldGenLevel level, RandomSource random, BlockPos pos) {
        BlockState chest = ModBlocks.THICK_CLOUD_CHEST.get().defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random));
        if (!level.isEmptyBlock(pos)) {
            return;
        }
        level.setBlock(pos, chest, Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chestEntity) {
            chestEntity.setLootTable(LOOT_TABLE, random.nextLong());
        }
    }
}
