package com.vingame.glswing.input;

import java.awt.event.MouseEvent;

/** Mouse event enriched with GPU hit data and object-local coordinates. */
public class GLMouseEvent {
	@FunctionalInterface
	public interface PointerWarpRequest {
		void warpTo(float screenX, float screenY);
	}

	private final MouseEvent originalEvent;
	private final GLPickResult pickResult;
	private final float deltaX;
	private final float deltaY;
	private final PointerWarpRequest pointerWarpRequest;
	private final GLPointerController pointerController;

	public GLMouseEvent(MouseEvent originalEvent, GLPickResult pickResult,
			float deltaX, float deltaY) {
		this(originalEvent, pickResult, deltaX, deltaY, null);
	}

	public GLMouseEvent(MouseEvent originalEvent, GLPickResult pickResult,
			float deltaX, float deltaY, PointerWarpRequest pointerWarpRequest) {
		this(originalEvent, pickResult, deltaX, deltaY, pointerWarpRequest,
				GLPointerControllers.forAwt(originalEvent == null
						? null : originalEvent.getComponent()));
	}

	public GLMouseEvent(MouseEvent originalEvent, GLPickResult pickResult,
			float deltaX, float deltaY, PointerWarpRequest pointerWarpRequest,
			GLPointerController pointerController) {
		if (originalEvent == null || pickResult == null)
			throw new IllegalArgumentException("Mouse event and pick result must not be null");
		this.originalEvent = originalEvent;
		this.pickResult = pickResult;
		this.deltaX = deltaX;
		this.deltaY = deltaY;
		this.pointerWarpRequest = pointerWarpRequest;
		this.pointerController = pointerController == null
				? GLPointerController.NONE : pointerController;
	}

	public MouseEvent getOriginalEvent() {
		return originalEvent;
	}

	public GLPickResult getPickResult() {
		return pickResult;
	}

	public GLMouseTarget getTarget() {
		return pickResult.getTarget();
	}

	public float getScreenX() {
		return pickResult.getScreenX();
	}

	public float getScreenY() {
		return pickResult.getScreenY();
	}

	public boolean hasLocalPosition() {
		return pickResult.hasLocalPosition();
	}

	public float getLocalX() {
		return pickResult.getLocalX();
	}

	public float getLocalY() {
		return pickResult.getLocalY();
	}

	/** Horizontal movement in logical Swing pixels since the previous drag event. */
	public float getDeltaX() {
		return deltaX;
	}

	/** Vertical movement in logical Swing pixels since the previous drag event. */
	public float getDeltaY() {
		return deltaY;
	}

	/**
	 * Requests a component-local pointer position after the current drag callback.
	 * The dispatcher also moves its drag baseline, so the synthetic warp does not
	 * appear as user movement in the following event.
	 */
	public boolean requestPointerWarp(float screenX, float screenY) {
		if (pointerWarpRequest == null)
			return false;
		if (!Float.isFinite(screenX) || !Float.isFinite(screenY))
			throw new IllegalArgumentException("Pointer warp coordinates must be finite");
		pointerWarpRequest.warpTo(screenX, screenY);
		return true;
	}

	public GLPointerController getPointerController() {
		return pointerController;
	}

	public int getButton() {
		return originalEvent.getButton();
	}

	public int getClickCount() {
		return originalEvent.getClickCount();
	}

	/**
	 * Returns whether this click completes a double-click pair. AWT may continue
	 * counting rapid clicks beyond two, so clicks 2, 4, 6, ... each complete a
	 * distinct pair.
	 */
	public boolean completesDoubleClick() {
		int count = getClickCount();
		return count > 0 && (count & 1) == 0;
	}

	public int getModifiersEx() {
		return originalEvent.getModifiersEx();
	}

	public long getWhen() {
		return originalEvent.getWhen();
	}

	public void consume() {
		originalEvent.consume();
	}

	public boolean isConsumed() {
		return originalEvent.isConsumed();
	}
}
