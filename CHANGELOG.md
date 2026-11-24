# Changelog

- Keep connection picking independent of transparent border decoration, for
  straight and curved wires. Invisible hit borders preserve selection and deletion
  without changing rendered colors. Covered by 0.2.0-SNAPSHOT.

- Prefer opaque themed dialog surfaces on Linux to avoid the translucent
  GLJPanel/compositor path; retain an explicit glswing.dialog.translucent override.
  Hardware acceptance remains required. Covered by 0.2.0-SNAPSHOT.

- Rebuild cached component/panel recordings when the renderer changes, including
  OpenGL context recreation during monitor changes. Stable frames keep their cache.
  Covered by 0.2.0-SNAPSHOT; renderer replacement GPU regression added.

- Preserve the visible world-space camera center when resizing a graph workspace,
  including zoomed views and temporary zero-sized viewports. Inspector toggles keep
  their existing screen-position policy. Covered by 0.2.0-SNAPSHOT.

- Share the Find dialog and matching engine between text/code editors and Markdown views, including highlighted document matches; suppress Markdown component-name tooltips. Covered by 0.2.0-SNAPSHOT.


- Code languages can add transient semantic bold/italic emphasis; drawing and caret geometry use the same styles without editing document formatting.

- Suppress component-name fallback tooltips in code editors; retain diagnostics,
  symbol help and explicitly configured tooltips. Covered by 0.2.0-SNAPSHOT.

- Merge provisional/provider parameter hints at the same logical argument,
  including intervening whitespace/comments. Draw title badges with contrasting
  text and keep node minimum widths independent of title length. Covered by 0.2.0-SNAPSHOT.

- Add severity-colored trailing node title badges with reserved space, independent
  of title truncation and renaming. Covered by 0.2.0-SNAPSHOT.

- Exclude control characters such as Backspace from automatic code completion.
  Covered by 0.2.0-SNAPSHOT.

- Let code languages opt into automatic identifier completion and provide their
  own Enter indentation and formatter. Align diagnostic underlines after inline
  parameter hints. Existing languages retain their defaults. Covered by 0.2.0-SNAPSHOT.

- Distinguish Bezier, linear and step curve points with circles, squares and
  diamonds. Let phase editors set grid divisions and align the plot to its edges.
  Covered by 0.2.0-SNAPSHOT.

- Show curve points and Bezier handles only while hovered or focused. Add fixed
  point X positions/topology for phase editors while retaining handle shaping.
  Covered by 0.2.0-SNAPSHOT.

- Keep position-aware Arrange compact: rough positions guide branch ordering
  instead of creating global grid rows or extra signal columns. Restore nearby
  control-source placement and port-aware compaction. Covered by 0.2.0-SNAPSHOT.

- Workspace Arrange retains rough user rows and columns while resolving overlaps
  and respecting signal flow and group boundaries. The compact layout API remains
  available. No document format change. Covered by 0.2.0-SNAPSHOT.

- Preserve graph screen placement when toggling the workspace inspector,
  compensating for zoom-centered viewport resizing. Covered by 0.2.0-SNAPSHOT.

- Reduce borderless round socket glyphs by a further five percent, retaining
  zoom-proportional sizing and unchanged hit areas. Covered by 0.2.0-SNAPSHOT.

- Render zero-width combined UI borders as plain fills, preserving asymmetric
  corners. Fix repeated render exceptions from borderless popup themes; invalid
  negative/non-finite widths remain rejected. Covered by 0.2.0-SNAPSHOT.

- Join flush slider side buttons to the center value area with straight inner
  edges and rounded outer corners. Preserve the shared slider interaction.
  Covered by 0.2.0-SNAPSHOT.

- Centralize command routing across panel and canvas hosts: retain focus-local
  handling, allow declared hover commands and a configured default area, and
  suppress fallback during text/exclusive input. Skip hidden/detached targets
  and invoke shared ancestors at most once. Covered by 0.2.0-SNAPSHOT.

