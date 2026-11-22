package com.flightring;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;

/**
 * 传送门屏幕滤镜（换掉地狱门那层紫色）。
 * <p>
 * 原版机制：{@code ScreenEffectRenderer} 在玩家身处方块内时，取该方块模型自带的
 * <b>particle 贴图</b> 铺满全屏、颜色乘 0.1。我们的传送门方块在这条路径上最终拿到的是
 * 地狱门的紫色贴图（用户 2026-11-22 反馈"和下界门一模一样"）。
 * <p>
 * 与其继续猜原版从哪儿取图，直接把这个 overlay 取消 —— 云门不再有紫色滤镜，
 * 维度切换本身的原版闪光仍然保留。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudPortalOverlay {

    private CloudPortalOverlay() {
    }

    @SubscribeEvent
    public static void onBlockOverlay(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.BLOCK) {
            return;
        }
        if (!(event.getBlockState().getBlock() instanceof BrokenPortalBlock)) {
            return;
        }
        event.setCanceled(true);
    }
}
