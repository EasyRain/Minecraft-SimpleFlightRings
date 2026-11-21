#version 150

uniform float Time;

in vec4 vertexColor;
in vec3 rawDir;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}

void main() {
    // ★ 在片元里归一化，方向才在整个天空连续（否则四边形边界会露出来）
    vec3 dir = normalize(rawDir);

    float ang = atan(dir.z, dir.x);
    float h = dir.y;

    // 以"高度"为纵向坐标，做上下渐隐的带
    float band = exp(-pow((h - 0.22) / 0.30, 2.0));

    // 帘褶：不同尺度的噪声叠加，横向拉伸、纵向压缩
    vec2 p = vec2(ang * 2.2 + Time * 0.03, h * 5.0 - Time * 0.02);
    float n1 = fbm(p * vec2(2.0, 0.8));
    float n2 = fbm(p * vec2(6.0, 2.2) + 11.3);
    float curtain = smoothstep(0.30, 0.80, n1 * 0.6 + n2 * 0.4);

    float a = vertexColor.a * curtain * band;
    if (a < 0.004) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, a);
}
