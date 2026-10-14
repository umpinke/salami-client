#version 330

// Salami Client – Motion Blur: aktuelles Bild mit dem vorherigen mischen.

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 PrevSize;
};

layout(std140) uniform MotionBlurConfig {
    float Strength;
};

out vec4 fragColor;

void main() {
    vec3 current = texture(InSampler, texCoord).rgb;
    vec3 previous = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(current, previous, Strength), 1.0);
}
