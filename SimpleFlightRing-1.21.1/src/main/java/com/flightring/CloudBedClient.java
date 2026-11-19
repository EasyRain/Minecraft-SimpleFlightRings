package com.flightring;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BedRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * 厚云床的客户端部分。
 * <p>
 * 床的渲染没法只换材质：{@code BedRenderer} 里 {@code headRoot}/{@code footRoot} 是 <b>private</b> 字段、
 * {@code renderPiece} 也是 private 方法，只有 {@code render} 是 public —— 所以这里自己 bake 一遍模型层
 * （{@code bakeLayer} 有缓存，不会重复烘焙），再把 {@code render} 的逻辑照搬过来、换成我们的材质。
 * <p>
 * 物品的 3D 显示沿用箱子那套：{@code builtin/entity} + {@link BlockEntityWithoutLevelRenderer}。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class CloudBedClient {

    private CloudBedClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.THICK_CLOUD_BED.get(), CloudBedRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private CloudBedItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new CloudBedItemRenderer();
                }
                return this.renderer;
            }
        }, ModItems.THICK_CLOUD_BED_ITEM.get());
    }

    /** 方块渲染：模型/腿部/开合都沿用原版，只有材质换成厚云床贴图。 */
    public static final class CloudBedRenderer extends BedRenderer {

        private final ModelPart cloudHead;
        private final ModelPart cloudFoot;

        public CloudBedRenderer(BlockEntityRendererProvider.Context context) {
            super(context);
            this.cloudHead = context.bakeLayer(ModelLayers.BED_HEAD);
            this.cloudFoot = context.bakeLayer(ModelLayers.BED_FOOT);
        }

        @Override
        public void render(BedBlockEntity bed, float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
                           int packedLight, int packedOverlay) {
            Material material = material();
            Level level = bed.getLevel();
            if (level != null) {
                BlockState state = bed.getBlockState();
                int light = net.minecraft.world.level.block.DoubleBlockCombiner
                        .combineWithNeigbour(ModBlockEntities.THICK_CLOUD_BED.get(), BedBlock::getBlockType,
                                BedBlock::getConnectedDirection, ChestBlock.FACING, state, level,
                                bed.getBlockPos(), (l, p) -> false)
                        .apply(new net.minecraft.client.renderer.blockentity.BrightnessCombiner<>())
                        .get(packedLight);
                renderPiece(poseStack, buffer,
                        state.getValue(BedBlock.PART) == BedPart.HEAD ? this.cloudHead : this.cloudFoot,
                        state.getValue(BedBlock.FACING), material, light, packedOverlay, false);
            } else {
                renderPiece(poseStack, buffer, this.cloudHead, Direction.SOUTH, material, packedLight, packedOverlay, false);
                renderPiece(poseStack, buffer, this.cloudFoot, Direction.SOUTH, material, packedLight, packedOverlay, true);
            }
        }

        private void renderPiece(PoseStack poseStack, MultiBufferSource buffer, ModelPart model, Direction facing,
                                 Material material, int light, int overlay, boolean isFoot) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.5625F, isFoot ? -1.0F : 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F + facing.toYRot()));
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            VertexConsumer consumer = material.buffer(buffer, RenderType::entitySolid);
            model.render(poseStack, consumer, light, overlay);
            poseStack.popPose();
        }
    }

    private static Material material() {
        return new Material(Sheets.BED_SHEET,
                ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "entity/bed/cloud"));
    }

    /** 物品：把物品画成一个真实的厚云床方块实体。 */
    public static final class CloudBedItemRenderer extends BlockEntityWithoutLevelRenderer {

        private final ThickCloudBedBlockEntity bed =
                new ThickCloudBedBlockEntity(BlockPos.ZERO, ModBlocks.THICK_CLOUD_BED.get().defaultBlockState());

        public CloudBedItemRenderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                 MultiBufferSource buffer, int packedLight, int packedOverlay) {
            Minecraft.getInstance().getBlockEntityRenderDispatcher()
                    .renderItem(this.bed, poseStack, buffer, packedLight, packedOverlay);
        }
    }
}
