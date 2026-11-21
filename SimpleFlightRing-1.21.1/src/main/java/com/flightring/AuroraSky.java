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
    /** 自定义 core shader：片元着色器里直接算噪声，避免顶点色面片的块状感。 */
    private static net.minecraft.client.renderer.ShaderInstance SHADER;

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
    public static void onRegisterShaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event) {
        try {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(
                    event.getResourceProvider(), "simpleflightring:aurora",
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR),
                    shader -> SHADER = shader);
        } catch (java.io.IOException e) {
            FlightRingMod.LOGGER.error("[Aurora] failed to load shader", e);
        }
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
     * 参考 {@code Auroras} 的预览图：它是一条**横贯天空的彩色光带** —— 中间一条亮芯，
     * 上下柔和地淡出，而不是竖直帘幕或头顶光斑。所以这里：
     * <ul>
     *   <li>画一段**很宽**的圆弧（≈240°）当作"带子"；</li>
     *   <li>竖直方向只占 90~220 格，亮度用**高斯**剖面，中间最亮、上下渐隐；</li>
     *   <li>沿弧的强度用几层低频正弦做明暗起伏，边缘平滑、不断开；</li>
     *   <li>颜色：亮芯偏绿、两侧偏青蓝。</li>
     * </ul>
     */
    private static void drawRibbon(PoseStack poseStack, Vec3 cameraPos, float time, float darken,
                                   org.joml.Quaternionf cameraRotation) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        if (SHADER == null) {
            return;   // 着色器没加载成功：直接跳过，不要让 drawWithShader 空指针崩端
        }
        RenderSystem.setShader(() -> SHADER);

        if (SHADER != null) {
            var timeUniform = SHADER.safeGetUniform("Time");
            if (timeUniform != null) {
                timeUniform.set(time / 20.0F);
            }
        }

        poseStack.pushPose();
        poseStack.mulPose(new org.joml.Quaternionf(cameraRotation).conjugate());
        poseStack.translate(0.0F, HEIGHT, 0.0F);

        float seconds = time / 20.0F;
        // 一圈"幕布"，但压在靠近地平线的高度，并让上下边缘起伏 —— 既不是头顶光环，
        // 也没有断口（用户 2026-11-21：整圈、地平线略高、带波浪）。
        drawBand(poseStack, seconds, 300.0F, 0.0F, 1.0F, 1.0F);
        drawBand(poseStack, seconds * 0.75F + 23.0F, 400.0F, 26.0F, 0.45F, 1.6F);

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** 一圈光带（横贯天空的弧），竖直方向用高斯剖面。 */
    private static void drawBand(PoseStack poseStack, float time, float radius, float yShift,
                                 float strength, float waviness) {
        // 段数/行数要够密，否则每格之间的透明度差会看成一格一格的方块（用户 2026-11-21）
        final int steps = 96;
        final int rows = 8;
        // 垂到相机高度以下：半径 300 时，-140~110 格约对应 -25°~+20° 仰角
        final float bandBottom = -140.0F + yShift;
        final float bandTop = 110.0F + yShift;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < steps; i++) {
            // 整圈
            double a0 = Math.PI * 2.0 * i / steps;
            double a1 = Math.PI * 2.0 * (i + 1) / steps;
            float w0 = arcIntensity(time, a0);
            float w1 = arcIntensity(time, a1);
            if (w0 + w1 < 0.03F) {
                continue;
            }
            float x0 = (float) Math.cos(a0) * radius;
            float z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;

            for (int r = 0; r < rows; r++) {
                float t0 = r / (float) rows;
                float t1 = (r + 1) / (float) rows;
                float p0 = gaussianProfile(t0);
                float p1 = gaussianProfile(t1);
                float a00 = strength;
                float a10 = strength;
                float a11 = strength;
                float a01 = strength;
                if (a00 + a10 + a11 + a01 < 0.004F) {
                    continue;
                }
                float waveTop0 = edgeWave(time, a0, waviness);
                float waveTop1 = edgeWave(time, a1, waviness);
                float y0 = bandBottom + (bandTop - bandBottom + waveTop0) * t0;
                float y1 = bandBottom + (bandTop - bandBottom + waveTop1) * t1;
                // 亮芯偏绿、上下两侧偏青蓝
                float core0 = coreWeight(t0);
                float core1 = coreWeight(t1);
                float r0 = 0.30F - 0.12F * core0;
                float g0 = 0.65F + 0.35F * core0;
                float b0 = 0.85F - 0.35F * core0;
                float r1 = 0.30F - 0.12F * core1;
                float g1 = 0.65F + 0.35F * core1;
                float b1 = 0.85F - 0.35F * core1;

                var pose = poseStack.last().pose();
                buffer.addVertex(pose, x0, y0, z0).setColor(r0, g0, b0, a00);
                buffer.addVertex(pose, x1, y0, z1).setColor(r0, g0, b0, a10);
                buffer.addVertex(pose, x1, y1, z1).setColor(r1, g1, b1, a11);
                buffer.addVertex(pose, x0, y1, z0).setColor(r1, g1, b1, a01);
            }
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** 光带沿弧的强度：几层低频正弦，保持连续（不断开），只做明暗起伏。 */
    private static float arcIntensity(float time, double angle) {
        double a = angle;
        double w = Math.sin(a * 2.0 + time * 0.03) * 0.35
                + Math.sin(a * 5.0 - time * 0.02 + 1.3) * 0.25
                + Math.sin(a * 13.0 + time * 0.05 + 2.7) * 0.12;
        return (float) (0.55 + 0.45 * w);
    }

    /**
     * 边缘起伏：让幕布的上缘（以及整体高度）缓慢上下波动，
     * 这样它看起来是一条起伏的云带，而不是一个规矩的圆环。
     */
    private static float edgeWave(float time, double angle, float amount) {
        double a = angle;
        double w = Math.sin(a * 3.0 + time * 0.08) * 0.5
                + Math.sin(a * 7.0 - time * 0.05 + 1.9) * 0.3
                + Math.sin(a * 11.0 + time * 0.03 + 3.3) * 0.2;
        return (float) (w * 55.0 * amount);
    }

    /** 竖直方向的高斯剖面：中间最亮，上下柔和淡出。 */
    private static float gaussianProfile(float t) {
        float d = (t - 0.5F) / 0.34F;
        return (float) Math.exp(-d * d);
    }

    /** 越靠近亮芯越绿，两侧偏青。 */
    private static float coreWeight(float t) {
        float d = (t - 0.5F) / 0.26F;
        return (float) Math.exp(-d * d);
    }

    /** 两端下垂的弧度。 */
    private static float sag(float normalizedX) {
        return (float) (normalizedX * normalizedX);
    }
}
