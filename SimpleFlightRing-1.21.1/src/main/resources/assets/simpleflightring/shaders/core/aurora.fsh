#version 150

uniform float Time;

in vec4 vertexColor;
in vec3 worldDir;

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
    // 把方向投影到"天空平面"上做噪声：水平角 + 高度，纹理就自然随视角连续
    float ang = atan(worldDir.z, worldDir.x);
    float h = clamp(worldDir.y * 2.0 + 0.5, 0.0, 1.0);

    vec2 p = vec2(ang * 1.6 + Time * 0.02, h * 2.4 + Time * 0.01);

    // 竖向条纹：在不同尺度上取噪声，做出"帘褶"
    float n1 = fbm(p * vec2(3.0, 1.2));
    float n2 = fbm(p * vec2(9.0, 3.0) + 13.7);
    float curtain = smoothstep(0.35, 0.85, n1 * 0.65 + n2 * 0.35);

    // 上下柔和淡出（高斯）
    float band = exp(-pow((h - 0.45) / 0.30, 2.0));

    float a = vertexColor.a * curtain * band;
    if (a < 0.004) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, a);
}
