package com.flightring;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * 云块的群系染色（1.21.1 用 {@code BlockColors} + {@code BlockColor}）。
 * <p>
 * 只给云块的四个方块（本体 + 半砖 / 台阶 / 墙）注册；颜色来自 {@link CloudColors}。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudBlockColors {

    private CloudBlockColors() {
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> {
                    // BlockAndTintGetter 本身不带 getBiome，实际渲染时拿到的是 LevelReader（ClientLevel）
                    if (pos != null && level instanceof net.minecraft.world.level.LevelReader reader) {
                        return CloudColors.of(reader.getBiome(pos));
                    }
                    return CloudColors.DEFAULT;
                },
                ModBlocks.CLOUD_BLOCK.get(),
                ModBlocks.CLOUD_BLOCK_SLAB.get(),
                ModBlocks.CLOUD_BLOCK_STAIRS.get(),
                ModBlocks.CLOUD_BLOCK_WALL.get());
    }
}