- Preserve the prior keyboard target when clicking menu-bar entries, so closing
  a menu restores application shortcuts. Covered by 0.2.0-SNAPSHOT.

- Keep borderless round socket fills compact with a zoom-proportional inset;
  preserve their layout and picking bounds. Keep the inner event-port diamond
  visible independently of transparent outlines. Covered by 0.2.0-SNAPSHOT.

- Inherit connected scroll-viewport corner shapes, including straight shared edges.
  Keep round socket fills proportional at every zoom and support edge-to-edge
  node title backgrounds independently of text padding. Covered by 0.2.0-SNAPSHOT.

- Keep connected scroll-pane viewports flush with adjacent controls, suppressing
  standalone padding and inner frames. Add optional visual-only diamond socket
  scaling without changing round sockets or hit areas. Covered by 0.2.0-SNAPSHOT.

- Build font atlases outside the shared cache monitor so background font warming
  does not stall unrelated render contexts. Texture ownership remains per context. Covered by 0.2.0-SNAPSHOT.

- Keep offscreen component captures on context-owned font textures. Repeated captures
  retain their text without uploading or disposing the source surface fonts; component
  parenting and geometry stay unchanged. Covered by 0.2.0-SNAPSHOT.

- Reuse built-in connection gradients across frames and skip complete offscreen node
  subtrees with conservative socket/shadow bounds. Keep focused editors and crossing
  cables available; preserve custom color-function evaluation. Covered by 0.2.0-SNAPSHOT.

- Complete theme-dependent node measurement synchronously after child controls refresh.
  Restore saved node bounds above their content minimum while preserving larger user sizes.
  Add a protected post-theme hook; covered by 0.2.0-SNAPSHOT.

- Add reusable escaped Markdown HTML rendering and a themed retained Markdown view
  with tables, anchors and full-area link picking. Applications retain navigation policy.
- Isolate code text operations, connection tessellation caching and workspace view
  snapshots; keep public editor APIs and document formats unchanged.
- Move external showcase theme assets under the examples module. Remove tracked
  Maven cache debris and validate consumer artifacts against the current build.
  Additive APIs remain covered by 0.2.0-SNAPSHOT.

- Prefer horizontal node flow in multi-column auto-layouts, accepting modestly
  angled wires instead of accumulating port offsets along long chains. Preserve
  exact two-column port alignment and compact branch/theme clearances. No API or
  serialization change; covered by the current 0.2.0-SNAPSHOT development version.

- Arrange graphs using actual port anchors, deterministic strongly connected
  feedback regions and compact skyline packing. Keep short source branches near
  their consumers and remove unnecessary width inherited from unrelated columns.
  Explicit input/output boundary roles keep group interfaces at the outer edges.
  Crossing reduction and local row swaps have bounded search budgets; repeated
  arrangement is stable and changes locations only. Compatible API additions are
  covered by 0.2.0-SNAPSHOT; document and execution contracts are unchanged.

- Defer composite boundary reprojection while user wire changes await interface
  synchronization, preserving alternate-input relocation. Add tree row click
  notifications independent of selection restoration. File-browser single clicks
  navigate the main pane, double clicks expand the tree without navigating, and
  main-pane navigation no longer reveals folders in the sidebar. Compatible changes
  covered by 0.2.0-SNAPSHOT; no document-format change.

- Persist a world-space camera center per graph while keeping zoom shared. Document
  schema 10 remains readable alongside schemas 1–9; older readers cannot load newly
  saved schema-10 documents. Retain camera metadata through copies and Undo.
- Arrange disconnected graph components separately and reduce crossing/vertical
  wire paths with layered barycentric ordering and non-overlapping compaction.
  Add an opt-in connection replacement router for free compatible alternate inputs.
- Show application custom inspector editors even when their values are inherited
  defaults rather than explicit instance properties. Covered by 0.2.0-SNAPSHOT.

