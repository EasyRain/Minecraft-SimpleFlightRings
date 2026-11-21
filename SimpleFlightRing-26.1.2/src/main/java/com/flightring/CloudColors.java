package com.flightring;

import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;

/**
 * 云层的群系配色表。
 * <p>
 * 像草方块那样，让云块的颜色随所在群系变化（用户 2026-11-21 指定）：
 * 这里只维护「群系 → 颜色」的小表，客户端渲染时按玩家所在位置查表即可。
 * 颜色是**乘算**到贴图上的，所以要挑接近白色的浅色，才不会把云压暗。
 */
public final class CloudColors {

    /** 兜底：没有登记颜色的群系（例如主世界的云屿）用这个偏冷的白。 */
    public static final int DEFAULT = 0xDDE8F2;

    private CloudColors() {
    }

    /** 按群系 id 取颜色（RGB，不含 alpha）。 */
    public static int of(@Nullable Identifier biomeId) {
        if (biomeId == null) {
            return DEFAULT;
        }
        if (!FlightRingMod.MODID.equals(biomeId.getNamespace())) {
            return DEFAULT;
        }
        return switch (biomeId.getPath()) {
            case "cloud_sea" -> 0xDCEEFF;        // 云海：偏蓝的白
            case "clear_sky" -> 0xF2FAFF;        // 晴空：最亮
            case "thunderhead" -> 0xB2BECC;      // 雷云：压暗的灰蓝
            case "aurora" -> 0xD9F6EC;           // 极光：淡淡的青绿
            case "sunset_clouds" -> 0xFFDCC4;    // 晚霞：暖桃色
            default -> DEFAULT;
        };
    }

    /** 按群系 Holder 取颜色。 */
    public static int of(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> of(key.identifier())).orElse(DEFAULT);
    }

    /** 按世界 + 坐标取颜色。 */
    public static int of(LevelReader level, net.minecraft.core.BlockPos pos) {
        return of(level.getBiome(pos));
    }
}
