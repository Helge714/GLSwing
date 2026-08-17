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
    // A completely opaque source pixel cannot gain any visible outer
    // coverage. Skip the circular neighborhood search for solid interiors.
    if (source.a >= 0.9999) {
        fragColor = source * uOpacity;
        return;
    }

    // Half a physical pixel on either side of the requested radius produces
    // a one-screen-pixel antialiasing transition, independent of HiDPI scale.
    float feather = 0.5 / min(uDeviceScale.x, uDeviceScale.y);
    ivec2 pixelRadius = ivec2(ceil((uWidth + feather) * uDeviceScale));
    float neighboringAlpha = source.a;

    for (int y = -pixelRadius.y; y <= pixelRadius.y; y++) {
        for (int x = -pixelRadius.x; x <= pixelRadius.x; x++) {
            vec2 logicalOffset = vec2(x, y) / uDeviceScale;
            float distance = length(logicalOffset);
            if (distance > uWidth + feather)
                continue;
            float kernelCoverage = 1.0 - smoothstep(
                    uWidth - feather, uWidth + feather, distance);
            neighboringAlpha = max(neighboringAlpha,
                    alphaAt(vTexCoord + vec2(x, y) * uTexelSize)
                            * kernelCoverage);
        }
    }

    // The difference is the newly dilated, genuinely outer coverage. Since it
    // no longer overlaps the source coverage, it must not be attenuated again.
    float outlineAlpha = max(neighboringAlpha - source.a, 0.0)
            * uOutlineColor.a;
    vec4 outline = vec4(uOutlineColor.rgb * outlineAlpha, outlineAlpha);
    fragColor = (source + outline) * uOpacity;
}
