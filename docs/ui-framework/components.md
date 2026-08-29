# Components, Containers, and Hosts

[UI guide](../ui-framework.md) · [Next: Layout](layout.md)

GLSwing UI is a lightweight component tree rendered by one OpenGL host.
`GLComponent` defines bounds, visibility, focus, invalidation, and lifecycle;
`GLContainer` adds children, layout, recording, clipping, and inherited state.

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

Theme, localizer, focus, font cache, and `GLSaveManager` flow from the host
through containers. A container can override theme or localization for its
subtree. Hooks `themeChanged()` and `localizerChanged()` invalidate dependent
appearance. Use `requestGLFocus(...)` and `clearGLFocus(subtree)` for keyboard
focus.

## Hosts

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

All hosts expose frame metrics, font access, theme/localizer configuration,
focus, history, render resolution, and GL resource callbacks. Call `close()` to
stop animation and release host resources.

## Dialogs

`GLDialog` is a GPU-rendered dialog hosted in the component tree. Modality modes
include `MODELESS` and `OWNER_MODAL`. Set content and bounds before showing it;
use `toFront()` when application logic activates an existing dialog.
