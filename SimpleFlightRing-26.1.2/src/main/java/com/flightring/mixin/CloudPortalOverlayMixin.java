package com.flightring.mixin;

import com.flightring.CloudPortalOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 云门的屏幕滤镜换成我们自己的贴图（26.1.2）。
 * <p>
 * 原版 {@code Hud#extractPortalOverlay} 的贴图同样写死 {@code Blocks.NETHER_PORTAL}。
 * 做法参考 CustomPortals：HEAD 接管、用我们的贴图重画、再 cancel。
 */
@Mixin(Gui.class)
public abstract class CloudPortalOverlayMixin {

    private static final Identifier CLOUD_OVERLAY =
            Identifier.fromNamespaceAndPath("simpleflightring", "block/cloud_portal_particle");

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void simpleflightring$cloudPortalOverlay(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !CloudPortalOverlay.shouldDrawCloudOverlay(minecraft.player)) {
            return;
        }
        if (alpha < 1.0F) {
            alpha *= alpha;
            alpha *= alpha;
            alpha = alpha * 0.8F + 0.2F;
        }
        TextureAtlasSprite sprite = minecraft.getAtlasManager()
                .get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, CLOUD_OVERLAY));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, 0,
                graphics.guiWidth(), graphics.guiHeight(), ARGB.white(alpha));
        ci.cancel();
    }
}
