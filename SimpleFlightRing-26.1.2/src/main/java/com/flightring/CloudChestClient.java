package com.flightring;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 厚云箱子的客户端部分：渲染器。
 * <p>
 * 箱子图集（{@code minecraft:textures/atlas/chests.png}）在 26.x 是按**目录**收集的
 * （{@code entity/chest/} 会扫描所有命名空间），所以把贴图放进
 * {@code assets/simpleflightring/textures/entity/chest/} 就自动进图集了，不用注册图集。
 * 渲染上只需覆写 {@code getCustomSprite} 返回我们的 sprite。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudChestClient {

    // 惰性创建：类初始化阶段就碰 Sheets 会触发 "Sheets loaded too early"（1.21.1 日志里踩过）
    private static SpriteId single;
    private static SpriteId left;
    private static SpriteId right;

    private static SpriteId spriteFor(ChestType type) {
        switch (type) {
            case LEFT:
                if (left == null) {
                    left = sprite("cloud_left");
                }
                return left;
            case RIGHT:
                if (right == null) {
                    right = sprite("cloud_right");
                }
                return right;
            default:
                if (single == null) {
                    single = sprite("cloud");
                }
                return single;
        }
    }

    private CloudChestClient() {
    }

    private static SpriteId sprite(String name) {
        return new SpriteId(Sheets.CHEST_SHEET,
                Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "entity/chest/" + name));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.THICK_CLOUD_CHEST.get(), CloudChestRenderer::new);
    }

    /** 复用原版箱子模型与开合动画，只把 sprite 换成厚云版（单箱 / 左半 / 右半）。 */
    public static final class CloudChestRenderer extends ChestRenderer<ChestBlockEntity> {

        public CloudChestRenderer(BlockEntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected SpriteId getCustomSprite(ChestBlockEntity blockEntity, ChestRenderState renderState) {
            return spriteFor(renderState.type);
        }
    }
}
