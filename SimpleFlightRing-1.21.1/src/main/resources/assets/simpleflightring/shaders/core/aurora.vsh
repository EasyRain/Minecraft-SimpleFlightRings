#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec3 rawDir;

void main() {
    // ★ 传"未归一化"的顶点位置：在顶点着色器里归一化后再插值，跨大四边形会失真，
    // 结果就是每个网格块自己一套噪声（用户 2026-11-21 看到的"一块一块"）。
    rawDir = Position;
    vertexColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
