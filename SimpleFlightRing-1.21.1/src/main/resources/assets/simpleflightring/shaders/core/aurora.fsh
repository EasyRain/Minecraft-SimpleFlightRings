#version 150

// 直接把 Complementary Unbound 的极光算法搬过来做对照实验：
//   shaders/lib/atmospherics/auroraBorealis.glsl -> GetAuroraBorealis()
// 差异只有：gbufferModelViewInverse*viewPos -> rawDir（我们自己的世界方向），
//           noisetex -> Sampler0（随机噪声贴图），cameraPosition/frameTimeCounter -> 0/Time。

uniform sampler2D Sampler0;
uniform float Time;

in vec4 vertexColor;
in vec3 rawDir;

out vec4 fragColor;

const int sampleCount = 12;
const int sampleCountP = sampleCount + 5;
const float AURORA_SIZE = 1.0;
const float AURORA_NOISE_SCALE = 1.0;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 dir = normalize(rawDir);

    // 光影里是 wpos.xz /= wpos.y（只在看天时有效）；这里把 y 夹到一个正数下限，
    // 这样"地平线附近"也能算，不会除零炸掉。
    float y = max(dir.y, 0.06);
    vec2 wposXZ = dir.xz / y;

    float dither = hash12(gl_FragCoord.xy);
    float ditherM = dither * 5.0;

    vec3 aurora = vec3(0.0);
    vec3 auroraUp = vec3(112.0, 36.0, 192.0) / 255.0;   // [1] Complementary
    vec3 auroraDown = vec3(96.0, 255.0, 192.0) / 255.0;

    for (int i = 0; i < sampleCount; i++) {
        float current = pow((i + ditherM) / float(sampleCountP), 2.0);
        vec2 planePos = wposXZ * (AURORA_SIZE * 0.8 + current) * 11.0 * AURORA_NOISE_SCALE;
        planePos *= 0.004;

        float noise = texture(Sampler0, planePos * 0.35).r;
        noise = pow(pow(pow(pow(1.0 - 2.0 * abs(noise - 0.5), 2.0), 2.0), 2.0), 2.0);
        noise *= texture(Sampler0, planePos * 0.9 + Time * 0.02).b;
        noise *= texture(Sampler0, planePos * 1.7 - Time * 0.015).b;

        float currentM = 1.0 - current;
        aurora += noise * currentM * mix(auroraUp, auroraDown, pow(currentM * currentM, 2.0));
    }

    aurora *= 6.0;
    aurora /= float(sampleCount);

    // 上下渐隐由几何自身的 alpha 提供
    float lum = clamp(dot(aurora, vec3(0.4)), 0.0, 1.0);
    fragColor = vec4(aurora * 1.5 + vec3(0.02, 0.08, 0.05), vertexColor.a * clamp(lum * 6.0, 0.0, 1.0));
}
