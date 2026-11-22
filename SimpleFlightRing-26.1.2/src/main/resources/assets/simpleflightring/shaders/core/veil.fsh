#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // 阴天穹顶：灰白色 + 由 ColorModulator.a 控制的整体透明度
    fragColor = vec4(ColorModulator.rgb, ColorModulator.a * vertexColor.a);
}
