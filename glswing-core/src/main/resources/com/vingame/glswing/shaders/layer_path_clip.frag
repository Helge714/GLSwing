#version 330 core

in vec2 vTexCoord;

uniform sampler2D uTexture;
uniform sampler2D uMaskTexture;
uniform float uOpacity;

out vec4 fragColor;

void main() {
	float coverage = texture(uMaskTexture, vTexCoord).r;
	fragColor = texture(uTexture, vTexCoord) * (uOpacity * coverage);
}
