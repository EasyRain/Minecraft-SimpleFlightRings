#version 150

// 极光顶点着色器（2026-11-22 矩阵架构定案）：
//   Position 属性 = 原始世界方向（Java 端不做任何 CPU 变换），
//   worldDir 直传给片元当世界方向算图案（图案世界锁定的唯一正确做法）；
//   gl_Position 的视图旋转只来自 ModelViewMat —— Java 端在绘制前把视图矩阵
//   event.getModelViewMatrix()（= AFTER_SKY 事件 PoseStack 为恒等、真正视图矩阵在此）
//   作用域内放进全局 ModelViewStack，drawWithShader 自动取用。

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
