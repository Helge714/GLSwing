# Text Editing

- [Choosing a control](#choosing-a-control)
- [Documents and text areas](#documents-and-text-areas)
- [Selection and navigation](#selection-and-navigation)
- [Wrapping, tabs, and scrolling](#wrapping-tabs-and-scrolling)
- [Alignment](#alignment)
- [Character formatting](#character-formatting)
- [Editing history](#editing-history)

## Choosing a control

Use `GLTextEditor` for a single-line value that is committed or cancelled as
one control value. It supports filters, final validation, and integration with
the enclosing history scope when a value is committed.

Use `GLTextArea` for multiline documents. Enter inserts a newline, Tab inserts
an indentation character, and undo/redo operate on the active document-editing
session rather than committing the complete text as one value.

## Documents and text areas

`GLTextDocument` stores normalized text and maintains an indexed logical-line
structure. CRLF and CR input is normalized to LF. Applications can share or
replace a document independently of the view.

Text is stored as Java Unicode strings. Caret movement, deletion, mouse hit
testing, and wrapping follow locale-aware Unicode character boundaries rather
than splitting UTF-16 surrogate pairs. Rendering still depends on the selected
font containing the required glyphs.

```java
GLTextDocument document = new GLTextDocument(initialText);
GLTextArea area = new GLTextArea(document);
area.setLineWrap(true);
area.setTabSize(4);

document.addDocumentListener((source, change) -> saveDraft(source.getText()));
```

`Change` reports the replacement offset plus removed and inserted text.
Methods such as `getLineStart`, `getLineEnd`, `getLineText`, and
`getLineOfOffset` provide logical-line lookup without requiring callers to
rescan the complete document.

## Selection and navigation

Mouse dragging selects across visual and logical lines. The standard input map
provides character, word, line, document, clipboard, and selection commands.
Home and End address the current logical line; the platform command modifier
combines with them for document boundaries and with Left/Right for word
navigation. Up and Down preserve the logical column where possible; Page Up
and Page Down move by the visible line count.

Selection endpoints remain stored when the text area loses focus, but the
selection highlight is hidden while the control is inactive. Focusing the area
again restores the visual highlight for the unchanged selection.

`setEditable(false)` retains navigation, selection, and copying while blocking
document changes.

## Wrapping, tabs, and scrolling

Soft wrapping is enabled by default and does not insert newline characters.
Wrapping prefers the latest whitespace boundary that fits and falls back to a
Unicode character boundary only when a single word is wider than the viewport.
An ordinary hyphen is also a preferred wrap boundary, so authors can provide
manual hyphenation without adding document metadata. The hyphen remains visible
at the end of the row.
The first separator that exceeds the remaining width acts as the visual wrap
delimiter: the fitting word stays in place and the caret moves to the start of
the continuation row. Further spaces are ordinary indentation on that row.
`setLineWrap(false)` enables horizontal scrolling for long logical lines.
`setTabSize(...)` controls the visual width of tab characters without changing
document content. The caret is scrolled into view automatically, and the mouse
wheel scrolls vertically by font line height. With wrapping disabled, the
configured horizontal-scroll modifier scrolls horizontally. Applications may
also inspect or change both offsets through `getHorizontalScrollOffset()`,
`getVerticalScrollOffset()`, and `setScrollOffset(...)`.

When the visual rows exceed the viewport, `GLTextArea` displays an integrated,
theme-aware vertical scrollbar. Its thumb can be dragged or repositioned by
clicking the track. `isVerticalScrollBarVisible()` reports the current overflow
state. The text viewport reserves the themed scrollbar thickness so wrapping
does not change when the bar appears or disappears.

Only rows intersecting the viewport are submitted for text drawing. The
document remains one model object; GLSwing does not create a child control for
each line.

## Alignment

`setTextAlignment(...)` applies `LEFT`, `CENTER`, `RIGHT`, or `JUSTIFIED`
alignment to the text area. A secondary-button press opens the built-in context
menu, whose **Alignment** submenu contains these commands. Retrieve the root
menu through `getContextMenu()` to adjust its items or presentation for an
application. Center and right alignment also affect caret placement, selection,
pointer hit testing, and vertical navigation.
Alignment commands selected from this menu are recorded in the enclosing
history. Direct calls to `setTextAlignment(...)` are ordinary programmatic
state updates and do not create history entries.

Justification expands spaces only on rows produced by soft wrapping. The final
row of a logical paragraph remains naturally spaced, as is customary in text
editors. Tabs retain their configured width and are not justification points.
Automatic hyphenation is intentionally not inferred: correct results require a
language-specific hyphenation dictionary and an explicit document language.

## Character formatting

The context menu's **Formatting** submenu provides **Bold** and **Italic**
commands. With a non-empty selection, a command changes that range and records
an operation in the enclosing document history. Any pending typing session is
committed first, keeping formatting and text insertion as distinct undo steps.
With only a caret, a command changes the typing style for text inserted
afterward. Bold and italic may be combined.

Formatting belongs to `GLTextDocument`, not to its view. Use `setStyle(...)`,
`getStyleAt(...)`, and `getStyleRuns(...)` when loading, saving, or inspecting
formatted content. `GLTextStyle` is immutable and currently contains the bold
and italic attributes. `snapshot()` and `restore(...)` preserve text and
formatting together; completed focus sessions therefore also restore both
through the enclosing history.

All built-in context-menu labels use `GLUiKeys` and GLSwing's resource bundles.
Changing the host `GLLocalizer` refreshes existing menus. Applications can
override individual labels or supply their own localized bundle without
rebuilding the editor menu.

## Editing history

Each `GLTextArea` owns a `GLHistoryManager` returned by
`getEditingHistoryManager()`. Undo and redo shortcuts are handled inside the
focused area through the common `GLEditingHistoryTarget` contract, before the
host history sees the shortcut. Replacing the document or applying unrelated programmatic
changes resets this local editing history, preventing commands from crossing
document boundaries.

When focus leaves the area, the complete editing session is recorded as one
entry in the enclosing host history. `setHistoryEnabled(false)` disables this
outer commit without disabling local editing history. The enclosing history
therefore remains suitable for application-level operations while character
edits stay granular only during active text entry.
