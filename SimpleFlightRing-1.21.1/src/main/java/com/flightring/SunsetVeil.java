package com.flightring;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 晚霞群系的"白天看不见太阳"（1.21.1）。
 * <p>
 * 用户 2026-11-22：晚霞群系白天要像下雨那样看不到太阳。
 * 做法：在 {@code AFTER_SKY} 之后画一层**灰白色的阴天穹顶**（普通 alpha 混合、关深度测试），
 * 把原版太阳和星空盖住；夜里（暖色时段之外）自动淡出，不影响夜景。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SunsetVeil {

    private static final ResourceLocation SUNSET_BIOME =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "sunset_clouds");

    private static net.minecraft.client.renderer.ShaderInstance shader;

    private SunsetVeil() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(
                            event.getResourceProvider(), "simpleflightring:veil",
                            DefaultVertexFormat.POSITION_COLOR),
                    s -> shader = s);
        } catch (java.io.IOException e) {
            FlightRingMod.LOGGER.error("[SunsetVeil] failed to load shader", e);
        }
    }

    /** 上一次生效的"白天程度"档位（-1 = 未知），用来判断是否需要重烘焙区块染色。 */
    private static int lastTintBand = -1;

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        // 群系方块颜色是烘焙进区块网格的，所以只在"档位变化"时刷新一次，避免每帧重建
        int band = AuroraSky.sunVisibilityPublic(minecraft) >= 0.5F ? 1 : 0;
        if (band == lastTintBand) {
            return;
        }
        boolean first = lastTintBand < 0;
        lastTintBand = band;
        if (first) {
            return;
        }
        if (!minecraft.level.dimension().equals(BrokenPortalTravel.CLOUD_DIMENSION)) {
            return;
        }
        if (!inSunsetBiome(minecraft, minecraft.player.position())) {
            return;
        }
        // 只重建一次：把"晚霞白天暖色 / 夜里冷白"刷进区块
        minecraft.levelRenderer.allChanged();
        FlightRingMod.LOGGER.info("[SunsetVeil] tint band -> {} (chunks re-meshed)", band);
    }

    @SubscribeEvent
    public static void onAfterSky(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || shader == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().equals(BrokenPortalTravel.CLOUD_DIMENSION)) {
            return;
        }
        Camera camera = event.getCamera();
        if (camera == null) {
            return;
        }
        Vec3 cameraPos = camera.getPosition();
        if (!inSunsetBiome(minecraft, cameraPos)) {
            return;
        }
        float day = AuroraSky.sunVisibilityPublic(minecraft);   // 0=深夜 1=白天
        // ★ 白天遮阳、夜里透明（之前写成 1-day，结果夜里盖了一整颗白球）
        float alpha = 0.92F * day;
        if (alpha <= 0.01F) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> shader);
        var colorUniform = shader.safeGetUniform("ColorModulator");
        if (colorUniform != null) {
            colorUniform.set(0.70F, 0.73F, 0.78F, alpha);   // 阴天的灰蓝色，别用纯白
        }

        var poseStack = event.getPoseStack();
        var view = event.getModelViewMatrix();
        var stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.identity();
        stack.mul(view);
        RenderSystem.applyModelViewMatrix();
        try {
            drawDome();
        } finally {
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean inSunsetBiome(Minecraft minecraft, Vec3 cameraPos) {
        BlockPos pos = BlockPos.containing(cameraPos);
        if (!minecraft.level.hasChunkAt(pos)) {
            return false;
        }
        return minecraft.level.getBiome(pos).unwrapKey()
                .map(k -> k.location().equals(SUNSET_BIOME)).orElse(false);
    }

    /** 球面穹顶：与极光同尺度（半径 100）、同仰角范围，保证盖住太阳与星星。 */
    private static void drawDome() {
        final int sectors = 48;
        final int rings = 20;
        final double elevMin = Math.toRadians(-10.0);
        final double elevRange = Math.toRadians(100.0);
        final float radius = 100.0F;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int r = 0; r < rings; r++) {
            double t0 = (double) r / rings;
            double t1 = (double) (r + 1) / rings;
            double elev0 = elevMin + elevRange * t0 * t0;
            double elev1 = elevMin + elevRange * t1 * t1;
            for (int s = 0; s < sectors; s++) {
                double az0 = Math.PI * 2.0 * s / sectors;
                double az1 = Math.PI * 2.0 * (s + 1) / sectors;
                float y0 = (float) Math.sin(elev0) * radius;
                float y1 = (float) Math.sin(elev1) * radius;
                float c0 = (float) Math.cos(elev0) * radius;
                float c1 = (float) Math.cos(elev1) * radius;
                buffer.addVertex((float) Math.cos(az0) * c0, y0, (float) Math.sin(az0) * c0).setColor(255, 255, 255, 255);
                buffer.addVertex((float) Math.cos(az1) * c0, y0, (float) Math.sin(az1) * c0).setColor(255, 255, 255, 255);
                buffer.addVertex((float) Math.cos(az1) * c1, y1, (float) Math.sin(az1) * c1).setColor(255, 255, 255, 255);
                buffer.addVertex((float) Math.cos(az0) * c1, y1, (float) Math.sin(az0) * c1).setColor(255, 255, 255, 255);
            }
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
