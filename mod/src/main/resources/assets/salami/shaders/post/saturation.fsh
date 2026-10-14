#version 330

// Salami Client – Color Saturation: Farben kräftiger (> 1) oder blasser (< 1).

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform SaturationConfig {
    float Saturation;
};

out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    fragColor = vec4(clamp(mix(vec3(luma), color, Saturation), 0.0, 1.0), 1.0);
}
