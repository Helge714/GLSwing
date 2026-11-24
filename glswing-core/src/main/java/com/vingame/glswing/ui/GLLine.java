package com.vingame.glswing.ui;

import java.awt.Color;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLStroke;

/**
 * Lightweight retained line whose endpoints are relative to its component
 * bounds. It uses the same {@link GLStroke} implementation as direct drawing.
 */
public final class GLLine extends GLContainer {

	public enum Orientation {
		HORIZONTAL,
		VERTICAL,
		DIAGONAL_DOWN,
		DIAGONAL_UP
	}

	private float startX;
	private float startY;
	private float endX;
	private float endY;
	private Color color;
    private boolean themed;
	private GLStroke stroke;
	private Orientation orientation;

	public GLLine(Orientation orientation) {
		this(orientation, GLTheme.DEFAULT.values().getColor("line.color"),
				new GLStroke(1).withWidthSpace(GLStroke.WidthSpace.SCREEN));
        themed=true;
	}

	public GLLine(Orientation orientation, Color color, GLStroke stroke) {
		this.color = requireColor(color);
		this.stroke = requireStroke(stroke);
		setOrientation(orientation);
	}

	public void setOrientation(Orientation orientation) {
		if (orientation == null)
			throw new IllegalArgumentException("Line orientation must not be null");
		this.orientation = orientation;
		switch (orientation) {
		case HORIZONTAL -> setRelativeEndpointsInternal(0, 0.5f, 1, 0.5f);
		case VERTICAL -> setRelativeEndpointsInternal(0.5f, 0, 0.5f, 1);
		case DIAGONAL_DOWN -> setRelativeEndpointsInternal(0, 0, 1, 1);
		case DIAGONAL_UP -> setRelativeEndpointsInternal(0, 1, 1, 0);
		}
		updateMetrics();
	}

	/** Returns {@code null} when custom relative endpoints are in use. */
	public Orientation getOrientation() {
		return orientation;
	}

	/**
	 * Sets endpoint factors relative to the current width and height. Values are
	 * deliberately not clamped, so a line may extend beyond its component.
	 */
	public void setRelativeEndpoints(float startX, float startY,
			float endX, float endY) {
		requireFinite(startX, startY, endX, endY);
		orientation = null;
		setRelativeEndpointsInternal(startX, startY, endX, endY);
		updateMetrics();
	}

	private void setRelativeEndpointsInternal(float startX, float startY,
			float endX, float endY) {
		if (this.startX == startX && this.startY == startY
				&& this.endX == endX && this.endY == endY)
			return;
		this.startX = startX;
		this.startY = startY;
		this.endX = endX;
		this.endY = endY;
		invalidateRecording();
	}

	public float getRelativeStartX() { return startX; }
	public float getRelativeStartY() { return startY; }
	public float getRelativeEndX() { return endX; }
	public float getRelativeEndY() { return endY; }

	public Color getColor() {
		return themed ? getGLTheme().values().getColor("line.color") : color;
	}

	public void setColor(Color color) {
		color = requireColor(color);
		if (!themed && this.color.equals(color))
			return;
        themed=false;
		this.color = color;
		invalidateRecording();
	}

	public GLStroke getStroke() {
		return stroke;
	}

	public void setStroke(GLStroke stroke) {
		stroke = requireStroke(stroke);
		if (this.stroke.equals(stroke))
			return;
		this.stroke = stroke;
		updateMetrics();
		invalidateRecording();
	}

	@Override
	protected void record(GLContext2D context) {
		context.setStroke(stroke);
		float x1 = startX * getWidth();
		float y1 = startY * getHeight();
		float x2 = endX * getWidth();
		float y2 = endY * getHeight();
		if (x1 == x2 && y1 == y2)
			return;
		if (stroke.isDashed() || stroke.getOffset() != 0)
			context.drawLine(x1, y1, x2, y2, getColor());
		else
			context.drawUILine(x1, y1, x2, y2, getColor());
	}

	@Override
	protected boolean isMouseTargetEnabled() {
		return false;
	}

	private void updateMetrics() {
		float thickness = stroke.getWidth();
		setIntrinsicPreferredGLSize(orientation == Orientation.VERTICAL
				? new GLSize(Math.max(1, thickness), 80)
				: new GLSize(80, Math.max(1, thickness)));
		setVisualOverflow(GLInsets.all(thickness * 0.5f));
	}

	private static Color requireColor(Color color) {
		if (color == null)
			throw new IllegalArgumentException("Line color must not be null");
		return color;
	}

	private static GLStroke requireStroke(GLStroke stroke) {
		if (stroke == null)
			throw new IllegalArgumentException("Line stroke must not be null");
		return stroke;
	}

	private static void requireFinite(float... values) {
		for (float value : values)
			if (!Float.isFinite(value))
				throw new IllegalArgumentException("Relative line endpoints must be finite");
	}
}