- Add opt-in graph-local Undo/Redo scopes with document-wide persisted state and
  retained group documents. Preserve boundary insertion atomically, clear tooltip
  ownership on hidden/detached ancestors and pointer changes on both canvas backends,
  ignore filtered menu icons for column sizing, and let unchanged text fields pass
  Escape to their container. Sidebar Escape dismisses the focused inspector before
  group navigation. Covered by the existing 0.2.0 development version; no format change.

- Allow applications to exclude unused embedded assets from persistence while clearing
  stale forward navigation. Active breadcrumb assets remain protected. Compatible
  addition covered by 0.2.0-SNAPSHOT; no document-format change.

## Unreleased UI fixes

- Preserve reentrant graph projections, expose explicit view refresh, and allow application icons in node creation menus.
- Add undoable node-name reset and include control names in tooltips.
- Do not open slider history transactions on right-click or disabled controls.
- Covered by 0.2.0-SNAPSHOT; compatible APIs, no document-format change.

- Log failed node and connection projections with graph/element identity and the
  original exception stack trace. Repeated failures are suppressed until recovery
  or a changed error. Other nodes remain available. Compatible diagnostic fix
  covered by the current development version.

- Include common Greek and mathematical symbols in the default font atlas so native scientific documentation renders ranges and equations without replacement boxes. Explicit custom repertoires are unchanged. Covered by the current development version.

- Add an optional view-factory state-transfer callback for model-identity-preserving view replacements, including changed types and port layouts. Existing factories remain source/binary compatible through the default implementation; persistence contracts are unchanged. Covered by the current development version.

- Restore saved node dimensions after applying hidden presentation so graph navigation retains custom sizes. Covered by the current development version; persistence contracts are unchanged.

- Record bound-editor connection Undo with immutable model endpoints instead of visual port references. Preserve composite boundary bindings when node views are rebuilt, and let boundary rewiring use its single interface transaction. The current development version covers these compatible fixes; document formats are unchanged.

- Preserve custom node names separately from default labels and title annotations, including graph navigation, copying and Undo. Graph document schema 9 stores custom names and still reads versions 1–8; newly saved documents require the updated Core. Covered by the current development version.

- Add transport-independent editor driver targets, range mapping/soft takeover, driver menus and indicators. Add non-undoable dirty-state tracking and allow driver metadata on composite instances. Covered by the current development version.

- Reuse one text snapshot and grapheme iterator per multiline layout. Avoid document-size copies for every wrapped character when displaying large read-only texts.

- Persist direct composite input-to-output routes across navigation, history and document copying. Flatten forwarding routes without execution nodes. Documents using these routes require this Core version; the current development version covers the change.

- Allow applications to extend the curve-editor context menu while retaining the standard point and segment actions.

- Keep event-driven Canvas surfaces repainting while modal dialogs are open, without adding a continuous animator.

- Center ungrouped contents on the former instance bounds, excluding boundary projections and stale view entries.

- Add one-level composite ungrouping with undo, endpoint/property rebinding and preserved reusable definitions. Connection-handle changes invalidate cached geometry immediately.

- Render inactive connections using the theme muted opacity while retaining diagnostic tooltips, and provide a geometric group-title indicator.

- Add `GLNodeEditor.fitAllNodes()` to center and fit all visible nodes, including large graphs and group boundaries, without changing layout.

- Explicitly copyable custom editors can participate in standalone editor coupling.

- Node ports can provide context menus on sockets and row labels, including connected inputs.

## 0.2.0-SNAPSHOT — Unreleased

- Compound node rows can anchor their sockets to a child editor and collapse to one row while connected.

- Reference connection dash settings are now recognized by theme validation.
  Theme regression tests follow the current presets and configured metrics.

- Added optional full-area label tooltips, component image capture, PNG/JPEG
  export, themed image previews and a remembering file-dialog decorator.
- Added neutral group-editor capabilities, choice-template storage and nested
  graph copying with application metadata hooks. Existing document contracts
  remain unchanged. See docs/application-services.md.

