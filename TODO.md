# GLSwing Roadmap

This internal roadmap tracks deferred work that has not yet been scheduled.
Items are removed when completed or intentionally rejected.

## Input and focus

- Complete keyboard operation and focus traversal, including conventional
  navigation keys, focus order, safe focus transfer, and a themed focus ring.

## Data views

- Complete the `GLScrollPane` showcase and interaction tests.
- Develop a virtualized generic `GLList<T>` with selection and replaceable cell renderers.
- Build future `GLTree<T>` and `GLTable` controls on the list selection model.

## Rendering and color spaces

- Add partial `GLSpriteBuffer` updates if measured streaming workloads require them.
- Extend 2.5D lighting based on measured application needs, including possible
  multiple lights, material maps, transparency strategies, and sampleable depth.
- Develop a reusable UI material system that preserves efficient batching.
- Add and benchmark GPU interpolation in `SRGB`, `LINEAR_RGB`, `OKLAB`, `HSV`,
  and `OKLCH`, including gamut handling and custom-shader compatibility.

## Further investigation

- Accessibility interfaces for GPU-rendered controls.
