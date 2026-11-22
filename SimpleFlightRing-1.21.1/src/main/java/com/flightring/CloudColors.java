package com.flightring;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

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
    public static int of(@Nullable ResourceLocation biomeId) {
        if (biomeId == null || !FlightRingMod.MODID.equals(biomeId.getNamespace())) {
            return DEFAULT;
        }
        return switch (biomeId.getPath()) {
            case "cloud_sea" -> 0xDCEEFF;
            case "clear_sky" -> 0xF2FAFF;
            case "thunderhead" -> 0xB2BECC;
            case "aurora" -> 0xFFFFFF;   // 极光群系不染色：绿色太诡异，保持纯白（天空另有极光渲染）
            case "sunset_clouds" -> 0xFFDCC4;
            default -> DEFAULT;
        };
    }

    /** 晚霞群系：白天暖桃色，入夜后恢复成冷白（用户 2026-11-22 要求）。 */
    private static final int SUNSET_WARM = 0xFFDCC4;

    /**
     * 按"白天程度"给晚霞群系做时间混合：{@code dayFactor} 1 = 白天（暖色）、0 = 深夜（冷白）。
     * <p>
     * 白天程度用与极光同一套日出日落窗口反算：
     * 日落 12500→13500 暖色渐隐、日出 22500→23500 暖色渐显，深夜完全冷白。
     */
    public static int applyTimeTint(String biomePath, int color, float dayFactor) {
        if (!"sunset_clouds".equals(biomePath)) {
            return color;
        }
        float t = Math.clamp(dayFactor, 0.0F, 1.0F);
        return lerpColor(DEFAULT, SUNSET_WARM, t);
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (Math.clamp(r, 0, 255) << 16) | (Math.clamp(g, 0, 255) << 8) | Math.clamp(b, 0, 255);
    }
}
