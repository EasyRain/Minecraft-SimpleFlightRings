package com.flightring;

import net.minecraft.client.renderer.block.FluidModel;
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

    private StarlightFluidClient() {
    }

    public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        // 源与流动两种流体共用同一套贴图
        event.register(new FluidModel.Unbaked(STILL, FLOWING, null, null), ModFluids.LIQUID_STARLIGHT);
        event.register(new FluidModel.Unbaked(STILL, FLOWING, null, null), ModFluids.FLOWING_LIQUID_STARLIGHT);
    }
}
