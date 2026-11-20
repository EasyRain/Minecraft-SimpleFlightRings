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

    private static double solidity(int x, int y, int z, double mask, double center) {
        double t = (y - center) / BAND_HALF;
        if (t <= -1.0 || t >= 1.0) {
            return Double.NEGATIVE_INFINITY;
        }
        double band = 1.0 - t * t;
        double detail = CloudNoise.value3(x, y * 1.6, z, DETAIL_SCALE, SEED_DETAIL);
        // 三维噪声只在岛屿内部起作用：边缘（mask 小）把它的权重压到 0，
        // 否则"带 × 遮罩"本来就贴着阈值，噪声一冒头就单独成一根竖条 —— 也就是岛边那一溜溜"鳍"。
        double detailWeight = DETAIL * CloudNoise.smoothstep(0.02, 0.32, mask);
        return mask * band * BAND_WEIGHT + detail * detailWeight;
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
            if (solidity(x, y, z, mask, center) > SOLID_THRESHOLD) {
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
            if (solidity(x, y, z, mask, center) > SOLID_THRESHOLD) {
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

        // 先算好 20×20 邻域每列的实心范围，用来判断"这一格离侧面有几格"
        int originX = pos.getMinBlockX() - 2;
        int originZ = pos.getMinBlockZ() - 2;
        int[] tops = new int[NEIGHBOURHOOD * NEIGHBOURHOOD];
        int[] bottoms = new int[NEIGHBOURHOOD * NEIGHBOURHOOD];
        for (int ix = 0; ix < NEIGHBOURHOOD; ix++) {
            for (int iz = 0; iz < NEIGHBOURHOOD; iz++) {
                int index = ix + iz * NEIGHBOURHOOD;
                tops[index] = columnTop(originX + ix, originZ + iz, minY, maxY);
                bottoms[index] = columnBottom(originX + ix, originZ + iz, minY, maxY, tops[index]);
            }
        }

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = pos.getMinBlockX() + dx;
                int z = pos.getMinBlockZ() + dz;
                int top = tops[(dx + 1) + (dz + 1) * NEIGHBOURHOOD];
                if (top != Integer.MIN_VALUE) {
                    double mask = horizontalMask(x, z);
                    double center = bandCenter(x, z);
                    int from = Math.max(minY, (int) Math.floor(center - BAND_HALF));
                    // ★ 兜底必须是 from，不能是 minY：万一这一列找不到实心点，
                    // 用 minY 会从世界底部一路填到顶，生成一根"云柱"（用户 2026-11-20 截图里的竖条）。
                    int bottom = from;
                    boolean found = false;
                    for (int y = from; y <= top; y++) {
                        if (solidity(x, y, z, mask, center) > SOLID_THRESHOLD) {
                            bottom = y;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        bottom = top;
                    }

                    // dx/dz 是区块内坐标，邻域原点比区块左/后各偏 2 格
                    // base = (dx+2) + (dz+2)*N
                    int base = (dx + 2) + (dz + 2) * NEIGHBOURHOOD;
                    int up1 = tops[base - NEIGHBOURHOOD];
                    int down1 = tops[base + NEIGHBOURHOOD];
                    int left1 = tops[base - 1];
                    int right1 = tops[base + 1];
                    int up2 = tops[base - 2 * NEIGHBOURHOOD];
                    int down2 = tops[base + 2 * NEIGHBOURHOOD];
                    int left2 = tops[base - 2];
                    int right2 = tops[base + 2];
                    int bUp1 = bottoms[base - NEIGHBOURHOOD];
                    int bDown1 = bottoms[base + NEIGHBOURHOOD];
                    int bLeft1 = bottoms[base - 1];
                    int bRight1 = bottoms[base + 1];

                    for (int y = bottom; y <= top; y++) {
                        if (y < top - 1 && isCave(x, y, z)) {
                            continue;   // 内部洞穴
                        }
                        // 侧面：这一格在某个邻居列里是"空的"（高于它的顶 或 低于它的底）
                        boolean side1 = y > up1 || y > down1 || y > left1 || y > right1
                                || y < bUp1 || y < bDown1 || y < bLeft1 || y < bRight1;
                        boolean side2 = y > up2 || y > down2 || y > left2 || y > right2;
                        boolean exposedSide = side1 || side2;
                        boolean exposedBottom = y < bottom + SHELL;
                        boolean surface = y >= top - 1;
                        // 顶面（含贴顶一层）= 厚云块：站得住、能刷怪、能放地物/结构
                        // 侧面/底面 SHELL 层 = 云块：蓬松轮廓
                        // 其余内部 = 厚云块：实心核
                        BlockState state = (surface || (!exposedSide && !exposedBottom)) ? thick() : cloud();
                        cursor.set(x, y, z);
                        chunk.setBlockState(cursor, state, false);
                    }
                }

                // 高空薄云片：强度连续变化，厚度 = round(strength*3)，边缘自然收薄
                double strength = patchStrength(x, z);
                if (strength > 0.12) {
                    int top2 = patchTop(x, z);
                    if (top != Integer.MIN_VALUE && Math.abs(top2 - top) < 8) {
                        continue;
                    }
                    int thickness = Math.max(1, (int) Math.round(strength * 3.0));
                    for (int i = 0; i < thickness; i++) {
                        int y = top2 - i;
                        if (y < minY || y >= maxY) {
                            continue;
                        }
                        cursor.set(x, y, z);
                        chunk.setBlockState(cursor, cloud(), false);
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
                if (solidity(x, y, z, mask, center) <= SOLID_THRESHOLD) {
                    continue;
                }
                if (y < top - 1 && isCave(x, y, z)) {
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
