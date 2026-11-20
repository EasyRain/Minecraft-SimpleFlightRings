package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * 云层之上的地形生成器（M2）。
 * <p>
 * 世界分成两层：
 * <ul>
 *   <li><b>大陆</b>：大尺度噪声切出来的宽片区（几百格宽），是云海的"陆地"；</li>
 *   <li><b>岛屿</b>：中尺度噪声切出来的小块（几十格），散布在虚空里，靠飞行往返。</li>
 * </ul>
 * 两者都用 domain warp 把边缘揉出薄片感，越靠边云越薄（1 层云皮），中间最厚（云皮 2 层 + 厚云核 5 层）。
 * 主层高度 {@link #BASE_Y}（192），厚度上限 {@link #MAX_THICKNESS}；虚空在下面，掉下去就是空。
 * <p>
 * 用自己写的 {@link CloudNoise}（不是原版噪声路由）：确定性、无注册开销、跨区块不会接缝。
 * 少数几个可调参数都是常量，等玩法定了再抽成配置。
 */
public class CloudChunkGenerator extends ChunkGenerator {

    public static final MapCodec<CloudChunkGenerator> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
            ).apply(instance, CloudChunkGenerator::new));

    /** 云海主层高度（y）。 */
    public static final int BASE_Y = 192;
    /** 最厚处：2 层云皮 + 5 层厚云核。 */
    public static final int MAX_THICKNESS = 7;
    /** 大陆噪声尺度（越大越碎）。 */
    private static final double CONTINENT_SCALE = 620.0;
    /** 岛屿噪声尺度。 */
    private static final double ISLAND_SCALE = 88.0;
    /** 大陆边缘阈值：噪声高于它就成陆。 */
    private static final double CONTINENT_LO = 0.18;
    private static final double CONTINENT_HI = 0.42;
    /** 岛屿边缘阈值。 */
    private static final double ISLAND_LO = 0.30;
    private static final double ISLAND_HI = 0.52;

    private static final long SEED_CONTINENT = 0x51A7C0DEL;
    private static final long SEED_ISLAND = 0x2B7E1516L;
    private static final long SEED_WARP = 0x6A09E667L;
    private static final long SEED_HEIGHT = 0x3C6EF372L;

    public CloudChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** 某个 (x,z) 的"云密度"：0 = 没有云，1 = 最厚。 */
    private static double density(int x, int z) {
        // domain warp：让边缘不是噪声那副规整的圆角
        double wx = x + CloudNoise.fbm(x, z, 260.0, 2, SEED_WARP) * 48.0;
        double wz = z + CloudNoise.fbm(x + 7919, z - 104729, 260.0, 2, SEED_WARP) * 48.0;

        double continent = CloudNoise.smoothstep(CONTINENT_LO, CONTINENT_HI,
                CloudNoise.fbm(wx, wz, CONTINENT_SCALE, 3, SEED_CONTINENT));
        double island = CloudNoise.smoothstep(ISLAND_LO, ISLAND_HI,
                CloudNoise.fbm(wx, wz, ISLAND_SCALE, 2, SEED_ISLAND));
        // 大陆优先；大陆边缘之外才由岛屿补上
        return Math.max(continent, island * 0.92);
    }

    /** 该列云层的顶面高度。 */
    private static int topY(int x, int z, double density) {
        int wobble = (int) Math.round(CloudNoise.fbm(x, z, 340.0, 2, SEED_HEIGHT) * 7.0);
        return BASE_Y + wobble + (int) Math.round((density - 0.5) * 6.0);
    }

    private BlockState cloud() {
        return ModBlocks.CLOUD_BLOCK.get().defaultBlockState();
    }

    private BlockState thick() {
        return ModBlocks.THICK_CLOUD_BLOCK.get().defaultBlockState();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int minY = this.getMinY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = pos.getMinBlockX() + dx;
                int z = pos.getMinBlockZ() + dz;
                double density = density(x, z);
                if (density <= 0.02) {
                    continue;
                }
                int top = topY(x, z, density);
                int thickness = Math.max(1, (int) Math.round(density * MAX_THICKNESS));
                // 边缘（density 很小）只有 1 层云皮；厚起来以后上面 2 层云皮、下面全是厚云核
                int shell = thickness <= 2 ? 1 : 2;
                for (int i = 0; i < thickness; i++) {
                    int y = top - i;
                    if (y < minY || y >= minY + this.getGenDepth()) {
                        continue;
                    }
                    BlockState state = (i < shell) ? cloud() : thick();
                    cursor.set(x, y, z);
                    chunk.setBlockState(cursor, state, false);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState,
                             ChunkAccess chunk) {
        // 云没有"表面层"：所有方块都在 fillFromNoise 里放好了
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk,
                             net.minecraft.world.level.levelgen.GenerationStep.Carving step) {
        // 云不需要洞穴
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // 云维度暂时不刷怪（M3 再说）
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        double density = density(x, z);
        if (density <= 0.02) {
            return this.getMinY() - 1;
        }
        return topY(x, z, density) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int minY = this.getMinY();
        BlockState[] states = new BlockState[this.getGenDepth()];
        double density = density(x, z);
        int top = density <= 0.02 ? minY - 1 : topY(x, z, density);
        int thickness = Math.max(1, (int) Math.round(density * MAX_THICKNESS));
        int shell = thickness <= 2 ? 1 : 2;
        for (int i = 0; i < states.length; i++) {
            int y = minY + i;
            if (y > top) {
                states[i] = Blocks.AIR.defaultBlockState();
            } else if (top - y < thickness) {
                states[i] = (top - y < shell) ? cloud() : thick();
            } else {
                states[i] = Blocks.AIR.defaultBlockState();
            }
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        double density = density(pos.getX(), pos.getZ());
        lines.add("Cloud density: " + String.format("%.2f", density));
        lines.add("Cloud top: " + (density <= 0.02 ? "-" : String.valueOf(topY(pos.getX(), pos.getZ(), density))));
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState randomState,
                                                    long seed) {
        // 云维度暂时不生成结构，声明"一个都没有"，省掉原版那套结构搜索
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource, java.util.stream.Stream.empty());
    }
}
