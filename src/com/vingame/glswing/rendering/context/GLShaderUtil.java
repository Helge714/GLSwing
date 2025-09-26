package com.vingame.glswing.rendering.context;

import java.nio.IntBuffer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL3;

public class GLShaderUtil {

	public static int compileShader(GL2 gl, int type, String source) {
		int shader = gl.glCreateShader(type);

		String[] srcArray = new String[] { source };
		int[] lengths = new int[] { source.length() };
		gl.glShaderSource(shader, 1, srcArray, lengths, 0);
		gl.glCompileShader(shader);

		// Fehlerprüfung
		IntBuffer status = IntBuffer.allocate(1);
		gl.glGetShaderiv(shader, GL3.GL_COMPILE_STATUS, status);
		if (status.get(0) != GL.GL_TRUE) {
			IntBuffer infoLen = IntBuffer.allocate(1);
			gl.glGetShaderiv(shader, GL3.GL_INFO_LOG_LENGTH, infoLen);
			byte[] infoLog = new byte[infoLen.get(0)];
			gl.glGetShaderInfoLog(shader, infoLen.get(0), null, 0, infoLog, 0);
			throw new RuntimeException("Shader compile error: " + new String(infoLog));
		}

		return shader;
	}

	public static int createProgram(GL2 gl, String vertexSource, String fragmentSource) {
		int vertShader = compileShader(gl, GL2.GL_VERTEX_SHADER, vertexSource);
		int fragShader = compileShader(gl, GL2.GL_FRAGMENT_SHADER, fragmentSource);
		int program = gl.glCreateProgram();
		gl.glAttachShader(program, vertShader);
		gl.glAttachShader(program, fragShader);
		gl.glLinkProgram(program);

		int[] linked = new int[1];
		gl.glGetProgramiv(program, GL2.GL_LINK_STATUS, linked, 0);
		if (linked[0] == 0) {
			byte[] log = new byte[512];
			gl.glGetProgramInfoLog(program, 512, null, 0, log, 0);
			throw new RuntimeException("Program link error: " + new String(log));
		}
		return program;
	}

	public static int create_R32UI_Color_Program(GL2 gl, String vertexSource, String fragmentSource) {
		int vs = compileShader(gl, GL3.GL_VERTEX_SHADER, vertexSource);
		int fs = compileShader(gl, GL3.GL_FRAGMENT_SHADER, fragmentSource);

		int program = gl.glCreateProgram();
		gl.glAttachShader(program, vs);
		gl.glAttachShader(program, fs);
//			gl.glBindFragDataLocation(program, 0, "fragColor");
//			gl.glBindFragDataLocation(program, 0, "fragID");
		gl.glLinkProgram(program);

		// Fehlerprüfung
		IntBuffer status = IntBuffer.allocate(1);
		gl.glGetProgramiv(program, GL3.GL_LINK_STATUS, status);
		if (status.get(0) != GL.GL_TRUE) {
			IntBuffer infoLen = IntBuffer.allocate(1);
			gl.glGetProgramiv(program, GL3.GL_INFO_LOG_LENGTH, infoLen);
			byte[] infoLog = new byte[infoLen.get(0)];
			gl.glGetProgramInfoLog(program, infoLen.get(0), null, 0, infoLog, 0);
			throw new RuntimeException("Program link error: " + new String(infoLog));
		}

		// Shader-Objekte können nach dem Link gelöscht werden
		gl.glDeleteShader(vs);
		gl.glDeleteShader(fs);

		return program;
	}
}
