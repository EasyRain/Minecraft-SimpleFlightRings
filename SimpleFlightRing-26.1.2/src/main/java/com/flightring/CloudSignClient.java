package com.flightring;

import net.minecraft.client.renderer.blockentity.StandingSignRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 云木告示牌的渲染：直接复用原版的 {@link StandingSignRenderer}（26.x 把立式告示牌的渲染拆成了这个类），
 * 只是把它挂到我们自己的方块实体类型上（原版类型的注册里只认原版告示牌）。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudSignClient {

    private CloudSignClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CLOUD_SIGN.get(), StandingSignRenderer::new);
    }
}
