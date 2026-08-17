#version 330 core

in vec2 vLocal;
in vec2 vTexCoord;
in float vAlpha;
flat in vec4 vParameters;
flat in vec2 vSize;
flat in uint vPickId;
flat in vec4 vSecondaryColor;

uniform sampler2D uTexture;
uniform float uTextAlphaThreshold;
uniform float uTextureAlphaThreshold;
uniform vec2 uDeviceScale;

layout(location = 0) out uint outPickId;

float roundedDistance(vec2 local, vec2 size, float requestedRadius) {
    vec2 halfSize = size * 0.5;
    float radius = min(max(requestedRadius, 0.0), min(halfSize.x, halfSize.y));
    vec2 q = abs(local - halfSize) - halfSize + vec2(radius);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

float asymmetricRoundedDistance(vec2 local, vec2 size, vec4 radii) {
    vec2 halfSize = size * 0.5;
    bool right = local.x > halfSize.x;
    bool bottom = local.y > halfSize.y;
    float requestedRadius = bottom
            ? (right ? radii.z : radii.w)
            : (right ? radii.y : radii.x);
    float radius = min(max(requestedRadius, 0.0), min(halfSize.x, halfSize.y));
    vec2 q = abs(local - halfSize) - halfSize + vec2(radius);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

float edgeCoverage(float distance) {
	float antialias = max(length(vec2(dFdx(distance), dFdy(distance))), 0.0001);
    return 1.0 - smoothstep(-antialias, antialias, distance);
}

float resolvedStrokeWidth(float distance) {
	if (vParameters.w < 0.5)
		return vParameters.z;
	vec2 logicalDerivative = vec2(dFdx(distance), dFdy(distance)) * uDeviceScale;
	return vParameters.z * max(length(logicalDerivative), 0.0001);
}

float msdfCoverage() {
    vec3 msdf = texture(uTexture, vTexCoord).rgb;
    float distance = max(min(msdf.r, msdf.g),
            min(max(msdf.r, msdf.g), msdf.b)) - 0.5;
    vec2 unitRange = vec2(vParameters.w) / vec2(textureSize(uTexture, 0));
    vec2 screenTextureSize = vec2(1.0)
            / max(fwidth(vTexCoord), vec2(0.000001));
    float pixelRange = max(0.5 * dot(unitRange, screenTextureSize), 1.0);
    return clamp(distance * pixelRange + 0.5, 0.0, 1.0);
}

float segmentDistance(vec2 point, vec2 start, vec2 end) {
    vec2 segment = end - start;
    float projection = clamp(dot(point - start, segment)
            / max(dot(segment, segment), 0.000001), 0.0, 1.0);
    return length(point - (start + segment * projection));
}

float checkMarkCoverage() {
    vec2 first = vSize * vec2(0.16, 0.52);
    vec2 middle = vSize * vec2(0.40, 0.76);
    vec2 last = vSize * vec2(0.84, 0.22);
    float distance = min(segmentDistance(vLocal, first, middle),
            segmentDistance(vLocal, middle, last)) - vParameters.z * 0.5;
    return edgeCoverage(distance);
}

float chevronSegmentCoverage(vec2 start, vec2 end) {
	if (vParameters.w < 0.5) {
		float distance = segmentDistance(vLocal, start, end);
		return edgeCoverage(distance - vParameters.z * 0.5);
	}
	mat2 physicalToLocal = mat2(dFdx(vLocal), dFdy(vLocal));
	mat2 localToPhysical = inverse(physicalToLocal);
	vec2 safeDeviceScale = max(uDeviceScale, vec2(0.0001));
	vec2 pointScreen = (localToPhysical * vLocal) / safeDeviceScale;
	vec2 startScreen = (localToPhysical * start) / safeDeviceScale;
	vec2 endScreen = (localToPhysical * end) / safeDeviceScale;
	float distance = segmentDistance(pointScreen, startScreen, endScreen)
			- vParameters.z * 0.5;
	float antialias = 1.0 / max((safeDeviceScale.x + safeDeviceScale.y) * 0.5,
			0.0001);
	return 1.0 - smoothstep(-antialias, antialias, distance);
}

float chevronCoverage() {
	bool pointsRight = vParameters.y > 0.0;
	vec2 first = vSize * vec2(pointsRight ? 0.30 : 0.70, 0.18);
	vec2 middle = vSize * vec2(pointsRight ? 0.68 : 0.32, 0.50);
	vec2 last = vSize * vec2(pointsRight ? 0.30 : 0.70, 0.82);
	return max(chevronSegmentCoverage(first, middle),
			chevronSegmentCoverage(middle, last));
}

void main() {
    if (vPickId == uint(0))
        discard;
    int mode = int(vParameters.x + 0.5);
	if (mode == 5) {
		if (vAlpha < uTextAlphaThreshold)
			discard;
		outPickId = vPickId;
		return;
	}
    float coverage;
	float alpha = vAlpha;
    float threshold = uTextAlphaThreshold;
    if (mode == 0) {
        coverage = edgeCoverage(roundedDistance(vLocal, vSize, vParameters.y));
    } else if (mode == 1) {
        float distance = roundedDistance(vLocal, vSize, vParameters.y);
        coverage = max(0.0, edgeCoverage(distance)
				- edgeCoverage(distance + resolvedStrokeWidth(distance)));
    } else if (mode == 2) {
        coverage = msdfCoverage();
    } else if (mode == 3) {
        coverage = texture(uTexture, vTexCoord).a;
        threshold = uTextureAlphaThreshold;
	} else if (mode == 4) {
		coverage = checkMarkCoverage();
	} else if (mode == 7) {
		float outer = edgeCoverage(roundedDistance(vLocal, vSize, vParameters.y));
		float progress = clamp(vParameters.z, 0.0, 1.0);
		float progressCoverage = progress <= 0.0 ? 0.0
				: progress >= 1.0 ? 1.0
				: edgeCoverage(vLocal.x - vSize.x * progress);
		coverage = outer * progressCoverage;
		alpha = max(vAlpha, vSecondaryColor.a);
	} else if (mode == 8) {
		coverage = chevronCoverage();
	} else if (mode == 9) {
		coverage = edgeCoverage(asymmetricRoundedDistance(
				vLocal, vSize, vSecondaryColor));
	} else if (mode == 10) {
		float distance = asymmetricRoundedDistance(
				vLocal, vSize, vSecondaryColor);
		coverage = max(0.0, edgeCoverage(distance)
				- edgeCoverage(distance + resolvedStrokeWidth(distance)));
	} else if (mode == 11) {
		vec4 radii = vec4(vParameters.y, vParameters.y,
				vParameters.w, vParameters.w);
		float outer = edgeCoverage(asymmetricRoundedDistance(
				vLocal, vSize, radii));
		float progress = clamp(vParameters.z, 0.0, 1.0);
		float progressCoverage = progress <= 0.0 ? 0.0
				: progress >= 1.0 ? 1.0
				: edgeCoverage(vLocal.x - vSize.x * progress);
		coverage = outer * progressCoverage;
		alpha = max(vAlpha, vSecondaryColor.a);
	} else {
		coverage = edgeCoverage(roundedDistance(vLocal, vSize, vParameters.y));
		alpha = max(vAlpha, vSecondaryColor.a);
    }
	if (alpha * coverage < threshold)
        discard;
    outPickId = vPickId;
}
