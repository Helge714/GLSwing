package com.vingame.glswing.ui;

/** State of the most recent asynchronous semantic-token request. */
public record GLCodeSemanticStatus(State state, int tokenCount, Throwable failure) {
	public enum State { REQUESTING, AVAILABLE, EMPTY, FAILED }

	public GLCodeSemanticStatus {
		if (state == null || tokenCount < 0
				|| (state == State.FAILED) != (failure != null))
			throw new IllegalArgumentException("Invalid semantic-token status");
	}

	public static GLCodeSemanticStatus requesting() {
		return new GLCodeSemanticStatus(State.REQUESTING, 0, null);
	}

	public static GLCodeSemanticStatus completed(int count) {
		return new GLCodeSemanticStatus(count == 0 ? State.EMPTY : State.AVAILABLE,
				count, null);
	}

	public static GLCodeSemanticStatus failed(Throwable failure) {
		return new GLCodeSemanticStatus(State.FAILED, 0,
				java.util.Objects.requireNonNull(failure, "failure"));
	}
}
