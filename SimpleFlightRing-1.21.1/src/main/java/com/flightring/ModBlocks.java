package com.flightring;

import net.minecraft.world.level.block.Block;
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
                    .sound(SoundType.SNOW)
                    .noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .isValidSpawn((state, level, pos, type) -> false));

    /** 厚云块：实心内核，可站立、可刷怪。 */
    public static final DeferredBlock<Block> THICK_CLOUD_BLOCK = BLOCKS.registerBlock(
            "thick_cloud_block",
            Block::new,
            BlockBehaviour.Properties.of().strength(0.6F)
                    .sound(SoundType.SNOW)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false));

    /** 厚云砖块：建筑方块，也是云门的门框。 */
    public static final DeferredBlock<Block> THICK_CLOUD_BRICKS = BLOCKS.registerBlock(
            "thick_cloud_bricks",
            Block::new,
            BlockBehaviour.Properties.of().strength(0.8F)
                    .sound(SoundType.SNOW));

    public static final DeferredBlock<Block> THICK_CLOUD_SLAB = BLOCKS.registerBlock(
            "thick_cloud_slab",
            SlabBlock::new,
            BlockBehaviour.Properties.of().strength(0.8F)
                    .sound(SoundType.SNOW));


    /** 厚云楼梯（楼梯只需 baseState 供 codec 用，行为上不依赖它，所以这里用石头占位避免注册期取 .get()）。 */
    public static final DeferredBlock<Block> THICK_CLOUD_STAIRS = BLOCKS.registerBlock(
            "thick_cloud_stairs",
            properties -> new StairBlock(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), properties),
            BlockBehaviour.Properties.of().strength(0.8F)
                    .sound(SoundType.SNOW));

    public static final DeferredBlock<Block> THICK_CLOUD_WALL = BLOCKS.registerBlock(
            "thick_cloud_wall",
            WallBlock::new,
            BlockBehaviour.Properties.of().strength(0.8F)
                    .sound(SoundType.SNOW));

    private ModBlocks() {
    }
}
