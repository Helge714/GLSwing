# Inserting nodes into connections

Register `GLNodeInsertionPorts(inputId, outputId)` with
`GLNodeTypeRegistry.setInsertionPorts(typeId, ports)`. Unconfigured types do not
participate; passing null disables insertion. Input and output must exist with
the corresponding directions. Both replacement connections must have compatible
value types. Existing connections on any port exclude the dragged node.

`GLNodeGraphEditingSession` installs the model-aware handler on its editor.
Dragging a single unconnected node across a wire highlights the nearest eligible
wire intersecting its bounds. Release replaces exactly that wire by two wires.
Capture loss does not insert. Move/paste placements use the same preview and clear
it on cancellation. A plain editor may install its own `ConnectionInsertionHandler`.

`nodeEditor.horizontalNodeSpacing` defines the horizontal edge-to-edge gap in graph
units (default 60). In a sufficiently large gap, the drop position is clamped to
the available interval without moving the other nodes. Otherwise the inserted
node is placed one gap after the source, and the target moves right far enough to
leave another gap. Connected nodes at or right of the target's original x move by
the same amount. Traversal visits each node once, excludes the source anchor and
does not move unrelated nodes. Y coordinates remain unchanged.

Placement and connection insertion have separate history entries. The first undo
restores the original wire and pre-insertion spacing while keeping the node at
its drop position. The second undo restores the original position, or removes the
pasted/duplicated nodes. Undo/redo also works in a different composite level.

Ctrl+Delete invokes `GLNodeEditingActions.exclude()`. Configure a preferred input
using `GLNodeTypeRegistry.setExclusionInput(typeId, inputId)` independently of wire
insertion. If absent or unconnected, the first connected input in descriptor order
is used (for multiple wires on that port, the first in snapshot order). Every
external destination of every output is considered for reconnection to the chosen
source. Only matching value types are connected; capacity or cycle violations skip
that replacement. Additional inputs are disconnected. Without a source or a matching
type the nodes are simply deleted. Selected chains resolve sources through the
same preference/fallback rules; closed selected cycles have no external source.
Deletion and accepted replacements are one atomic, undoable edit. Existing unrelated
graph diagnostics, including missing required inputs, do not block deletion.
Only simple linear chains close their gap. Branching, merging, reversed layouts
or collisions leave positions unchanged. Normal Delete is unchanged.

Clipboard fragments carry a graph-space pointer offset. Interactive paste restores
this offset even after zoom changes or composite navigation. Duplicate starts at
the original nodes' positions and follows pointer movement without snapping their
top-left corner to the pointer. Three-argument fragment construction remains
available and defaults to a zero offset. Explicit synchronous `paste(x,y)` retains
its top-left-coordinate semantics.
