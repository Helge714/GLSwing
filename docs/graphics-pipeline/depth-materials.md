# Depth and 2.5D Materials

[Rendering guide](../graphics-pipeline.md) · [Resources](resources.md) · [Next: Performance](performance.md)

GLSwing can depth-test textured quads while retaining the 2D drawing model.
`GLSprite`, `GLMaterial2D`, and `GLSpriteBuffer` provide the high-level API.

## Contents

- [Depth state](#depth-state)
- [Sprites and materials](#sprites-and-materials)
- [Alpha modes](#alpha-modes)
- [Depth and normal maps](#depth-and-normal-maps)
- [Lighting](#lighting)
- [Batching and lifetime](#batching-and-lifetime)
- [Render targets and layers](#render-targets-and-layers)

## Depth state

Depth is dimensionless: `0` is the back and `1` is the front. Configure the
test before drawing depth-aware content and clear the attachment once per pass.

```java
g.setDepthState(GLDepthState.READ_WRITE);
g.clearDepth();
g.setDepth(0.4f);
```

`GLDepthState.DISABLED` bypasses testing, `READ_ONLY` tests without writing,
and `READ_WRITE` tests and updates depth. `GLDepthFunction.GREATER` matches the
standard back-to-front convention. State changes flush affected batches.

## Sprites and materials

`GLSprite` describes geometry and UV placement. `GLMaterial2D` owns the image
channels and render state shared by compatible sprites.

```java
GLMaterial2D material = GLMaterial2D.builder(colorImage)
        .alphaMode(GLAlphaMode.CUTOUT)
        .alphaCutoff(0.5f)
        .lit(true)
        .build();

g.drawMaterial(material, x, y, width, height);
g.drawSprite(sprite, x, y);
```

The overload with an explicit tint and depth applies values to one draw only.
Keep material instances immutable and reuse them across sprites.

## Alpha modes

| Mode | Behavior | Recommended use |
|---|---|---|
| `OPAQUE` | Ignores source transparency | Fully opaque tiles and backgrounds |
| `CUTOUT` | Discards alpha below `alphaCutoff` | Foliage, glyph-like sprites, hard edges |
| `BLEND` | Premultiplied-alpha blending | Translucent effects drawn back to front |

Opaque and cutout sprites can write reliable depth. Blended sprites normally
require application-controlled back-to-front order because depth testing does
not sort translucent fragments.

## Depth and normal maps

A depth map offsets the sprite's base depth across its surface. Configure the
mapping with `depthRange(back, front)`. Values remain within the same normalized
depth convention as `setDepth(float)`.

A normal map supplies tangent-space normals for lighting. GLSwing derives the
tangent basis from sprite geometry and UV orientation. Color, depth, and normal
images must describe matching content and dimensions.

## Lighting

```java
g.setLighting(GLLighting2D.DEFAULT.withoutDirectionalLight());
```

Lighting combines ambient and directional contributions. Intensities are
non-negative dimensionless values. Unlit materials bypass the lighting work;
use them for UI, emissive artwork, and assets whose shading is already baked.

## Batching and lifetime

Material, shader, alpha, and depth-state changes create batch boundaries.
Compatible material draws can share texture slots; call `flush()` only when an
external OpenGL operation requires submitted geometry to be complete.

Use `GLSpriteBuffer` for large, unchanged sprite sets. Its source images must
use `GLImageOptions.Usage.STATIC`. Close buffers when they are no longer needed;
renderer-owned GPU objects are released safely on a subsequent frame.

Picking uses the same visible fragments as color rendering. Numeric pick IDs
and `GLMouseTarget` work for both dynamic sprites and sprite buffers.

## Render targets and layers

Depth testing requires a destination with a depth attachment. Create persistent
targets with `GLRenderTargetOptions.DEFAULT.withDepth(true)`. Layer bounds and
effects are compositing operations; use a depth-enabled render target when a
complete off-screen scene needs depth testing.

Custom sprite shaders belong to `GLMaterial2D`. See
[Custom shaders and uniforms](shaders.md) for the required attributes, samplers,
and managed uniforms.

Runnable examples: `DepthSpriteDemo`, `DepthSpriteGpuSelfTest`,
`SpritePipelineBenchmarkDemo`, and `ResidentSpriteBenchmark`.
