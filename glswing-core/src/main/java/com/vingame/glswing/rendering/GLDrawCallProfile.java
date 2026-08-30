package com.vingame.glswing.rendering;

import java.util.List;

/** Immutable draw-call breakdown for the most recently rendered frame. */
public record GLDrawCallProfile(int total, int filled, int texture, int text,
		int ui, int sprite, int layers, int renderTargets, int other,
		List<GLDrawCallEvent> events) {

	public static final GLDrawCallProfile EMPTY =
			new GLDrawCallProfile(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());

	public GLDrawCallProfile {
		if (total < 0 || filled < 0 || texture < 0 || text < 0 || ui < 0
				|| sprite < 0 || layers < 0 || renderTargets < 0 || other < 0)
			throw new IllegalArgumentException("Draw-call counts must not be negative");
		events = List.copyOf(events);
	}
}
