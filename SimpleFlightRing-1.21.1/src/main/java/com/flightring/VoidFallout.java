package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 掉出云维度虚空 => 送回主世界重生点（用户 2026-11-22）。
 * <p>
 * 规则：
 * <ul>
 *   <li>玩家在 {@code the_cloud} 里掉到世界底部以下 ⇒ 触发；</li>
 *   <li><b>生存/冒险</b>：先受 <b>10 点伤害</b>（5 颗心）；血不够就正常死亡，按原版流程在重生点复活；</li>
 *   <li>没死 ⇒ 直接传送到<b>主世界重生点</b>（有床/锚就用它，否则世界出生点）；</li>
 *   <li><b>创造/旁观</b>：不受伤，直接传送。</li>
 * </ul>
 * 之所以先扣血再传送：让"血不够"的情况自然走原版死亡/复活，不用自己处理重生逻辑。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class VoidFallout {

    /** 掉到世界底部以下这么多格就触发（给玩家留一点缓冲，别贴边误伤）。 */
    private static final double VOID_MARGIN = 8.0;

    /** 摔出虚空受到的伤害（10 点 = 5 颗心）。 */
    private static final float FALL_DAMAGE = 10.0F;

    private VoidFallout() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(BrokenPortalTravel.CLOUD_DIMENSION)) {
            return;
        }
        if (player.getY() > level.getMinBuildHeight() - VOID_MARGIN) {
            return;
        }

        // 生存/冒险先扣血；扣死就走原版复活流程（在重生点复活）
        boolean vulnerable = !player.isCreative() && !player.isSpectator();
        if (vulnerable) {
            player.hurt(level.damageSources().generic(), FALL_DAMAGE);
            if (!player.isAlive()) {
                FlightRingMod.LOGGER.debug("[VoidFallout] {} died from void fall", player.getGameProfile().getName());
                return;
            }
        }

        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        BlockPos target = respawnPos(player, overworld);
        player.teleportTo(overworld, target.getX() + 0.5D, target.getY() + 0.1D, target.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        FlightRingMod.LOGGER.debug("[VoidFallout] {} returned to overworld at {}", player.getGameProfile().getName(), target);
    }

    /**
     * 优先用玩家的重生点（床/重生锚），没有就用世界出生点。
     * <p>
     * ★ 世界出生点的 Y 可能是"生成时的高度"（超平坦、或出生点被挖过），直接用它会把玩家
     * 丢在半空摔下来（用户 2026-11-22）。所以这里按 x/z **重新取地表高度**，
     * 和原版重生时的落点判定保持一致。
     */
    private static BlockPos respawnPos(ServerPlayer player, ServerLevel overworld) {
        BlockPos bed = player.getRespawnPosition();
        boolean inOverworld = player.getRespawnDimension().equals(Level.OVERWORLD);
        // 床/重生锚：必须在主世界、且方块还在（被拆掉 = 失效，退回世界出生点，和原版一致）
        if (bed != null && inOverworld && isValidSpawnBlock(overworld, bed)) {
            return new BlockPos(bed.getX(), bed.getY() + 1, bed.getZ());   // 床高 0.5625，+1 才不会卡进床里
        }
        return snapToSurface(overworld, overworld.getSharedSpawnPos());
    }

    /** 重生点方块是否还有效（床 / 重生锚）。 */
    private static boolean isValidSpawnBlock(ServerLevel level, BlockPos pos) {
        // ★ 不要用 hasChunkAt 拦：重生点常在未加载的区块里（玩家跑远了），
        //   getBlockState 会按需加载区块（原版复活时也是这么做的），
        //   用 hasChunkAt 判会让"设过床"永远失效（用户 2026-11-22）。
        var state = level.getBlockState(pos);
        return state.is(net.minecraft.tags.BlockTags.BEDS)
                || state.is(net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR);
    }

    /** 把坐标的 Y 吸附到该列的地表（找不到地面就保持原 Y）。 */
    private static BlockPos snapToSurface(ServerLevel level, BlockPos pos) {
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                pos.getX(), pos.getZ());
        int minY = level.getMinBuildHeight();
        int maxY = minY + level.getHeight() - 1;
        if (surface <= minY || surface > maxY) {
            return pos;   // 这一列没有地面（虚空/未生成）：别乱改 Y
        }
        return new BlockPos(pos.getX(), surface, pos.getZ());
    }
}
