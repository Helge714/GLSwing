#version 330 core

layout(location = 0) in vec2 aP00;
layout(location = 1) in vec2 aP10;
layout(location = 2) in vec2 aP11;
layout(location = 3) in vec2 aP01;
layout(location = 4) in vec4 aColorUvRect;
layout(location = 5) in vec4 aDepthUvRect;
layout(location = 6) in vec4 aNormalUvRect;
layout(location = 7) in vec4 aTint;
layout(location = 8) in vec4 aDepthData;
layout(location = 9) in vec2 aTangentX;
layout(location = 10) in vec2 aTangentY;
layout(location = 11) in uint aPickId;
layout(location = 12) in int aTextureSlot;
layout(location = 13) in int aFlags;

uniform mat4 uProjection;
uniform mat4 uModel;

out vec2 vColorUv;
out vec2 vDepthUv;
out vec2 vNormalUv;
out vec4 vTint;
out vec4 vDepthData;
out float vAlphaCutoff;
out vec2 vTangentX;
out vec2 vTangentY;
flat out uint vPickId;
flat out int vTextureSlot;
flat out int vFlags;

void main() {
    const vec2 corners[6] = vec2[6](
            vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
            vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0));
    vec2 corner = corners[gl_VertexID];
    vec2 position = mix(mix(aP00, aP10, corner.x),
            mix(aP01, aP11, corner.x), corner.y);
    vColorUv = mix(aColorUvRect.xy, aColorUvRect.zw, corner);
    vDepthUv = mix(aDepthUvRect.xy, aDepthUvRect.zw, corner);
    vNormalUv = mix(aNormalUvRect.xy, aNormalUvRect.zw, corner);
    vTint = aTint;
    vDepthData = aDepthData;
    vAlphaCutoff = aDepthData.w;
    vec2 transformedTangentX = (uModel * vec4(aTangentX, 0.0, 0.0)).xy;
    vec2 transformedTangentY = (uModel * vec4(aTangentY, 0.0, 0.0)).xy;
    vTangentX = length(transformedTangentX) > 0.000001
            ? normalize(transformedTangentX) : vec2(1.0, 0.0);
    vTangentY = length(transformedTangentY) > 0.000001
            ? normalize(transformedTangentY) : vec2(0.0, 1.0);
    vPickId = aPickId;
    vTextureSlot = aTextureSlot;
    vFlags = aFlags;
    gl_Position = uProjection * uModel * vec4(position, 0.0, 1.0);
    gl_Position.z = aDepthData.x * 2.0 - 1.0;
}
