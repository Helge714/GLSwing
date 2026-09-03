# GLSwing User Guide

This guide documents GLSwing 0.1.0 from an application developer's
perspective. It covers the rendering pipeline, retained UI framework, resource
lifecycle, extension points, and the behavior of the standard controls.

## Contents

- [Choose a starting point](#choose-a-starting-point)
- [Rendering guide](#rendering-guide)
- [UI framework guide](#ui-framework-guide)
- [Examples](#examples)
- [API reference and conventions](#api-reference-and-conventions)

## Choose a starting point

| Goal | Start here |
|---|---|
| Create a standalone GLSwing application | [Application startup](ui-framework/application-startup.md) |
| Embed OpenGL rendering in Swing | [Components and hosts](ui-framework/components.md#hosts) |
| Draw shapes, text, images, or SVG content | [Drawing commands](graphics-pipeline/drawing.md) |
| Build an interface with standard controls | [Controls](ui-framework/controls.md) |
| Arrange components | [Layouts and sizing](ui-framework/layout.md) |
| Apply blur, shadows, outlines, or group opacity | [Layers and effects](graphics-pipeline/layers-effects.md) |
| Cache expensive geometry | [Recordings](graphics-pipeline/recording.md) |
| Render into an off-screen texture | [Render targets](graphics-pipeline/render-targets.md) |
| Respond to pointer input on custom geometry | [Picking](graphics-pipeline/picking.md) |
| Customize appearance or language | [Themes and localization](ui-framework/themes-localization.md) |
| Configure shortcuts and interaction modifiers | [Input configuration](ui-framework/input-configuration.md) |
| Create a node-based editor | [Node editor](ui-framework/nodes.md) |

## Rendering guide

The [rendering overview](graphics-pipeline.md) contains a categorized map of
the `GLContext2D` API and defines the coordinate, color, and lifetime
conventions used by every rendering chapter.

| Chapter | Covers |
|---|---|
| [Context configuration](graphics-pipeline/context-configuration.md) | Paint, strokes, transforms, projections, depth, clipping, and output masks |
| [Drawing commands](graphics-pipeline/drawing.md) | Shapes, paths, images, text, UI primitives, sprites, and render order |
| [Recordings](graphics-pipeline/recording.md) | Cached commands, replay parameters, and GPU-resident instance data |
| [Shaders](graphics-pipeline/shaders.md) | Shader contracts, custom programs, uniforms, and alpha conventions |
| [Depth and 2.5D materials](graphics-pipeline/depth-materials.md) | Depth tests, sprite depth maps, normal maps, alpha modes, and lighting |
| [Layers and effects](graphics-pipeline/layers-effects.md) | Group compositing, blend modes, clipping, and all built-in effects |
| [Render targets](graphics-pipeline/render-targets.md) | Persistent off-screen buffers and ping-pong feedback |
| [Resources](graphics-pipeline/resources.md) | Images, texture atlases, icons, fonts, paths, and SVG documents |
| [Picking](graphics-pipeline/picking.md) | Pick IDs, object targets, events, alpha thresholds, and asynchronous readback |
| [Performance](graphics-pipeline/performance.md) | Batch boundaries, damage tracking, measurement, and resource ownership |

## UI framework guide

The [UI overview](ui-framework.md) introduces the component tree and provides
an index of the retained-mode API.

| Chapter | Covers |
|---|---|
| [Application startup](ui-framework/application-startup.md) | Launcher, splash screen, resource preparation, task progress, first-frame handoff, and shutdown |
| [Components and hosts](ui-framework/components.md) | Component lifecycle, bounds, children, z-order, damage, windows, canvas, panel, and dialogs |
| [Layouts and sizing](ui-framework/layout.md) | Preferred sizes, padding, free layout, grid constraints, and custom layouts |
| [Controls](ui-framework/controls.md) | Labels, buttons, lists, sliders, text editors, combo boxes, menus, tabs, and scrolling |
| [File dialogs](ui-framework/file-dialogs.md) | Native SWT open/save dialogs, themed fallback, filters, and previews |
| [Themes and localization](ui-framework/themes-localization.md) | Theme files, style records, color expressions, locale fallback, and live reload |
| [Input configuration](ui-framework/input-configuration.md) | Semantic actions, external `.glinput` files, platform shortcuts, modifiers, and scopes |
| [Text editing](ui-framework/text-editing.md) | Single-line fields, multiline documents, selection, wrapping, scrolling, and editing history |
| [Undo and redo](ui-framework/history.md) | Edits, transactions, keyboard shortcuts, and save state |
| [Node editor](ui-framework/nodes.md) | Nodes, rows, ports, connections, selection, view controls, and history |

## Examples

Runnable examples are located in
`glswing-examples/src/main/java/com/vingame/glswing/demo`. They are intended to
be copied, modified, and used alongside the guide.

Recommended progression:

1. `GLFrameDemo` or `DrawingApiDemo`
2. `GuiShowcaseDemo`
3. `RecordingParameterDemo` and `PickingDemo`
4. `CustomShaderDemo`, `LayerDemo`, and `ImageAtlasDemo`
5. `DepthSpriteDemo`, `GameOfLifeDemo`, or `NodeEditorDemo`

Benchmarks are examples of measurement methodology, not fixed performance
guarantees. Results depend on GPU, driver, display scaling, window system,
resolution, and power-management state.

## API reference and conventions

- The generated Javadoc is the authoritative reference for individual
  constructors, overloads, return values, and exceptions.
- The guides describe system-level behavior and show representative API usage.
- Coordinates and visual measurements use logical pixels unless a section
  explicitly states screen space, world space, normalized values, or physical
  framebuffer pixels.
- Angles are expressed in radians.
- Opacity and normalized color components use the range `0..1`.
- Types in `.internal` packages are implementation details even if Java
  visibility is required for communication between packages.
- Objects implementing `AutoCloseable` own GPU or operating-system resources
  and must be closed as documented.

The documentation tracks the current release. GLSwing 0.x does not guarantee
source compatibility between minor releases.
