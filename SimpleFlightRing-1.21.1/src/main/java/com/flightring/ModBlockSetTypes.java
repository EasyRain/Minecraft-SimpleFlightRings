package com.flightring;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * The {@link BlockSetType} of the cloud blocks.
 * <p>
 * A door needs one: it decides the open/close sounds, whether a hand can open it, and the
 * pressure-plate sensitivity. Clouds are treated like a soft wooden set - snowy sound, wooden door
 * sounds - so the door of a cloud house does not click like iron.
 * <p>
 * {@code BlockSetType} is <b>not</b> a registry object in this Minecraft version: {@code register}
 * only puts it into a static map keyed by name, which is why the name is prefixed to stay unique.
 */
public final class ModBlockSetTypes {

    public static final BlockSetType CLOUD = BlockSetType.register(new BlockSetType(
            "simpleflightring_cloud",
            true,   // can be opened by hand
            true,   // can be opened by a wind charge
            true,   // can be activated by arrows
            BlockSetType.PressurePlateSensitivity.EVERYTHING,
            SoundType.SNOW,
            // 木门的开关声在云上太"硬"，整套换成羊毛的放置/破坏声（用户 2026-11-19）
            SoundEvents.WOOL_BREAK,
            SoundEvents.WOOL_PLACE,
            SoundEvents.WOOL_BREAK,
            SoundEvents.WOOL_PLACE,
            SoundEvents.WOOL_BREAK,
            SoundEvents.WOOL_PLACE,
            SoundEvents.WOOL_BREAK,
            SoundEvents.WOOL_PLACE));

    private ModBlockSetTypes() {
    }
}
