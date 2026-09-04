# Input Configuration

[UI guide](../ui-framework.md) · [Controls](controls.md) · [Node editor](nodes.md)

GLSwing resolves keyboard commands and pointer modifiers through immutable
`GLInputMap` objects. Applications can use the bundled defaults, load a
packaged resource, or load an external UTF-8 `.glinput` file. A map set on a
container applies to its complete subtree; descendants may start a nearer
input scope.

## Contents

- [Loading and applying a map](#loading-and-applying-a-map)
- [File format](#file-format)
- [Key bindings](#key-bindings)
- [Pointer modifiers and values](#pointer-modifiers-and-values)
- [Inheritance and scopes](#inheritance-and-scopes)
- [Live reload](#live-reload)
- [Editable shortcuts and preferences](#editable-shortcuts-and-preferences)
- [Editing input-map source](#editing-input-map-source)
- [Standard actions](#standard-actions)
- [Application actions](#application-actions)

## Loading and applying a map

```java
GLInputMap input = GLInputMapLoader.load(Path.of("application.glinput"));
host.setGLInputMap(input);
```

Use `GLInputMapLoader.loadResource(...)` for class-path resources.
`loadOrDefault(...)` returns `GLInputMaps.DEFAULT` when an optional external
file cannot be loaded. Required configuration should use `load(...)` and
report `GLInputMapLoadException` to the user.

The fallback overloads mirror theme loading:

```java
GLInputMap optional = GLInputMapLoader.loadOrDefault(
        path, GLInputMaps.DEFAULT, this::reportInputError);
```

## File format

Files contain one `key=value` property per line. Blank lines and lines beginning
with `#` are ignored.

| Prefix | Purpose | Example |
|---|---|---|
| `key.` | Alternative key combinations | `key.history.undo=shortcut+Z` |
| `modifier.` | Required modifier combination | `modifier.menu.mnemonic=ALT` |
| `value.` | Finite positive behavior value | `value.slider.precise-factor=0.1` |

Invalid properties, duplicate entries, invalid values, missing files, and
inheritance cycles cause `GLInputMapLoadException`.

## Key bindings

Separate modifiers and the key with `+`; separate alternatives with commas.
Names correspond to `KeyEvent.VK_*` names without the `VK_` prefix. Supported
modifiers are `SHIFT`, `CTRL`, `ALT`, `META`, and `ALT_GRAPH`. `shortcut`
resolves to Control on Windows and Linux and Command/Meta on macOS.

```properties
key.history.undo=shortcut+Z
key.history.redo=shortcut+shift+Z, shortcut+Y
key.application.save=shortcut+S
modifier.menu.mnemonic=ALT
```

A binding matches its exact keyboard modifiers. `CTRL+S` therefore does not
also match `CTRL+SHIFT+S`.

## Pointer modifiers and values

```properties
modifier.slider.precise=SHIFT
modifier.slider.coarse=CTRL
value.slider.precise-factor=0.1
value.slider.coarse-factor=10
value.text.history-coalesce-millis=750
value.render.interaction-settle-millis=75
value.node.interactive-curve-tolerance-factor=3

modifier.node.horizontal-wheel=CTRL
modifier.node.vertical-wheel=SHIFT
modifier.node.toggle-selection=SHIFT
modifier.list.extend-selection=SHIFT
modifier.list.toggle-selection=shortcut
```

An individual slider may override configured factors with
`setPreciseDragFactor(...)` and `setCoarseDragFactor(...)`.
`text.history-coalesce-millis` controls how long compatible consecutive typing
or deletion operations remain one text-editor undo step. Each editor may
override the inherited value with `setHistoryCoalesceMillis(...)`; zero disables
coalescing for that editor.

`render.interaction-settle-millis` is the idle period before GLSwing rebuilds
GPU picking after continuous color-only updates such as wheel zooming. A
pointer press ends the period immediately and waits for current picking before
dispatch. `node.interactive-curve-tolerance-factor` multiplies the node
editor's normal Bezier tolerance during those updates; values above one reduce
tessellation work, while the settled frame always restores full quality.

## Inheritance and scopes

An external file can inherit the bundled defaults or a sibling file:

```properties
input.extends=default
key.history.redo=shortcut+shift+Z
```

Hosts expose `setGLInputMap(...)`. Every `GLContainer` resolves the nearest map
from itself, an ancestor, or its host. Use `setGLInputMap(...)` to start a local
scope and `clearGLInputMap()` to resume inheritance. Independent editor
surfaces in one canvas can therefore use different interaction conventions.

## Live reload

`GLInputMapWatcher` follows the same lifecycle as `GLThemeWatcher`. It performs
an initial load, debounces filesystem changes, dispatches callbacks on the AWT
event thread, and keeps the last valid map active after an incomplete write.

```java
GLInputMapWatcher watcher = GLInputMapWatcher.watch(
        path,
        host::setGLInputMap,
        this::reportInputError);
```

Call `reloadNow()` for an explicit refresh and close the watcher when its host
is disposed.

## Editable shortcuts and preferences

`GLInputMapEditor` keeps a base map and user overrides separate. It supports
shortcut dialogs, accessibility profiles, and game control menus.

```java
GLInputMapEditor editor = GLInputMapEditor.loadOrCreate(
        userInputFile, GLInputMaps.DEFAULT);
editor.bind("application.save", "shortcut+S");
editor.setModifier(GLInputActions.SLIDER_PRECISE, "SHIFT");
editor.setValue(GLInputActions.SLIDER_PRECISE_FACTOR, 0.05);

host.setGLInputMap(editor.build()); // preview
editor.save(userInputFile);         // persist
```

Capture a binding from an AWT event and inspect conflicts before assigning it:

```java
GLKeyBinding captured = GLKeyBinding.from(keyEvent);
List<String> conflicts = editor.findActions(captured);
editor.bind("application.save", captured);
```

Captured platform shortcut modifiers are saved using the portable `shortcut`
alias. `unsetBinding(...)`, `unsetModifier(...)`, and `unsetValue(...)` restore
inheritance. `unbind(...)` explicitly disables an inherited key action, and
`reset()` removes the complete user override layer. Saving atomically replaces
the file and writes only explicit overrides.

## Editing input-map source

`GLCodeEditor` recognizes `.glinput` document URIs and installs
`GLCodeLanguages.GLINPUT` automatically:

```java
Path file = Path.of("config", "application.glinput");
GLCodeEditor editor = new GLCodeEditor(Files.readString(file), file.toUri());
```

The built-in language validates shortcut alternatives, key names, modifiers,
positive behavior values, duplicate properties, inherited files, and
inheritance cycles using the same contracts as `GLInputMapLoader`. Completion
and hover documentation cover every bundled action and preference. Application
entries under `key.*`, `modifier.*`, and `value.*` remain valid and receive the
same value validation. File completion and navigation from `input.extends`
require a real file URI.

## Standard actions

`GLInputActions` defines identifiers for history, focus traversal, activation,
directional and page navigation, list selection, slider drag modes, and
node-editor interaction. When
replacing standard bindings, preserve accessible alternatives such as Enter or
Space for activation and directional keys for navigation.

Lists combine navigation bindings with `list.extend-selection` and
`list.toggle-selection`; modifier matching does not require duplicate
`Shift+Down` or `shortcut+Down` key declarations. `list.select-all` is a normal
key action and defaults to `shortcut+A`.

Trees inherit list navigation and use `tree.edit` to start inline editing of
the selected node. The bundled map assigns F2, matching the accelerator shown
by the standard localized **Rename** context-menu command.

## Application actions

The default map provides `document.save` (`shortcut+S`),
`document.save-as` (`shortcut+shift+S`), and `document.save-all`
(`shortcut+alt+S`) as semantic application-level actions.
Handle them on a `GLKeyTarget` ancestor of the active editor so unconsumed key
events bubble naturally from the editor to the document surface:

```java
if (container.getGLInputMap().matches(GLInputActions.DOCUMENT_SAVE, event)) {
    saveDocument();
    event.consume();
}
if (container.getGLInputMap().matches(GLInputActions.DOCUMENT_SAVE_ALL, event)) {
    saveAllDocuments();
    event.consume();
}
```

Applications may define and query additional action names in the same way.

Menu shortcut labels are presentation text; dispatch remains driven by the
input map.

Menu mnemonics are object-specific key codes rather than global shortcuts.
`modifier.menu.mnemonic` configures the modifier used to open a top-level menu;
the default is `ALT`. Once a menu is open, pressing an item's mnemonic activates
that item or opens its submenu. Assign mnemonics through `GLMenuBarItem` and
`GLMenuItem`.
