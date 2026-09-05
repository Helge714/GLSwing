package com.vingame.glswing.document;

/**
 * Observable coordinator state for status indicators and logging.
 *
 * @param state operation state
 * @param document related document, when applicable
 * @param recovery related snapshot, when applicable
 * @param failure failure cause for {@link State#FAILED}, otherwise {@code null}
 */
public record GLDocumentAutosaveEvent(State state, GLDocument<?> document,
		GLDocumentRecovery recovery, Throwable failure) {
	/** Lifecycle states emitted by the autosave coordinator. */
	public enum State { IDLE, SCHEDULED, SAVING, SAVED, DELETED, FAILED, CLOSED }
	public GLDocumentAutosaveEvent {
		if (state == null) throw new IllegalArgumentException("Autosave state required");
		if (state == State.FAILED && failure == null)
			throw new IllegalArgumentException("Failed autosave event requires a cause");
	}
}
