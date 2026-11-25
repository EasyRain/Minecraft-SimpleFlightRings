package com.flightring;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 雷云群系：玩家在<b>露天空域</b>里有小概率被落雷直接劈中（用户 2026-11-25）。
 * <p>
 * 规则：
 * <ul>
 *   <li>只在 {@code thunderhead} 群系触发；</li>
 *   <li>要求玩家在<b>所在列的地表之上</b>（{@code y >= 地表高度 - 1}）——
 *       站在地表、飞在岛上方、飘在两座岛之间的空中都算；<b>不要求站立</b>，
 *       因为这个模组就是主打飞行的，要求落地等于让规则永远不生效（用户 2026-11-25 指出）；</li>
 *   <li>钻在岛体内部 / 洞穴里时，所在列的地表在头顶，自然被排除；</li>
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
            Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "thunderhead"));

    /** 每 tick 触发概率：1/7200 ≈ 平均 6 分钟一次。 */
    private static final double CHANCE_PER_TICK = 1.0D / 7200.0D;

    static final String INV_CHANCE_TEXT = "7200";

    /** 同一位玩家的最小间隔（秒）。 */
    static final int MIN_INTERVAL_SECONDS = 45;
    private static final long MIN_INTERVAL_TICKS = 20L * MIN_INTERVAL_SECONDS;

    /**
     * 允许比地表低多少格仍算"在地表之上"（脚下这块方块顶面就在脚底时，
     * 高度图给的是 y+1，所以要留 1 格余量）。
     */
    private static final double SURFACE_SLACK = 1.0D;

    /** 玩家 -> 上次被劈的游戏刻。 */
    private static final Map<UUID, Long> LAST_STRIKE = new ConcurrentHashMap<>();

    private ThunderheadStrikes() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        ServerLevel level = player.level();
        if (!level.getBiome(player.blockPosition()).is(THUNDERHEAD)) {
            return;
        }
        if (!isAboveSurface(level, player)) {
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

    /**
     * 玩家是否在所在列的地表之上（站着 / 飞着 / 漂浮都算）。
     * <p>
     * 用高度图而不是 {@code canSeeSky}：高度图两种版本 API 一致，
     * 而且"岛体内部/洞穴"这一情形天然满足 {@code y < 地表}，不需要额外判天空。
     */
    private static boolean isAboveSurface(ServerLevel level, ServerPlayer player) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                player.getBlockX(), player.getBlockZ());
        return player.getY() >= surfaceY - SURFACE_SLACK;
    }

    /**
     * 检查落雷条件（给 {@code /sfr ambience check} 用）。
     * 返回每一条的布尔值和脚下列的地表高度、剩余冷却，方便"为什么没劈"一眼看出来。
     */
    public static Report check(ServerPlayer player) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long last = LAST_STRIKE.get(player.getUUID());
        long cooldown = last == null ? 0L : Math.max(0L, MIN_INTERVAL_TICKS - (now - last));
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                player.getBlockX(), player.getBlockZ());
        return new Report(
                level.getBiome(player.blockPosition()).is(THUNDERHEAD),
                isAboveSurface(level, player),
                surfaceY,
                cooldown,
                player.isSpectator());
    }

    /** 无视概率与冷却，立刻落一次雷（测试用；条件不满足也照劈）。 */
    public static void strikeNow(ServerPlayer player) {
        ServerLevel level = player.level();
        LAST_STRIKE.put(player.getUUID(), level.getGameTime());
        strike(level, player);
    }

    /** {@link #check(ServerPlayer)} 的结果。 */
    public record Report(boolean inBiome, boolean aboveSurface,
                         int surfaceY, long cooldownTicks, boolean spectator) {

        public boolean ready() {
            return inBiome && aboveSurface && cooldownTicks <= 0L && !spectator;
        }
    }

    private static void strike(ServerLevel level, ServerPlayer player) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) {
            return;
        }
        bolt.setPos(player.getX(), player.getY(), player.getZ());
        bolt.setVisualOnly(false);
        level.addFreshEntity(bolt);
    }
}
