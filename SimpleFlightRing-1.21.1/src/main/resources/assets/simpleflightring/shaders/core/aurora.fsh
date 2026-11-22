#version 150

// 极光片元着色器 —— 忠实移植 Complementary Unbound r5.9.3 + EuphoriaPatches 1.10.5 的
//   shaders/lib/atmospherics/auroraBorealis.glsl -> GetAuroraBorealis()   (AURORA_STYLE 2 = Unbound)
//   shaders/lib/atmospherics/auroraBorealisVisibility.glsl -> GetAuroraVisibility()
// 参数取 Euphoria 默认: AURORA_DRAW_DISTANCE 0.65 / AURORA_UP_I 33 / AURORA_DOWN_I 33 /
//                       AURORA_SIZE 1.00 / AURORA_NOISE_SCALE 1.00 / AURORA_COLOR_MIX_POWER 2.0
// 采样数取 deferred1 天空通道的 25(+5) —— 光影里极光就是画在天空缓冲上的。
//
// 差异（都是"没有光影包管线"带来的必要替换，逐条见注释）:
//   noisetex            -> Sampler0（我们自己的 256x256 白噪声，3 通道独立 = Iris noisetex 的做法）
//   gbufferModelViewInverse*viewPos -> worldDir（vsh 传入的世界朝向顶点位置，normalize 后同 wpos 等价）
//   cameraPosition      -> CameraPos uniform（相机绝对世界坐标）
//   syncedTimeDynamic   -> Time uniform（世界年龄秒，gameTime/20，语义相同）
//   sunVisibility/rainFactorDynamic -> SunFade/RainFactor uniform（Java 侧算好传进来）
//   dither(蓝噪声)       -> hash12(gl_FragCoord.xy) 白噪声抖动（消除分层）
//   AURORA_CONDITION 3（雪原+满月）-> 由 Java 侧"极光群系"触发代替，这里不再扣减

uniform sampler2D Sampler0;
uniform float Time;        // 世界年龄（秒）= (tick + partialTick) / 20
uniform vec3 CameraPos;    // 相机绝对世界坐标
uniform float RainFactor;  // 0~1，雨量（原版 rainLevel）
uniform float SunFade;     // 0=深夜 1=白天，替代光影的 sunVisibility（平滑昼夜过渡）

in vec3 worldDir;    // 世界朝向（顶点位置未归一化，vsh 直传）
in vec4 vertexColor; // 占位（POSITION_COLOR 格式要求），不参与计算

out vec4 fragColor;

const float AURORA_SIZE = 1.0;
// 图案整体尺度：越小 => 极光纹理越大（用户 2026-11-22：之前像"地图被缩小"）
const float AURORA_PATTERN_SCALE = 6.0;
const float AURORA_NOISE_SCALE = 1.0;
const int sampleCount = 25;     // deferred1 天空通道用 25；gbuffers 版本才降到 10
const int sampleCountP = sampleCount + 5;

// ---- CU 的工具函数（shaders/lib/util/commonFunctions.glsl，逐字照抄）----

float pow2(float x) { return x * x; }

// 更快的 sqrt 近似（0~1 区间），不是真 sqrt —— 照抄，别"修正"
float sqrt1(float x) { return x * (2.0 - x); }

float clamp01(float x) { return clamp(x, 0.0, 1.0); }

// Rec.601 亮度（CU 用的是这个权重，不是 Rec.709）
float GetLuminance(vec3 color) { return dot(color, vec3(0.299, 0.587, 0.114)); }

// 白噪声抖动（Dave Hoskins hash12）——替代光影的蓝噪声 dither
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 dir = normalize(worldDir);
    float VdotU = dir.y;

    // ---- GetAuroraVisibility(VdotU, 1.0)，AURORA_DRAW_DISTANCE 0.65 ----
    float visibility = sqrt1(clamp01(VdotU * (0.65 * 1.125 + 0.75) - 0.225)) - SunFade - RainFactor;
    visibility *= 1.0 - VdotU * 0.9;  // VdotUAmount = 1.0：靠近天顶渐隐、地平线附近最亮
    if (visibility <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    // ---- 投影到"极光平面"：纯方向函数（wpos.xz / wpos.y），天体般钉在世界朝向上 ----
    float y = max(dir.y, 0.06);
    vec2 wposXZ = dir.xz / y;

    // 视差 + 慢漂移（CU: cameraPosition.xz*0.0075, x += syncedTimeDynamic*0.04）
    vec2 cameraPositionM = CameraPos.xz * 0.0075;
    cameraPositionM.x += Time * 0.04;

    float dither = hash12(gl_FragCoord.xy);
    float ditherM = dither + 5.0;
    float auroraAnimate = Time * 0.001;

    // ---- GetAuroraColor：预设 0（手动），UP(112,36,192) DOWN(96,255,192)，强度归一（AURORA_*_I=33）----
    vec3 auroraUp = vec3(112.0, 36.0, 192.0);
    vec3 auroraDown = vec3(96.0, 255.0, 192.0);
    auroraUp = max(auroraUp, vec3(0.001)) * ((33.0 * 0.093 + 3.1) / GetLuminance(auroraUp));
    auroraDown = max(auroraDown, vec3(0.001)) * ((33.0 * 0.245 + 8.15) / GetLuminance(auroraDown));

    // ---- 沿视线方向 25 层采样（"层叠体积"正是极光帘幕感的来源）----
    vec3 aurora = vec3(0.0);
    for (int i = 0; i < sampleCount; i++) {
        float current = pow2((float(i) + ditherM) / float(sampleCountP));
        vec2 planePos = wposXZ * (AURORA_SIZE * 0.8 + current) * AURORA_PATTERN_SCALE * AURORA_NOISE_SCALE + cameraPositionM;
        planePos *= 0.0007;

        // 主结构：白噪声 ridged 化（1-2|n-0.5| 的 16 次方 => 只留 0.5 附近的窄带 = 丝缕）
        float noise = texture(Sampler0, planePos).r;
        noise = pow2(pow2(pow2(pow2(1.0 - 2.0 * abs(noise - 0.5)))));

        // 两层不同频率的动态调制（.b 通道与 .r 独立 => 丝缕会各自扭动）
        noise *= texture(Sampler0, planePos * 3.0 + auroraAnimate).b;
        noise *= texture(Sampler0, planePos * 5.0 - auroraAnimate).b;

        float currentM = 1.0 - current;
        // 垂直颜色渐变：顶部紫、底部绿（AURORA_COLOR_MIX_POWER 2.0）
        aurora += noise * currentM * mix(auroraUp, auroraDown, pow2(pow2(currentM)));
    }
    aurora *= 1.8;  // AURORA_STYLE 2 (Unbound)

    fragColor = vec4(aurora * visibility / float(sampleCount), 1.0);
}
