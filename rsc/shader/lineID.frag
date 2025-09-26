#version 130

flat in uint v_id;

out uint fragID;

void main() {
	fragID = v_id;	
}