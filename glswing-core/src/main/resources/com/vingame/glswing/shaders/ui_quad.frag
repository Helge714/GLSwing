#version 330 core

in vec2 vLocal;
in vec2 vTexCoord;
in vec4 vColor;
flat in vec4 vParameters;
flat in vec2 vSize;
flat in vec4 vSecondaryColor;
flat in int vTextureSlot;

uniform sampler2D uTextures[16];
uniform vec2 uDeviceScale;

out vec4 fragColor;

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
    int mode = int(vParameters.x + 0.5);
    float coverage;
    vec4 color = vColor;
    if (mode == 0) {
        coverage = edgeCoverage(roundedDistance(vLocal, vSize, vParameters.y));
    } else if (mode == 1) {
        float distance = roundedDistance(vLocal, vSize, vParameters.y);
        float outer = edgeCoverage(distance);
		float inner = edgeCoverage(distance + resolvedStrokeWidth(distance));
        coverage = max(0.0, outer - inner);
    } else if (mode == 2) {
        coverage = msdfCoverage();
    } else if (mode == 3) {
        vec4 sampled = sampleTexture(vTextureSlot, vTexCoord);
        color *= sampled;
        coverage = 1.0;
    } else if (mode == 4) {
        coverage = checkMarkCoverage();
	} else if (mode == 6) {
		float distance = roundedDistance(vLocal, vSize, vParameters.y);
		float outer = edgeCoverage(distance);
		float inner = edgeCoverage(distance + resolvedStrokeWidth(distance));
		float borderCoverage = max(0.0, outer - inner);
		float fillAlpha = vColor.a * inner;
		float borderAlpha = vSecondaryColor.a * borderCoverage;
		float combinedAlpha = fillAlpha + borderAlpha;
		vec3 premultiplied = vColor.rgb * fillAlpha
				+ vSecondaryColor.rgb * borderAlpha;
		vec3 combinedColor = combinedAlpha > 0.000001
				? premultiplied / combinedAlpha : vec3(0.0);
		fragColor = vec4(combinedColor, combinedAlpha);
		return;
	} else if (mode == 7) {
		float outer = edgeCoverage(roundedDistance(vLocal, vSize, vParameters.y));
		float progress = clamp(vParameters.z, 0.0, 1.0);
		float progressCoverage = progress <= 0.0 ? 0.0
				: progress >= 1.0 ? 1.0
				: edgeCoverage(vLocal.x - vSize.x * progress);
		coverage = outer * progressCoverage;
		color = mix(vColor, vSecondaryColor,
				clamp(vLocal.y / max(vSize.y, 0.0001), 0.0, 1.0));
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
		color = mix(vColor, vSecondaryColor,
				clamp(vLocal.y / max(vSize.y, 0.0001), 0.0, 1.0));
	} else if (mode == 12) {
		vec2 casterOrigin = vSecondaryColor.xy;
		vec2 casterSize = vSecondaryColor.zw;
		float distance = roundedDistance(vLocal - casterOrigin,
				casterSize, vParameters.y);
		float softness = max(vParameters.z, 0.0);
		coverage = softness > 0.0001
				? 1.0 - smoothstep(-softness, softness, distance)
				: edgeCoverage(distance);
    } else {
        discard;
    }
    fragColor = vec4(color.rgb, color.a * coverage);
}
