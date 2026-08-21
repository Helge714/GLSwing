package com.vingame.glswing.history;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bounded undo/redo history with compound transactions and a saved-state
 * checkpoint. Edits are recorded after their change has already been applied.
 */
public final class GLSaveManager {

	public static final int DEFAULT_HISTORY_LIMIT = 100;

	private final Deque<Entry> undo = new ArrayDeque<>();
	private final Deque<Entry> redo = new ArrayDeque<>();
	private final Deque<Transaction> transactions = new ArrayDeque<>();
	private final CopyOnWriteArrayList<GLSaveManagerListener> listeners =
			new CopyOnWriteArrayList<>();
	private int historyLimit;
	private long nextStateId;
	private long currentStateId;
	private long savedStateId;
	private boolean applying;

	public GLSaveManager() {
		this(DEFAULT_HISTORY_LIMIT);
	}

	public GLSaveManager(int historyLimit) {
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
		return !undo.isEmpty();
	}

	public boolean canRedo() {
		return !redo.isEmpty();
	}

	public boolean isApplying() {
		return applying;
	}

	public boolean isInTransaction() {
		return !transactions.isEmpty();
	}

	public boolean isDirty() {
		return currentStateId != savedStateId || !transactions.isEmpty();
	}

	public String getUndoName() {
		return canUndo() ? undo.peekLast().edit.name() : null;
	}

	public String getRedoName() {
		return canRedo() ? redo.peekLast().edit.name() : null;
	}

	/** Marks the current history position as persisted. */
	public void markSaved() {
		if (isInTransaction())
			throw new IllegalStateException(
					"Cannot mark a partially recorded transaction as saved");
		savedStateId = currentStateId;
		fireChanged();
	}

	/** Clears history and treats the current document state as saved. */
	public void reset() {
		if (isInTransaction())
			throw new IllegalStateException("Cannot reset during a transaction");
		undo.clear();
		redo.clear();
		currentStateId = savedStateId = ++nextStateId;
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
		Entry entry = undo.peekLast();
		apply(entry.edit::undo);
		undo.removeLast();
		redo.addLast(entry);
		currentStateId = entry.beforeStateId;
		fireChanged();
		return true;
	}

	public boolean redo() {
		if (!canRedo() || isInTransaction())
			return false;
		Entry entry = redo.peekLast();
		apply(entry.edit::redo);
		redo.removeLast();
		undo.addLast(entry);
		currentStateId = entry.afterStateId;
		fireChanged();
		return true;
	}

	/** Handles Ctrl/Cmd+Z, Ctrl/Cmd+Shift+Z and Ctrl/Cmd+Y. */
	public boolean handleKeyPressed(KeyEvent event) {
		if (!isHistoryShortcut(event))
			return false;
		int modifiers = event.getModifiersEx();
		if (event.getKeyCode() == KeyEvent.VK_Z)
			if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0)
				redo();
			else
				undo();
		else if (event.getKeyCode() == KeyEvent.VK_Y)
			redo();
		else
			return false;
		event.consume();
		return true;
	}

	public static boolean isHistoryShortcut(KeyEvent event) {
		if (event == null || event.getID() != KeyEvent.KEY_PRESSED)
			return false;
		int modifiers = event.getModifiersEx();
		boolean shortcut = (modifiers & (InputEvent.CTRL_DOWN_MASK
				| InputEvent.META_DOWN_MASK)) != 0;
		return shortcut && (modifiers & InputEvent.ALT_DOWN_MASK) == 0
				&& (event.getKeyCode() == KeyEvent.VK_Z
						|| event.getKeyCode() == KeyEvent.VK_Y);
	}

	public void addListener(GLSaveManagerListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("History listener must not be null");
		listeners.addIfAbsent(listener);
	}

	public void removeListener(GLSaveManagerListener listener) {
		listeners.remove(listener);
	}

	private void append(GLUndoableEdit edit) {
		long before = currentStateId;
		long after = ++nextStateId;
		undo.addLast(new Entry(edit, before, after));
		currentStateId = after;
		redo.clear();
		trimUndoHistory();
		fireChanged();
	}

	private void trimUndoHistory() {
		while (undo.size() > historyLimit)
			undo.removeFirst();
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
		record(edit);
	}

	private void fireChanged() {
		for (GLSaveManagerListener listener : listeners)
			listener.historyChanged(this);
	}

	public final class Transaction implements AutoCloseable {
		private final String name;
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
			GLSaveManager.this.close(this);
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
