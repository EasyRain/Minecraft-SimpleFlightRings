package com.flightring;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 云门的传送逻辑。
 * <p>
 * 与下界"按坐标比例换算"不同，这里主世界与云维度是**同一套坐标**（1:1）：
 * 从哪进去就大致从哪出来，只是高度换成云层的表面。
 * <p>
 * 到达点不再像 {@link BrokenPortalTravel} 那样搭一座完整的门框 ——
 * 云层本身就足够密（用户 2026-11-20：岛很密集，直接找有地形的地方），
 * 所以只用 {@link #findSurfaceNear} 在附近找一格真的有云岛的位置，把两格门放在岛面上即可。
 * 万一附近什么都没有，就在半空中铺一小块厚云平台兜底。
 */
public final class CloudPortalTravel {

    public static final ResourceKey<Level> CLOUD_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "the_cloud"));

    /** 在目标 (x,z) 附近搜寻岛面的最大半径。 */
    private static final int SEARCH_RADIUS = 96;
    /** 找不到地形时的兜底高度。 */
    private static final int FALLBACK_Y = 300;

    private CloudPortalTravel() {
    }

    /** 主世界 -> 云层。 */
    @Nullable
    public static TeleportTransition toCloud(ServerLevel from, Entity entity, BlockPos portalPos) {
        ServerLevel cloud = from.getServer().getLevel(CLOUD_DIMENSION);
        if (cloud == null) {
            return null;
        }
        BlockPos target = findSurfaceNear(cloud, portalPos.getX(), portalPos.getZ());
        BlockPos arrival = placePortal(cloud, target, axisOf(from.getBlockState(portalPos)));
        return transition(cloud, arrival, entity);
    }

    /** 云层 -> 主世界。 */
    @Nullable
    public static TeleportTransition backToOrigin(ServerLevel from, Entity entity, BlockPos portalPos) {
        ServerLevel overworld = from.getServer().overworld();
        BlockPos target = findSurfaceNear(overworld, portalPos.getX(), portalPos.getZ());
        BlockPos arrival = placePortal(overworld, target, axisOf(from.getBlockState(portalPos)));
        return transition(overworld, arrival, entity);
    }

    private static TeleportTransition transition(ServerLevel to, BlockPos feet, Entity entity) {
        return new TeleportTransition(to, Vec3.atBottomCenterOf(feet), Vec3.ZERO,
                entity.getYRot(), entity.getXRot(),
                TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
    }

    /**
     * 在 (x,z) 附近找一块"站得住"的地面，返回脚所在的位置。
     * <p>
     * 从中心向外一圈圈找，找到第一个有实心地面的列就用它；找不到就退回半空平台。
     */
    private static BlockPos findSurfaceNear(ServerLevel level, int x, int z) {
        level.getChunkAt(new BlockPos(x, level.getMinY() + 8, z));
        for (int r = 0; r <= SEARCH_RADIUS; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;   // 只走当前这一圈的边框
                    }
                    int cx = x + dx;
                    int cz = z + dz;
                    int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                    if (surface > level.getMinY() + 8) {
                        return new BlockPos(cx, surface, cz);
                    }
                }
            }
        }
        return new BlockPos(x, FALLBACK_Y, z);
    }

    /**
     * 在落点放两格云门（必要时先铺一小块厚云平台），返回玩家脚下应该站的位置。
     */
    private static BlockPos placePortal(ServerLevel level, BlockPos target, Direction.Axis axis) {
        BlockState portal = ModBlocks.CLOUD_PORTAL.get().defaultBlockState().setValue(CloudPortalBlock.AXIS, axis);
        BlockState platform = ModBlocks.THICK_CLOUD_BLOCK.get().defaultBlockState();

        int y = target.getY();
        level.getChunkAt(target);

        // 地面：脚下这一格如果是空气就补一块厚云，避免直接掉下去
        if (level.getBlockState(target.below()).isAir()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(target.offset(dx, -1, dz), platform);
                }
            }
        }

        // 两格高的门（沿传送轴的垂直面）
        level.setBlockAndUpdate(target, portal);
        level.setBlockAndUpdate(target.above(), portal);
        level.setBlockAndUpdate(target.above(2), Blocks.AIR.defaultBlockState());
        return target;
    }

    private static Direction.Axis axisOf(BlockState state) {
        return state.hasProperty(CloudPortalBlock.AXIS) ? state.getValue(CloudPortalBlock.AXIS) : Direction.Axis.X;
    }
}
