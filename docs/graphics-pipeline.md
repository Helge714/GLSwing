# Rendering Guide

This guide documents the public 2D rendering API. Start with the context map
below, then use the focused chapters for contracts, units, examples, and
performance implications.

## Contents

- [Core concepts and units](#core-concepts-and-units)
- [`GLContext2D` API map](#glcontext2d-api-map)
- [Constants](#constants)
- [Detailed guides](#detailed-guides)

## Core concepts and units

| Term | Meaning |
|---|---|
| Immediate drawing | A command is submitted to the renderer during the current frame. |
| Recording | Context commands are stored as reusable, preprocessed instructions. |
| Replay | `draw(GLRecording)` feeds recorded instructions into the current batches. |
| Standard pipeline | Customizable filled, texture, text, or sprite batches. |
| UI pipeline | Instanced batch specialized for controls, MSDF text, and atlas icons. |
| Layer | Temporary off-screen content composited and optionally filtered as a group. |
| Render target | Persistent, application-owned off-screen color buffer. |

Most drawing commands work both immediately and while recording. Render-target
updates and `drawRenderTarget(...)` remain dynamic and are intentionally not
recorded.

Unless a method states otherwise, GLSwing uses these units:

| Value | Unit |
|---|---|
| Geometry, position, and size | Current local user coordinates; one unit is one logical pixel with `TOP_LEFT_PIXELS` |
| Explicit screen-space values | Logical pixels; the renderer applies the physical HiDPI scale |
| Angles | Radians |
| Opacity, alpha, and normalized ratios | Dimensionless values, normally `0..1` |
| Colors | `java.awt.Color`; integer constructor channels use `0..255` |
| Names ending in `Millis` | Milliseconds |

Stroke measurements can use world or screen space. See
[Strokes](graphics-pipeline/context-configuration.md#strokes).

## `GLContext2D` API map

Overloads of the same operation are grouped. The
[drawing reference](graphics-pipeline/drawing.md) documents accepted geometry,
color, picking, and pipeline variants.

### Recordings and replay

| Methods | Purpose |
|---|---|
| `beginRecording`, `endRecording`, `cancelRecording`, `isRecording` | Manage a recording |
| `beginParameters`, `endParameters`, `withParameters` | Define nestable parameter regions |
| `draw(GLRecording)` | Replay a recording |
| `draw(GLRecording, GLRenderParameters)` | Replay with parameter overrides |
| `beginSpriteBuffer`, `endSpriteBuffer`, `cancelSpriteBuffer`, `isRecordingSpriteBuffer` | Build GPU-resident sprite instances |
| `draw(GLSpriteBuffer)` | Draw resident instances relative to the current model transform |

### General state, clearing, and bounds

| Methods | Purpose |
|---|---|
| `setColor`, `getColor` | Set or inspect the default drawing color |
| `clear()`, `clear(Color)`, `clearDepth()` | Clear active color, picking, or depth outputs |
| `getScreenBounds` | Transform local bounds into screen bounds |
| `intersectsRedrawBounds` | Test geometry against the current damage region |
| `flush` | Submit all open batches explicitly |

### Transform, projection, and view

| Methods | Purpose |
|---|---|
| `getTransform`, `setTransform`, `resetTransform` | Manage the model transform |
| `translate` | Translate subsequent geometry |
| `rotate(radians)`, `rotate(radians,x,y)` | Rotate around the origin or an anchor |
| `scale(s)`, `scale(x,y)` | Apply uniform or independent scaling |
| `getProjection`, `setProjection(GLTransform)` | Read or replace the projection matrix |
| `setProjection(Projection2D)` | Apply a projection provider |
| `setView(GLView2D)` | Apply a complete view-projection configuration |

### Depth and 2.5D lighting

| Methods | Purpose |
|---|---|
| `setDepthState`, `getDepthState` | Configure depth testing, writes, and comparison |
| `setDepth`, `getDepth` | Set constant geometry depth; `0` is back and `1` is front |
| `setLighting`, `getLighting` | Configure ambient and directional sprite lighting |

### Stroke and curve tessellation

| Methods | Purpose |
|---|---|
| `setStroke`, `getStroke` | Replace or inspect the complete `GLStroke` |
| `setStrokeWidth`, `getStrokeWidth` | Line width |
| `setStrokeCap`, `getStrokeCap` | End caps |
| `setStrokeJoin`, `getStrokeJoin` | Joins |
| `setStrokeOffset`, `getStrokeOffset` | Shift the stroke center along the path normal |
| `setStrokeMiterLimit`, `getStrokeMiterLimit` | Limit sharp miter extensions |
| `setStrokeCurveTolerance`, `getStrokeCurveTolerance` | Stroke curve approximation tolerance |
| `setStrokeWidthSpace`, `getStrokeWidthSpace` | Select `WORLD` or `SCREEN` measurements |
| `setStrokeDash`, `getStrokeDash`, `clearStrokeDash` | Configure a dash pattern |
| `setStrokeDashPhase`, `getStrokeDashPhase` | Set the dash-pattern starting offset |
| `setCurveTolerance`, `getCurveTolerance` | Filled path and curve approximation tolerance |

### Picking and output masks

| Methods | Purpose |
|---|---|
| `setOutputMask` | Enable or disable color and picking output together |
| `setColorRenderingEnabled`, `isColorRenderingEnabled` | Control color output |
| `setPickingRenderingEnabled`, `isPickingRenderingEnabled` | Control picking output |
| `setTexturePickAlphaThreshold`, `getTexturePickAlphaThreshold` | Set alpha rejection for texture picking |
| `setPickId`, `getPickId`, `clearPickId` | Manage a numeric pick ID |
| `setMouseTarget`, `getMouseTarget`, `clearMouseTarget` | Manage a direct `GLMouseTarget` |

### Shaders and uniforms

| Methods | Purpose |
|---|---|
| `setFilledShader`, `getFilledShader`, `useDefaultFilledShader` | Shape and stroke shader |
| `setTextureShader`, `getTextureShader`, `useDefaultTextureShader` | Texture, image, icon, and target shader |
| `setTextShader`, `getTextShader`, `useDefaultTextShader` | Standard MSDF text shader |
| `setUIShader`, `getUIShader`, `useDefaultUIShader` | Complete universal UI shader replacement |
| `setFilledUniform`, `setFilledUniformMatrix4` | Uniforms for a custom filled shader |
| `setTextureUniform`, `setTextureUniformMatrix4` | Uniforms for a custom texture shader |
| `setTextUniform`, `setTextUniformMatrix4` | Uniforms for a custom text shader |
| `setUIUniform`, `setUIUniformMatrix4` | Uniforms for a custom UI shader |

Each `set...Uniform` family accepts scalar `float`, `vec2`, `vec3`, `vec4`,
and `int` values. Matrix methods accept 16 floats.

### Clips and layers

| Methods | Purpose |
|---|---|
| `beginClip`, `endClip`, `getClipDepth` | Manage nested rectangular clips |
| `beginUIRoundedClip` | Add antialiased corner clipping to standard UI color and picking output |
| `beginLayer()` | Start a layer covering the active surface |
| `beginLayer(x,y,w,h)` | Start a layer with explicit local bounds |
| `beginLayer(x,y,w,h,screenInset)` | Add screen-space room around explicit bounds |
| `endLayer()` | Composite normally |
| `endLayer(opacity)` | Composite with group opacity |
| `endLayer(GLBlendMode)` | Composite with a blend mode |
| `endLayer(GLComposite)` | Composite with opacity, blend mode, and effects |
| `getLayerDepth` | Inspect the nesting depth |

### Persistent render targets

| Methods | Purpose |
|---|---|
| `beginRenderTarget`, `endRenderTarget`, `withRenderTarget` | Render into a persistent target |
| `getRenderTargetDepth` | Inspect nested target passes |
| `drawRenderTarget` | Draw target content through the texture pipeline |

### Standard drawing pipeline

| Method family | Batch |
|---|---|
| `drawLine`, `drawPolyline`, `drawPolygon`, `drawCubicBezier` | Filled |
| `drawPath`, `drawRect`, `drawRoundRect`, `drawEllipse`, `drawCircle` | Filled |
| `fillPath`, `fillTriangle`, `fillRect`, `fillPolygon` | Filled |
| `fillRoundRect`, `fillEllipse`, `fillCircle` | Filled |
| `drawTexture`, `drawImage`, `drawIcon`, `drawRenderTarget` | Texture |
| `drawText` | Text |
| `drawSprite`, `drawMaterial` | 2.5D sprite material |

Strokes are tessellated into triangles and therefore share the filled batch.

### Universal UI pipeline

| Method family | Purpose |
|---|---|
| `fillUIRoundRect` | Analytic rounded fill |
| `drawUIRoundRect` | Analytic inner border |
| `fillAndDrawUIRoundRect` | Combined fill and border without AA bleeding |
| `drawUIRoundRectShadow` | Analytic, non-pickable shadow |
| `drawUIText` | MSDF UI text |
| `drawUICheckMark`, `drawUIChevron`, `drawUILine` | Standard symbols and analytic line segments |
| `fillUIProgressRoundRect` | Rounded progress gradient |
| `fillUIVerticalGradientRoundRect` | Full rounded vertical gradient |
| `addUIHitArea` | Picking-only rectangle with no color output |
| `drawUIImage`, `drawUIIcon` | RGBA or MSDF atlas content |

## Constants

| Constant | Value |
|---|---|
| `NO_PICK_ID` | `0`, no numeric picking target |
| `DEFAULT_CLEAR_COLOR` | Dark background `(15, 20, 31)` |
| `DEFAULT_TEXTURE_PICK_ALPHA_THRESHOLD` | `0.1f` |

## Detailed guides

1. [Context configuration](graphics-pipeline/context-configuration.md)
2. [Drawing commands and render paths](graphics-pipeline/drawing.md)
3. [Recordings and runtime parameters](graphics-pipeline/recording.md)
4. [Custom shaders and uniforms](graphics-pipeline/shaders.md)
5. [Depth and 2.5D materials](graphics-pipeline/depth-materials.md)
6. [Layers, compositing, and effects](graphics-pipeline/layers-effects.md)
7. [Render targets and feedback](graphics-pipeline/render-targets.md)
8. [Images, atlases, icons, text, paths, and SVG](graphics-pipeline/resources.md)
9. [Picking and pointer targets](graphics-pipeline/picking.md)
10. [Batching, performance, and resource lifetime](graphics-pipeline/performance.md)
