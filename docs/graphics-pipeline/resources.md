# Images, Textures, Fonts, Icons, Paths, and SVG

[Rendering guide](../graphics-pipeline.md) · [Drawing commands](drawing.md) · [Next: Render targets](render-targets.md)

This chapter covers reusable rendering resources. Member-level constructor and
method details are available in the generated Javadoc.

## Contents

- [`GLImage`](#glimage)
- [Images and UI images](#images-and-ui-images)
- [`GLIcon`](#glicon)
- [`GLTexture`](#gltexture)
- [`GLFont`](#glfont)
- [Paths and shapes](#paths-and-shapes)
- [SVG documents](#svg-documents)
- [Lifetime](#lifetime)

## `GLImage`

`GLImage` is the standard managed RGBA image. Its immutable `GLImageOptions`
select storage, usage, filtering, wrapping, and mipmapping.

```java
GLImage image = GLImage.of(bufferedImage,
        GLImageOptions.DEFAULT
                .withStorage(GLImageOptions.Storage.ATLAS)
                .withUsage(GLImageOptions.Usage.STATIC)
                .withFilter(GLImageOptions.Filter.LINEAR)
                .withMipmapping(GLImageOptions.Mipmapping.ENABLED));
```

| Option | Choices and use |
|---|---|
| Storage | `AUTO`, `ATLAS`, or `STANDALONE`; use standalone for large or repeating images |
| Usage | `STATIC` for immutable content, `DYNAMIC` when `update(...)` is required |
| Filter | `LINEAR` for smooth scaling, `NEAREST` for pixel art |
| Wrap | `CLAMP` for ordinary images, `REPEAT` for tiled standalone textures |
| Mipmapping | Reduces aliasing when images are minified |

Convenience presets include `atlas()`, `standalone()`, `dynamic()`,
`pixelArt()`, and `repeating()`. Atlas padding protects filtered and mipmapped
edges. `prepareAsync()` performs CPU preparation; the first draw completes any
required GL upload on the rendering thread.

Dynamic images retain standalone storage. Update their content with
`update(...)`; do not place them in `GLSpriteBuffer`, whose texture references
must remain stable.

## Images and UI images

`drawImage` and `drawTexture` use the general texture batch and obey model/view
transforms. `drawUIImage` uses the instanced UI batch and is appropriate inside
controls. Source rectangles are image pixels; destination rectangles use the
current user coordinate system.

## `GLIcon`

`GLIcon` represents reusable vector-like icon content backed by `GLPath2D` or
an adaptively rasterized RGBA source.

```java
GLPath2D path = GLPath2D.fromSvgPath("M 2 12 L 9 19 L 22 4");
GLIcon icon = GLIcon.fromPath(path);
g.drawUIIcon(icon, x, y, width, height, color);
```

`drawIcon` uses the general pipeline; `drawUIIcon` participates in UI batching.
Monochrome icons accept a draw color. RGBA icons preserve source colors and can
cache variants selected for the physical display size.

`GLIconOptions.fixed(resolution)` creates one fixed raster size. Adaptive
options control minimum and maximum resolution, resolution step, oversampling,
raster policy, and rasterization delay. `prepareAsync(physicalPixels)` can
prewarm an expected variant.

## `GLTexture`

`GLTexture` is the low-level context-bound texture API. Create and initialize it
with a current `GL3`, bind it with `bind(gl, unit)`, and release it with
`dispose(gl)`. Prefer `GLImage` unless direct format, upload, or binding control
is required.

## `GLFont`

`GLFont` stores an MSDF atlas for an AWT font. Obtain cached fonts from the host:

```java
GLFont font = host.getGLFont(new GLFontSpec(Font.SANS_SERIF, Font.PLAIN, 16));
float width = font.measureText("GLSwing");
g.drawUIText(font, "GLSwing", x, baselineY, Color.WHITE);
```

Text coordinates use the baseline. Metrics include ascent, descent, line
height, and measured width. `drawText` uses the text batch; `drawUIText` uses
the UI batch. Host-managed fonts should not be disposed by application code.

## Paths and shapes

`GLPath2D` is a mutable path builder with `moveTo`, `lineTo`, `quadTo`,
`cubicTo`, and `closePath`. It supports `NON_ZERO` and `EVEN_ODD` fill rules,
SVG path parsing, copying, transformation, bounds, and segment inspection.

`GLShape2D` provides immutable shape descriptions such as rectangles, rounded
rectangles, ellipses, circles, polygons, and combined shapes. Convert a shape
with `toPath()` when path operations are required.

## SVG documents

```java
try (InputStream in = getClass().getResourceAsStream("/icons/sample.svg")) {
    GLSvgDocument svg = GLSvgDocument.parse(in);
    BufferedImage raster = svg.rasterize(256, 256);
}
```

`GLSvgDocument` exposes view-box coordinates, shape count, and rasterization.
Supported path commands include `M/L/H/V/C/S/Q/T/A/Z`. Keep SVG input on the
CPU side and create a `GLImage` or `GLIcon` for repeated drawing.

## Lifetime

| Resource | Ownership and release |
|---|---|
| `GLImage`, `GLIcon` | Renderer-independent objects; released when no longer referenced |
| Host-cached `GLFont` | Host-owned |
| Manually created `GLFont`, `GLTexture`, `ShaderProgram` | Call `dispose(gl)` with a current context |
| `GLPath2D`, `GLShape2D`, `GLSvgDocument` | CPU objects; garbage-collected |
| `GLRenderTarget` | Application-owned; call `close()` |

Recordings do not own referenced resources. Keep every image, icon, font, and
shader valid for as long as a recording can replay it.
