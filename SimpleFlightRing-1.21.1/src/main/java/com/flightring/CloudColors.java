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
            case "aurora" -> 0xD9F6EC;
            case "sunset_clouds" -> 0xFFDCC4;
            default -> DEFAULT;
        };
    }
}
