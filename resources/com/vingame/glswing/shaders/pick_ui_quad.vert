#version 330 core

layout(location = 0) in vec2 aCorner;
layout(location = 1) in vec2 aP00;
layout(location = 2) in vec2 aP10;
layout(location = 3) in vec2 aP01;
layout(location = 4) in vec4 aUvRect;
layout(location = 5) in vec4 aColor;
layout(location = 6) in vec4 aParameters;
layout(location = 7) in vec2 aSize;
layout(location = 8) in uint aPickId;
layout(location = 9) in vec4 aSecondaryColor;
layout(location = 10) in int aTextureSlot;

uniform mat4 uProjection;

out vec2 vLocal;
out vec2 vTexCoord;
out float vAlpha;
flat out vec4 vParameters;
flat out vec2 vSize;
flat out uint vPickId;
flat out vec4 vSecondaryColor;
flat out int vTextureSlot;

void main() {
    vec2 position = aP00
            + (aP10 - aP00) * aCorner.x
            + (aP01 - aP00) * aCorner.y;
    vLocal = aCorner * aSize;
    vTexCoord = mix(aUvRect.xy, aUvRect.zw, aCorner);
    vAlpha = aColor.a;
    vParameters = aParameters;
    vSize = aSize;
    vPickId = aPickId;
    vSecondaryColor = aSecondaryColor;
    vTextureSlot = aTextureSlot;
    gl_Position = uProjection * vec4(position, 0.0, 1.0);
}
