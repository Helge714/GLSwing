#version 330 core

in vec2 vTexCoord;

uniform sampler2D uTexture;
uniform float uOpacity;
uniform float uGrayscale;

out vec4 fragColor;

void main() {
    vec4 source = texture(uTexture, vTexCoord);
    float luminance = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 transformed = mix(source.rgb, vec3(luminance), uGrayscale);
    fragColor = vec4(transformed, source.a) * uOpacity;
}
