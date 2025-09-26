#version 130

uniform mat4 u_Projection;

in vec2 a_position;
in vec4 a_color;
in uint a_id;

flat out uint v_id;
out vec4 v_color;

void main() {
	v_id = a_id;
	v_color = a_color;
	vec4 pos = vec4(a_position, 0.0, 1.0);
	gl_Position = u_Projection * pos;
}
