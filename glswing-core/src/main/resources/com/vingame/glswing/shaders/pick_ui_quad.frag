#version 330 core

in vec2 vLocal;
in vec2 vTexCoord;
in float vAlpha;
flat in vec4 vParameters;
flat in vec2 vSize;
flat in uint vPickId;
flat in vec4 vSecondaryColor;
flat in int vTextureSlot;
flat in vec4 vAux;
flat in vec4 vTertiaryColor;
flat in vec4 vClipRect;
in vec2 vScreenPosition;

uniform sampler2D uTextures[16];
uniform float uTextAlphaThreshold;
uniform float uTextureAlphaThreshold;
uniform vec2 uDeviceScale;

layout(location = 0) out uint outPickId;

vec4 sampleTexture(int slot, vec2 uv) {
    switch (slot) {
    case 0: return texture(uTextures[0], uv);
    case 1: return texture(uTextures[1], uv);
    case 2: return texture(uTextures[2], uv);
    case 3: return texture(uTextures[3], uv);
    case 4: return texture(uTextures[4], uv);
    case 5: return texture(uTextures[5], uv);
    case 6: return texture(uTextures[6], uv);
    case 7: return texture(uTextures[7], uv);
    case 8: return texture(uTextures[8], uv);
    case 9: return texture(uTextures[9], uv);
    case 10: return texture(uTextures[10], uv);
    case 11: return texture(uTextures[11], uv);
    case 12: return texture(uTextures[12], uv);
    case 13: return texture(uTextures[13], uv);
    case 14: return texture(uTextures[14], uv);
    default: return texture(uTextures[15], uv);
    }
}

vec2 texturePixelSize(int slot) {
    switch (slot) {
    case 0: return vec2(textureSize(uTextures[0], 0));
    case 1: return vec2(textureSize(uTextures[1], 0));
    case 2: return vec2(textureSize(uTextures[2], 0));
    case 3: return vec2(textureSize(uTextures[3], 0));
    case 4: return vec2(textureSize(uTextures[4], 0));
    case 5: return vec2(textureSize(uTextures[5], 0));
    case 6: return vec2(textureSize(uTextures[6], 0));
    case 7: return vec2(textureSize(uTextures[7], 0));
    case 8: return vec2(textureSize(uTextures[8], 0));
    case 9: return vec2(textureSize(uTextures[9], 0));
    case 10: return vec2(textureSize(uTextures[10], 0));
    case 11: return vec2(textureSize(uTextures[11], 0));
    case 12: return vec2(textureSize(uTextures[12], 0));
    case 13: return vec2(textureSize(uTextures[13], 0));
    case 14: return vec2(textureSize(uTextures[14], 0));
    default: return vec2(textureSize(uTextures[15], 0));
    }
}

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

float asymmetricCornerRadius(vec2 local, vec2 size, vec4 radii) {
	bool right = local.x > size.x * 0.5;
	bool bottom = local.y > size.y * 0.5;
	return bottom ? (right ? radii.z : radii.w)
			: (right ? radii.y : radii.x);
}

float insetRoundedDistance(vec2 local, vec2 size, float radius, float inset) {
	vec2 innerSize = max(size - vec2(inset * 2.0), vec2(0.0001));
	return roundedDistance(local - vec2(inset), innerSize,
			max(radius - inset, 0.0));
}

float insetAsymmetricRoundedDistance(vec2 local, vec2 size, vec4 radii,
		float inset) {
	vec2 innerSize = max(size - vec2(inset * 2.0), vec2(0.0001));
	return asymmetricRoundedDistance(local - vec2(inset), innerSize,
			max(radii - vec4(inset), vec4(0.0)));
}

float edgeCoverage(float distance) {
	float antialias = max(length(vec2(dFdx(distance), dFdy(distance))), 0.0001);
    return 1.0 - smoothstep(-antialias, antialias, distance);
}

float squareInsetCoverage(vec2 inset) {
	vec2 width = fwidth(vLocal);
	vec2 lower = smoothstep(inset - width, inset + width, vLocal);
	vec2 upperDistance = vSize - vLocal;
	vec2 upper = smoothstep(inset - width, inset + width, upperDistance);
	return min(min(lower.x, lower.y), min(upper.x, upper.y));
}

