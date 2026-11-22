#version 150

uniform vec4 ColorModulator;

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // 阴天穹顶：颜色由 ColorModulator 给（灰白），透明度也来自它
    fragColor = vec4(ColorModulator.rgb, ColorModulator.a * vertexColor.a);
}
