package com.vingame.glswing.node;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.history.GLHistoryManagerListener;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLKeyPressCaptureTarget;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.node.binding.GLNodeEditorBinding;
import com.vingame.glswing.node.binding.GLNodeEditorViewState;
import com.vingame.glswing.node.binding.GLNodeGraphEditingSession;
import com.vingame.glswing.node.binding.GLNodeViewState;
import com.vingame.glswing.node.binding.GLNodeViewFactory;
import com.vingame.glswing.node.composite.GLCompositeGraphRegistry;
import com.vingame.glswing.node.composite.GLCompositeInterfaceEditor;
import com.vingame.glswing.node.composite.GLCompositeInterfaceTransactions;
import com.vingame.glswing.node.composite.GLCompositeNodeDescriptor;
import com.vingame.glswing.node.composite.GLCompositeNodeViewFactory;
import com.vingame.glswing.node.composite.GLNodeInputEditorRegistry;
import com.vingame.glswing.node.composite.GLCompositePortMapping;
import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.document.GLNodeGraphDocument;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLGraphCyclePolicy;
import com.vingame.glswing.node.model.GLNodeCatalog;
import com.vingame.glswing.node.model.GLNodeCatalogEntry;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraph;
import com.vingame.glswing.node.model.GLNodeGraphFragment;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodePropertySchemaRegistry;
import com.vingame.glswing.node.model.GLNodeTypeRegistry;
import com.vingame.glswing.node.model.GLNodeValueTypeRegistry;
import com.vingame.glswing.node.model.GLPortDirection;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLScrollPane;
import com.vingame.glswing.ui.GLTabbedPane;

/**
 * Production node-authoring surface with searchable creation, one visible
 * editor, in-place composite navigation, breadcrumbs, and a composite-port
 * sidebar. Execution remains outside this presentation-oriented control.
 */
