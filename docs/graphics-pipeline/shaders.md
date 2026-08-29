# Custom Shaders and Uniforms

[Rendering guide](../graphics-pipeline.md) · [Resources](resources.md) · [Next: Layers and effects](layers-effects.md)

GLSwing exposes separate shader contracts for filled geometry, textures, text,
sprites, UI primitives, and layer effects. A custom program must keep every
required attribute and managed uniform active after linking.

## Contents

- [Creation and lifetime](#creation-and-lifetime)
- [Pipeline contracts](#pipeline-contracts)
- [Context shaders and uniforms](#context-shaders-and-uniforms)
- [Sprite material shaders](#sprite-material-shaders)
- [UI shaders](#ui-shaders)
- [Layer shaders](#layer-shaders)
- [Alpha rules](#alpha-rules)

## Creation and lifetime

Create `ShaderProgram` objects during host resource initialization, when a
current `GL3` is available. Factory methods combine application fragment source
with compatible built-in vertex shaders where appropriate.

```java
ShaderProgram shader = ShaderProgram.fromUIFragmentSource(gl, fragmentSource);
```

Call `dispose(gl)` from the matching resource-disposal callback. `isDisposed()`
reports lifetime state. `fromBuiltInResources(...)` is intended for GLSwing's
packaged shader resources.

## Pipeline contracts

| Pipeline | Required inputs and managed state |
|---|---|
| Filled | `aPosition`, `aColor`; `uProjection`, `uModel` |
| Texture | `aPosition`, `aTexCoord`, `aColor`, texture slot; projection/model and samplers |
| Text | Texture contract plus active `uMsdfPixelRange` and MSDF coverage |
| UI | Instanced corner/quad, UV, color, parameter, size, secondary-color, and texture-slot data |
| Sprite | Instanced quad, color/depth/normal UVs, tint, depth data, tangent basis, optional picking data |
| Layer | Full-screen input texture plus resolution/texel information |

The GLSL compiler removes unused declarations. A required value must
participate in the linked program, not merely appear in source. Contract
validation reports missing attributes, uniforms, or incompatible types at the
first assignment/use.

## Context shaders and uniforms

Assign programs with the pipeline-specific setters, for example
`setTextureShader`, `setTextShader`, and `setUIShader`. Passing `null` or calling
the matching `useDefault...Shader()` restores the built-in program.

Typed uniform setters support `float`, `vec2`, `vec3`, `vec4`, `int`/`bool`,
and `mat4`. The uniform must be active and match the setter type.

```java
g.setFilledShader(shader);
g.setFilledUniform("uGamma", 2.2f);
drawGeometry(g);
g.useDefaultFilledShader();
```

Shader or uniform changes flush that pipeline's open batch. Reassigning the
same program and value is inexpensive. Changes are recordable; referenced
programs must remain alive for replay.

Managed resolution uniforms distinguish physical target pixels from logical UI
coordinates: `uResolution`, `uLogicalResolution`, `uDeviceScale`, and
`uTexelSize`. Use the value appropriate to the effect instead of mixing spaces.

## Sprite material shaders

Custom sprite shaders belong to immutable `GLMaterial2D`, keeping shader,
uniforms, maps, alpha mode, and depth state together. Use
`fromSpriteFragmentSource(...)` to retain the built-in instanced vertex stage.
A complete replacement must implement the documented sprite attributes,
15 sampler bindings, lighting inputs, `uProjection`, and `uModel`.

`aDepthData` contains base depth, back/front offsets, and alpha cutoff. Dynamic
instances use identity `uModel`; `GLSpriteBuffer` supplies its draw transform.

## UI shaders

`fromUIFragmentSource(...)` retains the built-in UI vertex stage. The fragment
stage receives `vLocal`, `vTexCoord`, `vColor`, `vParameters`, `vSize`,
`vSecondaryColor`, and `vTextureSlot`.

A custom UI fragment shader replaces all built-in UI modes, including rounded
fills/borders, gradients, images, icons, MSDF text, progress fills, hit areas,
and analytic shadows. Start from the packaged `ui_quad.frag` implementation and
change only the required behavior. GPU picking remains a separate output.

## Layer shaders

Wrap a compatible program in `GLShaderEffect.of(shader)`, add uniforms through
`withUniform(...)`, and declare sampling expansion with `withPadding(...)`.
Layer effects receive premultiplied color and managed resolution/texel values.

## Alpha rules

GLSwing compositing uses premultiplied alpha. A fragment shader must output RGB
already multiplied by alpha. Filtering, blur, and layer operations also assume
premultiplied values; straight-alpha output causes dark or bright fringes.

`ShaderProgram` also exposes low-level `use`, `hasUniform`, `setFloat`,
`setInteger`, `setVector2/3/4`, and `setMatrix4` methods. Use them only while the
correct program and GL context are active; the context-level API preserves
batch ordering and recording semantics automatically.
