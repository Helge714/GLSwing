package com.vingame.glswing.history;

import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.input.GLInputMaps;

/**
 * Bounded undo/redo history with compound transactions and a persisted-state
 * checkpoint. File serialization and storage are intentionally outside this type.
 * Edits are recorded after their change has already been applied.
 */
public final class GLHistoryManager {

	public static final int DEFAULT_HISTORY_LIMIT = 100;

	private static final class Scope {
        final Deque<Entry> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
        long currentStateId,persistedStateId;
    }
    private final java.util.Map<Object,Scope> scopes=new java.util.LinkedHashMap<>();
    private Object activeScope="default";
    private Scope scope(){return scopes.computeIfAbsent(activeScope,key->new Scope());}
    /** Select a local Undo/Redo stack while retaining one document-wide save checkpoint. */
    public void setActiveScope(Object scope){
        java.util.Objects.requireNonNull(scope);
        if(activeScope.equals(scope))return;
        activeScope=scope;scope();fireChanged();
    }
    public Object getActiveScope(){return activeScope;}
    /** Synchronize an already-accounted-for model change without recording a second edit. */
    public void withoutRecording(Runnable action){
        java.util.Objects.requireNonNull(action);boolean previous=applying;applying=true;
        try{action.run();}finally{applying=previous;}
    }
	private final Deque<Transaction> transactions = new ArrayDeque<>();
	private final CopyOnWriteArrayList<GLHistoryManagerListener> listeners =
			new CopyOnWriteArrayList<>();
	private int historyLimit;
	private long nextStateId;
	private boolean applying;
    private long externalRevision,persistedExternalRevision;
    /** Marks persisted data changed without adding an undo edit or clearing redo. */
    public void markExternalChange(){boolean wasModified=isModified();externalRevision++;if(!wasModified)fireChanged();}


	public GLHistoryManager() {
		this(DEFAULT_HISTORY_LIMIT);
	}

	public GLHistoryManager(int historyLimit) {
		setHistoryLimit(historyLimit);
	}

	public int getHistoryLimit() {
		return historyLimit;
	}

	public void setHistoryLimit(int historyLimit) {
		if (historyLimit < 1)
			throw new IllegalArgumentException("History limit must be positive");
		this.historyLimit = historyLimit;
		trimUndoHistory();
		fireChanged();
	}

	public boolean canUndo() {
		return !scope().undo.isEmpty();
	}

	public boolean canRedo() {
		return !scope().redo.isEmpty();
	}

	public boolean isApplying() {
		return applying;
	}

	public boolean isInTransaction() {
		return !transactions.isEmpty();
	}

	/** Whether the current history position differs from the persisted checkpoint. */
	public boolean isModified() {
		return scopes.values().stream().anyMatch(s->s.currentStateId!=s.persistedStateId) || externalRevision!=persistedExternalRevision || !transactions.isEmpty();
	}

	/** @deprecated Use {@link #isModified()}. */
	@Deprecated(forRemoval = false)
	public boolean isDirty() {
		return isModified();
	}

	public String getUndoName() {
		return canUndo() ? scope().undo.peekLast().edit.name() : null;
	}

	public String getRedoName() {
		return canRedo() ? scope().redo.peekLast().edit.name() : null;
	}

	/** Marks the current history position as successfully persisted. */
	public void markPersisted() {
		if (isInTransaction())
			throw new IllegalStateException(
					"Cannot persist a partially recorded transaction");
		scopes.values().forEach(s->s.persistedStateId=s.currentStateId);
        persistedExternalRevision=externalRevision;
		fireChanged();
	}

	/**
	 * Compatibility alias for the former terminology.
	 * @deprecated Use {@link #markPersisted()}.
	 */
	@Deprecated(forRemoval = false)
	public void markSaved() {
		markPersisted();
	}

	/** Clears history and treats the current document state as persisted. */
	public void reset() {
		if (isInTransaction())
			throw new IllegalStateException("Cannot reset during a transaction");
		scopes.clear();scope();
        externalRevision=persistedExternalRevision=0;
		fireChanged();
	}

	/** Starts a nestable compound edit. Closing it commits its collected edits. */
	public Transaction beginTransaction(String name) {
		if (name == null || name.isBlank())
			throw new IllegalArgumentException("Transaction name must not be blank");
		Transaction transaction = new Transaction(name);
		transactions.addLast(transaction);
		return transaction;
	}

	/** Records an already-applied reversible change. */
	public void record(String name, Runnable undo, Runnable redo) {
		record(GLUndoableEdit.of(name, undo, redo));
	}

