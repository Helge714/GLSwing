# Node Editor

[UI guide](../ui-framework.md) · [Controls](controls.md) · [Undo and redo](history.md)

`GLNodeEditor` provides an interactive canvas for nodes, typed-direction ports,
connections, selection, panning, zooming, and history integration. The
application remains responsible for its domain graph and evaluation logic.

## Contents

- [Minimal graph](#minimal-graph)
- [Domain graph model](#domain-graph-model)
- [Types and validation](#types-and-validation)
- [Cycles and feedback](#cycles-and-feedback)
- [Model/editor binding](#modeleditor-binding)
- [Node catalogs and insertion](#node-catalogs-and-insertion)
- [Editing, clipboard, and composite extraction](#editing-clipboard-and-composite-extraction)
- [Production workspace](#production-workspace)
- [Graph documents and reusable composites](#graph-documents-and-reusable-composites)
- [Composite contracts and expansion budgets](#composite-contracts-and-expansion-budgets)
- [Execution plans and backends](#execution-plans-and-backends)
- [`GLNode`](#glnode)
- [Rows, content, and ports](#rows-content-and-ports)
- [Connection rules](#connection-rules)
- [Selection and interaction](#selection-and-interaction)
- [View and geometry](#view-and-geometry)
- [Styles](#styles)
- [History and model synchronization](#history-and-model-synchronization)

## Minimal graph

```java
GLNodeEditor editor = new GLNodeEditor();

GLNode source = new GLNode("Source");
GLContainer sourceContent = new GLContainer();
sourceContent.addGL(new GLLabel("Value"));
GLNodePort out = source.addOutput("value", sourceContent);

GLNode sink = new GLNode("Sink");
GLContainer sinkContent = new GLContainer();
sinkContent.addGL(new GLLabel("Value"));
GLNodePort in = sink.addInput("value", sinkContent);

editor.addNode(source, 40, 80);
editor.addNode(sink, 360, 80);
editor.connect(out, in);
```

Use stable application IDs with `setName(...)` and `findPort(id)` when graphs
are serialized.

## Domain graph model

The `com.vingame.glswing.node.model` package is independent of OpenGL, the
editor, and any execution backend. `GLNodeGraph` owns the mutable current state
and publishes immutable `GLNodeGraphSnapshot` revisions containing
`GLGraphNode` and `GLGraphConnection` values with UUID-based stable identities.

Apply related changes through one atomic edit callback:

```java
GLNodeGraph graph = new GLNodeGraph();
GLNodeId sourceId = GLNodeId.random();
GLNodeId outputId = GLNodeId.random();

graph.edit("Create signal path", edit -> {
    edit.addNode(new GLGraphNode(sourceId, "audio.source"));
    edit.addNode(new GLGraphNode(outputId, "audio.output"));
    edit.setProperty(sourceId, "frequency", GLNodeValue.of(440.0));
    edit.addConnection(new GLGraphConnection(
            GLNodeConnectionId.random(),
            new GLNodeEndpoint(sourceId, "signal"),
            new GLNodeEndpoint(outputId, "signal")));
});
```

If the callback fails or produces an invalid snapshot, the graph retains its
previous revision and listeners receive no partial change. An editor handle is
valid only inside its callback. Removing a node also removes its incident
connections. `replace(...)` installs validated content from the same graph as a
new revision, which allows document and history integrations to restore an
earlier snapshot without exposing mutable collections.

Node properties use the portable sealed `GLNodeValue` representation. It
supports null, booleans, signed integers, finite decimals, text, lists, and
string-keyed maps. Arbitrary application objects are deliberately excluded;
applications can encode them through an explicit domain codec instead of
silently placing nonportable values in a graph document.

The model enforces identity and structural referential integrity. Type and
execution rules are applied separately by `GLNodeGraphValidator`, so a document
can retain and display an invalid or temporarily incomplete graph while the
editor presents precise diagnostics. Editor coordinates and other view state
remain separate from executable graph semantics.

`glswing-core` provides the generic model, descriptor and validation contracts,
execution-plan interfaces, editor binding, and document integration.
C++ generation and real-time audio execution are optional integrations rather
than dependencies of the UI library. Concrete DSP algorithms and product node
catalogs belong to applications or plugins.

## Types and validation

Register the domain contract of every available node type in a
`GLNodeTypeRegistry`. Descriptors contain stable port IDs, directions, value
type IDs, timing, required-input state, and connection limits; they do not
contain controls, rendering code, DSP algorithms, or backend objects.

```java
GLNodeTypeRegistry types = new GLNodeTypeRegistry();
types.register(new GLNodeTypeDescriptor("math.add",
        GLNodePortDescriptor.input("left", "number", true, 1),
        GLNodePortDescriptor.input("right", "number", true, 1),
        GLNodePortDescriptor.output("result", "number")));

GLNodeGraphValidationResult validation = GLNodeGraphValidator.validate(
        graph.snapshot(), types, GLGraphCyclePolicy.ACYCLIC);
```

Validation covers unknown node types and ports, direction and value-type
mismatches, required inputs, connection limits, and dependency cycles.
`GLGraphDiagnostic` attaches a stable code and message to the affected node and
connection IDs. Applications can therefore populate a problem view, decorate
the graph, or reject compilation without parsing diagnostic text.

Associate a displayed `GLNodeConnection` with its domain identity through
`setModelId(...)`, then pass the result to
`editor.applyConnectionDiagnostics(validation)`. Affected connections retain
their normal type-color gradient and receive a warning or error outer border.
The border colors use `nodeEditor.warningConnectionBorder` and
`nodeEditor.errorConnectionBorder`. The diagnostic message remains available
from the connection for a tooltip or inspector. Do not communicate errors by
color alone; production editors should also expose messages and a problem list.

## Cycles and feedback

Choose a cycle policy for the graph profile rather than embedding an
audio-specific rule in the editor:

- `ACYCLIC` rejects every cycle, including self-loops.
- `DELAYED_FEEDBACK` permits a structural cycle only when at least one path is
  broken by an output declared as `GLPortTiming.PREVIOUS_STEP`.
- `PREVIOUS_STEP` treats every connection as a dependency on the previous
  evaluation step and therefore permits structural cycles.

The validator finds strongly connected components and attaches the cycle
diagnostic only to nodes and connections that participate in the invalid
component. Unrelated branches remain undecorated.

`PREVIOUS_STEP` deliberately has no fixed duration in the Core API. An audio
backend may define it as the previous sample or processing block, while a
simulation may define it as the previous tick. The selected backend must
document and validate that interpretation. Concrete delay nodes and their
latency belong to the application or an optional execution module.

## Model/editor binding

`GLNodeEditorBinding` projects an authoritative `GLNodeGraph` into a
`GLNodeEditor`. Supply a `GLNodeViewFactory` that creates the visual control for
each registered node type:

```java
GLNodeEditorBinding binding = new GLNodeEditorBinding(
        graph,
        nodeTypes,
        editor,
        (model, type) -> createNodeControl(model, type),
        GLGraphCyclePolicy.DELAYED_FEEDBACK,
        restoredViewState);
```

The factory must expose every descriptor port through `GLNode.findPort(...)`
with the same stable ID and direction. It may add extra presentation-only rows
and controls. Override `GLNodeViewFactory.updateNode(...)` when property changes
must update an existing visual node without rebuilding it.

Graph transactions add, update, remove, and reconnect visual objects
automatically. Connections created or removed in the editor and nodes deleted
through the editor are reflected back into graph transactions. The binding
assigns stable model IDs to new visual connections, reruns validation after
every graph revision, and updates connection diagnostics. Register a validation
listener to update problem views or compilation availability.

Node creation remains model-first: add a `GLGraphNode` through
`GLNodeGraph.edit(...)`, then let the binding create its view. This keeps the
node type explicit and prevents a visual control from inventing domain data.

Positions and sizes are deliberately excluded from `GLNodeGraphSnapshot`.
Use `captureViewState()` when saving an editor layout and pass or apply a
`GLNodeEditorViewState` when restoring it. This state can be stored in a view
section of the graph document while headless consumers ignore it.

Close the binding when disposing the editor so its graph and editor listeners
are detached. Calls that mutate the graph and its bound editor should use the
application's UI thread unless the host provides explicit dispatching.

## Node catalogs and insertion

`GLNodeCatalog` adds presentation metadata to registered node contracts without
coupling `GLNodeTypeDescriptor` to a particular editor design. Each
`GLNodeCatalogEntry` supplies a display name, category, search keywords, and
portable default properties. The catalog provides deterministic text search,
favorites, recents, and compatibility filtering against a concrete
`GLNodePortDescriptor`; compatible results have an opposite direction and the
same value type.

After choosing an entry, call `GLNodeEditorBinding.addNode(...)` to add its
model and initial view position as one synchronized operation. An
`EmptyConnectionDropListener` on `GLNodeEditor` can redirect a connection
dropped on empty canvas space into a filtered palette. Its callback supplies
the fixed port plus graph and screen coordinates. Once the user chooses a
compatible entry, add the node at the graph coordinates and connect the
opposite compatible port through a normal graph transaction.
`NodeCreationRequestListener` provides the same coordinates for a secondary
click on empty graph space, allowing an application to open a compact creation
menu or focus its full searchable palette. `GLNodeWorkspace` groups the compact
menu by catalog category while the query is empty. Typing switches it to a flat
list containing only matching nodes; filtering never inserts or selects a node,
so creation still requires an explicit result click.

Favorites and recents belong to the catalog instance. Applications that want
them across sessions should persist the returned type IDs in preferences; they
are deliberately not part of the graph document.

## Editing, clipboard, and composite extraction

`GLNodeGraphEditingSession` provides model-aware cut, copy, paste, duplicate,
delete, and composite-extraction operations for a `GLNodeEditorBinding`. Pass
the document's `GLHistoryManager` so each complete operation—including graph
content and view positions—occupies exactly one undo/redo entry:

```java
GLNodeGraphEditingSession editing =
        new GLNodeGraphEditingSession(binding, documentHistory);

editing.copySelection();
editing.paste(420, 180);             // graph coordinates
editing.duplicateSelection(24, 24); // offset from the selection
```

`GLNodeGraphFragment` is the portable clipboard value. It contains the selected
nodes, their view bounds, and only those connections whose two endpoints are in
the selection. Pasting allocates fresh node and connection IDs, remaps every
internal endpoint, preserves properties and node-type versions, and selects the
new nodes. The session clipboard is application-local; applications may share a
fragment between compatible editor sessions through `setClipboard(...)`.

Expose the session through `GLNodeEditingActions` on the editor to enable the
standard node shortcuts and node context-menu commands. The default input map
uses Ctrl/Cmd+C and X for copy and cut. Ctrl/Cmd+V and Shift+D start floating
paste and duplicate placement: the selection follows the pointer until a
primary click confirms it; Escape or a secondary click cancels it. `G` applies
the same placement mode to the existing selection. Ctrl/Cmd+A selects every
node and clears the selection when every node is already selected. Shift+A
requests the positioned node-creation UI, while Ctrl/Cmd+G requests composite
extraction. Placement captures the confirming press at the editor level, so a
button or editor embedded in the node beneath the pointer is not activated by
the same click.

H toggles the compact hidden presentation for the current selection. A hidden
node keeps its title and connected port rows but removes unconnected ports and
content-only controls. A node factory can retain an essential row with
`GLNode.setAlwaysVisible(content, true)` or
`GLNode.setAlwaysVisible(port, true)`. The portable `glswing.hidden` state is
stored with the graph and participates in undo/redo and clipboard operations.

M toggles execution muting. A muted node is dimmed through direct vertex alpha
without an off-screen layer, loses its colored title
surface, and draws its active input-to-output bypass as a red Bezier behind the
node using the editor's normal connection geometry. By default the
first type-compatible input/output pair is used. Applications can define
multi-port behavior explicitly with
`GLNodeTypeRegistry.setMuteBypasses(typeId, mappings)`. Compilation removes
muted operations and rewires their producers directly to the mapped consumers,
including chains of multiple muted nodes. `node.opacity`, `node.mutedOpacity`,
`node.mutedBypass`, and `node.mutedBypassWidth` control the presentation.
The default `node.opacity` is `0.95`, providing a subtle translucent surface
without creating per-node layers or additional draw calls.
The node context menu derives accelerator labels for title editing, clipboard,
duplication, extraction, hiding, muting, and deletion from the active input map.

### Node palette

`GLNodePalette` is the reusable searchable view for a `GLNodeCatalog`. It can be
embedded as a docked control or use `showPopup(...)` to present the same entries
as a categorized creation menu. The palette preserves selection across searches,
uses the catalog's favorites and recents ordering, and can filter candidates by
an application predicate and a compatible `GLNodePortDescriptor`.

```java
GLNodePalette palette = new GLNodePalette(catalog);
palette.setAllowedPredicate(entry -> !entry.type().id().equals(currentType));
palette.setCompatiblePort(connectionSource);
palette.setCompatibleOnly(true);
palette.setActivationListener(entry -> addNode(entry, graphX, graphY));
```

The workspace uses the popup form for `Shift+A` and positioned insertion. It
also excludes the composite currently being edited, preventing direct recursive
insertion. Applications can call `toggleSelectedFavorite()` or access the
contained search editor, list, compatibility control, and status label when
building a custom dock around the palette.

### Property inspector

`GLNodePropertyInspector` builds editors for properties shared by the currently
selected model nodes. Presentation metadata is registered explicitly in a
`GLNodePropertySchemaRegistry`; the inspector never guesses types or ranges from
property names.

The core provides three portable descriptors:

- `GLNumberNodeProperty` uses a bounded `GLSlider` by default. Specify
  `GLNumberNodeProperty.Editor.SPINNER` only when a spinner is preferable.
- `GLEnumNodeProperty` uses a `GLComboBox` with explicit text choices.
- `GLBooleanNodeProperty` uses a `GLCheckBox`.

```java
GLNodePropertySchemaRegistry schemas = new GLNodePropertySchemaRegistry();
schemas.register("audio.gain", List.of(
    new GLNumberNodeProperty("gain", "Gain", 0, 1, 0.01, false),
    new GLEnumNodeProperty("curve", "Curve",
        List.of("linear", "logarithmic")),
    new GLBooleanNodeProperty("bypass", "Bypass")
));

GLNodePropertyInspector inspector =
    new GLNodePropertyInspector(binding, schemas);
```

Pass the same registry to the extended `GLNodeWorkspace` constructor to obtain
a scrollable Properties tab next to the composite Interface tab. Workspaces
created with the shorter constructors retain the interface-only sidebar.

Multi-selection exposes only identical descriptors present on every selected
node and applies a change to all of them in one graph transaction. Colors,
files, audio resources, curves, and other domain values intentionally have no
built-in editor. Applications provide those controls separately.

Slider, combo-box, and checkbox gestures use their normal host history manager.
A complete slider drag is therefore one undoable edit even though its live
intermediate values are reflected in the graph. Undo and redo update the
control and graph together; ordinary graph listeners can use the same changes
to maintain document dirty state.

`GLNodeEditingActions.beginPaste(...)`, `beginDuplicate(...)`, and
`beginMoveSelection(...)` return a `GLNodePlacement`. Applications using
`GLNodeGraphEditingSession` can return its corresponding placement directly;
the editor handles pointer tracking, confirmation, cancellation, and the single
history entry. `requestNodeCreation(...)` receives graph and screen coordinates
for a searchable popup or palette.

The composite-properties sidebar exposes the composite name above its input and
output lists. Committing a new name updates the catalog presentation and records
the change in document history. Inside a composite, the workspace shows a boundary
node only for directions that currently expose at least one visible public port;
an input-only composite therefore has no empty Group Output node, and vice versa.
Projected composite nodes are packed to their current preferred size whenever
their contract is refreshed. Obsolete bounds from a formerly larger interface
are therefore not retained when returning to the containing graph.

Composite extraction is a structural operation rather than a screenshot of the
selection. `GLCompositeExtraction.extract(...)` moves the selected subgraph
into a reusable `GLNodeGraphAsset`, keeps internal connections inside that
asset, derives public typed ports from every boundary endpoint, inserts one
composite instance, and reconnects the surrounding graph through those public
ports. `GLNodeGraphEditingSession.extractSelection(...)` additionally registers
the asset contract with the composite, type, and catalog registries and makes
the complete change undoable.

The returned extraction result supports both storage models:

```java
GLCompositeExtraction.Result extracted = editing.extractSelection(
        "effects.stereo-delay.asset", "effects.stereo-delay",
        "Stereo Delay", "Effects", composites, catalog);

GLNodeGraphDocument standalone = extracted.standaloneDocument();
GLNodeGraphDocument projectDocument = extracted.embedIn(currentDocument);
```

Write the standalone document with `GLNodeGraphDocumentCodec` to produce a
portable `.glgraph` file. Embedding keeps the asset within its containing
project document. When the extracted selection itself contains composites, use
`standaloneDocument(composites)` to include their recursively referenced assets
in the exported document. These persistence choices do not change the public
composite contract or execution behavior.

Applications can use `GLNodeEditingActions.openSelectedComposite()` for the
default Tab action. Open the registered asset in another `GLNodeEditorBinding`
and write graph and view-state changes back through
`GLCompositeGraphRegistry.replaceAsset(...)`. This keeps composite contents
fully editable without requiring a modal dialog; public ports, metadata, and
other instance settings can be presented in the editor's normal property
sidebar. A document-style editor can display the nested binding in the same
editor area, use Escape to return to the containing graph, and project public
input and output mappings as group-input and group-output boundary nodes. The
nested editor can expose the same editing actions as its parent. Its creation
catalog must exclude the composite currently being edited to prevent direct
self-containment; nested instances of other composite types remain valid.
Composite-aware editors should create exactly one group-input and one
group-output boundary node automatically and omit both from the ordinary node
creation catalog. Each boundary node projects the corresponding public port
mappings without becoming an executable operation in the graph. It can remain
movable as editor state while being protected from deletion, duplication,
clipboard operations, and composite extraction. Applications can expose public
port editing through the composite property sidebar.

## Production workspace

`GLNodeWorkspace` provides the complete authoring surface around one visible
`GLNodeEditor`. It includes the searchable Shift+A creation menu, the standard
clipboard and placement commands, in-place composite navigation, a breadcrumb,
Escape/back navigation, and `GLCompositeInterfaceEditor`. The same editor area
is reused at every nesting level, so entering a composite does not create a
modal dialog or a second independent editing model.

```java
GLNodeWorkspace workspace = new GLNodeWorkspace(
        nodeTypes,
        compositeRegistry,
        nodeCatalog,
        (model, type) -> createNodeControl(model, type),
        GLGraphCyclePolicy.DELAYED_FEEDBACK,
        documentHistory);

compositeRegistry.registerDocument(graphDocument);
workspace.openDocument("Audio graph", graphDocument);
```

Tab or a double-click on the node body opens the selected composite; a
double-click on its title retains the normal title-editing behavior. Escape
returns to the containing graph.
The header exposes Back and Forward actions plus clickable breadcrumb segments.
Selecting a segment jumps directly to that ancestor; removed descendants remain
available through Forward until a different composite is opened. Every level
retains its graph, pan/zoom and node bounds, and model-node selection. Returning
to a parent therefore restores the composite instance that led into the child.
`canNavigateBack()`, `canNavigateForward()`, `navigateBack()`,
`navigateForward()`, and `navigateToDepth(...)` expose the same navigation to
application menus and shortcuts.
All `GLNode` instances place input rows before neutral content and output rows
by default, independently of the order in which the rows were added. Use
`GLNode.setPortOrder(...)` to select outputs-first or declaration order for an
individual node. `GLNodeWorkspace.setCompositePortOrder(...)` applies the same
policy to projected composite nodes without changing their persisted contract.
The workspace creates exactly one nondeletable Group Input and Group Output
projection for a composite. They are editor-only views of the public contract
and are never serialized as executable nodes. Their positions are retained as
auxiliary editor view state and persist with the graph asset. The currently edited composite
is excluded from its own creation menu, while other registered composites stay
available.

`GLCompositeInterfaceEditor` presents separate input and output lists. Each
public port exposes its name and an application-defined value type. Inputs can
also select an inline editor and independently control whether a node socket is
visible. The built-in slider metadata selects bar or button presentation,
floating-point or integer values, a reset default, and independently enabled
lower and upper bounds. Enabling both bounds creates fixed-range behavior;
disabling either one permits values beyond that side. Bounds and the default
use `GLNumberSpinner` controls. Low-level endpoint bindings, required-input
state, and connection limits are maintained by the workspace instead of being
shown as user-facing settings. A public port is independent from the nested
implementation. Creating a port never searches for or connects an internal
endpoint. Changing its public type preserves its existing binding even when
that makes the connection temporarily invalid. Such connections use the normal
error border and diagnostic instead of being silently moved or removed.
Connect or disconnect its projected Group Input/Output socket to change the binding.
Flattening rejects an unbound port only when an enclosing graph connects to it.

Both port collections are scrollable and remain separate. Double-click a row to
rename the port in place. Drag a row or use its up/down actions to change the
public interface order. Renames and moves use the same composite-interface
transactions as the remaining settings, so dependent open graphs, undo/redo
history, and persisted port order stay synchronized. The complete interface
inspector is vertically scrollable when its settings exceed the workspace
height; port lists never scroll horizontally. Settings take effect immediately;
there is no separate Apply step. Each completed name edit and each selector,
toggle, or spinner change is an undoable interface transaction. The symbol-only add, remove, and move actions
are drawn by GLSwing and therefore do not depend on optional font glyphs.

In every `GLNode`, labels belonging to output-only rows are right-aligned with
the output edge. Inputs remain left-aligned, while arbitrary controls and shared
input/output rows continue to use the full content width. Rebuilding a composite
boundary after an interface edit preserves the existing Group Input and Group
Output positions; default positions are used only when the corresponding
projection does not yet exist.

Applications register names for their domain types and the editor definitions
available to composite authors:

```java
GLNodeValueTypeRegistry valueTypes = new GLNodeValueTypeRegistry();
valueTypes.register(new GLNodeValueTypeDescriptor("color", "Color"));
valueTypes.register(new GLNodeValueTypeDescriptor("audio", "Audio"));

GLNodeInputEditorRegistry inputEditors = new GLNodeInputEditorRegistry();
inputEditors.register(new GLNodeInputEditorDescriptor(
        "application.color-picker", "Color picker",
        Set.of("color"), Map.of()),
        context -> new GLColorChooser(Color.WHITE));

GLNodeWorkspace workspace = new GLNodeWorkspace(
        nodeTypes, compositeRegistry, nodeCatalog, viewFactory,
        cyclePolicy, documentHistory, valueTypes, inputEditors);
```

Editor IDs and their option maps are portable metadata. An optional runtime
factory registered under the same ID resolves that metadata to a concrete
control; Java factories and control instances are never serialized. A
`GLNodeWorkspace` wraps the application's leaf-node view factory in a
`GLCompositeNodeViewFactory`, which projects composite editors, sockets, order,
and output alignment consistently. The built-in slider already has a Core
factory; application controls such as a color chooser register their own. An
inline input editor is hidden while a socket connection supplies that input and
returns when the connection is removed.
`GLCompositeInterfaceTransactions`
updates the registered asset and node-type descriptor atomically. A renamed
port remaps connections on every open dependent graph supplied to the editor;
removing a port removes those now-invalid connections. Both the contract and
all affected graphs occupy one undo/redo entry. Applications embedding the
sidebar separately should call `setDependentGraphsSupplier(...)` with their
open graph documents. A workspace already supplies its own navigation stack;
use `setAdditionalDependentGraphsSupplier(...)` only when composite instances
also exist in application-owned graphs outside that stack.

The slider's editor default controls its visible reset value. Domain values that
must participate in execution and persistence should still be modeled as typed
`GLCompositePropertyDescriptor` values and mapped to internal node properties;
this keeps transport contracts independent from application state.

Before saving, obtain a detached document value and pass it to the normal
document manager and `.glgraph` codec:

```java
GLNodeGraphDocument current = workspace.snapshotDocument();
document.setContent(current);
documents.save(document);
```

The snapshot contains the current root graph and view state plus the latest
registered version of every embedded composite, including composites extracted
during the session. External asset references remain external. `openRoot(...)`
is available for applications that own persistence themselves; a workspace
opened that way intentionally has no `snapshotDocument()` source document.

Call `close()` when the workspace is disposed to detach graph and history
listeners. Evaluation and backend compilation remain separate services and do
not run inside this UI control.

## Graph documents and reusable composites

`GLNodeGraphDocument` is the persistence root for graph projects and reusable
composites. Its root `GLNodeGraphAsset` contains a stable asset ID, an
application-controlled asset version, a `PROJECT` or `COMPOSITE` kind, the
backend-neutral graph snapshot, a separate editor view state, and portable
metadata values.

Register the ready-made codec with the standard document infrastructure:

```java
GLNodeGraphDocumentCodec graphCodec = new GLNodeGraphDocumentCodec();
GLDocumentCodecRegistry codecs = new GLDocumentCodecRegistry();
codecs.register(graphCodec);

GLDocumentManager documents = new GLDocumentManager(codecs);
GLDocument<GLNodeGraphDocument> graphDocument = documents.create(
        new GLNodeGraphDocument(rootAsset), "Untitled graph", graphCodec);
documents.saveAs(graphDocument, projectDirectory.resolve("audio.glgraph"));
```

The `.glgraph` codec preserves stable graph identities, revisions, node type
IDs and versions, all portable `GLNodeValue` properties, connections, node
bounds, embedded assets, external references, and metadata. It does not require
registered node types while reading, so a missing plugin cannot silently erase
its nodes.
String values use length-prefixed UTF-8 and can contain source code beyond the
size supported by modified-UTF encodings. Collection sizes and nesting depth
are bounded when reading untrusted files.

Embedded composites are independently addressed by asset ID. Export one as a
standalone file without rebuilding its graph:

```java
GLNodeGraphDocument reusable = project.exportAsset("filters.low-pass");
GLDocument<GLNodeGraphDocument> composite = documents.create(
        reusable, "Low-pass filter", graphCodec);
documents.saveAs(composite,
        projectDirectory.resolve("assets/low-pass.glgraph"));
```

The resulting file can be copied into another project and registered as a node
asset. `GLNodeGraphAssetReference` represents an alternative external
dependency using a stable alias, expected asset ID, minimum version, and a
project-relative path. Resolve it against the owning project directory before
opening it through `GLDocumentManager`. Absolute paths, parent-directory
escapes, drive-qualified paths, and backslashes are rejected so project bundles
remain portable and cannot escape their asset root accidentally.

### Asset and node-type migration

File-schema migration, graph-asset migration, and node-type migration are
separate compatibility layers. Register application-owned asset and node rules
before constructing the codec:

```java
GLNodeGraphMigrationRegistry migrations =
        new GLNodeGraphMigrationRegistry();

migrations.registerNodeType("audio.gain", 2);
migrations.addNodeMigration("audio.gain", 1, node ->
        node.withProperty("gainDb",
                node.properties().get("gain"))
            .withoutProperty("gain"));

migrations.registerAsset("filters.low-pass", 3);
migrations.addAssetMigration("filters.low-pass", 2,
        FilterMigrations::exposeResonance);

GLNodeGraphDocumentCodec graphCodec =
        new GLNodeGraphDocumentCodec(migrations);
```

Every rule converts exactly one version to the next. All required steps run on
an immutable detached document before it enters an editor. A missing step,
newer registered subject, changed instance identity, or thrown conversion error
fails the complete operation with `GLNodeGraphMigrationException`; no partial
model is returned. `GLNodeGraphMigrationReport` records every successful asset
and node step. Unknown, unregistered node types remain unchanged so a missing
plug-in does not destroy serialized content.

Schema-1 `.glgraph` files remain readable. Schema 2 introduced node type
versions; schema 3 persists a composite's complete public port/property
contract with its asset. Schema 4 adds portable input-editor options and socket
visibility; schema 5 permits explicitly unbound public ports. Older composites
remain readable and use their stored binding plus socket-only presentation when
no newer presentation data exists. Opening an older file therefore yields a migrated,
modified in-memory document while leaving the original bytes intact until the
user explicitly saves.

Use embedded assets when a project must be self-contained. Use external assets
for shared libraries that should be updated or versioned independently.

## Composite contracts and expansion budgets

`GLCompositeNodeDescriptor` exposes a stored graph asset as a node type.
`GLCompositePortMapping` associates each public typed port with an optional
stable internal endpoint. Unbound ports are valid while authoring but must be
bound before a connected instance can be flattened.
`GLCompositePropertyDescriptor` supplies a stable semantic
type ID, portable value shape, default value, and optional inclusive numeric
range. One public property may feed several internal properties through
`GLCompositePropertyMapping`; one internal target cannot have competing public
sources. The descriptor also declares the minimum compatible asset version.

Store the descriptor directly on its `GLNodeGraphAsset`. Standalone composite
exports therefore remain self-describing:

```java
GLCompositePropertyDescriptor cutoff = new GLCompositePropertyDescriptor(
        "cutoff", "frequency", GLCompositeValueKind.DECIMAL,
        GLNodeValue.of(1000.0), 20.0, 20000.0);

GLCompositeNodeDescriptor filterContract = new GLCompositeNodeDescriptor(
        "audio.low-pass", "filters.low-pass", 3,
        List.of(
                new GLCompositePortMapping(audioInput, internalInput),
                new GLCompositePortMapping(audioOutput, internalOutput)),
        List.of(cutoff),
        List.of(new GLCompositePropertyMapping(
                "cutoff", filterNodeId, "cutoffHz")));

GLNodeGraphAsset filterAsset = new GLNodeGraphAsset(
        "filters.low-pass", 3, GLNodeGraphAssetKind.COMPOSITE,
        graph, viewState, metadata, filterContract);
```

Loaded embedded or external assets and their descriptors are placed in a
`GLCompositeGraphRegistry`; registering a document adds its root and embedded
assets but deliberately performs no file I/O for external references.

Before flattening or compiling a graph, inspect it without materializing the
expanded graph:

```java
GLCompositeExpansionResult estimate = GLCompositeExpansionAnalyzer.analyze(
        projectAsset,
        compositeRegistry,
        GLCompositeExpansionLimits.DEFAULT);

if (!estimate.isValid())
    showProblems(estimate.diagnostics());
```

The default budgets permit 64 nested levels, 100,000 expanded nodes, 200,000
connections, and 100,000 estimated leaf operations. These are independent
limits: recursion is still detected through the complete stable asset-ID path,
while a wide acyclic graph can exceed its node or operation budget without
being deeply nested. `GLCompositeExpansionLimits` accepts application-specific
values. `unlimited()` is an explicit opt-out for trusted inputs and still
rejects recursive composition.

The analyzer reports missing assets, incompatible asset versions, invalid
public-port targets, direct or indirect recursion, and every exceeded budget as
`GLGraphDiagnostic` values attached to the originating composite node. It stops
expansion after a budget failure, preventing a malformed or hostile document
from consuming unbounded time merely to calculate its final size. Leaf-node
count is currently the conservative operation estimate; a future backend
compiler can replace it with implementation-specific operation costs.

After successful analysis, `GLCompositeGraphFlattener.flatten(...)` recursively
replaces every composite instance with its leaf nodes. It applies instance
property overrides or descriptor defaults, rewires outer connections through
the public port mappings, and returns no graph if any mapping or value is
invalid. Generated node and connection IDs are deterministic and include the
complete instance path, so two instances of the same asset never collide while
equivalent input produces an identical execution snapshot.

`GLCompositeFlattenResult.origins()` maps every generated leaf ID to a
`GLCompositeOrigin`. That origin retains all containing composite instances,
the source asset, and the original internal node ID. Backends can consequently
attach compiler errors, runtime measurements, and profiler results to the
visible composite instance without keeping rendering objects in the execution
model.

Use `GLCompositePropertyTransactions.set(...)` or `reset(...)` when editing an
instance through a property inspector. All supplied values are validated before
the graph changes, and the complete edit is recorded as one reversible
`GLHistoryManager` entry.

## Execution plans and backends

The `com.vingame.glswing.node.execution` package converts a validated leaf
graph into an immutable, language-independent `GLExecutionPlan`. Register one
`GLNodeImplementationDescriptor` for every executable node type. Its operation
type ID is a portable backend contract rather than a Java class name or native
symbol:

```java
GLNodeImplementationRegistry implementations =
        new GLNodeImplementationRegistry();

implementations.register(new GLNodeImplementationDescriptor(
        "math.add", "numeric.add", GLNodeImplementationKind.PURE));
implementations.register(new GLNodeImplementationDescriptor(
        "audio.output", "audio.output", GLNodeImplementationKind.EFFECT));

GLExecutionPlanResult result = GLExecutionPlanCompiler.compile(
        projectAsset,
        compositeRegistry,
        GLCompositeExpansionLimits.DEFAULT,
        nodeTypes,
        implementations,
        GLGraphCyclePolicy.DELAYED_FEEDBACK,
        new GLExecutionPlanOptions("sample"));
```

This overload first performs bounded, transactional composite flattening. A
failed validation or expansion returns diagnostics and never exposes a partial
plan. Successful results retain the `GLCompositeOrigin` of every retained leaf,
allowing a backend failure or profiler sample to be traced through the complete
visible composite instance path.

Compilation removes graph branches that cannot reach an implementation marked
as observable. `EFFECT` implementations are always observable. If a graph has
no explicitly observable operation, all nodes are retained; this makes plans
for reusable fragments inspectable instead of silently producing an empty
program. A pure implementation may additionally register a
`GLNodeConstantEvaluator`. When all connected inputs are current-step constants,
the compiler evaluates the operation once and stores its outputs directly in
the plan.

Current-step dependencies are sorted topologically. Nodes that are otherwise
independent are ordered by stable UUID, so equivalent snapshots produce the
same operation order and SHA-256 plan hash. Connections from a
`PREVIOUS_STEP` output do not constrain the current schedule. They read a
persistent state slot from the completed preceding evaluation; the first read
has no prior value. Under the global `GLGraphCyclePolicy.PREVIOUS_STEP` policy,
the compiler applies this timing to every connection even when individual port
descriptors declare current-step timing. Temporary values receive lifetime-based reusable slots,
while previous-step outputs receive dedicated state slots. Static statistics
report retained nodes, folded and scheduled operations, estimated operation
cost, required temporary and state storage, and maximum feed-forward latency.

The compilation option names the latency quantum without defining its physical
duration. An audio graph can use `sample`, a block processor can use `frame`,
and a simulation can use `tick`. Implementations declare a nonnegative fixed
latency or register a `GLNodeLatencyEstimator` when properties such as delay
length determine it. The compiler propagates latency over current-step edges.
At a node with parallel input paths, each `GLExecutionInput` records the
additional `compensationLatency()` required to align it with the latest input.
Folded constants have zero runtime latency. Previous-step inputs are already
available at the evaluation boundary and are deliberately excluded from
feed-forward compensation. A backend must either honor these compensation
values or reject the plan through its capability contract.

Backends implement `GLExecutionBackend<T>` and consume only the plan. They do
not depend on `GLNodeEditor`, OpenGL, or graph-document controls. The included
`GLJavaExecutionBackend` is a small deterministic reference implementation for
unit tests, offline evaluation, and future conformance fixtures:

```java
GLJavaExecutionBackend javaBackend = new GLJavaExecutionBackend();
javaBackend.register("numeric.add", (context, inputs, properties) -> {
    long left = ((GLNodeValue.IntegerValue) inputs.get("left").get(0)).value();
    long right = ((GLNodeValue.IntegerValue) inputs.get("right").get(0)).value();
    return Map.of("result", GLNodeValue.of(left + right));
});

GLJavaExecutablePlan executable = javaBackend.prepare(result.plan().orElseThrow());
GLJavaExecutionResult step = executable.evaluate();
```

Each input ID maps to a list because a domain may permit more than one
connection on a port. A prepared Java plan owns its previous-step state and can
be restarted with `reset()`. `nextOperation()` exposes the operation at the
execution cursor, and `executeNext()` advances by at most one operation while
returning a `GLJavaExecutionTrace` with immutable inputs, outputs, and measured
duration. State is committed only after the final operation completes; a
failure abandons the partial step. Calling `evaluate()` completes the current
step, including one already started through incremental execution. This makes
the reference backend suitable for inspectors, operation stepping, and
breakpoints without weakening its step-boundary semantics. It validates every returned output and throws
`GLJavaExecutionException` with the originating `GLExecutionOperation` if an
implementation fails. Applications should use this backend as a correctness
reference, not on a real-time audio callback. Generated C++, GPU, remote, and
product-specific runtimes can implement the same backend SPI independently.

Every backend advertises a stable `GLExecutionBackendDescriptor`: supported
plan-version range, operation IDs, latency units, previous-step support, and
whether it can apply input-latency compensation.
`GLExecutionBackendCompatibility.check(...)` validates these capabilities
before toolchain work begins and returns structured `GLBackendDiagnostic`
objects rather than relying on exception text. Runtime failures can attach an
operation index, leaf node, and complete composite origin. The Java reference
backend performs this compatibility check in `prepare(...)`.

An execution inspector can rebuild the visible composite hierarchy from each
operation's ordered `GLCompositeOrigin.instances()` path. Selecting an internal
operation should keep the outermost visible composite selected in the graph
while presenting the leaf operation's ports, values, storage, latency, and
trace duration. Aggregate views can sum operations and measured durations whose
origin begins with the selected composite path. This prefix rule remains
unambiguous for repeated or recursively nested composite assets.

`GLExecutionPlanCodec` provides the versioned `GLXP` binary exchange format for
passing plans to another process or persisting a build input. It preserves
operations, typed portable properties, value storage, latency, and composite
origins. Payload sizes and collection counts are bounded and a SHA-256 envelope
checksum rejects truncated or modified data. The plan format version is
independent of graph-document schema and of any native ABI version.

Use `GLExecutionPlanController<T>` when builds happen asynchronously. It
prepares plans on an application-supplied executor, publishes only the newest
successful request, and retains the previous working plan after rejection or
failure. An execution thread obtains a `GLExecutionPlanLease<T>` through
`acquire()`. Swapping is atomic; retired backend resources are passed to the
configured disposer only after their final outstanding lease closes. This lets
an audio or rendering callback finish with a stable prepared plan without
holding a lock or observing a partially replaced plan.

GLSwing deliberately does not provide a C++, Rust, GPU, or audio runtime. Such
integrations should be separate projects that depend on `glswing-core`, consume
the same exchange plan and backend contract, and version their toolchains and
native ABIs independently. GLSwing never depends on those projects.

## `GLNode`

A node owns title, selection state, width, rows, and ports. Resize behavior is
controlled by `GLNodeResizeMode` (`NONE`, `HORIZONTAL`, `VERTICAL`, or `BOTH`).
The editor tracks the active node separately from the selected node set;
`isActiveSelected()` distinguishes the primary selection.

Node content can be added and removed with `addContent(...)` and
`removeContent(...)`. Input/output convenience methods create the internal row
structure while returning the public `GLNodePort` or `GLNodePorts` handles.
`getContents()`, `getPorts()`, and `findPort(...)` expose the current structure
without making row layout part of the public API.

## Rows, content, and ports

Rows align an input, center content, and output. `addInputOutput(...)` creates a
row with both directions. A port can hide its associated content while
connected through `setContentHiddenWhenConnected(true)`;
`isContentHiddenByConnection()` reports the result.

Port direction determines valid orientation. `setMaximumConnections(...)`
limits fan-in or fan-out. `canConnectTo(...)` performs port-level checks;
`editor.canConnect(...)` also applies editor policies and listener validation.

## Connection rules

`GLNodeConnection` links compatible ports. Connections cannot attach a port to
itself, duplicate a prohibited edge, or exceed connection limits. The editor
owns displayed connections; add and remove them through editor methods so
selection, repaint, callbacks, and history remain consistent.

`GLNodeEditorListener` receives graph interaction events and can validate or
mirror changes into an application model. Keep listener work fast and avoid
mutating editor collections recursively during dispatch.

## Selection and interaction

The editor supports single and multi-node selection, an active node, and a
selected connection. Public methods include `setSelectedNode`,
`setSelectedNodes`, `setNodeSelected`, `clearSelection`, and
`setSelectedConnection`. `getSelectedNodes()` returns the current set.

Users can drag nodes, resize enabled axes, create/remove connections, edit
titles, pan, zoom, and select via click or marquee. `dragAnywhere=true` allows
dragging from the node body instead of only its title region.

## View and geometry

Node positions and dimensions use editor/world units. Connection widths,
borders, snap distance, and curve tolerance are style values in logical screen
pixels unless the relevant style property documents otherwise.

`zoomByAtScreenPosition(...)` keeps the world point under the cursor stable.
Connection curves use `connectionHandleFactor` and
`minimumConnectionHandleLength`; setting both to zero produces the shortest
direct handle behavior.

Wheel zooming and wheel panning repaint the visible editor continuously while
the input is active. The picking buffer is rebuilt after a short idle period,
which avoids repeated off-screen picking passes during a wheel gesture. Direct
calls to the zoom and pan API rebuild picking immediately, so programmatic view
changes are ready for pointer input when the call returns.

During interactive viewport updates, node dragging, and connection dragging,
connection curves use the
`node.interactive-curve-tolerance-factor` input preference. The normal curve
tolerance is restored for the settled frame. A pointer press during the idle
period first completes picking, preventing dispatch against stale geometry.

Retained connection polylines are cached per connection. Moving a node
invalidates only curves connected to that node; unchanged curves reuse their
sampled points for both border and gradient drawing. Zoom, tolerance, handle,
or outer transform-scale changes invalidate affected cache entries
automatically. `getLastCurveTessellationCount()`,
`getLastCurveCacheHitCount()`, and `getLastCurveSegmentCount()` expose the most
recent frame's curve workload for diagnostics and benchmarks.

`NodeEditorBenchmarkDemo` creates a deterministic 80-node graph and alternates
between automated node movement and zoom phases. Its overlay reports CPU/GPU
scene time, frame rate, draw calls, tessellations, cache hits, and submitted
curve segments.

## Styles

`GLNodeStyle` controls individual node appearance. `GLNodeEditorStyle` controls
canvas, connection, selection, snapping, and curve geometry. Apply local styles
with `setGLStyle(...)` and return to theme inheritance with `clearGLStyle()`.

The canvas can render a graph-coordinate grid that follows pan and zoom. Theme
keys `nodeEditor.gridVisible`, `.gridSpacing`, `.gridMajorInterval`,
`.gridLineWidth`, `.gridMinor`, and `.gridMajor` configure it. Typed styles can
replace the same values with `GLNodeEditorStyle.withGrid(...)`.

Connection customization includes normal and selected color, border color and
width, main width, curve tolerance, handle length, and a
`GLColorInterpolator`. `clearColor()`, `clearWidth()`, and related clear methods
restore inherited values.

## History and model synchronization

User operations integrate with the host `GLHistoryManager`. Treat one drag or
multi-selection operation as one undo step. Programmatic graph loading should
normally suppress or reset history, populate the editor, then call
`markPersisted()`.

The editor is a view/controller, not the authoritative domain model. New code
should use `GLNodeGraph` as its domain state and `GLNodeEditorBinding` for
synchronization. The editor's existing history records visual connection,
movement, resize, and deletion gestures; replayed structural gestures are
mirrored back into the model. Document-level graph history and persisted
checkpoints should operate on immutable graph and view-state snapshots.
