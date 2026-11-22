package com.flightring;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 云门不再显示那层紫色滤镜（用户 2026-11-22）。
 * <p>
 * 关键发现：原版那层"进门滤镜"的贴图是<b>写死的</b> ——
 * <ul>
 *   <li>1.21.1：{@code Gui#renderPortalOverlay} 用 {@code Blocks.NETHER_PORTAL} 的 particle 图标；</li>
 *   <li>26.1.2：{@code Gui#extractPortalOverlay} 同样写死 {@code Blocks.NETHER_PORTAL}。</li>
 * </ul>
 * 所以换我们方块的贴图、取消 {@code RenderBlockScreenEffectEvent} 都没用 —— 那根本不是同一条路径。
 * 真正驱动它的是玩家身上的"旋转/传送门"强度值：
 * <ul>
 *   <li>1.21.1：{@code LocalPlayer#spinningEffectIntensity}（+ o... 上一帧）；</li>
 *   <li>26.1.2：{@code LocalPlayer#portalEffectIntensity}（+ o... 上一帧）。</li>
 * </ul>
 * 站在云门里时把这两个值清零，滤镜自然就不画了，而且不需要 mixin。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudPortalOverlay {

    private CloudPortalOverlay() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (!isInsideCloudPortal(player)) {
            return;
        }
        // 26.1.2 用的是 portalEffectIntensity（Gui#extractPortalOverlay 的驱动值）
        player.portalEffectIntensity = 0.0F;
        player.oPortalEffectIntensity = 0.0F;
    }

    /** 玩家身体或眼睛是否在云门/破门方块里。 */
    /** 供 mixin 复用：玩家身体/眼睛是否在云门里。 */
    public static boolean isInsideCloudPortal(LocalPlayer player) {
        for (int i = 0; i < 8; i++) {
            double dx = (((i >> 0) % 2) - 0.5D) * player.getBbWidth() * 0.8D;
            double dy = (((i >> 1) % 2) - 0.5D) * 0.1D * player.getScale();
            double dz = (((i >> 2) % 2) - 0.5D) * player.getBbWidth() * 0.8D;
            BlockPos pos = BlockPos.containing(player.getX() + dx, player.getEyeY() + dy, player.getZ() + dz);
            BlockState state = player.level().getBlockState(pos);
            if (state.getBlock() instanceof BrokenPortalBlock) {
                return true;
            }
        }
        return false;
    }
}