- Added reusable `GLImageExportDimensions` for raster size, aspect locking and
  inside/outside padding, with independent geometry regression tests.

- Node catalogs support an explicit category order; unspecified categories retain
  alphabetical ordering.

- Composite boundary reconciliation waits for connection gestures to finish,
  preserving detached-wire dragging and cancellation.

- Composite boundary nodes initially bracket the inner graph and remain available
  when empty. Dragging connections onto their bodies previews and creates ports;
  applications can provide editor metadata for those ports.
- Insertion into composite boundary wires supports graph editing, layout spacing
  and separate movement/insertion undo, including placement actions.
- Port identifiers accept Unicode letters and marks. Applications may select the
  default composite value type and provide read-only reasons for inspector fields.

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
- Inner rounded-border coverage is constrained to its inset bounds and to the
  outer contour. This prevents isolated fill pixels at the bottom-left and
  top-right corners when border width consumes the inner corner radius.
- Buttons, combo boxes, and text editors now compose fill and border from one
  analytic contour, including controls with asymmetric corner radii.

- Batched 2D rendering for shapes, lines, textures, MSDF text, and UI.
- Transforms, projections, cameras, picking, and reusable recordings.
- Paths, Bézier curves, dashed strokes, and SVG import.
- Nested layers, blend modes, effects, and custom shaders and uniforms.
- Render targets, feedback buffers, atlases, and adaptive SVG rasterization.
- 2.5D sprites with depth and normal maps, lighting, instancing, and resident buffers.

### UI

- Composite-interface sidebars now derive their scrollable height from the
  rows that are actually visible. Port lists use compact connected add,
  remove, move-up, and move-down controls, while icon-only bound toggles
  directly determine whether slider values are fixed or open-ended.
- Added the rendering-independent `GLNodeGraph` model with stable graph, node,
  connection, and endpoint identities; portable structured property values;
  immutable revision snapshots; atomic edit callbacks; cascading node removal;
  and validated snapshot replacement. This forms the shared model boundary for
  future editor bindings, document codecs, validators, composites, and optional
  execution backends.
- Added backend-neutral node and port descriptors, a type registry,
  configurable cycle policies, previous-step port timing, and structured graph
  diagnostics. Node editors can map diagnostics by stable connection ID and
  draw warning or error borders without replacing connection data colors. The
  Showcase node page demonstrates cycle-policy and delayed-feedback changes
  interactively.
- Added `GLNodeEditorBinding` for bidirectional graph/editor synchronization,
  model-driven node projection, automatic validation decoration, stable visual
  connection IDs, and separately captured/restored node layout state. The
  Showcase node page now uses this binding instead of maintaining two graphs.
- Added model-aware node editing sessions with cut, copy, paste, duplicate, and
  delete commands. Clipboard fragments preserve properties, type versions,
  internal wiring, and view bounds; paste assigns fresh identities, and each
  complete graph-and-layout change forms one undo/redo entry. Standard node
  shortcuts and context-menu actions can delegate to the session.
- Node editors now provide select-all toggling, positioned creation requests,
  direct composite opening, and cancellable floating placement. Paste,
  duplicate, and move selections follow the pointer until confirmed and still
  produce a single graph-and-layout history entry. Popup menus can host custom
  interactive rows and filter normal items, enabling an inline searchable node
  creation popup. Placement confirmation captures presses before embedded node
  controls, and popup dismissal reliably restores the preceding keyboard focus.
- The Showcase opens composite assets in the existing graph workspace with
  Escape navigation, the complete editing command set, filtered creation that
  prevents direct self-containment, and unique group-input/group-output
  boundary projections. These structural nodes are created automatically,
  omitted from the add menu, and protected from destructive editing commands.
- Added selection-to-composite extraction. Boundary connections become typed
  public ports, internal nodes and edges form a reusable graph asset, and the
  surrounding graph is rewired through one composite instance. Extracted assets
  can be embedded in a project document or written as standalone `.glgraph`
  documents and registered in the node catalog.
