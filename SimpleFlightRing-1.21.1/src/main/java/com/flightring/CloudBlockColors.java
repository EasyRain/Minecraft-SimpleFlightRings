package com.flightring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * 云块的群系染色（1.21.1）。
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
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudBlockColors {

    /** biome -> 颜色 的缓存，避免每次渲染都查注册表。 */
    private static final Map<Biome, Integer> CACHE = new ConcurrentHashMap<>();

    /** 解析器本身（注册进 NeoForge 的 ColorResolver 列表，供 getBlockTint 使用）。 */
    public static final ColorResolver RESOLVER = (biome, x, z) -> colorOf(biome);

    private CloudBlockColors() {
    }

    private static int colorOf(Biome biome) {
        return CACHE.computeIfAbsent(biome, b -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return CloudColors.DEFAULT;
            }
            var key = minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).getResourceKey(b).orElse(null);
            int color = CloudColors.of(key == null ? null : key.location());
            if (key != null) {
                FlightRingMod.LOGGER.debug("[CloudTint] {} -> #{}{}{}", key.location(),
                        String.format("%06X", color), "", "");
            }
            return color;
        });
    }

    @SubscribeEvent
    public static void onRegisterColorResolvers(RegisterColorHandlersEvent.ColorResolvers event) {
        event.register(RESOLVER);
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> {
                    if (level == null || pos == null) {
                        return CloudColors.DEFAULT;
                    }
                    return level.getBlockTint(pos, RESOLVER);
                },
                ModBlocks.CLOUD_BLOCK.get(),
                ModBlocks.CLOUD_BLOCK_SLAB.get(),
                ModBlocks.CLOUD_BLOCK_STAIRS.get(),
                ModBlocks.CLOUD_BLOCK_WALL.get());
    }
}
