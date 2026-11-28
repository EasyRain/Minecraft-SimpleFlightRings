package com.flightring;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * 液态星光的客户端渲染（1.21.1）。
 * <p>
 * 1.21.1 走 {@code IClientFluidTypeExtensions}：贴图 + 染色都在这里给。
 * 我们不做彩虹、也不需要染色，所以 {@code getTintColor} 直接返回不透明白（不做乘法）。
 * <p>
 * 动画（星点闪烁、十字浮现）完全由贴图的 {@code .mcmeta} 帧动画实现，没有 shader。
 */
public final class StarlightFluidClient {

    private static final ResourceLocation STILL = ResourceLocation.fromNamespaceAndPath(
            FlightRingMod.MODID, "block/liquid_starlight_still");

    private static final ResourceLocation FLOWING = ResourceLocation.fromNamespaceAndPath(
            FlightRingMod.MODID, "block/liquid_starlight_flow");

    private StarlightFluidClient() {
    }

    public static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOWING;
            }

            @Override
            public int getTintColor() {
                // 游戏内渲染：贴图已是最终色，不做乘法
                return 0xFFFFFFFF;
            }

            @Override
            public int getTintColor(net.minecraft.world.level.material.FluidState state,
                                    net.minecraft.world.level.BlockAndTintGetter getter,
                                    net.minecraft.core.BlockPos pos) {
                // 地图 mod（Xaero 用自己的 getter）要一个显眼的地图色；游戏内渲染给白
                return getter != null && getter.getClass().getName().startsWith("xaero.")
                        ? 0xFF6A5AE0 : 0xFFFFFFFF;
            }
        }, ModFluids.LIQUID_STARLIGHT_TYPE.get());
    }
}
