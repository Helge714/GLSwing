#version 330 core

in vec2 vTexCoord;

uniform sampler2D uColorTexture;
uniform usampler2D uPickTexture;
uniform int uRecordBase;
uniform int uFallbackPickId;
uniform float uTintAlpha;
uniform float uAlphaThreshold;

layout(location = 0) out uint outPickId;

void main() {
	uint localId = texture(uPickTexture, vTexCoord).r;
	if (localId != uint(0)) {
		outPickId = uint(uRecordBase) + localId - uint(1);
		return;
	}

	// A target-local ID is authoritative even for an invisible UI hit area.
	// The color alpha threshold only governs the optional whole-target fallback.
	float alpha = texture(uColorTexture, vTexCoord).a * uTintAlpha;
	if (uFallbackPickId == 0 || alpha < uAlphaThreshold)
		discard;
	outPickId = uint(uFallbackPickId);
}
