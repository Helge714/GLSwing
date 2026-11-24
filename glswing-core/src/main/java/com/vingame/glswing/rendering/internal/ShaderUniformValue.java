package com.vingame.glswing.rendering.internal;

import java.util.Arrays;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.ShaderProgram;

/** Immutable internal value for a user-defined shader uniform. */
public final class ShaderUniformValue {

	private final Kind kind;
	private final float[] floats;
	private final int integer;

	private ShaderUniformValue(Kind kind, float[] floats, int integer) {
		this.kind = kind;
		this.floats = floats;
		this.integer = integer;
	}

	public static ShaderUniformValue of(float value) {
		requireFinite(value);
		return new ShaderUniformValue(Kind.FLOAT, new float[] { value }, 0);
	}

	public static ShaderUniformValue of(float x, float y) {
		requireFinite(x, y);
		return new ShaderUniformValue(Kind.VEC2, new float[] { x, y }, 0);
	}

	public static ShaderUniformValue of(float x, float y, float z) {
		requireFinite(x, y, z);
		return new ShaderUniformValue(Kind.VEC3, new float[] { x, y, z }, 0);
	}

	public static ShaderUniformValue of(float x, float y, float z, float w) {
		requireFinite(x, y, z, w);
		return new ShaderUniformValue(Kind.VEC4, new float[] { x, y, z, w }, 0);
	}

	public static ShaderUniformValue of(int value) {
		return new ShaderUniformValue(Kind.INTEGER, null, value);
	}

	public static ShaderUniformValue matrix4(float[] matrix) {
		if (matrix == null || matrix.length != 16)
			throw new IllegalArgumentException("A mat4 uniform requires exactly 16 values");
		requireFinite(matrix);
		return new ShaderUniformValue(Kind.MAT4, matrix.clone(), 0);
	}

	public boolean acceptsGLType(int glType) {
		return switch (kind) {
		case FLOAT -> glType == GL.GL_FLOAT;
		case VEC2 -> glType == GL2ES2.GL_FLOAT_VEC2;
		case VEC3 -> glType == GL2ES2.GL_FLOAT_VEC3;
		case VEC4 -> glType == GL2ES2.GL_FLOAT_VEC4;
		case INTEGER -> glType == GL2ES2.GL_INT || glType == GL2ES2.GL_BOOL;
		case MAT4 -> glType == GL2ES2.GL_FLOAT_MAT4;
		};
	}

	public String typeName() {
		return switch (kind) {
		case FLOAT -> "float";
		case VEC2 -> "vec2";
		case VEC3 -> "vec3";
		case VEC4 -> "vec4";
		case INTEGER -> "int or bool";
		case MAT4 -> "mat4";
		};
	}

	public void apply(GL3 gl, ShaderProgram program, String uniformName) {
		switch (kind) {
		case FLOAT -> program.setFloat(gl, uniformName, floats[0]);
		case VEC2 -> program.setVector2(gl, uniformName, floats[0], floats[1]);
		case VEC3 -> program.setVector3(gl, uniformName, floats[0], floats[1], floats[2]);
		case VEC4 -> program.setVector4(gl, uniformName, floats[0], floats[1], floats[2], floats[3]);
		case INTEGER -> program.setInteger(gl, uniformName, integer);
		case MAT4 -> program.setMatrix4(gl, uniformName, floats);
		}
	}

	@Override
	public boolean equals(Object object) {
		if (this == object)
			return true;
		if (!(object instanceof ShaderUniformValue other))
			return false;
		return kind == other.kind && integer == other.integer && Arrays.equals(floats, other.floats);
	}

	@Override
	public int hashCode() {
		return 31 * (31 * kind.hashCode() + integer) + Arrays.hashCode(floats);
	}

	private static void requireFinite(float... values) {
		for (float value : values) {
			if (!Float.isFinite(value))
				throw new IllegalArgumentException("Uniform float values must be finite");
		}
	}

	private enum Kind {
		FLOAT,
		VEC2,
		VEC3,
		VEC4,
		INTEGER,
		MAT4
	}
}
