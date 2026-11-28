package com.flightring;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * 液态星光的流体类型：默认 {@link FluidType} 在非原版流体里不会给玩家任何移动逻辑，
 * 所以这里补一套"像在水里"的移动。详见 {@link #moveLikeWater}。
 */
public final class StarlightFluidType extends FluidType {

    public StarlightFluidType(Properties properties) {
        super(properties);
    }

    @Override
    public boolean move(LivingEntity entity, Vec3 input, double gravity) {
        moveLikeWater(entity, input, gravity);
        return true;      // 告诉游戏"移动我处理完了"，别再往下走
    }

    /**
     * 复刻原版"在水里"的那一套移动（{@code LivingEntity#travelInWater} 的等价实现）。
     * <p>
     * 为什么必须自己写：26.x 的 {@code LivingEntity#travelInFluid} 对**非原版流体**只做一件事 ——
     * 调 {@code moveInFluid(...)}，返回 true 才认为"我自己动完了"；返回 false 就直接走到
     * {@code isInWater()}/{@code isInLava()} 两个分支，而我们的流体两者都不成立，
     * 于是**没有任何移动代码被执行**，玩家一进去就完全定住（物品走别的路径所以正常）。
     * <p>
     * 这里用的是原版水里的参数：缓慢阻尼 0.8（冲刺 0.9）、速度 0.02、
     * 受"水中移动效率"与 SWIM_SPEED 属性影响、出水时给一个 0.3 的向上助推。
     */
    private static void moveLikeWater(LivingEntity entity, Vec3 input, double gravity) {
        boolean falling = entity.getDeltaMovement().y <= 0.0D;
        double oldY = entity.getY();

        float slowDown = entity.isSprinting() ? 0.9F : 0.8F;
        float speed = 0.02F;
        float efficiency = (float) entity.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if (!entity.onGround()) {
            efficiency *= 0.5F;
        }
        if (efficiency > 0.0F) {
            slowDown += (0.54600006F - slowDown) * efficiency;
            speed += (entity.getSpeed() - speed) * efficiency;
        }
        speed *= (float) entity.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED);

        entity.moveRelative(speed, input);
        entity.move(MoverType.SELF, entity.getDeltaMovement());

        Vec3 movement = entity.getDeltaMovement();
        if (entity.horizontalCollision && entity.onClimbable()) {
            movement = new Vec3(movement.x, 0.2D, movement.z);
        }
        movement = movement.multiply(slowDown, 0.8F, slowDown);

        // 原版 LivingEntity#getFluidFallingAdjustedMovement 的等价实现（那个方法是 protected 的）
        if (!entity.isNoGravity() && !entity.isSprinting()) {
            double dy;
            if (falling && Math.abs(movement.y - 0.005D) < 0.005D) {
                dy = -0.005D;
            } else {
                dy = movement.y - gravity / 16.0D;
            }
            movement = new Vec3(movement.x, dy, movement.z);
        }
        entity.setDeltaMovement(movement);

        // 顶到方块时像原版一样"蹬一下"出水
        if (entity.horizontalCollision && entity.isFree(movement.x, movement.y + 0.6D - entity.getY() + oldY, movement.z)) {
            entity.setDeltaMovement(movement.x, 0.3D, movement.z);
        }
    }
}
