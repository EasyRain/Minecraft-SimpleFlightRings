package com.flightring;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * {@code simpleflightring:the_cloud} 的维度视觉效果。
 * <p>
 * 为什么要自己写一个：**1.21.1 的云高不来自 dimension_type，而来自维度特效用 {@code getCloudHeight()}**。
 * 我们先前借用 {@code minecraft:overworld} 的特效，于是原版那层云（y=192）也跟进了云维度 ——
 * 而云维度的地形主层正好也在 192，会糊在一起。
 * <p>
 * 这里返回 {@link Float#NaN}，客户端就不画原版云了 —— 云维度的"云"全部由我们自己的云块组成。
 * 想要原版云回来，把 dimension_type 的 {@code effects} 改回 {@code minecraft:overworld} 即可。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudDimensionEffects extends DimensionSpecialEffects {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "the_cloud");

    public CloudDimensionEffects() {
        // cloudLevel = NaN（不画原版云）、hasGround = false、天空类型 NORMAL、不强制亮色、非常亮环境光
        super(Float.NaN, false, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @SubscribeEvent
    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ID, new CloudDimensionEffects());
    }
}
