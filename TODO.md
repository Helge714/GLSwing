# GLSwing Roadmap

This internal roadmap tracks deferred work that has not yet been scheduled.
Items are removed when completed or intentionally rejected.

## Data views

- Migrate other large item surfaces to `GLList<T>` where profiling shows that
  virtualization or the shared selection model provides a concrete benefit.
- Build a future `GLTable` control on the list selection model when an
  application use case justifies the additional column and header APIs.

## Application startup

- Add opt-in lazy tab-content factories for applications whose measured startup
  time is dominated by large inactive views. Keep eager tab construction as the
  default so tab selection remains immediate and lifecycle behavior predictable.

## File dialogs

- Add switchable content views to the themed file dialog, including the current
  details list and large tile views with file and folder thumbnails.
- Add asynchronous thumbnail generation and caching without delaying directory
  navigation or blocking the rendering thread.

## Rendering and color spaces

- Add partial `GLSpriteBuffer` updates if measured streaming workloads require them.
- Extend 2.5D lighting based on measured application needs, including possible
  multiple lights, material maps, transparency strategies, and sampleable depth.
- Develop a reusable UI material system that preserves efficient batching.
- Add and benchmark GPU interpolation in `SRGB`, `LINEAR_RGB`, `OKLAB`, `HSV`,
  and `OKLCH`, including gamut handling and custom-shader compatibility.
