#version 330 core

in vec2 vTexCoord;

uniform sampler2D uTexture;
uniform float uOpacity;
uniform vec2 uDeviceScale;
uniform vec4 uClipBounds;
uniform vec4 uCornerRadii;

out vec4 fragColor;

float roundedDistance(vec2 local, vec2 size, vec4 radii) {
    vec2 halfSize = size * 0.5;
    bool right = local.x > halfSize.x;
    bool top = local.y > halfSize.y;
    float requestedRadius = top
            ? (right ? radii.y : radii.x)
            : (right ? radii.z : radii.w);
    float radius = min(max(requestedRadius, 0.0), min(halfSize.x, halfSize.y));
    vec2 q = abs(local - halfSize) - halfSize + vec2(radius);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

void main() {
    vec2 scale = max(uDeviceScale, vec2(0.0001));
    vec2 local = (gl_FragCoord.xy - uClipBounds.xy) / scale;
    vec2 size = uClipBounds.zw / scale;
    float distance = roundedDistance(local, size, uCornerRadii);
    float antialias = max(length(vec2(dFdx(distance), dFdy(distance))), 0.0001);
    float coverage = 1.0 - smoothstep(-antialias, antialias, distance);
    fragColor = texture(uTexture, vTexCoord) * (uOpacity * coverage);
}
