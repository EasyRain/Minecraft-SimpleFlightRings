package com.flightring;

/**
 * 云维度用的确定性二维噪声（自己实现，不依赖原版 noise 路由）。
 * <p>
 * 为什么不用 {@code NormalNoise}：那套东西要注册 {@code NoiseParameters}、还要从 {@code RandomState}
 * 拿噪声实例，维度 JSON 里也得跟着配；而云层地形只需要"几个大尺度上连续、又有细节"的场，
 * 自己写一个哈希值噪声 + 多层叠加（fbm）就够了，而且完全确定、与运行环境无关。
 * <p>
 * 输出范围约 [-1, 1]；同一坐标同一 seed 永远相同，所以区块怎么加载都不会接缝。
 */
public final class CloudNoise {

    private CloudNoise() {
    }

    /** 单个晶格点的哈希 → [-1, 1]。 */
    private static double hash(int x, int z, long seed) {
        long h = seed;
        h = h * 6364136223846793005L + 1442695040888963407L;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L;
        h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        h = Long.rotateLeft(h, 27) * 0x94D049BB133111EBL;
        h ^= h >>> 33;
        // 取低 53 bit 变成 [0,1)，再映射到 [-1,1]
        return ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** 单层值噪声。{@code scale} 越大，特征越小。 */
    public static double value(double x, double z, double scale, long seed) {
        double fx = x / scale;
        double fz = z / scale;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = smooth(fx - x0);
        double tz = smooth(fz - z0);
        double n00 = hash(x0, z0, seed);
        double n10 = hash(x0 + 1, z0, seed);
        double n01 = hash(x0, z0 + 1, seed);
        double n11 = hash(x0 + 1, z0 + 1, seed);
        double a = n00 + (n10 - n00) * tx;
        double b = n01 + (n11 - n01) * tx;
        return a + (b - a) * tz;
    }

    /** 多层叠加：每层尺度减半、权重减半，结果归一化到约 [-1, 1]。 */
    public static double fbm(double x, double z, double scale, int octaves, long seed) {
        double sum = 0.0;
        double norm = 0.0;
        double amplitude = 1.0;
        double current = scale;
        for (int i = 0; i < octaves; i++) {
            sum += value(x, z, current, seed + i * 7919L) * amplitude;
            norm += amplitude;
            amplitude *= 0.5;
            current *= 0.5;
        }
        return sum / norm;
    }


    /** 三维值噪声：给厚云核挖洞、做悬垂用。 */
    public static double value3(double x, double y, double z, double scale, long seed) {
        double fx = x / scale;
        double fy = y / scale;
        double fz = z / scale;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        int z0 = (int) Math.floor(fz);
        double tx = smooth(fx - x0);
        double ty = smooth(fy - y0);
        double tz = smooth(fz - z0);

        double n000 = hash3(x0, y0, z0, seed);
        double n100 = hash3(x0 + 1, y0, z0, seed);
        double n010 = hash3(x0, y0 + 1, z0, seed);
        double n110 = hash3(x0 + 1, y0 + 1, z0, seed);
        double n001 = hash3(x0, y0, z0 + 1, seed);
        double n101 = hash3(x0 + 1, y0, z0 + 1, seed);
        double n011 = hash3(x0, y0 + 1, z0 + 1, seed);
        double n111 = hash3(x0 + 1, y0 + 1, z0 + 1, seed);

        double x00 = n000 + (n100 - n000) * tx;
        double x10 = n010 + (n110 - n010) * tx;
        double x01 = n001 + (n101 - n001) * tx;
        double x11 = n011 + (n111 - n011) * tx;
        double y0i = x00 + (x10 - x00) * ty;
        double y1i = x01 + (x11 - x01) * ty;
        return y0i + (y1i - y0i) * tz;
    }

    private static double hash3(int x, int y, int z, long seed) {
        long h = seed ^ ((long) y * 0x27D4EB2F165667C5L);
        h = h * 6364136223846793005L + 1442695040888963407L;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L;
        h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        h = Long.rotateLeft(h, 27) * 0x94D049BB133111EBL;
        h ^= h >>> 33;
        return ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    /** 平滑阶梯：把 t 从 [lo, hi] 映射到 [0, 1]，两端各有平滑过渡。 */
    public static double smoothstep(double lo, double hi, double t) {
        double v = (t - lo) / (hi - lo);
        v = Math.max(0.0, Math.min(1.0, v));
        return v * v * (3.0 - 2.0 * v);
    }
}
