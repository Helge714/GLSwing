#version 130

in vec4 v_color;

out vec4 fragColor;

void main() {
    gl_FragColor = v_color;
}