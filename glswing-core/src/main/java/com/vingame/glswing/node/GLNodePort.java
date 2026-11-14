package com.vingame.glswing.node;

import java.awt.Color;
import java.awt.event.MouseEvent;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLStroke;

/** Pickable input or output socket positioned on a {@link GLNode} edge. */
public final class GLNodePort extends GLContainer {
	public enum Direction { INPUT, OUTPUT }
	public enum Shape { CIRCLE, DIAMOND, DOUBLE_DIAMOND }
	private Shape shape = Shape.CIRCLE;
	public Shape getShape() { return shape; }
	public void setShape(Shape shape) {
		java.util.Objects.requireNonNull(shape);
		if (this.shape == shape) return;
		this.shape = shape; invalidateRecording();
	}

	@FunctionalInterface
	public interface ConnectionValidator {
		boolean canConnect(GLNodePort ownPort, GLNodePort otherPort);
	}

	private final String id;
	private final Direction direction;
	private GLNode node;
	private Color localColor;
	private boolean hovered;
    private boolean socketVisible = true;
    public boolean isSocketVisible() { return socketVisible; }
    /** Keeps the model port and its editor row while hiding the connection handle. */
    public void setSocketVisible(boolean visible) {
        if (socketVisible == visible) return;
        socketVisible = visible;
        setVisible(visible);
        if (node != null) node.inputPresentationChanged(this);
    }
    private boolean connectionDashed;
    /** Presentation hint for resource/reference connections, including drag previews. */
    public boolean isConnectionDashed() { return connectionDashed; }
    public void setConnectionDashed(boolean dashed) {
        if (connectionDashed == dashed) return;
        connectionDashed = dashed;
        invalidateRecording();
        if (node != null && node.getGLParent() instanceof GLNodeEditor editor)
            editor.invalidateRecording();
    }
    private String toolTipText;
    public String getToolTipText(){return toolTipText;}
    public void setToolTipText(String text){toolTipText=text==null||text.isBlank()?null:text;}
    @Override public void mouseMoved(GLMouseEvent event){com.vingame.glswing.ui.GLToolTipManager.hover(this,event,toolTipText,null);}
    @Override protected void detached(com.vingame.glswing.component.GLParent parent){com.vingame.glswing.ui.GLToolTipManager.detached(this);super.detached(parent);}
	private boolean connectionCandidate;
	private boolean contentHiddenWhenConnected;
	private int maximumConnections;
	private ConnectionValidator connectionValidator = (own, other) -> true;

	public GLNodePort(String id, Direction direction) {
		if (id == null || id.isBlank())
			throw new IllegalArgumentException("Node port ID must not be blank");
		if (direction == null)
			throw new IllegalArgumentException("Node port direction must not be null");
		this.id = id;
		this.direction = direction;
		maximumConnections = direction == Direction.INPUT ? 1 : Integer.MAX_VALUE;
		setIntrinsicPreferredGLSize(new GLSize(10, 10));
	}

	public static GLNodePort input(String id) {
		return new GLNodePort(id, Direction.INPUT);
	}

	public static GLNodePort output(String id) {
		return new GLNodePort(id, Direction.OUTPUT);
	}

	public String getId() { return id; }
	public Direction getDirection() { return direction; }
	public GLNode getNode() { return node; }
	public float getCenterXInNode() { return getX() + getWidth() * 0.5f; }
	public float getCenterYInNode() { return getY() + getHeight() * 0.5f; }

	@Override
	protected String defaultName() { return id; }

	public int getMaximumConnections() { return maximumConnections; }

	public void setMaximumConnections(int maximumConnections) {
		if (maximumConnections < 1)
			throw new IllegalArgumentException(
					"Maximum connection count must be positive");
		this.maximumConnections = maximumConnections;
	}

    public int getActiveConnectionCount(){
        return node!=null&&node.getGLParent() instanceof GLNodeEditor editor?editor.activeConnectionCount(this):0;
    }
	public int getConnectionCount() {
		return node != null && node.getGLParent() instanceof GLNodeEditor editor
				? editor.connectionCount(this) : 0;
	}

	/**
	 * Returns whether this input replaces its row control with a lightweight name
	 * label while a connection supplies the value.
	 */
	public boolean isContentHiddenWhenConnected() {
		return contentHiddenWhenConnected;
	}

	public void setContentHiddenWhenConnected(boolean hidden) {
		if (direction != Direction.INPUT)
			throw new IllegalStateException(
					"Only input ports can hide connected content");
		if (contentHiddenWhenConnected == hidden)
			return;
		contentHiddenWhenConnected = hidden;
		if (node != null)
			node.inputPresentationChanged(this);
	}

	public boolean isContentHiddenByConnection() {
		return contentHiddenWhenConnected && getActiveConnectionCount() > 0;
	}

	public void setConnectionValidator(ConnectionValidator validator) {
		if (validator == null)
			throw new IllegalArgumentException(
					"Connection validator must not be null");
		connectionValidator = validator;
	}

	public boolean canConnectTo(GLNodePort other) {
		return other != null && other != this && direction != other.direction
				&& (node == null || other.node == null || node != other.node)
				&& connectionValidator.canConnect(this, other);
	}

