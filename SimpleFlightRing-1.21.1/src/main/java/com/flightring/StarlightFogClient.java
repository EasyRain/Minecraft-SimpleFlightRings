package com.flightring;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 液态星光的水下雾（用户 2026-11-28：把水下的雾加上）。
 * <p>
 * 为什么不用 {@code IClientFluidTypeExtensions#modifyFogColor}：那条路两版签名不一样
 * （26.x 往 {@code Vector4f} 里写、1.21.1 返回 {@code Vector3f}）。NeoForge 的
 * {@link ViewportEvent.ComputeFogColor} / {@link ViewportEvent.RenderFog} 两版
 * 方法名一致（{@code setRed/Green/Blue}、{@code setNearPlaneDistance/FarPlaneDistance}），
 * 一套代码就能用，而且只在眼睛真的浸在液体里时生效。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class StarlightFogClient {

    /** 雾色：偏紫的深蓝，和蓝白星点相配。 */
    private static final float FOG_RED = 0.05F;
    private static final float FOG_GREEN = 0.06F;
    private static final float FOG_BLUE = 0.13F;

    /** 雾的距离：很浓（原版水约 96 格可见），20 格外基本看不见，符合"稠液"的手感。 */
    private static final float FOG_NEAR = 0.4F;
    private static final float FOG_FAR = 20.0F;

    private StarlightFogClient() {
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!isEyeInStarlight(event.getCamera())) {
            return;
        }
        event.setRed(FOG_RED);
        event.setGreen(FOG_GREEN);
        event.setBlue(FOG_BLUE);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!isEyeInStarlight(event.getCamera())) {
            return;
        }
        event.setNearPlaneDistance(FOG_NEAR);
        event.setFarPlaneDistance(FOG_FAR);
    }

    /** 眼睛是否浸在液态星光里（液面以上不算）。 */
    private static boolean isEyeInStarlight(Camera camera) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        Vec3 eye = camera.getPosition();
        BlockPos pos = BlockPos.containing(eye);
        FluidState state = level.getFluidState(pos);
        if (state.isEmpty() || !state.getType().isSame(ModFluids.LIQUID_STARLIGHT.get())) {
            return false;
        }
        return eye.y < pos.getY() + state.getHeight(level, pos);
    }
}
