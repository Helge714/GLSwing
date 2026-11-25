package com.vingame.glswing.node.binding;

import com.vingame.glswing.node.GLNode;

/** Presentation bounds and optional user title, separate from graph semantics. */
public record GLNodeViewState(float x, float y, float width, float height, String customTitle, boolean mirrored) {
	public GLNodeViewState {
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width)
				|| !Float.isFinite(height) || width < 0 || height < 0)
			throw new IllegalArgumentException("Node view bounds must be finite");
	}

	public GLNodeViewState(float x,float y,float width,float height,String customTitle){this(x,y,width,height,customTitle,false);}

	public GLNodeViewState(float x,float y,float width,float height){this(x,y,width,height,null);}

	/** A zero size lets the visual node use its preferred dimensions. */
	public GLNodeViewState(float x, float y) {
		this(x, y, 0, 0);
	}

	public static GLNodeViewState capture(GLNode node) {
		if (node == null) throw new IllegalArgumentException("Node view required");
		return new GLNodeViewState(node.getX(), node.getY(), node.getWidth(),
				node.getHeight(),node.getCustomTitle(),node.isMirrored());
	}
}
