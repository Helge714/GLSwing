# Accessibility

[UI guide](../ui-framework.md) · [Controls](controls.md) · [Input configuration](input-configuration.md)

GLSwing exposes its GPU-rendered component hierarchy through Java's standard
`javax.accessibility` API. `GLSwingPanel` and `GLSwingContainerCanvas` act as
native roots; their lightweight `GLContainer` descendants appear as virtual
accessible children even though they are not AWT components.

## Contents

- [Standard semantics](#standard-semantics)
- [Names and descriptions](#names-and-descriptions)
- [Custom controls](#custom-controls)
- [Keyboard access](#keyboard-access)
- [Host integration](#host-integration)

## Standard semantics

Standard controls publish roles and their relevant dynamic state:

| Control | Java role and behavior |
|---|---|
| `GLLabel` | Label |
| `GLButton` | Push button with an invokable click action |
| `GLCheckBox` | Check box with checked state and toggle action |
| `GLComboBox<T>` | Combo box with expanded/collapsed state and toggle action |
| `GLSlider` | Slider with readable and writable current, minimum, and maximum values |
| `GLTextEditor` | Editable single-line text with content, caret, and selection access |
| `GLTextArea` and `GLCodeEditor` | Editable or read-only multiline text with content, caret, and selection access |
| `GLList<T>` | List, including multiselectable state where configured |
| `GLTree<T>` | Tree |
| `GLTabbedPane` | Page-tab list |
| Menu controls | Menu bar, menu, popup menu, and actionable menu items |
| `GLScrollPane` and `GLScrollBar` | Scroll pane plus oriented, readable, and writable scroll values |

Individual tabs expose their title, selected state, and selection action.
Visible virtualized list rows expose list-item names, selected state, and a
selection action. Tree branches add their expanded or collapsed state.

Enabled, visible, showing, focusable, and focused states are derived from the
live component. Accessibility actions use the same public control operations as
pointer and keyboard input, so disabled controls cannot be invoked through the
accessibility bridge.

## Names and descriptions

Controls with visible text use that text as their default accessible name.
Applications should provide an explicit name for icon-only controls and a
description when the result of an action is not clear from the name:

```java
GLButton sampleColor = new GLButton(eyedropperAction);
sampleColor.setAccessibleName("Sample screen color");
sampleColor.setAccessibleDescription(
        "Selects a color from any visible point on the screen");
```

`setAccessibleName(...)` overrides the normal component name only for assistive
technology. Passing `null` restores the automatic name. Use `setName(...)` for
a stable name shared by diagnostics, testing, and accessibility.

Purely decorative nodes can be removed from the accessible tree together with
their descendants:

```java
separator.setAccessibilityEnabled(false);
```

For an implementation-only layout wrapper whose descendants must remain
accessible, use `setAccessibilityTransparent(true)`. The wrapper is omitted
and its semantic descendants are promoted to the nearest exposed ancestor.
Their reported coordinates are adjusted accordingly. Standard tab hosts,
scroll viewports, and popup overlays use this mechanism.

Do not hide an interactive control solely because it has no visible text;
provide an accessible name instead.

## Custom controls

`GLContainer` defaults to `AccessibleRole.PANEL` when it owns children and to
`AccessibleRole.CANVAS` when it is a leaf. A custom component can provide a
standard role either declaratively or through its implementation:

```java
statusIndicator.setAccessibleRole(AccessibleRole.STATUS_BAR);
statusIndicator.setAccessibleName("Connection status");
```

Reusable control subclasses can override `defaultAccessibleRole()`,
`collectAccessibleStates(...)`, the `accessibleAction...` hooks, and the
numeric `accessible...Value()` hooks. State and value mutations should call
`fireAccessibleStateChange(...)` or `fireAccessibleValueChange(...)` so active
assistive technology receives an immediate update.

Custom list renderers can use `setItemAccessibleNameProvider(...)`,
`setItemAccessibleRole(...)`, and `setItemAccessibleStateProvider(...)` to keep
row semantics independent of their visual renderer.

## Keyboard access

Accessibility does not replace keyboard behavior. Interactive controls remain
in GLSwing's normal focus traversal, activated through semantic input actions,
and display their themed focus indication. `setFocusable(false)` removes a
control from traversal; `setEnabled(false)` also prevents accessibility actions.
Use external `.glinput` mappings when an application needs platform-specific
bindings. See [Input configuration](input-configuration.md).

## Host integration

Assistive technology starts at `GLSwingPanel#getAccessibleContext()` or
`GLSwingContainerCanvas#getAccessibleContext()`. The returned context includes
the currently visible and accessibility-enabled GL children. Each child's
context supplies its parent, index, bounds, screen position when showing, and
the accessible node at a local point.

Java Accessibility availability and screen-reader behavior depend on the JDK,
operating system, and installed accessibility bridge. Applications should test
their complete workflows with the target screen reader and platform.
