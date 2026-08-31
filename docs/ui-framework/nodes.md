# Node Editor

[UI guide](../ui-framework.md) · [Controls](controls.md) · [Undo and redo](history.md)

`GLNodeEditor` provides an interactive canvas for nodes, typed-direction ports,
connections, selection, panning, zooming, and history integration. The
application remains responsible for its domain graph and evaluation logic.

## Contents

- [Minimal graph](#minimal-graph)
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

## `GLNode`

A node owns title, selection state, width, rows, and ports. Resize behavior is
controlled by `GLNodeResizeMode` (`NONE`, `HORIZONTAL`, `VERTICAL`, or `BOTH`).
The editor tracks the active node separately from the selected node set;
`isActiveSelected()` distinguishes the primary selection.

Node content can be added and removed with `addContent(...)` and
`removeContent(...)`. Input/output convenience methods create `GLNodeRow`
structures. `getContents()`, `getPorts()`, `getInput()`, and `getOutput()` expose
the current structure.

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

Connection customization includes normal and selected color, border color and
width, main width, curve tolerance, handle length, and a
`GLColorInterpolator`. `clearColor()`, `clearWidth()`, and related clear methods
restore inherited values.

## History and model synchronization

User operations integrate with the host `GLHistoryManager`. Treat one drag or
multi-selection operation as one undo step. Programmatic graph loading should
normally suppress or reset history, populate the editor, then call
`markPersisted()`.

The editor is a view/controller, not the authoritative domain model. Mirror
accepted edits through listeners, serialize stable node/port IDs and domain
properties, then reconstruct nodes and connections when loading.
