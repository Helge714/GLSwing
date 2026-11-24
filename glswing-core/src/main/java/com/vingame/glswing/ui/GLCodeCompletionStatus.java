package com.vingame.glswing.ui;

/** State of the most recent asynchronous code-completion request. */
public record GLCodeCompletionStatus(State state, int proposalCount, Throwable failure) {
	public enum State { REQUESTING, AVAILABLE, EMPTY, FAILED }
	public GLCodeCompletionStatus {
		if (state == null || proposalCount < 0
				|| (state == State.FAILED) != (failure != null))
			throw new IllegalArgumentException("Invalid completion status");
	}
	public static GLCodeCompletionStatus requesting() {
		return new GLCodeCompletionStatus(State.REQUESTING, 0, null);
	}
	public static GLCodeCompletionStatus completed(int count) {
		return new GLCodeCompletionStatus(count == 0 ? State.EMPTY : State.AVAILABLE,
				count, null);
	}
	public static GLCodeCompletionStatus failed(Throwable failure) {
		return new GLCodeCompletionStatus(State.FAILED, 0,
				java.util.Objects.requireNonNull(failure, "failure"));
	}
}
