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
 * 密度模型照《天境》的做法：**三维噪声 × 一条竖直渐变**（band），于是云岛被限制在一条高度带里、
 * 中间厚两端薄、表面由三维噪声揉出凹凸。每片云的带中心高低不同（{@link #ALT_RANGE}）。
 * <p>
 * 方块的"材质"按**暴露面**而不是按密度决定（用户 2026-11-20 的三条意见）：
 * <ul>
 *   <li><b>顶面是厚云块</b>：站得住、能刷怪、能放地物和结构（不再被云皮盖住）；</li>
 *   <li><b>侧面和底面是云块</b>：只有贴着空气的那一圈是软的，做出云岛的蓬松轮廓；</li>
 *   <li><b>内部是厚云块</b>：实心核；</li>
 *   <li><b>洞</b>：三维噪声在内部掏空，破到表面就形成一个开口（开口的内壁自动变成云皮）。</li>
 * </ul>
 * 主层之上另有一层薄云片：厚度由一张平滑的二维噪声连续变化（1~3 格），边缘会顺着噪声收薄，
 * 不再是"每列随机取 1~3 格"那种锯齿。
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
    /** 竖向半高：带中心到上下边缘的距离（最厚约 2×BAND_HALF）。 */
    public static final double BAND_HALF = 24.0;

    private static final double BAND_WEIGHT = 1.25;
    private static final double DETAIL = 0.55;
    private static final double SOLID_THRESHOLD = 0.52;

    /** 洞里再掏空一层：只掏内部，不动顶面两层。 */
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

    /** 邻域边长（16 区块 + 两侧各 2 格），用来判断"侧面是否暴露"——2 格才能算两层厚度。 */
    private static final int NEIGHBOURHOOD = 20;
    /** 侧面/底面的云皮厚度（格）。 */
    private static final int SHELL = 2;

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

    /** 密度值（不含洞穴）；大于 {@link #SOLID_THRESHOLD} 才可能是实心。 */
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

    /**
     * 该体素最终是否是实心（密度 + 洞穴）。
     * <p>
     * {@code top} 传该列的顶面高度：顶部两层不参与洞穴，保证地表完整。
     * 材料（云皮/厚云核）不再靠"猜邻居列的高度范围"，而是**直接问邻居这一格是不是实心** ——
     * 之前用列的 [bottom, top] 近似，洞穴挖空的位置会被误判成"被挡住"，于是厚云块从侧壁戳出来
     * （用户 2026-11-20 截图里那一排排凸起）。
     */
    private static boolean solidAt(int x, int y, int z, double mask, double center, int top, int minY, int maxY) {
        if (y < minY || y >= maxY || mask <= 0.02) {
            return false;
        }
        if (densityAt(x, y, z, mask, center) <= SOLID_THRESHOLD) {
            return false;
        }
        return y >= top - 1 || !isCave(x, y, z);
    }

    /** 内部洞穴：三维噪声高于阈值的格子掏空（顶面两层不掏，保证地表完整）。 */
    private static boolean isCave(int x, int y, int z) {
        return CloudNoise.value3(x, y * 1.33, z, CAVE_SCALE, SEED_CAVE) > CAVE_THRESHOLD;
    }

    /** 该列实心段的最高点（没有就是 Integer.MIN_VALUE）。 */
    private static int columnTop(int x, int z, int minY, int maxY) {
        double mask = horizontalMask(x, z);
        if (mask <= 0.02) {
            return Integer.MIN_VALUE;
        }
        double center = bandCenter(x, z);
        int from = Math.max(minY, (int) Math.floor(center - BAND_HALF));
        int to = Math.min(maxY - 1, (int) Math.ceil(center + BAND_HALF));
        for (int y = to; y >= from; y--) {
            if (densityAt(x, y, z, mask, center) > SOLID_THRESHOLD) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** 该列实心段的最低点（没有就是 Integer.MAX_VALUE）。 */
    private static int columnBottom(int x, int z, int minY, int maxY, int top) {
        if (top == Integer.MIN_VALUE) {
            return Integer.MAX_VALUE;
        }
        double mask = horizontalMask(x, z);
        double center = bandCenter(x, z);
        int from = Math.max(minY, (int) Math.floor(center - BAND_HALF));
        for (int y = from; y <= top; y++) {
            if (densityAt(x, y, z, mask, center) > SOLID_THRESHOLD) {
                return y;
            }
        }
        return top;
    }

    private BlockState cloud() {
        return ModBlocks.CLOUD_BLOCK.get().defaultBlockState();
    }

    private BlockState thick() {
        return ModBlocks.THICK_CLOUD_BLOCK.get().defaultBlockState();
    }

    /** 高空薄云片的高度；厚度由平滑噪声连续变化（1~3 格），边缘顺着噪声收薄。 */
    private static double patchStrength(int x, int z) {
        return CloudNoise.smoothstep(PATCH_LO, PATCH_HI, CloudNoise.fbm(x, z, PATCH_SCALE, 2, SEED_PATCH));
    }

    private static int patchTop(int x, int z) {
        int offset = 55 + (int) Math.round(CloudNoise.fbm(x, z, 160.0, 2, SEED_PATCH_ALT) * 22.0);
        return BASE_Y + offset;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int minY = this.getMinY();
        int maxY = minY + this.getGenDepth();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // 先算好 20×20 邻域每列的 mask / 带中心 / 顶面。
        // 材料判定要"问邻居这一格是不是实心"，所以这些值必须按邻域缓存，避免重复算 fbm。
        int originX = pos.getMinBlockX() - 2;
        int originZ = pos.getMinBlockZ() - 2;
        int size = NEIGHBOURHOOD * NEIGHBOURHOOD;
        double[] masks = new double[size];
        double[] centers = new double[size];
        int[] tops = new int[size];
        for (int ix = 0; ix < NEIGHBOURHOOD; ix++) {
            for (int iz = 0; iz < NEIGHBOURHOOD; iz++) {
                int index = ix + iz * NEIGHBOURHOOD;
                int wx = originX + ix;
                int wz = originZ + iz;
                masks[index] = horizontalMask(wx, wz);
                centers[index] = bandCenter(wx, wz);
                tops[index] = masks[index] <= 0.02 ? Integer.MIN_VALUE : columnTop(wx, wz, minY, maxY);
            }
        }
        BlockState cloudState = cloud();
        BlockState thickState = thick();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = pos.getMinBlockX() + dx;
                int z = pos.getMinBlockZ() + dz;
                int self = (dx + 2) + (dz + 2) * NEIGHBOURHOOD;
                int top = tops[self];
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                double mask = masks[self];
                double center = centers[self];
                int from = Math.max(minY, (int) Math.floor(center - BAND_HALF));
                int bottom = top;
                for (int y = from; y <= top; y++) {
                    if (solidAt(x, y, z, mask, center, top, minY, maxY)) {
                        bottom = y;
                        break;
                    }
                }

                int nUp = self - NEIGHBOURHOOD;
                int nDown = self + NEIGHBOURHOOD;
                int nLeft = self - 1;
                int nRight = self + 1;

                for (int y = bottom; y <= top; y++) {
                    if (!solidAt(x, y, z, mask, center, top, minY, maxY)) {
                        continue;
                    }
                    boolean surface = y >= top - 1;
                    boolean shell;
                    if (surface) {
                        shell = false;   // 顶面 = 厚云块：站得住、能刷怪、能放地物/结构
                    } else {
                        // 只要六个方向（含上下）里有一格是空气，这一格就是"壳"，用云皮
                        boolean airNeighbour =
                                !solidAt(x, y - 1, z, mask, center, top, minY, maxY)
                                || !solidAt(x, y + 1, z, mask, center, top, minY, maxY)
                                || !solidAt(x - 1, y, z, masks[nLeft], centers[nLeft], tops[nLeft], minY, maxY)
                                || !solidAt(x + 1, y, z, masks[nRight], centers[nRight], tops[nRight], minY, maxY)
                                || !solidAt(x, y, z - 1, masks[nUp], centers[nUp], tops[nUp], minY, maxY)
                                || !solidAt(x, y, z + 1, masks[nDown], centers[nDown], tops[nDown], minY, maxY);
                        // 再看距离 2 的邻居，让云皮有两层厚
                        boolean airNeighbour2 =
                                !solidAt(x - 2, y, z, masks[nLeft - 1], centers[nLeft - 1], tops[nLeft - 1], minY, maxY)
                                || !solidAt(x + 2, y, z, masks[nRight + 1], centers[nRight + 1], tops[nRight + 1], minY, maxY)
                                || !solidAt(x, y, z - 2, masks[nUp - NEIGHBOURHOOD], centers[nUp - NEIGHBOURHOOD], tops[nUp - NEIGHBOURHOOD], minY, maxY)
                                || !solidAt(x, y, z + 2, masks[nDown + NEIGHBOURHOOD], centers[nDown + NEIGHBOURHOOD], tops[nDown + NEIGHBOURHOOD], minY, maxY)
                                || !solidAt(x, y - 2, z, mask, center, top, minY, maxY);
                        shell = airNeighbour || airNeighbour2;
                    }
                    cursor.set(x, y, z);
                    chunk.setBlockState(cursor, shell ? cloudState : thickState, 0);
                }

                // 高空薄云片
                double strength = patchStrength(x, z);
                if (strength > 0.12) {
                    int patchTopY = patchTop(x, z);
                    if (Math.abs(patchTopY - top) >= 8) {
                        int thickness = Math.max(1, (int) Math.round(strength * 3.0));
                        for (int i = 0; i < thickness; i++) {
                            int y = patchTopY - i;
                            if (y < minY || y >= maxY) {
                                continue;
                            }
                            cursor.set(x, y, z);
                            chunk.setBlockState(cursor, cloudState, 0);
                        }
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
        int top = columnTop(x, z, minY, maxY);
        double strength = patchStrength(x, z);
        if (strength > 0.12) {
            int top2 = patchTop(x, z);
            if (top == Integer.MIN_VALUE || Math.abs(top2 - top) >= 8) {
                top = Math.max(top, top2);
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

        int top = columnTop(x, z, minY, maxY);
        if (top != Integer.MIN_VALUE) {
            double mask = horizontalMask(x, z);
            double center = bandCenter(x, z);
            for (int y = minY; y <= top; y++) {
                if (!solidAt(x, y, z, mask, center, top, minY, maxY)) {
                    continue;
                }
                states[y - minY] = thick();   // 高度图与结构只关心是否实心，统一给厚云块
            }
        }
        double strength = patchStrength(x, z);
        if (strength > 0.12) {
            int top2 = patchTop(x, z);
            if (top == Integer.MIN_VALUE || Math.abs(top2 - top) >= 8) {
                int thickness = Math.max(1, (int) Math.round(strength * 3.0));
                for (int i = 0; i < thickness; i++) {
                    int y = top2 - i;
                    if (y >= minY && y < maxY) {
                        states[y - minY] = cloud();
                    }
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
        lines.add("Patch strength: " + String.format("%.2f", patchStrength(pos.getX(), pos.getZ())));
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState randomState,
                                                    long seed) {
        // 云维度暂时不生成结构
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource,
                java.util.stream.Stream.empty());
    }
}
