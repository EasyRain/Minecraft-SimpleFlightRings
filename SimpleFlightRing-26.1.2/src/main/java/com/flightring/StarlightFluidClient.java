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
     * <b>世界内不染色</b>（{@code colorInWorld} 返回不透明白）—— 贴图本身已经是深蓝黑 + 星点；
     * <b>无坐标查询才给颜色</b>（{@code color}）—— Xaero 之类的 map mod 就是这样取色：
     * {@code BlockTintProvider} 只在 {@code state instanceof LiquidBlock} 时去问
     * {@code FluidModel#tintSource()}，没有 tint 源时它会退化成"当水处理"、套生物群系水色。
     */
    private static final FluidTintSource STARLIGHT_TINT = new FluidTintSource() {
        @Override
        public int color(FluidState state) {
            return 0xFF10142A;
        }

        @Override
        public int colorInWorld(FluidState state, BlockState blockState,
                                BlockAndTintGetter getter, BlockPos pos) {
            return 0xFFFFFFFF;
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
