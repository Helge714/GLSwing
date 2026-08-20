#version 330 core

in vec2 vTexCoord;
in vec4 vColor;

uniform sampler2D uTexture;
uniform vec2 uTexelSize;

out vec4 fragColor;

float alive(vec2 offset) {
    return step(0.5, texture(uTexture,
            vTexCoord + offset * uTexelSize).r);
}

void main() {
    float neighbors =
            alive(vec2(-1.0, -1.0)) + alive(vec2(0.0, -1.0))
          + alive(vec2( 1.0, -1.0)) + alive(vec2(-1.0, 0.0))
          + alive(vec2( 1.0,  0.0)) + alive(vec2(-1.0, 1.0))
          + alive(vec2( 0.0,  1.0)) + alive(vec2( 1.0, 1.0));
    float current = alive(vec2(0.0));
    float survives = current * step(1.5, neighbors) * step(neighbors, 3.5);
    float born = (1.0 - current) * step(2.5, neighbors) * step(neighbors, 3.5);
    float next = clamp(survives + born, 0.0, 1.0);
    fragColor = vec4(vec3(next), 1.0) * vColor;
}
