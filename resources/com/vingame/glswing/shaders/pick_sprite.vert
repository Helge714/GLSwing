#version 330 core

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aColorUv;
layout(location = 2) in vec4 aTint;
layout(location = 3) in uint aPickId;
layout(location = 4) in int aTextureSlot;
layout(location = 5) in vec2 aDepthUv;
layout(location = 7) in vec4 aDepthData;
layout(location = 8) in int aFlags;
layout(location = 9) in float aAlphaCutoff;

uniform mat4 uProjection;

out vec2 vColorUv;
out vec2 vDepthUv;
out float vTintAlpha;
out vec4 vDepthData;
out float vAlphaCutoff;
flat out uint vPickId;
flat out int vTextureSlot;
flat out int vFlags;

void main() {
    vColorUv = aColorUv;
    vDepthUv = aDepthUv;
    vTintAlpha = aTint.a;
    vDepthData = aDepthData;
    vAlphaCutoff = aAlphaCutoff;
    vPickId = aPickId;
    vTextureSlot = aTextureSlot;
    vFlags = aFlags;
    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
    gl_Position.z = aDepthData.x * 2.0 - 1.0;
}

