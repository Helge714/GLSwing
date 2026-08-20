package com.vingame.glswing.rendering;

import java.awt.Color;

/**
 * Small immutable forward-lighting setup for normal-mapped sprites. Directions
 * use screen/world x to the right, y down and z towards the viewer.
 */
public record GLLighting2D(Color ambientColor, float ambientIntensity,
		DirectionalLight directionalLight) {

	public static final GLLighting2D DEFAULT = new GLLighting2D(
			Color.WHITE, 0.28f,
			new DirectionalLight(-0.35f, -0.45f, 0.82f,
					Color.WHITE, 0.9f));

	public GLLighting2D {
		if (ambientColor == null)
			throw new IllegalArgumentException("Ambient color must not be null");
		if (!finiteNonNegative(ambientIntensity))
			throw new IllegalArgumentException(
					"Ambient intensity must be finite and non-negative");
	}

	public GLLighting2D withAmbient(Color color, float intensity) {
		return new GLLighting2D(color, intensity, directionalLight);
	}

	public GLLighting2D withDirectionalLight(DirectionalLight light) {
		return new GLLighting2D(ambientColor, ambientIntensity, light);
	}

	public GLLighting2D withoutDirectionalLight() {
		return withDirectionalLight(null);
	}

	public record DirectionalLight(float x, float y, float z,
			Color color, float intensity) {
		public DirectionalLight {
			if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
					|| x * x + y * y + z * z <= 0)
				throw new IllegalArgumentException(
						"Light direction must be finite and non-zero");
			if (color == null)
				throw new IllegalArgumentException("Light color must not be null");
			if (!finiteNonNegative(intensity))
				throw new IllegalArgumentException(
						"Light intensity must be finite and non-negative");
			float inverseLength = 1f / (float) Math.sqrt(x * x + y * y + z * z);
			x *= inverseLength;
			y *= inverseLength;
			z *= inverseLength;
		}
	}

	private static boolean finiteNonNegative(float value) {
		return Float.isFinite(value) && value >= 0;
	}
}

