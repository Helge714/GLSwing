#version 330 core

in vec2 vColorUv;
in vec2 vDepthUv;
in vec2 vNormalUv;
in vec4 vTint;
in vec4 vDepthData;
in float vAlphaCutoff;
in vec2 vTangentX;
in vec2 vTangentY;
flat in int vTextureSlot;
flat in int vFlags;

uniform sampler2D uColorMaps[5];
uniform sampler2D uDepthMaps[5];
uniform sampler2D uNormalMaps[5];
uniform vec3 uAmbientColor;
uniform float uAmbientIntensity;
uniform vec3 uDirectionalDirection;
uniform vec3 uDirectionalColor;
uniform float uDirectionalIntensity;

out vec4 fragColor;

vec4 colorSample(int slot, vec2 uv) {
    switch (slot) {
    case 0: return texture(uColorMaps[0], uv);
    case 1: return texture(uColorMaps[1], uv);
    case 2: return texture(uColorMaps[2], uv);
    case 3: return texture(uColorMaps[3], uv);
    default: return texture(uColorMaps[4], uv);
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

vec3 normalSample(int slot, vec2 uv) {
    switch (slot) {
    case 0: return texture(uNormalMaps[0], uv).rgb;
    case 1: return texture(uNormalMaps[1], uv).rgb;
    case 2: return texture(uNormalMaps[2], uv).rgb;
    case 3: return texture(uNormalMaps[3], uv).rgb;
    default: return texture(uNormalMaps[4], uv).rgb;
    }
}

void main() {
    vec4 value = colorSample(vTextureSlot, vColorUv) * vTint;
    if ((vFlags & 8) != 0 && value.a < vAlphaCutoff)
        discard;

    float relief = (vFlags & 1) != 0
            ? depthSample(vTextureSlot, vDepthUv) : 0.5;
    gl_FragDepth = clamp(vDepthData.x
            + mix(vDepthData.y, vDepthData.z, relief), 0.0, 1.0);

    if ((vFlags & 6) == 6) {
        vec3 tangentNormal = normalize(
                normalSample(vTextureSlot, vNormalUv) * 2.0 - 1.0);
        vec3 worldNormal = normalize(vec3(
                vTangentX * tangentNormal.x
                + vTangentY * tangentNormal.y,
                tangentNormal.z));
        float diffuse = max(0.0, dot(worldNormal,
                normalize(uDirectionalDirection)));
        vec3 light = uAmbientColor * uAmbientIntensity
                + uDirectionalColor * uDirectionalIntensity * diffuse;
        value.rgb *= light;
    }
    fragColor = value;
}