vec2 resolvedSquareStrokeInset() {
	if (vParameters.w < 0.5)
		return vec2(vParameters.z);
	vec2 xDerivative = vec2(dFdx(vLocal.x), dFdy(vLocal.x)) * uDeviceScale;
	vec2 yDerivative = vec2(dFdx(vLocal.y), dFdy(vLocal.y)) * uDeviceScale;
	return vParameters.z * max(vec2(length(xDerivative), length(yDerivative)),
			vec2(0.0001));
}

float resolvedStrokeWidth(float distance) {
	if (vParameters.w < 0.5)
		return vParameters.z;
	vec2 logicalDerivative = vec2(dFdx(distance), dFdy(distance)) * uDeviceScale;
	return vParameters.z * max(length(logicalDerivative), 0.0001);
}

float msdfCoverage() {
    vec3 msdf = sampleTexture(vTextureSlot, vTexCoord).rgb;
    float distance = max(min(msdf.r, msdf.g),
            min(max(msdf.r, msdf.g), msdf.b)) - 0.5;
    vec2 unitRange = vec2(vParameters.w) / texturePixelSize(vTextureSlot);
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
	if (vScreenPosition.x < vClipRect.x || vScreenPosition.y < vClipRect.y
			|| vScreenPosition.x > vClipRect.z
			|| vScreenPosition.y > vClipRect.w)
		discard;
    if (vPickId == uint(0))
        discard;
	int mode = int(vParameters.x + 0.5);
	if (mode == 12)
		discard;
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
        coverage = vParameters.y <= 1.0 ? 1.0
				: edgeCoverage(roundedDistance(vLocal, vSize, vParameters.y));
    } else if (mode == 1) {
        float distance = roundedDistance(vLocal, vSize, vParameters.y);
		float strokeWidth = resolvedStrokeWidth(distance);
		float outer = vParameters.y <= 1.0 ? 1.0 : edgeCoverage(distance);
		float inner = vParameters.y <= 1.0
				? squareInsetCoverage(resolvedSquareStrokeInset())
				: edgeCoverage(insetRoundedDistance(vLocal, vSize,
						vParameters.y, strokeWidth));
		coverage = max(0.0, outer - inner);
    } else if (mode == 2) {
        coverage = msdfCoverage();
    } else if (mode == 3) {
        coverage = sampleTexture(vTextureSlot, vTexCoord).a;
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
		float strokeWidth = resolvedStrokeWidth(distance);
		coverage = max(0.0, edgeCoverage(distance) - edgeCoverage(
				insetAsymmetricRoundedDistance(vLocal, vSize,
						vSecondaryColor, strokeWidth)));
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
	} else if (mode == 13) {
		coverage = asymmetricCornerRadius(vLocal, vSize, vAux) <= 1.0 ? 1.0
				: edgeCoverage(asymmetricRoundedDistance(vLocal, vSize, vAux));
		alpha = max(vAlpha, vSecondaryColor.a);
	} else if (mode == 14) {
		float distance = roundedDistance(vLocal, vSize, vParameters.y);
		float outer = vParameters.y <= 1.0 ? 1.0
				: distance <= 0.0 ? 1.0 : 0.0;
		float progress = clamp(vParameters.z, 0.0, 1.0);
		float progressCoverage = progress <= 0.0 ? 0.0
				: progress >= 1.0 ? 1.0
				: edgeCoverage(vLocal.x - vSize.x * progress);
		coverage = outer * progressCoverage;
		alpha = max(vAlpha, vSecondaryColor.a);
	} else if (mode == 15) {
		coverage = vParameters.y <= 1.0 ? 1.0
				: edgeCoverage(roundedDistance(vLocal, vSize, vParameters.y));
		alpha = max(max(vAlpha, vSecondaryColor.a),
				max(vAux.a, vTertiaryColor.a));
	} else {
		coverage = edgeCoverage(roundedDistance(vLocal, vSize, vParameters.y));
		alpha = max(vAlpha, vSecondaryColor.a);
    }
	if (alpha * coverage < threshold)
        discard;
    outPickId = vPickId;
}
