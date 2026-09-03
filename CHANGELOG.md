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

- Declarative `GLApplication` startup with event-driven GLJPanel and GLCanvas backends,
  automatic theme/input/localization stages, weighted custom background/AWT/GL
  tasks, a configurable progress splash, typed prepared results, visible failure
  handling, and splash-to-application handoff only after a complete frame has
  been rendered in the still-hidden native window. An optional handoff delay
  keeps the completed splash above the revealed window for a controlled visual
  transition.
- The standard startup splash supports background artwork with cover, contain,
  or stretch scaling, translucent readability overlays, nine content anchors,
  configurable dimensions and padding, and an ordered element recipe. Titles,
  subtitles, logos, gaps, current-task lines, histories, content progress bars,
  custom Swing components, and optionally unpadded progress bars are added in a
  compact declaration; omitting a declaration selects the complete default.
  Task-history elements independently choose their width, status markers, past
  and upcoming rows, and fade target. The active row retains its position while
  both directions fade toward an opacity just beyond the visible range.
  Status glyphs use a fixed marker column so task labels remain aligned across
  pending, active, completed, and failed states.
- Applications can replace the standard splash layout through
  `splashView(...)` and freely compose automatically bound Swing elements for
  the title, subtitle, logo, current task, task history, and progress bar.
  Standard and custom task histories can independently hide status markers,
  while the standard layout can omit its separate current-task line. Task
  histories grow upward and keep the newest visible entry bottom-aligned.
- `GLFrame`, direct canvases, and embeddable `GLSwingPanel` hosts.
- Containers, layouts, damage tracking, and Z-order control.
- Buttons, checkboxes, sliders, text editors, combo boxes, and menus.
- Tabs, scroll panes, dialogs, themes, and localization.
- Virtualized generic lists with replaceable data and selection models,
  reusable visible cells, custom renderers, conventional mouse and keyboard
  selection, activation events, automatic scroll-to-selection behavior, and a
  reusable single-cell editing lifecycle with validation-aware commits.
- The reusable `GLFileBrowser` combines a lazy `GLFileSystemTreeModel`,
  system-derived places and drives, and a virtualized file-details list for
  embedded asset browsers and application-specific dialogs. Directory
  enumeration is asynchronous, the current path is revealed in the tree, and
  refreshes discard stale results. The themed file dialog builds its path,
  search, commands, inline renaming, and request controls around this public
  browser instead of maintaining a separate navigation implementation.
- Virtualized `GLTree<T>` views add replaceable hierarchical models, mutable
  default models, lazy empty branches, expansion events, disclosure rendering,
  standard tree keyboard navigation, shared list selection, optional icons,
  custom renderers, inline cell editing, and localized F2/context-menu renaming
  without constructing components for collapsed nodes.
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
- Schema-aware `.gltheme` editing in `GLCodeEditor`, including automatic
  language detection, semantic highlighting, diagnostics, completion, hover,
  imported-property resolution, and cross-file definition navigation without
  an external language server. Resolved colors receive inline swatches with
  transparency previews and exact hexadecimal/RGBA hover values.
- Functional RGB and RGBA components now use an unambiguous per-channel rule:
  unsuffixed integers are `0..255`, decimals and `f`-suffixed values are
  normalized `0..1`, percentages are `0..100`, and formats may be mixed. The
  same rule applies to absolute, additive, and subtractive derived unit-channel
  values; multiplicative overrides remain ratios.
- Theme colors now accept optional alpha directly in `rgb(...)`, plus `hsv(...)`,
  `hsl(...)`, `oklab(...)`, and `oklch(...)` forms. The existing `rgba(...)`
  spelling remains supported as a compatibility alias.
- Native `GLCodeEditor` support for `.glinput` files adds standard-action
  completion, binding/modifier/value validation, hover help, inheritance-cycle
  diagnostics, and parent-file navigation. Native localization `.properties`
  support adds stable-key completion, `MessageFormat` validation, parent-bundle
  comparison, and root-key navigation without an external language server.

### Build and platforms

- Java 17 minimum runtime and API level.
- Maven multi-module build with separate core and examples artifacts.
- JOGL 2.6.0 and platform-specific native libraries from Maven Central.
- Maven Wrapper, source and Javadoc JARs, and automated smoke tests.
- Apache License 2.0.
