package com.flightring;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * 云维度的群系层：用一张大尺度噪声把天空切成 5 个群系（云海 / 晴空 / 雷暴 / 极光 / 霞云）。
 * <p>
 * 为什么不用 {@code minecraft:multi_noise}：那要在 JSON 里手写几十个温度/湿度参数点，改一次配色就得动参数表；
 * 而这里只想要"几块很大的天气区域"，一张噪声 + 阈值就够，而且随机源是确定性的 {@link CloudNoise}。
 * <p>
 * 群系通过 {@code RegistryOps.retrieveRegistryLookup(Registries.BIOME)} 拿注册表：维度 JSON 被解析时
 * 注册表已经完整可用（这是 {@link BrokenIslandBiomeSource} 踩过坑之后立的规矩）。
 */
public class CloudBiomeSource extends BiomeSource {

    public static final MapCodec<CloudBiomeSource> CODEC =
            RegistryOps.retrieveRegistryLookup(Registries.BIOME)
                    .xmap(CloudBiomeSource::new, source -> source.lookup);

    private static final ResourceKey<Biome> CLOUD_SEA = key("cloud_sea");
    private static final ResourceKey<Biome> CLEAR_SKY = key("clear_sky");
    private static final ResourceKey<Biome> THUNDERHEAD = key("thunderhead");
    private static final ResourceKey<Biome> AURORA = key("aurora");
    private static final ResourceKey<Biome> SUNSET_CLOUDS = key("sunset_clouds");

    /** 天气区域的空间尺度（格）：越大，一个群系占地越广。 */
    private static final double REGION_SCALE = 1100.0;
    private static final long SEED_TEMPERATURE = 0x1F83D9ABL;
    private static final long SEED_HUMIDITY = 0x5BE0CD19L;

    private final HolderLookup.RegistryLookup<Biome> lookup;
    private final List<Holder<Biome>> biomes;

    public CloudBiomeSource(HolderLookup.RegistryLookup<Biome> lookup) {
        this.lookup = lookup;
        this.biomes = List.of(
                lookup.getOrThrow(CLOUD_SEA),
                lookup.getOrThrow(CLEAR_SKY),
                lookup.getOrThrow(THUNDERHEAD),
                lookup.getOrThrow(AURORA),
                lookup.getOrThrow(SUNSET_CLOUDS));
    }

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath(FlightRingMod.MODID, path));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // x/z 是 quart 坐标（1 格 = 4 方块）
        double bx = x * 4.0;
        double bz = z * 4.0;
        double temperature = CloudNoise.fbm(bx, bz, REGION_SCALE, 2, SEED_TEMPERATURE);
        double humidity = CloudNoise.fbm(bx, bz, REGION_SCALE, 2, SEED_HUMIDITY);

        Holder<Biome> picked;
        if (temperature > 0.34) {
            // 干热：霞云
            picked = this.biomes.get(4);
        } else if (temperature > 0.10) {
            // 温和偏干：晴空
            picked = this.biomes.get(1);
        } else if (humidity > 0.30) {
            // 湿：雷暴云
            picked = this.biomes.get(2);
        } else if (temperature < -0.30) {
            // 冷：极光
            picked = this.biomes.get(3);
        } else {
            // 默认：云海
            picked = this.biomes.get(0);
        }
        return picked;
    }
}
