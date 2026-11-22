package com.flightring;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 极光群系的夜空极光（26.1.2）。
 * <p>
 * 与 1.21.1 版本视觉公式完全相同（移植自 Complementary Unbound 的
 * {@code GetAuroraBorealis()}，AURORA_STYLE 2 = Unbound）：球面穹顶当"方向画布"，
 * 片元里按世界方向对极光平面采样 25 层白噪声。
 * <p>
 * <b>26.x 的结构性差异</b>（1.21.1 的经验在这里全部作废，必须按新管线写）：
 * <ul>
 *   <li><b>没有 RenderSystem.setShader / BufferUploader.drawWithShader</b>：要注册
 *       {@link RenderPipeline}（{@link RegisterRenderPipelinesEvent}），再用
 *       {@code RenderPass} + {@code GpuBuffer} 提交；</li>
 *   <li><b>没有自定义 uniform</b>（着色器里没有 Time/SunFade 这种独立 uniform）：
 *       全部参数走 {@code DynamicTransforms}：
 *       {@code ModelOffset.xy} = 视差+漂移、{@code ModelOffset.z} = 时间、
 *       {@code ColorModulator} = (群系权重, SunFade, 雨量, 1)；</li>
 *   <li><b>矩阵不用自己摆</b>：{@code RenderSystem.getModelViewMatrix()} 在原版渲染天空时
 *       已经是"世界朝向 -> 视线"的矩阵，直接写进 DynamicTransforms 即可 ——
 *       1.21.1 里 push/identity/mul(V)/apply 那套在这里不需要；</li>
 *   <li><b>顶点属性照样必须是原始世界方向</b>（否则图案锁死在屏幕上，1.21.1 已踩过）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class AuroraSky {

    private static final Identifier NOISE_TEXTURE =
            Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "textures/noise.png");
    private static final Identifier AURORA_BIOME =
            Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "aurora");

    /** 穹顶半径：与原版天空家族（天穹盘/太阳/月亮/星星都在 ~100）同尺度。 */
    private static final float DOME_RADIUS = 100.0F;

    /** 群系过渡半径（格）= 一个插值网格单元的边长（与 1.21.1 一致：12）。 */
    private static final double BIOME_BLEND_RADIUS = 12.0;

    /** 首帧诊断：只打一次，方便排查"看不到极光"。 */
    private static boolean loggedFirstDraw = false;

    private static RenderPipeline pipeline;
    private static GpuBuffer vertexBuffer;
    private static int vertexCount;

    private AuroraSky() {
    }

    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "pipeline/aurora"))
                .withVertexShader(Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "core/aurora"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "core/aurora"))
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(false)
                .build();
        event.registerPipeline(pipeline);
    }

    @SubscribeEvent
    public static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || pipeline == null) {
            return;
        }
        if (!minecraft.level.dimension().equals(BrokenPortalTravel.CLOUD_DIMENSION)) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null) {
            return;
        }
        Vec3 cameraPos = camera.position();
        float sunFade = sunVisibility(minecraft);
        float biomeWeight = auroraBiomeWeight(minecraft, cameraPos);
        if (biomeWeight <= 0.001F || sunFade >= 0.999F) {
            return;
        }

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = (minecraft.level.getGameTime() + partialTick) / 20.0F;
        float rainFactor = minecraft.level.getRainLevel(partialTick);

        // 视差 + 漂移烘进 ModelOffset（26.x 没有自定义 uniform）
        float driftU = (float) (cameraPos.x * 0.0075) + time * 0.04F;
        float driftV = (float) (cameraPos.z * 0.0075);

        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                view,
                new Vector4f(biomeWeight, sunFade, rainFactor, 1.0F),
                new Vector3f(driftU, driftV, time),
                new Matrix4f());

        if (!loggedFirstDraw) {
            loggedFirstDraw = true;
            FlightRingMod.LOGGER.info("[Aurora] first draw (26.x): biomeWeight={} sunFade={} time={} cam={}",
                    biomeWeight, sunFade, time, cameraPos);
        }

        MeshData mesh = buildDome();
        GpuBuffer old = vertexBuffer;
        vertexCount = mesh.drawState().vertexCount();
        vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Aurora dome", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
        mesh.close();
        if (old != null) {
            old.close();
        }

        var renderTarget = Minecraft.getInstance().getMainRenderTarget();
        var texture = Minecraft.getInstance().getTextureManager().getTexture(NOISE_TEXTURE);
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Aurora", renderTarget.getColorTextureView(),
                        OptionalInt.empty(), renderTarget.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertexCount);
        }
    }

    /** 球面穹顶：顶点 = 原始世界方向 × 100（仰角 -10°~90°，按 t² 分布）。 */
    private static MeshData buildDome() {
        final int sectors = 48;
        final int rings = 24;
        final double elevMin = Math.toRadians(-10.0);
        final double elevRange = Math.toRadians(100.0);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
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
                float x00 = (float) (Math.cos(az0) * cosE0) * DOME_RADIUS;
                float z00 = (float) (Math.sin(az0) * cosE0) * DOME_RADIUS;
                float x10 = (float) (Math.cos(az1) * cosE0) * DOME_RADIUS;
                float z10 = (float) (Math.sin(az1) * cosE0) * DOME_RADIUS;
                float x11 = (float) (Math.cos(az1) * cosE1) * DOME_RADIUS;
                float z11 = (float) (Math.sin(az1) * cosE1) * DOME_RADIUS;
                float x01 = (float) (Math.cos(az0) * cosE1) * DOME_RADIUS;
                float z01 = (float) (Math.sin(az0) * cosE1) * DOME_RADIUS;
                buffer.addVertex(x00, (float) sinE0 * DOME_RADIUS, z00).setColor(255, 255, 255, 255);
                buffer.addVertex(x10, (float) sinE0 * DOME_RADIUS, z10).setColor(255, 255, 255, 255);
                buffer.addVertex(x11, (float) sinE1 * DOME_RADIUS, z11).setColor(255, 255, 255, 255);
                buffer.addVertex(x01, (float) sinE1 * DOME_RADIUS, z01).setColor(255, 255, 255, 255);
            }
        }
        return buffer.buildOrThrow();
    }

    /** 群系权重：世界锚定网格 + 双线性插值（与 1.21.1 完全一致）。 */
    private static float auroraBiomeWeight(Minecraft minecraft, Vec3 cameraPos) {
        double step = BIOME_BLEND_RADIUS;
        double gx = cameraPos.x / step;
        double gz = cameraPos.z / step;
        long ix = (long) Math.floor(gx);
        long iz = (long) Math.floor(gz);
        double tx = gx - ix;
        double tz = gz - iz;

        boolean v00 = isAuroraAt(minecraft, ix * step, cameraPos.y, iz * step);
        boolean v10 = isAuroraAt(minecraft, (ix + 1) * step, cameraPos.y, iz * step);
        boolean v01 = isAuroraAt(minecraft, ix * step, cameraPos.y, (iz + 1) * step);
        boolean v11 = isAuroraAt(minecraft, (ix + 1) * step, cameraPos.y, (iz + 1) * step);

        double a = (v00 ? 1.0 : 0.0) * (1.0 - tx) + (v10 ? 1.0 : 0.0) * tx;
        double b = (v01 ? 1.0 : 0.0) * (1.0 - tx) + (v11 ? 1.0 : 0.0) * tx;
        float w = (float) (a * (1.0 - tz) + b * tz);
        return w * w * (3.0F - 2.0F * w);
    }

    private static boolean isAuroraAt(Minecraft minecraft, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!minecraft.level.hasChunkAt(pos)) {
            return false;
        }
        return minecraft.level.getBiome(pos).unwrapKey()
                .map(key -> key.identifier().equals(AURORA_BIOME)).orElse(false);
    }

    /** 与 1.21.1 一致：日落 12500→13500 淡入、日出 22500→23500 淡出（与太阳同步）。 */
    private static float sunVisibility(Minecraft minecraft) {
        long dayTime = minecraft.level.getOverworldClockTime() % 24000L;
        if (dayTime < 0) {
            dayTime += 24000L;
        }
        if (dayTime >= 13500L && dayTime <= 22500L) {
            return 0.0F;
        }
        if (dayTime <= 12500L) {
            return 1.0F;
        }
        if (dayTime < 13500L) {
            return 1.0F - smoothstep((dayTime - 12500L) / 1000.0F);
        }
        return smoothstep((dayTime - 22500L) / 1000.0F);
    }

    private static float smoothstep(float t) {
        float c = Math.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }
}
