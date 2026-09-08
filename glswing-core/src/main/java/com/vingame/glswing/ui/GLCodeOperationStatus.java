package com.vingame.glswing.ui;

/** Progress and outcome of an explicitly requested provider-backed code operation. */
public record GLCodeOperationStatus(Operation operation, State state,
		int resultCount, Throwable failure) {
	public enum Operation { REFERENCES, RENAME, CODE_ACTIONS }
	public enum State { REQUESTING, AVAILABLE, EMPTY, APPLIED, FAILED }

	public GLCodeOperationStatus {
		if (operation == null || state == null || resultCount < 0)
			throw new IllegalArgumentException("Invalid code-operation status");
		if ((state == State.FAILED) != (failure != null))
			throw new IllegalArgumentException("Only failed statuses carry a failure");
	}
	public static GLCodeOperationStatus requesting(Operation operation) {
		return new GLCodeOperationStatus(operation, State.REQUESTING, 0, null);
	}
	public static GLCodeOperationStatus completed(Operation operation, int count) {
		return new GLCodeOperationStatus(operation,
				count == 0 ? State.EMPTY : State.AVAILABLE, count, null);
	}
	public static GLCodeOperationStatus applied(Operation operation, int count) {
		return new GLCodeOperationStatus(operation, State.APPLIED, count, null);
	}
	public static GLCodeOperationStatus failed(Operation operation, Throwable failure) {
		return new GLCodeOperationStatus(operation, State.FAILED, 0,
				java.util.Objects.requireNonNull(failure));
	}
}
