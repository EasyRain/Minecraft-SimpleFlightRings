package com.flightring;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
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
 * 晚霞群系的"白天看不见太阳"（26.1.2）。
 * <p>
 * 在 {@code AFTER_SKY} 之后画一层灰白阴天穹顶（普通 alpha 混合、关深度测试），
 * 把太阳/星空盖住；夜里自动淡出。结构与 {@link AuroraSky} 相同，只是
 * 管线用 {@code BlendFunction.TRANSLUCENT}、着色器输出纯色。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class SunsetVeil {

    private static final Identifier SUNSET_BIOME =
            Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "sunset_clouds");

    private static final float DOME_RADIUS = 100.0F;

    private static RenderPipeline pipeline;
    private static GpuBuffer vertexBuffer;
    private static int vertexCount;
    private static boolean loggedFirstDraw = false;

    private SunsetVeil() {
    }

    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "pipeline/sky_veil"))
                .withVertexShader(Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "core/veil"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "core/veil"))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
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
        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().position();
        if (!inSunsetBiome(minecraft, cameraPos)) {
            return;
        }
        float day = FlightRingMod.auroraDayFactor(minecraft);
        // ★ 白天遮阳、夜里透明（之前写成 1-day，结果夜里盖了一整颗白球）
        float alpha = 0.92F * day;
        if (alpha <= 0.01F) {
            return;
        }

        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                view, new Vector4f(0.70F, 0.73F, 0.78F, alpha), new Vector3f(), new Matrix4f());

        MeshData mesh = buildDome();
        GpuBuffer old = vertexBuffer;
        vertexCount = mesh.drawState().vertexCount();
        vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Sunset veil", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
        mesh.close();
        if (old != null) {
            old.close();
        }
        if (!loggedFirstDraw) {
            loggedFirstDraw = true;
            FlightRingMod.LOGGER.info("[SunsetVeil] first draw (26.x): day={} alpha={}", day, alpha);
        }

        var renderTarget = minecraft.getMainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sunset veil", renderTarget.getColorTextureView(),
                        OptionalInt.empty(), renderTarget.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertexCount);
        }
    }

    private static boolean inSunsetBiome(Minecraft minecraft, Vec3 cameraPos) {
        BlockPos pos = BlockPos.containing(cameraPos);
        if (!minecraft.level.hasChunkAt(pos)) {
            return false;
        }
        return minecraft.level.getBiome(pos).unwrapKey()
                .map(k -> k.identifier().equals(SUNSET_BIOME)).orElse(false);
    }

    private static MeshData buildDome() {
        final int sectors = 48;
        final int rings = 20;
        final double elevMin = Math.toRadians(-10.0);
        final double elevRange = Math.toRadians(100.0);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int r = 0; r < rings; r++) {
            double t0 = (double) r / rings;
            double t1 = (double) (r + 1) / rings;
            double elev0 = elevMin + elevRange * t0 * t0;
            double elev1 = elevMin + elevRange * t1 * t1;
            for (int s = 0; s < sectors; s++) {
                double az0 = Math.PI * 2.0 * s / sectors;
                double az1 = Math.PI * 2.0 * (s + 1) / sectors;
                float y0 = (float) Math.sin(elev0) * DOME_RADIUS;
                float y1 = (float) Math.sin(elev1) * DOME_RADIUS;
                float c0 = (float) Math.cos(elev0) * DOME_RADIUS;
                float c1 = (float) Math.cos(elev1) * DOME_RADIUS;
                float x00 = (float) Math.cos(az0) * c0;
                float z00 = (float) Math.sin(az0) * c0;
                float x10 = (float) Math.cos(az1) * c0;
                float z10 = (float) Math.sin(az1) * c0;
                float x11 = (float) Math.cos(az1) * c1;
                float z11 = (float) Math.sin(az1) * c1;
                float x01 = (float) Math.cos(az0) * c1;
                float z01 = (float) Math.sin(az0) * c1;
                buffer.addVertex(x00, y0, z00).setColor(255, 255, 255, 255);
                buffer.addVertex(x10, y0, z10).setColor(255, 255, 255, 255);
                buffer.addVertex(x11, y1, z11).setColor(255, 255, 255, 255);

                buffer.addVertex(x00, y0, z00).setColor(255, 255, 255, 255);
                buffer.addVertex(x11, y1, z11).setColor(255, 255, 255, 255);
                buffer.addVertex(x01, y1, z01).setColor(255, 255, 255, 255);
            }
        }
        return buffer.buildOrThrow();
    }
}
