package com.flightring;

import java.util.List;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * 云块的群系染色（26.x 用 {@code BlockTintSource} + {@code BlockTintSources} 事件，
 * 不再是 1.21.1 那种 {@code BlockColor}）。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudBlockTint {

    /** 按位置查群系颜色；没有世界/坐标时退回默认色。 */
    private static final class BiomeTint implements BlockTintSource {

        @Override
        public int color(BlockState state) {
            return CloudColors.DEFAULT;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            // BlockAndTintGetter 不带 getBiome，实际渲染时是 ClientLevel（LevelReader）
            if (level instanceof net.minecraft.world.level.LevelReader reader) {
                return CloudColors.of(reader.getBiome(pos));
            }
            return CloudColors.DEFAULT;
        }
    }

    private CloudBlockTint() {
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
