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
                // 无坐标查询（map mod 常用这条）给星液的深蓝紫色
                return 0xFF10142A;
            }

            @Override
            public int getTintColor(net.minecraft.world.level.material.FluidState state,
                                    net.minecraft.world.level.BlockAndTintGetter getter,
                                    net.minecraft.core.BlockPos pos) {
                // 世界内不染色：贴图本身已经是深蓝黑 + 星点
                return 0xFFFFFFFF;
            }
        }, ModFluids.LIQUID_STARLIGHT_TYPE.get());
    }
}
