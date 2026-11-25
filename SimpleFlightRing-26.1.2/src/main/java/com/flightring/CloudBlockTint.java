package com.flightring;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * 云层方块的群系染色（26.x：{@code BlockTintSource} + {@code BlockTintSources} 事件）。
 * <p>
 * 取色同样走 {@code getBlockTint(pos, resolver)}，理由见 1.21.1 那份的注释
 * （区块渲染时拿到的是 RenderChunkRegion，拿不到 getBiome；biomes 是动态注册表）。
 * 三张表：云块、云绒草、云絮花（白花）。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudBlockTint {

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

    private CloudBlockTint() {
    }

    /** 26.x 的染色源：世界中按群系取色，脱离世界（物品栏/预览）时退回兜底色。 */
    private record BiomeTint(ColorResolver resolver, int fallback) implements BlockTintSource {

        @Override
        public int color(BlockState state) {
            return fallback;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            if (level == null || pos == null) {
                return fallback;
            }
            return level.getBlockTint(pos, resolver);
        }
    }

    private static final BiomeTint BLOCK_TINT = new BiomeTint(RESOLVER, CloudColors.DEFAULT);
    private static final BiomeTint GRASS_TINT = new BiomeTint(GRASS_RESOLVER, CloudColors.PLANT_DEFAULT);
    private static final BiomeTint FLOWER_TINT = new BiomeTint(FLOWER_RESOLVER, CloudColors.PLANT_DEFAULT);

    private static int tintOf(Biome biome, ToIntFunction<Identifier> table,
                              Map<Biome, Integer> cache, int fallback) {
        return cache.computeIfAbsent(biome, b -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return fallback;
            }
            Identifier id = minecraft.level.registryAccess()
                    .lookupOrThrow(Registries.BIOME)
                    .getResourceKey(b)
                    .map(key -> key.identifier())
                    .orElse(null);
            return table.applyAsInt(id);
        });
    }

    @SubscribeEvent
    public static void onRegisterColorResolvers(RegisterColorHandlersEvent.ColorResolvers event) {
        event.register(RESOLVER);
        event.register(GRASS_RESOLVER);
        event.register(FLOWER_RESOLVER);
    }

    @SubscribeEvent
    public static void onRegisterTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        register(event, BLOCK_TINT,
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
        register(event, GRASS_TINT, ModBlocks.CLOUD_GRASS.get());
        register(event, FLOWER_TINT, ModBlocks.CLOUD_BLOOM.get());
    }

    private static void register(RegisterColorHandlersEvent.BlockTintSources event,
                                 BlockTintSource source, Block... blocks) {
        event.register(List.of(source), blocks);
    }
}
