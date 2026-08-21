#version 330 core

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
layout(location = 2) in vec4 aColor;
layout(location = 3) in uint aPickId;
layout(location = 4) in int aTextureSlot;

uniform mat4 uProjection;

out vec2 vTexCoord;
out float vAlpha;
flat out uint vPickId;
flat out int vTextureSlot;

void main() {
    vTexCoord = aTexCoord;
    vAlpha = aColor.a;
    vPickId = aPickId;
    vTextureSlot = aTextureSlot;
    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
}
