package com.flightring;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 临时诊断：把玩家所在列的各种高度值打出来，定位"地图显示的高度不是岛顶"的问题。
 * 只读，不改变任何行为；排查完删除。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class SunsetTintDebug {

    private static int lastTick = -1000;

    private SunsetTintDebug() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (!minecraft.level.dimension().equals(BrokenPortalTravel.CLOUD_DIMENSION)) {
            return;
        }
        int tick = minecraft.player.tickCount;
        if (tick - lastTick < 100) {
            return;
        }
        lastTick = tick;

        BlockPos pos = minecraft.player.blockPosition();
        Level level = minecraft.level;
        int maxY = level.getHeight();   // Level#getHeight() 返回总高度（LevelHeightAccessor）
        int scannedTop = Integer.MIN_VALUE;
        int minY = level.getMinBuildHeight();
        for (int y = maxY - 1; y >= minY; y--) {
            if (!level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).isAir()) {
                scannedTop = y;
                break;
            }
        }
        FlightRingMod.LOGGER.info(
                "[HeightDiag] xz={},{} playerY={} scannedTop={} WORLD_SURFACE={} MOTION_BLOCKING={} OCEAN_FLOOR={} MB_NO_LEAVES={}",
                pos.getX(), pos.getZ(), pos.getY(), scannedTop,
                level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()),
                level.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ()),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()));
    }
}
