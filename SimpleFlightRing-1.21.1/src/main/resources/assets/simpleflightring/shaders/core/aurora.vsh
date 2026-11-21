#version 150

// 极光顶点着色器：几何就是"方向画布"（单位球穹顶），
// worldDir 直接传顶点位置，片元里 normalize 即得世界朝向 —— 与光影里
// gbufferModelViewInverse*viewPos 再除以分量的 wpos 完全等价（尺度不变）。

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 worldDir;
out vec4 vertexColor;

void main() {
    worldDir = Position;
    vertexColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
