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
import com.vingame.glswing.node.composite.GLCompositeForwarding;
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
    private java.util.function.Predicate<GLNodeCatalogEntry> nodeCreationFilter=entry->true;
    @FunctionalInterface public interface NodeCreationHandler {
        /** Return true when an application-specific entry was handled. */
        boolean create(GLNodeCatalogEntry entry,float x,float y);
    }
    public void setNodeCatalogIconProvider(java.util.function.Function<GLNodeCatalogEntry,com.vingame.glswing.rendering.GLIcon> provider){nodePalette.setIconProvider(provider);}
    private NodeCreationHandler nodeCreationHandler=(entry,x,y)->false;
    public void setNodeCreationFilter(java.util.function.Predicate<GLNodeCatalogEntry> filter){
        nodeCreationFilter=filter==null?entry->true:filter;
        nodePalette.setAllowedPredicate(this::isNodeCreationAllowed);
    }
    public void setNodeCreationHandler(NodeCreationHandler handler){nodeCreationHandler=handler==null?(entry,x,y)->false:handler;}
    public boolean isNodeCreationAllowed(GLNodeCatalogEntry entry){
        return entry!=null&&!entry.type().id().equals(currentCompositeType())&&nodeCreationFilter.test(entry);
    }
    /** Creates a catalog entry at graph coordinates, using the same policy as the popup. */
    public void createNode(GLNodeCatalogEntry entry,float x,float y){insert(entry,x,y);}

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
    private GLScrollPane groupEditorsScroll;
    private final GLContainer generalTab=new SidebarHost(),ioTab=new SidebarHost();
    private static final class SidebarHost extends GLContainer{
        @Override protected void record(com.vingame.glswing.rendering.GLContext2D c){}
        @Override protected void resized(float w,float h){for(var child:getGLChildren())child.setBounds(0,0,w,h);}
    }
    public GLTabbedPane inspectorTabs(){return inspectorTabs;}
    /** A single application sidebar; editor definitions stay application-owned. */
    public void setGroupEditors(GLContainer content){
        if(inspectorTabs==null)throw new IllegalStateException("Property schemas required");
        while(inspectorTabs.getTabCount()>1)inspectorTabs.removeTabAt(inspectorTabs.getTabCount()-1);
        groupEditorsScroll=new GLScrollPane(content);groupEditorsScroll.setContentTracksViewportWidth(true);
        groupEditorsScroll.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
        interfaceEditor.setSectionTabsVisible(false);inspectorTabs.setTabPlacement(GLTabbedPane.Placement.LEFT);
        inspectorTabs.addSelectionListener((pane,before,after)->{
            if(groupEditorsScroll==null||after<2)return;
            if(interfaceScroll.getGLParent() instanceof GLContainer parent)parent.removeGL(interfaceScroll);
            var host=after==2?generalTab:ioTab;host.addGL(interfaceScroll);interfaceScroll.setBounds(0,0,host.getWidth(),host.getHeight());interfaceEditor.selectSection(after==2?0:1);
        });
        updateHeader();
    }
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
			inspectorTabs.setClosedTabHistoryEnabled(false);
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
				if (!rebuildingBoundaries && (binding==null||!binding.isSynchronizing()) && touchesBoundary(connection))
					scheduleBoundaryBindingSync();
			}
			@Override public void connectionGestureFinished(GLNodeEditor source){
                if(boundarySyncScheduled){boundarySyncScheduled=false;scheduleBoundaryBindingSync();}
            }
			@Override public void connectionRemoved(GLNodeEditor source,
					GLNodeConnection connection) {
				if (!rebuildingBoundaries && (binding==null||!binding.isSynchronizing()) && touchesBoundary(connection))
					scheduleBoundaryBindingSync();
			}
		});
		addGL(back); addGL(forward); addGL(breadcrumb); addGL(editor);
		addGL(inspectorTabs == null ? interfaceScroll : inspectorTabs);
		setIntrinsicPreferredGLSize(new GLSize(900, 600));
		history.addListener(historyListener);
		updateHeader();
	}

    /** Application-owned composite rendering; model editing and navigation remain in the workspace. */
    public void setAdditionalGraphDiagnostics(java.util.List<com.vingame.glswing.node.model.GLGraphDiagnostic> diagnostics,java.util.Set<com.vingame.glswing.node.model.GLNodeConnectionId> inactive){if(binding!=null)binding.setAdditionalDiagnostics(diagnostics,inactive);}
    /** Set application diagnostics for the currently displayed graph. */
    public void setAdditionalGraphDiagnostics(java.util.List<com.vingame.glswing.node.model.GLGraphDiagnostic> diagnostics){if(binding!=null)binding.setAdditionalDiagnostics(diagnostics);}
    public void refreshNodeViews(){if(binding!=null)binding.refresh();}
    public void setCompositeViewFactory(GLNodeViewFactory factory){viewFactory.setApplicationFactory(factory);if(binding!=null)binding.refresh();}
	public GLNodeEditor editor() { return editor; }
	public GLCompositeInterfaceEditor interfaceEditor() { return interfaceEditor; }
    /** Stops persisting an unused asset and drops stale forward navigation to it.
     * Applications own reference checks and registry/catalog removal. */
    public void excludeEmbeddedAsset(String id){
        ensureOpen();
        if(openAssetIds().contains(id))throw new IllegalArgumentException("Cannot remove an open asset");
        embeddedAssetIds.remove(id);
        if(forwardDocuments.stream().anyMatch(d->id.equals(d.assetId)))forwardDocuments.clear();
        updateHeader();
    }
    /** Include an already registered application-created composite in document snapshots. */
    public void includeEmbeddedAsset(String id){ensureOpen();if(composites.findAsset(id).isEmpty())throw new IllegalArgumentException("Unknown embedded asset: "+id);embeddedAssetIds.add(id);}

	public GLNodePropertyInspector propertyInspector() { return propertyInspector; }
	public GLNodeGraph currentGraph() {
		Document current = documents.peekLast();
		return current == null ? null : current.graph;
	}
	public String currentAssetId() {
		Document current = documents.peekLast();
		return current == null ? null : current.assetId;
	}
    /** Asset path from the outermost group to the current group; root graphs have no asset ID. */
    public List<String> openAssetIds(){return documents.stream().map(d->d.assetId).filter(Objects::nonNull).toList();}
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
        historyDocuments.clear();
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
        historyDocuments.clear();
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
		saveActiveAsset();
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
		Document opened=graphHistoryEnabled?historyDocuments.get(assetId):null;
        if(opened==null){opened=new Document(assetId,asset.compositeDescriptor().nodeTypeId(),new GLNodeGraph(asset.graph()),asset.viewState());if(graphHistoryEnabled)historyDocuments.put(assetId,opened);}
        else {var retained=opened;history.withoutRecording(()->retained.graph.replace("Refresh group",asset.graph()));opened.viewState=asset.viewState();}
        documents.addLast(opened);
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
				getGLTheme().values().getColor("nodeWorkspace.background"));
	}

    private boolean sidebarFocused(){
        var target=getGLFocusOwner();
        for(GLParent parent=target instanceof GLContainer container?container:null;parent instanceof GLContainer container;parent=container.getGLParent())
            if(parent==inspectorTabs || parent==interfaceScroll)return inspectorVisible;
        return false;
    }
	@Override public boolean capturesKeyPress(GLKeyEvent event) {
		return getGLInputMap().matches(GLInputActions.CANCEL,event) && (sidebarFocused() || canNavigateBack());
	}

	@Override public void keyPressed(GLKeyEvent event) {
        if (!event.isConsumed() && getGLInputMap().matches(GLInputActions.NODE_ARRANGE, event)) {
            editor.keyPressed(event); return;
        }
		if (capturesKeyPress(event)) {
            if(sidebarFocused()){setInspectorVisible(false);editor.requestGLFocus(editor);}else navigateBack(); event.consume(); return;
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
        historyDocuments.clear();
		forwardDocuments.clear();
	}

	private boolean graphHistoryEnabled;
    private final java.util.Map<String,Document> historyDocuments=new java.util.HashMap<>();
    /** Use independent stacks per graph definition; modified state covers all graphs. */
    public void setGraphHistoryEnabled(boolean enabled){graphHistoryEnabled=enabled;if(enabled)selectHistoryScope();}
    private void selectHistoryScope(){var current=documents.peekLast();if(current!=null)history.setActiveScope(current.assetId==null?"workspace.root":current.assetId);}
    private boolean restoreCameraPending;
    private void activateCurrent() {
		Document document = documents.peekLast();
		if (document == null) { updateHeader(); return; }
        restoreCameraPending=true;
		binding = new GLNodeEditorBinding(document.graph, types, editor, viewFactory,
				cyclePolicy, document.viewState);
        binding.addProjectionListener(this::restoreProjectedBoundaryConnections);
		if (propertyInspector != null) propertyInspector.close();
		if (propertyScroll != null) {
			propertyInspector = new GLNodePropertyInspector(binding, propertySchemas);
			propertyScroll.setContent(propertyInspector);
		}
		editing = new GLNodeGraphEditingSession(binding, history,
				state -> {
					var currentView=captureActiveViewState();document.viewState = new GLNodeEditorViewState(state.nodes(),currentView.auxiliaryNodes(),currentView.camera());
					persistDocumentAsset(document);
				});
		if (clipboard != null) editing.setClipboard(clipboard);
        installBoundaryEditing();
		if (!document.selection.isEmpty()) editor.setSelectedNodes(document.selection
				.stream().map(binding::findNodeView)
				.flatMap(java.util.Optional::stream).toList());
		graphListener = (source, change) -> {
			saveActiveAsset();
			changeListener.run();
		};
		document.graph.addListener(graphListener);
		configureEditingActions();
        if(graphHistoryEnabled)selectHistoryScope();
		refreshRegisteredInterface();
		updateHeader();
		refreshCompositeEditor();
        if(compositeEditor==null)editor.requestGLFocus(editor);
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
			@Override public boolean canExclude() { return editing.canExcludeSelection(); }
			@Override public void exclude() { editing.excludeSelection(); } @Override public void arrangeAll() { GLNodeWorkspace.this.arrangeAll(); }
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
			@Override public void duplicate() {
				float[] point=editor.pointerGraphPosition();
				editor.beginPlacement(editing.beginDuplicate(point[0],point[1]));
			}
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

    /** Expand a configured instance as a single undo step; its definition stays registered. */
    public void ungroup(GLGraphNode instance,GLNodeGraphAsset configured) {
        ungroup(instance,configured,(parent,result)->parent);
    }
    /** The adapter can remap application-owned interface metadata before committing. */
    public void ungroup(GLGraphNode instance,GLNodeGraphAsset configured,
            java.util.function.BiFunction<GLNodeGraphAsset,com.vingame.glswing.node.composite.GLCompositeUngroup.Result,GLNodeGraphAsset> adapter) {
        if(binding==null)return;
        var document=documents.peekLast();var before=document.graph.snapshot();var beforeView=captureActiveViewState();
        var parent=document.assetId==null?null:composites.findAsset(document.assetId).orElseThrow();
        var result=com.vingame.glswing.node.composite.GLCompositeUngroup.expand(before,beforeView,instance,configured);
        var afterParent=parent==null?null:adapter.apply(new GLNodeGraphAsset(parent.id(),parent.version(),parent.kind(),result.graph(),result.viewState(),parent.metadata(),
            result.remap(parent.compositeDescriptor(),instance.id(),configured.compositeDescriptor())),result);
        var selection=editor.getSelectedNodes().stream().flatMap(n->binding.findNodeId(n).stream()).toList();
        var afterSelection=List.copyOf(result.nodeIds().values());
        try { restoreUngroup(document,result.graph(),result.viewState(),afterParent,afterSelection); }
        catch(RuntimeException failure){restoreUngroup(document,before,beforeView,parent,selection);throw failure;}
        if(history!=null)history.record("Ungroup",()->restoreUngroup(document,before,beforeView,parent,selection),
            ()->restoreUngroup(document,result.graph(),result.viewState(),afterParent,afterSelection));
    }
    private void restoreUngroup(Document document,com.vingame.glswing.node.model.GLNodeGraphSnapshot graph,
            GLNodeEditorViewState view,GLNodeGraphAsset parent,List<GLNodeId> selection) {
        synchronizingAsset=true;
        try {
            document.viewState=view;document.selection=selection;
            document.graph.replace("Ungroup",graph);
            if(parent!=null)composites.replaceAsset(parent);
            if(documents.peekLast()==document&&binding!=null){
                binding.refresh();binding.applyViewState(view);refreshRegisteredInterface();
                editor.setSelectedNodes(selection.stream().map(binding::findNodeView).flatMap(java.util.Optional::stream).toList());
            }
        } finally {synchronizingAsset=false;}
        changeListener.run();
    }

	/** Arrange the current graph, including boundary projections, as one view-only undo step. */
    public void arrangeAll() {
        if(binding==null||editor.getNodes().isEmpty())return;
        var document=documents.peekLast();var before=captureActiveViewState();
        GLNodeAutoLayout.arrange(editor);var after=captureActiveViewState();
        if(before.equals(after))return;
        document.viewState=after;persistDocumentAsset(document);
        if(history!=null)history.record("Arrange all nodes",()->restoreArrangement(document,before),()->restoreArrangement(document,after));
    }
    private void restoreArrangement(Document document,GLNodeEditorViewState state) {
        document.viewState=state;
        if(documents.peekLast()==document&&binding!=null){
            binding.applyViewState(state);
            for(var n:editor.getNodes())if(n instanceof BoundaryNode boundary){
                var b=state.auxiliaryNodes().get(boundary.direction==GLPortDirection.INPUT?BOUNDARY_INPUT_VIEW:BOUNDARY_OUTPUT_VIEW);
                if(b!=null)n.setBounds(b.x(),b.y(),b.width(),b.height());
            }
        }
        persistDocumentAsset(document);
    }
    private void showCreationMenu(float x, float y, float screenX, float screenY) {
		GLParent host = rootParent();
		if (host == null) return;
		if (creationMenu != null && creationMenu.isShowing()) creationMenu.close();
		creationMenu = nodePalette.showPopup(host, screenX, screenY,
				this::isNodeCreationAllowed, null,
				entry -> insert(entry, x, y));
	}

	private void insert(GLNodeCatalogEntry entry, float x, float y) {
		if (binding == null || !isNodeCreationAllowed(entry))
			return;
        if(nodeCreationHandler.create(entry,x,y))return;
		binding.addNode(entry.type().id(), entry.defaultProperties(), x, y);
		catalog.markUsed(entry.type().id());
	}

	private void refreshRegisteredInterface() {
		if (closed || binding == null || rebuildingBoundaries) return;
		Document current = documents.peekLast();
		GLCompositeNodeDescriptor descriptor = current == null || current.assetId == null
				? null : composites.findAsset(current.assetId)
						.map(GLNodeGraphAsset::compositeDescriptor).orElse(null);
        binding.setSuppliedInputs(descriptor==null?java.util.Set.of():descriptor.portMappings().stream()
            .filter(p->p.publicPort().direction()==GLPortDirection.INPUT)
            .flatMap(p->p.internalEndpoints().stream()).collect(java.util.stream.Collectors.toSet()));
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
        var inner=editor.getNodes().stream().filter(n->!(n instanceof BoundaryNode)).toList();
        float minX=(float)inner.stream().mapToDouble(GLNode::getX).min().orElse(300);
        float maxX=(float)inner.stream().mapToDouble(n->n.getX()+n.getWidth()).max().orElse(600);
        float top=(float)inner.stream().mapToDouble(GLNode::getY).min().orElse(80);
        float width=editor.getGLTheme().values().getFloat("node.minimumWidth",190),gap=editor.getHorizontalNodeSpacing();
		float inputX = previousInput != null ? previousInput.getX()
				: storedInput == null ? minX-width-gap : storedInput.x();
		float inputY = previousInput != null ? previousInput.getY()
				: storedInput == null ? top : storedInput.y();
		float outputX = previousOutput != null ? previousOutput.getX()
				: storedOutput == null ? maxX+gap
						: storedOutput.x();
		float outputY = previousOutput != null ? previousOutput.getY()
				: storedOutput == null ? top : storedOutput.y();
		if (previousInput != null) editor.removeNode(previousInput);
		if (previousOutput != null) editor.removeNode(previousOutput);
		var inputBoundary = addBoundaryNode(descriptor, GLPortDirection.INPUT, inputX, inputY);
        if (previousInput == null && storedInput == null)
            inputBoundary.setLocation(minX - inputBoundary.getWidth() - gap, inputY);
		addBoundaryNode(descriptor, GLPortDirection.OUTPUT, outputX, outputY);
		} finally { rebuildingBoundaries = false; }
	}

    private java.util.function.BiFunction<GLCompositePortMapping,GLNodeEndpoint,GLCompositePortMapping> boundaryPortFactory=(p,e)->p;
    public void setBoundaryPortFactory(java.util.function.BiFunction<GLCompositePortMapping,GLNodeEndpoint,GLCompositePortMapping> factory){boundaryPortFactory=Objects.requireNonNull(factory);}
    private void installBoundaryEditing(){
        var ordinary=editor.getConnectionInsertionHandler();
        editor.setConnectionInsertionHandler(new GLNodeEditor.ConnectionInsertionHandler(){
            public boolean canInsert(GLNode node,GLNodeConnection edge){return touchesBoundary(edge)?canInsertBoundary(node,edge):ordinary.canInsert(node,edge);}
            public boolean insert(GLNode node,GLNodeConnection edge,float x,float y){return touchesBoundary(edge)?insertBoundary(node,edge,x,y):ordinary.insert(node,edge,x,y);}
        });
        editor.setConnectionBodyDropHandler(new GLNodeEditor.ConnectionBodyDropHandler(){
            BoundaryNode preview;GLLabel label;GLNodePort ghost;float originalHeight;
            public GLNodePort previewPort(){return ghost;}
            public void clear(){if(preview!=null){preview.removeContent(label);preview.setSize(preview.getWidth(),originalHeight);preview=null;label=null;ghost=null;}}
            public void preview(GLNodePort fixed,float x,float y){
                var target=boundaryAt(fixed,x,y);if(target==preview)return;clear();if(target==null)return;
                preview=target;originalHeight=target.getHeight();label=new GLLabel("+ "+fixed.getId());
                String ghostId="__new_port__";while(target.findPort(ghostId)!=null)ghostId+="_";
                var port=target.direction==GLPortDirection.INPUT?target.addOutput(ghostId,label):target.addInput(ghostId,label);
                ghost=port;port.setConnectionCandidate(true);port.setColor(fixed.getColor());port.setShape(fixed.getShape());port.setConnectionValidator((a,b)->false);
                target.setSize(target.getWidth(),Math.max(originalHeight,target.getPreferredGLSize().height()));
            }
            public boolean drop(GLNodePort fixed,float x,float y){var target=boundaryAt(fixed,x,y);clear();return target!=null&&createBoundaryPort(fixed,target);}
        });
    }
    private BoundaryNode boundaryAt(GLNodePort fixed,float x,float y){
        if(fixed==null||fixed.getNode() instanceof BoundaryNode)return null;
        var direction=fixed.getDirection()==GLNodePort.Direction.INPUT?GLPortDirection.INPUT:GLPortDirection.OUTPUT;
        float margin=editor.getConnectionSnapDistance()/editor.getZoom();
        for(var n:editor.getNodes())if(n instanceof BoundaryNode b&&b.direction==direction&&x>=n.getX()-margin&&x<=n.getX()+n.getWidth()+margin&&y>=n.getY()-margin&&y<=n.getY()+n.getHeight()+margin)return b;
        return null;
    }
    private boolean createBoundaryPort(GLNodePort fixed,BoundaryNode boundary){
        var document=documents.peekLast();if(document==null||document.assetId==null)return false;
        var descriptor=composites.findAsset(document.assetId).orElseThrow().compositeDescriptor();
        var id=binding.findNodeId(fixed.getNode()).orElse(null);if(id==null)return false;
        String base=fixed.getId(),name=base;int suffix=2;
        var names=descriptor.portMappings().stream().map(p->p.publicPort().id()).collect(java.util.stream.Collectors.toSet());
        while(names.contains(name))name=base+" "+suffix++;
        var port=boundary.direction==GLPortDirection.INPUT?GLNodePortDescriptor.input(name,valueTypeOf(fixed),false,1):GLNodePortDescriptor.output(name,valueTypeOf(fixed));
        var endpoint=new GLNodeEndpoint(id,fixed.getId());
        var mapping=boundaryPortFactory.apply(new GLCompositePortMapping(port,endpoint),endpoint);
        GLCompositeInterfaceTransactions.addPort(composites,types,document.assetId,mapping,history,"Create group port from connection");
        refreshRegisteredInterface();changeListener.run();return true;
    }
    private GLCompositePortMapping boundaryMapping(GLNodePort port){
        if(!(port.getNode() instanceof BoundaryNode)||documents.peekLast()==null)return null;
        var asset=composites.findAsset(documents.peekLast().assetId).orElse(null);
        return asset==null||asset.compositeDescriptor()==null?null:asset.compositeDescriptor().portMappings().stream().filter(p->p.publicPort().id().equals(port.getId())).findFirst().orElse(null);
    }
    private boolean canInsertBoundary(GLNode node,GLNodeConnection edge){
        if(binding==null||binding.findNodeId(node).isEmpty()||editor.getConnections().stream().anyMatch(c->c.getInput().getNode()==node||c.getOutput().getNode()==node))return false;
        var model=binding.graph().snapshot().findNode(binding.findNodeId(node).orElseThrow()).orElseThrow();
        var ports=types.insertionPorts(model.typeId()).orElse(null);if(ports==null)return false;
        var in=node.findPort(ports.inputPortId());var out=node.findPort(ports.outputPortId());
        return in!=null&&out!=null&&types.compatibleValueTypes(valueTypeOf(edge.getOutput()),valueTypeOf(in))&&types.compatibleValueTypes(valueTypeOf(out),valueTypeOf(edge.getInput()));
    }
    private boolean insertBoundary(GLNode node,GLNodeConnection edge,float startX,float startY){
        if(!canInsertBoundary(node,edge))return false;
        rebuildingBoundaries=true;
        try { return insertBoundaryModel(node,edge,startX,startY); }
        finally { rebuildingBoundaries=false; refreshRegisteredInterface();saveActiveAsset();changeListener.run(); }
    }
    private boolean insertBoundaryModel(GLNode node,GLNodeConnection edge,float startX,float startY){
        var document=documents.peekLast();var graph=binding.graph();var before=graph.snapshot();var current=captureActiveViewState();
        var id=binding.findNodeId(node).orElseThrow();var pair=types.insertionPorts(before.findNode(id).orElseThrow().typeId()).orElseThrow();
        var boundary=edge.getOutput().getNode() instanceof BoundaryNode?edge.getOutput():edge.getInput();
        var mapping=boundaryMapping(boundary);if(mapping==null)return false;
        var internal=boundary==edge.getOutput()?edge.getInput():edge.getOutput();
        if(internal.getNode() instanceof BoundaryNode){
            var inputMapping=boundaryMapping(edge.getOutput());var outputMapping=boundaryMapping(edge.getInput());
            var targets=new ArrayList<>(inputMapping.internalEndpoints());targets.add(new GLNodeEndpoint(id,pair.inputPortId()));
            var d=composites.findAsset(document.assetId).orElseThrow().compositeDescriptor();
            GLCompositeInterfaceTransactions.replacePorts(composites,types,document.assetId,d.portMappings().stream().map(p->p.publicPort().id().equals(inputMapping.publicPort().id())? p.withEndpoints(targets):p.publicPort().id().equals(outputMapping.publicPort().id())?p.withEndpoints(List.of(new GLNodeEndpoint(id,pair.outputPortId()))):p).toList(),history,"Insert node into group connection");
            return true;
        }
        var oldEndpoint=new GLNodeEndpoint(binding.findNodeId(internal.getNode()).orElseThrow(),internal.getId());
        var endpoint=new GLNodeEndpoint(id,boundary==edge.getOutput()?pair.inputPortId():pair.outputPortId());
        var replacement=mapping.withEndpoints(mapping.internalEndpoints().stream().map(e->e.equals(oldEndpoint)?endpoint:e).toList());
        var downstream=new LinkedHashSet<GLNode>();var pending=new java.util.ArrayDeque<GLNode>();pending.add(edge.getInput().getNode());
        while(!pending.isEmpty()){
            var next=pending.removeFirst();if(next==node||next==edge.getOutput().getNode()||!downstream.add(next))continue;
            for(var wire:editor.getConnections())if(wire.getOutput().getNode()==next)pending.add(wire.getInput().getNode());
        }
        var beforeBounds=new LinkedHashMap<>(current.nodes());var bounds=beforeBounds.get(id);
        beforeBounds.put(id,new GLNodeViewState(startX,startY,bounds.width(),bounds.height(),bounds.customTitle()));
        var start=current.withNodes(beforeBounds);
        if(!start.equals(current))history.record("Move node",()->restoreArrangement(document,start),()->restoreArrangement(document,current));
        try(var transaction=history.beginTransaction("Insert node into group connection")){
            graph.edit("Insert node",e->e.addConnection(new com.vingame.glswing.node.model.GLGraphConnection(com.vingame.glswing.node.model.GLNodeConnectionId.random(),boundary==edge.getOutput()?new GLNodeEndpoint(id,pair.outputPortId()):oldEndpoint,boundary==edge.getOutput()?oldEndpoint:new GLNodeEndpoint(id,pair.inputPortId()))));
            var after=graph.snapshot();history.record("Connect inserted node",()->graph.replace("Undo insertion",before),()->graph.replace("Redo insertion",after));
            var descriptor=composites.findAsset(document.assetId).orElseThrow().compositeDescriptor();
            GLCompositeInterfaceTransactions.replacePorts(composites,types,document.assetId,descriptor.portMappings().stream().map(p->p.publicPort().id().equals(mapping.publicPort().id())?replacement:p).toList(),history,"Reconnect group boundary");
            float gap=editor.getHorizontalNodeSpacing();var from=edge.getOutput().getNode();var to=edge.getInput().getNode();
            float x=Math.max(from.getX()+from.getWidth()+gap,node.getX());node.setLocation(x,node.getY());
            float shift=Math.max(0,x+node.getWidth()+gap-to.getX());
            if(shift>0){float threshold=to.getX();for(var n:downstream)if(n.getX()>=threshold)n.setLocation(n.getX()+shift,n.getY());}
            var placed=captureActiveViewState();history.record("Make room",()->restoreArrangement(document,current),()->restoreArrangement(document,placed));
        }
        return true;
    }
	private static boolean touchesBoundary(GLNodeConnection connection) {
		return connection != null && (connection.getOutput().getNode() instanceof BoundaryNode
				|| connection.getInput().getNode() instanceof BoundaryNode);
	}

    private void restoreProjectedBoundaryConnections() {
        if(closed||rebuildingBoundaries||boundarySyncScheduled||editor.isConnectionGestureActive()||binding==null||currentAssetId()==null)return;
        var descriptor=composites.findAsset(currentAssetId()).map(GLNodeGraphAsset::compositeDescriptor).orElse(null);
        boolean deletedTarget=descriptor!=null&&descriptor.portMappings().stream().flatMap(p->p.internalEndpoints().stream())
            .anyMatch(e->{
                if(GLCompositeForwarding.isInput(e))return false;
                var node=binding.graph().snapshot().findNode(e.nodeId()).orElse(null);
                return node==null||types.find(node.typeId()).map(t->t.findPort(e.portId()).isEmpty()).orElse(false);
            });
        if(descriptor!=null&&descriptor.portMappings().stream().filter(p->!p.presentation().applicationManaged())
            .anyMatch(p->!new java.util.HashSet<>(p.internalEndpoints()).equals(new java.util.HashSet<>(visibleBoundaryEndpoints(p)))))
            rebuildBoundaryNodes(descriptor);
        if(deletedTarget)scheduleBoundaryBindingSync();
    }

	private void scheduleBoundaryBindingSync() {
		if (boundarySyncScheduled) return;
		boundarySyncScheduled = true;
		java.awt.EventQueue.invokeLater(() -> {
			if(editor.isConnectionGestureActive())return;
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
				.map(mapping -> interfaceEditor.transformPortPresentation(mapping.withEndpoints(visibleBoundaryEndpoints(mapping)))).toList();
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

	private List<GLNodeEndpoint> visibleBoundaryEndpoints(GLCompositePortMapping mapping) {
        List<GLNodeEndpoint> result=new ArrayList<>();
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
			if(internalPort.getNode() instanceof BoundaryNode other){
                if(mapping.publicPort().direction()==GLPortDirection.OUTPUT&&other.direction==GLPortDirection.INPUT)result.add(GLCompositeForwarding.input(internalPort.getId()));
                continue;
            }
            GLNodeId node = binding.findNodeId(internalPort.getNode()).orElse(null);
			if (node != null) result.add(new GLNodeEndpoint(node, internalPort.getId()));
		}
		return result.stream().distinct().toList();
	}

	private BoundaryNode addBoundaryNode(GLCompositeNodeDescriptor descriptor,
			GLPortDirection direction, float x, float y) {
		BoundaryNode node = new BoundaryNode(direction);
		List<GLNodePort[]> connections = new ArrayList<>();
		for (GLCompositePortMapping mapping : descriptor.portMappings()) {
			if (mapping.publicPort().direction() != direction || mapping.presentation().applicationManaged()) continue;
			GLNodePort boundaryPort = direction == GLPortDirection.INPUT
					? node.addOutput(mapping.publicPort().id(),
							new GLLabel(mapping.publicPort().id()))
					: node.addInput(mapping.publicPort().id(),
							new GLLabel(mapping.publicPort().id()));
			boundaryPort.setConnectionValidator((own, other) ->
					types.compatibleValueTypes(mapping.publicPort().valueTypeId(),valueTypeOf(other)));
			if (!mapping.isBound()) continue;
			for (var endpoint : mapping.internalEndpoints()) {
            GLNode internal = GLCompositeForwarding.isInput(endpoint)
                    ? editor.getNodes().stream().filter(n->n instanceof BoundaryNode b&&b.direction==GLPortDirection.INPUT).findFirst().orElse(null)
                    : binding.findNodeView(endpoint.nodeId()).orElse(null);
			GLNodePort internalPort = internal == null ? null
					: internal.findPort(endpoint.portId());
			if (internalPort == null) continue;
			connections.add(direction == GLPortDirection.INPUT
					? new GLNodePort[] { boundaryPort, internalPort }
					: new GLNodePort[] { internalPort, boundaryPort });
            }
		}
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
        return node;
	}

	private String valueTypeOf(GLNodePort port) {
		if (port == null || binding == null) return "";
        var mapping=boundaryMapping(port);if(mapping!=null)return mapping.publicPort().valueTypeId();
		GLNodeId id = binding.findNodeId(port.getNode()).orElse(null);
		GLGraphNode node = id == null ? null
				: binding.graph().snapshot().findNode(id).orElse(null);
		return node == null ? "" : types.find(node.typeId())
				.flatMap(type -> type.findPort(port.getId()))
				.map(GLNodePortDescriptor::valueTypeId).orElse("");
	}

	private void saveActiveAsset() {
		if (synchronizingAsset || binding == null) return;
        if(boundarySyncScheduled&&!rebuildingBoundaries&&!editor.isConnectionGestureActive()){boundarySyncScheduled=false;synchronizeBoundaryBindings();}
		Document current = documents.peekLast();
		if (current == null) return;
		current.viewState = captureActiveViewState();
		persistDocumentAsset(current);
	}

	private void persistDocumentAsset(Document document) {
		if (synchronizingAsset || document == null || document.assetId == null) return;
		GLNodeGraphAsset previous = composites.findAsset(document.assetId).orElse(null);
		if (previous == null) return;
		synchronizingAsset = true;
		try {
			composites.replaceAsset(new GLNodeGraphAsset(previous.id(), previous.version(),
					previous.kind(), document.graph.snapshot(), document.viewState,
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
		return new GLNodeEditorViewState(projected.nodes(), auxiliary,restoreCameraPending&&current!=null?current.viewState.camera():projected.camera());
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
        boolean group = currentAssetId() != null;
        back.setVisible(group); forward.setVisible(group); breadcrumb.setVisible(group);
        if(inspectorTabs != null){
            if(group && inspectorTabs.getTabCount()==1){
                if(groupEditorsScroll!=null){inspectorTabs.addTab("Editors",groupEditorsScroll);inspectorTabs.addTab("Group-General",generalTab);inspectorTabs.addTab("Group-IO",ioTab);}
                else inspectorTabs.addTab("Group Settings",interfaceScroll);
            }else if(!group && inspectorTabs.getTabCount()>1){
                inspectorTabs.setSelectedIndex(0);
                while(inspectorTabs.getTabCount()>1)inspectorTabs.removeTabAt(inspectorTabs.getTabCount()-1);
            }
        }
		back.setEnabled(canNavigateBack());
		forward.setEnabled(canNavigateForward());
		breadcrumb.setSegments(documents.isEmpty() ? List.of("Node graph")
				: documents.stream().map(document -> document.title).toList());
		layoutWorkspace();
	}

	private java.util.function.Function<GLNodeGraphAsset,GLContainer> compositeEditorFactory;
    private GLContainer compositeEditor;
    /** Alternative editing surface for application-owned composite languages. Inspector/navigation remain shared. */
    public void setCompositeEditorFactory(java.util.function.Function<GLNodeGraphAsset,GLContainer> factory) {
        compositeEditorFactory=factory; refreshCompositeEditor();
    }
    public void refreshCompositeEditor() {
        GLNodeGraphAsset asset=currentAssetId()==null?null:composites.findAsset(currentAssetId()).orElse(null);
        GLContainer next=asset==null||compositeEditorFactory==null?null:compositeEditorFactory.apply(asset);
        if(next!=compositeEditor){if(compositeEditor!=null)removeGL(compositeEditor);compositeEditor=next;if(next!=null)addGL(next);}
        editor.setVisible(compositeEditor==null);layoutWorkspace();
    }
    private void layoutWorkspace() {
		float width = getWidth(), height = getHeight();
        float header = currentAssetId() == null ? 0 : HEADER_HEIGHT;
		boolean showInspector = inspectorVisible
				&& (currentAssetId() != null || !propertySchemas.isEmpty());
		float sidebar = showInspector ? Math.min(inspectorWidth,
				Math.max(180, width * .45f)) : 0;
		float editorWidth = Math.max(0, width - sidebar);
		back.setBounds(8, 6, 32, 28);
		forward.setBounds(44, 6, 32, 28);
		breadcrumb.setBounds(84, 6, Math.max(0, editorWidth - 92), 28);
		editor.setBounds(0, header, editorWidth,
				Math.max(0, height - header));
		if(restoreCameraPending&&editorWidth>0&&editor.getHeight()>0){
            var document=documents.peekLast();var camera=document==null?null:document.viewState.camera();
            editor.setPan(camera==null?0:editor.getZoom()*(editorWidth*.5f-camera.x()),camera==null?0:editor.getZoom()*(editor.getHeight()*.5f-camera.y()));
            restoreCameraPending=false;
        }
        if(compositeEditor!=null)compositeEditor.setBounds(0,header,editorWidth,Math.max(0,height-header));
        GLContainer sidebarControl = inspectorTabs == null ? interfaceScroll : inspectorTabs;
		sidebarControl.setVisible(showInspector);
		if (showInspector) sidebarControl.setBounds(width - sidebar, header,
				sidebar, Math.max(0, height - header));
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
            setAutoLayoutRole(direction==GLPortDirection.INPUT?GLNodeAutoLayout.Role.INPUT_BOUNDARY:GLNodeAutoLayout.Role.OUTPUT_BOUNDARY);
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

