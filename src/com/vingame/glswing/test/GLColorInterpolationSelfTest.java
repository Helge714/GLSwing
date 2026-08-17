package com.vingame.glswing.test;

import java.awt.Color;

import com.vingame.glswing.rendering.GLColorInterpolation;

/** CPU regression checks for built-in gradient color spaces. */
public final class GLColorInterpolationSelfTest {
	private GLColorInterpolationSelfTest() {
	}

	public static void main(String[] args) {
		testEndpointsAndAlpha();
		testPerceptualStrategies();
		testNames();
		System.out.println("GL color interpolation self-test passed.");
	}

	private static void testEndpointsAndAlpha() {
		Color start = new Color(237, 157, 91, 200);
		Color end = new Color(112, 174, 232, 100);
		for (GLColorInterpolation interpolation : GLColorInterpolation.values()) {
			check(interpolation.interpolate(start, end, 0).equals(start)
					&& interpolation.interpolate(start, end, 1).equals(end),
					interpolation + " did not preserve its endpoint colors");
			check(interpolation.interpolate(start, end, 0.5f).getAlpha() == 150,
					interpolation + " did not interpolate alpha linearly");
		}
	}

	private static void testPerceptualStrategies() {
		Color output = new Color(237, 157, 91);
		Color input = new Color(112, 174, 232);
		Color srgb = GLColorInterpolation.SRGB.interpolate(output, input, 0.5f);
		Color oklab = GLColorInterpolation.OKLAB.interpolate(output, input, 0.5f);
		Color oklch = GLColorInterpolation.OKLCH_SHORTEST.interpolate(
				output, input, 0.5f);
		check(!oklab.equals(srgb),
				"OKLab unexpectedly produced the sRGB midpoint");
		float srgbSaturation = Color.RGBtoHSB(srgb.getRed(), srgb.getGreen(),
				srgb.getBlue(), null)[1];
		float oklchSaturation = Color.RGBtoHSB(oklch.getRed(), oklch.getGreen(),
				oklch.getBlue(), null)[1];
		check(oklchSaturation > srgbSaturation + 0.15f,
				"OKLCH did not retain visibly more chroma than sRGB");
	}

	private static void testNames() {
		check(GLColorInterpolation.parse("oklab") == GLColorInterpolation.OKLAB
				&& GLColorInterpolation.parse("OKLCH-shortest")
						== GLColorInterpolation.OKLCH_SHORTEST
				&& GLColorInterpolation.parse("linear_rgb")
						== GLColorInterpolation.LINEAR_RGB,
				"Color interpolation names were not parsed consistently");
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
