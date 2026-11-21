package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * 厚云箱子 —— 与 {@link ChestBlockEntity} 唯一的不同就是**开关盖的声音换成我们自己的**。
 * <p>
 * 为什么不能直接继承就算了：原版的开关声写在 {@code ChestBlockEntity} 字段初始化里的匿名
 * {@link ContainerOpenersCounter} 上（{@code onOpen}/{@code onClose} 里硬编码 {@code SoundEvents.CHEST_OPEN}），
 * 那个字段是 private final，子类既拿不到也换不掉。所以这里**另起一个同款计数器**，
 * 并把 {@code startOpen}/{@code stopOpen} 改成走我们自己的计数器 —— 原版那个就再也不会被触发，
 * 于是不会同时响两遍。
 * <p>
 * 开盖动画仍然由父类负责：{@code signalOpenCount} 会发出 {@code triggerEvent(1, count)}，
 * 父类的 {@code ChestLidController} 收到后照常做开合动画，{@code ChestBlock#getTicker} 也照常驱动。
 */
public class ThickCloudChestBlockEntity extends ChestBlockEntity {

    private final ContainerOpenersCounter cloudOpeners = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            playCloudSound(level, pos, state, net.minecraft.sounds.SoundEvents.CHEST_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            playCloudSound(level, pos, state, net.minecraft.sounds.SoundEvents.CHEST_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int oldCount, int newCount) {
            ThickCloudChestBlockEntity.this.signalOpenCount(level, pos, state, oldCount, newCount);
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            if (!(player.containerMenu instanceof ChestMenu menu)) {
                return false;
            }
            Container container = menu.getContainer();
            return container == ThickCloudChestBlockEntity.this
                    || container instanceof CompoundContainer compound
                    && compound.contains(ThickCloudChestBlockEntity.this);
        }
    };

    public ThickCloudChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THICK_CLOUD_CHEST.get(), pos, state);
    }

    @Override
    public void startOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.cloudOpeners.incrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.cloudOpeners.decrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }


    /**
     * 关键：父类的 {@code recheckOpen()} 会把**父类那个私有计数器**重新核对一遍。
     * 方块每 5 tick 会被 {@code scheduleTick} 调度一次（计数器自己排的），此时 {@code recheckOpeners}
     * 发现玩家正开着容器，就会走父类计数器的 {@code onOpen} —— 于是**原版箱子音效又响了一遍**，
     * 和我们的叠在一起（用户 2026-11-19 实测就是这个现象）。
     * 所以这里也一起覆写，让"重新核对"只走我们自己的计数器。
     */
    @Override
    public void recheckOpen() {
        if (!this.remove) {
            this.cloudOpeners.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    /** 与原版 {@code ChestBlockEntity#playSound} 同样的定位规则：双箱只在右半边播一次。 */
    private static void playCloudSound(Level level, BlockPos pos, BlockState state, SoundEvent event) {
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.LEFT) {
            return;
        }
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        if (type == ChestType.RIGHT) {
            Direction direction = ChestBlock.getConnectedDirection(state);
            x += direction.getStepX() * 0.5D;
            z += direction.getStepZ() * 0.5D;
        }
        level.playSound(null, x, y, z, event, SoundSource.BLOCKS, 0.5F,
                level.random.nextFloat() * 0.1F + 0.9F);
    }
}