- Added the versioned `.glgraph` document codec with separately stored view
  state, portable metadata, unknown-node preservation, embedded graph assets,
  project-relative external asset references, and standalone composite export.
- `.glgraph` schema 2 adds explicit node-type versions while retaining schema-1
  compatibility. Applications can register transactional consecutive asset and
  node migrations and inspect an immutable step report. Migrated plain or
  bundled documents remain modified in memory until explicitly saved.
- Document lifecycles now expose structured operation failures and migration
  approval. Standard localized themed dialogs cover missing codecs, unsupported
  versions, damaged files, I/O failures, migration failures, read-only opening,
  cancellation, and copyable technical details.
- Added composite node contracts with public-to-internal port mappings, loaded
  asset registration, minimum dependency versions, and bounded pre-expansion
  analysis. Direct and indirect recursion, missing assets, invalid mappings,
  excessive nesting, and node/connection/operation budgets produce structured
  graph diagnostics before a flattened graph is allocated.
- `.glgraph` schema 3 stores self-describing composite contracts, including
  typed public properties, defaults, numeric ranges, and multi-target internal
  mappings. Transactional property edits integrate with document history.
- Added deterministic composite flattening with per-instance node/connection
  IDs, public-port rewiring, property propagation, all-or-nothing failure, and
  complete origin paths for mapping backend diagnostics and profiler data back
  to visible composite instances.
- Added a language-independent immutable node execution plan with registered
  operation contracts, deterministic topological scheduling and hashing,
  explicit previous-step state reads, dead-subgraph removal, constant folding,
  reusable temporary slots, static cost/storage statistics, and preserved
  composite origins. The backend SPI is independent of the editor and includes
  a deterministic Java reference runtime for offline tests and future backend
  conformance fixtures.
- Execution plans now propagate fixed or property-dependent latency in a named
  domain unit and record per-input compensation for parallel paths. Backends
  advertise plan versions, operations, latency units, and previous-step support
  before preparation. Structured diagnostics preserve operation, node, and
  composite origins.
- Added a bounded, checksummed, versioned binary execution-plan exchange codec
  and an asynchronous plan controller. Newer successful builds replace plans
  atomically, failed builds retain the previous plan, and leases defer disposal
  until in-flight execution has finished. Toolchain-specific C++, Rust, GPU,
  and audio runtimes remain independent downstream projects.
- The Showcase node page now includes an execution inspector for compiling,
  stepping, inspecting, and deliberately rejecting replacement plans while the
  active plan remains usable. Global previous-step cycle policy is now applied
  consistently during scheduling, input binding, and state allocation.
- The Java reference backend now supports operation-level execution with value
  and duration traces while retaining atomic graph-step state updates. The
  Showcase inspector links selected nodes and backend failures to their planned
  operations, displays live input/output values and storage, and provides
  operation stepping, continue, and per-node breakpoints.
- The Showcase execution inspector now presents a collapsible hierarchy rebuilt
  from complete composite origin paths. Internal leaf operations remain linked
  to their outer visible node, while composite summaries aggregate scheduled
  operations, estimated cost, persistent state, and measured Java runtime.
- Added a backend-neutral searchable node catalog with categories, keywords,
  default properties, favorites, recents, and typed-port compatibility filters.
  The editor binding can insert catalog nodes at graph coordinates, and empty
  connection drops can be handed to a compatible-node chooser.
- The Showcase node workspace now includes a searchable palette, compatible
  connection completion, a transactional property inspector, and editable
  Composite tabs with breadcrumb navigation. Composite edits replace the
  registered asset atomically and rebuild the root execution plan.
- Added reusable `GLNodeWorkspace` and `GLCompositeInterfaceEditor` controls.
  The workspace provides searchable insertion, standard editing commands,
  in-place composite navigation, automatic Group Input/Output projections, and
  persistable `.glgraph` snapshots. Public-port renames and removals update
  dependent open graphs in the same undo/redo transaction, and node-catalog
  descriptors are refreshed with the resulting contract.
