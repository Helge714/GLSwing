# Undo and Redo with `GLHistoryManager`

[UI guide](../ui-framework.md) · [Controls](controls.md) ·
[Documents](documents.md) · [Node editor](nodes.md)

`GLHistoryManager` stores named reversible edits, groups related operations, and
tracks whether the current state differs from the last saved state.

## Contents

- [Setup and status](#setup-and-status)
- [Independent history scopes](#independent-history-scopes)
- [Recording completed changes](#recording-completed-changes)
- [Performing changes](#performing-changes)
- [Transactions](#transactions)
- [Keyboard shortcuts](#keyboard-shortcuts)
- [Integrated controls](#integrated-controls)
- [Saved-state tracking](#saved-state-tracking)

## Setup and status

Hosts provide a manager through `getGLHistoryManager()`. Replace it with
`setGLHistoryManager(...)` when several views must share one document history.

```java
GLHistoryManager history = host.getGLHistoryManager();
if (history.canUndo()) {
    undoItem.setText("Undo " + history.getUndoName());
}
```

`canUndo()`, `canRedo()`, `getUndoName()`, and `getRedoName()` support command
state. `undo()`, `redo()`, and `reset()` execute or clear history.
`setHistoryLimit(...)` controls retained edits; the default is
`DEFAULT_HISTORY_LIMIT`.

## Independent history scopes

Every `GLContainer` inherits the nearest history manager from its parent. Set a
local manager to make the container the root of an independent document or
editing surface:

```java
GLHistoryManager documentHistory = new GLHistoryManager();
documentSurface.setGLHistoryManager(documentHistory);
```

All controls below `documentSurface` now record into `documentHistory`, while
sibling surfaces continue using their own scope or the host default. Nested
scopes are supported. `clearGLHistoryManager()` restores inheritance and
`hasExplicitGLHistoryManager()` reports whether a container starts a scope.

Keyboard undo and redo resolve both the history manager and the semantic
[input map](input-configuration.md) from the focused component. The bundled
`Ctrl/Cmd+Z` binding therefore affects the active surface rather than an
unrelated host-level history, and applications may replace the binding.

For document tabs, `GLDocumentTabController` installs these scopes and
`GLDocumentWorkspace` routes application-level menu actions to the selected
document. See [Documents and serialization](documents.md#active-documents-and-tabs).

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
with the nearest inherited history manager. Programmatic setters do not necessarily create
history; user gestures do. Composite controls can disable a child editor's
history with `setHistoryEnabled(false)` and record one higher-level operation.

While a `GLTextEditor` is focused, undo and redo operate on its private editing
session. Consecutive text operations may be coalesced, and each entry restores
text, caret, and selection. A successful commit clears that session and adds
one complete value change to the inherited document history; cancel clears the
session without adding a document edit. The coalescing interval is configured
through the [input map](input-configuration.md).

## Saved-state tracking

`markPersisted()` marks the current history position as the persisted document
state. `isModified()` changes as edits, undo, and redo move relative to that point.
Call `markPersisted()` only after a successful save. Clearing or truncating history
can make the previous saved position unreachable, in which case dirty state
remains conservative.
