package com.flightring;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * 云块的群系染色（26.x：{@code BlockTintSource} + {@code BlockTintSources} 事件）。
 * <p>
 * 取色同样走 {@code getBlockTint(pos, resolver)}，理由见 1.21.1 那份的注释
 * （区块渲染时拿到的是 RenderChunkRegion，拿不到 getBiome；biomes 是动态注册表）。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudBlockTint {

    private static final Map<Biome, Integer> CACHE = new ConcurrentHashMap<>();

    public static final ColorResolver RESOLVER = (biome, x, z) -> colorOf(biome);

    private static final class BiomeTint implements BlockTintSource {

        @Override
        public int color(BlockState state) {
            return CloudColors.DEFAULT;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            if (pos == null) {
                return CloudColors.DEFAULT;
            }
            return level.getBlockTint(pos, RESOLVER);
        }
    }

    private CloudBlockTint() {
    }

    private static int colorOf(Biome biome) {
        return CACHE.computeIfAbsent(biome, b -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return CloudColors.DEFAULT;
            }
            var key = minecraft.level.registryAccess().lookupOrThrow(Registries.BIOME).getResourceKey(b).orElse(null);
            return CloudColors.of(key == null ? null : key.identifier());
        });
    }

    @SubscribeEvent
    public static void onRegisterColorResolvers(RegisterColorHandlersEvent.ColorResolvers event) {
        event.register(RESOLVER);
    }

    @SubscribeEvent
    public static void onRegisterTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(new BiomeTint()),
                ModBlocks.CLOUD_BLOCK.get(),
                ModBlocks.CLOUD_BLOCK_SLAB.get(),
                ModBlocks.CLOUD_BLOCK_STAIRS.get(),
                ModBlocks.CLOUD_BLOCK_WALL.get());
    }
}
