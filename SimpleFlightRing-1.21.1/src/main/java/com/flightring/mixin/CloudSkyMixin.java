package com.flightring.mixin;

import com.flightring.BrokenPortalTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 云维度 {@code the_cloud} 的天空稳定器：让原版天空（天穹盘/日出盘/太阳/月亮/星星）
 * 用【无 view bob 的干净投影】绘制，与 {@link com.flightring.AuroraSky} 的极光同源同速，
 * 整个"天空家族"运动一致 —— 复刻光影包（Complementary Unbound 等）的观感：
 * 走路/受击时前景地形照常晃，天空整体纹丝不动。
 * <p>
 * 为什么需要它：原版把 view bob（走路摇晃）/bobHurt/反胃翻转折进【全局投影矩阵】
 * （{@code GameRenderer#renderLevel}），原版天空因此跟着画面一起抖；极光（干净投影）
 * 不抖 ⇒ 星星在极光背后晃，观感上"天空和相机之间卡了一层固定的极光"。
 * 只稳定天空、不动地形：本 mixin 只替换传给 {@code renderSky} 的投影参数，
 * 地形/实体用的投影原封不动（继续晃，原版手感）。
 * <p>
 * 作用域：**仅 the_cloud 维度**（其他维度零改动）。sky 渲染整维度稳定而非只极光群系，
 * 避免跨群系边界时星星运动状态突跳。
 * <p>
 * 与 Iris 共存：装了光影包时 Iris 自己就把 bob 从投影里剥离
 * （{@code MixinModelViewBobbing#separateViewBobbing}），传入 renderSky 的投影本来就是
 * 干净的，这里替换进去的是同一个值（getProjectionMatrix(getFov(...))），等价无害；
 * Iris 无包时走原版路径，本 mixin 照常生效。
 * <p>
 * 干净投影 = {@code gameRenderer.getProjectionMatrix(gameRenderer.getFov(camera, partialTick, true))}
 * —— FOV/疾跑/缩放与画面严格一致、只少摇晃成分。getFov 经
 * {@code META-INF/accesstransformer.cfg} 开放（private → public）。
 */
@Mixin(LevelRenderer.class)
public abstract class CloudSkyMixin {

    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V"
            ),
            index = 1
    )
    private Matrix4f simpleflightring$stableSkyProjection(Matrix4f projection) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.level.dimension().equals(BrokenPortalTravel.CLOUD_DIMENSION)) {
            return projection;   // 其他维度：原版行为，零改动
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        double fov = minecraft.gameRenderer.getFov(minecraft.gameRenderer.getMainCamera(), partialTick, true);
        return minecraft.gameRenderer.getProjectionMatrix(fov);
    }
}
