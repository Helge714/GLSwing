#version 330 core

in vec2 vColorUv;
in vec2 vDepthUv;
in float vTintAlpha;
in vec4 vDepthData;
in float vAlphaCutoff;
flat in uint vPickId;
flat in int vTextureSlot;
flat in int vFlags;

uniform sampler2D uColorMaps[5];
uniform sampler2D uDepthMaps[5];

layout(location = 0) out uint outPickId;

float colorAlpha(int slot, vec2 uv) {
    switch (slot) {
    case 0: return texture(uColorMaps[0], uv).a;
    case 1: return texture(uColorMaps[1], uv).a;
    case 2: return texture(uColorMaps[2], uv).a;
    case 3: return texture(uColorMaps[3], uv).a;
    default: return texture(uColorMaps[4], uv).a;
    }
}

float depthSample(int slot, vec2 uv) {
    switch (slot) {
    case 0: return texture(uDepthMaps[0], uv).r;
    case 1: return texture(uDepthMaps[1], uv).r;
    case 2: return texture(uDepthMaps[2], uv).r;
    case 3: return texture(uDepthMaps[3], uv).r;
    default: return texture(uDepthMaps[4], uv).r;
    }
}

void main() {
    float alpha = colorAlpha(vTextureSlot, vColorUv) * vTintAlpha;
    float threshold = (vFlags & 8) != 0 ? vAlphaCutoff : 0.001;
    if (vPickId == uint(0) || alpha < threshold)
        discard;
    float relief = (vFlags & 1) != 0
            ? depthSample(vTextureSlot, vDepthUv) : 0.5;
    gl_FragDepth = clamp(vDepthData.x
            + mix(vDepthData.y, vDepthData.z, relief), 0.0, 1.0);
    outPickId = vPickId;
}

