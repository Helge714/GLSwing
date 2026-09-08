package com.vingame.glswing.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.ui.GLButton;

final class GLPointerGestureTest {
	@Test void nearbyPressCanReuseConfirmedHoverTarget() {
		GLContainer control = new GLButton("Action");
		control.setBounds(0, 0, 100, 30);
		GLPickResult hover = hit(control, 10, 10);

		assertTrue(GLPointerGesture.retainedPressPick(hover, 13, 12).hasTarget());
		assertFalse(GLPointerGesture.retainedPressPick(hover, 15, 12).hasTarget());
		assertFalse(GLPointerGesture.retainedPressPick(hover, 102, 10).hasTarget());
	}

	@Test void retainedControlUsesCapturedLocalBoundsInsteadOfReleaseSnapshot() {
		GLContainer control = new GLButton("Action");
		control.setBounds(0, 0, 100, 30);
		GLPickResult pressed = hit(control, 10, 10);

		assertTrue(GLPointerGesture.completesClick(pressed.at(80, 20),
				GLPickResult.none()));
		assertFalse(GLPointerGesture.completesClick(pressed.at(101, 20),
				pressed));
	}

	@Test void targetWithoutLogicalBoundsRetainsExactReleaseHitRule() {
		GLMouseTarget target = new GLMouseTarget() { };
		GLPickResult pressed = hit(target, 4, 4);
		assertTrue(GLPointerGesture.completesClick(pressed, pressed));
		assertFalse(GLPointerGesture.completesClick(pressed, GLPickResult.none()));
	}

	private static GLPickResult hit(GLMouseTarget target, float x, float y) {
		return new GLPickResult(1, target, x, y, x, y,
				1, 0, 0, 1, 0, 0);
	}
}
