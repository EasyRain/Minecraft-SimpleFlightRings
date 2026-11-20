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
 * 天空类型用 {@link SkyType#NORMAL}：保留太阳、月亮、星星。
 * <p>
 * 试过 {@link SkyType#NONE}（不画日月星辰、天空只剩雾色）—— 用户 2026-11-20 评价"太素了"，
 * 宁可要带日月的天空，所以退回 NORMAL。副作用是虚空维度里白天能同时看到头顶的太阳和脚下的月亮；
 * 这个现象要靠**自己接管 {@code renderSky}**（只画天空 + 太阳、不画月亮）才能消掉，
 * 属于自己写整套天空渲染，等工作量允许再考虑。
 * <p>
 * 云高设成 **192**（原版默认）：我们的云岛主层在 **256**，于是原版云在脚下变成一片"云海"，
 * 不会再和云岛叠在同一层。
 * <p>
 * 试过用 NaN 关掉原版云，但用户的客户端装了 Sodium/Iris：即使效果类生效，云也照样画出来，
 * 所以改成"把它放到脚下"这种不依赖渲染管线行为的办法。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudDimensionEffects extends DimensionSpecialEffects {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "the_cloud");

    public CloudDimensionEffects() {
        // cloudLevel = NaN（不画原版云，避免和云岛重叠）、hasGround = false、天空类型 NORMAL（保留日月星辰）
        super(192.0F, false, SkyType.NORMAL, false, false);
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
