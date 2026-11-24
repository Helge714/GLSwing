# Components, Containers, and Hosts

[UI guide](../ui-framework.md) · [Next: Layout](layout.md)

GLSwing UI is a lightweight component tree rendered by one OpenGL host.
`GLContainer` is the public component base. It defines bounds, visibility,
focus, invalidation, lifecycle, children, layout, recording, clipping, and
inherited state. Leaf-style controls specialize that same container contract;
there is no separate heavyweight peer for each control.

## Contents

- [Component lifecycle](#component-lifecycle)
- [Bounds and visibility](#bounds-and-visibility)
- [Children and Z-order](#children-and-z-order)
- [Rendering and invalidation](#rendering-and-invalidation)
- [Inherited services](#inherited-services)
- [Hosts](#hosts)
- [Dialogs](#dialogs)

## Component lifecycle

Components become usable when attached to a `GLParent` and stop receiving host
services when detached. Override `attached(GLParent)` and `detached(GLParent)`
for lifecycle work. Release application-owned resources when the component or
host is closed; do not allocate GPU resources in per-frame rendering.

The rendering sequence is:

1. apply component position through `transformComponent(GLContext2D)`;
2. replay or rebuild `record(GLContext2D)`;
3. invoke dynamic `render(GLContext2D)` work;
4. render container-specific content and children.

Subclass the narrowest appropriate hook. Do not call framework sequencing hooks
manually. `resized(width,height)` is invoked after size changes.

## Bounds and visibility

```java
component.setBounds(x, y, width, height);
component.setVisible(true);
component.setName("toolbar.save");
```

Bounds use parent-local logical pixels. Width and height must be non-negative.
`setLocation` and `setSize` update one part. Hidden components are neither
painted nor interactive. Names support diagnostics and application lookup;
`hasExplicitName()` distinguishes assigned names from defaults.

## Children and Z-order

```java
container.addGL(child);
container.removeGL(child);
child.toFront();
```

`getGLChildren()` exposes logical child order and
`getGLChildrenInPaintOrder()` the effective painting order. `bringToFront`,
`sendToBack`, `moveForward`, and `moveBackward` change Z-order. A component has
one parent; detach it before inserting it elsewhere.

## Rendering and invalidation

`repaintGL()` invalidates visible output. `invalidateRecording()` rebuilds
cached commands, and `invalidateGLLayout()` requests layout. Use the least
expensive operation that matches the change. `repaintGL(source)` and
`repaintAndRebuildPicking(source)` retain the originating component for damage
and interaction updates.

Containers cull children against damage and visible bounds. Override or
configure `visualOverflow` for shadows and other pixels extending beyond normal
bounds. `getLastRenderedScreenBounds()` reports the latest logical screen
rectangle used by the host.

## Inherited services

Theme, localizer, focus, font cache, and `GLHistoryManager` flow from the host
through containers. A container can override theme, localization, or history
for its subtree. A local history manager creates an independent undo/redo scope
for a document or editing surface. Hooks `themeChanged()` and `localizerChanged()` invalidate dependent
appearance. Use `requestGLFocus(...)` and `clearGLFocus(subtree)` for keyboard
focus.

## Hosts

New standalone applications should normally use
[`GLApplication`](application-startup.md). It owns host creation, standard
configuration loading, startup progress, first-frame handoff, and orderly
shutdown. Instantiate the hosts below directly when embedding GLSwing in an
existing window or when an application requires a custom host hierarchy.

### `GLFrame`

`GLFrame` is the managed standalone window. It combines a `JFrame`, direct
canvas, animator, resource lifecycle, and shutdown. Add lightweight content
with `addGL(...)`. Use the GPU `GLMenuBar`; inherited Swing content methods are
deprecated where they bypass GLSwing behavior.

### `GLSwingContainerCanvas`

The direct canvas provides the lowest-overhead host for embedding in a custom
AWT/Swing window. The application owns the surrounding window and animator.

### `GLSwingPanel`

`GLSwingPanel` embeds rendered output in ordinary Swing layout. It performs an
additional transfer compared with the direct canvas but integrates with
lightweight Swing containers.

Custom hosts do not require an animator for a static interface. Calls to
`repaintGL()` schedule individual AWT render passes, so input, caret blinking,
resource updates, and control state changes remain responsive without
continuously presenting an unchanged framebuffer. Attach an animator only for
content that changes independently of input events, such as simulations or
time-based animation, and stop it when that activity ends. The GUI showcase
uses this event-driven mode for both `GLSwingPanel` and
`GLSwingContainerCanvas`.

All hosts expose frame metrics, font access, theme/localizer configuration,
focus, history, render resolution, and GL resource callbacks. Call `close()` to
stop animation and release host resources. `firstFrameFuture()` completes after
the host's first successful presentation; `nextFrameFuture()` can be used when
an integration must await one subsequent presentation. `GLApplication` uses
the latter internally after attaching the completed root surface.

## Dialogs

`GLDialog` is a GPU-rendered dialog hosted in the component tree. Modality modes
include `MODELESS` and `OWNER_MODAL`. Set content and bounds before showing it;
use `toFront()` when application logic activates an existing dialog.

System window chrome remains the default. Select optional GLSwing-rendered
chrome before `show(...)` when a dialog should follow the application theme:

```java
GLDialog dialog = new GLDialog("Preferences", content);
dialog.setDecoration(GLDialog.Decoration.THEMED);
dialog.setCornerRadius(12);
dialog.show(owner);
```

`Decoration.NONE` creates an undecorated surface without a title bar. Both
undecorated modes use `dialog.cornerRadius` unless `setCornerRadius(...)`
overrides it; `clearCornerRadius()` restores theme resolution. Shaped-window
support is desktop-dependent, so unsupported platforms retain square native
window bounds. The themed title bar supplies dragging and a close action. Its
close target is flat in the normal state and uses configurable hover and pressed
colors while preserving the window's outer top-right radius. Relevant theme
keys include `dialog.titleBar.height`, `dialog.titleBar.background`,
`dialog.titleBar.text`, `dialog.titleBar.border`,
`dialog.titleBar.borderWidth`, `dialog.close.text`,
`dialog.close.hover`, and `dialog.close.pressed`. On desktops supporting
per-pixel translucent windows, GLSwing renders an antialiased outer contour and
a soft shadow configured through `dialog.border*` and `dialog.shadow.*`. Other
desktops use the native shaped-window fallback. The close target's hit area is
the complete hover surface rather than only the visible cross. The dialog body
and border share one analytic primitive, while the title separator is an opaque
inner fill; neither seam exposes the transparent window surface. The configured
dialog size
continues to describe application content; themed chrome adds its title-bar
height instead of reducing the content area. Native resize handles are not
available on undecorated windows, so use themed decoration primarily for fixed
dialogs or provide application-specific resize handles.
