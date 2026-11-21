package com.flightring;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 启动自检：把模组注册的所有物品 id 打一行日志。
 * <p>
 * 作用很单纯 —— 出现"创造栏里看不到某个物品"时，先确认它到底有没有注册：
 * 日志里有 id ⇒ 注册没问题，问题在创造栏；日志里没有 ⇒ 注册阶段就没成。
 * 用户 2026-11-21 的排查就是靠这个把口径定下来的。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class StartupReport {

    private StartupReport() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        List<String> ids = new ArrayList<>();
        ModItems.ITEMS.getEntries().forEach(entry -> {
            String path = entry.getId().getPath();
            if (path.startsWith("cloud_") || path.startsWith("stripped_cloud_") || path.startsWith("thick_cloud_")) {
                ids.add(path);
            }
        });
        FlightRingMod.LOGGER.info("[FlightRing] registered cloud items ({}): {}", ids.size(), ids);
    }
}
