package com.flightring;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BedRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BedRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 厚云床的客户端部分。
 * <p>
 * 26.x 的床渲染器把 {@code modelTransform} 与 {@code submitPiece} 都开成了 public，
 * 所以这里只要覆写 8 行的 {@code submit}、把 sprite 换成我们的即可（1.21.1 那边得整段复制）。
 * 床图集同样是"目录"型（{@code entity/bed/}），贴图放进去自动入图集。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudBedClient {

    private static SpriteId cloudSprite;

    /** 惰性创建，避免在类初始化阶段加载 Sheets（见 CloudChestClient 的注释）。 */
    private static SpriteId cloudSprite() {
        if (cloudSprite == null) {
            cloudSprite = new SpriteId(Sheets.BED_SHEET,
                    Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "entity/bed/cloud"));
        }
        return cloudSprite;
    }

    private CloudBedClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.THICK_CLOUD_BED.get(), CloudBedRenderer::new);
    }

    public static final class CloudBedRenderer extends BedRenderer {

        public CloudBedRenderer(BlockEntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void submit(BedRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                           CameraRenderState camera) {
            poseStack.pushPose();
            poseStack.mulPose(modelTransform(state.facing));
            this.submitPiece(state.part, cloudSprite(), poseStack, collector, state.lightCoords,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, state.breakProgress, 0);
            poseStack.popPose();
        }
    }
}
