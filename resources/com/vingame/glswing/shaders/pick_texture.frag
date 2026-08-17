#version 330 core

in vec2 vTexCoord;
in float vAlpha;
flat in uint vPickId;

uniform sampler2D uTexture;
uniform float uAlphaThreshold;

layout(location = 0) out uint outPickId;

void main() {
    float alpha = texture(uTexture, vTexCoord).a * vAlpha;
    if (vPickId == uint(0) || alpha < uAlphaThreshold)
        discard;
    outPickId = vPickId;
}
