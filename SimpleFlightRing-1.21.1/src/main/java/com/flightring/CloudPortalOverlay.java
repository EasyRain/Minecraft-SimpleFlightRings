package com.flightring;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

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
public final class CloudPortalOverlay {

    private CloudPortalOverlay() {
    }

    /** 最近一次"人在云门里"的 tick，用于滤镜淡出期间保持我们的颜色（用户 2026-11-22）。 */
    private static int lastInsideTick = Integer.MIN_VALUE / 2;

    /**
     * 滤镜是否该由我们接管。
     * <p>
     * 只看"此刻在不在门里"会漏掉**中途退出传送门**的情况：原版那层滤镜不会立刻消失，
     * 而是快速淡出；一旦我们已经离开方块，判定为 false => 退回原版紫色，
     * 于是"蓝 → 紫"闪一下。所以这里额外记住最近一次在门里的 tick，
     * 淡出期间（{@value #FADE_GRACE_TICKS} tick 内）继续用我们的贴图。
     */
    public static boolean shouldDrawCloudOverlay(LocalPlayer player) {
        if (isInsideCloudPortal(player)) {
            lastInsideTick = player.tickCount;
            return true;
        }
        return player.tickCount - lastInsideTick <= FADE_GRACE_TICKS;
    }

    /** 滤镜淡出的宽限时长（tick）。原版的淡出大约 1 秒，这里给 2 秒更保险。 */
    private static final int FADE_GRACE_TICKS = 40;

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
