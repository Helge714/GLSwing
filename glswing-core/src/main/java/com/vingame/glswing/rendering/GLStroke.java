package com.vingame.glswing.rendering;

import java.util.Arrays;
import java.util.Objects;

/** Immutable geometric style for stroked paths. */
public final class GLStroke {

	/** Shape placed at each end of an open stroked path. */
	public enum Cap {
		/** Ends exactly at the path endpoint. */
		BUTT,
		/** Extends by half the stroke width with a straight edge. */
		SQUARE,
		/** Extends by half the stroke width with a semicircle. */
		ROUND
	}

	/** Shape connecting two consecutive stroked path segments. */
	public enum Join {
		/** Extends both edges until they meet. */
		MITER,
		/** Cuts the outer corner off with a straight edge. */
		BEVEL,
		/** Connects the outer edges with an arc. */
		ROUND
	}

	/** Coordinate space used by width, offset and dash lengths. */
	public enum WidthSpace {
		/** Geometric stroke that follows model transforms and camera zoom. */
		WORLD,
		/** Cosmetic stroke measured in logical screen pixels. */
		SCREEN
	}

	/** Convenient curve-tolerance presets measured in logical screen pixels. */
	public static final float CURVE_TOLERANCE_COARSE = 0.4f;
	public static final float CURVE_TOLERANCE_NORMAL = 0.2f;
	public static final float CURVE_TOLERANCE_FINE = 0.1f;

	public static final GLStroke DEFAULT = new GLStroke(1);

	private final float width;
	private final Cap cap;
	private final Join join;
	private final float miterLimit;
	private final float offset;
	private final float curveTolerance;
	private final WidthSpace widthSpace;
	private final float[] dashArray;
	private final float dashPhase;

	public GLStroke(float width) {
		this(width, Cap.BUTT, Join.MITER, 10, 0, CURVE_TOLERANCE_NORMAL);
	}

	public GLStroke(float width, Cap cap, Join join, float miterLimit, float offset) {
		this(width, cap, join, miterLimit, offset, CURVE_TOLERANCE_NORMAL);
	}

	public GLStroke(float width, Cap cap, Join join, float miterLimit,
			float offset, float curveTolerance) {
		this(width, cap, join, miterLimit, offset, curveTolerance, WidthSpace.WORLD);
	}

	public GLStroke(float width, Cap cap, Join join, float miterLimit,
			float offset, float curveTolerance, WidthSpace widthSpace) {
		this(width, cap, join, miterLimit, offset, curveTolerance, widthSpace,
				null, 0);
	}

	public GLStroke(float width, Cap cap, Join join, float miterLimit,
			float offset, float curveTolerance, WidthSpace widthSpace,
			float[] dashArray, float dashPhase) {
		if (!Float.isFinite(width) || width <= 0)
			throw new IllegalArgumentException("Stroke width must be finite and greater than zero");
		if (cap == null)
			throw new IllegalArgumentException("Stroke cap must not be null");
		if (join == null)
			throw new IllegalArgumentException("Stroke join must not be null");
		if (!Float.isFinite(miterLimit) || miterLimit < 1)
			throw new IllegalArgumentException("Stroke miter limit must be finite and at least one");
		if (!Float.isFinite(offset))
			throw new IllegalArgumentException("Stroke offset must be finite");
		if (!Float.isFinite(curveTolerance) || curveTolerance <= 0)
			throw new IllegalArgumentException("Stroke curve tolerance must be finite and greater than zero");
		if (widthSpace == null)
			throw new IllegalArgumentException("Stroke width space must not be null");
		float[] validatedDash = validateDash(dashArray);
		if (!Float.isFinite(dashPhase) || dashPhase < 0)
			throw new IllegalArgumentException(
					"Stroke dash phase must be finite and non-negative");
		if (validatedDash == null && dashPhase != 0)
			throw new IllegalArgumentException(
					"A dash phase requires a dash array");

		this.width = width;
		this.cap = cap;
		this.join = join;
		this.miterLimit = miterLimit;
		this.offset = offset;
		this.curveTolerance = curveTolerance;
		this.widthSpace = widthSpace;
		this.dashArray = validatedDash;
		this.dashPhase = dashPhase;
	}

	public float getWidth() {
		return width;
	}

	public Cap getCap() {
		return cap;
	}

	public Join getJoin() {
		return join;
	}

	public float getMiterLimit() {
		return miterLimit;
	}

	/** Returns the center displacement in world units or logical screen pixels. */
	public float getOffset() {
		return offset;
	}

	/** Returns the maximum visible curve-to-line deviation in logical screen pixels. */
	public float getCurveTolerance() {
		return curveTolerance;
	}