- Composite interfaces now present dedicated input and output lists with
  application-defined value types. Inputs support portable editor metadata,
  socket visibility, and built-in slider options for range and numeric kind.
  Port names can be edited in place and ports can be reordered by dragging or
  by the adjacent move actions. The complete inspector is vertically scrollable
  without horizontal port-list overflow; all port settings are committed
  atomically by its Apply action. List actions use font-independent vector
  symbols.
  Editor registrations can pair portable metadata with runtime control
  factories. `GLCompositeNodeViewFactory` resolves those factories for every
  composite instance while the application factory remains responsible for
  leaf nodes. Public ports can be authored before a matching internal endpoint
  exists; Group Input/Output connections establish or remove the binding, and
  only a connected unbound port prevents flattening. Added
  `GLNodeValueTypeRegistry`, `GLNodeInputEditorRegistry`, and graph schema 5
  persistence; older ports remain compatible.
- Composite interface settings now apply immediately. Changing a public value
  type preserves its current internal binding and displays an incompatible
  binding with the standard connection-error decoration. New public ports stay
  unbound until the author explicitly connects their Group Input/Output socket.
  Typed node ports only become drag-snap candidates when both port validators
  accept the connection.
- The built-in composite slider editor now exposes fixed/free behavior,
  bar/button presentation, integer or floating-point input, a spinner-based
  default, and independently optional minimum and maximum constraints. Inline
  editors hide while a connected socket supplies the value. Node workspaces
  capture Escape before focused sidebar controls so nested-composite navigation
  remains reliable.
- Output-only labels are right-aligned in every node. Composite interface
  updates preserve existing Group Input and Group Output positions instead of
  returning their projections to default coordinates.
- Corrected the Showcase design and composite-page bounds so tab content stays
  within the visible tab container at the default window size.
- `GLList` now supports optional drag reordering, programmatic movement of the
  selected row, application-owned reorder handlers, and left/right default-cell
  text alignment. `GLDefaultListModel.moveElement(...)` provides the matching
  mutable-model operation.

- Added a Java Accessibility bridge for `GLJPanel` and `GLCanvas` hosts.
  Lightweight controls now expose a virtual accessible hierarchy with names,
  descriptions, roles, live states, geometry, focus, standard actions, and
  numeric values. Standard buttons, check boxes, combo boxes, sliders, text
  controls, lists, trees, labels, and tab panes provide appropriate defaults;
  custom controls can override the same semantic hooks.
  Menus, individual tabs, virtualized list rows, tree branches, scroll panes,
  and scroll bars expose their own semantics. Layout-only containers can be
  flattened without hiding meaningful descendants.

- Pointer clicks now complete directly from the retained press target when the
  button is released within that control. This removes missed actions caused
  by a one-frame-old picking snapshot while preserving drag-out cancellation
  and double-click counts on both `GLJPanel` and `GLCanvas` hosts. A confirmed
  hover hit at the same pointer position also starts the pressed state without
  waiting for an unrelated pending picking rebuild; rapid releases remain
  ordered behind an asynchronous press resolution.
- Code workspaces now include an active-editor status line for caret position,
  selection length, language, indentation, diagnostics, and tooling state.
- Code-editor gutters reserve independent diagnostic, folding, and right-aligned
  line-number columns. The number column grows with the document's digit count,
  while tab content is clipped to its assigned area so it cannot draw through
  shared Find/Replace or status bars.
- Code editors now support structural folding, multiple carets, rectangular
  selection, provider-backed references, workspace-wide rename edits, and
  source code actions. The LSP adapter maps the corresponding standard
  protocol requests without making these features Java-specific. Reference
  ranges are highlighted in place, while inline rename synchronizes all local
  occurrences until Enter commits or Escape cancels the provider transaction.
  Provider-backed shortcuts are refreshed as soon as a language server is
  attached, including servers connected asynchronously after editor creation.
