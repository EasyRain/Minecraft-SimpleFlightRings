package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * 本模组自己声明的方块标签。
 */
public final class ModTags {

    /**
     * 「能种草种花的地表」：厚云块 / 云块，以及原版的各种泥土。
     * <p>
     * 两版差异放在标签 JSON 里，代码只认这一个标签：
     * <ul>
     *   <li>1.21.1：{@code #minecraft:dirt}（泥土全家）+ {@code minecraft:farmland}</li>
     *   <li>26.1.2：{@code #minecraft:supports_vegetation}（原版树苗用的那个）</li>
     * </ul>
     * 见 {@code data/simpleflightring/tags/block/cloud_soil.json}。
     */
    public static final TagKey<Block> CLOUD_SOIL = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "cloud_soil"));

    private ModTags() {
    }
}
