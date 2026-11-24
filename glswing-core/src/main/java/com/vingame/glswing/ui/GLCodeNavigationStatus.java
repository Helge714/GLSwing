package com.vingame.glswing.ui;

/** State reported for an explicitly requested definition lookup. */
public record GLCodeNavigationStatus(State state, int targetCount, Throwable failure) {
	public enum State { REQUESTING, AVAILABLE, EMPTY, FAILED }
	public GLCodeNavigationStatus {
		if (state == null) throw new IllegalArgumentException("State must not be null");
		if (targetCount < 0) throw new IllegalArgumentException("Target count must not be negative");
	}
	public static GLCodeNavigationStatus requesting() {
		return new GLCodeNavigationStatus(State.REQUESTING, 0, null);
	}
	public static GLCodeNavigationStatus completed(int count) {
		return new GLCodeNavigationStatus(count == 0 ? State.EMPTY : State.AVAILABLE, count, null);
	}
	public static GLCodeNavigationStatus failed(Throwable failure) {
		return new GLCodeNavigationStatus(State.FAILED, 0,
				failure == null ? new IllegalStateException("Definition lookup failed") : failure);
	}
}
