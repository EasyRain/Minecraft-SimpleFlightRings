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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 极光群系的夜空极光（1.21.1）。
 * <p>
 * 视觉公式<strong>逐字移植</strong>自 Complementary Unbound r5.9.3 + EuphoriaPatches 1.10.5
 * （{@code shaders/lib/atmospherics/auroraBorealis.glsl} 的 {@code GetAuroraBorealis()}，
 * Unbound 分支 = AURORA_STYLE 2，即光影包用户实际看到的样式）：
 * 沿视线方向对"极光平面"采样 25 层噪声，{@code 1-2|noise-0.5|} 的 16 次方只保留噪声值在
 * 0.5 附近的窄带 —— 丝缕感来自贴图本身（白噪声），而不是网格形状。
 * <p>
 * 因此几何只是个"方向画布"：单位球穹顶（-10°~90° 仰角），片元着色器拿世界朝向算一切。
 * <b>必须配白噪声贴图</b>（{@code textures/noise.png}，3 通道独立）—— 平滑 fBm 在
 * 16 次方下会全灭（离线仿真实测），而 {@code clamp}（CLAMP_TO_EDGE）会把公式必然超界的
 * 采样坐标全部捏到贴图边缘一列 texel 上，得到"一块一块正方形层叠"。
 * <p>
 * 在 {@code AFTER_SKY} 阶段补画（原版天空之后、地形之前），加法混合、无深度写入，
 * 星空之上、云层之下。极光是纯方向函数（{@code wpos.xz / wpos.y}）：像日月星辰一样
 * 钉在天空上，只随视角转动、不随平移移动（平移视差由 {@code CameraPos} 微量提供，
 * 与光影包一致）。
 * <p>
 * 触发条件：{@code the_cloud} 维度、所在群系是 {@code simpleflightring:aurora}。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class AuroraSky {

    /** 白噪声贴图（必须 REPEAT 包裹 + LINEAR 过滤，见 noise.png.mcmeta）。 */
    private static final ResourceLocation NOISE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "textures/noise.png");

    private static final ResourceLocation AURORA_BIOME =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "aurora");

    /** 自定义 core shader（片元里跑移植后的 GetAuroraBorealis）。 */
    private static net.minecraft.client.renderer.ShaderInstance SHADER;

    /** 日志限流用（每 10 秒最多打一行）。 */
    private static int lastLoggedTick = -1000;

    private AuroraSky() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(
                            event.getResourceProvider(), "simpleflightring:aurora",
                            DefaultVertexFormat.POSITION_COLOR),
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
        Camera camera = event.getCamera();
        if (camera == null || SHADER == null) {
            return;
        }
        Vec3 cameraPos = camera.getPosition();
        boolean auroraBiome = inAuroraBiome(minecraft, cameraPos);
        // 昼夜淡入淡出（替代光影的 sunVisibility；夜晚 SunFade≈0，白天≈1）
        float sunFade = sunVisibility(minecraft);
        int ticks = minecraft.player == null ? 0 : minecraft.player.tickCount;
        if (ticks - lastLoggedTick > 200) {
            lastLoggedTick = ticks;
            FlightRingMod.LOGGER.info("[Aurora] stage fired: dim={} auroraBiome={} sunFade={}",
                    minecraft.level.dimension().location(), auroraBiome, sunFade);
        }
        if (!auroraBiome || sunFade >= 0.999F) {
            return;
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        float time = (minecraft.level.getGameTime() + partialTick) / 20.0F; // 世界年龄秒（= 光影 syncedTime）
        float rainFactor = minecraft.level.getRainLevel(partialTick);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE); // 纯加法
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> SHADER);
        RenderSystem.setShaderTexture(0, NOISE_TEXTURE);

        var timeUniform = SHADER.safeGetUniform("Time");
        if (timeUniform != null) {
            timeUniform.set(time);
        }
        var cameraUniform = SHADER.safeGetUniform("CameraPos");
        if (cameraUniform != null) {
            cameraUniform.set((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        }
        var rainUniform = SHADER.safeGetUniform("RainFactor");
        if (rainUniform != null) {
            rainUniform.set(rainFactor);
        }
        var sunUniform = SHADER.safeGetUniform("SunFade");
        if (sunUniform != null) {
            sunUniform.set(sunFade);
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // ★ AFTER_SKY 的 PoseStack 带相机旋转（2026-11-21 实证）：乘一次逆旋转，
        // 让穹顶顶点坐标处于"世界朝向"坐标系 —— 片元拿到的 worldDir 才是真正的世界方向。
        poseStack.mulPose(new org.joml.Quaternionf(camera.rotation()).conjugate());
        drawDome(poseStack);
        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean inAuroraBiome(Minecraft minecraft, Vec3 cameraPos) {
        var pos = net.minecraft.core.BlockPos.containing(cameraPos);
        var holder = minecraft.level.getBiome(pos);
        return holder.unwrapKey().map(key -> key.location().equals(AURORA_BIOME)).orElse(false);
    }

    /**
     * 光影包 sunVisibility 的等价物：0=深夜、1=白天，日出日落平滑过渡。
     * 深夜 [13500,22500]=0；白天 [0,12000]=1；黄昏 [12000,13500] 与黎明 [22500,24000] smoothstep。
     * 白天 &gt;= 0.999 时直接不画（省一次 draw call）。
     */
    private static float sunVisibility(Minecraft minecraft) {
        long dayTime = minecraft.level.getDayTime() % 24000L;
        if (dayTime < 0) {
            dayTime += 24000L;
        }
        if (dayTime >= 13500L && dayTime <= 22500L) {
            return 0.0F;                       // 深夜
        }
        if (dayTime <= 12000L) {
            return 1.0F;                       // 白天（含 24000 环绕无缝）
        }
        if (dayTime < 13500L) {
            return 1.0F - smoothstep((dayTime - 12000L) / 1500.0F);   // 黄昏 1→0
        }
        return smoothstep((dayTime - 22500L) / 1500.0F);               // 黎明 0→1
    }

    private static float smoothstep(float t) {
        float c = Math.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    /**
     * 单位球穹顶（仰角 -10°~90°），顶点坐标即世界方向，片元着色器负责全部观感。
     * 仰角按 t² 分布：地平线附近（极光最亮、方向变化最快）密，天顶附近疏。
     */
    private static void drawDome(PoseStack poseStack) {
        final int sectors = 48;
        final int rings = 24;
        final double elevMin = Math.toRadians(-10.0);
        final double elevRange = Math.toRadians(100.0);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var pose = poseStack.last().pose();

        for (int r = 0; r < rings; r++) {
            double t0 = (double) r / rings;
            double t1 = (double) (r + 1) / rings;
            double elev0 = elevMin + elevRange * t0 * t0;
            double elev1 = elevMin + elevRange * t1 * t1;
            double cosE0 = Math.cos(elev0);
            double sinE0 = Math.sin(elev0);
            double cosE1 = Math.cos(elev1);
            double sinE1 = Math.sin(elev1);
            for (int s = 0; s < sectors; s++) {
                double az0 = Math.PI * 2.0 * s / sectors;
                double az1 = Math.PI * 2.0 * (s + 1) / sectors;
                float x00 = (float) (Math.cos(az0) * cosE0);
                float z00 = (float) (Math.sin(az0) * cosE0);
                float x10 = (float) (Math.cos(az1) * cosE0);
                float z10 = (float) (Math.sin(az1) * cosE0);
                float x11 = (float) (Math.cos(az1) * cosE1);
                float z11 = (float) (Math.sin(az1) * cosE1);
                float x01 = (float) (Math.cos(az0) * cosE1);
                float z01 = (float) (Math.sin(az0) * cosE1);
                buffer.addVertex(pose, x00, (float) sinE0, z00).setColor(255, 255, 255, 255);
                buffer.addVertex(pose, x10, (float) sinE0, z10).setColor(255, 255, 255, 255);
                buffer.addVertex(pose, x11, (float) sinE1, z11).setColor(255, 255, 255, 255);
                buffer.addVertex(pose, x01, (float) sinE1, z01).setColor(255, 255, 255, 255);
            }
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
