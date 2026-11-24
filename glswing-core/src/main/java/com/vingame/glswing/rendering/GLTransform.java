package com.vingame.glswing.rendering;

import java.util.Objects;

/** Mutable 2D affine transform using Graphics2D-compatible concatenation order. */
public final class GLTransform {

	private float m00;
	private float m10;
	private float m01;
	private float m11;
	private float m02;
	private float m12;

	/** Creates an identity transform. */
	public GLTransform() {
		setToIdentity();
	}

	/** Creates an independent copy of {@code transform}. */
	public GLTransform(GLTransform transform) {
		setTransform(transform);
	}

	public GLTransform(float m00, float m10, float m01,
			float m11, float m02, float m12) {
		setTransform(m00, m10, m01, m11, m02, m12);
	}

	public GLTransform setToIdentity() {
		m00 = 1;
		m10 = 0;
		m01 = 0;
		m11 = 1;
		m02 = 0;
		m12 = 0;
		return this;
	}

	public GLTransform setTransform(GLTransform transform) {
		if (transform == null)
			throw new IllegalArgumentException("Transform must not be null");
		return setTransform(transform.m00, transform.m10, transform.m01,
				transform.m11, transform.m02, transform.m12);
	}

	public GLTransform setTransform(float m00, float m10, float m01,
			float m11, float m02, float m12) {
		requireFinite(m00, m10, m01, m11, m02, m12);
		this.m00 = m00;
		this.m10 = m10;
		this.m01 = m01;
		this.m11 = m11;
		this.m02 = m02;
		this.m12 = m12;
		return this;
	}

	/** Appends a translation in the current user coordinate system. */
	public GLTransform translate(float x, float y) {
		requireFinite(x, y);
		m02 += m00 * x + m01 * y;
		m12 += m10 * x + m11 * y;
		return this;
	}

	/** Appends a clockwise-positive rotation in screen coordinates, measured in radians. */
	public GLTransform rotate(float radians) {
		if (!Float.isFinite(radians))
			throw new IllegalArgumentException("Rotation must be finite");
		float cosine = (float) Math.cos(radians);
		float sine = (float) Math.sin(radians);
		float oldM00 = m00;
		float oldM10 = m10;
		m00 = oldM00 * cosine + m01 * sine;
		m10 = oldM10 * cosine + m11 * sine;
		m01 = -oldM00 * sine + m01 * cosine;
		m11 = -oldM10 * sine + m11 * cosine;
		return this;
	}

	/** Rotates around an anchor point in the current user coordinate system. */
	public GLTransform rotate(float radians, float anchorX, float anchorY) {
		return translate(anchorX, anchorY)
				.rotate(radians)
				.translate(-anchorX, -anchorY);
	}

	public GLTransform scale(float scale) {
		return scale(scale, scale);
	}

	/** Appends a scale in the current user coordinate system. */
	public GLTransform scale(float scaleX, float scaleY) {
		requireFinite(scaleX, scaleY);
		m00 *= scaleX;
		m10 *= scaleX;
		m01 *= scaleY;
		m11 *= scaleY;
		return this;
	}

	/** Appends {@code transform}, equivalent to {@code this = this * transform}. */
	public GLTransform concatenate(GLTransform transform) {
		if (transform == null)
			throw new IllegalArgumentException("Transform must not be null");
		float resultM00 = m00 * transform.m00 + m01 * transform.m10;
		float resultM10 = m10 * transform.m00 + m11 * transform.m10;
		float resultM01 = m00 * transform.m01 + m01 * transform.m11;
		float resultM11 = m10 * transform.m01 + m11 * transform.m11;
		float resultM02 = m00 * transform.m02 + m01 * transform.m12 + m02;
		float resultM12 = m10 * transform.m02 + m11 * transform.m12 + m12;
		return setTransform(resultM00, resultM10, resultM01,
				resultM11, resultM02, resultM12);
	}

	/** Transforms {@code pointCount} interleaved x/y pairs in place. */
	public void transform(float[] points, int offset, int pointCount) {
		if (points == null)
			throw new IllegalArgumentException("Points must not be null");
		if (offset < 0 || pointCount < 0 || offset + pointCount * 2 > points.length)
			throw new IllegalArgumentException("Point range lies outside the array");
		for (int point = 0; point < pointCount; point++) {
			int index = offset + point * 2;
			float x = points[index];
			float y = points[index + 1];
			float transformedX = m00 * x + m01 * y + m02;
			float transformedY = m10 * x + m11 * y + m12;
			if (!Float.isFinite(transformedX) || !Float.isFinite(transformedY))
				throw new IllegalArgumentException("Transformed coordinate is not finite");
			points[index] = transformedX;
			points[index + 1] = transformedY;
		}
	}

