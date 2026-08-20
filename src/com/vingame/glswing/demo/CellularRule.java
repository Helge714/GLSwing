package com.vingame.glswing.demo;

import java.util.Objects;

import com.vingame.glswing.rendering.GLContext2D;

/** Demo-local configuration for binary outer-totalistic cellular automata. */
record CellularRule(
		String name,
		int radius,
		Neighborhood neighborhood,
		boolean includeCenter,
		int birthMin,
		int birthMax,
		int survivalMin,
		int survivalMax) {

	static final int MAX_SHADER_RADIUS = 5;

	static final CellularRule CONWAY = new CellularRule(
			"Conway B3/S23", 1, Neighborhood.MOORE, false,
			3, 3, 2, 3);

	static final CellularRule BOSCO = new CellularRule(
			"Bosco R5", 5, Neighborhood.MOORE, true,
			34, 45, 34, 58);

	enum Neighborhood {
		MOORE(0),
		VON_NEUMANN(1),
		CIRCULAR(2);

		private final int shaderValue;

		Neighborhood(int shaderValue) {
			this.shaderValue = shaderValue;
		}
	}

	CellularRule {
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("Rule name must not be blank");
		Objects.requireNonNull(neighborhood, "neighborhood");
		if (radius < 1 || radius > MAX_SHADER_RADIUS)
			throw new IllegalArgumentException("Radius must be between 1 and "
					+ MAX_SHADER_RADIUS);
		int maximumCount = neighborhoodSize(radius, neighborhood)
				- (includeCenter ? 0 : 1);
		validateRange("birth", birthMin, birthMax, maximumCount);
		validateRange("survival", survivalMin, survivalMax, maximumCount);
	}

	void applyUniforms(GLContext2D context, int width, int height) {
		Objects.requireNonNull(context, "context");
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("Grid dimensions must be positive");
		context.setTextureUniform("uTexelSize", 1f / width, 1f / height);
		context.setTextureUniform("uRadius", radius);
		context.setTextureUniform("uNeighborhood", neighborhood.shaderValue);
		context.setTextureUniform("uIncludeCenter", includeCenter ? 1 : 0);
		context.setTextureUniform("uBirthMin", birthMin);
		context.setTextureUniform("uBirthMax", birthMax);
		context.setTextureUniform("uSurvivalMin", survivalMin);
		context.setTextureUniform("uSurvivalMax", survivalMax);
	}

	int kernelSize() {
		return radius * 2 + 1;
	}

	private static int neighborhoodSize(int radius, Neighborhood neighborhood) {
		return switch (neighborhood) {
			case MOORE -> (radius * 2 + 1) * (radius * 2 + 1);
			case VON_NEUMANN -> 1 + 2 * radius * (radius + 1);
			case CIRCULAR -> {
				int count = 0;
				for (int y = -radius; y <= radius; y++)
					for (int x = -radius; x <= radius; x++)
						if (x * x + y * y <= radius * radius)
							count++;
				yield count;
			}
		};
	}

	private static void validateRange(String label, int minimum, int maximum,
			int maximumCount) {
		if (minimum < 0 || maximum < minimum || maximum > maximumCount)
			throw new IllegalArgumentException(label + " range must be between 0 and "
					+ maximumCount + " and ordered");
	}
}
