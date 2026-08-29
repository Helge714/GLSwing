# Picking and Mouse Targets

[Rendering guide](../graphics-pipeline.md) · [Drawing commands](drawing.md) · [Next: Recording](recording.md)

GLSwing renders object identity into a picking attachment alongside color.
Draws may carry either a numeric ID, a direct `GLMouseTarget`, or neither.

## Contents

- [Numeric IDs](#numeric-ids)
- [Direct targets](#direct-targets)
- [Pick results and events](#pick-results-and-events)
- [Pointer capture](#pointer-capture)
- [Alpha and output masks](#alpha-and-output-masks)
- [Timing and lifetime](#timing-and-lifetime)

## Numeric IDs

```java
g.setPickId(42);
g.fillRect(x, y, width, height, color);
g.clearPickId();
```

`NO_PICK_ID` means no numeric object. `pick(x,y)` returns the ID from the most
recent completed picking snapshot. Prefer IDs when application state already
has a stable integer key.

## Direct targets

```java
g.setMouseTarget(target);
g.drawImage(image, x, y);
g.clearMouseTarget();
```

`GLMouseTarget` receives mouse callbacks without a separate ID map. UI controls
and containers use this mechanism. `addUIHitArea(...)` can add an analytic hit
region when the visible content is too small or contains gaps.

## Pick results and events

`pickResult(x,y)` returns `GLPickResult.none()` or a hit containing the numeric
ID, target, screen coordinates, and—when available—target-local coordinates.
Use `hasId()`, `hasTarget()`, `hasLocalPosition()`, `getId()`, `getTarget()`,
and `getTarget(Class<T>)` to inspect it.

`GLMouseEvent` exposes button, click count, modifiers, timestamp, screen and
local coordinates, deltas, and the original AWT event. Call `consume()` to stop
further dispatch. `GLMouseWheelEvent` additionally exposes precise rotation,
wheel rotation, scroll amount, and scroll type.

Targets can use `GLPickResult#toScreen(localX,localY)`, `at(...)`, and
`retarget(...)` when forwarding an interaction through a composite component.

## Pointer capture

The host's `GLPointerController` keeps drag and release events routed to the
pressed target even when the pointer leaves it. A target receives
`mouseCaptureLost` if capture ends unexpectedly. Pointer warping is available
through `requestPointerWarp(...)`; use it only for interactions that require
unbounded relative movement.

## Alpha and output masks

Texture-backed commands apply `setTexturePickAlphaThreshold(float)` to texture
alpha multiplied by tint alpha. This prevents transparent pixels from becoming
hits. Analytic shapes, text, and UI geometry use their own fragment coverage.

```java
g.setOutputMask(false, true);
drawPickingOnly(g);
g.setOutputMask(true, true);
```

Color and picking writes can be enabled independently. `drawTexture`,
`drawImage`, `drawUIImage`, and `drawRenderTarget` preserve their configured
pick behavior. A render target must have picking enabled to retain pick data.

## Timing and lifetime

Picking normally reads an asynchronous snapshot from the last rendered frame;
event handlers should not assume that newly submitted geometry is immediately
available. `repaintAndRebuildPicking(source)` requests a fresh frame and pick
snapshot. Immediate CPU picking is available on selected UI hosts for cases
such as menu hover.

Recordings preserve numeric IDs. Direct mouse targets are stored as
`WeakReference`s, so a recording does not keep a detached component alive.
`isMouseTargetEnabled()` allows a target to reject interaction without changing
the recorded geometry.
