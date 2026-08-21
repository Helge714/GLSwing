#version 330 core

flat in uint vPickId;
in float vAlpha;

uniform float uAlphaThreshold;

layout(location = 0) out uint outPickId;

void main() {
    if (vPickId == uint(0) || vAlpha < uAlphaThreshold)
        discard;
    outPickId = vPickId;
}
