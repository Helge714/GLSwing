#version 130

uniform float u_align;
uniform float u_Thickness;
uniform mat4 u_Projection;
in vec2 a_position;
in vec2 a_prev;
in vec2 a_next;
in float a_side;
in vec4 a_color;
in uint a_id;

flat out uint v_id;
out vec4 v_color;

void main() {
	v_id = a_id;
	v_color = a_color;
	vec2 offset = vec2(0);
	vec2 normal = vec2(0);
	vec4 pos = vec4(a_position, 0.0, 1.0);
	
	float thickness = u_align * 2 * u_Thickness;
	if (a_side == 1)
		thickness = (1 - u_align) * 2 * u_Thickness;
	
	if ((a_position.x == a_prev.x && a_position.y == a_prev.y) || (a_position.x == a_next.x && a_position.y == a_next.y)) {
		vec2 dir = normalize(a_next - a_prev);
		normal = vec2(-dir.y, dir.x);
		float halfW = thickness / 2;
		offset = normal * halfW * a_side;
	} else {
		vec2 dir1 = normalize(a_position - a_prev);
		vec2 dir2 = normalize(a_next - a_position);
		vec2 normal1 = vec2(-dir1.y, dir1.x);
		vec2 normal2 = vec2(-dir2.y, dir2.x);

		normal = normalize(normal1 + normal2);
		float miterLen = thickness * 0.5 / max(dot(normal, normal1), 0.001);
		offset = normal * miterLen * a_side;
	}
	pos.xy += offset;
	gl_Position = u_Projection * pos;
}
