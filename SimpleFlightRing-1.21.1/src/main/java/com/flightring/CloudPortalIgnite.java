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
 * 用飞行戒指点亮云门：对**厚云砖**框里的 2×3 空位右键，里面就填满 {@link CloudPortalBlock}。
 * <p>
 * 与 {@link BrokenPortalConversion} 的差别：这里不接管原版下界门、也不改写现成的门，
 * 只认"厚云砖围出来的 2 宽 × 3 高的空位"，所以规则简单、不会和现有一套互相干扰。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class CloudPortalIgnite {

    private CloudPortalIgnite() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown() || !isFlightRing(event.getItemStack())) {
            return;
        }
        BlockPos clicked = event.getPos();
        if (!event.getLevel().getBlockState(clicked).is(ModBlocks.THICK_CLOUD_BRICKS.get())) {
            return;
        }
        Direction.Axis axis = findFrameAxis(event.getLevel(), clicked);
        if (axis == null) {
            return;
        }
        // 两边都要吃掉这次点击：否则服务端的取消会漏到"使用物品"流程里（会触发 Curios 装备戒指）
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        fill(level, clicked, axis);
    }

    private static boolean isFlightRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem;
    }

    /**
     * 从被点的那块厚云砖出发，找一个以它为边框的 2×3 空位，返回该空位所在平面（X 或 Z）。
     * <p>
     * 砖可能在任意一条边上，所以这里枚举"空位左下角"的候选位置（点击处周围 3×3×3），
     * 再验证 2×3 的内部全是空气、并且被厚云砖完整围住。
     */
    private static Direction.Axis findFrameAxis(Level level, BlockPos clicked) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            for (int dy = -3; dy <= 1; dy++) {
                for (int da = -2; da <= 1; da++) {
                    BlockPos min = clicked.offset(axis == Direction.Axis.X ? da : 0, dy,
                            axis == Direction.Axis.X ? 0 : da);
                    if (isValidFrame(level, min, axis)) {
                        return axis;
                    }
                }
            }
        }
        return null;
    }

    /** 以 {@code min} 为左下角（宽 2、高 3）检验框：内部空气、四周厚云砖。 */
    private static boolean isValidFrame(Level level, BlockPos min, Direction.Axis axis) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                BlockPos inner = min.offset(axis == Direction.Axis.X ? i : 0, j, axis == Direction.Axis.X ? 0 : j);
                if (!level.getBlockState(inner).isAir()) {
                    return false;
                }
            }
        }
        // 上下边框
        for (int i = 0; i < 2; i++) {
            if (!isBrick(level, min.offset(axis == Direction.Axis.X ? i : 0, -1, axis == Direction.Axis.X ? 0 : i))
                    || !isBrick(level, min.offset(axis == Direction.Axis.X ? i : 0, 3, axis == Direction.Axis.X ? 0 : i))) {
                return false;
            }
        }
        // 左右边框
        for (int j = 0; j < 3; j++) {
            BlockPos left = min.offset(axis == Direction.Axis.X ? -1 : 0, j, axis == Direction.Axis.X ? 0 : -1);
            BlockPos right = min.offset(axis == Direction.Axis.X ? 2 : 0, j, axis == Direction.Axis.X ? 0 : 2);
            if (!isBrick(level, left) || !isBrick(level, right)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBrick(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.THICK_CLOUD_BRICKS.get());
    }

    private static void fill(ServerLevel level, BlockPos clicked, Direction.Axis axis) {
        BlockState portal = ModBlocks.CLOUD_PORTAL.get().defaultBlockState().setValue(CloudPortalBlock.AXIS, axis);
        for (Direction.Axis candidate : new Direction.Axis[]{axis}) {
            for (int dy = -3; dy <= 1; dy++) {
                for (int da = -2; da <= 1; da++) {
                    BlockPos min = clicked.offset(candidate == Direction.Axis.X ? da : 0, dy,
                            candidate == Direction.Axis.X ? 0 : da);
                    if (!isValidFrame(level, min, candidate)) {
                        continue;
                    }
                    for (int i = 0; i < 2; i++) {
                        for (int j = 0; j < 3; j++) {
                            BlockPos inner = min.offset(candidate == Direction.Axis.X ? i : 0, j,
                                    candidate == Direction.Axis.X ? 0 : j);
                            level.setBlockAndUpdate(inner, portal);
                        }
                    }
                    level.playSound(null, clicked, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return;
                }
            }
        }
    }
}
