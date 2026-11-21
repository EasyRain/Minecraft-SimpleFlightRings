#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec3 worldDir;

void main() {
    // 顶点位置就是"方向 * 半径"，直接当方向传给片元着色器
    worldDir = normalize(Position);
    vertexColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
