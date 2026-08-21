package com.vingame.glswing.input;

/**
 * Marker and event target for geometry drawn through {@code GLContext2D}.
 * Implement only the callbacks an object is interested in. A callback that
 * changes visible state is responsible for requesting the affected repaint;
 * dispatching an event does not invalidate the complete render surface.
 */
public interface GLMouseTarget {

	default void mouseEntered(GLMouseEvent event) {
	}

	default void mouseExited(GLMouseEvent event) {
	}

	default void mouseMoved(GLMouseEvent event) {
	}

	default void mousePressed(GLMouseEvent event) {
	}

	default void mouseDragged(GLMouseEvent event) {
	}

	default void mouseReleased(GLMouseEvent event) {
	}

	/** Called when an active native pointer capture ends without a release. */
	default void mouseCaptureLost(GLMouseEvent event) {
	}

	default void mouseClicked(GLMouseEvent event) {
	}

	default void mouseWheelMoved(GLMouseWheelEvent event) {
	}
}
