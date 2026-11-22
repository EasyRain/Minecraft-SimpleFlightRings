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

    /** 优先用玩家的重生点（床/重生锚），没有就用世界出生点。 */
    private static BlockPos respawnPos(ServerPlayer player, ServerLevel overworld) {
        BlockPos bed = player.getRespawnPosition();
        if (bed != null) {
            return new BlockPos(bed.getX(), bed.getY(), bed.getZ());
        }
        return overworld.getSharedSpawnPos();
    }
}
