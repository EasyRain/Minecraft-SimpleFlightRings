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
     * 这一个值有<b>两个消费者</b>，必须区分开：
     * <ul>
     *   <li><b>游戏内渲染</b>：tint 是贴图的<b>乘数</b>。我们的贴图已经是最终色（深黑底 + 白星点），
     *       所以这里必须给不透明白，否则星点会被染色（实测拿洋红做对照时，池子里的星全变洋红了）。</li>
     *   <li><b>地图 mod（Xaero）</b>：`BlockTintProvider` 直接拿 tint 的颜色当像素色，
     *       见 {@code tintSource().colorInWorld(state, getter, pos)}。没 tint 时它会退化成
     *       "当水处理"、套生物群系水色（就是原版水蓝）。</li>
     * </ul>
     * 区分方式：Xaero 传进来的 {@code getter} 是它自己的 {@code BlockTintProvider}
     * （包名以 "xaero" 开头），而游戏内渲染传的是区块/世界 getter。于是——
     * 点到地图那边就给星液的蓝紫色，世界内一律给白色。这样两边都好看。
     */
    private static final int WORLD_TINT = 0xFFFFFFFF;

    /**
     * 地图上的颜色。
     * <p>
     * 星液本体是"黑色占大头 + 稀疏白星"，所以地图上给一个**很深的灰蓝**（不是水蓝、也不是亮紫）：
     * 在一片浅灰白的云块中间，深色池子反而最像它本身。
     */
    private static final int MAP_TINT = 0xFFFF0000;   // 诊断：纯红


    /** 诊断用：第一次见到某个"重载 + getter 类型"组合时打一行日志，方便定位地图取色走的是哪条。 */
    private static final java.util.Set<String> LOGGED_QUERIES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void note(String overload, BlockAndTintGetter getter, int color) {
        String key = overload + " <- " + (getter == null ? "null" : getter.getClass().getName());
        if (LOGGED_QUERIES.add(key)) {
            FlightRingMod.LOGGER.info("[FlightRing] liquid_starlight tint {} => #{}", key,
                    String.format("%08X", color));
        }
    }

    private static final FluidTintSource STARLIGHT_TINT = new FluidTintSource() {
        // 无坐标 / 带 BlockState 的这几个重载：地图 mod 取色用（Xaero 的
        // BlockTintProvider 就是在 getBiomeColor 里问 tint 的颜色）
        @Override
        public int color(FluidState state) {
            note("color(FluidState)", null, WORLD_TINT);
            return WORLD_TINT;
        }

        @Override
        public int color(BlockState state) {
            note("color(BlockState)", null, MAP_TINT);
            return MAP_TINT;
        }

        @Override
        public int colorInWorld(BlockState blockState, BlockAndTintGetter getter, BlockPos pos) {
            note("colorInWorld(BlockState)", getter, MAP_TINT);
            return MAP_TINT;
        }

        // 带 FluidState 的那个：流体渲染走这条，必须不染色，否则星点会被乘掉
        @Override
        public int colorInWorld(FluidState state, BlockState blockState,
                                BlockAndTintGetter getter, BlockPos pos) {
            note("colorInWorld(FluidState)", getter, WORLD_TINT);
            return WORLD_TINT;
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
