# GLSwing Roadmap

This internal roadmap tracks deferred work that has not yet been scheduled.
Items are removed when completed or intentionally rejected.

## Graph editor

- Expose horizontal socket hit padding as an editor/application preference using
  the existing Core theme value `node.portHitPaddingX` (currently 8 world units).
  Keep editor picking above socket padding and vertical resize boundaries clear;
  consider a screen-space option and validate behavior across zoom levels.
- Consider per-node mirrored flow (inputs right, outputs left) for feedback
  layouts. Persist orientation in view state with Undo, context-menu and inspector
  actions. Adapt socket/label alignment, curve tangents, insertion/reconnection
  gestures, boundary nodes and automatic layout without changing graph semantics.

## Data views

- Migrate other large item surfaces to `GLList<T>` where profiling shows that
  virtualization or the shared selection model provides a concrete benefit.
- Build a future `GLTable` control on the list selection model when an
  application use case justifies the additional column and header APIs.

## Markdown and documentation views

`GLMarkdown` and `GLMarkdownView` now provide shared escaped HTML rendering and
native headings, paragraphs, lists, tables, code, emphasis, anchors and link
picking. EasyKnack uses the Core view; discovery and language policy stay in the
application. Unicode, tables and links have Core and consumer regressions.

Remaining hardening:

- Add read-only text selection/copy to the control and expose spacing through
  theme metrics. The application currently provides whole-document copying.
- Add bounded shared parse/layout caching and reflow only for relevant content,
  width or theme changes. Profile hover invalidation and offscreen work before
  introducing further caches. Preserve each viewer's navigation/layout state.
- Stabilize the public content model before a stable API release; prefer typed,
  immutable blocks over exposing mutable parser builders.

## Application startup

- Add opt-in lazy tab-content factories for applications whose measured startup
  time is dominated by large inactive views. Keep eager tab construction as the
  default so tab selection remains immediate and lifecycle behavior predictable.

## Node graph platform

The generic graph model, descriptors, validation contracts, execution-plan
interfaces, editor binding, and document integration belong in `glswing-core`.
Toolchain-specific code generation and runtimes belong in optional modules.
Concrete audio nodes, product rules, and DSP algorithms remain application or
plugin code; the audio items below define requirements for a future optional
integration rather than dependencies of the UI library.

### Model and persistence

- Extend the initial `GLNodeEditorBinding` beyond structural synchronization,
  automatic validation, and separate node layout snapshots with property-editor
  adapters, incremental large-graph updates, and document lifecycle integration.
- Define immutable graph snapshots and explicit change operations so user and
  programmatic edits share validation, notifications, transactions, and
  undo/redo behavior.
- Extend the versioned `.glgraph` codec and its atomic document-manager
  integration with lazy partial section loading, dependency hashes, and
  project-level conflict handling. Schema, asset, and node-type migrations now
  run transactionally, report every applied step, preserve unknown node types,
  and support embedded or project-relative standalone composite assets.
- Preserve node positions and editor metadata separately from executable graph
  semantics so headless tools and non-GL frontends can use the same model.

### Types, factories, and implementations

- Extend the initial `GLNodeTypeRegistry` and port descriptors with categories, display metadata,
  typed ports, properties, defaults, factories, and versioned type aliases.
- Extend the implemented presentation-independent operation descriptors and
  backend registry with source-code implementations, external modules,
  versioned aliases, and richer implementation compatibility metadata.
- Allow applications to register domain-specific value types, implicit or
  explicit converters, connection limits, and editor controls without coupling
  those definitions to the renderer.
- Provide clean behavior for missing node types and implementations, including
  inspectable placeholders that retain serialized data and connections.

### Validation and compilation

- Extend the reusable graph validator beyond its current port direction, exact
  type, required-input, connection-limit, and cycle checks with duplicate-edge,
  conversion, unreachable-output, property, and domain-specific rules.
- Extend the implemented cycle policies and previous-step timing with explicit
  backend contracts for feedback, state, and delay operations. Continue to
  report diagnostics against concrete nodes and connections, then add port and
  property locations as those model identities are introduced.
- Extend the implemented language-independent immutable execution-plan compiler
  with compatible-operation fusion. Inline composite
  expansion, recursive-composition rejection, deterministic scheduling and
  hashing, constant folding, dead-subgraph removal, temporary lifetime reuse,
  named-unit latency propagation, and parallel-input compensation are
  implemented.
- Extend the implemented static plan statistics beyond expanded/retained nodes,
  operation cost, temporary/state storage, and maximum latency with
  backend-specific estimates without presenting estimates as runtime profiling.

### Execution backends and native code

- Publish reusable backend-conformance fixtures for the implemented capability
  descriptor, structured diagnostics, versioned checksummed plan exchange,
  deterministic Java reference backend, and lease-protected asynchronous plan
  replacement.
- Keep generated C++, Rust, GPU, remote, audio, and other product-specific
  runtimes in independently versioned projects that depend on GLSwing. Do not
  add toolchain-specific implementations or native dependencies to this
  repository.
- Add a C++ generator that emits typed static processing code and generated
  wrappers for ports, properties, persistent state, and the host ABI. Let the
  native compiler perform function inlining, vectorization, and low-level dead
  code elimination.
- Version the native ABI and validate node implementation, compiler, platform,
  architecture, and build-option compatibility before loading a module.
