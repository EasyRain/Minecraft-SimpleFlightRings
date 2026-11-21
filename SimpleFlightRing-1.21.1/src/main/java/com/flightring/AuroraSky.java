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
     * 画极光。
     * <p>
     * 关键经验（用户 2026-11-21 两轮反馈）：
     * <ul>
     *   <li>整圈画在头顶 ⇒ 从下面看就是一个"镜片光斑"，不像极光；</li>
     *   <li>所以要画**一段远处的巨大竖幕**（像真实极光那样挂在一侧天空），
     *       高度远大于宽度、下缘接近地平线、上缘很高。</li>
     * </ul>
     * 参考 {@code Auroras} 的做法：不用贴图，顶点色 + 加法混合，强度和颜色都由方程算。
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
        // 北边一段主幕 + 稍偏东一段更淡的副幕（副幕更远、更淡，制造层次）
        drawCurtain(poseStack, seconds, -Math.PI / 2.0, 2.6, 340.0F, 1.0F);
        drawCurtain(poseStack, seconds * 0.75F + 21.0F, -Math.PI / 2.0 + 0.55, 2.1, 430.0F, 0.55F);

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 一段弧形竖幕：{@code center} 是弧的中心方位角，{@code width} 是跨度（弧度）。
     * 竖直方向从地平线下方一直拉到很高，形成"挂在天边"的帘子。
     */
    private static void drawCurtain(PoseStack poseStack, float time, double center, double width,
                                    float radius, float strength) {
        final int steps = 96;
        final int rows = 10;
        final float bottomY = -90.0F;
        final float topY = 260.0F;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < steps; i++) {
            double a0 = center - width / 2.0 + width * i / steps;
            double a1 = center - width / 2.0 + width * (i + 1) / steps;
            float w0 = wave(time, a0);
            float w1 = wave(time, a1);
            if (w0 + w1 < 0.02F) {
                continue;
            }
            // 弧的两端自然收薄
            float edge0 = (float) Math.sin(Math.PI * (i + 0.5) / steps);
            float edge1 = (float) Math.sin(Math.PI * (i + 1.5) / steps);
            w0 *= edge0;
            w1 *= edge1;

            float x0 = (float) Math.cos(a0) * radius;
            float z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;

            for (int r = 0; r < rows; r++) {
                float t0 = r / (float) rows;
                float t1 = (r + 1) / (float) rows;
                float p0 = verticalProfile(t0);
                float p1 = verticalProfile(t1);
                float a00 = w0 * p0 * strength * 0.30F;
                float a10 = w1 * p0 * strength * 0.30F;
                float a11 = w1 * p1 * strength * 0.30F;
                float a01 = w0 * p1 * strength * 0.30F;
                if (a00 + a10 + a11 + a01 < 0.004F) {
                    continue;
                }
                float y0 = bottomY + (topY - bottomY) * t0;
                float y1 = bottomY + (topY - bottomY) * t1;
                // 下缘偏青白、上缘偏绿（真实极光下缘更亮更白）
                float r0 = 0.55F - 0.25F * t0;
                float g0 = 1.0F - 0.15F * t0;
                float b0 = 0.95F - 0.45F * t0;
                float r1 = 0.55F - 0.25F * t1;
                float g1 = 1.0F - 0.15F * t1;
                float b1 = 0.95F - 0.45F * t1;

                var pose = poseStack.last().pose();
                buffer.addVertex(pose, x0, y0, z0).setColor(r0, g0, b0, a00);
                buffer.addVertex(pose, x1, y0, z1).setColor(r0, g0, b0, a10);
                buffer.addVertex(pose, x1, y1, z1).setColor(r1, g1, b1, a11);
                buffer.addVertex(pose, x0, y1, z0).setColor(r1, g1, b1, a01);
            }
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** 某个角度上的帘幕强度：几层慢速正弦叠加，再乘一个"带状"窗函数，两端自然为 0。 */
    private static float wave(float time, double angle) {
        double a = angle;
        double w = Math.sin(a * 3.0 + time * 0.05) * 0.30
                + Math.sin(a * 11.0 - time * 0.03 + 1.7) * 0.35
                + Math.sin(a * 23.0 + time * 0.02 + 4.1) * 0.20
                + Math.sin(a * 2.0 + time * 0.017) * 0.15;
        float v = (float) Math.max(0.0, (w + 0.55) / 1.15);
        return v * v;
    }

    /**
     * 竖直亮度剖面：**下缘最亮，向上迅速变淡**，顶部完全消失（真实极光的样子）。
     */
    private static float verticalProfile(float t) {
        if (t <= 0.0F || t >= 1.0F) {
            return 0.0F;
        }
        float fadeUp = (float) Math.pow(1.0F - t, 2.2);
        float fadeBottom = Math.min(1.0F, t / 0.10F);
        return fadeUp * fadeBottom;
    }

    /** 两端下垂的弧度。 */
    private static float sag(float normalizedX) {
        return (float) (normalizedX * normalizedX);
    }
}
