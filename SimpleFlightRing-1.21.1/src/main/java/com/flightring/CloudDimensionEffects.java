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
 * 云高设成 **64**：玩家在云维度里（主层 192）始终**站在原版云层之上**，于是
 * <ul>
 *   <li>脚下是一整片原版云海，不会只看到空荡荡的虚空；</li>
 *   <li>同时把"下半球"的夜空挡住 —— 之前设成 NaN（不画云）时，因为没有地形遮挡，
 *       白天能同时看到头顶的太阳和脚下的月亮（用户 2026-11-20 实测）。</li>
 * </ul>
 * M2 会把我们自己的云层地形铺在 192，那时这片原版云海就变成"云海底部"的背景。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudDimensionEffects extends DimensionSpecialEffects {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "the_cloud");

    public CloudDimensionEffects() {
        // cloudLevel = 64（原版云海在脚下）、hasGround = false、天空类型 NORMAL、不强制亮色、非常亮环境光
        super(64.0F, false, SkyType.NORMAL, false, false);
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
