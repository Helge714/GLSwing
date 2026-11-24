#version 330 core

in vec2 vTexCoord;
in float vAlpha;
flat in uint vPickId;

uniform sampler2D uTexture;
uniform float uMsdfPixelRange;
uniform float uAlphaThreshold;

layout(location = 0) out uint outPickId;

void main() {
    vec3 msdf = texture(uTexture, vTexCoord).rgb;
    float signedDistance = max(min(msdf.r, msdf.g), min(max(msdf.r, msdf.g), msdf.b)) - 0.5;
    vec2 unitRange = vec2(uMsdfPixelRange) / vec2(textureSize(uTexture, 0));
    vec2 screenTextureSize = vec2(1.0) / max(fwidth(vTexCoord), vec2(0.000001));
    float screenPixelRange = max(0.5 * dot(unitRange, screenTextureSize), 1.0);
    float coverage = clamp(signedDistance * screenPixelRange + 0.5, 0.0, 1.0);
    if (vPickId == uint(0) || vAlpha * coverage < uAlphaThreshold)
        discard;
    outPickId = vPickId;
}
