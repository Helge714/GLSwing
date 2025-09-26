package com.vingame.glswing.rendering.context;

public class GLProjection {
	public static final GLProjection DEFAULT_PROJECTION = new GLProjection();

	public final float[] projection;

	public GLProjection(float[] projection) {
		this.projection = projection;
	}

	private GLProjection() {
		this.projection = new float[] { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
	}

	public GLProjection scale(float sx, float sy) {
		return new GLProjection(GLProjectionUtils.multiply(projection, GLProjectionUtils.scale(sx, sy, 1)));
	}

	public GLProjection preScale(float sx, float sy) {
		return new GLProjection(GLProjectionUtils.multiply(GLProjectionUtils.scale(sx, sy, 1), projection));
	}

	public GLProjection translate(float dx, float dy) {
		return new GLProjection(GLProjectionUtils.multiply(projection, GLProjectionUtils.translate(dx, dy, 0)));
	}

	public GLProjection preTranslate(float dx, float dy) {
		return new GLProjection(GLProjectionUtils.multiply(GLProjectionUtils.translate(dx, dy, 0), projection));
	}

	public GLProjection rotate(float rad, float x, float y) {
		return new GLProjection(GLProjectionUtils.multiply(projection, GLProjectionUtils.rotate(rad, x, y)));
	}

	public GLProjection rotate(float rad) {
		return new GLProjection(GLProjectionUtils.multiply(projection, GLProjectionUtils.rotate(rad)));
	}

	public GLProjection preRotate(float rad, float x, float y) {
		return new GLProjection(GLProjectionUtils.multiply(GLProjectionUtils.rotate(rad, x, y), projection));
	}

	public GLProjection preRotate(float rad) {
		return new GLProjection(GLProjectionUtils.multiply(GLProjectionUtils.rotate(rad), projection));
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof GLProjection other))
			return false;
		if (other == this)
			return true;
		for (int i = 0; i < 16; i++) {
			if (this.projection[i] != other.projection[i]) {
				return false;
			}
		}
		return true;
	}

}
