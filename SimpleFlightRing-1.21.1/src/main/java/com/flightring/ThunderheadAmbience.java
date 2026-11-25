package com.flightring;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 雷云群系的"远处闷雷"环境音（用户 2026-11-25：频率对齐原版雷雨天气）。
 * <p>
 * <b>为什么不用生物群系 JSON 的 {@code additions_sound}：</b>那条走
 * {@code SimpleSoundInstance.forAmbientAddition}，是<b>音量 1、贴脸、非定位</b>播放的，
 * 每 2 分钟在你耳边炸一声，既吵又不像天气。原版雷雨里的雷声是
 * {@code LightningBolt} 用 <b>音量 10000</b> 在远处的落点播的，所以听起来是"天边滚过"。
 * 这里照原版的做法：客户端按随机间隔，在玩家周围 35~70 格、上方 6~22 格的位置播一记雷，
 * 音量 8~12 用来抵消线性衰减（MC 的可听半径 ≈ 16 × 音量，所以 60 格外仍有 0.5 左右的响度）。
 * <p>
 * <b>频率：</b>原版落雷是"每个已加载区块每 tick 掷 1/100000"，视距 8~12 时约 289~625 个区块
 * ⇒ 平均 8~17 秒一记雷。这里直接用 8~20 秒的随机间隔，节奏与原版雷雨一致且不呆板。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class ThunderheadAmbience {

    private static final ResourceKey<Biome> THUNDERHEAD = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "thunderhead"));

    /** 两次雷声的间隔（秒，随机）。 */
    private static final int MIN_GAP_SECONDS = 8;
    private static final int MAX_GAP_SECONDS = 20;

    /** 雷声落点：玩家周围这个半径内（格）。 */
    private static final double MIN_DISTANCE = 35.0D;
    private static final double MAX_DISTANCE = 70.0D;

    /** 雷声落点比玩家高多少格（从"天上"滚下来）。 */
    private static final double MIN_HEIGHT = 6.0D;
    private static final double MAX_HEIGHT = 22.0D;

    /** 音量（8~12 ⇒ 60 格外仍有约一半响度，听起来才是远处的雷而不是贴脸炸）。 */
    private static final float MIN_VOLUME = 8.0F;
    private static final float MAX_VOLUME = 12.0F;

    /** 距离下次雷声还剩多少 tick。 */
    private static int ticksUntilThunder;

    /** 上一 tick 是否在雷云群系里（用来做"进入时先等一段"）。 */
    private static boolean wasInBiome;

    private ThunderheadAmbience() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused()) {
            return;
        }

        if (!level.getBiome(player.blockPosition()).is(THUNDERHEAD)) {
            ticksUntilThunder = 0;
            wasInBiome = false;
            return;
        }

        if (!wasInBiome) {
            // 刚进群系：先安静一段，别一进去就炸
            wasInBiome = true;
            ticksUntilThunder = nextGapTicks(level.getRandom());
            return;
        }

        if (ticksUntilThunder > 0) {
            ticksUntilThunder--;
            return;
        }

        ticksUntilThunder = nextGapTicks(level.getRandom());
        playDistantThunder(level, player);
    }

    private static int nextGapTicks(RandomSource random) {
        int seconds = MIN_GAP_SECONDS + random.nextInt(MAX_GAP_SECONDS - MIN_GAP_SECONDS + 1);
        return seconds * 20;
    }

    private static void playDistantThunder(ClientLevel level, LocalPlayer player) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        double x = player.getX() + Math.cos(angle) * distance;
        double z = player.getZ() + Math.sin(angle) * distance;
        double y = player.getY() + MIN_HEIGHT + random.nextDouble() * (MAX_HEIGHT - MIN_HEIGHT);

        float volume = MIN_VOLUME + random.nextFloat() * (MAX_VOLUME - MIN_VOLUME);
        float pitch = 0.72F + random.nextFloat() * 0.16F;      // 压低一点 = 更远

        level.playLocalSound(x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, volume, pitch, false);
    }
}
