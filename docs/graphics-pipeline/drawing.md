# Drawing Commands and Rendering Paths

[Rendering guide](../graphics-pipeline.md) · [Context configuration](context-configuration.md) · [Next: Picking](picking.md)

`GLContext2D` provides immediate-mode commands that feed specialized GPU
batches. Explicit-color overloads affect one command; other overloads use the
current context paint and stroke.

## Contents

- [Units and command modes](#units-and-command-modes)
- [Filled geometry](#filled-geometry)
- [Textures and images](#textures-and-images)
- [Sprites and materials](#sprites-and-materials)
- [Text](#text)
- [UI primitives](#ui-primitives)
- [Paths and paints](#paths-and-paints)
- [Choosing an operation](#choosing-an-operation)

## Units and command modes

Geometry uses current user coordinates. With `TOP_LEFT_PIXELS`, these are
logical pixels. Angles are radians. Texture source rectangles are image pixels.
Progress, opacity, and interpolation amounts are dimensionless.

Immediate calls submit into the current frame. A `GLRecording` stores prepared
commands for replay; `GLSpriteBuffer` retains large static sprite sets on the
GPU. All modes preserve command order.

## Filled geometry

Common primitives include:

```java
g.fillRect(x, y, width, height, color);
g.drawRect(x, y, width, height, stroke, color);
g.fillCircle(centerX, centerY, radius, color);
g.drawEllipse(x, y, width, height, color);
g.fillTriangle(x1, y1, x2, y2, x3, y3, color);
g.drawLine(x1, y1, x2, y2, color);
```

Rectangle and ellipse bounds use the top-left corner plus non-negative width
and height. Circle arguments use center and radius. Polygon point arrays contain
alternating `x,y` values. `drawPolyline` remains open; `drawPolygon` closes the
outline. Per-vertex overloads interpolate supplied colors.

`GLStroke` controls line width, width space, dash, phase, cap, join, miter,
offset, and curve tolerance. Screen-space strokes stay visually constant under
view zoom; world-space strokes scale with geometry.

Cubic Bézier commands receive start, two control points, and end. Tolerance
controls tessellation error in logical screen pixels.

## Textures and images

```java
g.drawTexture(texture, x, y, width, height);
g.drawTexture(texture, sx, sy, sw, sh, x, y, width, height);
g.drawImage(image, x, y);
g.drawRenderTarget(target, x, y, width, height);
```

`drawTexture` is the low-level texture route. `drawImage` uses managed
`GLImage` storage and atlas UVs. Tint and picking alpha apply where supported.
See [Resources](resources.md) for storage and lifetime.

## Sprites and materials

`drawSprite(sprite,x,y)` uses the sprite's immutable description.
`drawMaterial(material,x,y,w,h)` draws one material quad. Overloads can supply
tint and depth. Use this pipeline for color/depth/normal maps, lighting,
depth-tested 2.5D content, and static `GLSpriteBuffer` batches.

## Text

```java
g.drawText(font, "Status", x, baselineY, color);
g.drawUIText(font, "Status", x, baselineY, color);
```

Text Y coordinates are baselines. `drawText` uses the general MSDF text batch;
`drawUIText` shares the UI pipeline. Use font metrics to position and size text
instead of assuming glyph height equals font size.

## UI primitives

The UI pipeline provides instanced shapes, borders, gradients, shadows, text,
images, icons, and hit areas:

- `fillUIRoundRect` and `drawUIRoundRect`;
- `fillAndDrawUIRoundRect`;
- `fillUIVerticalGradientRoundRect` and `fillUIProgressRoundRect`;
- `drawUICheckMark`, `drawUIChevron`, and `drawUIRoundRectShadow`;
- `drawUIText`, `drawUIImage`, `drawUIIcon`, and `addUIHitArea`.

Radii use `GLCornerRadii` or a uniform radius. Border and symbol stroke widths
accept `GLStroke.WidthSpace` where provided. `progress` uses `0..1`.
`addUIHitArea` writes picking coverage without visible color.

## Paths and paints

```java
GLPath2D path = new GLPath2D()
        .moveTo(20, 20)
        .lineTo(120, 20)
        .cubicTo(160, 20, 160, 100, 120, 100)
        .closePath();
path.setFillRule(GLPath2D.FillRule.NON_ZERO);
g.fillPath(path, GLPaint2D.solid(color));
g.drawPath(path, stroke, outline);
```

`EVEN_ODD` is useful for explicit holes; `NON_ZERO` follows winding direction.
`GLPaint2D` supports solid and interpolated fills. Color interpolation modes
include `SRGB`, `LINEAR_RGB`, `OKLAB`, `HSV_SHORTEST`, and `OKLCH_SHORTEST`.
`GLColorInterpolator.interpolate(start,end,amount)` computes CPU colors.

`GLShape2D` creates rectangles, rounded rectangles, circles, ellipses,
polygons, transformed shapes, and combinations. Convert with `toPath()` for
path filling, stroking, icon creation, or layer path clipping.

## Choosing an operation

| Requirement | Operation |
|---|---|
| Solid vector geometry | `fill...` / `draw...` |
| Managed raster image | `drawImage` |
| UI control artwork | `fillUI...`, `drawUI...`, `drawUIText` |
| Depth/normal maps and lighting | `drawSprite` / `drawMaterial` |
| Reusable vector icon | `GLIcon` with `drawIcon` or `drawUIIcon` |
| Arbitrary vector contour | `GLPath2D` |
| Cached mixed commands | `GLRecording` |
| Large immutable sprite set | `GLSpriteBuffer` |

Switching pipelines may flush a batch. Preserve visual order first, then group
compatible adjacent work where the composition permits it.
