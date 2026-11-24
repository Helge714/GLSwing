package com.vingame.glswing.rendering;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

final class GLRenderer2DClipTest {
	@Test
	void nestedInsetRetainsOuterRoundedCorners() {
		float[] outer = { 0, 0, 100, 80, 12, 12, 12, 12 };
		float[] actual = GLRenderer2D.intersectUIClip(
				1, 1, 99, 79, new float[4], outer);

		assertArrayEquals(new float[] {
				1, 1, 99, 79, 11, 11, 11, 11
		}, actual);
	}

	@Test
	void innerRoundedClipKeepsStricterEffectiveCorner() {
		float[] outer = { 0, 0, 100, 80, 12, 12, 12, 12 };
		float[] actual = GLRenderer2D.intersectUIClip(
				1, 1, 99, 79, new float[] { 6, 6, 6, 6 }, outer);

		assertArrayEquals(new float[] {
				1, 1, 99, 79, 11, 11, 11, 11
		}, actual);
	}
}
