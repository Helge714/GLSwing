#version 330 core

in vec2 vTexCoord;

uniform sampler2D uTexture;
uniform float uOpacity;

out vec4 fragColor;

void main() {
    // Layer textures contain premultiplied color and alpha.
    fragColor = texture(uTexture, vTexCoord) * uOpacity;
}
