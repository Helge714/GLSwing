#version 330 core

layout(location = 0) in vec2 aP00;
layout(location = 1) in vec2 aP10;
layout(location = 2) in vec2 aP11;
layout(location = 3) in vec2 aP01;
layout(location = 4) in vec4 aColorUvRect;
layout(location = 5) in vec4 aDepthUvRect;
layout(location = 7) in vec4 aTint;
layout(location = 8) in vec4 aDepthData;
layout(location = 11) in uint aPickId;
layout(location = 12) in int aTextureSlot;
layout(location = 13) in int aFlags;

uniform mat4 uProjection;
uniform mat4 uModel;

out vec2 vColorUv;
out vec2 vDepthUv;
out float vTintAlpha;
out vec4 vDepthData;
out float vAlphaCutoff;
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
    vTintAlpha = aTint.a;
    vDepthData = aDepthData;
    vAlphaCutoff = aDepthData.w;
    vPickId = aPickId;
    vTextureSlot = aTextureSlot;
    vFlags = aFlags;
    gl_Position = uProjection * uModel * vec4(position, 0.0, 1.0);
    gl_Position.z = aDepthData.x * 2.0 - 1.0;
}
