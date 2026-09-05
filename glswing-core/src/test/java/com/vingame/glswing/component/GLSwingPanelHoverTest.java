package com.vingame.glswing.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.awt.event.MouseEvent;

import org.junit.jupiter.api.Test;

import com.vingame.glswing.rendering.GLContext2D;

final class GLSwingPanelHoverTest {
	@Test
	void mouseMotionBeforeFirstFrameWaitsForRenderer() {
		GLSwingPanel panel = new GLSwingPanel() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void render(GLContext2D context) {
			}
		};
		panel.setSize(320, 200);
		MouseEvent move = new MouseEvent(panel, MouseEvent.MOUSE_MOVED,
				System.currentTimeMillis(), 0, 40, 30, 0, false);

		assertDoesNotThrow(() -> panel.dispatchEvent(move));
	}
}
