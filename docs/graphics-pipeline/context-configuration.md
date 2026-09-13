# Context Configuration

[Rendering guide](../graphics-pipeline.md) · [Next: Drawing commands](drawing.md)

`GLContext2D` begins every frame with defined defaults. `GLContainer` restores
its transform, paint, stroke, and picking state after rendering. Keep temporary
state changes local and balance every begin/end operation.

## Contents

- [Units](#units)
- [Paint, alpha, and strokes](#paint-alpha-and-strokes)
- [Coordinate systems](#coordinate-systems)
- [Depth and lighting](#depth-and-lighting)
- [Outputs and clipping](#outputs-and-clipping)
- [Shader state](#shader-state)
- [Returned values](#returned-values)

## Units

| Value | Unit |
|---|---|
| Local coordinates | Current user space; logical pixels with `TOP_LEFT_PIXELS` |
| Screen measurements | Logical pixels, independent of HiDPI framebuffer scale |
| Angles | Radians; clockwise is positive in top-left coordinates |
| Scale, alpha, and thresholds | Dimensionless |
| Colors | `java.awt.Color` |

## Paint, alpha, and strokes

The context color supplies the default for overloads without an explicit
`Color`; a color argument affects only that command.

```java
g.setColor(new Color(70, 140, 230));
g.fillRect(20, 20, 120, 50);
g.fillCircle(200, 45, 25, Color.ORANGE);
```

`setAlphaMultiplier(float)` multiplies the alpha channel of every subsequent
drawing operation without introducing a layer or another draw call. The value
must be between `0` and `1`; nested component multipliers are multiplied. This
is direct shape opacity, so overlapping translucent shapes remain individually
visible. Use a [layer](layers-effects.md) when a complete group must be
composited once after its contents have been drawn.

```java
float previous = g.getAlphaMultiplier();
try {
    g.setAlphaMultiplier(previous * 0.85f);
    drawNodeContents(g);
} finally {
    g.setAlphaMultiplier(previous);
}
```

`GLContainer#setRenderingAlphaMultiplier(float)` applies the same direct alpha
behavior to a complete component subtree. `setRenderingOpacity(float)` retains
isolated layer semantics.

### Strokes

`GLStroke` is immutable. Context convenience setters replace it with a modified
copy.

```java
GLStroke stroke = new GLStroke(3)
        .withCap(GLStroke.Cap.ROUND)
        .withJoin(GLStroke.Join.ROUND)
        .withMiterLimit(8)
        .withCurveTolerance(GLStroke.CURVE_TOLERANCE_FINE)
        .withWidthSpace(GLStroke.WidthSpace.SCREEN)
        .withDash(new float[] { 10, 6, 2, 6 }, 3);
g.setStroke(stroke);
```

| Property | Unit and behavior |
|---|---|
| Width | User units in `WORLD`; logical pixels in `SCREEN`; must be positive |
| Cap | `BUTT`, `SQUARE`, or `ROUND` |
| Join | `MITER`, `BEVEL`, or `ROUND` |
| Miter limit | Dimensionless ratio of at least `1` |
| Offset | Same space as width; `0` centers the stroke |
| Curve tolerance | Maximum visible error in logical pixels |
| Dash array and phase | Same space as width; all lengths are positive |

Odd-length dash arrays repeat like `BasicStroke` patterns. `withoutDash()`
returns a solid stroke and `getStrokeDash()` returns a defensive copy or
`null`. `GLStroke.DEFAULT` is a solid, one-unit world-space stroke.

`setCurveTolerance(float)` controls filled curves, paths, and rounded shapes;
`setStrokeCurveTolerance(float)` controls stroke geometry. Presets are
`CURVE_TOLERANCE_COARSE` (`0.4f`), `NORMAL` (`0.2f`), and `FINE` (`0.1f`).

## Coordinate systems

### Model transform

```java
GLTransform saved = g.getTransform();
try {
    g.translate(200, 120);
    g.rotate((float) Math.toRadians(15));
    g.scale(1.5f, 0.8f);
    g.fillRect(-50, -25, 100, 50, Color.ORANGE);
} finally {
    g.setTransform(saved);
}
```

`rotate(radians, anchorX, anchorY)` rotates around a local anchor;
`resetTransform()` restores identity. Recordings start in identity model space
and restore the previous transform when completed or cancelled.

### `GLTransform` reference

| Methods | Purpose |
|---|---|
| `setToIdentity`, `setTransform(...)` | Replace the affine matrix |
| `translate`, `rotate`, `scale`, `concatenate` | Append an operation |
| `transform(float[],offset,count)` | Transform in-place X/Y pairs |
| `transformVertices(float[],stride)` | Transform X/Y at the start of each vertex record |
| `transformX`, `transformY` | Calculate one transformed component |
| `isIdentity`, `isTranslation` | Test optimized forms |
| `copy`, `createInverse` | Copy or invert the matrix |
| `determinant`, `getMaximumScale` | Inspect invertibility and effective scale |
| `toMatrix4` | Return a column-major OpenGL `float[16]` |

### Projection and view

Projection changes flush open batches because the matrix is a draw-call
uniform.

| Projection | Coordinates |
|---|---|
| `TOP_LEFT_PIXELS` | Origin at top left; logical pixels |
| `CENTERED_PIXELS` | Origin at viewport center; logical pixels |
| `CENTERED_NORMALIZED` | Y spans `-1..1`; X is aspect-corrected |
| `CLIP_SPACE` | X and Y span `-1..1`; no aspect correction |
| `Projection2D.fixed(transform)` | Application-defined matrix |

```java
GLCamera2D camera = new GLCamera2D(Projection2D.CENTERED_PIXELS);
camera.setPosition(120, 40);
camera.setZoom(1.75f);
camera.setRotation((float) Math.toRadians(12));
g.setView(camera);
```

`GLView2D#createViewProjection(width,height)` receives the logical viewport
size. Custom projections implement `Projection2D#create(width,height)`.

`intersectsRedrawBounds(x,y,width,height)` tests transformed local bounds.
`getScreenBounds(...)` returns `{x,y,width,height}` in logical screen pixels.
`GLContainer` already performs component-level culling.

## Depth and lighting

`setDepthState`, `setDepth`, `clearDepth`, and `setLighting` configure 2.5D
rendering. Depth runs from `0` at the back to `1` at the front. Lighting values
are non-negative dimensionless factors. See
[Depth and 2.5D materials](depth-materials.md).

## Outputs and clipping

`clear()` uses `DEFAULT_CLEAR_COLOR`; `clear(Color)` submits open geometry and
clears enabled outputs. `clearDepth()` clears the active depth attachment.

```java
g.setOutputMask(true, false);  // Color only
g.setOutputMask(false, true);  // Picking only
g.setOutputMask(true, true);   // Normal output
```

The output mask selects attachments of the current destination, not a render
destination. Use `beginRenderTarget(...)` to change destinations.
`setTexturePickAlphaThreshold` accepts `0..1` and applies to textured picking.

```java
g.beginClip(20, 20, 300, 160);
try {
    drawScrollableContent(g);
} finally {
    g.endClip();
}
```

Use a rounded UI clip when a control's content must follow curved viewport
corners:

```java
g.beginUIRoundedClip(20, 20, 300, 160,
        GLCornerRadii.all(8));
try {
    drawUIContent(g);
} finally {
    g.endClip();
}
```

Clips are nested and recordable. GLSwing resolves their bounds to transformed,
axis-aligned logical screen coordinates and intersects them with the parent
clip. Standard UI instances retain the effective rectangle and corner radii,
so differently clipped controls remain in one batch and use the same boundary
for color and picking. Nested rectangular clips remain square unless they
share an outer rounded corner.

`beginUIRoundedClip` applies rounded antialiasing only to the standard UI
pipeline. Other pipelines, including custom UI shaders, receive its rectangular
bounding scissor and flush when that scissor changes. Use a layer or path-based
solution when non-UI content requires an arbitrary curved boundary.
`getClipDepth()` helps detect unbalanced calls.

## Shader state

Filled, texture, text, sprite, and UI pipelines use separate shader contracts.
Shader and uniform changes validate the target pipeline, flush its batch, and
can be recorded. See [Custom shaders and uniforms](shaders.md).

## Returned values

- `getTransform()` and `getProjection()` return independent copies.
- `getStroke()` is safe to retain because `GLStroke` is immutable.
- `getStrokeDash()` returns a defensive copy.
- Shader getters return the custom program or `null` for the default.
- Clip, layer, and render-target depth getters return current nesting counts.
