package com.vingame.glswing.node.execution;

/** Observable execution behavior of one backend-neutral node implementation. */
public enum GLNodeImplementationKind {
	/** Deterministic operation without externally visible state or side effects. */
	PURE,
	/** Operation whose result can depend on persistent state between evaluations. */
	STATEFUL,
	/** Operation that produces an externally visible effect and is never discarded. */
	EFFECT
}
