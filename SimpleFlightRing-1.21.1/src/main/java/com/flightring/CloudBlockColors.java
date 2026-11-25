package com.flightring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * 云层方块的群系染色（1.21.1）。
 * <p>
 * ★ 两个关键点：
 * <ol>
 *   <li>区块渲染时传进来的 {@code BlockAndTintGetter} 是 {@code RenderChunkRegion}，
 *       <b>既不是 Level 也不是 LevelReader</b>，拿不到 {@code getBiome}；
 *       必须走 {@code getBlockTint(pos, resolver)}（草方块变色也走这条路）。</li>
 *   <li>{@code ColorResolver} 只给 {@code Biome} 实例，而 biomes 是动态注册表、
 *       {@code BuiltInRegistries} 里没有它 —— 要用 {@code Minecraft.getInstance().level}
 *       的 {@code registryAccess()} 反查 id。</li>
 * </ol>
 * 三张表：云块、云绒草、云絮花（白花）。后两者按用户 2026-11-25 的要求单独配色，
 * 极光花/阳光花各自只有一个群系，不需要染色。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudBlockColors {

    private static final Map<Biome, Integer> BLOCK_CACHE = new ConcurrentHashMap<>();
    private static final Map<Biome, Integer> GRASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Biome, Integer> FLOWER_CACHE = new ConcurrentHashMap<>();

    /** 云块 / 云皮 / 厚云块 / 云叶的解析器。 */
    public static final ColorResolver RESOLVER =
            (biome, x, z) -> tintOf(biome, CloudColors::of, BLOCK_CACHE, CloudColors.DEFAULT);

    /** 云绒草的解析器。 */
    public static final ColorResolver GRASS_RESOLVER =
            (biome, x, z) -> tintOf(biome, CloudColors::grass, GRASS_CACHE, CloudColors.PLANT_DEFAULT);

    /** 云絮花（白花）的解析器。 */
    public static final ColorResolver FLOWER_RESOLVER =
            (biome, x, z) -> tintOf(biome, CloudColors::flower, FLOWER_CACHE, CloudColors.PLANT_DEFAULT);

    private CloudBlockColors() {
    }

    private static int tintOf(Biome biome, ToIntFunction<ResourceLocation> table,
                              Map<Biome, Integer> cache, int fallback) {
        return cache.computeIfAbsent(biome, b -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return fallback;
            }
            ResourceLocation id = minecraft.level.registryAccess()
                    .registryOrThrow(Registries.BIOME)
                    .getResourceKey(b)
                    .map(key -> key.location())
                    .orElse(null);
            return table.applyAsInt(id);
        });
    }

    private static int tintAt(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos,
                              ColorResolver resolver, int fallback) {
        if (level == null || pos == null) {
            return fallback;
        }
        return level.getBlockTint(pos, resolver);
    }

    @SubscribeEvent
    public static void onRegisterColorResolvers(RegisterColorHandlersEvent.ColorResolvers event) {
        event.register(RESOLVER);
        event.register(GRASS_RESOLVER);
        event.register(FLOWER_RESOLVER);
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> tintAt(level, pos, RESOLVER, CloudColors.DEFAULT),
                ModBlocks.CLOUD_BLOCK.get(),
                ModBlocks.CLOUD_BLOCK_SLAB.get(),
                ModBlocks.CLOUD_BLOCK_STAIRS.get(),
                ModBlocks.CLOUD_BLOCK_WALL.get(),
                ModBlocks.THICK_CLOUD_BLOCK.get(),
                ModBlocks.THICK_CLOUD_SLAB.get(),
                ModBlocks.THICK_CLOUD_STAIRS.get(),
                ModBlocks.THICK_CLOUD_WALL.get(),
                ModBlocks.THICK_CLOUD_BRICKS.get(),
                ModBlocks.THICK_CLOUD_BRICKS_SLAB.get(),
                ModBlocks.THICK_CLOUD_BRICKS_STAIRS.get(),
                ModBlocks.THICK_CLOUD_BRICKS_WALL.get(),
                // 树叶：只染叶子，树干/木板保持原色
                ModBlocks.CLOUD_LEAVES.get());

        // 花草：只染云绒草和云絮花（极光花/阳光花各自只有一个群系，不需要）
        event.register(
                (state, level, pos, tintIndex) -> tintAt(level, pos, GRASS_RESOLVER, CloudColors.PLANT_DEFAULT),
                ModBlocks.CLOUD_GRASS.get());
        event.register(
                (state, level, pos, tintIndex) -> tintAt(level, pos, FLOWER_RESOLVER, CloudColors.PLANT_DEFAULT),
                ModBlocks.CLOUD_BLOOM.get());
    }
}
