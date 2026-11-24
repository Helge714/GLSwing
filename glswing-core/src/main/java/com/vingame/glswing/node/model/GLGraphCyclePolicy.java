package com.vingame.glswing.node.model;

/** Defines which dependency cycles a graph profile permits. */
public enum GLGraphCyclePolicy {
	/** Every directed cycle, including a self-loop, is invalid. */
	ACYCLIC,
	/** Cycles are valid only when broken by a previous-step output. */
	DELAYED_FEEDBACK,
	/** Every connection reads its source value from the previous evaluation step. */
	PREVIOUS_STEP
}