	public Color getColor() {
		if (localColor != null)
			return localColor;
		GLNodeStyle style = style();
		return direction == Direction.INPUT
				? style.inputPort() : style.outputPort();
	}

	public void setColor(Color color) {
		if (color == null)
			throw new IllegalArgumentException("Node port color must not be null");
		if (color.equals(localColor))
			return;
		localColor = color;
		invalidateRecording();
	}

	public void clearColor() {
		if (localColor == null)
			return;
		localColor = null;
		invalidateRecording();
	}

	@Override
	protected void record(GLContext2D context) {
		GLNodeStyle style = style();
		Color fill = hovered || connectionCandidate
				? style.portHover() : getColor();
		float diameter = Math.min(getWidth(), getHeight());
		if (shape == Shape.DIAMOND || shape == Shape.DOUBLE_DIAMOND) {
			float scale = shape == Shape.DIAMOND
                    ? Math.max(.1f, Math.min(1f, getGLTheme().values().getFloat("node.portDiamondScale", 1f))) : 1f;
            float x = getWidth() / 2, y = getHeight() / 2, radius = diameter / 2 * 1.12f * scale;
			float[] points = {x, y - radius, x + radius, y, x, y + radius, x - radius, y};
			var stroke = context.getStroke();
			try {
				context.fillPolygon(points, fill);
				context.setStroke(new GLStroke(Math.max(1, style.borderWidth())).withWidthSpace(GLStroke.WidthSpace.SCREEN));
				context.drawPolygon(points, style.portBorder());
                if(shape == Shape.DOUBLE_DIAMOND){
                    float inner=radius*Math.max(.15f,Math.min(.65f,getGLTheme().values().getFloat("node.portEventInnerScale",.36f)));
                    context.fillPolygon(new float[]{x,y-inner,x+inner,y,x,y+inner,x-inner,y},style.portBorder());
                }
			} finally { context.setStroke(stroke); }
			return;
		}
		context.fillAndDrawUIRoundRect(0, 0, getWidth(), getHeight(),
				diameter * 0.5f, Math.max(1, style.borderWidth()), fill,
				style.portBorder(), GLStroke.WidthSpace.SCREEN);
	}

	@Override
	public void mouseEntered(GLMouseEvent event) {
		hovered = true;
        com.vingame.glswing.ui.GLToolTipManager.hover(this,event,toolTipText,null);
		invalidateRecording();
	}

	@Override
	public void mouseExited(GLMouseEvent event) {
		hovered = false;
        com.vingame.glswing.ui.GLToolTipManager.leave(this);
		invalidateRecording();
	}

    private com.vingame.glswing.ui.GLPopupMenu contextMenu;
    /** Optional application menu, shared by the socket and its row label. */
    public void setContextMenu(com.vingame.glswing.ui.GLPopupMenu menu){contextMenu=menu;}
    public com.vingame.glswing.ui.GLPopupMenu getContextMenu(){return contextMenu;}
    boolean showContextMenu(GLMouseEvent event){
        if(contextMenu==null)return false;
        var host=getGLParent();
        while(host instanceof com.vingame.glswing.component.GLContainer parent && parent.getGLParent()!=null)host=parent.getGLParent();
        if(host==null)return false;
        com.vingame.glswing.ui.GLToolTipManager.leave(this);
        contextMenu.show(host,event.getScreenX(),event.getScreenY());event.consume();return true;
    }

	@Override
	public void mousePressed(GLMouseEvent event) {
        com.vingame.glswing.ui.GLToolTipManager.leave(this);
        if(event.getButton()==MouseEvent.BUTTON3 && showContextMenu(event))return;
		if (node != null && event.getButton() == MouseEvent.BUTTON1) {
			node.activateFromPort(event, false);
			if (node.getGLParent() instanceof GLNodeEditor editor)
				editor.beginConnectionGesture(this, event);
			event.consume();
		}
	}

	@Override
	public void mouseDragged(GLMouseEvent event) {
		if (node != null && node.getGLParent() instanceof GLNodeEditor editor)
			editor.dragConnectionGesture(this, event);
	}

	@Override
	public void mouseReleased(GLMouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON1 && node != null
				&& node.getGLParent() instanceof GLNodeEditor editor)
			editor.endConnectionGesture(this, event, false);
	}

	@Override
	public void mouseCaptureLost(GLMouseEvent event) {
		if (node != null && node.getGLParent() instanceof GLNodeEditor editor)
			editor.endConnectionGesture(this, event, true);
	}

	@Override
	protected void themeChanged() {
		if (localColor == null)
			invalidateRecording();
	}

	void attachTo(GLNode owner) {
		if (node != null && node != owner)
			throw new IllegalArgumentException(
					"A node port cannot belong to multiple nodes");
		node = owner;
	}

	void detachFrom(GLNode owner) {
		if (node == owner)
			node = null;
	}

	void setConnectionCandidate(boolean candidate) {
		if (connectionCandidate == candidate)
			return;
		connectionCandidate = candidate;
		invalidateRecording();
	}

	void connectionStateChanged() {
		if (node != null)
			node.inputPresentationChanged(this);
	}

	private GLNodeStyle style() {
		return node == null ? GLNodeStyle.fromTheme(getGLTheme())
				: node.getGLStyle();
	}
}
