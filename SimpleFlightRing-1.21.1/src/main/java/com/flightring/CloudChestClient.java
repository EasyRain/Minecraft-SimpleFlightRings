package com.flightring;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 厚云箱子的客户端部分：贴图入图集 + 渲染器。
 * <p>
 * 箱子的贴图不在方块图集里，而在 **箱子图集**（{@code minecraft:textures/atlas/chests.png}）。
 * 这个图集在 1.21.1 是按**目录**收集的（{@code entity/chest/} 会扫描所有命名空间），
 * 所以贴图放进 {@code assets/simpleflightring/textures/entity/chest/} 就自动进图集，不用注册。
 * 渲染上只需把 {@code getMaterial} 换成我们的材质，模型与开合动画全部复用原版。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudChestClient {

    private static final ResourceLocation SINGLE_TEXTURE = id("entity/chest/cloud");
    private static final ResourceLocation LEFT_TEXTURE = id("entity/chest/cloud_left");
    private static final ResourceLocation RIGHT_TEXTURE = id("entity/chest/cloud_right");

    private static final Material SINGLE = new Material(Sheets.CHEST_SHEET, SINGLE_TEXTURE);
    private static final Material LEFT = new Material(Sheets.CHEST_SHEET, LEFT_TEXTURE);
    private static final Material RIGHT = new Material(Sheets.CHEST_SHEET, RIGHT_TEXTURE);

    private CloudChestClient() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, path);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.THICK_CLOUD_CHEST.get(), CloudChestRenderer::new);
    }

    /**
     * 3D 手持/展示模型。
     * <p>
     * 原版箱子物品的模型是 {@code builtin/entity}：真正画它的是 {@link BlockEntityWithoutLevelRenderer}，
     * 它拿一个真实的 {@code ChestBlockEntity} 交给方块实体渲染器画。我们照做 —— 建一个自己的箱子实体
     * （无世界也能画，{@code ChestRenderer} 会走"没有 level"的分支），挂上我们的渲染器就得到 3D 箱子物品。
     */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private CloudChestItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new CloudChestItemRenderer();
                }
                return this.renderer;
            }
        }, ModItems.THICK_CLOUD_CHEST_ITEM.get());
    }

    /** 物品专属渲染器：把物品画成一个真实的箱子方块实体。 */
    public static final class CloudChestItemRenderer extends BlockEntityWithoutLevelRenderer {

        private final ThickCloudChestBlockEntity chest =
                new ThickCloudChestBlockEntity(BlockPos.ZERO, ModBlocks.THICK_CLOUD_CHEST.get().defaultBlockState());

        public CloudChestItemRenderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                 MultiBufferSource buffer, int packedLight, int packedOverlay) {
            Minecraft.getInstance().getBlockEntityRenderDispatcher()
                    .renderItem(this.chest, poseStack, buffer, packedLight, packedOverlay);
        }
    }

    /** 复用原版箱子模型，只换材质（单箱 / 双箱左右两半各一张）。 */
    public static final class CloudChestRenderer extends ChestRenderer<ChestBlockEntity> {

        public CloudChestRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected Material getMaterial(ChestBlockEntity blockEntity, ChestType chestType) {
            return switch (chestType) {
                case LEFT -> LEFT;
                case RIGHT -> RIGHT;
                default -> SINGLE;
            };
        }
    }
}
