package com.flightring;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocks of the mod.
 * <p>
 * The only one so far is the portal that leads to the hidden "broken" dimension. It copies the
 * vanilla nether portal in every visible way (same textures/models, same obsidian frame rule, same
 * walk-in behaviour and delay, same sound and particles) and is registered without an item, exactly
 * like {@code minecraft:nether_portal} - the block id itself is what marks a portal as "rewritten",
 * so no extra bookkeeping is needed to remember which portals lead where.
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FlightRingMod.MODID);

    public static final DeferredBlock<Block> BROKEN_PORTAL = BLOCKS.registerBlock(
            "broken_portal",
            BrokenPortalBlock::new,
            BlockBehaviour.Properties.of()
                    .noCollission()
                    .randomTicks()
                    .strength(-1.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK));

    // ---- 云层之上 the_cloud 的方块（M1）-----------------------------------------------
    // 材料链：云块 ×9 → 厚云块 → 厚云砖 → 台阶 / 墙；厚云砖同时是云门的门框（M4）。

    /** 云块：外壳，踩上去会像蜘蛛网一样缓慢下陷并穿透；带飞行戒指时可当普通方块站立。 */
    public static final DeferredBlock<Block> CLOUD_BLOCK = BLOCKS.registerBlock(
            "cloud_block",
            CloudBlock::new,
            BlockBehaviour.Properties.of().strength(0.2F)
                    .sound(ModSounds.CLOUD_SOUND_TYPE)
                    .noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .isValidSpawn((state, level, pos, type) -> false));

    /**
     * 厚云块：实心内核，可站立、可刷怪。
     * <p>
     * ★ 这里**不能**用 {@code noOcclusion()}：它是实心不透明块，noOcclusion 会让网格器
     * 认为"邻居挡不住视线"，于是**内部面全部照画** —— 旁观模式钻进地形只看到一堆内部面，
     * 而且渲染量暴涨（用户 2026-11-20 实测）。
     */
    public static final DeferredBlock<Block> THICK_CLOUD_BLOCK = BLOCKS.registerBlock(
            "thick_cloud_block",
            Block::new,
            BlockBehaviour.Properties.of().strength(0.6F)
                    .sound(ModSounds.THICK_SOUND_TYPE)
                    .isViewBlocking((state, level, pos) -> false));

    /** 厚云砖块：建筑方块，也是云门的门框。 */
    public static final DeferredBlock<Block> THICK_CLOUD_BRICKS = BLOCKS.registerBlock(
            "thick_cloud_bricks",
            ThickCloudBrickBlock::new,
            BlockBehaviour.Properties.of().strength(0.8F)
                    .sound(ModSounds.THICK_SOUND_TYPE));

    public static final DeferredBlock<Block> THICK_CLOUD_SLAB = BLOCKS.registerBlock(
            "thick_cloud_slab",
            SlabBlock::new,
            BlockBehaviour.Properties.of().strength(0.8F)
                    .sound(ModSounds.THICK_SOUND_TYPE));


    /** 厚云楼梯（楼梯只需 baseState 供 codec 用，行为上不依赖它，所以这里用石头占位避免注册期取 .get()）。 */
    public static final DeferredBlock<Block> THICK_CLOUD_STAIRS = BLOCKS.registerBlock(
            "thick_cloud_stairs",
            properties -> new StairBlock(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), properties),
            BlockBehaviour.Properties.of().strength(0.8F)
                    .sound(ModSounds.THICK_SOUND_TYPE));

    /** 云门：厚云砖框里点亮出来的传送门方块（无物品、无掉落）。 */
    public static final DeferredBlock<Block> CLOUD_PORTAL = BLOCKS.registerBlock(
            "cloud_portal",
            CloudPortalBlock::new,
            BlockBehaviour.Properties.of()
                    .noCollission()
                    .randomTicks()
                    .strength(-1.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK));

    /** 厚云床：两格方块、睡觉/设置重生点全部沿用原版，渲染器单独换贴图（见 CloudBedClient）。 */
    public static final DeferredBlock<Block> THICK_CLOUD_BED = BLOCKS.registerBlock(
            "thick_cloud_bed",
            ThickCloudBedBlock::new,
            BlockBehaviour.Properties.of().strength(0.2F)
                    .sound(ModSounds.CHEST_SOUND_TYPE)
                    .noOcclusion());

    /** 厚云箱子：单箱/大箱照原版逻辑，开关盖用我们自己的音效（见 ThickCloudChestBlockEntity）。 */
    public static final DeferredBlock<Block> THICK_CLOUD_CHEST = BLOCKS.registerBlock(
            "thick_cloud_chest",
            ThickCloudChestBlock::new,
            BlockBehaviour.Properties.of().strength(2.0F)
                    .sound(ModSounds.CHEST_SOUND_TYPE)
                    .noOcclusion());

    /** 厚云门：云房子的门（BlockSetType 决定音效与开关行为）。 */
    public static final DeferredBlock<Block> THICK_CLOUD_DOOR = BLOCKS.registerBlock(
            "thick_cloud_door",
            properties -> new DoorBlock(ModBlockSetTypes.CLOUD, properties),
            BlockBehaviour.Properties.of().strength(0.8F)
                    .sound(ModSounds.THICK_SOUND_TYPE)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY));

    public static final DeferredBlock<Block> THICK_CLOUD_WALL = BLOCKS.registerBlock(
            "thick_cloud_wall",
            WallBlock::new,
            BlockBehaviour.Properties.of().strength(0.8F)
                    .sound(ModSounds.THICK_SOUND_TYPE));

    private ModBlocks() {
    }

    // ---- 云木（M5）：一套完整的木头材料 -------------------------------------------------
    // 云树长在云岛上，给床 / 箱子 / 门提供"正经材料"，也让这些方块能用原版的木头音效。

    /** 云木原木：云树的树干。 */
    public static final DeferredBlock<Block> CLOUD_LOG = BLOCKS.registerBlock(
            "cloud_log",
            RotatedPillarBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .sound(SoundType.WOOD));

    /** 云木：六面都是树皮纹理（和原版 oak_wood 一样）。 */
    public static final DeferredBlock<Block> CLOUD_WOOD = BLOCKS.registerBlock(
            "cloud_wood",
            RotatedPillarBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .sound(SoundType.WOOD));

    /** 云母木板：云木加工出来的建筑板材。 */
    public static final DeferredBlock<Block> CLOUD_PLANKS = BLOCKS.registerBlock(
            "cloud_planks",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD));

    /** 云叶：云树的树冠。 */
    public static final DeferredBlock<Block> CLOUD_LEAVES = BLOCKS.registerBlock(
            "cloud_leaves",
            LeavesBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(0.2F)
                    .randomTicks()
                    .sound(SoundType.GRASS)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
}
