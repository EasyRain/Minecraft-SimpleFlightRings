package com.flightring;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

/**
 * 云木的木头类型与方块组。
 * <p>
 * 用它们构造门 / 活板门 / 栅栏门 / 按钮 / 压力板，就能自动拿到**原版木头音效**
 * （开关门的吱呀声、按钮的咔哒声），不需要再单独准备音频文件。
 */
public final class CloudWoodTypes {

    /** 云母木的方块组：木制音效、可被活塞推动等默认行为。 */
    public static final BlockSetType CLOUD_SET = new BlockSetType("simpleflightring:cloud");

    /** 云母木的木头类型（栅栏门要用）。 */
    public static final WoodType CLOUD_WOOD_TYPE = new WoodType("simpleflightring:cloud", CLOUD_SET);

    private CloudWoodTypes() {
    }
}
