# Changelog

## 0.1.0 — 2026-08-21

First public preview of GLSwing. The API remains under active development and
may change in subsequent `0.x` releases.

### Rendering

- Batched 2D rendering for shapes, lines, textures, MSDF text, and UI.
- Transforms, projections, cameras, picking, and reusable recordings.
- Paths, Bézier curves, dashed strokes, and SVG import.
- Nested layers, blend modes, effects, and custom shaders and uniforms.
- Render targets, feedback buffers, atlases, and adaptive SVG rasterization.
- 2.5D sprites with depth and normal maps, lighting, instancing, and resident buffers.

### UI

- `GLFrame`, direct canvases, and embeddable `GLSwingPanel` hosts.
- Containers, layouts, damage tracking, and Z-order control.
- Buttons, checkboxes, sliders, text editors, combo boxes, and menus.
- Tabs, scroll panes, dialogs, themes, and localization.
- Node editor with ports, connections, selection, and undo/redo.
- Inheritable `GLHistoryManager` scopes for independent documents and editing surfaces.
- Semantic, inheritable input maps and external `.glinput` files for shortcuts,
  pointer modifiers, and interaction sensitivity.
- Editable theme and input-map override drafts with previews, reset-to-default,
  shortcut capture and conflict detection, and atomic persistence.
- Per-editor temporary text histories with caret/selection restoration,
  configurable edit coalescing, and single-step document commits.

### Build and platforms

- Java 17 minimum runtime and API level.
- Maven multi-module build with separate core and examples artifacts.
- JOGL 2.6.0 and platform-specific native libraries from Maven Central.
- Maven Wrapper, source and Javadoc JARs, and automated smoke tests.
- Apache License 2.0.