- Find/Replace now supports whole-word and regular-expression matching,
  capture-group replacements, safe invalid-pattern reporting, and cancellable
  background indexing through `GLTextSearchOptions`.
- Added `GLCodeWorkspace`, a reusable code-editing surface that composes
  closable document tabs, closed-tab recovery, shared source-navigation
  history, document routing, asynchronous source resolution, and a docked
  IDE-style Find/Replace bar below the tab strip. Applications can configure
  language tooling and document loading while retaining access to each
  composed service. Search queries and case changes update automatically through
  debounced, cancellable background indexing.
- Added localized Find and Find/Replace dialogs to multiline text and code
  editors, including configurable all-match highlighting, active/total result
  counts, repeated-Enter navigation, keyboard and button navigation,
  forward/backward wrapping search, case matching,
  programmatic search operations, read-only Find, and atomic undo for Replace
  All. Default shortcuts are `Ctrl/Cmd+F` and `Ctrl/Cmd+H`.
- Added one-based line navigation for multiline text and code editors, including
  a localized `GLNumberSpinner` dialog, context-menu action, read-only support,
  viewport centering, and the configurable `text.go-to-line` shortcut.
- Added a bounded source-navigation history for related code editors, with
  precise cross-document locations, automatic recording for definitions and
  line jumps, context-menu commands, and configurable Back/Forward shortcuts.
- Theme formatting now normalizes the compatibility form `rgba(...)` to
  `rgb(...)` and removes a redundant opaque alpha channel when requested by the
  active color-expression format.
- Connected layout groups now paint one opaque occupied-shape backing before
  their independently clipped children, preventing transparent dialog content
  below internal antialiased edges from revealing the owner window.
- Root render targets now compose framebuffer alpha with the source-over rule
  independently from RGB blending. Antialiased control and group edges drawn
  over an opaque themed dialog therefore remain opaque to the native desktop.

- Theme properties now use a packaged declarative `.glschema` contract instead
  of name-based type heuristics. Theme bundles can add application properties
  through `theme.schema`, using either one schema file or a directory of schema
  fragments. The effective schema drives runtime validation, fragment
  filtering, editor completion, diagnostics, hover descriptions, and inlay
  hints. Missing `theme.extends` inherits the standard theme; unknown
  properties remain available with a configurable warn, reject, or allow
  policy.
- Theme documents can declare document-local custom tokens with `@COLOR`,
  `@NUMBER`, `@INSETS`, `@BOOLEAN`, `@TEXT`, `@PATH`, or an explicit
  `@ENUM<value1, value2, ...>` list. The annotation supplies
  predictable validation and editor tooling without becoming part of the
  runtime property key; reusable contracts can remain in `.glschema` files.
  The built-in `palette.*` and global `metrics.*` reference tokens now use
  these declarations in `default.gltheme`; the central schema is reserved for
  fixed control properties and constraints.
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
  file-dialog integrations, a separate examples artifact, and an isolated
  packaged-artifact consumer test.
- JOGL 2.6.0 and platform-specific native libraries from Maven Central.
- Maven Wrapper, source and Javadoc JARs, and automated smoke tests. Javadoc
  generation uses the class path explicitly because GLSwing publishes an
  automatic-module name without requiring JPMS modular compilation.
- Opt-in source and binary API compatibility checks for release candidates;
  implementation-only rendering packages are excluded from the supported API.
- Apache License 2.0.

- Initial composite input placement uses the boundary node's actual width, preserving theme spacing before internal nodes. Saved and manually adjusted boundary positions remain unchanged.

- Bound multiline wrapping measurements with exponential bracketing and binary search over grapheme boundaries. Preserve word wrapping while avoiding per-character prefix layout allocations. Full Core suite: 132 tests, no failures, one optional skip. Covered by the current development version.
