# Controls

[UI guide](../ui-framework.md) · [Components](components.md) · [Next: Themes and localization](themes-localization.md)

All standard controls inherit input, focus, enabled state, picking, and theme
behavior from `GLControl`. Geometry and text measurements use logical pixels.

## Contents

- [Common behavior](#common-behavior)
- [Control overview](#control-overview)
- [Labels, buttons, checkboxes, and lines](#labels-buttons-checkboxes-and-lines)
- [Sliders and slider groups](#sliders-and-slider-groups)
- [Text editor](#text-editor)
- [Combo boxes and menus](#combo-boxes-and-menus)
- [Tabs](#tabs)
- [Scrolling](#scrolling)

## Common behavior

`setEnabled(false)` cancels active interaction and rejects new input.
`setFocusable(boolean)` controls keyboard-focus eligibility. Standard state is
available through `GLControlState` (`NORMAL`, `HOVERED`, `PRESSED`, and disabled
or focus-related state where supported).

Every standard control has an immutable typed style. `setGLStyle(...)` sets a
local override; `clearGLStyle()` returns to inherited theme values. Subclasses
implement protected `control...` hooks without bypassing `GLControl`'s final
event sequencing, capture, or state management.

## Control overview

| Type | Creation | Principal model |
|---|---|---|
| `GLLabel` | `new GLLabel(text)` | Text and alignment |
| `GLButton` | `new GLButton(text)` | Action listeners and `doClick()` |
| `GLCheckBox` | Text constructor | Selected state and action |
| `GLLine` | Orientation constructor | Stroke and relative endpoints |
| `GLSlider` | `fixed(...)` or `free(...)` | Value, range, step, editor, reset |
| `GLSliderGroup` | `new GLSliderGroup(sliders...)` | Coordinated multi-slider gesture |
| `GLTextEditor` | Text constructor | Single-line editing and validation |
| `GLComboBox<T>` | Empty or item-list constructor | Selection, sorting, popup |
| `GLPopupMenu` | `new GLPopupMenu()` | Menu items and submenus |
| `GLMenuBar` | `new GLMenuBar()` | Top-level menus |
| `GLTabbedPane` | `new GLTabbedPane()` | Tab metadata and selected content |
| `GLScrollPane` | Empty or content constructor | Viewport, policies, offsets |
| `GLScrollBar` | Orientation constructor | Value, extent, maximum |

## Labels, buttons, checkboxes, and lines

Labels display non-editable text. Buttons fire their semantic action after a
valid click; `doClick()` invokes the same action programmatically. Checkboxes
combine a selected boolean with button-like activation. Use `get/setText` and
`get/setSelected` for their models.

Buttons and checkboxes are not focusable by default in 0.1.0; enable focus
explicitly when the application supplies keyboard operation.

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
history transaction.

## Text editor

`GLTextEditor` is a single-line editor with caret, selection, clipboard,
horizontal scrolling, and edge fades.

```java
GLTextEditor editor = new GLTextEditor("12.5");
editor.setInputFilter(GLTextInputFormats.DECIMAL_NUMBER);
editor.setInputVerifier(text -> parseAndValidate(text));
```

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

`GLMenuItem` supports text, accelerator display text, action command, listeners,
`doClick()`, and an optional submenu. Accelerator text is presentational; bind
the actual global shortcut in the application.

```java
GLPopupMenu menu = new GLPopupMenu();
menu.add(new GLMenuItem("Delete", event -> deleteSelection()));
menu.show(host, screenX, screenY);
```

`show(host,x,y)` expects host coordinates. Menus and submenus render as topmost
overlays. `setImmediateCpuPickingEnabled(true)` is available when menu hover
must update without waiting for the normal GPU-picking snapshot.

## Tabs

`GLTabbedPane` shows only selected content. Applications can change titles,
disable or remove tabs, and select by index or component. An overflowing tab
strip scrolls with the wheel and ellipsizes long titles.

## Scrolling

`GLScrollPane` clips rendering and picking to its viewport. Horizontal and
vertical policies independently use `AS_NEEDED`, `ALWAYS`, or `NEVER`. Content
offsets use logical pixels.

`GLScrollBar` models:

- `value`: current offset;
- `extent`: visible length;
- `maximum`: total content length;
- effective maximum value: `max(0, maximum - extent)`.

`setValues(value,extent,maximum)` updates these atomically. Unit and block
increments control arrow/wheel and track actions. The scroll pane synchronizes
bars, wheel input, viewport size, content bounds, and nested clipping.
