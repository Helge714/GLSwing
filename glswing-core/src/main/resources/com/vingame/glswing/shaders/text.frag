#version 330 core

in vec2 vTexCoord;
in vec4 vColor;

uniform sampler2D uTexture;
uniform float uMsdfPixelRange;

out vec4 fragColor;

void main() {
    vec3 msdf = texture(uTexture, vTexCoord).rgb;
    float signedDistance = max(min(msdf.r, msdf.g), min(max(msdf.r, msdf.g), msdf.b)) - 0.5;

    vec2 unitRange = vec2(uMsdfPixelRange) / vec2(textureSize(uTexture, 0));
    vec2 screenTextureSize = vec2(1.0) / max(fwidth(vTexCoord), vec2(0.000001));
    float screenPixelRange = max(0.5 * dot(unitRange, screenTextureSize), 1.0);
    float coverage = clamp(signedDistance * screenPixelRange + 0.5, 0.0, 1.0);
    fragColor = vec4(vColor.rgb, vColor.a * coverage);
}
