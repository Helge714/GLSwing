#version 130

uniform sampler2D u_texture;

in vec2 v_texcoord;
in vec4 v_color;

out vec4 fragColor;

void main() {
	vec4 tex = texture(u_texture, v_texcoord) * v_color;
	if (tex.a <= 0.01)
		discard;
	fragColor = tex;
}