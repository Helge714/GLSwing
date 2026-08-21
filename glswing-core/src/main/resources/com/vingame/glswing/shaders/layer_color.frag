#version 330 core

in vec2 vTexCoord;

uniform sampler2D uTexture;
uniform float uOpacity;
uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform float uGamma;
uniform float uInvert;
uniform vec3 uTint;
uniform float uTintStrength;

out vec4 fragColor;

void main() {
    vec4 source = texture(uTexture, vTexCoord);
    if (source.a <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 color = source.rgb / source.a;
    color *= uBrightness;
    color = (color - 0.5) * uContrast + 0.5;
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luminance), color, uSaturation);
    color = pow(max(color, vec3(0.0)), vec3(1.0 / uGamma));
    color = mix(color, vec3(1.0) - color, uInvert);
    color = mix(color, uTint, uTintStrength);
    color = clamp(color, 0.0, 1.0);
    fragColor = vec4(color * source.a, source.a) * uOpacity;
}
