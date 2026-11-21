package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own sound events for the cloud blocks.
 * <p>
 * Why not reuse vanilla's events: hidden subtitles take their text from the <b>sound event's</b> subtitle, so a
 * cloud block playing {@code block.snow.break} showed "方块：被破坏" instead of naming the block (user
 * 2026-11-19, subtitle screenshot). Our own events carry our own subtitle keys while still playing the vanilla
 * <b>audio files</b> - the {@code sounds.json} entries point at {@code dig/snow1..4} / {@code dig/cloth1..4},
 * which live in the vanilla resource pack, so nothing is copied or redistributed.
 * <p>
 * The events are created eagerly ({@code createVariableRangeEvent}) because {@link SoundType} and
 * {@link net.minecraft.world.level.block.state.properties.BlockSetType} need the instances at class-init time,
 * long before the registry event runs; registering the same instance afterwards is the standard NeoForge pattern.
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, FlightRingMod.MODID);

    /** 云块：踩 / 挖 / 放 / 敲 / 落地 —— 用原版雪的音色。 */
    public static final SoundEvent CLOUD_BREAK = register("block.cloud_block.break");
    public static final SoundEvent CLOUD_STEP = register("block.cloud_block.step");
    public static final SoundEvent CLOUD_PLACE = register("block.cloud_block.place");
    public static final SoundEvent CLOUD_HIT = register("block.cloud_block.hit");
    public static final SoundEvent CLOUD_FALL = register("block.cloud_block.fall");

    /** 厚云（含砖/台阶/楼梯/墙）：同一套雪音色，但字幕点名"厚云"。 */
    public static final SoundEvent THICK_BREAK = register("block.thick_cloud.break");
    public static final SoundEvent THICK_STEP = register("block.thick_cloud.step");
    public static final SoundEvent THICK_PLACE = register("block.thick_cloud.place");
    public static final SoundEvent THICK_HIT = register("block.thick_cloud.hit");
    public static final SoundEvent THICK_FALL = register("block.thick_cloud.fall");




    public static final SoundType CLOUD_SOUND_TYPE =
            new SoundType(1.0F, 1.0F, CLOUD_BREAK, CLOUD_STEP, CLOUD_PLACE, CLOUD_HIT, CLOUD_FALL);

    public static final SoundType THICK_SOUND_TYPE =
            new SoundType(1.0F, 1.0F, THICK_BREAK, THICK_STEP, THICK_PLACE, THICK_HIT, THICK_FALL);

    private ModSounds() {
    }

    private static SoundEvent register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(FlightRingMod.MODID, path);
        SoundEvent event = SoundEvent.createVariableRangeEvent(id);
        SOUNDS.register(path, () -> event);
        return event;
    }
}
