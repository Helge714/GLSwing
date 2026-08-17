#version 330 core

in vec2 vTexCoord;

uniform sampler2D uTexture;
uniform float uOpacity;
uniform vec2 uTexelSize;
uniform vec2 uDeviceScale;
uniform float uRadius;
uniform vec2 uDirection;

out vec4 fragColor;

void main() {
    float scale = dot(abs(uDirection), uDeviceScale);
    float physicalRadius = uRadius * scale;
    if (physicalRadius <= 0.0) {
        fragColor = texture(uTexture, vTexCoord) * uOpacity;
        return;
    }

    int sampleRadius = int(ceil(physicalRadius));
    float sigma = max(physicalRadius / 2.0, 0.5);
    float inverseTwoSigmaSquared = 0.5 / (sigma * sigma);
    vec4 accumulated = texture(uTexture, vTexCoord);
    float totalWeight = 1.0;

    // Linear filtering combines each neighboring Gaussian pair into one tap.
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
        accumulated += (texture(uTexture, vTexCoord + displacement)
                + texture(uTexture, vTexCoord - displacement)) * pairWeight;
        totalWeight += 2.0 * pairWeight;
    }
    fragColor = accumulated * (uOpacity / totalWeight);
}
