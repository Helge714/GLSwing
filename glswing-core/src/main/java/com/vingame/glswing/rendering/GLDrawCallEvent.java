package com.vingame.glswing.rendering;

/** One non-empty batch flush in frame submission order. */
public record GLDrawCallEvent(String pipeline, int calls, String reason) {

	public GLDrawCallEvent {
		if (pipeline == null || pipeline.isBlank())
			throw new IllegalArgumentException("Pipeline must not be blank");
		if (calls <= 0)
			throw new IllegalArgumentException("Call count must be positive");
		if (reason == null || reason.isBlank())
			throw new IllegalArgumentException("Flush reason must not be blank");
	}
}
