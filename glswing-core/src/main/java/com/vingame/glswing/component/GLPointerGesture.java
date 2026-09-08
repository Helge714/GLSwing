package com.vingame.glswing.component;

import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLPickResult;

/** Shared pointer-capture rules for panel and canvas input dispatch. */
final class GLPointerGesture {
	private static final float MAX_RETAINED_PRESS_DRIFT = 4;

	private GLPointerGesture() { }

	/**
	 * Reuses a recently confirmed hover target for a press at effectively the
	 * same pointer position. This keeps presses responsive while a color-only
	 * frame or an asynchronous picking rebuild is pending, without treating a
	 * remotely stale hover result as a new hit.
	 */
	static GLPickResult retainedPressPick(GLPickResult hover, float screenX,
			float screenY) {
		if (hover == null || !hover.hasTarget()
				|| Math.abs(screenX - hover.getScreenX()) > MAX_RETAINED_PRESS_DRIFT
				|| Math.abs(screenY - hover.getScreenY()) > MAX_RETAINED_PRESS_DRIFT)
			return GLPickResult.none();
		GLPickResult candidate = hover.at(screenX, screenY);
		if (!(candidate.getTarget() instanceof GLContainer container)
				|| !candidate.hasLocalPosition())
			return GLPickResult.none();
		float x = candidate.getLocalX();
		float y = candidate.getLocalY();
		return x >= 0 && y >= 0 && x <= container.getWidth()
				&& y <= container.getHeight()
					? candidate : GLPickResult.none();
	}

	/**
	 * A retained container completes its captured click when release remains
	 * inside its logical bounds. It does not depend on a second GPU snapshot,
	 * which may lag the visual pressed/focus update by one frame.
	 */
	static boolean completesClick(GLPickResult capturedAtRelease,
			GLPickResult releaseHit) {
		if (capturedAtRelease == null || !capturedAtRelease.hasTarget()) return false;
		GLMouseTarget target = capturedAtRelease.getTarget();
		if (target instanceof GLContainer container
				&& capturedAtRelease.hasLocalPosition()) {
			float x = capturedAtRelease.getLocalX();
			float y = capturedAtRelease.getLocalY();
			return x >= 0 && y >= 0 && x <= container.getWidth()
					&& y <= container.getHeight();
		}
		return releaseHit != null && releaseHit.getTarget() == target;
	}
}
