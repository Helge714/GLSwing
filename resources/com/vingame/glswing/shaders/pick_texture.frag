#version 330 core

in vec2 vTexCoord;
in float vAlpha;
flat in uint vPickId;
flat in int vTextureSlot;

uniform sampler2D uTextures[16];
uniform float uAlphaThreshold;

layout(location = 0) out uint outPickId;

float textureAlpha(int slot, vec2 uv) {
    switch (slot) {
    case 0: return texture(uTextures[0], uv).a;
    case 1: return texture(uTextures[1], uv).a;
    case 2: return texture(uTextures[2], uv).a;
    case 3: return texture(uTextures[3], uv).a;
    case 4: return texture(uTextures[4], uv).a;
    case 5: return texture(uTextures[5], uv).a;
    case 6: return texture(uTextures[6], uv).a;
    case 7: return texture(uTextures[7], uv).a;
    case 8: return texture(uTextures[8], uv).a;
    case 9: return texture(uTextures[9], uv).a;
    case 10: return texture(uTextures[10], uv).a;
    case 11: return texture(uTextures[11], uv).a;
    case 12: return texture(uTextures[12], uv).a;
    case 13: return texture(uTextures[13], uv).a;
    case 14: return texture(uTextures[14], uv).a;
    default: return texture(uTextures[15], uv).a;
    }
}

void main() {
    float alpha = textureAlpha(vTextureSlot, vTexCoord) * vAlpha;
    if (vPickId == uint(0) || alpha < uAlphaThreshold)
        discard;
    outPickId = vPickId;
}
