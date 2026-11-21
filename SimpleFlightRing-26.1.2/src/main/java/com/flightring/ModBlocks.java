package com.flightring;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
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
            properties -> properties
                    .noCollision()
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
            properties -> properties.strength(0.2F)
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
            ThickCloudBlock::new,
            properties -> properties.strength(0.6F)
                    .sound(ModSounds.THICK_SOUND_TYPE)
                    .isViewBlocking((state, level, pos) -> false));

    /** 厚云砖块：建筑方块，也是云门的门框。 */
    public static final DeferredBlock<Block> THICK_CLOUD_BRICKS = BLOCKS.registerBlock(
            "thick_cloud_bricks",
            ThickCloudBrickBlock::new,
            properties -> properties.strength(0.8F)
                    .sound(ModSounds.THICK_SOUND_TYPE));

    public static final DeferredBlock<Block> THICK_CLOUD_SLAB = BLOCKS.registerBlock(
            "thick_cloud_slab",
            SlabBlock::new,
            properties -> properties.strength(0.8F)
                    .sound(ModSounds.THICK_SOUND_TYPE));


    /** 厚云楼梯（楼梯只需 baseState 供 codec 用，行为上不依赖它，所以这里用石头占位避免注册期取 .get()）。 */
    public static final DeferredBlock<Block> THICK_CLOUD_STAIRS = BLOCKS.registerBlock(
            "thick_cloud_stairs",
            properties -> new StairBlock(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), properties),
            properties -> properties.strength(0.8F)
                    .sound(ModSounds.THICK_SOUND_TYPE));

    /** 云门：厚云砖框里点亮出来的传送门方块（无物品、无掉落）。 */
    public static final DeferredBlock<Block> CLOUD_PORTAL = BLOCKS.registerBlock(
            "cloud_portal",
            CloudPortalBlock::new,
            properties -> properties
                    .noCollision()
                    .randomTicks()
                    .strength(-1.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK));

    /** 厚云床：睡觉/重生点照原版，渲染器单独换成厚云贴图。 */
    public static final DeferredBlock<Block> THICK_CLOUD_BED = BLOCKS.registerBlock(
            "thick_cloud_bed",
            ThickCloudBedBlock::new,
            properties -> properties.strength(0.2F)
                    .sound(ModSounds.CHEST_SOUND_TYPE)
                    .noOcclusion());

    /** 厚云箱子：单箱/大箱照原版逻辑，开关盖用我们自己的音效（26.x 由 ChestBlock 构造函数接收）。 */
    public static final DeferredBlock<Block> THICK_CLOUD_CHEST = BLOCKS.registerBlock(
            "thick_cloud_chest",
            ThickCloudChestBlock::new,
            properties -> properties.strength(2.0F)
                    .sound(ModSounds.CHEST_SOUND_TYPE)
                    .noOcclusion());


    public static final DeferredBlock<Block> THICK_CLOUD_WALL = BLOCKS.registerBlock(
            "thick_cloud_wall",
            WallBlock::new,
            properties -> properties.strength(0.8F)
                    .sound(ModSounds.THICK_SOUND_TYPE));

    private ModBlocks() {
    }

    // ---- 云木（M5）：一套完整的木头材料 -------------------------------------------------

    /** 云木原木：云树的树干。 */
    public static final DeferredBlock<Block> CLOUD_LOG = BLOCKS.registerBlock(
            "cloud_log",
            RotatedPillarBlock::new,
            properties -> properties.strength(2.0F).sound(SoundType.WOOD));

    /** 云木：六面都是树皮纹理。 */
    public static final DeferredBlock<Block> CLOUD_WOOD = BLOCKS.registerBlock(
            "cloud_wood",
            RotatedPillarBlock::new,
            properties -> properties.strength(2.0F).sound(SoundType.WOOD));


    /** 去皮云木原木：用斧头给云木原木去皮得到。 */
    public static final DeferredBlock<Block> STRIPPED_CLOUD_LOG = BLOCKS.registerBlock(
            "stripped_cloud_log",
            RotatedPillarBlock::new,
            properties -> properties.strength(2.0F).sound(SoundType.WOOD));

    /** 去皮云木。 */
    public static final DeferredBlock<Block> STRIPPED_CLOUD_WOOD = BLOCKS.registerBlock(
            "stripped_cloud_wood",
            RotatedPillarBlock::new,
            properties -> properties.strength(2.0F).sound(SoundType.WOOD));

    /** 云母木板。 */
    public static final DeferredBlock<Block> CLOUD_PLANKS = BLOCKS.registerBlock(
            "cloud_planks",
            Block::new,
            properties -> properties.strength(2.0F, 3.0F).sound(SoundType.WOOD));


    // ---- 云母木变体（M5）：台阶 / 楼梯 / 栅栏 / 栅栏门 / 门 / 活板门 / 按钮 / 压力板 ----------
    // 全部挂在 CloudWoodTypes.CLOUD_SET 上，音效直接复用原版木头。

    public static final DeferredBlock<Block> CLOUD_STAIRS = BLOCKS.registerBlock(
            "cloud_stairs",
            properties -> new StairBlock(ModBlocks.CLOUD_PLANKS.get().defaultBlockState(), properties),
            properties -> properties.strength(2.0F, 3.0F).sound(SoundType.WOOD));

    public static final DeferredBlock<Block> CLOUD_SLAB = BLOCKS.registerBlock(
            "cloud_slab",
            SlabBlock::new,
            properties -> properties.strength(2.0F, 3.0F).sound(SoundType.WOOD));

    public static final DeferredBlock<Block> CLOUD_FENCE = BLOCKS.registerBlock(
            "cloud_fence",
            FenceBlock::new,
            properties -> properties.strength(2.0F, 3.0F).sound(SoundType.WOOD));

    public static final DeferredBlock<Block> CLOUD_FENCE_GATE = BLOCKS.registerBlock(
            "cloud_fence_gate",
            properties -> new FenceGateBlock(CloudWoodTypes.CLOUD_WOOD_TYPE, properties),
            properties -> properties.strength(2.0F, 3.0F).sound(SoundType.WOOD));

    public static final DeferredBlock<Block> CLOUD_DOOR = BLOCKS.registerBlock(
            "cloud_door",
            properties -> new DoorBlock(CloudWoodTypes.CLOUD_SET, properties),
            properties -> properties.strength(3.0F).noOcclusion().sound(SoundType.WOOD));

    public static final DeferredBlock<Block> CLOUD_TRAPDOOR = BLOCKS.registerBlock(
            "cloud_trapdoor",
            properties -> new TrapDoorBlock(CloudWoodTypes.CLOUD_SET, properties),
            properties -> properties.strength(3.0F).noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false).sound(SoundType.WOOD));

    public static final DeferredBlock<Block> CLOUD_BUTTON = BLOCKS.registerBlock(
            "cloud_button",
            properties -> new ButtonBlock(CloudWoodTypes.CLOUD_SET, 30, properties),
            properties -> properties.noCollision().strength(0.5F)
                    .pushReaction(PushReaction.DESTROY).sound(SoundType.WOOD));

    public static final DeferredBlock<Block> CLOUD_PRESSURE_PLATE = BLOCKS.registerBlock(
            "cloud_pressure_plate",
            properties -> new PressurePlateBlock(CloudWoodTypes.CLOUD_SET, properties),
            properties -> properties.noCollision().strength(0.5F)
                    .pushReaction(PushReaction.DESTROY).sound(SoundType.WOOD));

    /** 云树苗：能种在土上，也能直接种在厚云块上。 */
    public static final DeferredBlock<Block> CLOUD_SAPLING = BLOCKS.registerBlock(
            "cloud_sapling",
            CloudSaplingBlock::new,
            properties -> properties.noCollision().randomTicks().instabreak()
                    .sound(SoundType.GRASS)
                    .pushReaction(PushReaction.DESTROY));

    /** 云叶。 */
    public static final DeferredBlock<Block> CLOUD_LEAVES = BLOCKS.registerBlock(
            "cloud_leaves",
            CloudLeavesBlock::new,
            properties -> properties.strength(0.2F)
                    .randomTicks()
                    .sound(SoundType.GRASS)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
}
