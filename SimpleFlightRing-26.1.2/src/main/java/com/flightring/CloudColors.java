package com.flightring;

import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;

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
    /** 花草的兜底色：纯白 = 不染色（主世界/未登记的群系）。 */
    public static final int PLANT_DEFAULT = 0xFFFFFF;

    /**
     * 云绒草的群系染色（用户 2026-11-25：只染草和白花）。
     * <p>
     * 贴图本身是云白色，这里乘一层淡淡的群系色：晴空偏嫩绿、雷云压灰、极光薄荷、晚霞暖橙、
     * 云海最接近原色。都是浅色，不会把草压暗。
     */
    public static int grass(@Nullable Identifier biomeId) {
        if (biomeId == null || !FlightRingMod.MODID.equals(biomeId.getNamespace())) {
            return PLANT_DEFAULT;
        }
        return switch (biomeId.getPath()) {
            case "cloud_sea" -> 0xD8EAF3;
            case "clear_sky" -> 0xE2F4E4;
            case "thunderhead" -> 0xB4C2CE;
            case "aurora" -> 0xBCEFD8;
            case "sunset_clouds" -> 0xFFD9B6;
            default -> PLANT_DEFAULT;
        };
    }

    /** 云絮花（白花）的群系染色：只在晴空/雷云出现，所以只给这两档。 */
    public static int flower(@Nullable Identifier biomeId) {
        if (biomeId == null || !FlightRingMod.MODID.equals(biomeId.getNamespace())) {
            return PLANT_DEFAULT;
        }
        return switch (biomeId.getPath()) {
            case "clear_sky" -> 0xF8FCFF;
            case "thunderhead" -> 0xC6D2DF;
            default -> PLANT_DEFAULT;
        };
    }
}
