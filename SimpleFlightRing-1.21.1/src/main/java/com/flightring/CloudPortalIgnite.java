package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 用飞行戒指点亮云门。
 * <p>
 * 量框、判断"这一面是不是朝门洞里"全部复用 {@link BrokenPortalConversion#isPortalAction} 那套
 * （它背后是 {@link BrokenPortalFrames}，原版下界门的规则）：厚云砖通过
 * {@link ThickCloudBrickBlock#isPortalFrame} 被认成框架，于是我们只负责"把门洞填成云门"。
 * <p>
 * 戒指**拿在手上**或**带在身上**都算（用户 2026-11-21：戴着戒指点门框也要有反应）。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class CloudPortalIgnite {

    private CloudPortalIgnite() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown()) {
            return;
        }
        if (!holdsOrCarriesRing(event.getEntity())) {
            return;
        }
        BlockPos clicked = event.getPos();
        if (!event.getLevel().getBlockState(clicked).is(ModBlocks.THICK_CLOUD_BRICKS.get())) {
            return;
        }
        if (!BrokenPortalConversion.isPortalAction(event.getLevel(), clicked, event.getFace())) {
            return;
        }
        // 两边都吃掉这次点击：否则客户端的"使用物品"会漏下去，触发 Curios 装备戒指
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        fill(level, clicked, event.getFace());
    }

    /**
     * 任意飞行戒指都能点亮云门（用户 2026-11-20：不要限定成末影戒指）。
     * 拿在手上、副手、或带在身上（Curios 槽 / 背包）都算。
     */
    private static boolean holdsOrCarriesRing(net.minecraft.world.entity.player.Player player) {
        if (player.getMainHandItem().getItem() instanceof FlightRingItem
                || player.getOffhandItem().getItem() instanceof FlightRingItem) {
            return true;
        }
        return FlightHandler.hasUsableRing(player);
    }

    /** 把门洞填成云门方块（复用现成的量框结果）。 */
    private static void fill(ServerLevel level, BlockPos clicked, Direction face) {
        BlockPos inside = clicked.relative(face);
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            BrokenPortalFrames.Rect rect = BrokenPortalFrames.measure(level, inside, axis);
            if (rect == null || !BrokenPortalFrames.frameIntact(level, rect)) {
                continue;
            }
            BlockState portal = ModBlocks.CLOUD_PORTAL.get().defaultBlockState()
                    .setValue(CloudPortalBlock.AXIS, rect.axis());
            BrokenPortalFrames.beginBulkEdit();
            try {
                for (int i = 0; i < rect.width(); i++) {
                    for (int j = 0; j < rect.height(); j++) {
                        BlockPos inner = rect.min().above(j).relative(rect.axis(), i);
                        if (level.getBlockState(inner).isAir()) {
                            level.setBlockAndUpdate(inner, portal);
                        }
                    }
                }
            } finally {
                BrokenPortalFrames.endBulkEdit();
            }
            level.playSound(null, clicked, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1.0F, 1.0F);
            return;
        }
    }
}