	/** Records an already-applied reversible change. */
	public void record(GLUndoableEdit edit) {
		if (edit == null)
			throw new IllegalArgumentException("Undoable edit must not be null");
		if (applying)
			return;
		if (!transactions.isEmpty()) {
			transactions.peekLast().edits.add(edit);
			return;
		}
		append(edit);
	}

	/** Applies a change and records it only if applying succeeds. */
	public void perform(String name, Runnable change, Runnable undo) {
		if (change == null || undo == null)
			throw new IllegalArgumentException("Change and undo actions must not be null");
		change.run();
		record(name, undo, change);
	}

	public boolean undo() {
		if (!canUndo() || isInTransaction())
			return false;
		Scope state=scope();
        Entry entry = state.undo.peekLast();
		apply(entry.edit::undo);
		state.undo.removeLast();
        state.redo.addLast(entry);
        state.currentStateId=entry.beforeStateId;
		fireChanged();
		return true;
	}

	public boolean redo() {
		if (!canRedo() || isInTransaction())
			return false;
		Scope state=scope();
        Entry entry = state.redo.peekLast();
		apply(entry.edit::redo);
		state.redo.removeLast();
        state.undo.addLast(entry);
        state.currentStateId=entry.afterStateId;
		fireChanged();
		return true;
	}

	/** Handles Ctrl/Cmd+Z, Ctrl/Cmd+Shift+Z and Ctrl/Cmd+Y. */
	public boolean handleKeyPressed(KeyEvent event) {
		return handleKeyPressed(event, GLInputMaps.DEFAULT);
	}

	/** Handles the undo or redo action assigned by the supplied input map. */
	public boolean handleKeyPressed(KeyEvent event, GLInputMap inputMap) {
		if (event == null || inputMap == null)
			return false;
		if (inputMap.matches(GLInputActions.HISTORY_UNDO, event))
			undo();
		else if (inputMap.matches(GLInputActions.HISTORY_REDO, event))
			redo();
		else
			return false;
		event.consume();
		return true;
	}

	public static boolean isHistoryShortcut(KeyEvent event) {
		return isHistoryShortcut(event, GLInputMaps.DEFAULT);
	}

	public static boolean isHistoryShortcut(KeyEvent event, GLInputMap inputMap) {
		return event != null && event.getID() == KeyEvent.KEY_PRESSED
				&& inputMap != null
				&& (inputMap.matches(GLInputActions.HISTORY_UNDO, event)
						|| inputMap.matches(GLInputActions.HISTORY_REDO, event));
	}

	public void addListener(GLHistoryManagerListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("History listener must not be null");
		listeners.addIfAbsent(listener);
	}

	public void removeListener(GLHistoryManagerListener listener) {
		listeners.remove(listener);
	}

	private void append(GLUndoableEdit edit) {
		long before = scope().currentStateId;
		long after = ++nextStateId;
		scope().undo.addLast(new Entry(edit, before, after));
		scope().currentStateId = after;
		scope().redo.clear();
		trimUndoHistory();
		fireChanged();
	}

	private void trimUndoHistory() {
		for(var state:scopes.values())while(state.undo.size()>historyLimit)state.undo.removeFirst();
	}

	private void apply(Runnable action) {
		applying = true;
		try {
			action.run();
		} finally {
			applying = false;
		}
	}

	private void close(Transaction transaction) {
		if (transaction.closed)
			return;
		if (transactions.peekLast() != transaction)
			throw new IllegalStateException(
					"Nested history transactions must close in reverse order");
		transactions.removeLast();
		transaction.closed = true;
		if (transaction.edits.isEmpty()) {
			fireChanged();
			return;
		}
		GLUndoableEdit edit = new CompoundEdit(transaction.name,
				List.copyOf(transaction.edits));
        if(!transactions.isEmpty())record(edit);
        else {
            Object current=activeScope;activeScope=transaction.scope;
            try{append(edit);}finally{activeScope=current;}
        }
	}

	private void fireChanged() {
		for (GLHistoryManagerListener listener : listeners)
			listener.historyChanged(this);
	}

	public final class Transaction implements AutoCloseable {
		private final String name;
        private final Object scope=activeScope;
		private final List<GLUndoableEdit> edits = new ArrayList<>();
		private boolean closed;

		private Transaction(String name) {
			this.name = name;
		}

		public String name() {
			return name;
		}

		@Override
		public void close() {
			GLHistoryManager.this.close(this);
		}
	}

	private record Entry(GLUndoableEdit edit, long beforeStateId,
			long afterStateId) {
	}

	private record CompoundEdit(String name, List<GLUndoableEdit> edits)
			implements GLUndoableEdit {
		@Override public void undo() {
			for (int index = edits.size() - 1; index >= 0; index--)
				edits.get(index).undo();
		}
		@Override public void redo() {
			for (GLUndoableEdit edit : edits)
				edit.redo();
		}
	}
}
