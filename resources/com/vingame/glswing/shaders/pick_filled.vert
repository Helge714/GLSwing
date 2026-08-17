#version 330 core

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec4 aColor;
layout(location = 3) in uint aPickId;

uniform mat4 uProjection;

flat out uint vPickId;
out float vAlpha;

void main() {
    vPickId = aPickId;
    vAlpha = aColor.a;
    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
}
