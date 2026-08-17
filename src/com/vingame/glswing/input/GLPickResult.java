package com.vingame.glswing.input;

import java.awt.geom.Point2D;

/** Result of resolving one pixel from the GPU picking buffer. */
public final class GLPickResult {

	private static final GLPickResult NONE = new GLPickResult(
			0, null, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
			Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN);

	private final int id;
	private final GLMouseTarget target;
	private final float screenX;
	private final float screenY;
	private final float localX;
	private final float localY;
	private final float inverseM00;
	private final float inverseM10;
	private final float inverseM01;
	private final float inverseM11;
	private final float inverseM02;
	private final float inverseM12;

	/** Internal record constructor; applications normally obtain results from GLSwingPanel. */
	public GLPickResult(int id, GLMouseTarget target, float screenX, float screenY,
			float localX, float localY, float inverseM00, float inverseM10,
			float inverseM01, float inverseM11, float inverseM02, float inverseM12) {
		this.id = id;
		this.target = target;
		this.screenX = screenX;
		this.screenY = screenY;
		this.localX = localX;
		this.localY = localY;
		this.inverseM00 = inverseM00;
		this.inverseM10 = inverseM10;
		this.inverseM01 = inverseM01;
		this.inverseM11 = inverseM11;
		this.inverseM02 = inverseM02;
		this.inverseM12 = inverseM12;
	}

	public static GLPickResult none() {
		return NONE;
	}

	public boolean isHit() {
		return id != 0 || target != null;
	}

	public boolean hasId() {
		return id != 0;
	}

	/** Returns the user supplied integer ID, or zero for a target-only hit. */
	public int getId() {
		return id;
	}

	public boolean hasTarget() {
		return target != null;
	}

	public GLMouseTarget getTarget() {
		return target;
	}

	public <T extends GLMouseTarget> T getTarget(Class<T> type) {
		if (type == null)
			throw new IllegalArgumentException("Target type must not be null");
		return type.isInstance(target) ? type.cast(target) : null;
	}

	public float getScreenX() {
		return screenX;
	}

	public float getScreenY() {
		return screenY;
	}

	/** True when the transform used for drawing was invertible. */
	public boolean hasLocalPosition() {
		return Float.isFinite(localX) && Float.isFinite(localY);
	}

	/** Returns x relative to the target/draw-command origin. */
	public float getLocalX() {
		return localX;
	}

	/** Returns y relative to the target/draw-command origin. */
	public float getLocalY() {
		return localY;
	}

	/** Converts an object-local point through the transform captured by this hit. */
	public Point2D.Float toScreen(float targetLocalX, float targetLocalY) {
		float determinant = inverseM00 * inverseM11 - inverseM01 * inverseM10;
		if (!Float.isFinite(determinant) || Math.abs(determinant) < 0.0000001f)
			throw new IllegalStateException("Captured picking transform is not invertible");
		float translatedX = targetLocalX - inverseM02;
		float translatedY = targetLocalY - inverseM12;
		float resultX = (inverseM11 * translatedX
				- inverseM01 * translatedY) / determinant;
		float resultY = (-inverseM10 * translatedX
				+ inverseM00 * translatedY) / determinant;
		return new Point2D.Float(resultX, resultY);
	}

	/** Re-evaluates local coordinates using the transform captured by this hit. */
	public GLPickResult at(float newScreenX, float newScreenY) {
		if (!isHit())
			return NONE;
		float newLocalX = inverseM00 * newScreenX + inverseM01 * newScreenY + inverseM02;
		float newLocalY = inverseM10 * newScreenX + inverseM11 * newScreenY + inverseM12;
		return new GLPickResult(id, target, newScreenX, newScreenY, newLocalX, newLocalY,
				inverseM00, inverseM10, inverseM01, inverseM11, inverseM02, inverseM12);
	}
}