	/** Transforms the leading x/y values of each interleaved vertex in place. */
	public void transformVertices(float[] vertices, int floatsPerVertex) {
		if (vertices == null)
			throw new IllegalArgumentException("Vertices must not be null");
		if (floatsPerVertex < 2 || vertices.length % floatsPerVertex != 0)
			throw new IllegalArgumentException("Vertex data has an invalid stride");
		for (int index = 0; index < vertices.length; index += floatsPerVertex) {
			float x = vertices[index];
			float y = vertices[index + 1];
			float transformedX = m00 * x + m01 * y + m02;
			float transformedY = m10 * x + m11 * y + m12;
			if (!Float.isFinite(transformedX) || !Float.isFinite(transformedY))
				throw new IllegalArgumentException("Transformed coordinate is not finite");
			vertices[index] = transformedX;
			vertices[index + 1] = transformedY;
		}
	}

	public float transformX(float x, float y) {
		return m00 * x + m01 * y + m02;
	}

	public float transformY(float x, float y) {
		return m10 * x + m11 * y + m12;
	}

	public boolean isIdentity() {
		return m00 == 1 && m10 == 0 && m01 == 0 && m11 == 1 && m02 == 0 && m12 == 0;
	}

	/** Returns whether the linear part is identity and only translation remains. */
	public boolean isTranslation() {
		return m00 == 1 && m10 == 0 && m01 == 0 && m11 == 1;
	}

	public float getScaleX() {
		return m00;
	}

	public float getShearY() {
		return m10;
	}

	public float getShearX() {
		return m01;
	}

	public float getScaleY() {
		return m11;
	}

	public float getTranslateX() {
		return m02;
	}

	public float getTranslateY() {
		return m12;
	}

	public GLTransform copy() {
		return new GLTransform(this);
	}

	public float determinant() {
		return m00 * m11 - m01 * m10;
	}

	/**
	 * Returns the largest factor by which the linear part can stretch a vector.
	 * This also handles rotation, shear and non-uniform scaling.
	 */
	public float getMaximumScale() {
		double a = m00;
		double b = m01;
		double c = m10;
		double d = m11;
		double trace = a * a + b * b + c * c + d * d;
		double determinant = a * d - b * c;
		double discriminant = Math.max(0, trace * trace - 4 * determinant * determinant);
		return (float) Math.sqrt((trace + Math.sqrt(discriminant)) / 2);
	}

	/** Returns the inverse affine transform, or fails if this transform is singular. */
	public GLTransform createInverse() {
		float determinant = determinant();
		if (determinant == 0 || !Float.isFinite(determinant))
			throw new IllegalStateException("Transform is not invertible");
		float inverseM00 = m11 / determinant;
		float inverseM10 = -m10 / determinant;
		float inverseM01 = -m01 / determinant;
		float inverseM11 = m00 / determinant;
		float inverseM02 = (m01 * m12 - m11 * m02) / determinant;
		float inverseM12 = (m10 * m02 - m00 * m12) / determinant;
		return new GLTransform(inverseM00, inverseM10, inverseM01,
				inverseM11, inverseM02, inverseM12);
	}

	/** Returns this affine transform as a column-major OpenGL 4x4 matrix. */
	public float[] toMatrix4() {
		return new float[] {
				m00, m10, 0, 0,
				m01, m11, 0, 0,
				0, 0, 1, 0,
				m02, m12, 0, 1
		};
	}

	@Override
	public boolean equals(Object object) {
		if (this == object)
			return true;
		if (!(object instanceof GLTransform other))
			return false;
		return Float.compare(m00, other.m00) == 0
				&& Float.compare(m10, other.m10) == 0
				&& Float.compare(m01, other.m01) == 0
				&& Float.compare(m11, other.m11) == 0
				&& Float.compare(m02, other.m02) == 0
				&& Float.compare(m12, other.m12) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(m00, m10, m01, m11, m02, m12);
	}

	@Override
	public String toString() {
		return "GLTransform[[" + m00 + ", " + m01 + ", " + m02
				+ "], [" + m10 + ", " + m11 + ", " + m12 + "]]";
	}

	private static void requireFinite(float... values) {
		for (float value : values) {
			if (!Float.isFinite(value))
				throw new IllegalArgumentException("Transform values must be finite");
		}
	}
}
