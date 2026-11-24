# Render Targets and GPU Feedback

[Rendering guide](../graphics-pipeline.md) · [Resources](resources.md) · [Next: Custom shaders](shaders.md)

`GLRenderTarget` stores rendered color and optional picking/depth attachments
across frames. `GLFeedbackBuffer` manages two targets for ping-pong algorithms.

## Contents

- [Render target or layer](#render-target-or-layer)
- [Creation and options](#creation-and-options)
- [Updating and drawing](#updating-and-drawing)
- [Feedback buffers](#feedback-buffers)
- [Simulation shaders](#simulation-shaders)
- [Lifetime and cost](#lifetime-and-cost)

## Render target or layer

Use a layer for temporary grouped opacity, blending, clipping, or effects
within one render pass. Use a render target when the result must persist,
be sampled later, include picking/depth attachments, or participate in a
multi-pass algorithm.

## Creation and options

```java
GLRenderTarget target = new GLRenderTarget(1024, 1024,
        GLRenderTargetOptions.DEFAULT
                .withFilter(GLRenderTargetOptions.Filter.LINEAR)
                .withWrap(GLRenderTargetOptions.Wrap.CLAMP)
                .withPicking(true)
                .withDepth(true));
```

Width and height are physical texture pixels. `NEAREST` is suitable for pixel
data; `LINEAR` smooths scaling. `REPEAT` is useful for periodic simulations.
Convenience presets include `pixelArt()` and `repeatingPixelArt()`.

## Updating and drawing

```java
g.beginRenderTarget(target);
try {
    g.clear(Color.BLACK);
    drawScene(g);
} finally {
    g.endRenderTarget();
}

g.drawRenderTarget(target, x, y, width, height);
```

`withRenderTarget(target, Runnable)` provides balanced update scope. Nested
targets are supported, but a target cannot be sampled while it is currently
being written. Output masks select color and picking attachments. A depth clear
is required before a new depth-tested pass when prior depth must not persist.

`drawRenderTarget(...)` uses the target's color texture. If picking is enabled,
its picking attachment can also contribute object identity. Add an explicit UI
hit area when the composed result should act as one target instead.

## Feedback buffers

`GLFeedbackBuffer` exposes `current()` for reading and `next()` for writing,
then swaps them after a successful update.

```java
feedback.update(g, () -> {
    g.clear(Color.BLACK);
    g.drawRenderTarget(feedback.current(), 0, 0, width, height);
});
feedback.drawCurrent(g, x, y, width, height);
```

`beginUpdate(context)` / `endUpdate(context)` support manual scope;
`cancelUpdate(context)` abandons an incomplete update safely.

## Simulation shaders

A feedback shader samples the previous target and writes the next state without
CPU readback. Pass reciprocal texture size through a uniform such as
`uTexelSize`, and use `NEAREST` filtering for discrete cell state. The
`GameOfLifeDemo` demonstrates this pattern. Choose an attachment format such as
`R32UI` only when the shader contract and target options support it.

## Lifetime and cost

Call `close()` on targets and feedback buffers. `isClosed()` prevents reuse.
Actual GL deletion occurs safely on the rendering thread.

Memory and fill cost scale with physical width, height, attachment count,
multisampling, and number of passes. Reuse targets, size them to the required
region, avoid clearing content that is intentionally preserved, and avoid
rendering a target only to redraw it at the same resolution without reuse or
post-processing.
