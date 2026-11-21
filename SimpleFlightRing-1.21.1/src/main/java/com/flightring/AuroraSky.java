package com.flightring;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 极光群系的夜空极光（1.21.1）。
 * <p>
 * 参考 {@code Auroras} 模组的思路（它在 {@code LevelRenderer#renderSky} 注入），
 * 这里改用 NeoForge 的 {@link RenderLevelStageEvent} 在 {@code AFTER_SKY} 阶段补画 ——
 * 不需要 mixin，也不会打乱原版天空。
 * <p>
 * 触发条件：在 {@code the_cloud} 维度、所在群系是 {@code simpleflightring:aurora}、并且天已经暗下来。
 * 画法：把一张"极光帘幕"贴图当成两个大面片（一前一后、慢速反向滚动），加法混合、关深度写入，
 * 这样它在星空之上、地形之下，看起来像横贯天穹的一条光带。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class AuroraSky {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "textures/environment/aurora.png");
    private static final ResourceLocation AURORA_BIOME =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "aurora");

    /** 光带离玩家的高度（格）与半径（格）。 */
    private static final float HEIGHT = 120.0F;
    private static final float RADIUS = 260.0F;

    /** 日志限流用（每 10 秒最多打一行）。 */
    private static int lastLoggedTick = -1000;

    private AuroraSky() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (!minecraft.level.dimension().equals(BrokenPortalTravel.CLOUD_DIMENSION)) {
            return;
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        // 夜晚才出现：直接看 dayTime（0 = 日出，6000 = 正午，13000~23000 是夜）
        long dayTime = minecraft.level.getDayTime() % 24000L;
        if (dayTime < 0) {
            dayTime += 24000L;
        }
        boolean night = dayTime >= 12500L && dayTime <= 23500L;
        Camera camera = event.getCamera();
        if (camera == null) {
            return;
        }
        Vec3 cameraPos = camera.getPosition();
        boolean auroraBiome = inAuroraBiome(minecraft, cameraPos);
        int ticks = minecraft.player == null ? 0 : minecraft.player.tickCount;
        if (ticks - lastLoggedTick > 200) {
            lastLoggedTick = ticks;
            FlightRingMod.LOGGER.info("[Aurora] stage fired: dim={} auroraBiome={} dayTime={} night={}",
                    minecraft.level.dimension().location(), auroraBiome, dayTime, night);
        }
        if (!night || !auroraBiome) {
            return;
        }
        org.joml.Quaternionf cameraRotation = new org.joml.Quaternionf(camera.rotation());
        drawRibbon(event.getPoseStack(), cameraPos, ticks + partialTick, 1.0F, cameraRotation);
    }

    private static boolean inAuroraBiome(Minecraft minecraft, Vec3 cameraPos) {
        var pos = net.minecraft.core.BlockPos.containing(cameraPos);
        var holder = minecraft.level.getBiome(pos);
        return holder.unwrapKey().map(key -> key.location().equals(AURORA_BIOME)).orElse(false);
    }

    /**
     * 画极光帘幕。
     * <p>
     * 参考 {@code Auroras} 模组的做法：**不用贴图**，而是用一圈竖直的三角形带
     * （每段的角度决定强度、竖直方向决定颜色与透明度），顶点色 + 加法混合 ⇒
     * 边缘是渐隐的，不会像贴片那样有硬边。
     */
    private static void drawRibbon(PoseStack poseStack, Vec3 cameraPos, float time, float darken,
                                   org.joml.Quaternionf cameraRotation) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();
        poseStack.mulPose(new org.joml.Quaternionf(cameraRotation).conjugate());
        poseStack.translate(0.0F, HEIGHT, 0.0F);

        float seconds = time / 20.0F;
        // 两层帘幕：远的一层更淡、相位不同
        drawCurtain(poseStack, seconds, RADIUS, 0.0F, 1.0F);
        drawCurtain(poseStack, seconds * 0.7F + 13.0F, RADIUS * 0.78F, 18.0F, 0.55F);

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** 一圈竖直帘幕：角度决定强度（wave），高度决定颜色与透明度。 */
    private static void drawCurtain(PoseStack poseStack, float time, float radius, float yShift, float strength) {
        final int steps = 128;
        final int rows = 8;
        final float bottomY = -30.0F + yShift;
        final float topY = 90.0F + yShift;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2.0 * i / steps;
            double a1 = Math.PI * 2.0 * (i + 1) / steps;
            float w0 = wave(time, a0);
            float w1 = wave(time, a1);
            if (w0 + w1 < 0.02F) {
                continue;
            }
            float x0 = (float) Math.cos(a0) * radius;
            float z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;

            for (int r = 0; r < rows; r++) {
                float t0 = r / (float) rows;
                float t1 = (r + 1) / (float) rows;
                // 竖直剖面：中间偏下最亮，上下两端渐隐
                float p0 = verticalProfile(t0);
                float p1 = verticalProfile(t1);
                float a0a = w0 * p0 * strength * 0.42F;
                float a1a = w1 * p0 * strength * 0.42F;
                float a2a = w1 * p1 * strength * 0.42F;
                float a3a = w0 * p1 * strength * 0.42F;
                if (a0a + a1a + a2a + a3a < 0.004F) {
                    continue;
                }
                float y0 = bottomY + (topY - bottomY) * t0;
                float y1 = bottomY + (topY - bottomY) * t1;
                // 颜色：下缘偏青白、上缘偏绿
                float g0 = 1.0F - 0.25F * t0;
                float b0 = 0.95F - 0.45F * t0;
                float g1 = 1.0F - 0.25F * t1;
                float b1 = 0.95F - 0.45F * t1;

                var pose = poseStack.last().pose();
                buffer.addVertex(pose, x0, y0, z0).setColor(0.55F, g0, b0, a0a);
                buffer.addVertex(pose, x1, y0, z1).setColor(0.55F, g0, b0, a1a);
                buffer.addVertex(pose, x1, y1, z1).setColor(0.35F, g1, b1, a2a);
                buffer.addVertex(pose, x0, y1, z0).setColor(0.35F, g1, b1, a3a);
            }
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** 某个角度上的帘幕强度：几层慢速正弦叠加，再乘一个"带状"窗函数，两端自然为 0。 */
    private static float wave(float time, double angle) {
        double a = angle;
        double w = Math.sin(a * 3.0 + time * 0.05) * 0.45
                + Math.sin(a * 7.0 - time * 0.03 + 1.7) * 0.30
                + Math.sin(a * 2.0 + time * 0.017 + 4.1) * 0.25;
        float v = (float) Math.max(0.0, (w + 0.55) / 1.15);
        return v * v;
    }

    /** 竖直亮度剖面：中间偏下最亮，上下渐隐到 0。 */
    private static float verticalProfile(float t) {
        if (t <= 0.0F || t >= 1.0F) {
            return 0.0F;
        }
        float peak = 0.62F;
        float d = (t - peak) / (t < peak ? peak : 1.0F - peak);
        return Math.max(0.0F, 1.0F - d * d);
    }

    /** 两端下垂的弧度。 */
    private static float sag(float normalizedX) {
        return (float) (normalizedX * normalizedX);
    }
}
