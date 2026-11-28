package com.flightring;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 碰到液态星光时给的一套 buff（用户 2026-11-28 指定）。
 * <p>
 * 设计意图是一条<b>接力链</b>：泡在液体里时漂浮是不生效的（原版只在空气分支读 Levitation），
 * 但一浮出液面进入空气，3 秒的漂浮 III 立刻接上把人继续往天上带；等漂浮结束时，
 * 还有 6 秒缓降 II 兜着，不会摔。恢复和抗性则是"星液浸润"的即时回复。
 * <ul>
 *   <li>漂浮 III —— {@value #LEVITATION_TICKS} tick（3 秒）</li>
 *   <li>缓降 II —— {@value #SLOW_FALLING_TICKS} tick（6 秒）</li>
 *   <li>生命恢复 III —— {@value #REGENERATION_TICKS} tick（3 秒）</li>
 *   <li>抗性提升 II —— {@value #RESISTANCE_TICKS} tick（5 秒）</li>
 * </ul>
 * 只要身体还泡在液体里就每秒续一次，离开后自然倒数结束，所以手感是"离开液面才开始接力"。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class StarlightBuffs {

    /** 3 秒漂浮 III（amplifier 2）。 */
    private static final int LEVITATION_TICKS = 20 * 3;
    private static final int LEVITATION_AMPLIFIER = 2;

    /** 6 秒缓降 II（amplifier 1）。 */
    private static final int SLOW_FALLING_TICKS = 20 * 6;
    private static final int SLOW_FALLING_AMPLIFIER = 1;

    /** 3 秒生命恢复 III。 */
    private static final int REGENERATION_TICKS = 20 * 3;
    private static final int REGENERATION_AMPLIFIER = 2;

    /** 5 秒抗性提升 II。 */
    private static final int RESISTANCE_TICKS = 20 * 5;
    private static final int RESISTANCE_AMPLIFIER = 1;

    /** 续 buff 的间隔：剩余时间少于"总时长 - 1 秒"就补，避免每 tick 都发同步包。 */
    private static final int REFRESH_SLACK_TICKS = 20;

    private StarlightBuffs() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        // 身体有任何一部分浸在液态星光里就续 buff
        if (player.getFluidTypeHeight(ModFluids.LIQUID_STARLIGHT_TYPE.get()) <= 0.0D) {
            return;
        }

        refresh(player, MobEffects.LEVITATION, LEVITATION_TICKS, LEVITATION_AMPLIFIER);
        refresh(player, MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, SLOW_FALLING_AMPLIFIER);
        refresh(player, MobEffects.REGENERATION, REGENERATION_TICKS, REGENERATION_AMPLIFIER);
        refresh(player, MobEffects.RESISTANCE, RESISTANCE_TICKS, RESISTANCE_AMPLIFIER);
    }

    /** 只在"没有这个效果"或"剩余时间已经掉了 1 秒"或"等级比要求的低"时才重新施加。 */
    private static void refresh(ServerPlayer player, Holder<MobEffect> effect, int ticks, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() >= amplifier
                && current.getDuration() > ticks - REFRESH_SLACK_TICKS) {
            return;
        }
        player.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true, true));
    }
}
