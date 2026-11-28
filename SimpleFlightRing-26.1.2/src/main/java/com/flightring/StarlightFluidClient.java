package com.flightring;

import net.minecraft.client.renderer.block.FluidModel;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;

/**
 * 液态星光的客户端渲染（26.x）。
 * <p>
 * 26.x 把流体贴图从 {@code IClientFluidTypeExtensions} 挪到了"模型"这一层：
 * 要在 {@link RegisterFluidModelsEvent} 里注册 {@link FluidModel.Unbaked}（still / flowing /
 * overlay / tint 四个参数）。我们不做彩虹、也不需要染色，所以 overlay 与 tint 都传 null。
 * <p>
 * 动画（星点闪烁、十字浮现）完全由贴图的 {@code .mcmeta} 帧动画实现，没有 shader。
 */
public final class StarlightFluidClient {

    private static final Material STILL = new Material(
            Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "block/liquid_starlight_still"));

    private static final Material FLOWING = new Material(
            Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "block/liquid_starlight_flow"));

    /**
     * 显式的染色源。
     * <p>
     * Xaero 之类的地图 mod 对 {@code LiquidBlock} 取色的实际分支是
     * <pre>
     *   BlockTintSource tint = FluidStateModelSet.get(fluidState).tintSource();
     *   if (tint == null) return -1;                       // 没有 tint ⇒ 走"当水"的兜底
     *   return tint.colorInWorld(state, getter, pos);      // ← 注意是**带坐标**的这个重载
     * </pre>
     * 所以四个重载必须给同一个颜色，只控制无坐标那个没用（那是上一版的错误）。
     * <p>
     * 颜色取"淡星光白蓝"：它同时是游戏内染色的乘数，太暗会把贴图压黑，
     * 淡色几乎不改变世界里的观感，却能让地图上明显不是水的蓝。
     */
    private static final int STARLIGHT_TINT_COLOR = 0xFFD8E2FF;

    private static final FluidTintSource STARLIGHT_TINT = new FluidTintSource() {
        @Override
        public int color(FluidState state) {
            return STARLIGHT_TINT_COLOR;
        }

        @Override
        public int color(BlockState state) {
            return STARLIGHT_TINT_COLOR;
        }

        @Override
        public int colorInWorld(FluidState state, BlockState blockState,
                                BlockAndTintGetter getter, BlockPos pos) {
            return STARLIGHT_TINT_COLOR;
        }

        @Override
        public int colorInWorld(BlockState blockState, BlockAndTintGetter getter, BlockPos pos) {
            return STARLIGHT_TINT_COLOR;
        }
    };

    private StarlightFluidClient() {
    }

    public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        // 源与流动两种流体共用同一套贴图
        event.register(new FluidModel.Unbaked(STILL, FLOWING, null, STARLIGHT_TINT), ModFluids.LIQUID_STARLIGHT);
        event.register(new FluidModel.Unbaked(STILL, FLOWING, null, STARLIGHT_TINT), ModFluids.FLOWING_LIQUID_STARLIGHT);
    }
}
