# Performance, Batching, and Resource Lifetime

[Rendering guide](../graphics-pipeline.md) · [Recording](recording.md) · [Depth and materials](depth-materials.md)

GLSwing batches compatible commands and submits them when state or destination
changes require a boundary. Applications generally obtain the best throughput
by drawing related content consecutively and reusing long-lived resources.

## Contents

- [Frame flow](#frame-flow)
- [Batch boundaries](#batch-boundaries)
- [Choosing a caching strategy](#choosing-a-caching-strategy)
- [Layers and render targets](#layers-and-render-targets)
- [Damage and resolution](#damage-and-resolution)
- [Measurement](#measurement)
- [Resource lifetime](#resource-lifetime)

## Frame flow

Each frame initializes context state, records or replays dirty containers,
renders visible content, resolves picking, and presents the framebuffer.
Filled, texture, text, sprite, and UI commands use separate batches.

## Batch boundaries

The following operations submit at least one open batch:

- switching between rendering pipelines;
- changing shaders, uniforms, projection, depth state, or output masks;
- exhausting texture slots or changing incompatible image/material state;
- starting or ending a layer or render target;
- clearing, explicit `flush()`, and external OpenGL access.

Built-in UI instances carry their effective rectangular clip in logical screen
coordinates. Changing a nested clip therefore does not split the standard UI
batch; color and picking apply the same per-instance rectangle in their
shaders. Clips still split general filled, texture, text, and sprite batches,
as well as UI rendering through a custom UI shader, because those paths use
the OpenGL scissor state.

Avoid alternating pipelines unnecessarily—for example, repeated
`Fill -> Text -> Fill -> Text`. Draw order still defines compositing, so never
reorder commands when that would change the image.

## Choosing a caching strategy

| Workload | API |
|---|---|
| Small or changing every frame | Immediate `GLContext2D` calls |
| Stable shapes, text, icons, and UI | `GLRecording` |
| Stable geometry with changing transform, tint, or opacity | `GLRecording` plus `GLRenderParameters` |
| Large immutable sprite sets | `GLSpriteBuffer` |
| Independently changing world chunks | One sprite buffer per chunk |

Recordings cache CPU preparation but replay into dynamic batches. Sprite
buffers retain static instance data on the GPU. Call `invalidateRecording()`
only when recorded content changes; moving a component normally changes only
its replay transform.

Images may use an atlas when their options and dimensions are compatible.
Atlases reduce texture switches. Standalone textures remain appropriate for
large, repeating, render-target, or frequently updated images.

## Layers and render targets

`beginLayer(x,y,w,h)` limits off-screen allocation to known bounds. Effects
increase those bounds by their padding and may add multiple full-area passes.
`beginLayer()` uses the whole active surface and should be reserved for cases
that genuinely need it.

Render targets persist across frames and suit cached scenes, simulations, and
multi-pass rendering. Their memory cost scales with physical pixel area,
attachment count, multisampling, and depth/picking options.

## Damage and resolution

`GLContainer` uses damage tracking and `intersectsRedrawBounds(...)` to skip
unaffected work. Components with shadows or other overflow must report
`visualOverflow` so invalidation includes their visible extent.

`setRenderResolutionScale(float)` scales physical rendering while preserving
logical coordinates. Lower values reduce fragment and off-screen effect cost.
`GLSwingPanel` includes an additional transfer into Swing; use a direct
`GLCanvas` or `GLFrame` for latency- or throughput-sensitive windows.

## Measurement

Measure representative scenes after warm-up. Host metrics include:

- `getFramesPerSecond()` and `getAverageFrameTimeMillis()`;
- `getLastDrawCallCount()`;
- `getLastDrawCallProfile()` for the `UI`, `Filled`, `Texture`, `Text`,
  `Sprite`, layer, render-target, and uncategorized contributions;
- `getFrameProfile()`;
- `getActualAntialiasingSamples()` and `getActualSwapInterval()`.

GPU timers such as `GL_TIME_ELAPSED` distinguish GPU work from CPU submission.
Use the supplied benchmark demos for reproducible comparisons.

`GLDrawCallProfile.events()` returns the non-empty batch flushes in submission
order. Each `GLDrawCallEvent` identifies the pipeline, the physical call count,
and the reason that ended the batch, such as a pipeline switch, clip change,
recorded-state change, capacity limit, or frame end. A pickable batch normally
reports two physical calls because color and picking are rendered separately.
Use this trace to find unnecessary pipeline alternation; it is diagnostic data,
not a guarantee that a particular scene will always use the same number of
batches across renderer versions.

## Resource lifetime

| Resource | Release operation |
|---|---|
| `GLRenderTarget`, `GLFeedbackBuffer`, `GLSpriteBuffer` | `close()` |
| `GLImage`, `GLIcon` | Renderer-independent objects; released when no longer referenced |
| `GLTexture`, `GLFont`, `ShaderProgram` | `dispose(gl)` with a current context |
| `GLRecording`, `GLRenderParameters` | Garbage-collected; referenced GPU resources must remain valid |

Create context-bound resources during host initialization and dispose them from
the corresponding resource-disposal callback. Do not allocate images, fonts,
targets, or shaders inside a per-frame render method.
