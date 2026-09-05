# Controls

[UI guide](../ui-framework.md) · [Components](components.md) · [Next: Themes and localization](themes-localization.md)

All standard controls inherit input, focus, enabled state, picking, and theme
behavior from `GLControl`. Geometry and text measurements use logical pixels.

## Contents

- [Common behavior](#common-behavior)
- [Shared actions](#shared-actions)
- [Control overview](#control-overview)
- [Labels, buttons, checkboxes, and lines](#labels-buttons-checkboxes-and-lines)
- [Sliders and slider groups](#sliders-and-slider-groups)
- [Color controls](#color-controls)
- [Visual and input groups](#visual-and-input-groups)
- [Text editor](#text-editor)
- [Combo boxes and menus](#combo-boxes-and-menus)
- [Tabs](#tabs)
- [Lists](#lists)
- [Trees](#trees)
- [Scrolling](#scrolling)

## Visual and input groups

`GLLayoutGroup` connects touching controls without linking their values. Its
default grid layout supports spans: one tall input can meet two shorter buttons.
Connections are opt-in on both adjoining sides:

```java
GLLayoutGroup group = new GLLayoutGroup();
group.addGL(new GLTextEditor("0"),
    GLConstraints.at(0, 0).span(1, 2).weight(1, 1).fillBoth().connectAll());
group.addGL(new GLButton("+"),
    GLConstraints.at(1, 0).weight(0, 1).fillBoth().connectAll());
group.addGL(new GLButton("-"),
    GLConstraints.at(1, 1).weight(0, 1).fillBoth().connectAll());
```

Use `connect(Connection.RIGHT, Connection.BOTTOM)` to restrict individual sides.
Gaps remain gaps. Nested layout groups contribute their actual occupied cells,
so L- and T-shaped arrangements do not acquire a rectangular background across
empty space. Connections crossing a nested group's boundary also require that
group's permission in its parent's constraints. Layouts must be axis-aligned.
Convex exterior corners are rounded; concave junctions remain square. Standard
controls share the outline and single internal separators, while hover, pressed
and focus rendering remain local to the control. Theme keys are
`layoutGroup.cornerRadius`, `layoutGroup.borderWidth`, and `layoutGroup.border`,
falling back to the button style when omitted.

Custom controls can use the protected `layoutCornerRadius(...)` and
`layoutBorderWidth(...)` helpers to suppress independent borders within a group.
The rounded content clip applies to UI-batched drawing; other drawing pipelines
retain rectangular scissoring and require their own rounded masking if needed.

`GLInputGroup<T>` is independent of layout. Register immutable value snapshots
with `add(getter, setter)`, then call `begin(label)`, perform changes directly or
with `update(operation)`, and `commit()` to record one undo operation. `cancel()`
restores the snapshots without recording. Setters should apply values without
recording additional history. Use the group on the UI event thread.

The color picker uses one compact visual group for the three model channels and
a separate, aligned alpha group with its own numeric field and up/down buttons.
The separating gap makes the independent alpha semantics visible without
changing the column geometry. Drag vertically through the first three
sliders or fields to select several normalized color channels. During linked
numeric editing, only the selected rows receive the focus outline and the
raw text from the active field is mirrored immediately, including incomplete or
temporarily invalid input. Only the active field displays a caret. Enter commits
a valid value to every selected channel; invalid input is discarded and the
color state from before the edit is restored.
Moving the vertical selection gesture back removes rows again; `Ctrl`-click
toggles individual numeric fields. Alpha has the same row geometry
but remains logically independent. Color sliders respond to dragging rather than
track clicks and do not enter keyboard focus; their adjacent fields provide
precise input. Valid field content is reflected live, while committing or
leaving the field records one color edit. Holding an arrow button repeats the
change; releasing it records one edit. The precise/coarse slider modifiers and
factors also apply to these buttons.

Channel labels use their complete names (`Red`, `Hue`, `Saturation`,
`Lightness`, and so on) and are vertically centered and right-aligned. During a
linked field edit, every selected field mirrors the same temporary text and
caret position. Committing converts that shared normalized position back to the
individual channel ranges.

For other labels, `GLLabel.setAlignment(...)` and
`setVerticalAlignment(...)` independently control horizontal and vertical text
placement. Their defaults preserve left/top placement.

The top row contains a generously sized, keyboard-focusable icon-only eyedropper
action, one compact split original/current swatch aligned to the slider column,
and the editing color-model selector.
The selector begins at the numeric-field column. A separate selector in the
same column beside the expression field controls output formatting without
changing the editing model; the expression begins at the slider column. The
dialog reserves a status row only for actionable validation or gamut messages.
Color history stores complete colors, so undo remains correct after switching
models. The screen sampler uses the same eyedropper silhouette as the button
where custom desktop cursors are supported, with a crosshair fallback.

## Common behavior

`setEnabled(false)` cancels active interaction and rejects new input.
`setFocusable(boolean)` controls keyboard-focus eligibility. Standard state is
available through `GLControlState` (`NORMAL`, `HOVERED`, `PRESSED`, and disabled
or focus-related state where supported).

Interactive controls participate in focus traversal by default. `Tab` advances
in component-tree order and `Shift+Tab` moves backwards, wrapping at either end.
Hidden, disabled, detached, or explicitly non-focusable controls are skipped;
hiding or removing a subtree safely releases focus. Set `focusable` to `false`
for controls that must be omitted from keyboard navigation. Focus appearance is
resolved from the active control style and therefore follows the current theme.

Every standard control has an immutable typed style. `setGLStyle(...)` sets a
local override; `clearGLStyle()` returns to inherited theme values. Subclasses
implement protected `control...` hooks without bypassing `GLControl`'s final
event sequencing, capture, or state management.

## Shared actions

`GLAction` keeps one command's semantic ID, localized presentation, icons,
availability, optional selection state, and synchronous or asynchronous handler
together. Bind the same action to controls and menu items so every presentation
stays consistent:

```java
GLAction save = GLAction.builder(GLInputActions.DOCUMENT_SAVE)
        .textKey("action.document.save")
        .shortDescriptionKey("action.document.save.description")
        .icon(Icons.SAVE)
        .enabledWhen(documentLifecycle::canSave)
        .onAction(event -> documentLifecycle.save())
        .build();

GLMenuItem saveItem = fileMenu.addItem(save);
GLButton saveButton = new GLButton(save);
documentLifecycle.addListener(source -> save.refreshEnabled());
```

Selectable actions provide the shared model for checkboxes and checked menu
commands. `selected(initialValue)` creates a self-toggling action. Use
`selectedWhen(...)` when the state belongs to an application model; the handler
updates that model and the action re-evaluates the predicate after invocation:

```java
GLAction showGrid = GLAction.builder("view.show-grid")
        .textKey("action.view.showGrid")
        .selectedWhen(settings::isGridVisible)
        .onAction(() -> settings.setGridVisible(!settings.isGridVisible()))
        .build();

GLCheckBox gridBox = new GLCheckBox(showGrid);
GLMenuItem gridItem = viewMenu.addItem(showGrid);
```

Both controls follow later `setSelected(...)` or `refreshSelected()` changes.
Selectable menu actions reserve the standard icon column and render a check
mark while selected. `GLActionMap#refresh()` updates both availability and
derived selection; use `refreshEnabled()` when only command availability can
have changed.

The text and description keys are resolved against each bound component's
inherited `GLLocalizer`. Menu items derive their accelerator label from the
action ID's first binding in the inherited `GLInputMap`; applications do not
need to duplicate shortcut display strings. Changing an action updates every
bound control. Direct `setText(...)`, `setIcon(...)`, or `setEnabled(...)`
calls on a bound control act as temporary presentation overrides and can be
replaced by the next action update.

Register actions in a `GLActionMap` to dispatch their semantic shortcuts from
a `GLKeyTarget` ancestor:

```java
GLActionMap actions = new GLActionMap()
        .add(save)
        .add(saveAs)
        .add(saveAll);

@Override
public void keyPressed(GLKeyEvent event) {
    actions.handleKeyPressed(event, getGLInputMap(), this);
}
```

A matched shortcut is consumed even while its action is disabled. This keeps
disabled application commands from leaking into the active editor. Use
`GLAction.fromSwing(id, action)` when existing Swing integration already owns
a `javax.swing.Action`; name, short description, enabled state, optional
`SELECTED_KEY`, and invocation are adapted while GLSwing icons and semantic
shortcuts remain native.

## Control overview

| Type | Creation | Principal model |
|---|---|---|
| `GLLabel` | `new GLLabel(text)` | Text and alignment |
| `GLButton` | Text or `GLAction` constructor | Action listeners and `doClick()` |
| `GLDialogButtonBar` | Add one or more `GLButton` instances | Localized dialog-action sizing and alignment |
| `GLCheckBox` | Text or `GLAction` constructor | Selected state and action |
| `GLLine` | Orientation constructor | Stroke and relative endpoints |
| `GLSlider` | `fixed(...)` or `free(...)` | Value, range, step, editor, reset |
| `GLSliderGroup` | `new GLSliderGroup(sliders...)` | Coordinated multi-slider gesture |
| `GLTextEditor` | Text constructor | Single-line editing and validation |
| `GLComboBox<T>` | Empty or item-list constructor | Selection, sorting, popup |
| `GLPopupMenu` | `new GLPopupMenu()` | Menu items and submenus |
| `GLMenuBar` | `new GLMenuBar()` | Top-level menus |
| `GLTabbedPane` | `new GLTabbedPane()` | Tab metadata and selected content |
| `GLList<T>` | Item collection or `GLListModel<T>` constructor | Virtualized cells and index selection |
| `GLTree<T>` | Root collection or `GLTreeModel<T>` constructor | Virtualized hierarchical data |
| `GLScrollPane` | Empty or content constructor | Viewport, policies, offsets |
| `GLScrollBar` | Orientation constructor | Value, extent, maximum |

## Labels, buttons, checkboxes, and lines

Labels display non-editable text. Buttons fire their semantic action after a
valid click; `doClick()` invokes the same action programmatically. Checkboxes
combine a selected boolean with button-like activation. Use `get/setText` and
`get/setSelected` for their models.

Buttons and checkboxes activate with `Space` or `Enter` while focused.

Place dialog actions in a `GLDialogButtonBar` instead of assigning fixed button
widths. The bar uses each button's complete preferred width, including localized
text, icon, theme font, and padding. It is right-aligned by default:

```java
GLDialogButtonBar actions = new GLDialogButtonBar()
        .addButton(cancel)
        .addButton(apply);
actions.setBounds(20, height - 48, width - 40, 30);
```

Use `setAlignment(GLDialogButtonBar.Alignment.RIGHT)`, `setGap(...)`, and
`setMinimumButtonWidth(...)` for application-specific conventions. Buttons are
ellipsized only if the bar itself is narrower than their combined preferred
width and gaps. Dialogs should therefore give the bar the full content width.

`GLLine` draws horizontal, vertical, or diagonal separators. Orientations are
`HORIZONTAL`, `VERTICAL`, `DIAGONAL_DOWN`, and `DIAGONAL_UP`.
`setRelativeEndpoints(x1,y1,x2,y2)` supplies endpoints relative to component
width and height. It is non-pickable; use `GLContext2D` for free-form lines.

## Sliders and slider groups

```java
GLSlider gain = GLSlider.fixed("Gain", 0, 100, 50);
gain.setDefaultValue(50);

GLSlider offset = GLSlider.free("Offset", 0);
offset.setStep(0.1f);
```

`FIXED` clamps to `minimum..maximum` and displays progress. `FREE` has no range
and supports arrow regions. Drag sensitivity is value change per horizontal
logical screen pixel; Shift applies the precise factor and Ctrl the coarse
factor. `step` quantizes changes. `increment()`, `decrement()`, and `reset()`
perform semantic operations.

Clicking the value starts the embedded editor. Control it with
`startValueEditing()`, `cancelValueEditing()`, and `isValueEditing()`. The
context menu contains a localized reset action and can receive application
items through `getContextMenu()`.

`GLSliderGroup` joins vertically arranged sliders visually. A vertical gesture
selects multiple sliders and the following horizontal drag changes them as one
history transaction. Fixed groups render one continuous vertical progress
gradient across the complete group; each row receives the color interval that
corresponds to its position instead of restarting the gradient per slider.
The linked-editing focus outline covers only selected slider rows. Reversing the
vertical selection gesture removes rows before the editor opens.

## Color controls

`GLColorPicker` is an embeddable color editor. `GLColorChooser` is a compact
color button that opens the same editor in an owned, modal GLSwing dialog.

```java
GLColorPicker picker = new GLColorPicker(new Color(84, 120, 181));
picker.setBounds(0, 0, 480, 340);
picker.setColorModel(GLColorModel.OKLCH);
picker.addColorChangeListener(color -> preview.setColor(color));
parent.addGL(picker);

GLColorChooser chooser = new GLColorChooser(Color.RED);
chooser.setBounds(0, 350, 72, 28);
chooser.addColorChangeListener(color -> updateAccent(color));
parent.addGL(chooser);
```

The picker supports RGB (gamma-encoded sRGB), HSV, HSL, OKLab, and OKLCH.
RGB channels use 0–255, hue uses degrees, alpha and normalized channels use
0–1. OKLab a/b use −0.4–0.4 and the OKLCH chroma slider uses 0–0.4.
Changing the editing model does not change the selected color. Results are
8-bit sRGB `java.awt.Color` values; this is not an HDR or wide-gamut editor.

Each `GLColorSlider` previews the result of varying one channel while keeping
the other channels fixed. Transparent samples are composited over a checkerboard.
The black-and-white marker identifies the current value. Stripes on the lower
edge mark out-of-sRGB samples; their previews use component clipping, not
perceptual gamut mapping. The two comparison swatches show original/current color.
Numeric fields accept precise values; the expression field accepts the same
color syntax as theme files. Hold Shift while dragging for finer adjustments.

`GLColorSlider` can also be used independently. Its `setColorSampler(...)`
callback receives actual slider values; samples are cached until the callback
is replaced. Replace it when another channel changes. The optional gamut
predicate controls the striped warning edge.

The picker emits live color-change notifications. Pointer gestures become one
color-history operation; numeric commits and keyboard steps are separate edits.
The chooser keeps dialog edits local until **Apply**. **Cancel** leaves its caller
unchanged. `GLColorChooser.show(owner, initial, model, accepted)` opens the dialog
without adding a chooser button.

The enabled-by-default eyedropper captures the connected screens into temporary
in-memory overlays. Move the pointer to preview a pixel and its hex value; click
to select it. Escape or a non-left click cancels. Sampling preserves the current
alpha, because desktop pixels have no useful source alpha. Screen capture may
require operating-system permission and may be unavailable for protected content.
No screenshots are saved. Use `setPipetteEnabled(false)` to disable this feature.

The eyedropper button uses an SVG icon. Its pointer-following preview uses the
active theme's surface/text colors and sizes itself to the enabled content:

```java
// Enabled, color swatch, hexadecimal value, RGB values:
picker.setPipettePreview(new GLPipettePreview(true, true, true, true));
chooser.setPipettePreview(new GLPipettePreview(true, false, true, false));
// Disable only the preview; sampling and Escape remain available:
picker.setPipettePreview(GLPipettePreview.NONE);
```

`GLPipettePreview.DEFAULT` shows a swatch and hex value. All content switches
are independent; an empty selection also hides the preview. Settings apply to
the next sampling session. For theme-editor pickers, configure
`editor.setColorPickerPipettePreview(...)`. The `GLColorChooser.show(...)`
overload accepting a `GLPipettePreview` configures a standalone picker dialog.

## Text editor

`GLTextEditor` is a single-line editor with caret, selection, clipboard,
horizontal scrolling, and edge fades.

```java
GLTextEditor editor = new GLTextEditor("12.5");
editor.setInputFilter(GLTextInputFormats.DECIMAL_NUMBER);
editor.setInputVerifier(text -> parseAndValidate(text));
```

Each focused editor owns a temporary undo/redo history for its current editing
session. Text, caret, and selection are restored together. Compatible runs of
typing, Backspace, or Delete are coalesced according to the inherited
`text.history-coalesce-millis` [input preference](input-configuration.md).
Paste, cut, and selection replacement remain individual steps.

Commit clears the temporary history and records one complete before/after value
in the nearest document history. Cancel restores the session baseline without
creating a document edit and also clears the temporary history. Applications
can inspect the session manager through `getEditingHistoryManager()` and set a
per-editor cooldown with `setHistoryCoalesceMillis(...)`.

The input filter validates each intermediate string; the verifier validates the
committed value. Keep intermediate states such as a lone minus sign legal when
appropriate. `commit()` returns success and `cancel()` restores the pre-edit
value.

Invalid commit behavior can keep invalid text or restore the last valid value
(`KEEP_INVALID` and `RESTORE_LAST_VALID`). Validation feedback duration is in
milliseconds. Successful user commits enter history unless
`setHistoryEnabled(false)` delegates recording to a composite control.

## Combo boxes and menus

`GLComboBox<T>` separates item value from displayed text through
`setItemTextProvider(...)`. Items can be added, inserted, removed, sorted with
`sortItems(comparator)`, and selected through the model API. Submenus recursively
select a value of the same type.

Popups choose upward or downward placement from available host space and clamp
to the visible area. `setPopupMatchesComboWidth(true)` aligns widths;
`setPopupTitle(...)` adds a fixed heading. `GLPopupTextOverflow.EXPAND` may widen
the popup, while `ELLIPSIS` truncates labels.

The pressed gradient remains visible while the ComboBox popup is open. Its
light and dark colors come from `comboBox.pressedLight` and
`comboBox.pressedDark`; the gradient respects the attachment-side contour.

`GLMenuItem` supports text, mnemonic keys, accelerator display text, action
commands, listeners, `doClick()`, and an optional submenu. When backed by a
selectable `GLAction`, it displays the shared selected state in the icon column.
Accelerator text is presentational; bind the actual global shortcut in the
application.

```java
GLPopupMenu menu = new GLPopupMenu();
menu.addItem(new GLMenuItem("Delete", event -> deleteSelection()));
menu.show(host, screenX, screenY);
```

Menu mnemonics provide conventional keyboard access. Menu bars and popup menus
assign unique mnemonics automatically, preferring word initials and then unused
characters elsewhere in each label:

```java
GLPopupMenu fileMenu = new GLPopupMenu();
fileMenu.addItem("Save", event -> save());
fileMenu.addItem("Save As", event -> saveAs());

GLMenuBar menuBar = new GLMenuBar();
menuBar.addMenu("File", fileMenu);
```

With the default input map, `Alt+F` opens the File menu, `S` activates Save, and
`A` activates Save As. Changing a label recalculates the automatic allocation,
so localized menus require no separate mnemonic configuration. The assigned
character is underlined automatically.

`setMnemonic(...)` pins an explicit key when an application needs a stable
override; duplicate explicit keys in one menu are rejected.
`useAutomaticMnemonic()` restores allocation, while
`setMnemonicEnabled(false)` excludes an item. Applications can use
`setDisplayedMnemonicIndex(...)` to override the underline position for an
explicit mnemonic.

Menu labels normalize three consecutive periods to the typographic ellipsis.
Write `new GLMenuItem("Open ...")` when an action opens a dialog; it is exposed
and rendered as `Open …`. GLSwing does not infer whether an action needs an
ellipsis, and it does not alter editable user text. Overflow truncation also
uses the single Unicode ellipsis character.
The default `GLFont` atlas includes this glyph. Explicit font character sets are
available for application-specific scripts and symbols; include every character
that application-provided menu labels or localized text may render.

Menus accept the renderer-independent `GLIcon` type, including raster images,
adaptive SVG icons, and tintable MSDF icons. A popup reserves one shared leading
column when any row has an icon, keeping every label aligned:

```java
GLIcon saveIcon = GLIcon.fromSvgResource("icons/save.svg");
GLMenuItem save = fileMenu.addItem("Save", event -> save());
save.setIcon(saveIcon);
save.setDisabledIcon(disabledSaveIcon); // optional; normal icon is the fallback

fileMenu.setTitle("File actions");
fileMenu.setTitleIcon(saveIcon);
```

`GLMenuBarItem` supports the same normal and disabled icons. Icon color follows
the resolved normal or disabled text color, so monochrome icons adapt to the
active theme automatically.

Combo boxes use `GLItemPresentation` when values need both labels and icons. The
same presentation is used for the selected value and its popup row:

```java
combo.setItemPresentationProvider(value ->
        new GLItemPresentation(label(value), icon(value), disabledIcon(value)));
```

Clearing the presentation provider restores `setItemTextProvider(...)`.

`show(host,x,y)` expects host coordinates. Menus and submenus render as topmost
overlays. `setImmediateCpuPickingEnabled(true)` is available when menu hover
must update without waiting for the normal GPU-picking snapshot.
Root popups consume outside pointer input and close on an outside press by
default. Use `setOutsideInputBlocked(false)` for an interactive hover popup that
must leave the underlying interface available; the owner remains responsible
for closing that popup when its hover or focus context ends. A
`GLPopupMenuListener` can observe pointer entry and exit across the complete
popup tree, which allows the owner to provide a short movement grace period
between an anchor and a modeless popup.

Hovered and pressed menu items use the theme's `menu.itemCornerRadius` on all
four corners. This small inner radius is independent of the popup's outer
`menu.cornerRadius`.

All popup and submenu surfaces use the theme's `menu.shadow.*` values. The
shadow is an unpickable analytic UI instance, remains in the shared UI batch,
and does not require a layer or framebuffer blur pass.

## Tabs

`GLTabbedPane` shows only selected content. Applications can change titles,
disable or remove tabs, and select by index or component. An overflowing tab
strip scrolls with the wheel and ellipsizes long titles. Hovering an ellipsized
title shows its complete text in the standard tooltip.

The selected component fills the content area by default. The tabbed pane owns
the surrounding border and clips its content; applications that need an inset
should add a layout container as the tab content or override
`tabbedPane.contentPadding` in the theme.

Closable tabs are opt-in through `setTabsClosable(true)`. Closing a tab is a
navigation operation and is deliberately separate from document undo/redo.
Applications can enable a bounded recently-closed history with
`setClosedTabHistoryEnabled(true)`. The tab context menu then offers
**Reopen Closed Tab**; `reopenLastClosedTab()` and `reopenTab(content)` expose
the same operation programmatically.

Close buttons, middle-click, and context-menu close actions use
`requestCloseTabAt(...)`. An optional `GLTabCloseRequestHandler` may approve or
veto the operation asynchronously; repeated gestures reuse the pending request.
`removeTabAt(...)` bypasses that policy and is intended for already-approved
programmatic removal. Document applications normally install this behavior
through [`GLDocumentLifecycle`](documents.md#closing-modified-documents).

## Lists

`GLList<T>` displays large data sets without creating one component for every
item. When used as the direct content of a `GLScrollPane`, it attaches and
records only cells intersecting the viewport plus a small configurable
overscan range. Scrolling recycles those cells. Model size therefore does not
determine component-tree size, picking cost, or per-frame submission count.

Default rows show an automatic full-text tooltip only when their label is
ellipsized. Custom renderers can provide the equivalent behavior through
`setItemToolTipProvider(...)`; `GLFileBrowser` and `GLTree` configure this for
their name columns. Standard buttons, checkboxes, combo boxes, sliders, menu
items, menu-bar items, and tab titles follow the same overflow-tooltip rule.

```java
GLList<Path> files = new GLList<>(paths);
files.setItemTextProvider(path -> path.getFileName().toString());
files.getSelectionModel().setSelectionMode(
        GLListSelectionModel.SelectionMode.MULTIPLE_INTERVAL);
files.addActivationListener((list, path, index) -> open(path));

GLScrollPane viewport = new GLScrollPane(files);
viewport.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
viewport.setScrollUnit(files.getRowHeight());
```

The collection constructor creates a `GLDefaultListModel<T>`. Keep a model
reference when items change incrementally:

```java
GLDefaultListModel<Job> jobs = new GLDefaultListModel<>();
GLList<Job> list = new GLList<>(jobs);

jobs.addElement(new Job("Index assets"));
jobs.insertElementAt(new Job("Load project"), 0);
jobs.setElementAt(updatedJob, index);
jobs.removeElementAt(index);
```

`GLListModel<T>` is the minimal interface for application-owned or lazy data
sources. Its range events distinguish additions, removals, and content changes,
allowing selection indices and visible cells to update without reconstructing
the complete list. Mutate models on the application's UI event thread.

The independent `GLListSelectionModel` supports `SINGLE`, `SINGLE_INTERVAL`,
and `MULTIPLE_INTERVAL`. It exposes selected indices, anchor and lead indices,
range operations, toggle operations, and change listeners. Applications may
replace the selection model independently of the data model. Insertions and
removals reported by the list model shift retained selections.

Mouse selection follows desktop conventions: a plain press replaces selection,
the configured extend modifier selects from the anchor, and the configured
toggle modifier toggles one row. Right-clicking an unselected row selects it
before notifying item mouse listeners. A double-click and Enter activation are
reported through `addActivationListener(...)`. Arrow keys, Home, End, Page Up,
and Page Down move the lead row and reveal it with the smallest required scroll.

The default renderer converts values with `String.valueOf` and can instead use
`setItemTextProvider(...)`. For structured rows, install a cell renderer:

```java
list.setCellRenderer((context, source, value, index, state, width, height) -> {
    // GLSwing has already painted hover, pressed, selection, disabled,
    // and lead-focus surfaces in cell-local coordinates.
    drawJob(context, value, source.getGLStyle(), width, height);
});
```

The immutable `CellState` identifies selection, lead, list focus, enabled state,
and pointer state. `GLListStyle` resolves typography, row colors, focus color,
border geometry, padding, preferred width, and fixed row height from `list.*`
theme values. `setRowHeight(...)` provides a local geometry override. Fixed row
height keeps index-to-viewport conversion constant-time; variable-height cells
are intentionally outside the initial API.

Lists that support renaming or another in-place operation can install one
`GLList.CellEditor<T>`. The editor factory receives an `EditController` for
committing or cancelling the session. Returning `false` from `commitEditor(...)`
keeps the editor open, which allows validation or a confirmation dialog to
finish before the value is accepted.

```java
list.setCellEditor(new GLList.CellEditor<Document>() {
    @Override
    public GLContainer createEditor(GLList<Document> source, Document value,
            int index, GLList.EditController controller) {
        GLTextEditor editor = new GLTextEditor();
        editor.setText(value.name());
        editor.addActionListener(event -> controller.commit());
        editor.addCancelListener(event -> controller.cancel());
        return editor;
    }

    @Override
    public void layoutEditor(GLList<Document> source, Document value,
            int index, GLContainer editor, float width, float height) {
        editor.setBounds(8, 3, width - 16, height - 6);
    }

    @Override
    public boolean commitEditor(GLList<Document> source, Document value,
            int index, GLContainer component) {
        return rename(value, ((GLTextEditor) component).getText());
    }
});

list.startEditingAt(index);
```

Only one cell editor is active at a time. `commitEditing()` and
`cancelEditing()` expose the same lifecycle programmatically. Replacing the
model, changing row metrics, or scrolling the edited row beyond the recycled
cell range cancels the session safely. `ItemEvent.selectedBeforePress()` is
useful for desktop-style rename gestures in which a second single click on an
already selected name begins editing.

List cells are rectangular. When a list is the direct content of a
`GLScrollPane`, it also delegates its outer corner geometry to that pane, so the
viewport and scrollbar layout provide the only visible corner treatment. The
lead-selection focus outline follows those effective corners on the first and
last row only while that row actually touches the corresponding viewport edge.
Consequently, a short list does not round its final row in the middle of unused
viewport space. Corners adjacent to a scrollbar remain square.

## Trees

`GLTree<T>` projects the expanded part of a `GLTreeModel<T>` onto the same
virtualized cells and selection infrastructure as `GLList<T>`. Collapsed
descendants create no components, picking entries, or rendering work. The tree
contains its own `GLScrollPane` and can be added directly to a layout.

```java
GLDefaultTreeModel<String> model = new GLDefaultTreeModel<>();
model.addRoot("Project");
model.addChild("Project", "src");
model.addChild("src", "Main.java");

GLTree<String> tree = new GLTree<>(model);
tree.setExpanded("Project", true);
tree.setExpanded("src", true);
tree.addActivationListener((source, value) -> open(value));
```

`GLTreeModel<T>` supplies ordered roots and children and reports structural
changes. A custom model can return `isLeaf(node) == false` before children are
available; an expansion listener can then start asynchronous loading and notify
the model when the result arrives. Models must expose stable, non-null node
values and must not contain cycles.

The mutable `GLDefaultTreeModel<T>` provides unique roots and children,
subtree removal, node replacement, and `setAllowsChildren(...)` for empty lazy
branches. `setChildren(...)` replaces or reorders a branch in one notification,
which is useful when an asynchronous directory or service request completes.
Its notifications rebuild only the flattened visible projection, not a
component hierarchy.

Mouse clicks on disclosure chevrons and double-clicks on branches toggle their
expanded state. Up and Down use standard list navigation; Right expands a
branch or selects its first child, while Left collapses it or selects its
parent. The shared `GLListSelectionModel` provides single, interval, and
multiple selection. Collapsing the only selected descendant moves selection to
the collapsed parent.

Install a `GLTree.CellRenderer<T>` for icons or structured content. The callback
receives depth, expansion state, leaf state, and the horizontal position after
the disclosure area. For ordinary icon-and-label rows, retain the default
renderer and install `setItemIconProvider(...)`; `tree.iconSize` and
`tree.iconGap` control its geometry. `GLTree.CellEditor<T>` uses the standard
list editing lifecycle, including validation-aware commits and safe
cancellation when a visible row is recycled. Once an editor is installed,
every node—including a branch—can be renamed with F2 or the localized
**Rename** command in its context menu. Use
`setRenameContextMenuEnabled(false)` when an application supplies its own
context menu. `GLDefaultTreeModel.replaceNode(...)` preserves the expansion and
selection state of the replaced node.

## Scrolling

`GLScrollPane` clips rendering and picking to its viewport. Horizontal and
vertical policies independently use `AS_NEEDED`, `ALWAYS`, or `NEVER`. Content
offsets use logical pixels. The viewport stays inside the configured outer
border and padding. `scrollPane.viewportBorder` and
`scrollPane.viewportBorderWidth` define a separate inner contour around the
viewport; content is clipped at that contour's inner edge. The remaining corner
radii are carried through nested UI clips, so rows, pressed surfaces, and focus
indicators cannot cover either frame. The clip participates in the shared UI
batch and therefore does not create a layer or an additional draw call.

`GLScrollBar` models:

- `value`: current offset;
- `extent`: visible length;
- `maximum`: total content length;
- effective maximum value: `max(0, maximum - extent)`.

`setValues(value,extent,maximum)` updates these atomically. Unit and block
increments control arrow/wheel and track actions. The scroll pane synchronizes
bars, wheel input, viewport size, content bounds, and nested clipping.

The wheel scrolls vertically by default. The semantic
`scroll.horizontal` modifier (mapped to `Shift` by the bundled input map)
selects the horizontal axis, so applications may remap it without changing the
control. `setScrollUnit(...)` controls the wheel distance. Use
`setHorizontalScrollBarPolicy(...)` and `setVerticalScrollBarPolicy(...)` to
override automatic bar visibility, and `setScrollOffset(x,y)` for direct
positioning. `scrollRectToVisible(x,y,width,height)` performs the
minimal scroll required to reveal a content rectangle and clamps the result to
the available range.
