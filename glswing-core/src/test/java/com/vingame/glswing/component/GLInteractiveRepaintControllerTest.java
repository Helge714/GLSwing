package com.vingame.glswing.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GLInteractiveRepaintControllerTest {

	@Test
	void coalescesPickingAndCompletesEverySource() {
		int[] picking = { 0 };
		int[] settled = { 0, 0 };
		GLInteractiveRepaintController controller =
				new GLInteractiveRepaintController(() -> picking[0]++);

		controller.request("first", 10_000, () -> settled[0]++);
		controller.request("second", 10_000, () -> settled[1]++);

		assertTrue(controller.settle());
		assertEquals(1, picking[0]);
		assertEquals(1, settled[0]);
		assertEquals(1, settled[1]);
		assertFalse(controller.settle());
	}

	@Test
	void cancellationRestoresInteractiveStateWithoutPicking() {
		int[] picking = { 0 };
		int[] settled = { 0 };
		GLInteractiveRepaintController controller =
				new GLInteractiveRepaintController(() -> picking[0]++);
		controller.request("source", 10_000, () -> settled[0]++);

		controller.cancel();

		assertEquals(0, picking[0]);
		assertEquals(1, settled[0]);
		assertFalse(controller.settle());
	}
}
