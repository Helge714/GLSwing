# Changelog

## 0.1.0 — 2026-08-21

First public preview of GLSwing. The API remains under active development and
may change in subsequent `0.x` releases.

- `GLDialog` now optionally provides theme-rendered or absent window chrome.
  The themed title bar supports dragging and closing, while undecorated dialogs
  support configurable, platform-dependent rounded native window shapes.

### Rendering

- Added explicit visual layout connections through `GLLayoutGroup` and layout
  constraints, including partial edges and nested non-rectangular arrangements.
  `GLInputGroup` groups history transactions independently of visual placement.
- Color channels now form a compact connected panel of grouped color sliders,
  multi-select live numeric fields, and repeating up/down buttons. Alpha uses
  the same columns in a visually separate group while remaining independent.
  Linked fields mirror raw intermediate text, show one active
  caret and one group focus outline, and atomically commit or discard on Enter.
  Editing and output color models can be selected separately. Screen sampling
  and its larger, keyboard-focusable flat action use the same dedicated
  eyedropper icon/cursor where supported. The chooser dialog now packs to its
  content and shows its status row only for actionable messages.
- Themed dialogs now use a flat Windows-style close target with rounded outer
  geometry, a full-surface hit target, antialiased translucent edges and a
  configurable soft shadow. A cubic native contour remains the compatibility
  fallback. Body borders use one gap-free analytic fill/border primitive, and
  title separators no longer blend against the transparent window surface.
- Grouped slider and color-field focus outlines now cover only linked rows;
  reversing the selection gesture removes rows before editing begins.

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

- Added external document-file monitoring and save-conflict coordination.
  Clean documents can reload automatically in place; locally modified documents
  require an application-provided Reload, Overwrite, Save As, or Cancel choice.
  Reload preserves document and tab identity while resetting stale history and
  rebuilding the view. Successful atomic saves are acknowledged so they do not
  trigger false external-change reports.
- Existing document views created during application startup can now be added
  through `GLDocumentLifecycle.addDocument(document, view)`, enrolling them in
  autosave and external file monitoring instead of bypassing lifecycle services.

- Added reusable `GLColorPicker`, `GLColorChooser`, and `GLColorSlider` controls
  with RGB/HSV/HSL/OKLab/OKLCH editing, contextual channel ramps, alpha previews,
  gamut warnings, numeric input, undoable color gestures, and a screen eyedropper.
  Editable theme-document color swatches open the picker; applying replaces the
  source expression as one committed undoable edit.

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
- Startup splashes are owned by their application window instead of being
  globally always-on-top by default. Global foreground behavior remains
  available through an explicit splash option.
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
- Typed `GLDocument<T>` models combine URI/title metadata, read-only state,
  independent undo history, and a persisted-state checkpoint. Registries and
  asynchronous managers keep storage separate from application models and
  resolve caller-defined codecs by stable IDs or file extension.
- Versioned ZIP document bundles index independently readable subdocuments,
  apply explicit consecutive schema migrations, save through atomic sibling
  replacement, and preserve unloaded or unrecognized payloads. Container,
  application-format, and per-entry schema versions remain independent.
- Document workspaces route application menu state and undo/redo commands to
  the active document with an optional application-history fallback. A tab
  controller installs document histories as inherited component scopes,
  maintains modified-title markers, switches routing on tab selection, and
  preserves bindings when closed tabs are reopened.
- Asynchronous tab close requests let applications keep a tab open while a
  confirmation or save is pending. The document lifecycle provides conventional
  new, open, save, save-as, save-all, and close commands; integrates codec-based
  file filters and file dialogs; exposes menu-state predicates; and coordinates
  save, discard, and cancel decisions for modified documents.
- `GLPlainTextDocumentCodec` persists source and plain-text models as UTF-8, and
  code editors can now be constructed directly over an existing
  `GLTextDocument` model.
- Live text edits now update a visible `* ` prefix before the editor loses focus.
  Saving commits the active editor transaction before establishing the persisted
  checkpoint, so a later focus change cannot mark the same content as modified again.
- Ellipsized standard controls, tabs, menus, tree rows, and list rows expose
  their complete text as delayed tooltips. Tree disclosure arrows expand and
  collapse branches without changing selection.
- Tooltips close when their owning controls leave the component hierarchy.
- Code edits preserve unaffected semantic classifications while updated
  language-server results are pending; classifications intersecting the edit
  and obsolete diagnostics are discarded.
- Added semantic `document.save`, `document.save-as`, and `document.save-all`
  input actions with platform-shortcut defaults. Save command availability now
  distinguishes clean documents from Save As and is reflected in the showcase menu.
- Added shared `GLAction` commands with localized text and descriptions,
  GLSwing icons, predicate-based availability and selection, asynchronous
  handlers, automatic input-map accelerator labels, semantic shortcut dispatch,
  bidirectional Swing Action selection adaptation, and bindings for buttons,
  checkboxes, and popup-menu items. Text formatting, code-editor commands, and
  showcase document history now use the shared action model.
- Added optional, themeable code inlay hints with visual-only layout space,
  programmatic and context-menu visibility control, a language-neutral provider
  API, LSP conversion, and built-in parameter labels for RGB, HSV, HSL, OKLab,
  OKLCH, and derived theme colors. The Java LSP profile requests all parameter
  name hints, and the showcase includes Java and theme examples.
- Inlay hints outside the edited range now remain visible while asynchronous
  language intelligence computes a replacement snapshot; following anchors are
  translated by the text-edit delta instead of briefly clearing every label.
  Configuration functions also retain labels for temporarily empty argument
  slots identified by their commas. Theme color previews now remain stable and
  track source-range edits while refreshed color data is pending.
- Java signature help now supplies provisional parameter inlays for empty call
  slots when the available overload is unique or all overloads agree on that
  parameter name. Ambiguous overloads wait for normal language-server resolution.
- Fixed slider groups now distribute one continuous progress gradient over all
  rows instead of confining the light-to-dark transition to the first slider.
- Default font atlases now include every non-Latin-1 symbol used by the built-in
  UI and diagnostic presentation, including call-hierarchy arrows, typographic
  punctuation, navigation chevrons, and status marks.
- Bounded mnemonic-underline shadow softness to prevent oversized blur blobs at
  extreme text-shadow settings.
- Added asynchronous, cancellable `GLApplication` close requests and
  `GLDocumentLifecycle.prepareCloseAll()` for save/discard/cancel processing
  before application teardown.
- Added opt-in document autosave with externally loadable idle and periodic
  scheduling, atomic recovery snapshots outside authoritative files, startup
  discovery and restoration, observable status, automatic lifecycle tracking,
  and clean-shutdown cleanup. The GUI showcase includes a localized recovery
  chooser and an editable external autosave configuration.
- Added `GLDialogButtonBar` for preferred-width localized dialog actions.
  Recovery, unsaved-changes, color-picker, and themed file-dialog buttons now
  expand to their complete labels before falling back to ellipses.
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
- Maven multi-module build with a core artifact, optional LSP and native SWT
  file-dialog integrations, and a separate examples artifact.
- JOGL 2.6.0 and platform-specific native libraries from Maven Central.
- Maven Wrapper, source and Javadoc JARs, and automated smoke tests. Javadoc
  generation uses the class path explicitly because GLSwing publishes an
  automatic-module name without requiring JPMS modular compilation.
- Apache License 2.0.
