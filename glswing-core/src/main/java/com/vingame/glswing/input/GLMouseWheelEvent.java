package com.vingame.glswing.input;

import java.awt.event.MouseWheelEvent;

/** Mouse-wheel event enriched with GPU hit data and object-local coordinates. */
public final class GLMouseWheelEvent extends GLMouseEvent {

	private final MouseWheelEvent wheelEvent;

	public GLMouseWheelEvent(MouseWheelEvent event, GLPickResult pickResult) {
		super(event, pickResult, 0, 0);
		wheelEvent = event;
	}

	public int getWheelRotation() {
		return wheelEvent.getWheelRotation();
	}

	public double getPreciseWheelRotation() {
		return wheelEvent.getPreciseWheelRotation();
	}

	public int getScrollAmount() {
		return wheelEvent.getScrollAmount();
	}

	public int getScrollType() {
		return wheelEvent.getScrollType();
	}
}
