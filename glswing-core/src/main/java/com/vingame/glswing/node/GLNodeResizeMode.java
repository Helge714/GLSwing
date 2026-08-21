package com.vingame.glswing.node;

/** Axes on which a {@link GLNode} may be resized by its edge handles. */
public enum GLNodeResizeMode {
	NONE(false, false),
	HORIZONTAL(true, false),
	VERTICAL(false, true),
	BOTH(true, true);

	private final boolean horizontal;
	private final boolean vertical;

	GLNodeResizeMode(boolean horizontal, boolean vertical) {
		this.horizontal = horizontal;
		this.vertical = vertical;
	}

	public boolean allowsHorizontal() { return horizontal; }
	public boolean allowsVertical() { return vertical; }
}
