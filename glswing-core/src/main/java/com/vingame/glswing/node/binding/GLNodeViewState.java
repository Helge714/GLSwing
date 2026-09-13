package com.vingame.glswing.node.binding;

import com.vingame.glswing.node.GLNode;

/** Rendering-only bounds of one node, stored separately from graph semantics. */
public record GLNodeViewState(float x, float y, float width, float height) {
	public GLNodeViewState {
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width)
				|| !Float.isFinite(height) || width < 0 || height < 0)
			throw new IllegalArgumentException("Node view bounds must be finite");
	}

	/** A zero size lets the visual node use its preferred dimensions. */
	public GLNodeViewState(float x, float y) {
		this(x, y, 0, 0);
	}

	public static GLNodeViewState capture(GLNode node) {
		if (node == null) throw new IllegalArgumentException("Node view required");
		return new GLNodeViewState(node.getX(), node.getY(), node.getWidth(),
				node.getHeight());
	}
}
