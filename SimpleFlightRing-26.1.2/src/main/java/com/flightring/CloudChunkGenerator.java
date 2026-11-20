package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
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
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * 云层之上的地形生成器（M2）。
 * <p>
 * 构图参考《天境》的**做法**（不是复刻）：它的 skylands 维度用原版噪声设置，把
 * "三维基础噪声 × 一条竖直渐变"当密度 —— 于是浮岛天然被限制在一条高度带里，
 * 带中间最厚、上下两端收成薄片，表面又由三维噪声揉出凹凸、悬垂和洞。
 * 我们这里是自己写的噪声（{@link CloudNoise}），但用的是同一个思路：
 *
 * <pre>
 *   band(y)  = 1 - ((y - 该片云的带中心) / BAND_HALF)²      // 中间 1、两端 0
 *   solidity = 水平遮罩 × band × BAND_WEIGHT + 三维噪声 × DETAIL
 *   实心 ⟺ solidity > SOLID_THRESHOLD；solidity > CORE_THRESHOLD 的地方是厚云核
 * </pre>
 *
 * 这样做的效果：云岛是**透镜形**（中间厚、边缘薄）、表面不规则（有悬垂和洞）、
 * 每片云的**带中心高低不同**（{@link #ALT_RANGE}），不会再是一整块平板。
 * 主层之上另有一层 1~3 格厚的高空薄云片（天境"境云"那种）。
 */
public class CloudChunkGenerator extends ChunkGenerator {

    public static final MapCodec<CloudChunkGenerator> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
            ).apply(instance, CloudChunkGenerator::new));

    /** 云海主层基准高度（y）：原版云在 192，我们整体抬到它之上。 */
    public static final int BASE_Y = 256;
    /** 每片云的高度带中心在这个范围内起伏（±）。 */
    public static final int ALT_RANGE = 52;
    /** 竖向半高：带中心到上下边缘的距离（决定最厚能到 ~2×BAND_HALF）。 */
    public static final double BAND_HALF = 24.0;
    /** 水平遮罩对实心度的贡献权重。 */
    private static final double BAND_WEIGHT = 1.25;
    /** 三维噪声对实心度的贡献（越大越碎、洞越多）。 */
    private static final double DETAIL = 0.55;
    /** 实心阈值。 */
    private static final double SOLID_THRESHOLD = 0.52;
    /** 超过它就是厚云核，否则只是云皮。 */
    private static final double CORE_THRESHOLD = 0.78;

    private static final double CONTINENT_SCALE = 620.0;
    private static final double ISLAND_SCALE = 88.0;
    private static final double ALT_SCALE = 700.0;
    private static final double DETAIL_SCALE = 30.0;
    private static final double PATCH_SCALE = 44.0;

    private static final double CONTINENT_LO = 0.22;
    private static final double CONTINENT_HI = 0.46;
    private static final double ISLAND_LO = 0.34;
    private static final double ISLAND_HI = 0.56;
    private static final double PATCH_LO = 0.52;
    private static final double PATCH_HI = 0.74;

    private static final long SEED_CONTINENT = 0x51A7C0DEL;
    private static final long SEED_ISLAND = 0x2B7E1516L;
    private static final long SEED_WARP = 0x6A09E667L;
    private static final long SEED_ALT = 0x1F83D9ABL;
    private static final long SEED_DETAIL = 0x71374491L;
    private static final long SEED_PATCH = 0xB5C0FBCFL;
    private static final long SEED_PATCH_ALT = 0xE9B5DBA5L;

    public CloudChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** 水平遮罩：0 = 虚空，1 = 大陆内部；岛屿只长在大陆之外的虚空里。 */
    private static double horizontalMask(int x, int z) {
        double wx = x + CloudNoise.fbm(x, z, 260.0, 2, SEED_WARP) * 56.0;
        double wz = z + CloudNoise.fbm(x + 7919, z - 104729, 260.0, 2, SEED_WARP) * 56.0;

        double continent = CloudNoise.smoothstep(CONTINENT_LO, CONTINENT_HI,
                CloudNoise.fbm(wx, wz, CONTINENT_SCALE, 3, SEED_CONTINENT));
        double island = CloudNoise.smoothstep(ISLAND_LO, ISLAND_HI,
                CloudNoise.fbm(wx, wz, ISLAND_SCALE, 2, SEED_ISLAND));
        double islandOnly = island * (1.0 - CloudNoise.smoothstep(0.0, 0.25, continent));
        return Math.max(continent, islandOnly * 0.92);
    }

    /** 该列的云带中心高度。 */
    private static double bandCenter(int x, int z) {
        return BASE_Y + CloudNoise.fbm(x, z, ALT_SCALE, 3, SEED_ALT) * ALT_RANGE;
    }

    /**
     * 该体素的"实心度"。大于 {@link #SOLID_THRESHOLD} 就是云，越大越靠近核。
     * 返回 {@link Double#NEGATIVE_INFINITY} 表示这一列根本没有云（省掉竖直循环）。
     */
    private static double solidity(int x, int y, int z, double mask, double center) {
        double t = (y - center) / BAND_HALF;
        if (t <= -1.0 || t >= 1.0) {
            return Double.NEGATIVE_INFINITY;
        }
        double band = 1.0 - t * t;
        double detail = CloudNoise.value3(x, y * 1.6, z, DETAIL_SCALE, SEED_DETAIL);
        return mask * band * BAND_WEIGHT + detail * DETAIL;
    }

    private BlockState cloud() {
        return ModBlocks.CLOUD_BLOCK.get().defaultBlockState();
    }

    private BlockState thick() {
        return ModBlocks.THICK_CLOUD_BLOCK.get().defaultBlockState();
    }

    /** 高空薄云片：主层之上 55~77 格，1~3 格厚，只由云皮组成。 */
    private static int patchHeight(int x, int z, int mainTop) {
        double patch = CloudNoise.smoothstep(PATCH_LO, PATCH_HI,
                CloudNoise.fbm(x, z, PATCH_SCALE, 2, SEED_PATCH));
        if (patch <= 0.05) {
            return Integer.MIN_VALUE;
        }
        int offset = 55 + (int) Math.round(CloudNoise.fbm(x, z, 160.0, 2, SEED_PATCH_ALT) * 22.0);
        int top = BASE_Y + offset;
        if (mainTop != Integer.MIN_VALUE && Math.abs(top - mainTop) < 8) {
            return Integer.MIN_VALUE;
        }
        return top;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int minY = this.getMinY();
        int maxY = minY + this.getGenDepth();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = pos.getMinBlockX() + dx;
                int z = pos.getMinBlockZ() + dz;
                double mask = horizontalMask(x, z);

                int top = Integer.MIN_VALUE;
                if (mask > 0.02) {
                    double center = bandCenter(x, z);
                    int from = Math.max(minY, (int) Math.floor(center - BAND_HALF));
                    int to = Math.min(maxY - 1, (int) Math.ceil(center + BAND_HALF));
                    for (int y = to; y >= from; y--) {
                        double solidity = solidity(x, y, z, mask, center);
                        if (solidity <= SOLID_THRESHOLD) {
                            continue;
                        }
                        if (top == Integer.MIN_VALUE) {
                            top = y;
                        }
                        cursor.set(x, y, z);
                        chunk.setBlockState(cursor, solidity > CORE_THRESHOLD ? thick() : cloud(), 0);
                    }
                }

                int patchTop = patchHeight(x, z, top);
                if (patchTop != Integer.MIN_VALUE) {
                    int thickness = 1 + Math.abs((x * 31 + z * 17) % 3);   // 1~3 层
                    for (int i = 0; i < thickness; i++) {
                        int y = patchTop - i;
                        if (y < minY || y >= maxY) {
                            continue;
                        }
                        cursor.set(x, y, z);
                        chunk.setBlockState(cursor, cloud(), 0);
                    }
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
                             StructureManager structureManager, ChunkAccess chunk) {
        // 云不需要洞穴（洞由三维噪声直接掏出来）
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
        int minY = this.getMinY();
        int maxY = minY + this.getGenDepth();
        int top = Integer.MIN_VALUE;
        double mask = horizontalMask(x, z);
        if (mask > 0.02) {
            double center = bandCenter(x, z);
            for (int y = Math.min(maxY - 1, (int) Math.ceil(center + BAND_HALF)); y >= minY; y--) {
                if (solidity(x, y, z, mask, center) > SOLID_THRESHOLD) {
                    top = y;
                    break;
                }
            }
        }
        int patchTop = patchHeight(x, z, top);
        if (patchTop != Integer.MIN_VALUE) {
            top = Math.max(top, patchTop);
        }
        return top == Integer.MIN_VALUE ? minY - 1 : top + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int minY = this.getMinY();
        int maxY = minY + this.getGenDepth();
        BlockState[] states = new BlockState[this.getGenDepth()];
        java.util.Arrays.fill(states, Blocks.AIR.defaultBlockState());

        double mask = horizontalMask(x, z);
        if (mask > 0.02) {
            double center = bandCenter(x, z);
            for (int y = minY; y < maxY; y++) {
                double solidity = solidity(x, y, z, mask, center);
                if (solidity > SOLID_THRESHOLD) {
                    states[y - minY] = solidity > CORE_THRESHOLD ? thick() : cloud();
                }
            }
        }
        int patchTop = patchHeight(x, z, getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE, level, randomState) - 1);
        if (patchTop != Integer.MIN_VALUE) {
            int thickness = 1 + Math.abs((x * 31 + z * 17) % 3);
            for (int i = 0; i < thickness; i++) {
                int y = patchTop - i;
                if (y >= minY && y < maxY) {
                    states[y - minY] = cloud();
                }
            }
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        double mask = horizontalMask(pos.getX(), pos.getZ());
        lines.add("Cloud mask: " + String.format("%.2f", mask));
        lines.add("Cloud band center: " + String.format("%.0f", bandCenter(pos.getX(), pos.getZ())));
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState randomState,
                                                    long seed) {
        // 云维度暂时不生成结构
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource,
                java.util.stream.Stream.empty());
    }
}
