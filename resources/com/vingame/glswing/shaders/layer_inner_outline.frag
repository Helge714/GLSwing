#version 330 core

in vec2 vTexCoord;

uniform sampler2D uTexture;
uniform float uOpacity;
uniform vec2 uTexelSize;
uniform vec2 uDeviceScale;
uniform float uWidth;
uniform vec4 uOutlineColor;

out vec4 fragColor;

float alphaAt(vec2 position) {
    return texture(uTexture, position).a;
}

void main() {
    vec4 source = texture(uTexture, vTexCoord);
    if (source.a <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    float feather = 0.5 / min(uDeviceScale.x, uDeviceScale.y);
    ivec2 pixelRadius = ivec2(ceil((uWidth + feather) * uDeviceScale));
    float erodedAlpha = source.a;

    for (int y = -pixelRadius.y; y <= pixelRadius.y; y++) {
        for (int x = -pixelRadius.x; x <= pixelRadius.x; x++) {
            vec2 logicalOffset = vec2(x, y) / uDeviceScale;
            float distance = length(logicalOffset);
            if (distance > uWidth + feather)
                continue;
            float kernelCoverage = 1.0 - smoothstep(
                    uWidth - feather, uWidth + feather, distance);
            float constrainedAlpha = mix(1.0,
                    alphaAt(vTexCoord + vec2(x, y) * uTexelSize),
                    kernelCoverage);
            erodedAlpha = min(erodedAlpha, constrainedAlpha);
        }
    }

    float innerCoverage = max(source.a - erodedAlpha, 0.0);
    float colorStrength = innerCoverage / source.a * uOutlineColor.a;
    vec3 colored = mix(source.rgb, uOutlineColor.rgb * source.a,
            colorStrength);
    fragColor = vec4(colored, source.a) * uOpacity;
}
