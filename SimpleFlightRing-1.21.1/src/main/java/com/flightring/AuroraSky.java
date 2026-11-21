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

    /** 画两层错开的帘幕，越靠天顶越淡。 */
    private static void drawRibbon(PoseStack poseStack, Vec3 cameraPos, float time, float darken,
                                   org.joml.Quaternionf cameraRotation) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);

        poseStack.pushPose();
        // ★ AFTER_SKY 阶段的 PoseStack 仍带着相机的朝向（所以之前光带像一顶跟着视角转的帽子）。
        // 先用逆相机旋转把坐标系摆回"世界朝向"，再抬高到头顶，光带才是固定在天上的。
        poseStack.mulPose(new org.joml.Quaternionf(cameraRotation).conjugate());
        poseStack.translate(0.0F, HEIGHT, 0.0F);

        float alpha = 0.55F * darken;
        // 时间单位是 tick：这里换算成"每秒只移动百分之几纹理"的极慢漂移，
        // 之前直接 time*0.35 相当于每 tick 移动 0.35，看起来像射激光（用户 2026-11-21 反馈）。
        float seconds = time / 20.0F;
        drawLayer(poseStack, seconds * 0.06F, 0.0F, RADIUS, alpha);
        drawLayer(poseStack, -seconds * 0.035F, 55.0F, RADIUS * 0.78F, alpha * 0.55F);

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawLayer(PoseStack poseStack, float offset, float yShift, float radius, float alpha) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // 头顶的水平光带：贴在 y = 0 平面上（调用方已经把它抬到 HEIGHT），uv 横向滚动
        float size = radius;
        int segments = 6;
        float step = (2 * size) / segments;
        float repeat = 2.0F;
        for (int i = 0; i < segments; i++) {
            float z0 = -size + step * i;
            float z1 = z0 + step;
            float v0 = (i / (float) segments) * repeat + offset;
            float v1 = ((i + 1) / (float) segments) * repeat + offset;
            buffer.addVertex(poseStack.last().pose(), -size, yShift, z0)
                    .setUv(0.0F, v0).setColor(1.0F, 1.0F, 1.0F, alpha);
            buffer.addVertex(poseStack.last().pose(), size, yShift, z0)
                    .setUv(repeat, v0).setColor(1.0F, 1.0F, 1.0F, alpha);
            buffer.addVertex(poseStack.last().pose(), size, yShift, z1)
                    .setUv(repeat, v1).setColor(1.0F, 1.0F, 1.0F, alpha);
            buffer.addVertex(poseStack.last().pose(), -size, yShift, z1)
                    .setUv(0.0F, v1).setColor(1.0F, 1.0F, 1.0F, alpha);
        }
        // 用显式 shader 直接上传（RenderType.translucent() 期望 BLOCK 顶点格式，与我们不符，会静默失败）
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** 两端下垂的弧度。 */
    private static float sag(float normalizedX) {
        return (float) (normalizedX * normalizedX);
    }
}
