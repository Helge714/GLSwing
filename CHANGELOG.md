# Changelog

## 0.1.0 — 2026-08-21

First public preview of GLSwing. The API remains under active development and
may change in subsequent `0.x` releases.

### Rendering

- Square UI borders now use an explicitly inset square inner contour, avoiding
  corner rounding and single-pixel fill leaks when the corner radius is zero.
- Slider progress is clipped to the inner border contour so values near the
  minimum cannot bleed into the rounded outer border. Background, progress,
  and border are composited in one analytic instance, keeping both transitions
  antialiased without gaps or hard clipping.
- Subpixel corner radii up to one logical pixel resolve to stable square
  corners instead of producing diagonal corner-pixel asymmetry.
- Buttons, combo boxes, and text editors now compose fill and border from one
  analytic contour, including controls with asymmetric corner radii.

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
- Conventional focus traversal for interactive controls, including safe focus
  release when a control subtree is hidden or removed.
- Typed color and metrics theme fragments with conflict-free bundle slots,
  validation, duplicate-key detection, and deterministic override order.
- Theme-configurable ComboBox press gradients that remain active while the
  popup is open and follow its attachment-side contour.
- External TrueType, OpenType, and Type 1 font resources in filesystem and
  classpath themes, including separate regular, bold, italic, and bold-italic
  faces.
- Node editor with ports, connections, selection, and undo/redo.
- Inheritable `GLHistoryManager` scopes for independent documents and editing surfaces.
- Semantic, inheritable input maps and external `.glinput` files for shortcuts,
  pointer modifiers, and interaction sensitivity.
- Editable theme and input-map override drafts with previews, reset-to-default,
  shortcut capture and conflict detection, and atomic persistence.
- Per-editor temporary text histories with caret/selection restoration,
  configurable edit coalescing, and single-step document commits.
- Typographic ellipses for intentional `...` menu-label suffixes and automatic
  text truncation, including the required glyph in default font atlases.
- Automatic, conflict-free, localized keyboard mnemonics for menu bars, popup
  items, and nested submenus, with configurable activation and explicit overrides.
- Shared `GLIcon` presentation for menu rows, popup titles, menu bars, and combo
  box values, including aligned icon columns and disabled-state variants.

### Build and platforms

- Java 17 minimum runtime and API level.
- Maven multi-module build with separate core and examples artifacts.
- JOGL 2.6.0 and platform-specific native libraries from Maven Central.
- Maven Wrapper, source and Javadoc JARs, and automated smoke tests.
- Apache License 2.0.
