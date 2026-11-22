package com.flightring.mixin;

import com.flightring.BrokenPortalBlock;
import com.flightring.CloudPortalOverlay;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 云门的屏幕滤镜换成我们自己的贴图（1.21.1）。
 * <p>
 * 原版 {@code Gui#renderPortalOverlay} 的贴图是<b>写死</b>的 {@code Blocks.NETHER_PORTAL}，
 * 所以换方块模型、取消 {@code RenderBlockScreenEffectEvent} 都没用（用户 2026-11-22 反复实测）。
 * 做法参考 CustomPortals：在 HEAD 处接管，用我们自己传送门的贴图重画，然后 cancel 原版。
 * <p>
 * 只对"玩家正站在我们的传送门里"生效 —— 真·下界门的紫色滤镜保持原样。
 */
@Mixin(Gui.class)
public abstract class CloudPortalOverlayMixin {

    private static final ResourceLocation CLOUD_OVERLAY =
            ResourceLocation.fromNamespaceAndPath("simpleflightring", "block/cloud_portal_particle");

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void simpleflightring$cloudPortalOverlay(GuiGraphics graphics, float alpha, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !CloudPortalOverlay.shouldDrawCloudOverlay(minecraft.player)) {
            return;   // 站在别处（比如真下界门）：交给原版
        }
        if (alpha < 1.0F) {
            alpha *= alpha;
            alpha *= alpha;
            alpha = alpha * 0.8F + 0.2F;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(CLOUD_OVERLAY);
        graphics.blit(0, 0, -90, graphics.guiWidth(), graphics.guiHeight(), sprite);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        ci.cancel();
    }
}
