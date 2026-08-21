#version 330 core

in vec2 vTexCoord;

uniform sampler2D uTexture;
uniform sampler2D uBlurTexture;
uniform float uOpacity;
uniform vec2 uTexelSize;
uniform vec2 uDeviceScale;
uniform vec2 uOffset;
uniform float uRadius;
uniform vec2 uDirection;
uniform vec4 uShadowColor;

out vec4 fragColor;

void main() {
    vec4 source = texture(uTexture, vTexCoord);
    if (source.a <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }

    // Positive offsets place the inner shadow at the right/bottom edge.
    vec2 uvOffset = vec2(uOffset.x, -uOffset.y)
            * uTexelSize * uDeviceScale;
    vec2 center = vTexCoord + uvOffset;
    float scale = dot(abs(uDirection), uDeviceScale);
    float physicalRadius = uRadius * scale;
    int sampleRadius = int(ceil(physicalRadius));
    float sigma = max(physicalRadius / 2.0, 0.5);
    float inverseTwoSigmaSquared = 0.5 / (sigma * sigma);
    float accumulatedAlpha = texture(uBlurTexture, center).a;
    float totalWeight = 1.0;

    for (int offset = 1; offset <= sampleRadius; offset += 2) {
        float firstWeight = exp(-float(offset * offset) * inverseTwoSigmaSquared);
        int secondOffset = offset + 1;
        float secondWeight = secondOffset <= sampleRadius
                ? exp(-float(secondOffset * secondOffset) * inverseTwoSigmaSquared)
                : 0.0;
        float pairWeight = firstWeight + secondWeight;
        float sampleOffset = (float(offset) * firstWeight
                + float(secondOffset) * secondWeight) / pairWeight;
        vec2 displacement = uDirection * uTexelSize * sampleOffset;
        accumulatedAlpha += (texture(uBlurTexture, center + displacement).a
                + texture(uBlurTexture, center - displacement).a) * pairWeight;
        totalWeight += 2.0 * pairWeight;
    }

    float blurredAlpha = accumulatedAlpha / totalWeight;
    float shadowStrength = (1.0 - blurredAlpha) * uShadowColor.a;
    vec3 shadowed = mix(source.rgb, uShadowColor.rgb * source.a,
            shadowStrength);
    fragColor = vec4(shadowed, source.a) * uOpacity;
}
