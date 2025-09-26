package com.vingame.glswing.rendering.context;

public class GLProjectionUtils {
	public static float[] ortho(float left, float right, float bottom, float top, float near, float far) {
		float[] m = new float[16];

		m[0] = 2.0f / (right - left);
		m[5] = 2.0f / (top - bottom);
		m[10] = -2.0f / (far - near);
		m[12] = -(right + left) / (right - left);
		m[13] = -(top + bottom) / (top - bottom);
		m[14] = -(far + near) / (far - near);
		m[15] = 1.0f;

		return m;
	}

	public static float[] scale(float sx, float sy, float sz) {
		float[] m = new float[16];
		m[0] = sx;
		m[5] = sy;
		m[10] = sz;
		m[15] = 1.0f;
		return m;
	}

	public static float[] translate(float dx, float dy, float dz) {
		float[] m = new float[16];
		m[0] = 1;
		m[5] = 1;
		m[10] = 1;
		m[15] = 1;
		m[12] = dx;
		m[13] = dy;
		m[14] = dz;
		return m;
	}

	public static float[] rotate(float angleRad) {
		float[] m = new float[16];
		float c = (float) Math.cos(angleRad);
		float s = (float) Math.sin(angleRad);

		m[0] = c;
		m[4] = -s;
		m[8] = 0;
		m[12] = 0;
		m[1] = s;
		m[5] = c;
		m[9] = 0;
		m[13] = 0;
		m[2] = 0;
		m[6] = 0;
		m[10] = 1;
		m[14] = 0;
		m[3] = 0;
		m[7] = 0;
		m[11] = 0;
		m[15] = 1;

		return m;
	}

	public static float[] rotate(float angleRad, float px, float py) {
		return multiply(translate(-px, -py, 0), rotate(angleRad), translate(px, py, 0));
	}

	public static float[] multiply(float[]... f) {
		float[] result = f[0];
		for (int i = 1; i < f.length; i++) {
			result = multiply(result, f[i]);
		}
		return result;
	}

	public static float[] multiply(float[] a, float[] b) {
		float[] m = new float[16];
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 4; col++) {
				m[col + row * 4] = a[row * 4] * b[col] + a[row * 4 + 1] * b[col + 4] + a[row * 4 + 2] * b[col + 8]
						+ a[row * 4 + 3] * b[col + 12];
			}
		}
		return m;
	}
}
