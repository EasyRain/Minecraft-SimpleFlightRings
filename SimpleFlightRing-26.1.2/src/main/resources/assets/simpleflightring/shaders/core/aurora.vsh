#version 330

// 极光穹顶顶点着色器（26.x）
// 26.x 的矩阵来自 DynamicTransforms / Projection 两个 std140 uniform block，
// 不再是 1.21.1 的 ModelViewMat / ProjMat 单独 uniform。
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;

out vec3 worldDir;
out vec4 vertexColor;

void main() {
    // 顶点 = 原始世界方向 × 100；这里【不做任何变换】，只把方向直传片元，
    // 视图旋转全部交给 ModelViewMat（与原版星星/月亮同一对矩阵）。
    worldDir = Position;
    vertexColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
