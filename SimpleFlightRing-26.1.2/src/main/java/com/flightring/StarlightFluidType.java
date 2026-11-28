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

    /** 每"浸没 1 格"给多少向上的速度。 */
    private static final double BUOYANCY_PER_BLOCK = 0.012D;

    /** 浮力上限：压住它，否则深池里会像弹簧一样把人弹飞。 */
    private static final double BUOYANCY_MAX = 0.035D;

    /** 竖直速度封顶，保证是"慢慢浮起来"而不是"窜上去"。 */
    private static final double MAX_RISE = 0.06D;

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
     * 这里用的是"水与熔岩之间"的参数：阻尼 0.62（冲刺 0.72）、推进 0.014、
     * 受"水中移动效率"与 SWIM_SPEED 属性影响、出水时给一个 0.3 的向上助推；
     * 另外加了一层<b>浮力</b>：按浸没深度给 0.012/格（上限 0.035）的向上速度，
     * 竖直速度封顶 0.06 ⇒ 泡在里面会慢慢浮到液面，而不会被弹飞。
     */
    private static void moveLikeWater(LivingEntity entity, Vec3 input, double gravity) {
        boolean falling = entity.getDeltaMovement().y <= 0.0D;
        double oldY = entity.getY();

        // 粘度取"水(0.8/0.02) 与 熔岩(0.5/0.02) 之间"：阻尼更大、推进更弱，动起来有明显"稠"的感觉
        float slowDown = entity.isSprinting() ? 0.72F : 0.62F;
        float speed = 0.014F;
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
        movement = movement.multiply(slowDown, 0.72F, slowDown);

        // 浮力：按"浸没深度"给上升速度（越深托得越明显），但压上限，
        // 于是手感是"在稠液里慢慢浮上去"，而不是被弹出去。
        double submerged = entity.getFluidTypeHeight(ModFluids.LIQUID_STARLIGHT_TYPE.get());
        if (submerged > 0.0D) {
            double lift = Math.min(submerged * BUOYANCY_PER_BLOCK, BUOYANCY_MAX);
            movement = movement.add(0.0D, lift, 0.0D);
        }

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
        if (movement.y > MAX_RISE) {
            movement = new Vec3(movement.x, MAX_RISE, movement.z);
        }
        entity.setDeltaMovement(movement);

        // 顶到方块时像原版一样"蹬一下"出水
        if (entity.horizontalCollision && entity.isFree(movement.x, movement.y + 0.6D - entity.getY() + oldY, movement.z)) {
            entity.setDeltaMovement(movement.x, 0.3D, movement.z);
        }
    }
}