	public WidthSpace getWidthSpace() {
		return widthSpace;
	}

	/**
	 * Returns alternating visible and empty lengths as a defensive copy, or
	 * {@code null} for a solid stroke. An odd-sized pattern is repeated, matching
	 * the usual {@code BasicStroke} pattern semantics.
	 */
	public float[] getDashArray() {
		return dashArray == null ? null : dashArray.clone();
	}

	/** Returns the distance consumed from the dash pattern before the path starts. */
	public float getDashPhase() {
		return dashPhase;
	}

	public boolean isDashed() {
		return dashArray != null;
	}

	public GLStroke withWidth(float width) {
		return copy(width, cap, join, miterLimit, offset, curveTolerance,
				widthSpace, dashArray, dashPhase);
	}

	public GLStroke withCap(Cap cap) {
		return copy(width, cap, join, miterLimit, offset, curveTolerance,
				widthSpace, dashArray, dashPhase);
	}

	public GLStroke withJoin(Join join) {
		return copy(width, cap, join, miterLimit, offset, curveTolerance,
				widthSpace, dashArray, dashPhase);
	}

	public GLStroke withMiterLimit(float miterLimit) {
		return copy(width, cap, join, miterLimit, offset, curveTolerance,
				widthSpace, dashArray, dashPhase);
	}

	public GLStroke withOffset(float offset) {
		return copy(width, cap, join, miterLimit, offset, curveTolerance,
				widthSpace, dashArray, dashPhase);
	}

	public GLStroke withCurveTolerance(float curveTolerance) {
		return copy(width, cap, join, miterLimit, offset, curveTolerance,
				widthSpace, dashArray, dashPhase);
	}

	public GLStroke withWidthSpace(WidthSpace widthSpace) {
		return copy(width, cap, join, miterLimit, offset, curveTolerance,
				widthSpace, dashArray, dashPhase);
	}

	/** Creates a dashed copy whose first path point starts in the first visible run. */
	public GLStroke withDash(float... dashArray) {
		return withDash(dashArray, 0);
	}

	/** Creates a dashed copy with alternating visible/empty lengths and phase. */
	public GLStroke withDash(float[] dashArray, float dashPhase) {
		return copy(width, cap, join, miterLimit, offset, curveTolerance,
				widthSpace, dashArray, dashPhase);
	}

	public GLStroke withDashPhase(float dashPhase) {
		if (dashArray == null)
			throw new IllegalStateException(
					"Cannot set a dash phase on a solid stroke");
		return withDash(dashArray, dashPhase);
	}

	public GLStroke withoutDash() {
		return copy(width, cap, join, miterLimit, offset, curveTolerance,
				widthSpace, null, 0);
	}

	@Override
	public boolean equals(Object object) {
		if (this == object)
			return true;
		if (!(object instanceof GLStroke other))
			return false;
		return Float.compare(width, other.width) == 0
				&& cap == other.cap
				&& join == other.join
				&& Float.compare(miterLimit, other.miterLimit) == 0
				&& Float.compare(offset, other.offset) == 0
				&& Float.compare(curveTolerance, other.curveTolerance) == 0
				&& widthSpace == other.widthSpace
				&& Arrays.equals(dashArray, other.dashArray)
				&& Float.compare(dashPhase, other.dashPhase) == 0;
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(width, cap, join, miterLimit, offset,
				curveTolerance, widthSpace, dashPhase);
		return 31 * result + Arrays.hashCode(dashArray);
	}

	@Override
	public String toString() {
		return "GLStroke[width=" + width + ", cap=" + cap + ", join=" + join
				+ ", miterLimit=" + miterLimit + ", offset=" + offset
				+ ", curveTolerance=" + curveTolerance + ", widthSpace=" + widthSpace
				+ ", dashArray=" + Arrays.toString(dashArray)
				+ ", dashPhase=" + dashPhase + "]";
	}

	private static GLStroke copy(float width, Cap cap, Join join,
			float miterLimit, float offset, float curveTolerance,
			WidthSpace widthSpace, float[] dashArray, float dashPhase) {
		return new GLStroke(width, cap, join, miterLimit, offset,
				curveTolerance, widthSpace, dashArray, dashPhase);
	}

	private static float[] validateDash(float[] dashArray) {
		if (dashArray == null)
			return null;
		if (dashArray.length == 0)
			throw new IllegalArgumentException(
					"Stroke dash array must not be empty");
		float[] copy = dashArray.clone();
		for (float length : copy)
			if (!Float.isFinite(length) || length <= 0)
				throw new IllegalArgumentException(
						"Stroke dash lengths must be finite and greater than zero");
		return copy;
	}
}
