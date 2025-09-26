#version 130

uniform sampler2D u_texture;

in vec2 v_texcoord;
flat in uint v_id;

out uint fragID;

void main() {
	vec4 tex = texture(u_texture, v_texcoord);
	if (tex.a <= 0.01)
		discard;
	fragID = v_id;
}