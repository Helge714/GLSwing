# Undo and Redo with `GLSaveManager`

[UI guide](../ui-framework.md) · [Controls](controls.md) · [Node editor](nodes.md)

`GLSaveManager` stores named reversible edits, groups related operations, and
tracks whether the current state differs from the last saved state.

## Contents

- [Setup and status](#setup-and-status)
- [Recording completed changes](#recording-completed-changes)
- [Performing changes](#performing-changes)
- [Transactions](#transactions)
- [Keyboard shortcuts](#keyboard-shortcuts)
- [Integrated controls](#integrated-controls)
- [Saved-state tracking](#saved-state-tracking)

## Setup and status

Hosts provide a manager through `getGLSaveManager()`. Replace it with
`setGLSaveManager(...)` when several views must share one document history.

```java
GLSaveManager history = host.getGLSaveManager();
if (history.canUndo()) {
    undoItem.setText("Undo " + history.getUndoName());
}
```

`canUndo()`, `canRedo()`, `getUndoName()`, and `getRedoName()` support command
state. `undo()`, `redo()`, and `reset()` execute or clear history.
`setHistoryLimit(...)` controls retained edits; the default is
`DEFAULT_HISTORY_LIMIT`.

## Recording completed changes

Use `record(...)` after application code has already applied a change:

```java
float before = model.getValue();
model.setValue(after);
history.record(GLUndoableEdit.of(
        "Change value",
        () -> model.setValue(before),
        () -> model.setValue(after)));
```

Undo and redo callbacks must restore the complete affected state and trigger
the same repaint/model notifications as an ordinary change.

## Performing changes

Use `perform(...)` when the manager should execute and record the redo action
atomically. If execution fails, no incomplete edit should enter history.
`isApplying()` is true while undo or redo callbacks run and prevents listeners
from recording recursive edits.

## Transactions

Transactions merge several edits into one named undo step. They may be nested.
Use the returned `AutoCloseable` in try-with-resources or pair the documented
begin/end calls so a failure cannot leave the manager inside a transaction.
`isInTransaction()` reports current state.

Transactions are appropriate for multi-slider drags, moving several selected
nodes, and compound model operations that users perceive as one action.

## Keyboard shortcuts

`isHistoryShortcut(...)` identifies platform-appropriate undo/redo gestures;
`handleKeyPressed(...)` performs them. Invoke it from the host keyboard path
only when an active editor has not consumed the event.

## Integrated controls

`GLTextEditor`, sliders, `GLSliderGroup`, and node-editor operations integrate
with the nearest host manager. Programmatic setters do not necessarily create
history; user gestures do. Composite controls can disable a child editor's
history with `setHistoryEnabled(false)` and record one higher-level operation.

## Saved-state tracking

`markSaved()` marks the current history position as the persisted document
state. `isDirty()` changes as edits, undo, and redo move relative to that point.
Call `markSaved()` only after a successful save. Clearing or truncating history
can make the previous saved position unreachable, in which case dirty state
remains conservative.
