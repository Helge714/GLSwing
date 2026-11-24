# Layers, Compositing, and Effects

[Rendering guide](../graphics-pipeline.md) · [Drawing commands](drawing.md) · [Next: Render targets](render-targets.md)

A layer renders a bounded command group off screen and composites the finished
result back into its parent. Use layers for group opacity, blend modes, curved
clipping, and post-processing effects.

## Contents

- [Layer scope](#layer-scope)
- [Compositing](#compositing)
- [Built-in effects](#built-in-effects)
- [Effect chains and padding](#effect-chains-and-padding)
- [HiDPI, picking, and cost](#hidpi-picking-and-cost)

## Layer scope

```java
g.beginLayer(x, y, width, height);
try {
    drawGroup(g);
} finally {
    g.endLayer(0.75f);
}
```

Bounds use current local coordinates. Prefer bounded layers; `beginLayer()`
allocates for the whole active surface. The `screenInset` overload reduces an
otherwise full-screen region in logical screen pixels. Layers are nested and
recordable. `getLayerDepth()` exposes the current nesting count.

## Compositing

`GLComposite` combines opacity, blend mode, and an ordered effect chain.

```java
GLComposite composite = GLComposite.DEFAULT
        .withOpacity(0.8f)
        .withBlendMode(GLBlendMode.SCREEN)
        .withEffect(GLLayerEffects.blur(6));
g.endLayer(composite);
```

Opacity uses `0..1` and applies once to the composed result. Blend modes include
`NORMAL`, `ADD`, `SUBTRACT`, `MULTIPLY`, `SCREEN`, and `ERASE`.

## Built-in effects

| Effect | Purpose and principal values |
|---|---|
| `GLBlurEffect(radius)` | Gaussian-style blur; radius in logical pixels |
| Drop shadow | Offset, blur radius, and color |
| `GLOutlineEffect(width,color)` | Outer outline |
| `GLInnerOutlineEffect(width,color)` | Inner outline |
| Inner shadow | Inset shadow with offset and blur |
| `GLColorEffect` | Brightness, contrast, saturation, gamma, invert, tint |
| Rounded clip | Per-corner radii |
| `GLPathClipEffect` / `pathClip(shape)` | Arbitrary vector boundary |
| `GLShaderEffect` | Custom full-layer shader and uniforms |

Convenience factories include `grayscale(strength)`, `invert(strength)`,
`outerGlow(...)`, and shape factories for rectangle, rounded rectangle, circle,
ellipse, and polygon clips.

`GLShaderEffect.of(shader)` creates a custom effect. Add typed uniforms through
`withUniform(...)` and matrices with `withUniformMatrix4(...)`. The shader must
follow the layer contract described in [Custom shaders](shaders.md).

## Effect chains and padding

Effects execute in list order; each effect consumes the previous result. Add
them with `withEffect(...)`, inspect them through `getEffects()`, and remove the
chain with `withoutEffects()`.

`GLComposite#getEffectPadding()` returns the expansion required by the chain.
`GLShaderEffect#withPadding(...)` declares expansion when a custom shader
samples outside nominal bounds. Insufficient padding clips blur, shadow, and outline
pixels; excessive padding increases allocation and fragment cost.

## HiDPI, picking, and cost

Public radii, offsets, widths, and padding are logical pixels. GLSwing converts
them to the physical target size. Effects operate on premultiplied-alpha layer
textures.

Picking remains aligned with layer geometry and clipping. Visual effects do not
automatically make newly generated pixels interactive; add an explicit hit area
when a shadow or glow should accept input.

Each layer requires an off-screen allocation and composite pass; multi-effect
chains add passes. Tight bounds, modest blur radii, cached recordings, and
reduced render resolution are the primary cost controls.
