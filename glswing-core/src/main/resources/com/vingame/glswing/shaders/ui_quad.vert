#version 330 core

layout(location = 0) in vec2 aCorner;
layout(location = 1) in vec2 aP00;
layout(location = 2) in vec2 aP10;
layout(location = 3) in vec2 aP01;
layout(location = 4) in vec4 aUvRect;
layout(location = 5) in vec4 aColor;
layout(location = 6) in vec4 aParameters;
layout(location = 7) in vec2 aSize;
layout(location = 9) in vec4 aSecondaryColor;
layout(location = 10) in int aTextureSlot;
layout(location = 11) in vec4 aTertiaryColor;

uniform mat4 uProjection;
uniform vec2 uLogicalSize;

out vec2 vLocal;
out vec2 vTexCoord;
out vec4 vColor;
flat out vec4 vParameters;
flat out vec2 vSize;
flat out vec4 vSecondaryColor;
flat out int vTextureSlot;
flat out vec4 vAux;
flat out vec4 vTertiaryColor;

void main() {
    vec2 position = aP00
            + (aP10 - aP00) * aCorner.x
            + (aP01 - aP00) * aCorner.y;
    vLocal = aCorner * aSize;
    vTexCoord = mix(aUvRect.xy, aUvRect.zw, aCorner);
    vColor = aColor;
    vParameters = aParameters;
    vSize = aSize;
    vSecondaryColor = aSecondaryColor;
    vTextureSlot = aTextureSlot;
    vAux = aUvRect;
    vTertiaryColor = aTertiaryColor;
    gl_Position = uProjection * vec4(position, 0.0, 1.0);
    if (int(aParameters.x + 0.5) == 2 && aSecondaryColor.z > 0.5) {
        vec2 clipOffset = vec2(aSecondaryColor.x, -aSecondaryColor.y)
                * 2.0 / max(uLogicalSize, vec2(1.0));
        gl_Position.xy += clipOffset * gl_Position.w;
    }
}