- Cache native build artifacts by source, descriptor, dependency, toolchain,
  target, and option hashes. Return structured compiler diagnostics to the
  originating graph elements.
- Compile away from the UI and real-time threads and support safe asynchronous
  plan replacement. Retain the previous working plan when validation or a
  native build fails.
- Treat source nodes and native modules as trusted code by default. Document
  that a separate compiler process protects application responsiveness but does
  not sandbox a loaded native module; provide an out-of-process runtime option
  for untrusted projects.

### Code nodes

- Add a configurable code-node type whose author defines typed inputs, outputs,
  properties, state, and C++ processing source while GLSwing generates the host
  wrapper and function signature.
- Reuse the code workspace, search, completion, diagnostics, formatting,
  declaration navigation, and LSP infrastructure for embedded node source.
- Support node templates and export a validated code node as a reusable,
  versioned registered node type. Use the same mechanism for project-defined
  standard nodes.
- Show build state and diagnostics without blocking graph editing, and never
  compile or load code on an audio callback thread.

### Composite graphs

- Extend the implemented composite contracts with richer application-defined
  property constraints and editor metadata. Typed defaults, numeric ranges,
  multi-target property mappings, transactional instance overrides, and
  self-describing standalone persistence are implemented.
- Support `INLINE`, `MODULE`, and `OPAQUE` compilation strategies. Use `INLINE`
  by default so nested graphs can be flattened and optimized without adding a
  runtime abstraction cost.
- Extend the reusable node workspace with clickable breadcrumb segments,
  forward navigation, and an optional recent-document history. In-place nested
  editing, Escape/back navigation, searchable insertion, automatic boundary
  projections, transactional public-port editing, and `.glgraph` snapshots are
  implemented.
- Feed the origin paths now preserved by the execution-plan compiler into
  editor problem navigation. The flattener applies defaults and overrides,
  rewires public ports, gives every instance collision-free IDs, and aborts
  without partial output.
- Aggregate native profiler measurements back onto composite nodes while still
  permitting inspection of their internal operations, latency, and buffer use.

  ### Production editor workflow

  - Extend the implemented clickable composite breadcrumb and Back/Forward
  history with diagnostic and profiler deep links. Graph, per-level view state,
  selection restoration, direct ancestor jumps, Tab/body-double-click opening,
  and Escape navigation are implemented.
  - Extend the implemented reusable `GLNodePalette` with optional application
  metadata renderers. Docked and categorized-popup presentation, favorites,
  recents, text search, typed-port filtering, positioned insertion, and the
  empty-connection-drop workflow are implemented.
  - Extend the implemented schema-driven `GLNodePropertyInspector` with an
  application editor-provider SPI. Core number (slider by default, optional
  spinner), enum, boolean, multi-selection, and atomic graph edits are
  implemented; domain editors remain application-owned.
  - Add an optional dockable problem/diagnostic pane to `GLNodeWorkspace` and
    connect composite-origin diagnostics to navigation into nested graphs.
  - Extend the implemented model-level cut, copy, paste, duplicate, delete, and
    composite-extraction operations with operating-system clipboard interchange
    and import conflict dialogs. Fresh IDs, preserved internal connections,
    predictable placement, embedded/standalone assets, and single undo
    transactions are implemented.
- Add frames/groups, comments, connection labels, reroute points, alignment and
  distribution actions, snapping controls, a minimap, navigation history, and
  optional automatic layout.
- Provide graph search and filtering, validation/problem navigation, dependency
  inspection, and clear visualization of implicit conversions, feedback paths,
  disabled nodes, bypasses, and unavailable implementations.
- Keep large graphs responsive through spatial indexing, viewport culling,
  incremental validation, cached connection geometry, level-of-detail rules,
  and measured rendering budgets.

### Real-time audio requirements

- Provide an optional audio-oriented graph profile without making audio a core
  dependency of the generic node model.
- Generate a fixed execution schedule and preallocate audio buffers and node
  state. The callback path must avoid allocation, locks, filesystem access,
  logging, compilation, class loading, and other unbounded operations.
- Separate sample/audio-rate processing from control-rate parameter updates and
  define deterministic parameter smoothing, event timestamping, channel-layout
  negotiation, sample-rate changes, block state, and reset behavior.
- Calculate and compensate latency where appropriate; model feedback through
  explicit delay nodes and define safe state transfer or crossfading when an
  edited execution plan replaces the active plan.
- Add offline rendering and deterministic test backends before relying on a
  real-time native backend.

### Verification

- Add broader malformed-graph and recursive-composite fixtures, deterministic
  validation and compilation tests, and copy/paste identity tests. Current
  document fixtures already cover schema-1 loading, consecutive asset and node
  migration, missing-step failure, and current-schema round trips.
- Add backend conformance fixtures so equivalent plans can be compared across
  Java and C++ implementations within numeric tolerances.
- Benchmark compilation, plan swaps, large-graph interaction, buffer reuse,
  generated native code, and real-time callback deadlines. Include stress tests
  for rapid edits, failed builds, missing node types, and backend shutdown.

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

## Release validation

- Validate the complete accessibility workflow on Windows with Java Access
  Walker and at least one production screen reader such as NVDA. Cover menu,
  tab, list, tree, text, focus, selection, expansion, scrolling, and disabled
  control behavior before the public release.
