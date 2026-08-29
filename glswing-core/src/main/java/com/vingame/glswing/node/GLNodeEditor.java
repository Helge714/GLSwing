package com.vingame.glswing.node;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.input.GLMouseWheelCaptureTarget;
import com.vingame.glswing.input.GLPointerController;
import com.vingame.glswing.input.GLPointerCursor;
import com.vingame.glswing.rendering.GLColorInterpolator;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTransform;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLMenuItem;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLUiKeys;

/**
 * Lightweight host for positioned nodes. It owns node Z-order, graph
 * selection, viewport navigation and interactive graph connections.
 */
public class GLNodeEditor extends GLContainer
		implements GLMouseWheelCaptureTarget, GLKeyTarget {
	private static final float CONNECTION_DRAG_THRESHOLD = 4;
	private static final float MARQUEE_DRAG_THRESHOLD = 3;
	private final List<GLNode> nodes = new ArrayList<>();
	private final List<GLNodeConnection> connections = new ArrayList<>();
	private final LinkedHashSet<GLNode> selectedNodes = new LinkedHashSet<>();
	private final CopyOnWriteArrayList<GLNodeEditorListener> listeners =
			new CopyOnWriteArrayList<>();
	private final Map<GLNode, float[]> moveStartLocations =
			new LinkedHashMap<>();
	private GLNode selectedNode;
	private GLNodeConnection selectedConnection;
	private GLNodePort gestureSource;
	private GLNodePort fixedGesturePort;
	private GLNodePort candidatePort;
	private GLNodeConnection detachedConnection;
	private List<GLNodeConnection> connectionGestureBefore = List.of();
	private boolean connectionDragging;
	private float connectionPressScreenX;
	private float connectionPressScreenY;
	private float connectionPointerX;
	private float connectionPointerY;
	private GLNodeEditorStyle localStyle;
	private GLTheme cachedTheme;
	private GLNodeEditorStyle cachedThemeStyle;
	private Float connectionSnapDistance;
	private Float connectionCurveTolerance;
	private Float connectionHandleFactor;
	private Float minimumConnectionHandleLength;
	private Float connectionWidth;
	private Color connectionBorder;
	private Color selectedConnectionBorder;
	private Float connectionBorderWidth;
	private GLColorInterpolator connectionColorInterpolator;
	private float zoom = 1;
	private float minimumZoom = 0.25f;
	private float maximumZoom = 4;
	private float wheelPanDistance = 48;
	private float panX;
	private float panY;
	private WorkspaceGesture workspaceGesture = WorkspaceGesture.NONE;
	private GLPointerController workspacePointerController =
			GLPointerController.NONE;
	private float workspacePressViewportX;
	private float workspacePressViewportY;
	private float workspaceStartPanX;
	private float workspaceStartPanY;
	private float marqueeStartX;
	private float marqueeStartY;
	private float marqueeCurrentX;
	private float marqueeCurrentY;
	private boolean marqueeVisible;
	private Set<GLNode> marqueeBaseSelection = Set.of();
	private int marqueeModifiers;
	private GLPopupMenu connectionContextMenu;
	private GLMenuItem deleteConnectionMenuItem;
	private GLNodeConnection contextConnection;

	public void addNode(GLNode node, float x, float y) {
		if (node == null)
			throw new IllegalArgumentException("Node must not be null");
		if (nodes.contains(node)) {
			node.setLocation(x, y);
			return;
		}
		if (node.getGLParent() != null)
			throw new IllegalArgumentException(
					"Node must not already belong to another GL parent");
		if (node.getWidth() <= 0 || node.getHeight() <= 0) {
			var preferred = node.getPreferredGLSize();
			node.setSize(preferred.width(), preferred.height());
		}
		node.setLocation(x, y);
		nodes.add(node);
		addGL(node);
	}

	public boolean removeNode(GLNode node) {
		if (node == null || !nodes.remove(node))
			return false;
		if (gestureSource != null && (gestureSource.getNode() == node
				|| fixedGesturePort != null && fixedGesturePort.getNode() == node))
			cancelConnectionGesture(false);
		if (selectedNodes.contains(node)) {
			LinkedHashSet<GLNode> remaining = new LinkedHashSet<>(selectedNodes);
			remaining.remove(node);
			applySelection(remaining, null, last(remaining));
		}
		for (GLNodeConnection connection : List.copyOf(connections))
			if (connection.getInput().getNode() == node
					|| connection.getOutput().getNode() == node)
				removeConnection(connection);
		removeGL(node);
		return true;
	}

	public void removeAllNodes() {
		cancelConnectionGesture(false);
		setSelectedNode(null);
		for (GLNode node : List.copyOf(nodes))
			removeGL(node);
		nodes.clear();
		for (GLNodeConnection connection : List.copyOf(connections))
			removeConnection(connection);
	}

	public List<GLNode> getNodes() {
		return List.copyOf(nodes);
	}

	public GLNode getSelectedNode() {
		return selectedNode;
	}

	/** Alias emphasizing that the primary selected node is the active node. */
	public GLNode getActiveNode() {
		return selectedNode;
	}

	/** Selected nodes in stable editor order. The last activated node is primary. */
	public List<GLNode> getSelectedNodes() {
		return nodes.stream().filter(selectedNodes::contains).toList();
	}

	public void removeSelectedNodes() {
		for (GLNode node : List.copyOf(getSelectedNodes()))
			removeNode(node);
	}

	void removeSelectedNodesFromUser() {
		removeNodesFromUser(getSelectedNodes());
	}

	void removeNodeFromUser(GLNode node) {
		removeNodesFromUser(List.of(node));
	}

	/** Removes either the selected connection or all selected nodes. */
	public void deleteSelection() {
		if (selectedConnection != null)
			removeConnectionFromUser(selectedConnection);
		else
			removeSelectedNodesFromUser();
	}

	public GLNodeConnection connect(GLNodePort output, GLNodePort input) {
		GLNodeConnection connection = new GLNodeConnection(output, input);
		addConnection(connection);
		return connection;
	}

	public boolean canConnect(GLNodePort first, GLNodePort second) {
		GLNodePort output = outputOf(first, second);
		GLNodePort input = inputOf(first, second);
		if (output == null || input == null
				|| !nodes.contains(output.getNode()) || !nodes.contains(input.getNode())
				|| !output.canConnectTo(input) || !input.canConnectTo(output)
				|| hasConnection(output, input))
			return false;
		if (connectionCount(output) >= output.getMaximumConnections())
			return false;
		int inputConnections = connectionCount(input);
		return inputConnections < input.getMaximumConnections()
				|| input.getMaximumConnections() == 1;
	}

	public void addConnection(GLNodeConnection connection) {
		if (connection == null)
			throw new IllegalArgumentException("Node connection must not be null");
		if (!nodes.contains(connection.getOutput().getNode())
				|| !nodes.contains(connection.getInput().getNode()))
			throw new IllegalArgumentException(
					"Both connection ports must belong to nodes in this editor");
		if (connections.contains(connection))
			return;
		GLNodePort output = connection.getOutput();
		GLNodePort input = connection.getInput();
		if (!canConnect(output, input))
			throw new IllegalArgumentException(
					"Node ports reject this connection or reached their capacity");
		if (input.getMaximumConnections() == 1)
			for (GLNodeConnection existing : List.copyOf(connections))
				if (existing.getInput() == input)
					removeConnection(existing);
		connection.attachTo(this);
		connections.add(connection);
		input.connectionStateChanged();
		repaintGL();
		for (GLNodeEditorListener listener : listeners)
			listener.connectionAdded(this, connection);
	}

	public boolean removeConnection(GLNodeConnection connection) {
		if (!connections.contains(connection))
			return false;
		if (selectedConnection == connection)
			setSelectedConnection(null);
		connections.remove(connection);
		connection.detachFrom(this);
		connection.getInput().connectionStateChanged();
		repaintGL();
		for (GLNodeEditorListener listener : listeners)
			listener.connectionRemoved(this, connection);
		return true;
	}

	public List<GLNodeConnection> getConnections() {
		return List.copyOf(connections);
	}

	public GLNodeConnection getSelectedConnection() {
		return selectedConnection;
	}

	public void addNodeEditorListener(GLNodeEditorListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Node-editor listener must not be null");
		listeners.addIfAbsent(listener);
	}

	public void removeNodeEditorListener(GLNodeEditorListener listener) {
		listeners.remove(listener);
	}

	public GLNodeEditorStyle getGLStyle() {
		if (localStyle != null)
			return localStyle;
		GLTheme theme = getGLTheme();
		if (cachedTheme != theme || cachedThemeStyle == null) {
			cachedTheme = theme;
			cachedThemeStyle = GLNodeEditorStyle.fromTheme(theme);
		}
		return cachedThemeStyle;
	}

	public void setGLStyle(GLNodeEditorStyle style) {
		if (style == null)
			throw new IllegalArgumentException(
					"Node editor style must not be null");
		if (localStyle == style)
			return;
		localStyle = style;
		invalidateRecording();
	}

	public void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		invalidateRecording();
	}

	/** Screen-space radius around a port in which a dragged connection snaps. */
	public float getConnectionSnapDistance() {
		return connectionSnapDistance != null ? connectionSnapDistance
				: getGLStyle().connectionSnapDistance();
	}

	public void setConnectionSnapDistance(float distance) {
		if (!Float.isFinite(distance) || distance < 0)
			throw new IllegalArgumentException(
					"Connection snap distance must be finite and non-negative");
		if (connectionSnapDistance != null
				&& Float.compare(connectionSnapDistance, distance) == 0)
			return;
		connectionSnapDistance = distance;
		repaintGL();
	}

	public void clearConnectionSnapDistance() {
		if (connectionSnapDistance == null)
			return;
		connectionSnapDistance = null;
		repaintGL();
	}

	/**
	 * Maximum screen-space deviation used by adaptive connection tessellation.
	 * Smaller values create more segments and smoother curves.
	 */
	public float getConnectionCurveTolerance() {
		return connectionCurveTolerance != null ? connectionCurveTolerance
				: getGLStyle().connectionCurveTolerance();
	}

	public void setConnectionCurveTolerance(float tolerance) {
		if (!Float.isFinite(tolerance) || tolerance <= 0)
			throw new IllegalArgumentException(
					"Connection curve tolerance must be finite and positive");
		if (connectionCurveTolerance != null
				&& Float.compare(connectionCurveTolerance, tolerance) == 0)
			return;
		connectionCurveTolerance = tolerance;
		repaintGL();
	}

	public void clearConnectionCurveTolerance() {
		if (connectionCurveTolerance == null)
			return;
		connectionCurveTolerance = null;
		repaintGL();
	}

	/** Portion of horizontal endpoint distance used as Bezier handle length. */
	public float getConnectionHandleFactor() {
		return connectionHandleFactor != null ? connectionHandleFactor
				: getGLStyle().connectionHandleFactor();
	}

	public void setConnectionHandleFactor(float factor) {
		if (!Float.isFinite(factor) || factor < 0)
			throw new IllegalArgumentException(
					"Connection handle factor must be finite and non-negative");
		if (connectionHandleFactor != null
				&& Float.compare(connectionHandleFactor, factor) == 0)
			return;
		connectionHandleFactor = factor;
		repaintGL();
	}

	public void clearConnectionHandleFactor() {
		if (connectionHandleFactor == null)
			return;
		connectionHandleFactor = null;
		repaintGL();
	}

	/** Minimum Bezier handle length measured in logical node coordinates. */
	public float getMinimumConnectionHandleLength() {
		return minimumConnectionHandleLength != null
				? minimumConnectionHandleLength
				: getGLStyle().minimumConnectionHandleLength();
	}

	public void setMinimumConnectionHandleLength(float length) {
		if (!Float.isFinite(length) || length < 0)
			throw new IllegalArgumentException(
					"Minimum connection handle length must be finite and non-negative");
		if (minimumConnectionHandleLength != null
				&& Float.compare(minimumConnectionHandleLength, length) == 0)
			return;
		minimumConnectionHandleLength = length;
		repaintGL();
	}

	public void clearMinimumConnectionHandleLength() {
		if (minimumConnectionHandleLength == null)
			return;
		minimumConnectionHandleLength = null;
		repaintGL();
	}

	/** Default screen-space width used by connections without a local width. */
	public float getConnectionWidth() {
		return connectionWidth != null ? connectionWidth
				: getGLStyle().connectionWidth();
	}

	public void setConnectionWidth(float width) {
		if (!Float.isFinite(width) || width <= 0)
			throw new IllegalArgumentException(
					"Connection width must be finite and positive");
		if (connectionWidth != null
				&& Float.compare(connectionWidth, width) == 0)
			return;
		connectionWidth = width;
		repaintGL();
	}

	public void clearConnectionWidth() {
		if (connectionWidth == null)
			return;
		connectionWidth = null;
		repaintGL();
	}

	public Color getConnectionBorder() {
		return connectionBorder != null ? connectionBorder
				: getGLStyle().connectionBorder();
	}

	public void setConnectionBorder(Color color) {
		if (color == null)
			throw new IllegalArgumentException(
					"Connection border color must not be null");
		if (color.equals(connectionBorder))
			return;
		connectionBorder = color;
		repaintGL();
	}

	public void clearConnectionBorder() {
		if (connectionBorder == null)
			return;
		connectionBorder = null;
		repaintGL();
	}

	public Color getSelectedConnectionBorder() {
		return selectedConnectionBorder != null ? selectedConnectionBorder
				: getGLStyle().selectedConnectionBorder();
	}

	public void setSelectedConnectionBorder(Color color) {
		if (color == null)
			throw new IllegalArgumentException(
					"Selected connection border color must not be null");
		if (color.equals(selectedConnectionBorder))
			return;
		selectedConnectionBorder = color;
		repaintGL();
	}

	public void clearSelectedConnectionBorder() {
		if (selectedConnectionBorder == null)
			return;
		selectedConnectionBorder = null;
		repaintGL();
	}

	/** Border thickness added on each side of a connection in screen pixels. */
	public float getConnectionBorderWidth() {
		return connectionBorderWidth != null ? connectionBorderWidth
				: getGLStyle().connectionBorderWidth();
	}

	public void setConnectionBorderWidth(float width) {
		if (!Float.isFinite(width) || width < 0)
			throw new IllegalArgumentException(
					"Connection border width must be finite and non-negative");
		if (connectionBorderWidth != null
				&& Float.compare(connectionBorderWidth, width) == 0)
			return;
		connectionBorderWidth = width;
		repaintGL();
	}

	public void clearConnectionBorderWidth() {
		if (connectionBorderWidth == null)
			return;
		connectionBorderWidth = null;
		repaintGL();
	}

	public GLColorInterpolator getConnectionColorInterpolator() {
		return connectionColorInterpolator != null ? connectionColorInterpolator
				: getGLStyle().connectionColorInterpolator();
	}

	/** Overrides how output and input colors are mixed along connections. */
	public void setConnectionColorInterpolator(
			GLColorInterpolator interpolation) {
		if (interpolation == null)
			throw new IllegalArgumentException(
					"Connection color interpolation must not be null");
		if (connectionColorInterpolator == interpolation)
			return;
		connectionColorInterpolator = interpolation;
		repaintGL();
	}

	public void clearConnectionColorInterpolator() {
		if (connectionColorInterpolator == null)
			return;
		connectionColorInterpolator = null;
		repaintGL();
	}

	int connectionCount(GLNodePort port) {
		int count = 0;
		for (GLNodeConnection connection : connections)
			if (connection.getInput() == port || connection.getOutput() == port)
				count++;
		return count;
	}

	public float getZoom() { return zoom; }

	public void setZoom(float zoom) {
		if (!Float.isFinite(zoom) || zoom <= 0)
			throw new IllegalArgumentException("Node-editor zoom must be positive");
		float clamped = Math.max(minimumZoom, Math.min(maximumZoom, zoom));
		if (Float.compare(this.zoom, clamped) == 0)
			return;
		this.zoom = clamped;
		repaintAndRebuildPicking(this);
	}

	public void zoomBy(float factor) {
		if (!Float.isFinite(factor) || factor <= 0)
			throw new IllegalArgumentException("Zoom factor must be positive");
		setZoom(zoom * factor);
	}

	/** Zooms while keeping the world point below the mouse stationary. */
	public void zoomByAtScreenPosition(float factor, float screenX,
			float screenY) {
		if (!Float.isFinite(factor) || factor <= 0)
			throw new IllegalArgumentException("Zoom factor must be positive");
		setZoomAtScreenPosition(zoom * factor, screenX, screenY);
	}

	/** Sets zoom around a point expressed in host screen coordinates. */
	public void setZoomAtScreenPosition(float requestedZoom, float screenX,
			float screenY) {
		if (!Float.isFinite(requestedZoom) || requestedZoom <= 0
				|| !Float.isFinite(screenX) || !Float.isFinite(screenY))
			throw new IllegalArgumentException(
					"Zoom and screen position must be finite and valid");
		float clamped = Math.max(minimumZoom,
				Math.min(maximumZoom, requestedZoom));
		if (Float.compare(zoom, clamped) == 0)
			return;
		float[] viewport = screenToViewport(screenX, screenY);
		float centerX = getWidth() * 0.5f;
		float centerY = getHeight() * 0.5f;
		float worldX = centerX
				+ (viewport[0] - centerX - panX) / zoom;
		float worldY = centerY
				+ (viewport[1] - centerY - panY) / zoom;
		zoom = clamped;
		panX = viewport[0] - centerX - zoom * (worldX - centerX);
		panY = viewport[1] - centerY - zoom * (worldY - centerY);
		repaintAndRebuildPicking(this);
	}

	public float getPanX() { return panX; }
	public float getPanY() { return panY; }

	/** Sets the viewport displacement in logical screen pixels. */
	public void setPan(float x, float y) {
		if (!Float.isFinite(x) || !Float.isFinite(y))
			throw new IllegalArgumentException("Viewport pan must be finite");
		if (Float.compare(panX, x) == 0 && Float.compare(panY, y) == 0)
			return;
		panX = x;
		panY = y;
		repaintAndRebuildPicking(this);
	}

	public void panBy(float deltaX, float deltaY) {
		setPan(panX + deltaX, panY + deltaY);
	}

	public float getWheelPanDistance() { return wheelPanDistance; }

	public void setWheelPanDistance(float distance) {
		if (!Float.isFinite(distance) || distance < 0)
			throw new IllegalArgumentException(
					"Wheel pan distance must be finite and non-negative");
		wheelPanDistance = distance;
	}

	public void resetView() {
		boolean changed = Float.compare(zoom, 1) != 0
				|| Float.compare(panX, 0) != 0 || Float.compare(panY, 0) != 0;
		zoom = 1;
		panX = 0;
		panY = 0;
		if (changed)
			repaintAndRebuildPicking(this);
	}

	public void setZoomRange(float minimum, float maximum) {
		if (!Float.isFinite(minimum) || !Float.isFinite(maximum)
				|| minimum <= 0 || maximum < minimum)
			throw new IllegalArgumentException("Invalid node-editor zoom range");
		minimumZoom = minimum;
		maximumZoom = maximum;
		setZoom(zoom);
	}

	public void setSelectedNode(GLNode node) {
		applySelection(node == null ? List.of() : List.of(node), null, node);
	}

	public void setSelectedNodes(Collection<? extends GLNode> selection) {
		if (selection == null)
			throw new IllegalArgumentException("Node selection must not be null");
		LinkedHashSet<GLNode> normalized = new LinkedHashSet<>(selection);
		applySelection(normalized, null, last(normalized));
	}

	public void setNodeSelected(GLNode node, boolean selected) {
		if (node == null || !nodes.contains(node))
			throw new IllegalArgumentException(
					"Selected node must belong to this editor");
		LinkedHashSet<GLNode> next = new LinkedHashSet<>(selectedNodes);
		if (selected)
			next.add(node);
		else
			next.remove(node);
		applySelection(next, null, selected ? node : last(next));
	}

	public void setSelectedConnection(GLNodeConnection connection) {
		if (connection != null && !connections.contains(connection))
			throw new IllegalArgumentException(
					"Selected connection must belong to this editor");
		applySelection(List.of(), connection, null);
	}

	public void clearSelection() {
		applySelection(List.of(), null, null);
	}

	private void applySelection(Collection<? extends GLNode> nodeSelection,
			GLNodeConnection connection, GLNode primary) {
		LinkedHashSet<GLNode> next = new LinkedHashSet<>(nodeSelection);
		for (GLNode node : next)
			if (!nodes.contains(node))
				throw new IllegalArgumentException(
						"Selected nodes must belong to this editor");
		if (connection != null && !connections.contains(connection))
			throw new IllegalArgumentException(
					"Selected connection must belong to this editor");
		if (connection != null && !next.isEmpty())
			throw new IllegalArgumentException(
					"Nodes and a connection cannot be selected simultaneously");
		if (primary != null && !next.contains(primary))
			primary = last(next);
		if (primary == null && !next.isEmpty())
			primary = last(next);
		if (selectedNodes.equals(next) && selectedConnection == connection
				&& selectedNode == primary)
			return;
		LinkedHashSet<GLNode> affected = new LinkedHashSet<>(selectedNodes);
		affected.addAll(next);
		for (GLNode node : affected)
			node.setSelectionStateFromEditor(next.contains(node), node == primary);
		selectedNodes.clear();
		selectedNodes.addAll(next);
		selectedNode = primary;
		selectedConnection = connection;
		repaintGL();
		for (GLNodeEditorListener listener : listeners)
			listener.selectionChanged(this);
	}

	private static GLNode last(Collection<GLNode> nodes) {
		GLNode result = null;
		for (GLNode node : nodes)
			result = node;
		return result;
	}

	@Override
	protected void record(GLContext2D context) {
		if (getWidth() <= 0 || getHeight() <= 0)
			return;
		context.fillUIRoundRect(0, 0, getWidth(), getHeight(), 0,
				getGLStyle().background());
	}

	@Override
	protected void render(GLContext2D context) {
		super.render(context);
		GLTransform previous = context.getTransform();
		try {
			context.setTransform(viewTransform(previous));
			drawConnections(context);
		} finally {
			context.setTransform(previous);
		}
	}

	@Override
	protected void renderChildren(GLContext2D context) {
		if (getWidth() <= 0 || getHeight() <= 0)
			return;
		context.beginClip(0, 0, getWidth(), getHeight());
		GLTransform previous = context.getTransform();
		try {
			context.setTransform(viewTransform(previous));
			super.renderChildren(context);
			drawMarquee(context);
		} finally {
			context.setTransform(previous);
			context.endClip();
		}
	}

	@Override
	public void mousePressed(GLMouseEvent event) {
		requestGLFocus(this);
		if (event.getButton() == MouseEvent.BUTTON2) {
			beginPan(event);
			return;
		}
		if (event.getButton() == MouseEvent.BUTTON1)
			beginMarquee(event);
	}

	@Override
	public void mouseDragged(GLMouseEvent event) {
		if (workspaceGesture == WorkspaceGesture.PAN) {
			if ((event.getModifiersEx() & MouseEvent.BUTTON2_DOWN_MASK) == 0)
				return;
			float[] viewport = screenToViewport(event.getScreenX(),
					event.getScreenY());
			setPan(workspaceStartPanX
					+ viewport[0] - workspacePressViewportX,
					workspaceStartPanY
					+ viewport[1] - workspacePressViewportY);
			event.consume();
		} else if (workspaceGesture == WorkspaceGesture.MARQUEE) {
			updateMarquee(event);
		}
	}

	@Override
	public void mouseReleased(GLMouseEvent event) {
		if (workspaceGesture == WorkspaceGesture.PAN
				&& event.getButton() == MouseEvent.BUTTON2
				|| workspaceGesture == WorkspaceGesture.MARQUEE
				&& event.getButton() == MouseEvent.BUTTON1)
			finishWorkspaceGesture(event);
	}

	@Override
	public void mouseCaptureLost(GLMouseEvent event) {
		finishWorkspaceGesture(null);
	}

	@Override
	public void mouseWheelMoved(GLMouseWheelEvent event) {
		double rotation = event.getPreciseWheelRotation();
		if (rotation == 0)
			return;
		if (getGLInputMap().hasModifiers(GLInputActions.NODE_HORIZONTAL_WHEEL,
				event.getModifiersEx())) {
			panBy((float) -rotation * wheelPanDistance, 0);
		} else if (getGLInputMap().hasModifiers(GLInputActions.NODE_VERTICAL_WHEEL,
				event.getModifiersEx())) {
			panBy(0, (float) -rotation * wheelPanDistance);
		} else {
			zoomByAtScreenPosition((float) Math.pow(1.12, -rotation),
					event.getScreenX(), event.getScreenY());
		}
		event.consume();
	}

	@Override
	public void keyPressed(GLKeyEvent event) {
		if (!getGLInputMap().matches(GLInputActions.NODE_DELETE, event)
				|| selectedConnection == null && selectedNodes.isEmpty())
			return;
		deleteSelection();
		event.consume();
	}

	@Override
	protected void themeChanged() {
		cachedTheme = null;
		cachedThemeStyle = null;
		if (localStyle == null)
			invalidateRecording();
		else
			repaintGL();
	}

	@Override
	protected void localizerChanged() {
		if (deleteConnectionMenuItem != null)
			deleteConnectionMenuItem.setText(
					getGLLocalizer().text(GLUiKeys.NODE_DELETE_CONNECTION));
	}

	void activate(GLNode node) {
		activate(node, 0, false);
	}

	void activate(GLNode node, int modifiersEx, boolean contextClick) {
		if (!nodes.contains(node))
			return;
		requestGLFocus(this);
		boolean shift = getGLInputMap().hasModifiers(
				GLInputActions.NODE_TOGGLE_SELECTION, modifiersEx);
		if (contextClick) {
			if (!selectedNodes.contains(node))
				setSelectedNode(node);
		} else if (shift) {
			if (!selectedNodes.contains(node)) {
				setNodeSelected(node, true);
			} else if (selectedNode == node) {
				setNodeSelected(node, false);
			} else {
				applySelection(selectedNodes, null, node);
			}
		} else if (!selectedNodes.contains(node)) {
			setSelectedNode(node);
		} else if (selectedNode != node) {
			applySelection(selectedNodes, null, node);
		}
		if (selectedNodes.contains(node))
			bringSelectedNodesToFront();
	}

	void completeNodeClick(GLNode node, int modifiersEx) {
		if (!nodes.contains(node)
				|| getGLInputMap().hasModifiers(
						GLInputActions.NODE_TOGGLE_SELECTION, modifiersEx))
			return;
		setSelectedNode(node);
		bringSelectedNodesToFront();
	}

	void activateConnection(GLNodeConnection connection, int modifiersEx,
			boolean contextClick) {
		if (!connections.contains(connection))
			return;
		requestGLFocus(this);
		boolean toggle = !contextClick && getGLInputMap().hasModifiers(
				GLInputActions.NODE_TOGGLE_SELECTION, modifiersEx);
		if (toggle && selectedConnection == connection)
			clearSelection();
		else
			setSelectedConnection(connection);
	}

	public GLPopupMenu getConnectionContextMenu() {
		if (connectionContextMenu == null) {
			connectionContextMenu = new GLPopupMenu();
			deleteConnectionMenuItem = connectionContextMenu.addItem(
					getGLLocalizer().text(GLUiKeys.NODE_DELETE_CONNECTION),
					event -> {
						GLNodeConnection connection = contextConnection;
						if (connection != null && connections.contains(connection))
							removeConnectionFromUser(connection);
					});
		}
		return connectionContextMenu;
	}

	void showConnectionContextMenu(GLNodeConnection connection,
			GLMouseEvent event) {
		GLParent host = rootParent();
		if (host == null)
			return;
		contextConnection = connection;
		getConnectionContextMenu().show(host, event.getScreenX(),
				event.getScreenY());
	}

	private GLParent rootParent() {
		GLParent current = getGLParent();
		while (current instanceof GLContainer container
				&& container.getGLParent() != null)
			current = container.getGLParent();
		return current;
	}

	void beginNodeMove(GLNode source) {
		moveStartLocations.clear();
		Collection<GLNode> moved = selectedNodes.contains(source)
				? selectedNodes : List.of(source);
		for (GLNode node : moved)
			moveStartLocations.put(node,
					new float[] { node.getX(), node.getY() });
	}

	void dragNodeMove(GLNode source, float deltaX, float deltaY) {
		if (!moveStartLocations.containsKey(source))
			return;
		for (Map.Entry<GLNode, float[]> entry : moveStartLocations.entrySet()) {
			float[] start = entry.getValue();
			entry.getKey().setLocation(start[0] + deltaX, start[1] + deltaY);
		}
	}

	void endNodeMove(GLNode source) {
		if (moveStartLocations.containsKey(source)) {
			Map<GLNode, float[]> before = copyLocations(moveStartLocations);
			Map<GLNode, float[]> after = new LinkedHashMap<>();
			for (GLNode node : moveStartLocations.keySet())
				after.put(node, new float[] { node.getX(), node.getY() });
			moveStartLocations.clear();
			recordLocations(before, after);
		}
	}

	private void bringSelectedNodesToFront() {
		for (GLNode candidate : nodes)
			if (selectedNodes.contains(candidate))
				candidate.bringToFront();
	}

	void beginConnectionGesture(GLNodePort source, GLMouseEvent event) {
		cancelConnectionGesture(true);
		connectionGestureBefore = List.copyOf(connections);
		gestureSource = source;
		fixedGesturePort = source;
		connectionPressScreenX = event.getScreenX();
		connectionPressScreenY = event.getScreenY();
		connectionPointerX = source.getNode().getX()
				+ source.getCenterXInNode();
		connectionPointerY = source.getNode().getY()
				+ source.getCenterYInNode();
	}

	void dragConnectionGesture(GLNodePort source, GLMouseEvent event) {
		if (gestureSource != source || !event.hasLocalPosition()
				|| (event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == 0)
			return;
		if (!connectionDragging) {
			float deltaX = event.getScreenX() - connectionPressScreenX;
			float deltaY = event.getScreenY() - connectionPressScreenY;
			if (Math.hypot(deltaX, deltaY) < CONNECTION_DRAG_THRESHOLD)
				return;
			connectionDragging = true;
			if (source.getDirection() == GLNodePort.Direction.INPUT) {
				detachedConnection = firstConnection(source);
				if (detachedConnection != null) {
					fixedGesturePort = detachedConnection.getOutput();
					removeConnection(detachedConnection);
				}
			}
		}
		updateConnectionPointer(source, event);
		updateCandidate(candidateAt(event.getScreenX(), event.getScreenY()));
		repaintGL();
		event.consume();
	}

	void endConnectionGesture(GLNodePort source, GLMouseEvent event,
			boolean captureLost) {
		if (gestureSource != source)
			return;
		if (connectionDragging && !captureLost) {
			updateConnectionPointer(source, event);
			updateCandidate(candidateAt(event.getScreenX(), event.getScreenY()));
		}
		GLNodePort fixed = fixedGesturePort;
		GLNodePort target = !captureLost && connectionDragging
				? candidatePort : null;
		GLNodeConnection detached = detachedConnection;
		clearConnectionGestureState();
		if (target != null) {
			GLNodePort output = outputOf(fixed, target);
			GLNodePort input = inputOf(fixed, target);
			if (detached != null && detached.getOutput() == output
					&& detached.getInput() == input)
				addConnection(detached);
			else
				connect(output, input);
		} else if (captureLost && detached != null) {
			addConnection(detached);
		}
		recordConnectionChange(connectionGestureBefore,
				List.copyOf(connections));
		connectionGestureBefore = List.of();
		repaintGL();
		if (event != null)
			event.consume();
	}

	void connectionChanged(GLNodeConnection connection) {
		if (connections.contains(connection))
			repaintGL();
	}

	private void updateConnectionPointer(GLNodePort source,
			GLMouseEvent event) {
		connectionPointerX = source.getNode().getX() + source.getX()
				+ event.getLocalX();
		connectionPointerY = source.getNode().getY() + source.getY()
				+ event.getLocalY();
	}

	private GLNodePort candidateAt(float screenX, float screenY) {
		List<GLContainer> paintOrder = getGLChildrenInPaintOrder();
		GLNodePort nearest = null;
		float nearestDistance = Float.POSITIVE_INFINITY;
		for (int nodeIndex = paintOrder.size() - 1; nodeIndex >= 0; nodeIndex--) {
			if (!(paintOrder.get(nodeIndex) instanceof GLNode node)
					|| !node.isVisible())
				continue;
			List<GLNodePort> ports = node.getPorts();
			for (int portIndex = ports.size() - 1; portIndex >= 0; portIndex--) {
				GLNodePort port = ports.get(portIndex);
				float[] bounds = port.getLastRenderedScreenBounds();
				if (bounds == null || bounds[2] <= 0 || bounds[3] <= 0)
					continue;
				float centerX = bounds[0] + bounds[2] * 0.5f;
				float centerY = bounds[1] + bounds[3] * 0.5f;
				float distance = (float) Math.hypot(screenX - centerX,
						screenY - centerY);
				float hitRadius = Math.max(bounds[2], bounds[3]) * 0.5f
						+ getConnectionSnapDistance();
				if (distance <= hitRadius && distance < nearestDistance
						&& canConnect(fixedGesturePort, port)) {
					nearest = port;
					nearestDistance = distance;
				}
			}
		}
		return nearest;
	}

	private void updateCandidate(GLNodePort candidate) {
		if (candidatePort == candidate)
			return;
		if (candidatePort != null)
			candidatePort.setConnectionCandidate(false);
		candidatePort = candidate;
		if (candidatePort != null)
			candidatePort.setConnectionCandidate(true);
	}

	private void cancelConnectionGesture(boolean restoreDetached) {
		GLNodeConnection detached = detachedConnection;
		clearConnectionGestureState();
		if (restoreDetached && detached != null)
			addConnection(detached);
		connectionGestureBefore = List.of();
	}

	private void clearConnectionGestureState() {
		if (candidatePort != null)
			candidatePort.setConnectionCandidate(false);
		gestureSource = null;
		fixedGesturePort = null;
		candidatePort = null;
		detachedConnection = null;
		connectionDragging = false;
	}

	private GLNodeConnection firstConnection(GLNodePort port) {
		for (GLNodeConnection connection : connections)
			if (connection.getInput() == port || connection.getOutput() == port)
				return connection;
		return null;
	}

	private boolean hasConnection(GLNodePort output, GLNodePort input) {
		for (GLNodeConnection connection : connections)
			if (connection.getOutput() == output && connection.getInput() == input)
				return true;
		return false;
	}

	private static GLNodePort outputOf(GLNodePort first, GLNodePort second) {
		if (first == null || second == null || first.getDirection() == second.getDirection())
			return null;
		return first.getDirection() == GLNodePort.Direction.OUTPUT ? first : second;
	}

	private static GLNodePort inputOf(GLNodePort first, GLNodePort second) {
		if (first == null || second == null || first.getDirection() == second.getDirection())
			return null;
		return first.getDirection() == GLNodePort.Direction.INPUT ? first : second;
	}

	private GLTransform viewTransform(GLTransform base) {
		float centerX = getWidth() * 0.5f;
		float centerY = getHeight() * 0.5f;
		return base.copy().translate(centerX + panX, centerY + panY).scale(zoom)
				.translate(-centerX, -centerY);
	}

	private void beginPan(GLMouseEvent event) {
		workspaceGesture = WorkspaceGesture.PAN;
		float[] viewport = screenToViewport(event.getScreenX(), event.getScreenY());
		workspacePressViewportX = viewport[0];
		workspacePressViewportY = viewport[1];
		workspaceStartPanX = panX;
		workspaceStartPanY = panY;
		workspacePointerController = event.getPointerController();
		workspacePointerController.setCursor(this, GLPointerCursor.MOVE);
		event.consume();
	}

	private void beginMarquee(GLMouseEvent event) {
		workspaceGesture = WorkspaceGesture.MARQUEE;
		float[] world = screenToWorld(event.getScreenX(), event.getScreenY());
		marqueeStartX = world[0];
		marqueeStartY = world[1];
		marqueeCurrentX = world[0];
		marqueeCurrentY = world[1];
		marqueeVisible = false;
		marqueeBaseSelection = new LinkedHashSet<>(selectedNodes);
		marqueeModifiers = event.getModifiersEx();
		if (!getGLInputMap().hasModifiers(GLInputActions.NODE_TOGGLE_SELECTION,
				marqueeModifiers))
			clearSelection();
		float[] viewport = screenToViewport(event.getScreenX(), event.getScreenY());
		workspacePressViewportX = viewport[0];
		workspacePressViewportY = viewport[1];
		event.consume();
	}

	private void updateMarquee(GLMouseEvent event) {
		if ((event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == 0)
			return;
		float[] viewport = screenToViewport(event.getScreenX(), event.getScreenY());
		if (!marqueeVisible && Math.hypot(
				viewport[0] - workspacePressViewportX,
				viewport[1] - workspacePressViewportY) < MARQUEE_DRAG_THRESHOLD)
			return;
		marqueeVisible = true;
		float[] world = screenToWorld(event.getScreenX(), event.getScreenY());
		marqueeCurrentX = world[0];
		marqueeCurrentY = world[1];
		LinkedHashSet<GLNode> hits = marqueeHits();
		LinkedHashSet<GLNode> next = new LinkedHashSet<>();
		boolean toggle = getGLInputMap().hasModifiers(
				GLInputActions.NODE_TOGGLE_SELECTION, marqueeModifiers);
		if (toggle)
			next.addAll(marqueeBaseSelection);
		GLNode primary = selectedNode;
		if (toggle) {
			for (GLNode node : hits) {
				if (!next.remove(node)) {
					next.add(node);
					primary = node;
				}
			}
		} else {
			next.addAll(hits);
			primary = last(next);
		}
		if (primary == null || !next.contains(primary))
			primary = last(next);
		applySelection(next, null, primary);
		repaintGL();
		event.consume();
	}

	private LinkedHashSet<GLNode> marqueeHits() {
		float left = Math.min(marqueeStartX, marqueeCurrentX);
		float top = Math.min(marqueeStartY, marqueeCurrentY);
		float right = Math.max(marqueeStartX, marqueeCurrentX);
		float bottom = Math.max(marqueeStartY, marqueeCurrentY);
		LinkedHashSet<GLNode> result = new LinkedHashSet<>();
		for (GLNode node : nodes)
			if (node.isVisible() && node.getX() < right
					&& node.getX() + node.getWidth() > left
					&& node.getY() < bottom
					&& node.getY() + node.getHeight() > top)
				result.add(node);
		return result;
	}

	private void drawMarquee(GLContext2D context) {
		if (!marqueeVisible)
			return;
		float x = Math.min(marqueeStartX, marqueeCurrentX);
		float y = Math.min(marqueeStartY, marqueeCurrentY);
		float width = Math.abs(marqueeCurrentX - marqueeStartX);
		float height = Math.abs(marqueeCurrentY - marqueeStartY);
		GLNodeEditorStyle style = getGLStyle();
		context.fillRect(x, y, width, height, style.marqueeFill(),
				GLContext2D.NO_PICK_ID);
		context.setStroke(new GLStroke(style.marqueeBorderWidth())
				.withWidthSpace(GLStroke.WidthSpace.SCREEN));
		context.drawRect(x, y, width, height, style.marqueeBorder(),
				GLContext2D.NO_PICK_ID);
	}

	private void finishWorkspaceGesture(GLMouseEvent event) {
		boolean repaint = marqueeVisible;
		workspaceGesture = WorkspaceGesture.NONE;
		marqueeVisible = false;
		marqueeBaseSelection = Set.of();
		workspacePointerController.clearCursor(this);
		workspacePointerController = GLPointerController.NONE;
		if (repaint)
			repaintGL();
		if (event != null)
			event.consume();
	}

	private float[] screenToViewport(float screenX, float screenY) {
		float[] bounds = getLastRenderedScreenBounds();
		if (bounds == null || bounds[2] <= 0 || bounds[3] <= 0)
			return new float[] { screenX, screenY };
		return new float[] {
				(screenX - bounds[0]) * getWidth() / bounds[2],
				(screenY - bounds[1]) * getHeight() / bounds[3]
		};
	}

	private float[] screenToWorld(float screenX, float screenY) {
		float[] viewport = screenToViewport(screenX, screenY);
		float centerX = getWidth() * 0.5f;
		float centerY = getHeight() * 0.5f;
		return new float[] {
				centerX + (viewport[0] - centerX - panX) / zoom,
				centerY + (viewport[1] - centerY - panY) / zoom
		};
	}

	private void removeNodesFromUser(Collection<GLNode> requested) {
		List<GLNode> removed = requested.stream().filter(nodes::contains).toList();
		if (removed.isEmpty())
			return;
		RemovedNodes snapshot = captureRemovedNodes(removed);
		for (GLNode node : removed)
			removeNode(node);
		var manager = getGLHistoryManager();
		if (manager != null)
			manager.record(removed.size() == 1 ? "Delete node" : "Delete nodes",
					() -> restoreRemovedNodes(snapshot),
					() -> {
						for (NodeState state : snapshot.nodes())
							removeNode(state.node());
					});
	}

	private RemovedNodes captureRemovedNodes(List<GLNode> removed) {
		List<NodeState> states = new ArrayList<>();
		for (GLNode node : removed)
			states.add(new NodeState(node, nodes.indexOf(node), node.getX(),
					node.getY(), node.getWidth(), node.getHeight()));
		Set<GLNode> removedSet = Set.copyOf(removed);
		List<GLNodeConnection> removedConnections = connections.stream()
				.filter(connection -> removedSet.contains(connection.getInput().getNode())
						|| removedSet.contains(connection.getOutput().getNode()))
				.toList();
		return new RemovedNodes(List.copyOf(states), removedConnections,
				getGLChildrenInPaintOrder(), List.copyOf(selectedNodes), selectedNode);
	}

	private void restoreRemovedNodes(RemovedNodes snapshot) {
		for (NodeState state : snapshot.nodes().stream()
				.sorted((first, second) -> Integer.compare(first.index(), second.index()))
				.toList()) {
			GLNode node = state.node();
			node.setBounds(state.x(), state.y(), state.width(), state.height());
			nodes.add(Math.min(state.index(), nodes.size()), node);
			addGL(node);
		}
		for (int index = 0; index < snapshot.paintOrder().size(); index++) {
			GLContainer child = snapshot.paintOrder().get(index);
			if (child.getGLParent() == this)
				setGLZOrder(child, index);
		}
		for (GLNodeConnection connection : snapshot.connections())
			addConnection(connection);
		applySelection(snapshot.selection(), null, snapshot.active());
	}

	private void removeConnectionFromUser(GLNodeConnection connection) {
		if (connection == null || !connections.contains(connection))
			return;
		List<GLNodeConnection> before = List.copyOf(connections);
		removeConnection(connection);
		recordConnectionChange(before, List.copyOf(connections));
	}

	private void recordConnectionChange(List<GLNodeConnection> before,
			List<GLNodeConnection> after) {
		if (before.equals(after))
			return;
		var manager = getGLHistoryManager();
		if (manager != null)
			manager.record("Change node connection",
					() -> applyConnections(before), () -> applyConnections(after));
	}

	private void applyConnections(List<GLNodeConnection> desired) {
		for (GLNodeConnection connection : List.copyOf(connections))
			removeConnection(connection);
		for (GLNodeConnection connection : desired)
			addConnection(connection);
	}

	private void recordLocations(Map<GLNode, float[]> before,
			Map<GLNode, float[]> after) {
		boolean changed = before.entrySet().stream().anyMatch(entry -> {
			float[] next = after.get(entry.getKey());
			return entry.getValue()[0] != next[0] || entry.getValue()[1] != next[1];
		});
		if (!changed)
			return;
		var manager = getGLHistoryManager();
		if (manager != null)
			manager.record(before.size() == 1 ? "Move node" : "Move nodes",
					() -> applyLocations(before), () -> applyLocations(after));
	}

	private static Map<GLNode, float[]> copyLocations(
			Map<GLNode, float[]> locations) {
		Map<GLNode, float[]> copy = new LinkedHashMap<>();
		for (Map.Entry<GLNode, float[]> entry : locations.entrySet())
			copy.put(entry.getKey(), entry.getValue().clone());
		return copy;
	}

	private static void applyLocations(Map<GLNode, float[]> locations) {
		for (Map.Entry<GLNode, float[]> entry : locations.entrySet()) {
			float[] point = entry.getValue();
			entry.getKey().setLocation(point[0], point[1]);
		}
	}

	private record NodeState(GLNode node, int index, float x, float y,
			float width, float height) {
	}

	private record RemovedNodes(List<NodeState> nodes,
			List<GLNodeConnection> connections, List<GLContainer> paintOrder,
			List<GLNode> selection, GLNode active) {
	}

	private enum WorkspaceGesture { NONE, PAN, MARQUEE }

	private void drawConnections(GLContext2D context) {
		GLStroke previous = context.getStroke();
		float previousCurveTolerance = context.getCurveTolerance();
		try {
			context.setCurveTolerance(getConnectionCurveTolerance());
			for (GLNodeConnection connection : connections)
				if (connection != selectedConnection)
					drawConnection(context, connection);
			if (selectedConnection != null
					&& connections.contains(selectedConnection))
				drawConnection(context, selectedConnection);
			if (connectionDragging && fixedGesturePort != null)
				drawConnectionPreview(context);
		} finally {
			context.setCurveTolerance(previousCurveTolerance);
			context.setStroke(previous);
		}
	}

	private void drawConnection(GLContext2D context,
			GLNodeConnection connection) {
		GLNodePort output = connection.getOutput();
		GLNodePort input = connection.getInput();
		if (output.getNode() == null || input.getNode() == null)
			return;
		Color border = connection == selectedConnection
				? getSelectedConnectionBorder() : getConnectionBorder();
		drawConnectionCurve(context, portX(output), portY(output),
				portX(input), portY(input), connection.getWidth(),
				output.getColor(), input.getColor(), border, connection);
	}

	private void drawConnectionPreview(GLContext2D context) {
		if (fixedGesturePort.getDirection() == GLNodePort.Direction.OUTPUT) {
			Color start = fixedGesturePort.getColor();
			Color end = candidatePort == null ? faded(start)
					: candidatePort.getColor();
			float endX = candidatePort == null ? connectionPointerX
					: portX(candidatePort);
			float endY = candidatePort == null ? connectionPointerY
					: portY(candidatePort);
			drawConnectionCurve(context, portX(fixedGesturePort),
					portY(fixedGesturePort), endX, endY,
					getConnectionWidth(), start, end,
					getConnectionBorder(), null);
		} else {
			Color end = fixedGesturePort.getColor();
			Color start = candidatePort == null ? faded(end)
					: candidatePort.getColor();
			float startX = candidatePort == null ? connectionPointerX
					: portX(candidatePort);
			float startY = candidatePort == null ? connectionPointerY
					: portY(candidatePort);
			drawConnectionCurve(context, startX,
					startY, portX(fixedGesturePort),
					portY(fixedGesturePort), getConnectionWidth(), start, end,
					getConnectionBorder(), null);
		}
	}

	private void drawConnectionCurve(GLContext2D context,
			float x0, float y0, float x1, float y1, float width,
			Color start, Color end, Color border,
			GLNodeConnection mouseTarget) {
		float handleFactor = getConnectionHandleFactor();
		float handle = Math.max(getMinimumConnectionHandleLength(),
				Math.abs(x1 - x0) * handleFactor);
		context.setStroke(new GLStroke(width + 2 * getConnectionBorderWidth())
				.withCap(GLStroke.Cap.ROUND)
				.withJoin(GLStroke.Join.ROUND)
				.withWidthSpace(GLStroke.WidthSpace.SCREEN));
		if (handle == 0) {
			float[] points = { x0, y0, x1, y1 };
			if (mouseTarget == null)
				context.drawPolyline(points, border, GLContext2D.NO_PICK_ID);
			else
				context.drawPolyline(points, border, mouseTarget);
			context.setStroke(connectionStroke(width));
			float firstThirdX = x0 + (x1 - x0) / 3;
			float firstThirdY = y0 + (y1 - y0) / 3;
			float secondThirdX = x0 + (x1 - x0) * 2 / 3;
			float secondThirdY = y0 + (y1 - y0) * 2 / 3;
			context.drawCubicBezier(x0, y0, firstThirdX, firstThirdY,
					secondThirdX, secondThirdY, x1, y1, start, end,
					getConnectionColorInterpolator(), GLContext2D.NO_PICK_ID);
		} else {
			if (mouseTarget == null)
				context.drawCubicBezier(x0, y0, x0 + handle, y0,
						x1 - handle, y1, x1, y1, border,
						GLContext2D.NO_PICK_ID);
			else
				context.drawCubicBezier(x0, y0, x0 + handle, y0,
						x1 - handle, y1, x1, y1, border, mouseTarget);
			context.setStroke(connectionStroke(width));
			context.drawCubicBezier(x0, y0, x0 + handle, y0,
					x1 - handle, y1, x1, y1, start, end,
					getConnectionColorInterpolator(), GLContext2D.NO_PICK_ID);
		}
	}

	private static GLStroke connectionStroke(float width) {
		return new GLStroke(width).withCap(GLStroke.Cap.ROUND)
				.withJoin(GLStroke.Join.ROUND)
				.withWidthSpace(GLStroke.WidthSpace.SCREEN);
	}

	private static float portX(GLNodePort port) {
		return port.getNode().getX() + port.getCenterXInNode();
	}

	private static float portY(GLNodePort port) {
		return port.getNode().getY() + port.getCenterYInNode();
	}

	private static Color faded(Color color) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), 90);
	}
}