public final class GLNodeWorkspace extends GLContainer
		implements AutoCloseable, GLKeyPressCaptureTarget {
	public record CompositeExtraction(String assetId, String nodeTypeId,
			String displayName, String category) {
		public CompositeExtraction {
			if (assetId == null || assetId.isBlank() || nodeTypeId == null
					|| nodeTypeId.isBlank() || displayName == null
					|| displayName.isBlank() || category == null || category.isBlank())
				throw new IllegalArgumentException("Complete composite extraction required");
		}
	}

	private static final Color SURFACE = new Color(28, 31, 35);
	private static final float HEADER_HEIGHT = 40;
	private static final String BOUNDARY_INPUT_VIEW = "composite.boundary.input";
	private static final String BOUNDARY_OUTPUT_VIEW = "composite.boundary.output";
	private final GLNodeTypeRegistry types;
	private final GLCompositeGraphRegistry composites;
	private final GLNodeCatalog catalog;
	private final GLCompositeNodeViewFactory viewFactory;
	private final GLHistoryManager history;
	private final ArrayDeque<Document> documents = new ArrayDeque<>();
	private final ArrayDeque<Document> forwardDocuments = new ArrayDeque<>();
	private final LinkedHashSet<String> embeddedAssetIds = new LinkedHashSet<>();
	private final GLButton back = new GLButton("<");
	private final GLButton forward = new GLButton(">");
	private final GLNodeBreadcrumbBar breadcrumb = new GLNodeBreadcrumbBar();
	private final WorkspaceEditor editor = new WorkspaceEditor();
	private final GLCompositeInterfaceEditor interfaceEditor;
	private final GLScrollPane interfaceScroll;
	private final GLNodePropertySchemaRegistry propertySchemas;
	private final GLTabbedPane inspectorTabs;
	private final GLScrollPane propertyScroll;
	private GLNodePropertyInspector propertyInspector;
	private final GLNodePalette nodePalette;
	private GLPopupMenu creationMenu;
	private final GLHistoryManagerListener historyListener = manager ->
			refreshRegisteredInterface();
	private GLNodeEditorBinding binding;
	private GLNodeGraphEditingSession editing;
	private GLNodeGraph.Listener graphListener;
	private GLNodeGraphFragment clipboard;
	private GLGraphCyclePolicy cyclePolicy;
	private Supplier<CompositeExtraction> extractionSupplier;
	private float inspectorWidth = 300;
	private boolean inspectorVisible = true;
	private boolean synchronizingAsset;
	private boolean rebuildingBoundaries;
	private boolean boundarySyncScheduled;
	private boolean closed;
	private GLCompositeNodeDescriptor displayedDescriptor;
	private GLNodeGraphDocument sourceDocument;
	private Runnable changeListener = () -> { };
	private Runnable rootNavigationAction;
	private Supplier<? extends Collection<GLNodeGraph>> additionalDependentGraphs =
			List::of;

	public GLNodeWorkspace(GLNodeTypeRegistry types,
			GLCompositeGraphRegistry composites, GLNodeCatalog catalog,
			GLNodeViewFactory viewFactory, GLGraphCyclePolicy cyclePolicy,
			GLHistoryManager history) {
		this(types, composites, catalog, viewFactory, cyclePolicy, history,
				new GLNodeValueTypeRegistry(), new GLNodeInputEditorRegistry(),
				new GLNodePropertySchemaRegistry());
	}

	public GLNodeWorkspace(GLNodeTypeRegistry types,
			GLCompositeGraphRegistry composites, GLNodeCatalog catalog,
			GLNodeViewFactory viewFactory, GLGraphCyclePolicy cyclePolicy,
			GLHistoryManager history, GLNodeValueTypeRegistry valueTypes,
			GLNodeInputEditorRegistry inputEditors) {
		this(types, composites, catalog, viewFactory, cyclePolicy, history,
				valueTypes, inputEditors, new GLNodePropertySchemaRegistry());
	}

	public GLNodeWorkspace(GLNodeTypeRegistry types,
			GLCompositeGraphRegistry composites, GLNodeCatalog catalog,
			GLNodeViewFactory viewFactory, GLGraphCyclePolicy cyclePolicy,
			GLHistoryManager history, GLNodeValueTypeRegistry valueTypes,
			GLNodeInputEditorRegistry inputEditors,
			GLNodePropertySchemaRegistry propertySchemas) {
		this.types = Objects.requireNonNull(types, "Node type registry");
		this.composites = Objects.requireNonNull(composites, "Composite registry");
		this.catalog = Objects.requireNonNull(catalog, "Node catalog");
		GLNodeViewFactory leaves = Objects.requireNonNull(viewFactory, "Node view factory");
		this.viewFactory = new GLCompositeNodeViewFactory(composites, inputEditors,
				leaves, id -> catalog.find(id).map(GLNodeCatalogEntry::displayName)
						.orElse(id));
		this.cyclePolicy = Objects.requireNonNull(cyclePolicy, "Cycle policy");
		this.history = Objects.requireNonNull(history, "History manager");
		this.propertySchemas = Objects.requireNonNull(propertySchemas,
				"Property schema registry");
		nodePalette = new GLNodePalette(catalog);
		interfaceEditor = new GLCompositeInterfaceEditor(composites, types,
				valueTypes, inputEditors, history);
		interfaceScroll = new GLScrollPane(interfaceEditor);
		interfaceScroll.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
		interfaceScroll.setVerticalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.AS_NEEDED);
		interfaceScroll.setContentTracksViewportWidth(true);
		interfaceScroll.setScrollUnit(30);
		if (propertySchemas.isEmpty()) {
			inspectorTabs = null;
			propertyScroll = null;
		} else {
			inspectorTabs = new GLTabbedPane();
			propertyScroll = new GLScrollPane(new GLLabel("No graph loaded"));
			propertyScroll.setHorizontalScrollBarPolicy(
					GLScrollPane.ScrollBarPolicy.NEVER);
			propertyScroll.setVerticalScrollBarPolicy(
					GLScrollPane.ScrollBarPolicy.AS_NEEDED);
			propertyScroll.setContentTracksViewportWidth(true);
			inspectorTabs.addTab("Properties", propertyScroll);
			inspectorTabs.addTab("Interface", interfaceScroll);
		}
		interfaceEditor.setDependentGraphsSupplier(() -> {
			List<GLNodeGraph> result = new ArrayList<>(documents.stream()
					.map(document -> document.graph).toList());
			Collection<GLNodeGraph> additional = additionalDependentGraphs.get();
			if (additional != null) result.addAll(additional);
			return result;
		});
		interfaceEditor.setChangeListener(() -> {
			refreshRegisteredInterface();
			changeListener.run();
		});
		interfaceEditor.setCompositeNameChangeListener(this::renameCurrentComposite);
		back.addActionListener(event -> navigateBack());
		forward.addActionListener(event -> navigateForward());
		breadcrumb.setNavigationListener(this::navigateToDepth);
		editor.addNodeEditorListener(new GLNodeEditorListener() {
			@Override public void nodeAdded(GLNodeEditor source, GLNode node) {
				node.addActivationListener(() -> {
					source.setSelectedNode(node);
					openSelectedComposite();
				});
			}
			@Override public void connectionAdded(GLNodeEditor source,
					GLNodeConnection connection) {
				if (!rebuildingBoundaries && touchesBoundary(connection))
					scheduleBoundaryBindingSync();
			}
			@Override public void connectionRemoved(GLNodeEditor source,
					GLNodeConnection connection) {
				if (!rebuildingBoundaries && touchesBoundary(connection))
					scheduleBoundaryBindingSync();
			}
		});
		addGL(back); addGL(forward); addGL(breadcrumb); addGL(editor);
		addGL(inspectorTabs == null ? interfaceScroll : inspectorTabs);
		setIntrinsicPreferredGLSize(new GLSize(900, 600));
		history.addListener(historyListener);
		updateHeader();
	}

	public GLNodeEditor editor() { return editor; }
	public GLCompositeInterfaceEditor interfaceEditor() { return interfaceEditor; }
	public GLNodePropertyInspector propertyInspector() { return propertyInspector; }
	public GLNodeGraph currentGraph() {
		Document current = documents.peekLast();
		return current == null ? null : current.graph;
	}
	public String currentAssetId() {
		Document current = documents.peekLast();
		return current == null ? null : current.assetId;
	}
	public boolean canNavigateBack() { return documents.size() > 1; }
	public boolean canNavigateForward() { return !forwardDocuments.isEmpty(); }
	public GLNodePortOrder getCompositePortOrder() {
		return viewFactory.getPortOrder();
	}

	/** Changes composite-node row order and refreshes the current projection. */
	public void setCompositePortOrder(GLNodePortOrder order) {
		if (viewFactory.getPortOrder() == order) return;
		viewFactory.setPortOrder(order);
		if (binding != null) binding.refresh();
	}

	/**
	 * Sets the action invoked when the user requests Back while the workspace is
	 * already displaying its root document. Embedders can use this to close a
	 * composite page or return to a containing editor. A {@code null} action
	 * leaves Escape available to the surrounding UI.
	 */
	public void setRootNavigationAction(Runnable action) {
		rootNavigationAction = action;
	}

	public void setCompositeExtractionSupplier(
			Supplier<CompositeExtraction> supplier) {
		extractionSupplier = supplier;
	}

	/** Called after graph content or the active composite contract changes. */
	public void setChangeListener(Runnable listener) {
		changeListener = listener == null ? () -> { } : listener;
	}

	/** Adds application-owned open graphs to composite-port reconciliation. */
	public void setAdditionalDependentGraphsSupplier(
			Supplier<? extends Collection<GLNodeGraph>> supplier) {
		additionalDependentGraphs = supplier == null ? List::of : supplier;
	}

	public void setInspectorVisible(boolean visible) {
		if (inspectorVisible == visible) return;
		inspectorVisible = visible; layoutWorkspace();
	}
	public boolean isInspectorVisible() { return inspectorVisible; }
	public void setInspectorWidth(float width) {
		if (!Float.isFinite(width) || width < 180)
			throw new IllegalArgumentException("Inspector width must be at least 180");
		inspectorWidth = width; layoutWorkspace();
	}

	public void setCyclePolicy(GLGraphCyclePolicy policy) {
		cyclePolicy = Objects.requireNonNull(policy, "Cycle policy");
		if (binding != null) binding.setCyclePolicy(policy);
	}

	/** Opens a root project graph and clears composite navigation history. */
	public void openRoot(String title, GLNodeGraph graph,
			GLNodeEditorViewState viewState) {
		ensureOpen();
		if (graph == null || viewState == null)
			throw new IllegalArgumentException("Root graph and view state required");
		closeActiveDocument();
		documents.clear();
		forwardDocuments.clear();
		embeddedAssetIds.clear();
		sourceDocument = null;
		documents.addLast(new Document(null,
				title == null || title.isBlank() ? "Node graph" : title,
				graph, viewState));
		activateCurrent();
	}

	/**
	 * Opens a complete graph document. Its embedded composites must already be
	 * available in the registries supplied to this workspace.
	 */
	public void openDocument(String title, GLNodeGraphDocument document) {
		ensureOpen();
		if (document == null) throw new IllegalArgumentException("Graph document required");
		closeActiveDocument();
		documents.clear();
		forwardDocuments.clear();
		embeddedAssetIds.clear();
		embeddedAssetIds.addAll(document.embeddedAssets().keySet());
		sourceDocument = document;
		String rootAssetId = document.root().compositeDescriptor() == null
				? null : document.root().id();
		documents.addLast(new Document(rootAssetId,
				title == null || title.isBlank() ? "Node graph" : title,
				new GLNodeGraph(document.root().graph()), document.root().viewState()));
		activateCurrent();
	}

	/**
	 * Returns the current root graph, view state, and latest registered embedded
	 * assets as a persistable document value.
	 */
	public GLNodeGraphDocument snapshotDocument() {
		ensureOpen();
		if (sourceDocument == null)
			throw new IllegalStateException("The workspace was not opened with a document");
		Document rootDocument = documents.peekFirst();
		if (rootDocument == null) throw new IllegalStateException("No graph is open");
		if (documents.peekLast() == rootDocument && binding != null)
			rootDocument.viewState = captureActiveViewState();
		GLNodeGraphAsset template = rootDocument.assetId == null
				? sourceDocument.root()
				: composites.findAsset(rootDocument.assetId)
						.orElse(sourceDocument.root());
		GLNodeGraphAsset root = new GLNodeGraphAsset(template.id(), template.version(),
				template.kind(), rootDocument.graph.snapshot(), rootDocument.viewState,
				template.metadata(), template.compositeDescriptor());
		LinkedHashMap<String, GLNodeGraphAsset> embedded = new LinkedHashMap<>();
		for (String id : embeddedAssetIds)
			composites.findAsset(id).ifPresent(asset -> embedded.put(id, asset));
		return new GLNodeGraphDocument(root, embedded, sourceDocument.externalAssets());
	}

	/** Opens a registered composite asset in the existing editor area. */
	public boolean openComposite(String assetId) {
		ensureOpen();
		GLNodeGraphAsset asset = composites.findAsset(assetId).orElse(null);
		if (asset == null || asset.compositeDescriptor() == null) return false;
		if (documents.stream().anyMatch(document -> assetId.equals(document.assetId)))
			return false;
		closeActiveDocument();
		forwardDocuments.clear();
		documents.addLast(new Document(assetId,
				asset.compositeDescriptor().nodeTypeId(),
				new GLNodeGraph(asset.graph()), asset.viewState()));
		activateCurrent();
		return true;
	}

	public boolean openSelectedComposite() {
		GLGraphNode node = selectedModelNode();
		if (node == null) return false;
		return composites.findComposite(node.typeId())
				.map(GLCompositeNodeDescriptor::assetId)
				.map(this::openComposite).orElse(false);
	}

	public boolean navigateBack() {
		ensureOpen();
		if (documents.size() <= 1) return false;
		closeActiveDocument();
		forwardDocuments.addLast(documents.removeLast());
		activateCurrent();
		binding.ensureViewsFitContent();
		return true;
	}

	public boolean navigateForward() {
		ensureOpen();
		if (forwardDocuments.isEmpty()) return false;
		closeActiveDocument();
		documents.addLast(forwardDocuments.removeLast());
		activateCurrent();
		binding.ensureViewsFitContent();
		return true;
	}

	/** Navigates directly to a zero-based segment in the current breadcrumb. */
	public boolean navigateToDepth(int depth) {
		ensureOpen();
		if (depth < 0 || depth >= documents.size()) return false;
		if (depth == documents.size() - 1) return true;
		closeActiveDocument();
		while (documents.size() - 1 > depth)
			forwardDocuments.addLast(documents.removeLast());
		activateCurrent();
		binding.ensureViewsFitContent();
		return true;
	}

	@Override protected void resized(float width, float height) {
		layoutWorkspace();
	}

	@Override protected void record(GLContext2D context) {
		context.fillUIRoundRect(0, 0, getWidth(), getHeight(), 0,
				getGLTheme().values().getColor("palette.surface", SURFACE));
	}

	@Override public boolean capturesKeyPress(GLKeyEvent event) {
		return canNavigateBack() && getGLInputMap().matches(GLInputActions.CANCEL, event);
	}

	@Override public void keyPressed(GLKeyEvent event) {
		if (capturesKeyPress(event)) {
			navigateBack(); event.consume(); return;
		}
	}

	@Override public void close() {
		if (closed) return;
		closed = true;
		if (creationMenu != null) creationMenu.close();
		if (propertyInspector != null) propertyInspector.close();
		history.removeListener(historyListener);
		closeActiveDocument();
		documents.clear();
		forwardDocuments.clear();
	}

	private void activateCurrent() {
		Document document = documents.peekLast();
		if (document == null) { updateHeader(); return; }
		binding = new GLNodeEditorBinding(document.graph, types, editor, viewFactory,
				cyclePolicy, document.viewState);
		if (propertyInspector != null) propertyInspector.close();
		if (propertyScroll != null) {
			propertyInspector = new GLNodePropertyInspector(binding, propertySchemas);
			propertyScroll.setContent(propertyInspector);
		}
		editing = new GLNodeGraphEditingSession(binding, history);
		if (clipboard != null) editing.setClipboard(clipboard);
		if (!document.selection.isEmpty()) editor.setSelectedNodes(document.selection
				.stream().map(binding::findNodeView)
				.flatMap(java.util.Optional::stream).toList());
		graphListener = (source, change) -> {
			saveActiveAsset();
			changeListener.run();
		};
		document.graph.addListener(graphListener);
		configureEditingActions();
		refreshRegisteredInterface();
		updateHeader();
		editor.requestGLFocus(editor);
	}

	private void closeActiveDocument() {
		Document current = documents.peekLast();
		if (current != null && binding != null)
			current.viewState = captureActiveViewState();
		if (current != null && binding != null)
			current.selection = List.copyOf(selectedModelIds());
		saveActiveAsset();
		if (current != null && graphListener != null)
			current.graph.removeListener(graphListener);
		graphListener = null;
		if (propertyInspector != null) {
			propertyInspector.close();
			propertyInspector = null;
		}
		if (binding != null) binding.close();
		binding = null; editing = null; displayedDescriptor = null;
		for (GLNode node : List.copyOf(editor.getNodes())) editor.removeNode(node);
	}

	private void configureEditingActions() {
		editor.setEditingActions(new GLNodeEditingActions() {
			private boolean hasModelSelection() { return !selectedModelIds().isEmpty(); }
			@Override public boolean canCopy() { return hasModelSelection(); }
			@Override public boolean canCut() { return hasModelSelection(); }
			@Override public boolean canPaste() { return clipboard != null; }
			@Override public boolean canDuplicate() { return hasModelSelection(); }
			@Override public boolean canExtractComposite() {
				return hasModelSelection() && extractionSupplier != null;
			}
			@Override public boolean canDelete() { return hasModelSelection(); }
			@Override public void copy() {
				clipboard = editing.copySelection();
				if (clipboard != null) editing.setClipboard(clipboard);
			}
			@Override public void cut() {
				clipboard = editing.copySelection();
				if (clipboard != null) { editing.setClipboard(clipboard); editing.removeSelection(); }
			}
			@Override public void paste(float x, float y) {
				editing.setClipboard(clipboard); editing.paste(x, y);
			}
			@Override public void duplicate() { editing.duplicateSelection(28, 28); }
			@Override public GLNodePlacement beginPaste(float x, float y) {
				editing.setClipboard(clipboard); return editing.beginPaste(x, y);
			}
			@Override public GLNodePlacement beginDuplicate(float x, float y) {
				return editing.beginDuplicate(x, y);
			}
			@Override public GLNodePlacement beginMoveSelection(float x, float y) {
				return editing.beginMoveSelection(x, y);
			}
			@Override public void delete() { editing.removeSelection(); }
			@Override public void toggleHidden() { editing.toggleHiddenSelection(); }
			@Override public void toggleMuted() { editing.toggleMutedSelection(); }
			@Override public void requestNodeCreation(float x, float y,
					float screenX, float screenY) {
				showCreationMenu(x, y, screenX, screenY);
			}
			@Override public boolean canOpenSelectedComposite() {
				GLGraphNode node = selectedModelNode();
				return node != null && composites.findComposite(node.typeId()).isPresent();
			}
			@Override public void openSelectedComposite() {
				GLNodeWorkspace.this.openSelectedComposite();
			}
			@Override public void extractComposite() {
				CompositeExtraction request = extractionSupplier == null
						? null : extractionSupplier.get();
				if (request == null) return;
				var result = editing.extractSelection(request.assetId(), request.nodeTypeId(),
						request.displayName(), request.category(), composites, catalog);
				embeddedAssetIds.add(result.asset().id());
			}
		});
		editor.setNodeCreationRequestListener((source, x, y, screenX, screenY) ->
				showCreationMenu(x, y, screenX, screenY));
	}

	private void showCreationMenu(float x, float y, float screenX, float screenY) {
		GLParent host = rootParent();
		if (host == null) return;
		String excludedType = currentCompositeType();
		if (creationMenu != null && creationMenu.isShowing()) creationMenu.close();
		creationMenu = nodePalette.showPopup(host, screenX, screenY,
				entry -> !entry.type().id().equals(excludedType), null,
				entry -> insert(entry, x, y));
	}

	private void insert(GLNodeCatalogEntry entry, float x, float y) {
		if (binding == null || entry == null || entry.type().id().equals(currentCompositeType()))
			return;
		binding.addNode(entry.type().id(), entry.defaultProperties(), x, y);
		catalog.markUsed(entry.type().id());
	}

	private void refreshRegisteredInterface() {
		if (closed || binding == null) return;
		Document current = documents.peekLast();
		GLCompositeNodeDescriptor descriptor = current == null || current.assetId == null
				? null : composites.findAsset(current.assetId)
						.map(GLNodeGraphAsset::compositeDescriptor).orElse(null);
		boolean descriptorChanged = !Objects.equals(descriptor, displayedDescriptor);
		interfaceEditor.setAssetId(descriptor == null ? null : descriptor.assetId());
		interfaceEditor.setCompositeName(descriptor == null ? ""
				: catalog.find(descriptor.nodeTypeId())
						.map(GLNodeCatalogEntry::displayName).orElse(descriptor.nodeTypeId()));
		if (descriptorChanged && descriptor != null) {
			refreshCatalogEntry(descriptor);
			interfaceEditor.refresh();
			displayedDescriptor = descriptor;
			binding.refresh();
			rebuildBoundaryNodes(descriptor);
		}
		layoutWorkspace();
	}

	private void refreshCatalogEntry(GLCompositeNodeDescriptor descriptor) {
		catalog.find(descriptor.nodeTypeId()).ifPresent(previous -> catalog.replace(
				new GLNodeCatalogEntry(descriptor.nodeType(), previous.displayName(),
						previous.category(), previous.keywords(),
						previous.defaultProperties())));
	}

	private void renameCurrentComposite(String name) {
		String value = name == null ? "" : name.strip();
		if (value.isEmpty() || displayedDescriptor == null) return;
		GLNodeCatalogEntry before = catalog.find(displayedDescriptor.nodeTypeId())
				.orElse(null);
		if (before == null || before.displayName().equals(value)) return;
		GLNodeCatalogEntry after = new GLNodeCatalogEntry(before.type(), value,
				before.category(), before.keywords(), before.defaultProperties());
		Runnable undo = () -> applyCompositeCatalogEntry(before);
		Runnable redo = () -> applyCompositeCatalogEntry(after);
		redo.run();
		history.record("Rename composite", undo, redo);
	}

	private void applyCompositeCatalogEntry(GLNodeCatalogEntry entry) {
		catalog.replace(entry);
		interfaceEditor.setCompositeName(entry.displayName());
		changeListener.run();
	}

	private void rebuildBoundaryNodes(GLCompositeNodeDescriptor descriptor) {
		rebuildingBoundaries = true;
		try {
		BoundaryNode previousInput = null, previousOutput = null;
		for (GLNode node : List.copyOf(editor.getNodes())) {
			if (!(node instanceof BoundaryNode boundary)) continue;
			if (boundary.direction == GLPortDirection.INPUT) previousInput = boundary;
			else previousOutput = boundary;
		}
		GLNodeViewState storedInput = boundaryViewState(BOUNDARY_INPUT_VIEW);
		GLNodeViewState storedOutput = boundaryViewState(BOUNDARY_OUTPUT_VIEW);
		float inputX = previousInput != null ? previousInput.getX()
				: storedInput == null ? 24 : storedInput.x();
		float inputY = previousInput != null ? previousInput.getY()
				: storedInput == null ? 80 : storedInput.y();
		float outputX = previousOutput != null ? previousOutput.getX()
				: storedOutput == null ? Math.max(300, editor.getWidth() - 220)
						: storedOutput.x();
		float outputY = previousOutput != null ? previousOutput.getY()
				: storedOutput == null ? 80 : storedOutput.y();
		if (previousInput != null) editor.removeNode(previousInput);
		if (previousOutput != null) editor.removeNode(previousOutput);
		addBoundaryNode(descriptor, GLPortDirection.INPUT, inputX, inputY);
		addBoundaryNode(descriptor, GLPortDirection.OUTPUT, outputX, outputY);
		} finally { rebuildingBoundaries = false; }
	}

	private static boolean touchesBoundary(GLNodeConnection connection) {
		return connection != null && (connection.getOutput().getNode() instanceof BoundaryNode
				|| connection.getInput().getNode() instanceof BoundaryNode);
	}

	private void scheduleBoundaryBindingSync() {
		if (boundarySyncScheduled) return;
		boundarySyncScheduled = true;
		java.awt.EventQueue.invokeLater(() -> {
			boundarySyncScheduled = false;
			if (!closed && !rebuildingBoundaries) synchronizeBoundaryBindings();
		});
	}

	private void synchronizeBoundaryBindings() {
		if (binding == null) return;
		Document current = documents.peekLast();
		GLCompositeNodeDescriptor descriptor = current == null || current.assetId == null
				? null : composites.findComposite(currentCompositeType()).orElse(null);
		if (descriptor == null) return;
		List<GLCompositePortMapping> replacements = descriptor.portMappings().stream()
				.map(mapping -> new GLCompositePortMapping(mapping.publicPort(),
						visibleBoundaryEndpoint(mapping), mapping.presentation())).toList();
		if (replacements.equals(descriptor.portMappings())) return;
		try {
			GLCompositeInterfaceTransactions.replacePorts(composites, types,
					descriptor.assetId(), replacements, history,
					"Update composite port bindings");
			refreshRegisteredInterface();
			changeListener.run();
		} catch (RuntimeException failure) {
			rebuildBoundaryNodes(descriptor);
		}
	}

	private GLNodeEndpoint visibleBoundaryEndpoint(GLCompositePortMapping mapping) {
		for (GLNodeConnection connection : editor.getConnections()) {
			GLNodePort boundaryPort;
			GLNodePort internalPort;
			if (connection.getOutput().getNode() instanceof BoundaryNode boundary
					&& boundary.direction == mapping.publicPort().direction()) {
				boundaryPort = connection.getOutput(); internalPort = connection.getInput();
			} else if (connection.getInput().getNode() instanceof BoundaryNode boundary
					&& boundary.direction == mapping.publicPort().direction()) {
				boundaryPort = connection.getInput(); internalPort = connection.getOutput();
			} else continue;
			if (!boundaryPort.getId().equals(mapping.publicPort().id())) continue;
			GLNodeId node = binding.findNodeId(internalPort.getNode()).orElse(null);
			if (node != null) return new GLNodeEndpoint(node, internalPort.getId());
		}
		return null;
	}

	private void addBoundaryNode(GLCompositeNodeDescriptor descriptor,
			GLPortDirection direction, float x, float y) {
		BoundaryNode node = new BoundaryNode(direction);
		List<GLNodePort[]> connections = new ArrayList<>();
		for (GLCompositePortMapping mapping : descriptor.portMappings()) {
			if (mapping.publicPort().direction() != direction
					|| !mapping.presentation().socketVisible()) continue;
			GLNodePort boundaryPort = direction == GLPortDirection.INPUT
					? node.addOutput(mapping.publicPort().id(),
							new GLLabel(mapping.publicPort().id()))
					: node.addInput(mapping.publicPort().id(),
							new GLLabel(mapping.publicPort().id()));
			boundaryPort.setConnectionValidator((own, other) ->
					mapping.publicPort().valueTypeId().equals(valueTypeOf(other)));
			if (!mapping.isBound()) continue;
			GLNode internal = binding.findNodeView(mapping.internalEndpoint().nodeId())
					.orElse(null);
			GLNodePort internalPort = internal == null ? null
					: internal.findPort(mapping.internalEndpoint().portId());
			if (internalPort == null) continue;
			connections.add(direction == GLPortDirection.INPUT
					? new GLNodePort[] { boundaryPort, internalPort }
					: new GLNodePort[] { internalPort, boundaryPort });
		}
		if (node.getPorts().isEmpty()) return;
		editor.addNode(node, x, y);
		for (GLNodePort[] ports : connections) {
			GLNodeConnection connection = new GLNodeConnection(ports[0], ports[1]);
			if (editor.canConnect(ports[0], ports[1])) editor.addConnection(connection);
			else {
				editor.addProjectedConnection(connection);
				connection.setDiagnostic(GLNodeConnectionStatus.ERROR,
						"Public and internal port value types do not match");
			}
		}
	}

	private String valueTypeOf(GLNodePort port) {
		if (port == null || binding == null) return "";
		GLNodeId id = binding.findNodeId(port.getNode()).orElse(null);
		GLGraphNode node = id == null ? null
				: binding.graph().snapshot().findNode(id).orElse(null);
		return node == null ? "" : types.find(node.typeId())
				.flatMap(type -> type.findPort(port.getId()))
				.map(GLNodePortDescriptor::valueTypeId).orElse("");
	}

	private void saveActiveAsset() {
		if (synchronizingAsset || binding == null) return;
		Document current = documents.peekLast();
		if (current == null || current.assetId == null) return;
		GLNodeGraphAsset previous = composites.findAsset(current.assetId).orElse(null);
		if (previous == null) return;
		synchronizingAsset = true;
		try {
			current.viewState = captureActiveViewState();
			composites.replaceAsset(new GLNodeGraphAsset(previous.id(), previous.version(),
					previous.kind(), current.graph.snapshot(), current.viewState,
					previous.metadata(), previous.compositeDescriptor()));
		} finally { synchronizingAsset = false; }
	}

	private GLNodeEditorViewState captureActiveViewState() {
		GLNodeEditorViewState projected = binding.captureViewState();
		Document current = documents.peekLast();
		Map<String, GLNodeViewState> auxiliary = new LinkedHashMap<>(current == null
				? Map.of() : current.viewState.auxiliaryNodes());
		for (GLNode node : editor.getNodes()) {
			if (!(node instanceof BoundaryNode boundary)) continue;
			auxiliary.put(boundary.direction == GLPortDirection.INPUT
					? BOUNDARY_INPUT_VIEW : BOUNDARY_OUTPUT_VIEW,
					GLNodeViewState.capture(node));
		}
		return new GLNodeEditorViewState(projected.nodes(), auxiliary);
	}

	private GLNodeViewState boundaryViewState(String id) {
		Document current = documents.peekLast();
		return current == null ? null : current.viewState.auxiliaryNodes().get(id);
	}

	private List<GLNodeId> selectedModelIds() {
		if (binding == null) return List.of();
		return editor.getSelectedNodes().stream().map(binding::findNodeId)
				.flatMap(java.util.Optional::stream).toList();
	}

	private GLGraphNode selectedModelNode() {
		if (binding == null || editor.getActiveNode() == null) return null;
		GLNodeId id = binding.findNodeId(editor.getActiveNode()).orElse(null);
		return id == null ? null : binding.graph().snapshot().findNode(id).orElse(null);
	}

	private String currentCompositeType() {
		Document current = documents.peekLast();
		if (current == null || current.assetId == null) return "";
		return composites.findAsset(current.assetId)
				.map(GLNodeGraphAsset::compositeDescriptor)
				.map(GLCompositeNodeDescriptor::nodeTypeId).orElse("");
	}

	private void updateHeader() {
		back.setEnabled(canNavigateBack());
		forward.setEnabled(canNavigateForward());
		breadcrumb.setSegments(documents.isEmpty() ? List.of("Node graph")
				: documents.stream().map(document -> document.title).toList());
		layoutWorkspace();
	}

	private void layoutWorkspace() {
		float width = getWidth(), height = getHeight();
		boolean showInspector = inspectorVisible
				&& (currentAssetId() != null || !propertySchemas.isEmpty());
		float sidebar = showInspector ? Math.min(inspectorWidth,
				Math.max(180, width * .45f)) : 0;
		float editorWidth = Math.max(0, width - sidebar);
		back.setBounds(8, 6, 32, 28);
		forward.setBounds(44, 6, 32, 28);
		breadcrumb.setBounds(84, 6, Math.max(0, editorWidth - 92), 28);
		editor.setBounds(0, HEADER_HEIGHT, editorWidth,
				Math.max(0, height - HEADER_HEIGHT));
		GLContainer sidebarControl = inspectorTabs == null ? interfaceScroll : inspectorTabs;
		sidebarControl.setVisible(showInspector);
		if (showInspector) sidebarControl.setBounds(width - sidebar, HEADER_HEIGHT,
				sidebar, Math.max(0, height - HEADER_HEIGHT));
	}

	private GLParent rootParent() {
		GLParent current = getGLParent();
		while (current instanceof GLContainer container
				&& container.getGLParent() != null) current = container.getGLParent();
		return current;
	}

	private void ensureOpen() {
		if (closed) throw new IllegalStateException("Node workspace is closed");
	}

	private final class WorkspaceEditor extends GLNodeEditor {
		@Override public void keyPressed(GLKeyEvent event) {
			super.keyPressed(event);
			if (event.isConsumed()
					|| !getGLInputMap().matches(GLInputActions.CANCEL, event)) return;
			if (canNavigateBack()) navigateBack();
			else if (rootNavigationAction != null) rootNavigationAction.run();
			else return;
			event.consume();
		}
	}

	private static final class BoundaryNode extends GLNode {
		private final GLPortDirection direction;
		private BoundaryNode(GLPortDirection direction) {
			super(direction == GLPortDirection.INPUT ? "Group Input" : "Group Output");
			this.direction = direction;
			setTitleEditable(false);
			setResizeMode(GLNodeResizeMode.NONE);
		}
	}

	private static final class Document {
		private final String assetId;
		private final String title;
		private final GLNodeGraph graph;
		private GLNodeEditorViewState viewState;
		private List<GLNodeId> selection = List.of();
		private Document(String assetId, String title, GLNodeGraph graph,
				GLNodeEditorViewState viewState) {
			this.assetId = assetId; this.title = title;
			this.graph = graph; this.viewState = viewState;
		}
	}
}
