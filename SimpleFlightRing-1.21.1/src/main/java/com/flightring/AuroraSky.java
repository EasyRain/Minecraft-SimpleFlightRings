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
        drawRibbon(event.getPoseStack(), cameraPos, ticks + partialTick, 1.0F);
    }

    private static boolean inAuroraBiome(Minecraft minecraft, Vec3 cameraPos) {
        var pos = net.minecraft.core.BlockPos.containing(cameraPos);
        var holder = minecraft.level.getBiome(pos);
        return holder.unwrapKey().map(key -> key.location().equals(AURORA_BIOME)).orElse(false);
    }

    /** 画两层错开的帘幕，越靠天顶越淡。 */
    private static void drawRibbon(PoseStack poseStack, Vec3 cameraPos, float time, float darken) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE);

        poseStack.pushPose();
        // AFTER_SKY 阶段的 PoseStack 已经是相机相对坐标（原版天空就是在原点画的），
        // 这里只需要抬高到头顶，不能再减 cameraPos —— 之前那样会把光带推到地平线以下，看不见。
        poseStack.translate(0.0F, HEIGHT, 0.0F);

        float alpha = 0.55F * darken;
        drawLayer(poseStack, time * 0.35F, 0.0F, RADIUS, alpha);
        drawLayer(poseStack, -time * 0.22F, 40.0F, RADIUS * 0.82F, alpha * 0.65F);

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawLayer(PoseStack poseStack, float offset, float yShift, float radius, float alpha) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // 一条横贯东西的带子，上下各占 80 格；UV 横向滚动
        float top = 70.0F + yShift;
        float bottom = -10.0F + yShift;
        int segments = 8;
        for (int i = 0; i < segments; i++) {
            float x0 = -radius + (2 * radius) * i / segments;
            float x1 = -radius + (2 * radius) * (i + 1) / segments;
            float u0 = (i / (float) segments) * 3.0F + offset;
            float u1 = ((i + 1) / (float) segments) * 3.0F + offset;
            // 带一点弧度：两端压低
            float sag0 = sag(x0 / radius) * 60.0F;
            float sag1 = sag(x1 / radius) * 60.0F;
            buffer.addVertex(poseStack.last().pose(), x0, top + sag0, 0.0F)
                    .setUv(u0, 0.0F).setColor(1.0F, 1.0F, 1.0F, alpha);
            buffer.addVertex(poseStack.last().pose(), x1, top + sag1, 0.0F)
                    .setUv(u1, 0.0F).setColor(1.0F, 1.0F, 1.0F, alpha);
            buffer.addVertex(poseStack.last().pose(), x1, bottom + sag1, 0.0F)
                    .setUv(u1, 1.0F).setColor(1.0F, 1.0F, 1.0F, alpha * 0.15F);
            buffer.addVertex(poseStack.last().pose(), x0, bottom + sag0, 0.0F)
                    .setUv(u0, 1.0F).setColor(1.0F, 1.0F, 1.0F, alpha * 0.15F);
        }
        net.minecraft.client.renderer.RenderType.translucent().draw(buffer.buildOrThrow());
    }

    /** 两端下垂的弧度。 */
    private static float sag(float normalizedX) {
        return (float) (normalizedX * normalizedX);
    }
}
