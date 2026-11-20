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
 * 参考《天境》那种"浮空群岛"的构图（只借结构思路，不复刻）：
 * <ul>
 *   <li><b>高度带</b>：每片云的整体高度由一张大尺度噪声决定（{@link #ALT_RANGE} 内起伏），
 *       所以有的云海在 220 附近、有的在 300 附近，不再是一整块平板；</li>
 *   <li><b>大陆</b>（几百格）+ <b>岛屿</b>（几十格）两层，岛屿只长在大陆之外的虚空里；</li>
 *   <li><b>悬垂</b>：厚的地方往下多长几格，做出"云团底部"的垂坠感；</li>
 *   <li><b>透光洞</b>：厚云核里用三维噪声掏掉一部分方块，抬头/低头能看到光穿过去；</li>
 *   <li><b>高空薄云片</b>：主层之上 50~100 格另有一层零星 1~3 格厚的云片（天境的"境云"那种）。</li>
 * </ul>
 * 结构上仍是"列式"生成（同一 (x,z) 只有一段云），所以 {@link #getBaseHeight} 与
 * {@link #getBaseColumn} 能和实际地形保持一致。
 */
public class CloudChunkGenerator extends ChunkGenerator {

    public static final MapCodec<CloudChunkGenerator> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
            ).apply(instance, CloudChunkGenerator::new));

    /** 云海主层基准高度（y）：原版云在 192，我们整体抬到它之上。 */
    public static final int BASE_Y = 256;
    /** 高度带：主层上下各起伏这么多格。 */
    public static final int ALT_RANGE = 48;
    /** 最厚处（再叠加 {@link #THICK_VARIATION} 的 0.6~1.0 系数和悬垂）。 */
    public static final int MAX_THICKNESS = 36;

    private static final double CONTINENT_SCALE = 620.0;
    private static final double ISLAND_SCALE = 88.0;
    private static final double ALT_SCALE = 700.0;
    private static final double THICK_SCALE = 210.0;
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
    private static final long SEED_HEIGHT = 0x3C6EF372L;
    private static final long SEED_ALT = 0x1F83D9ABL;
    private static final long SEED_THICK = 0x5BE0CD19L;
    private static final long SEED_HANG = 0x428A2F98L;
    private static final long SEED_HOLE = 0x71374491L;
    private static final long SEED_PATCH = 0xB5C0FBCFL;
    private static final long SEED_PATCH_ALT = 0xE9B5DBA5L;

    public CloudChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** 一段云的竖直范围；没有云时返回 null。 */
    private record CloudColumn(int top, int bottom) {
    }

    /** 该列的主云（大陆/岛屿）。 */
    private static CloudColumn mainColumn(int x, int z) {
        double wx = x + CloudNoise.fbm(x, z, 260.0, 2, SEED_WARP) * 48.0;
        double wz = z + CloudNoise.fbm(x + 7919, z - 104729, 260.0, 2, SEED_WARP) * 48.0;

        double continent = CloudNoise.smoothstep(CONTINENT_LO, CONTINENT_HI,
                CloudNoise.fbm(wx, wz, CONTINENT_SCALE, 3, SEED_CONTINENT));
        double island = CloudNoise.smoothstep(ISLAND_LO, ISLAND_HI,
                CloudNoise.fbm(wx, wz, ISLAND_SCALE, 2, SEED_ISLAND));
        // 岛屿只长在大陆之外的虚空里
        double islandOnly = island * (1.0 - CloudNoise.smoothstep(0.0, 0.25, continent));
        double density = Math.max(continent, islandOnly * 0.95);
        if (density <= 0.02) {
            return null;
        }

        // 高度带：每片云整体抬高/压低，别让所有云挤在一个高度
        double altitude = BASE_Y + CloudNoise.fbm(x, z, ALT_SCALE, 3, SEED_ALT) * ALT_RANGE;
        // 厚度再乘一个 0.6~1.0 的系数，让同一片云有厚有薄
        double thickScale = 0.6 + 0.4 * (CloudNoise.fbm(x, z, THICK_SCALE, 2, SEED_THICK) * 0.5 + 0.5);
        int thickness = Math.max(1, (int) Math.round(density * MAX_THICKNESS * thickScale));

        // 悬垂：厚的地方往下多长 3~14 格
        if (density > 0.45) {
            double hang = CloudNoise.fbm(x, z, 90.0, 2, SEED_HANG);
            if (hang > 0.15) {
                thickness += (int) Math.round((hang - 0.15) / 0.85 * 14.0);
            }
        }

        int top = (int) Math.round(altitude + CloudNoise.fbm(x, z, 300.0, 2, SEED_HEIGHT) * 6.0);
        return new CloudColumn(top, top - thickness + 1);
    }

    /** 高空薄云片（1~3 格厚，只由云皮组成），和主云分开算。 */
    private static CloudColumn patchColumn(int x, int z, int mainTop) {
        double patch = CloudNoise.smoothstep(PATCH_LO, PATCH_HI,
                CloudNoise.fbm(x, z, PATCH_SCALE, 2, SEED_PATCH));
        if (patch <= 0.05) {
            return null;
        }
        int offset = 55 + (int) Math.round(CloudNoise.fbm(x, z, 160.0, 2, SEED_PATCH_ALT) * 22.0);
        int top = BASE_Y + offset;
        if (Math.abs(top - mainTop) < 6) {
            return null;   // 离主云太近就别贴上去
        }
        int thickness = Math.max(1, (int) Math.round(patch * 3.0));
        return new CloudColumn(top, top - thickness + 1);
    }

    private BlockState cloud() {
        return ModBlocks.CLOUD_BLOCK.get().defaultBlockState();
    }

    private BlockState thick() {
        return ModBlocks.THICK_CLOUD_BLOCK.get().defaultBlockState();
    }

    /** 厚云核里的透光洞：三维噪声掏掉一部分，但不碰外壳。 */
    private static boolean isHole(int x, int y, int z, int thicknessFromTop) {
        if (thicknessFromTop < 2) {
            return false;   // 顶部两层永远是实的
        }
        return CloudNoise.value3(x, y, z, 13.0, SEED_HOLE) > 0.58;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int minY = this.getMinY();
        int genDepth = this.getGenDepth();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = pos.getMinBlockX() + dx;
                int z = pos.getMinBlockZ() + dz;

                CloudColumn main = mainColumn(x, z);
                if (main != null) {
                    fillColumn(chunk, cursor, x, z, main, minY, genDepth, false);
                }
                CloudColumn patch = patchColumn(x, z, main == null ? Integer.MIN_VALUE : main.top());
                if (patch != null) {
                    fillColumn(chunk, cursor, x, z, patch, minY, genDepth, true);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /** 填一列：顶部/底部各一层云皮，中间是厚云核（薄云片则整列都是云皮）。 */
    private void fillColumn(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int x, int z,
                            CloudColumn column, int minY, int genDepth, boolean patch) {
        int thickness = column.top() - column.bottom() + 1;
        int shell = thickness <= 3 ? 1 : 2;
        for (int y = column.bottom(); y <= column.top(); y++) {
            if (y < minY || y >= minY + genDepth) {
                continue;
            }
            int fromTop = column.top() - y;
            boolean skin = patch || fromTop < shell || fromTop >= thickness - 1;
            if (!skin && isHole(x, y, z, fromTop)) {
                continue;
            }
            cursor.set(x, y, z);
            chunk.setBlockState(cursor, skin ? cloud() : thick(), 0);
        }
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState,
                             ChunkAccess chunk) {
        // 云没有"表面层"：所有方块都在 fillFromNoise 里放好了
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk) {
        // 云不需要洞穴（洞是三维噪声掏出来的）
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
        CloudColumn main = mainColumn(x, z);
        CloudColumn patch = patchColumn(x, z, main == null ? Integer.MIN_VALUE : main.top());
        int top = Math.max(main == null ? Integer.MIN_VALUE : main.top(), patch == null ? Integer.MIN_VALUE : patch.top());
        return top == Integer.MIN_VALUE ? this.getMinY() - 1 : top + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int minY = this.getMinY();
        BlockState[] states = new BlockState[this.getGenDepth()];
        java.util.Arrays.fill(states, Blocks.AIR.defaultBlockState());

        CloudColumn main = mainColumn(x, z);
        CloudColumn patch = patchColumn(x, z, main == null ? Integer.MIN_VALUE : main.top());
        for (CloudColumn column : new CloudColumn[]{main, patch}) {
            if (column == null) {
                continue;
            }
            int thickness = column.top() - column.bottom() + 1;
            int shell = thickness <= 3 ? 1 : 2;
            for (int y = column.bottom(); y <= column.top(); y++) {
                int index = y - minY;
                if (index < 0 || index >= states.length) {
                    continue;
                }
                int fromTop = column.top() - y;
                boolean skin = column == patch || fromTop < shell || fromTop >= thickness - 1;
                if (!skin && isHole(x, y, z, fromTop)) {
                    continue;
                }
                states[index] = skin ? cloud() : thick();
            }
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        CloudColumn main = mainColumn(pos.getX(), pos.getZ());
        lines.add(main == null
                ? "Cloud: none"
                : "Cloud: " + main.bottom() + " .. " + main.top()
                        + " (" + (main.top() - main.bottom() + 1) + " thick)");
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState randomState,
                                                    long seed) {
        // 云维度暂时不生成结构
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource,
                java.util.stream.Stream.empty());
    }
}
