package com.flightring;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * 极光群系的夜空极光（1.21.1）。
 * <p>
 * 视觉公式<strong>逐字移植</strong>自 Complementary Unbound r5.9.3 + EuphoriaPatches 1.10.5
 * （{@code shaders/lib/atmospherics/auroraBorealis.glsl} 的 {@code GetAuroraBorealis()}，
 * Unbound 分支 = AURORA_STYLE 2，即光影包用户实际看到的样式）：
 * 沿视线方向对"极光平面"采样 25 层噪声，{@code 1-2|noise-0.5|} 的 16 次方只保留噪声值在
 * 0.5 附近的窄带 —— 丝缕感来自贴图本身（白噪声），而不是网格形状。
 * <p>
 * 因此几何只是个"方向画布"：球面穹顶（-10°~90° 仰角，半径 100），片元着色器拿世界朝向算一切。
 * <b>必须配白噪声贴图</b>（{@code textures/noise.png}，3 通道独立）—— 平滑 fBm 在
 * 16 次方下会全灭（离线仿真实测），而 {@code clamp}（CLAMP_TO_EDGE）会把公式必然超界的
 * 采样坐标全部捏到贴图边缘一列 texel 上，得到"一块一块正方形层叠"。
 * <p>
 * 在 {@code AFTER_SKY} 阶段补画（原版天空之后、地形之前），加法混合、无深度写入，
 * 星空之上、云层之下。极光是纯方向函数（{@code wpos.xz / wpos.y}）：像日月星辰一样
 * 钉在天空上，只随视角转动、不随平移移动（平移视差由 {@code CameraPos} 微量提供，
 * 与光影包一致）。
 * <p>
 * ★ 矩阵架构（2026-11-22 定案，"帽子/拿在手里/夹层"四轮翻车的最终结论）：
 * <ul>
 *   <li>{@code AFTER_SKY} 事件的 PoseStack 是<b>恒等</b>（NeoForge 派发时 poseStack 传 null），
 *       视图旋转 V 在 {@code event.getModelViewMatrix()} —— 不要碰事件 PoseStack、
 *       不要自己算 camera.rotation() 的共轭，历史两版方案都因此翻车；</li>
 *   <li>顶点属性 = <b>原始世界方向</b>（不做 CPU 变换）：片元着色器拿它当世界方向算图案。
 *       一旦 CPU 乘过任何矩阵，图案输入就变成"视线空间坐标"，图案立刻锁死在屏幕上（帽子）；</li>
 *   <li>gl_Position 的视图旋转只能来自矩阵（vsh 的 ModelViewMat）。
 *       {@code BufferUploader.drawWithShader} 用<b>全局</b>{@code RenderSystem.getModelViewMatrix()}
 *       （此刻为恒等）—— 所以绘制前对全局栈做作用域内覆盖 push/identity/mul(V)/apply，
 *       画完 pop 恢复；</li>
 *   <li>投影<b>不碰</b>（全局投影 = 原版天空家族正在用的同一个，view bob/受击/反胃都折在里面）。
 *       天空家族运动一致性靠两件事：<b>同一对矩阵</b>（全局投影 × V，与星星/月亮完全相同）
 *       + <b>同一半径尺度</b>（100，与原版天穹盘/太阳/月亮同款）。晃动 = ±3° 旋转
 *       + 平移成分按 1/半径衰减（半径 100 时 ~0.17°，与星星逐像素一致 = "轻微晃动、
 *       与天空融为一体"，即光影包的观感；半径 1 会放大 100 倍 = "拿在手里"，旧版翻车根源）。
 *       —— 曾用"干净投影"（AT 开放 getFov 重建无 bob 投影）做过一版：极光全静止，
 *       与仍在晃的星星产生相对运动（"夹层"），且比光影的实况（天体轻微晃）更死，已回退。</li>
 * </ul>
 * <p>
 * 触发条件：{@code the_cloud} 维度、所在群系是 {@code simpleflightring:aurora}。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class AuroraSky {

    /** 白噪声贴图（必须 REPEAT 包裹 + LINEAR 过滤，见 noise.png.mcmeta）。 */
    private static final ResourceLocation NOISE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "textures/noise.png");

    private static final ResourceLocation AURORA_BIOME =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "aurora");

    /** 自定义 core shader（片元里跑移植后的 GetAuroraBorealis）。 */
    private static net.minecraft.client.renderer.ShaderInstance SHADER;

    /** 首帧矩阵诊断（只打一次，验证矩阵架构）。 */
    private static boolean loggedFirstDraw = false;

    /** 日志限流用（每 10 秒最多打一行）。 */
    private static int lastLoggedTick = -1000;

    private AuroraSky() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(
                            event.getResourceProvider(), "simpleflightring:aurora",
                            DefaultVertexFormat.POSITION_COLOR),
                    shader -> SHADER = shader);
        } catch (java.io.IOException e) {
            FlightRingMod.LOGGER.error("[Aurora] failed to load shader", e);
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (!minecraft.level.dimension().equals(BrokenPortalTravel.CLOUD_DIMENSION)) {
            return;
        }
        Camera camera = event.getCamera();
        if (camera == null || SHADER == null) {
            return;
        }
        Vec3 cameraPos = camera.getPosition();
        float biomeWeight = auroraBiomeWeight(minecraft, cameraPos);
        // 昼夜淡入淡出（替代光影的 sunVisibility；夜晚 SunFade≈0，白天≈1）
        float sunFade = sunVisibility(minecraft);
        int ticks = minecraft.player == null ? 0 : minecraft.player.tickCount;
        if (ticks - lastLoggedTick > 200) {
            lastLoggedTick = ticks;
            FlightRingMod.LOGGER.info("[Aurora] stage fired: dim={} biomeWeight={} sunFade={}",
                    minecraft.level.dimension().location(), biomeWeight, sunFade);
        }
        if (biomeWeight <= 0.001F || sunFade >= 0.999F) {
            return;
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        float time = (minecraft.level.getGameTime() + partialTick) / 20.0F; // 世界年龄秒（= 光影 syncedTime）
        float rainFactor = minecraft.level.getRainLevel(partialTick);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE); // 纯加法
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> SHADER);
        RenderSystem.setShaderTexture(0, NOISE_TEXTURE);

        var timeUniform = SHADER.safeGetUniform("Time");
        if (timeUniform != null) {
            timeUniform.set(time);
        }
        var cameraUniform = SHADER.safeGetUniform("CameraPos");
        if (cameraUniform != null) {
            cameraUniform.set((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        }
        var rainUniform = SHADER.safeGetUniform("RainFactor");
        if (rainUniform != null) {
            rainUniform.set(rainFactor);
        }
        var biomeUniform = SHADER.safeGetUniform("BiomeWeight");
        if (biomeUniform != null) {
            biomeUniform.set(biomeWeight);
        }
        var sunUniform = SHADER.safeGetUniform("SunFade");
        if (sunUniform != null) {
            sunUniform.set(sunFade);
        }

        // ★ 矩阵架构（2026-11-22 定案，详见 09-踩坑 §3.21/§3.23）：
        //   - 事件 PoseStack 在 AFTER_SKY 是【恒等】（dispatchRenderStage 传 null -> new PoseStack()），
        //     视图旋转 V 在 event.getModelViewMatrix() 里；不要碰 camera.rotation() 的共轭，历史方案全错。
        //   - 顶点必须是【原始世界方向】，不做任何 CPU 变换 —— 片元着色器拿它当世界方向算图案。
        //   - gl_Position 的视图旋转只能来自矩阵：BufferUploader.drawWithShader 用的是全局
        //     RenderSystem.getModelViewMatrix()（此刻为恒等），所以对全局栈做【作用域内覆盖】
        //     push -> identity -> mul(V) -> apply，画完 pop 恢复。
        //   - 投影【不碰】：全局投影（原版 renderLevel 已把 view bob/受击/反胃折进去）正是原版
        //     天空家族（天穹盘/日出盘/太阳/月亮/星星）使用的同一个投影 —— 极光跟着星星月亮
        //     以完全相同的量晃（±3° 旋转为主，平移成分在半径 100 处仅 ~0.17°），
        //     天空家族融为一体，= 光影包的观感（"轻微晃动"）。穹顶半径取 100（= 原版天空
        //     尺度）正是为此：晃动平移成分的角位移 = 平移量/半径，半径 1 会把它放大 100 倍
        //     成"拿在手里"的大幅晃动（旧版翻车根源）。
        Matrix4f viewMatrix = event.getModelViewMatrix();
        if (!loggedFirstDraw) {
            loggedFirstDraw = true;
            Matrix4f globalBefore = new Matrix4f(RenderSystem.getModelViewMatrix());
            FlightRingMod.LOGGER.info(
                    "[Aurora] first draw: view(row-major m00..m22)=[{},{},{},{},{},{},{},{},{}], globalMV-before=[{},{},{},{}]",
                    fmt(viewMatrix.m00()), fmt(viewMatrix.m01()), fmt(viewMatrix.m02()),
                    fmt(viewMatrix.m10()), fmt(viewMatrix.m11()), fmt(viewMatrix.m12()),
                    fmt(viewMatrix.m20()), fmt(viewMatrix.m21()), fmt(viewMatrix.m22()),
                    fmt(globalBefore.m00()), fmt(globalBefore.m11()),
                    fmt(globalBefore.m22()), fmt(globalBefore.m33()));
        }
        Matrix4fStack glStack = RenderSystem.getModelViewStack();
        glStack.pushMatrix();
        glStack.identity();
        glStack.mul(viewMatrix);
        RenderSystem.applyModelViewMatrix();
        try {
            drawDome();
        } finally {
            glStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** 群系过渡半径（格）：跨过边界时在这个距离内平滑淡入淡出（用户 2026-11-22：12 格）。 */
    private static final double BIOME_BLEND_RADIUS = 12.0;
    /** 采样网格：以相机为中心 (GRID)² 个点，之后做双线性插值。 */
    private static final int BIOME_GRID = 5;

    /**
     * 群系"极光权重" 0~1。
     * <p>
     * 用户 2026-11-22 两次反馈：① 跨一步极光突现/突消；② 加权重后像"有挡位的灯"一阶一阶变。
     * 原因分别是"只看中心点"和"只数命中比例"（命中数只有 0~10 十一个整数档）。
     * <p>
     * 现在改为：在相机周围取 {@link #BIOME_GRID}×{@link #BIOME_GRID} 的布尔网格
     * （间距 = 2×半径/(GRID-1)），再按相机在网格内的位置做**双线性插值** ⇒ 权重随位置连续变化，
     * 不再有台阶。
     */
    private static float auroraBiomeWeight(Minecraft minecraft, Vec3 cameraPos) {
        double span = BIOME_BLEND_RADIUS * 2.0;
        double step = span / (BIOME_GRID - 1);
        double baseX = cameraPos.x - BIOME_BLEND_RADIUS;
        double baseZ = cameraPos.z - BIOME_BLEND_RADIUS;

        boolean[][] cell = new boolean[BIOME_GRID][BIOME_GRID];
        for (int ix = 0; ix < BIOME_GRID; ix++) {
            for (int iz = 0; iz < BIOME_GRID; iz++) {
                cell[ix][iz] = isAuroraAt(minecraft,
                        baseX + step * ix, cameraPos.y, baseZ + step * iz);
            }
        }

        // 双线性插值：先算出相机在这张网格里的相对位置（0 ~ GRID-1）
        double gx = Math.clamp((cameraPos.x - baseX) / step, 0.0, BIOME_GRID - 1.0);
        double gz = Math.clamp((cameraPos.z - baseZ) / step, 0.0, BIOME_GRID - 1.0);
        int x0 = Math.min((int) Math.floor(gx), BIOME_GRID - 2);
        int z0 = Math.min((int) Math.floor(gz), BIOME_GRID - 2);
        double tx = gx - x0;
        double tz = gz - z0;

        double v00 = cell[x0][z0] ? 1.0 : 0.0;
        double v10 = cell[x0 + 1][z0] ? 1.0 : 0.0;
        double v01 = cell[x0][z0 + 1] ? 1.0 : 0.0;
        double v11 = cell[x0 + 1][z0 + 1] ? 1.0 : 0.0;
        double a = v00 * (1 - tx) + v10 * tx;
        double b = v01 * (1 - tx) + v11 * tx;
        float w = (float) (a * (1 - tz) + b * tz);
        return w * w * (3.0F - 2.0F * w);   // smoothstep：两端更柔和
    }

    private static boolean isAuroraAt(Minecraft minecraft, double x, double y, double z) {
        var pos = net.minecraft.core.BlockPos.containing(x, y, z);
        if (!minecraft.level.hasChunkAt(pos)) {
            return false;
        }
        return minecraft.level.getBiome(pos).unwrapKey()
                .map(key -> key.location().equals(AURORA_BIOME)).orElse(false);
    }

    private static boolean inAuroraBiome(Minecraft minecraft, Vec3 cameraPos) {
        var pos = net.minecraft.core.BlockPos.containing(cameraPos);
        var holder = minecraft.level.getBiome(pos);
        return holder.unwrapKey().map(key -> key.location().equals(AURORA_BIOME)).orElse(false);
    }

    /**
     * 光影包 sunVisibility 的等价物：0=深夜（极光完全显示）、1=白天（完全不显示）。
     * <p>
     * 用户给出的实测区间是**太阳切入/切出的过程**（2026-11-22）：
     * <ul>
     *   <li><b>日落过程 12500 → 13500</b>（13500 太阳完全落下）⇒ 极光在这段里**淡入**，
     *       太阳刚落完时正好完全显现；</li>
     *   <li><b>日出过程 22500 → 23500</b>（23500 太阳完全升起）⇒ 极光在这段里**淡出**，
     *       太阳完全升起时正好消失。</li>
     * </ul>
     * 也就是说：极光的淡入淡出**和太阳的切入切出同步**，两者共用同一段时间窗。
     */
    private static float sunVisibility(Minecraft minecraft) {
        long dayTime = minecraft.level.getDayTime() % 24000L;
        if (dayTime < 0) {
            dayTime += 24000L;
        }
        if (dayTime >= 13500L && dayTime <= 22500L) {
            return 0.0F;                       // 深夜：完全显示
        }
        if (dayTime <= 12500L) {
            return 1.0F;                       // 白天（含 24000 环绕无缝）
        }
        if (dayTime < 13500L) {
            return 1.0F - smoothstep((dayTime - 12500L) / 1000.0F);   // 日落 12500→13500 淡入
        }
        return smoothstep((dayTime - 22500L) / 1000.0F);               // 日出 22500→23500 淡出
    }

    private static float smoothstep(float t) {
        float c = Math.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    /** 穹顶半径：与原版天空家族（天穹盘/太阳/月亮/星星都在 ~100）同尺度。 */
    private static final float DOME_RADIUS = 100.0F;

    /**
     * 球面穹顶（仰角 -10°~90°），顶点坐标 = **原始世界方向 × 100**（不做任何其他 CPU 变换），
     * 由 drawDome 外层把视图矩阵放进全局 ModelViewStack 上屏：
     * 片元里 worldDir = 顶点属性（归一化后）= 世界方向（图案世界锁定），
     * gl_Position = P·V·v —— 与原版星星/月亮走**同一对矩阵**（全局投影 × V）。
     * 半径取 100 正是关键：view bob 的平移成分的角位移 = 平移量/半径，
     * 半径 100 时 ~0.17°（与星星一致 = "轻微晃动、融为一体"），半径 1 时放大 100 倍
     * （"拿在手里"，旧版翻车根源）。
     * 仰角按 t² 分布：地平线附近（极光最亮、方向变化最快）密，天顶附近疏。
     */
    private static void drawDome() {
        final int sectors = 48;
        final int rings = 24;
        final double elevMin = Math.toRadians(-10.0);
        final double elevRange = Math.toRadians(100.0);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int r = 0; r < rings; r++) {
            double t0 = (double) r / rings;
            double t1 = (double) (r + 1) / rings;
            double elev0 = elevMin + elevRange * t0 * t0;
            double elev1 = elevMin + elevRange * t1 * t1;
            double cosE0 = Math.cos(elev0);
            double sinE0 = Math.sin(elev0);
            double cosE1 = Math.cos(elev1);
            double sinE1 = Math.sin(elev1);
            for (int s = 0; s < sectors; s++) {
                double az0 = Math.PI * 2.0 * s / sectors;
                double az1 = Math.PI * 2.0 * (s + 1) / sectors;
                float x00 = (float) (Math.cos(az0) * cosE0) * DOME_RADIUS;
                float z00 = (float) (Math.sin(az0) * cosE0) * DOME_RADIUS;
                float x10 = (float) (Math.cos(az1) * cosE0) * DOME_RADIUS;
                float z10 = (float) (Math.sin(az1) * cosE0) * DOME_RADIUS;
                float x11 = (float) (Math.cos(az1) * cosE1) * DOME_RADIUS;
                float z11 = (float) (Math.sin(az1) * cosE1) * DOME_RADIUS;
                float x01 = (float) (Math.cos(az0) * cosE1) * DOME_RADIUS;
                float z01 = (float) (Math.sin(az0) * cosE1) * DOME_RADIUS;
                // ★ 原始方向×半径直传（不乘 pose）：Position 归一化即世界方向
                buffer.addVertex(x00, (float) sinE0 * DOME_RADIUS, z00).setColor(255, 255, 255, 255);
                buffer.addVertex(x10, (float) sinE0 * DOME_RADIUS, z10).setColor(255, 255, 255, 255);
                buffer.addVertex(x11, (float) sinE1 * DOME_RADIUS, z11).setColor(255, 255, 255, 255);
                buffer.addVertex(x01, (float) sinE1 * DOME_RADIUS, z01).setColor(255, 255, 255, 255);
            }
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
