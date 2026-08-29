# Recordings and Runtime Parameters

[Rendering guide](../graphics-pipeline.md) · [Picking](picking.md) · [Next: Resources](resources.md)

`GLRecording` caches prepared drawing commands for repeated replay.
`GLRenderParameters` change transform, tint, color, and opacity without
re-recording. `GLSpriteBuffer` is the GPU-resident alternative for large static
sprite sets.

## Contents

- [When to record](#when-to-record)
- [Creating recordings](#creating-recordings)
- [Container recordings](#container-recordings)
- [Replay and parameters](#replay-and-parameters)
- [Recorded operations](#recorded-operations)
- [Sprite buffers](#sprite-buffers)
- [Lifetime and diagnostics](#lifetime-and-diagnostics)

## When to record

Recording avoids repeated path/stroke tessellation, text layout, icon selection,
and command construction. It does not reduce fragment work, layer passes, or
the draw calls required by incompatible state.

## Creating recordings

```java
GLRecording recording;
g.beginRecording();
try {
    drawStaticContent(g);
    recording = g.endRecording();
} catch (RuntimeException ex) {
    g.cancelRecording();
    throw ex;
}

g.draw(recording);
```

Recordings begin with an identity model transform and restore outer
context state on completion. A frame must not finish with an open recording.

## Container recordings

`GLContainer#record(GLContext2D)` is called lazily when its cache is dirty;
`render(GLContext2D)` replays the result by default. Children maintain separate
caches and are not embedded in their parent's recording. Call
`invalidateRecording()` when the recorded appearance changes. Bounds or parent
transform changes normally require only replay with a different transform.

## Replay and parameters

```java
GLRenderParameters parameters = new GLRenderParameters();
parameters.setTranslation(x, y);
parameters.setTint(Color.WHITE);
parameters.setOpacity(0.75f);
g.draw(recording, parameters);
```

| Parameter | Effect |
|---|---|
| `setTransform`, `resetTransform`, `translate`, `rotate`, `scale` | Complete replay transform |
| `setTint`, `resetTint` | Multiplies recorded texture and icon color |
| `setColor`, `clearColor` | Replaces eligible recorded vertex colors |
| `setOpacity` | Multiplies per-command alpha by `0..1` |
| `reset` | Restores identity, original colors, and full opacity |

Opacity is applied to each command, not once to the finished group. Use a layer
when overlapping shapes must receive group opacity after composition.

`beginParameters(parameters)` and `endParameters()` create nested parameter
regions during recording. `withParameters(...)` balances the scope on failure.
Mutating a referenced parameter object affects the next replay.

## Recorded operations

Geometry, model transforms, paint/stroke state, projection/view changes,
numeric picking, compatible shader/uniform changes, and balanced clips/layers
can be recorded. `GLIcon` remains high-level so replay can select an adaptive
variant. Screen-space strokes cache geometry per relevant view transform.

Render-target updates and drawing another `GLRecording` while recording are not
supported. Use immediate rendering for frame-dependent target updates.

## Sprite buffers

```java
GLSpriteBuffer buffer;
g.beginSpriteBuffer();
try {
    drawStaticSprites(g);
    buffer = g.endSpriteBuffer();
} catch (RuntimeException ex) {
    throw ex;
}
g.draw(buffer);
```

Sprite buffers upload immutable instance sections with `GL_STATIC_DRAW` on
first use by a renderer. Supported content includes sprites, materials,
transforms, depth, light, and picking. Source `GLImage` channels must use
`Usage.STATIC`. Shader/material changes, texture-slot exhaustion, and explicit
`flush()` create sections.

Use one buffer per independently changing chunk. Partial instance updates are
not part of the 0.1.0 API; close and rebuild the affected buffer. Bounds getters,
`getInstanceCount()`, and `isEmpty()` describe recorded content.

## Lifetime and diagnostics

Recordings do not own fonts, images, icons, or shaders; keep them valid through
every replay. Direct `GLMouseTarget` references are weak. A recording can replay
only on the renderer that created it, while a sprite buffer may create a
renderer-specific GPU copy from its renderer-independent CPU description.

Call `close()` on sprite buffers. `getCommandCount()` counts coalesced replay
commands, not original context calls or guaranteed draw calls. Unbalanced
layers, clips, and parameter regions are rejected when recording ends.
