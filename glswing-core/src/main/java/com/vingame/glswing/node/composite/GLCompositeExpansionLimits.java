package com.vingame.glswing.node.composite;

/** Independent safety budgets for nested graph inspection and later compilation. */
public record GLCompositeExpansionLimits(int maximumDepth, long maximumNodes,
		long maximumConnections, long maximumOperations) {
	public static final GLCompositeExpansionLimits DEFAULT =
			new GLCompositeExpansionLimits(64, 100_000, 200_000, 100_000);
	/** Explicitly removes practical budgets while retaining recursion detection. */
	public static GLCompositeExpansionLimits unlimited() {
		return new GLCompositeExpansionLimits(Integer.MAX_VALUE, Long.MAX_VALUE,
				Long.MAX_VALUE, Long.MAX_VALUE);
	}
	public GLCompositeExpansionLimits {
		if (maximumDepth < 1 || maximumNodes < 1 || maximumConnections < 1
				|| maximumOperations < 1)
			throw new IllegalArgumentException("Composite expansion limits must be positive");
	}
}
