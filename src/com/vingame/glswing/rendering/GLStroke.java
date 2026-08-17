package com.vingame.glswing.rendering;

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

	/** Coordinate space used by width and offset. */
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

		this.width = width;
		this.cap = cap;
		this.join = join;
		this.miterLimit = miterLimit;
		this.offset = offset;
		this.curveTolerance = curveTolerance;
		this.widthSpace = widthSpace;
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

	public GLStroke withWidth(float width) {
		return new GLStroke(width, cap, join, miterLimit, offset, curveTolerance, widthSpace);
	}

	public GLStroke withCap(Cap cap) {
		return new GLStroke(width, cap, join, miterLimit, offset, curveTolerance, widthSpace);
	}

	public GLStroke withJoin(Join join) {
		return new GLStroke(width, cap, join, miterLimit, offset, curveTolerance, widthSpace);
	}

	public GLStroke withMiterLimit(float miterLimit) {
		return new GLStroke(width, cap, join, miterLimit, offset, curveTolerance, widthSpace);
	}

	public GLStroke withOffset(float offset) {
		return new GLStroke(width, cap, join, miterLimit, offset, curveTolerance, widthSpace);
	}

	public GLStroke withCurveTolerance(float curveTolerance) {
		return new GLStroke(width, cap, join, miterLimit, offset, curveTolerance, widthSpace);
	}

	public GLStroke withWidthSpace(WidthSpace widthSpace) {
		return new GLStroke(width, cap, join, miterLimit, offset, curveTolerance, widthSpace);
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
				&& widthSpace == other.widthSpace;
	}

	@Override
	public int hashCode() {
		return Objects.hash(width, cap, join, miterLimit, offset, curveTolerance, widthSpace);
	}

	@Override
	public String toString() {
		return "GLStroke[width=" + width + ", cap=" + cap + ", join=" + join
				+ ", miterLimit=" + miterLimit + ", offset=" + offset
				+ ", curveTolerance=" + curveTolerance + ", widthSpace=" + widthSpace + "]";
	}
}
