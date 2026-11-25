package com.flightring;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 雷云群系：玩家站在地表时，有小概率被落雷直接劈中（用户 2026-11-25）。
 * <p>
 * 规则：
 * <ul>
 *   <li>只在 {@code thunderhead} 群系触发；</li>
 *   <li>要求玩家<b>站在地表</b>（脚踩着地，且所在列的地表高度就在脚下 2 格内）；</li>
 *   <li>每 tick {@value #INV_CHANCE_TEXT} 分之一的概率 ⇒ 平均约 6 分钟一次；</li>
 *   <li>同一位玩家至少间隔 {@value #MIN_INTERVAL_SECONDS} 秒，避免连续劈；</li>
 *   <li>旁观者不触发（创造模式会触发，但没伤害，只是画面/音效）。</li>
 * </ul>
 * 落雷用原版 {@link LightningBolt}，所以伤害、着火、音效都跟原版一致；
 * 云层里全是不可燃方块，不会真的烧起来。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class ThunderheadStrikes {

    /** 雷云群系的 id。 */
    private static final ResourceKey<Biome> THUNDERHEAD = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "thunderhead"));

    /** 每 tick 触发概率：1/7200 ≈ 站在地表平均 6 分钟一次。 */
    private static final double CHANCE_PER_TICK = 1.0D / 7200.0D;

    static final String INV_CHANCE_TEXT = "7200";

    /** 同一位玩家的最小间隔（秒）。 */
    static final int MIN_INTERVAL_SECONDS = 45;
    private static final long MIN_INTERVAL_TICKS = 20L * MIN_INTERVAL_SECONDS;

    /** 脚下离地表高度图最多几格才算"站在地表"（容许站在矮墙/台阶上）。 */
    private static final double SURFACE_TOLERANCE = 2.0D;

    /** 玩家 -> 上次被劈的游戏刻。 */
    private static final Map<UUID, Long> LAST_STRIKE = new ConcurrentHashMap<>();

    private ThunderheadStrikes() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isSpectator() || !player.onGround()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.getBiome(player.blockPosition()).is(THUNDERHEAD)) {
            return;
        }
        if (!isAtSurface(level, player)) {
            return;
        }

        long now = level.getGameTime();
        Long last = LAST_STRIKE.get(player.getUUID());
        if (last != null && now - last < MIN_INTERVAL_TICKS) {
            return;
        }
        if (level.getRandom().nextDouble() >= CHANCE_PER_TICK) {
            return;
        }

        LAST_STRIKE.put(player.getUUID(), now);
        strike(level, player);
    }

    /** 所在列的地表高度就在脚下附近（= 站在地表，而不是洞穴/岛体内部）。 */
    private static boolean isAtSurface(ServerLevel level, ServerPlayer player) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                player.getBlockX(), player.getBlockZ());
        double feetY = player.getY();
        return feetY >= surfaceY - 1.0D && feetY <= surfaceY + SURFACE_TOLERANCE;
    }

    private static void strike(ServerLevel level, ServerPlayer player) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(player.getX(), player.getY(), player.getZ());
        bolt.setVisualOnly(false);
        level.addFreshEntity(bolt);
    }
    /**
     * 检查落雷条件（给 {@code /sfr ambience check} 用）。
     * 返回每一条的布尔值和脚下列的地表高度、剩余冷却，方便"为什么没劈"一眼看出来。
     */
    public static Report check(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long last = LAST_STRIKE.get(player.getUUID());
        long cooldown = last == null ? 0L : Math.max(0L, MIN_INTERVAL_TICKS - (now - last));
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                player.getBlockX(), player.getBlockZ());
        return new Report(
                level.getBiome(player.blockPosition()).is(THUNDERHEAD),
                player.onGround(),
                isAtSurface(level, player),
                surfaceY,
                cooldown,
                player.isSpectator());
    }

    /** 无视概率与冷却，立刻落一次雷（测试用；条件不满足也照劈）。 */
    public static void strikeNow(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LAST_STRIKE.put(player.getUUID(), level.getGameTime());
        strike(level, player);
    }

    /** {@link #check(ServerPlayer)} 的结果。 */
    public record Report(boolean inBiome, boolean onGround, boolean atSurface,
                         int surfaceY, long cooldownTicks, boolean spectator) {

        public boolean ready() {
            return inBiome && onGround && atSurface && cooldownTicks <= 0L && !spectator;
        }
    }
}
