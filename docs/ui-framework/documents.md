# Documents and serialization

- [Responsibility boundaries](#responsibility-boundaries)
- [Documents and persisted state](#documents-and-persisted-state)
- [Active documents and tabs](#active-documents-and-tabs)
- [Document lifecycle and commands](#document-lifecycle-and-commands)
- [Closing modified documents](#closing-modified-documents)
- [Codecs and schema migration](#codecs-and-schema-migration)
- [Plain files](#plain-files)
- [Compound document bundles](#compound-document-bundles)
- [Versions and forward compatibility](#versions-and-forward-compatibility)
- [Threading and storage guarantees](#threading-and-storage-guarantees)

The document API represents any independently editable application model. A
document may contain text, a node graph, a theme, a scene, binary data, or an
application-specific aggregate. It is not tied to an editor control or a
particular serialization format.

## Responsibility boundaries

| Type | Responsibility |
|---|---|
| `GLDocument<T>` | Typed content, title, location, read-only flag, history, and persisted state |
| `GLDocumentCodec<T>` | Conversion between one model type and a serialized representation |
| `GLVersionedDocumentCodec<T>` | Consecutive migrations from older entry schemas to the current schema |
| `GLDocumentCodecRegistry` | Resolution by stable type/codec IDs or file extension |
| `GLDocumentManager` | Asynchronous plain-file and bundle I/O with atomic replacement |
| `GLDocumentBundle` | Indexed package containing independently readable document entries |
| `GLDocumentWorkspace` | Routes application-level undo/redo state and commands to the active document |
| `GLDocumentTabController` | Associates tab content trees with documents and their history scopes |
| `GLDocumentLifecycle<T>` | New, open, save, save-as, save-all, and close coordination for one document type |

Views remain separate. An application may show the same `GLDocument<NodeGraph>`
in a node editor, inspector, and minimap without coupling serialization to any
of those components.

## Documents and persisted state

```java
GLDocument<NodeGraph> document =
        new GLDocument<>(new NodeGraph(), "Untitled graph");

document.historyManager().perform(
        "Add node",
        () -> document.content().add(node),
        () -> document.content().remove(node));

if (document.isModified())
    showDirtyMarker();
```

Every document owns a `GLHistoryManager`. Its persisted checkpoint contributes
to `isModified()`, so undoing back to the stored state removes the dirty marker.
For an in-place model mutation that is deliberately not recorded as an
undoable edit, call `markModified()`. GLSwing cannot observe arbitrary changes
inside an application model automatically. A successful save calls
`markPersisted()`; it does not clear undo/redo history.

Live editor sessions can report changes before they become one document-history
operation through `setTransientModified(source, true)`. The tab controller
configures this automatically when a `GLTextArea` or `GLCodeEditor` directly
displays the document's `GLTextDocument`. The `* ` modified-title prefix
therefore appears on the first edit and remains visible when a long title is
ellipsized. It disappears when live text returns to its persisted snapshot and
is cleared by a successful save.

`location()` is a `URI` and may be absent for a new document. `readOnly` is
metadata for views and prevents `GLDocumentManager` from writing a plain
document. A bundle entry has its own `GLDocument`, history, and dirty state.

## Active documents and tabs

Focused controls already resolve undo/redo through the nearest inherited
history scope. `GLDocumentTabController` assigns each bound tab content tree to
its document manager, tracks the selected document, updates modified tab
titles, and retains the association when a closed tab is reopened:

```java
GLDocumentWorkspace workspace =
        new GLDocumentWorkspace(applicationHistory);
GLDocumentTabController documents =
        new GLDocumentTabController(tabs, workspace);

documents.addDocument(graphDocument, nodeEditor);
documents.addDocument(sourceDocument, codeEditor);
```

Keyboard undo/redo inside `nodeEditor` now affects `graphDocument`; the same
shortcut inside `codeEditor` affects `sourceDocument`. Application menu actions
use the workspace:

```java
undoItem.addActionListener(event -> workspace.undo());
redoItem.addActionListener(event -> workspace.redo());

workspace.addListener(source -> {
    undoItem.setEnabled(source.canUndo());
    undoItem.setText(source.canUndo()
            ? "Undo " + source.undoName()
            : "Undo");
});
```

The fallback manager handles commands when no document is active. If the tab
group is itself one page in a larger interface, call `setActive(false)` on its
controller while that page is hidden; routing then returns to the fallback.
Close the controller and workspace with their owning view to detach document
listeners and history scopes.

## Document lifecycle and commands

`GLDocumentLifecycle<T>` assembles a manager, codec, tab controller, file-dialog
service, model factory, and view factory. It provides the conventional document
commands without prescribing menu layout or the application's editor component:

```java
GLDocumentCodecRegistry codecs = new GLDocumentCodecRegistry();
NodeGraphCodec graphCodec = new NodeGraphCodec();
codecs.register(graphCodec);

GLDocumentManager manager = new GLDocumentManager(codecs);
GLFileDialogs fileDialogs = GLFileDialogs.auto();
GLDocumentLifecycle<NodeGraph> lifecycle = new GLDocumentLifecycle<>(
        manager,
        graphCodec,
        tabDocuments,
        fileDialogs,
        window,
        NodeGraph::new,
        document -> new NodeGraphEditor(document.content()));

newItem.addActionListener(event -> lifecycle.newDocument());
openItem.addActionListener(event -> lifecycle.open());
saveItem.addActionListener(event -> lifecycle.save());
saveAsItem.addActionListener(event -> lifecycle.saveAs());
saveAllItem.addActionListener(event -> lifecycle.saveAll());
closeItem.addActionListener(event -> lifecycle.closeActiveDocument());
```

`open()` and save-as operations use the configured asynchronous file-dialog
backend. `open(Path)` bypasses the chooser. The codec's extensions populate the
format filter and an All files option remains available. UI view creation after
an asynchronous open is marshalled to the AWT event dispatch thread.
Applications can pass localized chooser titles and filter descriptions through
`setDialogText(...)`; `setUntitledName(...)` supplies the localized base name
used for new documents.
`saveAll()` visits modified writable documents in tab order. Cancelling a
required save-as chooser stops the sequence and leaves that document and all
following documents untouched; the result contains paths written before the
cancellation. Before a write starts, the lifecycle commits editor-local
transactions into the document history. A subsequent focus change therefore
cannot reintroduce changes that were already included in the saved checkpoint.

Use `canSave()`, `canSaveAs()`, `canSaveAll()`, and `canClose()` to maintain
command state. `canSave()` is true only for a modified writable document;
`canSaveAs()` also permits a clean writable document. Lifecycle listeners fire
when the selected document or its state changes:

```java
lifecycle.addListener(source -> {
    saveItem.setEnabled(source.canSave());
    saveAsItem.setEnabled(source.canSaveAs());
    saveAllItem.setEnabled(source.canSaveAll());
    closeItem.setEnabled(source.canClose());
});
```

`GLPlainTextDocumentCodec` is the ready-made UTF-8 codec for source and plain
text stored in a `GLTextDocument`. Application formats should provide a codec
that preserves all model data relevant to that format.

## Closing modified documents

User-initiated tab closure is asynchronous. Configure the lifecycle with a
handler that presents the application's themed confirmation UI and completes
with `SAVE`, `DISCARD`, or `CANCEL`:

```java
lifecycle.setCloseRequestHandler(document ->
        showUnsavedChangesDialog(document.title()));
```

`SAVE` waits for persistence and closes only after a successful write. Cancelling
either the confirmation or a required save-as chooser leaves the tab open.
`DISCARD` closes without writing and removes that view from recently closed tab
history so discarded in-memory changes cannot be restored accidentally.
`CANCEL` makes no change. Repeated close gestures share the pending request,
preventing duplicate dialogs or writes.

Before closing an application window, call `prepareCloseAll()`. It visits every
modified document in tab order and applies the same save, discard, or cancel
policy. It deliberately retains all tabs: a successful result authorizes the
application teardown, while `false` leaves the complete interface usable.

```java
.onCloseRequest(context -> lifecycle.prepareCloseAll())
```

At the tab level, close buttons, middle-click, Close, and Close Others call
`GLTabbedPane.requestCloseTabAt(...)`. A general tab application can install its
own `GLTabCloseRequestHandler`. `removeTabAt(...)` deliberately remains an
immediate programmatic removal operation for teardown and other cases that have
already obtained approval. `GLTabCloseListener` remains a post-close notification.

## Codecs and schema migration

Codecs use stable IDs because Java class names are not persistent format
identifiers:

```java
final class NodeGraphCodec extends GLVersionedDocumentCodec<NodeGraph> {
    NodeGraphCodec() {
        addMigration(1, NodeMigrations::portsToStableIds);
        addMigration(2, NodeMigrations::addConnectionKinds);
    }

    @Override public String typeId() { return "example.node-graph"; }
    @Override public String codecId() { return "json"; }
    @Override public Class<NodeGraph> contentType() { return NodeGraph.class; }
    @Override public int currentSchemaVersion() { return 3; }
    @Override public Set<String> fileExtensions() { return Set.of("glnodes"); }

    @Override protected NodeGraph readCurrent(InputStream input) throws IOException {
        return readGraphJson(input);
    }

    @Override public void write(NodeGraph graph, OutputStream output)
            throws IOException {
        writeGraphJson(graph, output);
    }
}
```

Each registered migration receives the serialized bytes of version `n` and
must return version `n + 1`. Migrations run in order before `readCurrent`.
Applications with streaming or format-specific migration requirements can
implement `GLDocumentCodec` directly and interpret `schemaVersion` themselves.

```java
GLDocumentCodecRegistry codecs = new GLDocumentCodecRegistry();
NodeGraphCodec nodeGraphs = new NodeGraphCodec();
codecs.register(nodeGraphs);
```

The pair `typeId + codecId` must be unique. Multiple codecs may represent the
same model type, such as JSON and a compact binary encoding.

## Plain files

`GLDocumentManager` resolves plain files by model class and extension, then
performs conversion away from the rendering thread:

```java
try (GLDocumentManager documents = new GLDocumentManager(codecs)) {
    documents.open(path, NodeGraph.class).thenAccept(document ->
            EventQueue.invokeLater(() -> openGraphTab(document)));
}
```

Use `create(content, title, codec)` for a new document. `save(document)` writes
to its existing file URI; `saveAs(document, path)` assigns a new location.
`manage(document, codec)` adopts an existing document, which is useful when an
already constructed editor is introduced into a managed workspace.
Plain files do not carry a GLSwing manifest, so extension-based opening assumes
the codec's current schema. If an external format supplies its own version,
pass that version to `open(path, codec, schemaVersion)`.

## Compound document bundles

`GLDocumentBundle` is a ZIP-based container with a small indexed manifest. It
stores each logical entry as a separate payload, allowing an application to
read one graph without decoding its themes, thumbnails, or other graphs:

```java
GLDocumentBundle project = GLDocumentBundle.create(
        "example.game-project", 2, "Editor 1.4", codecs);

project.put("graph/main", mainGraph, nodeGraphs);
project.put("graph/materials", materialGraph, nodeGraphs);
project.put("theme/editor", editorTheme, themeCodec);
project.save(projectFile);
```

Opening reads only the manifest:

```java
GLDocumentBundle project = GLDocumentBundle.open(projectFile, codecs);

GLDocument<NodeGraph> graph =
        project.open("graph/main", NodeGraph.class).document();
```

`GLDocumentBundle.open` is the synchronous primitive. UI applications can use
`GLDocumentManager.openBundle(...)` and `openEntry(...)` to perform both the
manifest read and the selected payload decode on the manager's I/O executor.

`entries()` can populate a project explorer without loading payloads.
`isLoaded(id)` reports whether a payload has been decoded. `put` adds or
replaces an entry, while `remove` removes its indexed payload on the next save.
Loaded entry documents use a `jar:` location that identifies their payload;
they must be saved through their owning bundle rather than as plain files.
Entry IDs may be hierarchical, but hierarchy is organizational: a port or node
normally remains part of its graph model. Promote a value to a bundle entry
only when it needs independent loading, serialization, history, or editing.

Bundle saves are atomic and preserve unloaded entries. They also preserve
unindexed archive resources, enabling application-specific metadata. This is
especially important when a tool recognizes only part of an extensible project
format.

## Versions and forward compatibility

Three version domains remain independent:

- `container.version` describes the GLSwing ZIP/manifest structure;
- `format.version` belongs to the application-level project format;
- each entry's `schemaVersion` belongs to its registered codec.

The bundle rejects unsupported container versions. A codec rejects a newer or
otherwise unsupported entry schema before decoding it. Loading an older entry
through `GLVersionedDocumentCodec` marks the bundle modified; saving rewrites
that loaded entry at the current schema. Unloaded entries retain their original
schema and bytes, even if their codec is not installed.

Applications control project-wide `formatVersion` migrations. They can inspect
the manifest and load only the entries required for a particular migration.
`createdWith` is informational and must not replace schema checks.

## Threading and storage guarantees

The default `GLDocumentManager` uses daemon I/O workers and implements
`AutoCloseable`. A manager created with an application executor does not own or
shut down that executor. Completion callbacks run according to the returned
stage, so callbacks that change GLSwing controls must marshal back to the AWT
event dispatch thread.

Plain documents and bundles are written to a temporary sibling and then moved
over the destination. An atomic move is used when the filesystem supports it;
otherwise GLSwing falls back to replacement after the temporary file has been
completed. Failed writes leave the previous destination untouched whenever the
filesystem provides the normal replacement guarantees.
