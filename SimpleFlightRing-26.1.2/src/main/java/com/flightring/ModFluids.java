package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundAction;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 液态星光（用户 2026-11-28）：云海里的发光流体，深黑背景 + 星光点点。
 * <p>
 * 行为刻意做得"不像水"：
 * <ul>
 *   <li>{@code density} 为负 ⇒ 泡在里面会被轻轻往上托；</li>
 *   <li>{@code viscosity} 很高 ⇒ 流动很慢，像稠液；</li>
 *   <li>{@code canDrown(false)} ⇒ 不会淹死（这是云层里的星液，不是水）；</li>
 *   <li>{@code fallDistanceModifier(0)} ⇒ 掉进去不受摔落伤害；</li>
 *   <li>{@code lightLevel(10)} ⇒ 和熔岩一样发光（红石火把 7、萤石 15 之间）。</li>
 * </ul>
 * 客户端渲染：26.x 在 {@link StarlightFluidClient} 里用 {@code RegisterFluidModelsEvent} 注册
 * 贴图与 model；1.21.1 用 {@code RegisterClientExtensionsEvent} 注册 {@code IClientFluidTypeExtensions}。
 */
public final class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, FlightRingMod.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, FlightRingMod.MODID);

    public static final DeferredHolder<FluidType, FluidType> LIQUID_STARLIGHT_TYPE = FLUID_TYPES.register(
            "liquid_starlight",
            () -> new StarlightFluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type.simpleflightring.liquid_starlight")
                    // 密度必须为正：NeoForge 规定 density <= 0 的流体算"比空气轻"，
                    // 会把模型顶点翻转（见 FluidType#isLighterThanAir）。
                    .density(1200)
                    // 手感偏"稠"，但粘度只影响扩散速度，不影响玩家移动
                    .viscosity(2000)
                    .canSwim(true)
                    .canDrown(false)
                    // 关键：不让流体推人。默认 motionScale 是 Entity.WATER_FLOW_SCALE = 0.014，
                    // 之前填 0.6（43 倍）导致池子里被水流按着动不了、边缘像弹簧。
                    .canPushEntity(false)
                    .motionScale(0.014D)   // = 原版 Entity.WATER_FLOW_SCALE（1.21.1 里是 private，写字面量）
                    // 26.x 专属：让"眼睛在水里"这类判定也把我们当液体（水下挖掘速度、游泳姿势）。
                    // 真正的移动逻辑由 StarlightFluidType#move 提供（默认实现返回 false ⇒ 玩家会完全定住）。
                    .isWaterLike(true)
                    .supportsBoating(false)
                    .canConvertToSource(false)
                    // 掉进去不受摔落伤害
                    .fallDistanceModifier(0.0F)
                    .lightLevel(10)
                    .sound(SoundAction.get("swim"), SoundEvents.GENERIC_SWIM)
                    .sound(SoundAction.get("splash"), SoundEvents.GENERIC_SPLASH)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_STARLIGHT =
            FLUIDS.register("liquid_starlight", () -> new BaseFlowingFluid.Source(properties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_LIQUID_STARLIGHT =
            FLUIDS.register("flowing_liquid_starlight", () -> new BaseFlowingFluid.Flowing(properties()));

    private ModFluids() {
    }

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(LIQUID_STARLIGHT_TYPE, LIQUID_STARLIGHT, FLOWING_LIQUID_STARLIGHT)
                .block(ModBlocks.LIQUID_STARLIGHT)
                .bucket(ModItems.LIQUID_STARLIGHT_BUCKET)
                // 扩散也取"水与熔岩之间"：水是 4/1/5，熔岩是 2/2/30
                .slopeFindDistance(3)
                .levelDecreasePerBlock(2)
                .tickRate(25)
                .explosionResistance(100.0F);
    }
}
