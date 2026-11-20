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
 * 天空类型用 {@link SkyType#NONE}：**不画太阳、月亮、星星**。
 * <p>
 * 为什么必须这样：NORMAL 天空类型会把太阳和月亮**永远画在天空球的两端**，而虚空维度没有地形遮挡，
 * 于是白天同时看到头顶太阳和脚下月亮（用户 2026-11-20 实测）。把原版云放到脚下（cloudLevel=64）
 * **挡不住** —— 天空球在云层后面，而且下半球的雾本来就是半透明的。
 * <p>
 * NONE 的效果：天空 = 每帧用雾色清屏（我们群系的雾色是亮白偏蓝）⇒ 一片干净的高空亮雾，
 * 没有任何天体穿过画面。想要太阳/月亮，需要自己接管 {@code renderSky}（工作量大，暂不做）。
 * <p>
 * 云高保留 64：玩家在主层 192，脚下仍然有一整片原版云海，M2 铺上我们自己的云层后它就是"云海底部"。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudDimensionEffects extends DimensionSpecialEffects {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "the_cloud");

    public CloudDimensionEffects() {
        // cloudLevel = 64（原版云海在脚下）、hasGround = false、天空类型 NONE（不画日月星辰）、不强制亮色、非常亮环境光
        super(64.0F, false, SkyType.NONE, false, false);
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
