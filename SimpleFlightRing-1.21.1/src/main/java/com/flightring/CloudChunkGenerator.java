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
 * 密度模型照《天境》：**三维噪声 × 一条竖直渐变**（band）—— 云岛被限制在一条高度带里、
 * 中间厚两端薄，每片云的带中心高低不同（{@link #ALT_RANGE}）。
 * <p>
 * 生成分成明确的三步（用户 2026-11-20 提出的做法，比"逐格判断材质"可靠得多）：
 * <ol>
 *   <li><b>先建实心体</b>：整片云岛先用 <b>厚云块</b> 填满（密度 &gt; 阈值，且不在洞穴里）；</li>
 *   <li><b>再铺云皮</b>：只在**侧面和底面**暴露的一圈（外两格）换成云块，顶面保持厚云块；</li>
 *   <li><b>最后挖洞</b>：洞穴在第一步就已经排除，所以洞是真的空腔，洞壁自然落在"暴露的一圈"里，
 *       自动变成云皮。</li>
 * </ol>
 * 这样"云皮"永远是贴着空气的那层，不会出现厚云块从侧壁戳出来的情况 ——
 * 之前用"邻居列高度区间"近似判断暴露面，洞穴一挖空就判错。
 */
public class CloudChunkGenerator extends ChunkGenerator {

    public static final MapCodec<CloudChunkGenerator> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
            ).apply(instance, CloudChunkGenerator::new));

    /** 云海主层基准高度（y）。 */
    public static final int BASE_Y = 256;
    /** 每片云的高度带中心在这个范围内起伏（±）。 */
    public static final int ALT_RANGE = 52;
    /** 竖向半高。 */
    public static final double BAND_HALF = 24.0;
    /** 云皮厚度（格）：距离空气这么多格以内都算"壳"。 */
    public static final int SHELL = 2;

    private static final double BAND_WEIGHT = 1.25;
    private static final double DETAIL = 0.55;
    private static final double SOLID_THRESHOLD = 0.52;
    private static final double CAVE_THRESHOLD = 0.55;

    private static final double CONTINENT_SCALE = 620.0;
    private static final double ISLAND_SCALE = 88.0;
    private static final double ALT_SCALE = 700.0;
    private static final double DETAIL_SCALE = 30.0;
    private static final double CAVE_SCALE = 32.0;
    private static final double PATCH_SCALE = 44.0;

    private static final double CONTINENT_LO = 0.22;
    private static final double CONTINENT_HI = 0.46;
    private static final double ISLAND_LO = 0.34;
    private static final double ISLAND_HI = 0.56;
    private static final double PATCH_LO = 0.50;
    private static final double PATCH_HI = 0.70;

    private static final long SEED_CONTINENT = 0x51A7C0DEL;
    private static final long SEED_ISLAND = 0x2B7E1516L;
    private static final long SEED_WARP = 0x6A09E667L;
    private static final long SEED_ALT = 0x1F83D9ABL;
    private static final long SEED_DETAIL = 0x71374491L;
    private static final long SEED_CAVE = 0x428A2F98L;
    private static final long SEED_PATCH = 0xB5C0FBCFL;
    private static final long SEED_PATCH_ALT = 0xE9B5DBA5L;

    /**
     * 调试开关：只生成厚云块（不铺云皮、不挖洞、不放云片）。
     * 用来确认"侧壁戳出来的凸起"到底是密度场本身的形状，还是材质分配的问题。
     */
    public static final boolean DEBUG_THICK_ONLY = true;

    /** 邻域边长：16 区块 + 两侧各 2 格（云皮要往外看两格）。 */
    private static final int NEIGHBOURHOOD = 20;

    public CloudChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

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

    private static double bandCenter(int x, int z) {
        return BASE_Y + CloudNoise.fbm(x, z, ALT_SCALE, 3, SEED_ALT) * ALT_RANGE;
    }

    /** 纯密度（不含洞穴）。 */
    private static double densityAt(int x, int y, int z, double mask, double center) {
        double t = (y - center) / BAND_HALF;
        if (t <= -1.0 || t >= 1.0) {
            return Double.NEGATIVE_INFINITY;
        }
        double band = 1.0 - t * t;
        double detail = CloudNoise.value3(x, y * 1.6, z, DETAIL_SCALE, SEED_DETAIL);
        double detailWeight = DETAIL * CloudNoise.smoothstep(0.02, 0.32, mask);
        return mask * band * BAND_WEIGHT + detail * detailWeight;
    }

    private static boolean isCave(int x, int y, int z) {
        return CloudNoise.value3(x, y * 1.33, z, CAVE_SCALE, SEED_CAVE) > CAVE_THRESHOLD;
    }

    private static double patchStrength(int x, int z) {
        return CloudNoise.smoothstep(PATCH_LO, PATCH_HI, CloudNoise.fbm(x, z, PATCH_SCALE, 2, SEED_PATCH));
    }

    private static int patchTop(int x, int z) {
        return BASE_Y + 55 + (int) Math.round(CloudNoise.fbm(x, z, 160.0, 2, SEED_PATCH_ALT) * 22.0);
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
        int maxY = minY + this.getGenDepth();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int originX = pos.getMinBlockX() - SHELL;
        int originZ = pos.getMinBlockZ() - SHELL;

        // 这次生成的高度窗口：覆盖邻域里所有列的云带（上下各留 2 格给云皮判定）
        double minCenter = Double.MAX_VALUE;
        double maxCenter = -Double.MAX_VALUE;
        for (int ix = 0; ix < NEIGHBOURHOOD; ix++) {
            for (int iz = 0; iz < NEIGHBOURHOOD; iz++) {
                double c = bandCenter(originX + ix, originZ + iz);
                minCenter = Math.min(minCenter, c);
                maxCenter = Math.max(maxCenter, c);
            }
        }
        int wy0 = Math.max(minY, (int) Math.floor(minCenter - BAND_HALF) - SHELL);
        int wy1 = Math.min(maxY - 1, (int) Math.ceil(maxCenter + BAND_HALF) + SHELL);
        int height = wy1 - wy0 + 1;

        // ---- 第一步：实心体（厚云块），洞穴直接排除 ----
        boolean[][][] solid = new boolean[NEIGHBOURHOOD][height][NEIGHBOURHOOD];
        int[][] columnTop = new int[NEIGHBOURHOOD][NEIGHBOURHOOD];
        for (int ix = 0; ix < NEIGHBOURHOOD; ix++) {
            for (int iz = 0; iz < NEIGHBOURHOOD; iz++) {
                columnTop[ix][iz] = Integer.MIN_VALUE;
            }
        }
        for (int ix = 0; ix < NEIGHBOURHOOD; ix++) {
            for (int iz = 0; iz < NEIGHBOURHOOD; iz++) {
                int x = originX + ix;
                int z = originZ + iz;
                double mask = horizontalMask(x, z);
                if (mask <= 0.02) {
                    continue;
                }
                double center = bandCenter(x, z);
                for (int iy = 0; iy < height; iy++) {
                    int y = wy0 + iy;
                    if (densityAt(x, y, z, mask, center) <= SOLID_THRESHOLD) {
                        continue;
                    }
                    solid[ix][iy][iz] = true;
                    columnTop[ix][iz] = Math.max(columnTop[ix][iz], y);
                }
            }
        }
        // 挖洞：顶部两层不掏，保证地表完整（调试模式下整段跳过）
        for (int ix = 0; !DEBUG_THICK_ONLY && ix < NEIGHBOURHOOD; ix++) {
            for (int iz = 0; iz < NEIGHBOURHOOD; iz++) {
                int top = columnTop[ix][iz];
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                int x = originX + ix;
                int z = originZ + iz;
                for (int iy = 0; iy < height; iy++) {
                    int y = wy0 + iy;
                    if (solid[ix][iy][iz] && y < top - 1 && isCave(x, y, z)) {
                        solid[ix][iy][iz] = false;
                    }
                }
            }
        }

        // ---- 第二步：铺云皮（侧面、底面外 SHELL 格），第三步顺手把方块写进区块 ----
        BlockState cloudState = cloud();
        BlockState thickState = thick();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int ix = dx + SHELL;
                int iz = dz + SHELL;
                int x = pos.getMinBlockX() + dx;
                int z = pos.getMinBlockZ() + dz;

                for (int iy = 0; iy < height; iy++) {
                    if (!solid[ix][iy][iz]) {
                        continue;
                    }
                    int y = wy0 + iy;
                    boolean surface = y >= columnTop[ix][iz];
                    boolean shell = !DEBUG_THICK_ONLY && !surface && exposed(solid, ix, iy, iz, height);
                    cursor.set(x, y, z);
                    chunk.setBlockState(cursor, shell ? cloudState : thickState, false);
                }

                // 高空薄云片
                double strength = DEBUG_THICK_ONLY ? 0.0 : patchStrength(x, z);
                if (strength > 0.12) {
                    int patchTopY = patchTop(x, z);
                    int top = columnTop[ix][iz];
                    if (top == Integer.MIN_VALUE || Math.abs(patchTopY - top) >= 8) {
                        int thickness = Math.max(1, (int) Math.round(strength * 3.0));
                        for (int i = 0; i < thickness; i++) {
                            int y = patchTopY - i;
                            if (y < minY || y >= maxY) {
                                continue;
                            }
                            cursor.set(x, y, z);
                            chunk.setBlockState(cursor, cloudState, false);
                        }
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /** 这一格在 SHELL 格以内是否贴着空气（只看侧面和底面，顶面另外处理）。 */
    private static boolean exposed(boolean[][][] solid, int ix, int iy, int iz, int height) {
        for (int d = 1; d <= SHELL; d++) {
            if (isAir(solid, ix - d, iy, iz, height) || isAir(solid, ix + d, iy, iz, height)
                    || isAir(solid, ix, iy, iz - d, height) || isAir(solid, ix, iy, iz + d, height)) {
                return true;
            }
        }
        // 底面
        return isAir(solid, ix, iy - 1, iz, height) || isAir(solid, ix, iy - 2, iz, height);
    }

    private static boolean isAir(boolean[][][] solid, int ix, int iy, int iz, int height) {
        if (iy < 0 || iy >= height) {
            return true;
        }
        if (ix < 0 || ix >= NEIGHBOURHOOD || iz < 0 || iz >= NEIGHBOURHOOD) {
            return true;   // 邻域之外当作空气（保守：宁可多做云皮）
        }
        return !solid[ix][iy][iz];
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
        double mask = horizontalMask(x, z);
        int top = Integer.MIN_VALUE;
        if (mask > 0.02) {
            double center = bandCenter(x, z);
            for (int y = Math.min(maxY - 1, (int) Math.ceil(center + BAND_HALF)); y >= minY; y--) {
                if (densityAt(x, y, z, mask, center) > SOLID_THRESHOLD) {
                    top = y;
                    break;
                }
            }
        }
        double strength = patchStrength(x, z);
        if (strength > 0.12) {
            int patchTopY = patchTop(x, z);
            if (top == Integer.MIN_VALUE || Math.abs(patchTopY - top) >= 8) {
                top = Math.max(top, patchTopY);
            }
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
            int top = Integer.MIN_VALUE;
            for (int y = minY; y < maxY; y++) {
                if (densityAt(x, y, z, mask, center) > SOLID_THRESHOLD) {
                    top = y;
                }
            }
            for (int y = minY; y < maxY; y++) {
                if (densityAt(x, y, z, mask, center) <= SOLID_THRESHOLD) {
                    continue;
                }
                if (y < top - 1 && isCave(x, y, z)) {
                    continue;
                }
                states[y - minY] = thick();
            }
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        lines.add("Cloud mask: " + String.format("%.2f", horizontalMask(pos.getX(), pos.getZ())));
        lines.add("Cloud band center: " + String.format("%.0f", bandCenter(pos.getX(), pos.getZ())));
        lines.add("Patch strength: " + String.format("%.2f", patchStrength(pos.getX(), pos.getZ())));
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState randomState,
                                                    long seed) {
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource,
                java.util.stream.Stream.empty());
    }
}
