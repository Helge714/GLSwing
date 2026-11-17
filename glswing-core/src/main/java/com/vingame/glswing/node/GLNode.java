package com.vingame.glswing.node;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLPointerController;
import com.vingame.glswing.input.GLPointerCursor;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.ui.GLTextEditor;
import com.vingame.glswing.ui.GLTextEditorStyle;
import com.vingame.glswing.ui.GLTextFitter;
import com.vingame.glswing.rendering.GLTextShadow;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLMenuItem;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLUiKeys;

/**
 * Reusable visual node with title bar, content rows and optional edge ports.
 * Public convenience methods hide the internal row representation.
 */
public class GLNode extends GLContainer {
	private GLNodeConnectionStatus diagnosticStatus=GLNodeConnectionStatus.NORMAL;
    private String diagnosticMessage;
    public GLNodeConnectionStatus getDiagnosticStatus(){return diagnosticStatus;}
    public String getDiagnosticMessage(){return diagnosticMessage;}
    /** Transient validation decoration; never changes graph or node theme settings. */
    public void setDiagnostic(GLNodeConnectionStatus status,String message){
        java.util.Objects.requireNonNull(status);
        String normalized=message==null||message.isBlank()?null:message;
        if(status==diagnosticStatus&&java.util.Objects.equals(normalized,diagnosticMessage))return;
        diagnosticStatus=status;diagnosticMessage=normalized;
        com.vingame.glswing.ui.GLToolTipManager.leave(this);invalidateRecording();
    }
    public void clearDiagnostic(){setDiagnostic(GLNodeConnectionStatus.NORMAL,null);}
    private java.awt.Color diagnosticColor(){
        return getGLTheme().values().getColor(diagnosticStatus==GLNodeConnectionStatus.ERROR
            ?"nodeEditor.errorConnectionBorder":"nodeEditor.warningConnectionBorder");
    }
    @Override protected void detached(GLParent parent){com.vingame.glswing.ui.GLToolTipManager.detached(this);super.detached(parent);}
	private final CopyOnWriteArrayList<Runnable> activationListeners =
			new CopyOnWriteArrayList<>();
	private final List<Entry> entries = new ArrayList<>();
	private final GLTextEditor titleEditor;
	private GLPopupMenu contextMenu;
	private GLMenuItem editTitleMenuItem;
	private GLMenuItem copyMenuItem;
	private GLMenuItem cutMenuItem;
	private GLMenuItem duplicateMenuItem;
	private GLMenuItem extractCompositeMenuItem;
	private GLMenuItem hideMenuItem;
	private GLMenuItem muteMenuItem;
	private GLMenuItem deleteNodeMenuItem;
	private String title;
    private String defaultTitle, customTitle, titleSuffix="";
    private String titleBadge="";
    private GLNodeConnectionStatus titleBadgeStatus=GLNodeConnectionStatus.NORMAL;
	private float themeWidth,themeHeight;
	private boolean automaticThemeWidth,automaticThemeHeight;
	private GLNodeStyle localStyle;
	private GLNodePortOrder portOrder = GLNodePortOrder.INPUTS_FIRST;
	private GLNodeResizeMode resizeMode = GLNodeResizeMode.BOTH;
	private boolean dragAnywhere;
	private boolean titleEditable = true;
	private boolean titleGradientEnabled;
    private GLNodeAutoLayout.Role autoLayoutRole=GLNodeAutoLayout.Role.NORMAL;
    public GLNodeAutoLayout.Role getAutoLayoutRole(){return autoLayoutRole;}
    public void setAutoLayoutRole(GLNodeAutoLayout.Role role){autoLayoutRole=java.util.Objects.requireNonNull(role);}
    private java.util.function.BiFunction<GLNodeConnection,GLNodeConnection,GLNodePort> connectionReplacementRouter;
    /** Optional destination for a displaced input wire; the editor checks availability and compatibility. */
    public void setConnectionReplacementRouter(java.util.function.BiFunction<GLNodeConnection,GLNodeConnection,GLNodePort> router){connectionReplacementRouter=router;}
    GLNodePort routeReplacedConnection(GLNodeConnection previous,GLNodeConnection incoming){return connectionReplacementRouter==null?null:connectionReplacementRouter.apply(previous,incoming);}
    private boolean groupIndicatorVisible;
    /** Draw a geometric group marker, independent of title text and font glyph coverage. */
    public void setGroupIndicatorVisible(boolean visible){
        if(groupIndicatorVisible==visible)return;groupIndicatorVisible=visible;invalidateRecording();
    }
    public boolean isGroupIndicatorVisible(){return groupIndicatorVisible;}
	private boolean contentHidden;
	private boolean muted;
	private Map<String, String> muteBypassMappings = Map.of();
	private float expandedHeight;
	private boolean selected;
	private boolean activeSelected;
	private boolean hovered;
	private boolean collapseChevronHovered;
	private boolean collapseChevronPressed;
	private boolean consumeChevronClick;
	private Gesture gesture = Gesture.NONE;
	private GLPointerController pointerController = GLPointerController.NONE;
	private float pressLocalX;
	private float pressLocalY;
	private float startX;
	private float startY;
	private float startWidth;
	private float startHeight;
	private float computedMinimumWidth;
	private float computedMinimumHeight;
	private String titleBeforeEditing;

	public GLNode(String title) {
		this.title = requireTitle(title);
        defaultTitle=this.title;
		titleEditor = new GLTextEditor(title);
		titleEditor.setHistoryEnabled(false);
		titleEditor.setVisible(false);
		titleEditor.setCommitOnFocusLost(true);
		titleEditor.addActionListener(event -> finishTitleEditing(true));
		titleEditor.addCancelListener(event -> finishTitleEditing(false));
		addGL(titleEditor);
		updateMetricsAndLayout();
	}

	public String getTitle() {
		return title;
	}

	@Override
	protected String defaultName() {
		return title;
	}

	/** Updates the default label without replacing a user-entered name. */
    public void setTitle(String title) {setDefaultTitle(title);}
    public void setDefaultTitle(String title){defaultTitle=requireTitle(title);refreshTitle();}
    public String getCustomTitle(){return customTitle;}
    /** Null restores the default label. Empty strings are valid custom titles. */
    public void setCustomTitle(String title){customTitle=title;refreshTitle();}
    public void resetTitle(){
        String before=customTitle;if(before==null)return;setCustomTitle(null);
        var manager=getGLHistoryManager();if(manager!=null)manager.record("Reset node title",()->setCustomTitle(before),()->setCustomTitle(null));
    }
    /** Non-editable presentation annotation, never included in the saved name. */
    public void setTitleSuffix(String suffix){titleSuffix=java.util.Objects.requireNonNull(suffix);refreshTitle();}
    /** Transient trailing title badge; reserves its own space even for truncated titles. */
    public void setTitleBadge(String text,GLNodeConnectionStatus status){
        java.util.Objects.requireNonNull(text);java.util.Objects.requireNonNull(status);
        if(titleBadge.equals(text)&&titleBadgeStatus==status)return;
        titleBadge=text;titleBadgeStatus=status;layoutTitleEditor();invalidateRecording();
    }
    public String getTitleBadge(){return titleBadge;}
    public GLNodeConnectionStatus getTitleBadgeStatus(){return titleBadgeStatus;}
    private float titleBadgeWidth(){return titleBadge.isEmpty()?0:getGLStyle().titleFont().measurementFont().measureText(titleBadge)+getGLStyle().contentPadding().right()+12;}
    private void refreshTitle(){applyTitle((customTitle==null?defaultTitle:customTitle)+titleSuffix);}
    private void applyTitle(String title) {
		title = requireTitle(title);
		if (this.title.equals(title))
			return;
		this.title = title;
		if (!titleEditor.isVisible())
			titleEditor.setText(title);
		updateMetricsAndLayout();
		invalidateRecording();
	}

	public boolean isTitleEditable() {
		return titleEditable;
	}

	public void setTitleEditable(boolean titleEditable) {
		this.titleEditable = titleEditable;
		if (!titleEditable && titleEditor.isVisible())
			finishTitleEditing(false);
	}

	public boolean isTitleGradientEnabled() {
		return titleGradientEnabled;
	}

	public void setTitleGradientEnabled(boolean enabled) {
		if (titleGradientEnabled == enabled)
			return;
		titleGradientEnabled = enabled;
		invalidateRecording();
	}

	public boolean isEditingTitle() {
		return titleEditor.isVisible();
	}

	public GLPopupMenu getContextMenu() {
		ensureContextMenu();
		updateContextMenuText();
		return contextMenu;
	}

	public void editTitle() {
		if (!titleEditable || titleEditor.isVisible())
			return;
		titleBeforeEditing = customTitle;
		titleEditor.setText(customTitle==null?defaultTitle:customTitle);
		configureTitleEditor(getGLStyle());
		titleEditor.setVisible(true);
		layoutTitleEditor();
		titleEditor.bringToFront();
		invalidateRecording();
		requestGLFocus(titleEditor);
		titleEditor.selectAll();
	}

	public GLNodeResizeMode getResizeMode() {
		return resizeMode;
	}

	public void setResizeMode(GLNodeResizeMode resizeMode) {
		if (resizeMode == null)
			throw new IllegalArgumentException("Node resize mode must not be null");
		if (this.resizeMode == resizeMode)
			return;
		this.resizeMode = resizeMode;
		repaintGL();
	}

	public boolean isDragAnywhere() {
		return dragAnywhere;
	}

	public void setDragAnywhere(boolean dragAnywhere) {
		this.dragAnywhere = dragAnywhere;
	}

	public boolean isSelected() {
		return selected;
	}

	/** True only for the single primary node in the current selection. */
	public boolean isActiveSelected() {
		return activeSelected;
	}

	public GLNodeStyle getGLStyle() {
		return localStyle != null ? localStyle
				: GLNodeStyle.fromTheme(getGLTheme());
	}

	public void setGLStyle(GLNodeStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Node style must not be null");
		if (localStyle == style)
			return;
		localStyle = style;
		styleChanged();
	}

	public void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		styleChanged();
	}

	/** Returns the visual order used for input and output rows. */
	public GLNodePortOrder getPortOrder() {
		return portOrder;
	}

	/**
	 * Sets the visual order used for port rows. Input/output rows remain stable
	 * relative to other rows of the same kind. OUTPUTS_FIRST moves output rows
	 * to the front and preserves the joint declaration order of all remaining rows.
	 */
	public void setPortOrder(GLNodePortOrder order) {
		if (order == null)
			throw new IllegalArgumentException("Node port order must not be null");
		if (portOrder == order)
			return;
		portOrder = order;
		updateMetricsAndLayout();
	}

	/** Returns whether nonessential, currently unconnected rows are collapsed. */
	public boolean isContentHidden() { return contentHidden; }

	/** Collapses or restores nonessential node rows. */
	public void setContentHidden(boolean hidden) {
		if (contentHidden == hidden) return;
		if (hidden) expandedHeight = getHeight();
		contentHidden = hidden;
		updateMetricsAndLayout();
		float preferred = getPreferredGLSize().height();
		setSize(getWidth(), hidden ? preferred : Math.max(preferred, expandedHeight));
		invalidateRecording();
	}

	/** Keeps a row visible while this node is collapsed. */
	public void setAlwaysVisible(GLContainer content, boolean alwaysVisible) {
		Entry entry = entries.stream().filter(value -> value.content == content)
				.findFirst().orElseThrow(() -> new IllegalArgumentException(
						"Content is not part of this node"));
		setAlwaysVisible(entry, alwaysVisible);
	}

	/** Keeps the row owning a port visible while this node is collapsed. */
	public void setAlwaysVisible(GLNodePort port, boolean alwaysVisible) {
		Entry entry = entryFor(port);
		if (entry == null) throw new IllegalArgumentException("Port is not part of this node");
		setAlwaysVisible(entry, alwaysVisible);
	}

	public boolean isMuted() { return muted; }
	public void setMuted(boolean muted) {
		if (this.muted == muted) return;
		this.muted = muted;
		updateRenderingOpacity();
		invalidateRecording();
	}

	/** Defines input-to-output lines displayed while this node is muted. */
	public void setMuteBypassMappings(Map<String, String> mappings) {
		if (mappings == null) throw new IllegalArgumentException("Bypass mappings required");
		Map<String, String> copy = new java.util.LinkedHashMap<>();
		mappings.forEach((input, output) -> {
			GLNodePort in = findPort(input), out = findPort(output);
			if (in == null || out == null || in.getDirection() != GLNodePort.Direction.INPUT
					|| out.getDirection() != GLNodePort.Direction.OUTPUT)
				throw new IllegalArgumentException("Bypass mapping requires node input and output ports");
			copy.put(input, output);
		});
		muteBypassMappings = Map.copyOf(copy);
		invalidateRecording();
	}

	/** Returns the visual and executable input-to-output mute mappings. */
	public Map<String, String> getMuteBypassMappings() {
		return muteBypassMappings;
	}

	public void addContent(GLContainer content) {
		addEntry(content, null, null);
	}

	public GLNodePort addInput(String id, GLContainer content) {
		GLNodePort input = GLNodePort.input(id);
		addInput(input, content);
		return input;
	}

	public GLNodePort addInput(GLNodePort input, GLContainer content) {
		requireDirection(input, GLNodePort.Direction.INPUT);
		addEntry(content, input, null);
		return input;
	}

	public GLNodePort addOutput(String id, GLContainer content) {
		GLNodePort output = GLNodePort.output(id);
		addOutput(content, output);
		return output;
	}

	public GLNodePort addOutput(GLContainer content, GLNodePort output) {
		requireDirection(output, GLNodePort.Direction.OUTPUT);
		addEntry(content, null, output);
		return output;
	}

	public GLNodePorts addInputOutput(String inputId, GLContainer content,
			String outputId) {
		GLNodePort input = GLNodePort.input(inputId);
		GLNodePort output = GLNodePort.output(outputId);
		return addInputOutput(input, content, output);
	}

	public GLNodePorts addInputOutput(GLNodePort input, GLContainer content,
			GLNodePort output) {
		requireDirection(input, GLNodePort.Direction.INPUT);
		requireDirection(output, GLNodePort.Direction.OUTPUT);
		addEntry(content, input, output);
		return new GLNodePorts(input, output);
	}

	public boolean removeContent(GLContainer content) {
		if (content == null)
			return false;
		for (int index = 0; index < entries.size(); index++) {
			Entry entry = entries.get(index);
			if (entry.content != content)
				continue;
			entries.remove(index);
			removeGL(entry.content);
			if (entry.connectedLabel != null)
				removeGL(entry.connectedLabel);
			removePort(entry.input);
			removePort(entry.output);
			updateMetricsAndLayout();
			return true;
		}
		return false;
	}

	public List<GLContainer> getContents() {
		return orderedEntries().stream().map(entry -> entry.content).toList();
	}

	public List<GLNodePort> getPorts() {
		return orderedEntries().stream()
				.flatMap(entry -> java.util.stream.Stream.of(
						entry.input, entry.output))
				.filter(java.util.Objects::nonNull).toList();
	}

	public GLNodePort findPort(String id) {
		if (id == null)
			return null;
		return getPorts().stream().filter(port -> id.equals(port.getId()))
				.findFirst().orElse(null);
	}

	@Override
	protected void record(GLContext2D context) {
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;
		GLNodeStyle style = getGLStyle();
		context.drawUIRoundRectShadow(0, 0, width, height,
				style.cornerRadius(), style.shadowSoftness(),
				style.shadowOffsetX(), style.shadowOffsetY(), style.shadow());
		context.fillUIRoundRect(0, 0, width, height,
				style.cornerRadius(), style.background());

		float border = Math.max(0, getGLTheme().values().getFloat("node.titleInset", style.borderWidth()));
		float innerWidth = Math.max(0, width - border * 2);
		float titleHeight = Math.min(style.titleHeight(),
				Math.max(0, height - border));
		if (innerWidth > 0 && titleHeight > 0) {
			float innerRadius = Math.max(0, style.cornerRadius() - border);
			if (muted)
				context.fillUIRoundRect(border, border, innerWidth, titleHeight,
						new GLCornerRadii(innerRadius, innerRadius, 0, 0),
						style.background());
			else if (titleGradientEnabled)
				context.fillUIVerticalGradientRoundRect(border, border,
						innerWidth, titleHeight, innerRadius, 0,
						style.titleGradientLight(), style.titleGradientDark());
			else
				context.fillUIRoundRect(border, border, innerWidth, titleHeight,
						new GLCornerRadii(innerRadius, innerRadius, 0, 0),
						style.titleBackground());
		}

		if (!titleEditor.isVisible()) {
			GLFont font = getGLFont(style.titleFont());
			float textX = style.contentPadding().left();
			float available = Math.max(0,
					width - style.contentPadding().horizontal() - titleBadgeWidth());
			if (contentHidden) {
				float hitSize = collapseChevronHitSize();
				drawCollapseChevron(context, textX, hitSize,
						style.titleHeight());
				textX += hitSize;
				available = Math.max(0, available - hitSize);
			}
            if(groupIndicatorVisible){
                float size=Math.min(16,style.titleHeight()*.55f), y=(style.titleHeight()-size)*.5f;
                context.drawUIRoundRect(textX+size*.25f,y,size*.75f,size*.75f,1,1,style.titleText());
                context.drawUIRoundRect(textX,y+size*.25f,size*.75f,size*.75f,1,1,style.titleText());
                textX+=size+5;available=Math.max(0,available-size-5);
            }
			String visibleTitle = GLTextFitter.ellipsize(font, title, available);
			float baseline = Math.max(font.getAscent(),
					(style.titleHeight() - font.getLineHeight()) * 0.5f
							+ font.getAscent());
			GLTextShadow shadow = getGLTheme().textShadow("node");
			context.drawUITextShadow(font, visibleTitle, textX, baseline, shadow);
			context.drawUIText(font, visibleTitle, textX, baseline,
					style.titleText());
		}
        if(!titleBadge.isEmpty()){
            GLFont font=getGLFont(style.titleFont());
            float baseline=Math.max(font.getAscent(),(style.titleHeight()-font.getLineHeight())*.5f+font.getAscent());
            java.awt.Color color=titleBadgeStatus==GLNodeConnectionStatus.NORMAL?style.titleText():getGLTheme().values().getColor(titleBadgeStatus==GLNodeConnectionStatus.ERROR?"nodeEditor.errorConnectionBorder":"nodeEditor.warningConnectionBorder");
            float badgeWidth=font.measureText(titleBadge)+8;
            float badgeHeight=Math.min(style.titleHeight()-4,font.getLineHeight()+2);
            float badgeX=Math.max(0,width-style.contentPadding().right()-badgeWidth),badgeY=(style.titleHeight()-badgeHeight)*.5f;
            java.awt.Color opaque=new java.awt.Color(color.getRed(),color.getGreen(),color.getBlue());
            context.fillUIRoundRect(badgeX,badgeY,badgeWidth,badgeHeight,2,opaque);
            java.awt.Color ink=(color.getRed()*299+color.getGreen()*587+color.getBlue()*114)>140000?java.awt.Color.BLACK:java.awt.Color.WHITE;
            context.drawUIText(font,titleBadge,badgeX+4,baseline,ink);
        }

	}

	@Override
	protected void renderChildren(GLContext2D context) {
		if (getWidth() <= 0 || getHeight() <= 0)
			return;
		List<GLContainer> paintOrder = getGLChildrenInPaintOrder();
		context.beginClip(0, 0, getWidth(), getHeight());
		try {
			for (GLContainer child : paintOrder)
				if (!(child instanceof GLNodePort) && isPresentedChild(child))
					child.renderComponent(context);
		} finally {
			context.endClip();
		}

		GLNodeStyle style = getGLStyle();
		if (style.borderWidth() > 0 || diagnosticStatus!=GLNodeConnectionStatus.NORMAL)
			context.drawUIRoundRect(0, 0, getWidth(), getHeight(),
					style.cornerRadius(), diagnosticStatus==GLNodeConnectionStatus.NORMAL?style.borderWidth():Math.max(2,style.borderWidth()),
					diagnosticStatus!=GLNodeConnectionStatus.NORMAL?diagnosticColor():activeSelected ? style.activeSelectedBorder()
							: selected ? style.selectedBorder() : style.border(),
					GLStroke.WidthSpace.SCREEN);

		for (GLContainer child : paintOrder)
			if (child instanceof GLNodePort)
				child.renderComponent(context);
	}

	@Override
	protected void resized(float width, float height) {
		layoutTitleEditor();
		layoutEntries();
	}

	@Override
	public void mouseClicked(GLMouseEvent event) {
		if (event.getButton() != MouseEvent.BUTTON1)
			return;
		if (consumeChevronClick) {
			consumeChevronClick = false;
			event.consume();
			return;
		}
		if (getGLParent() instanceof GLNodeEditor editor)
			editor.completeNodeClick(this, event.getModifiersEx());
		if (event.completesDoubleClick()
				&& event.hasLocalPosition()
				&& event.getLocalY() <= getGLStyle().titleHeight()) {
			editTitle();
			event.consume();
		} else if (event.completesDoubleClick()) {
			for (Runnable listener : activationListeners) listener.run();
			event.consume();
		}
	}

	/** Invoked by a double-click on the node body outside its editable title. */
	public void addActivationListener(Runnable listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		activationListeners.addIfAbsent(listener);
	}

	public void removeActivationListener(Runnable listener) {
		activationListeners.remove(listener);
	}

	@Override
	protected void themeChanged() {
        var before=getPreferredGLSize();
        themeWidth=getWidth();themeHeight=getHeight();
        automaticThemeWidth=themeWidth<=0||Math.abs(themeWidth-before.width())<.5f;
        automaticThemeHeight=themeHeight<=0||Math.abs(themeHeight-before.height())<.5f;
		updateRenderingOpacity();
		if (localStyle == null)
			styleChanged();
	}

    @Override protected void themeChangedAfterChildren(){
        super.themeChangedAfterChildren();
        updateMetricsAndLayout();
        var preferred=getPreferredGLSize();
        setSize(automaticThemeWidth&&Math.abs(getWidth()-themeWidth)<.5f?preferred.width():Math.max(getWidth(),preferred.width()),
            automaticThemeHeight&&Math.abs(getHeight()-themeHeight)<.5f?preferred.height():Math.max(getHeight(),preferred.height()));
    }

	private void updateRenderingOpacity() {
		float opacity = getGLTheme().values().getFloat(
				muted ? "node.mutedOpacity" : "node.opacity", muted ? .62f : .95f);
		setRenderingOpacity(1);
		setRenderingAlphaMultiplier(Math.max(0, Math.min(1, opacity)));
	}

	@Override
	protected void localizerChanged() {
		updateContextMenuText();
	}

	@Override
	public void mouseEntered(GLMouseEvent event) {
        com.vingame.glswing.ui.GLToolTipManager.hover(this,event,diagnosticMessage,null);
		hovered = true;
		updateCollapseChevronHover(event);
		updateCursor(event);
	}

	@Override
	public void mouseMoved(GLMouseEvent event) {
        com.vingame.glswing.ui.GLToolTipManager.hover(this,event,diagnosticMessage,null);
		updateCollapseChevronHover(event);
		updateCursor(event);
	}

	@Override
	public void mouseExited(GLMouseEvent event) {
        com.vingame.glswing.ui.GLToolTipManager.leave(this);
		hovered = false;
		setCollapseChevronHovered(false);
		if (gesture == Gesture.NONE)
			clearCursor();
	}

	@Override
	public void mousePressed(GLMouseEvent event) {
        com.vingame.glswing.ui.GLToolTipManager.leave(this);
		if (getGLParent() instanceof GLNodeEditor editor
				&& editor.finishPlacementFromClick(event)) return;
        if (event.getButton() == MouseEvent.BUTTON3 && event.hasLocalPosition()) {
            for (Entry entry : shownEntries()) {
                var row=presentedContent(entry);float x=event.getLocalX(),y=event.getLocalY();
                if(x>=row.getX() && x<row.getX()+row.getWidth() && y>=row.getY() && y<row.getY()+row.getHeight()) {
                    var port=entry.input!=null?entry.input:entry.output;
                    if(port!=null && port.showContextMenu(event))return;
                }
            }
        }
		if (event.getButton() == MouseEvent.BUTTON3) {
			activateFromPort(event, true);
			showContextMenu(event);
			event.consume();
			return;
		}
		if (event.getButton() != MouseEvent.BUTTON1 || !event.hasLocalPosition())
			return;
		if (isCollapseChevronHit(event)) {
			collapseChevronPressed = true;
			pointerController = event.getPointerController();
			pointerController.setCursor(this, GLPointerCursor.HAND);
			invalidateRecording();
			event.consume();
			return;
		}
		activateFromPort(event, false);
		if (!selected)
			return;
		Gesture requested = resizeGesture(event.getLocalX(), event.getLocalY());
		if (requested == Gesture.NONE
				&& (event.getLocalY() <= getGLStyle().titleHeight() || dragAnywhere))
			requested = Gesture.MOVE;
		if (requested == Gesture.NONE)
			return;
		gesture = requested;
		pressLocalX = event.getLocalX();
		pressLocalY = event.getLocalY();
		startX = getX();
		startY = getY();
		startWidth = getWidth();
		startHeight = getHeight();
		if (gesture == Gesture.MOVE
				&& getGLParent() instanceof GLNodeEditor editor)
			editor.beginNodeMove(this);
		pointerController = event.getPointerController();
		setGestureCursor(requested);
		event.consume();
	}

	@Override
	public void mouseDragged(GLMouseEvent event) {
		if (gesture == Gesture.NONE || !event.hasLocalPosition()
				|| (event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == 0)
			return;
		float deltaX = event.getLocalX() - pressLocalX;
		float deltaY = event.getLocalY() - pressLocalY;
		if (gesture == Gesture.MOVE) {
			if (getGLParent() instanceof GLNodeEditor editor)
				editor.dragNodeMove(this, deltaX, deltaY);
			else
				setLocation(startX + deltaX, startY + deltaY);
		} else {
			resizeFromGesture(deltaX, deltaY);
		}
		event.consume();
	}

	@Override
	public void mouseReleased(GLMouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON1 && collapseChevronPressed) {
			boolean expand = isCollapseChevronHit(event);
			collapseChevronPressed = false;
			consumeChevronClick = true;
			invalidateRecording();
			if (expand) expandFromChevron();
			event.consume();
			return;
		}
		if (event.getButton() == MouseEvent.BUTTON1)
			finishGesture(event);
	}

	@Override
	public void mouseCaptureLost(GLMouseEvent event) {
		if (collapseChevronPressed) {
			collapseChevronPressed = false;
			invalidateRecording();
		}
		finishGesture(null);
	}

	void activateFromPort() {
		if (getGLParent() instanceof GLNodeEditor editor)
			editor.activate(this);
	}

	void activateFromPort(GLMouseEvent event, boolean contextClick) {
		if (getGLParent() instanceof GLNodeEditor editor)
			editor.activate(this, event == null ? 0 : event.getModifiersEx(),
					contextClick);
	}

	void setSelectedFromEditor(boolean selected) {
		setSelectionStateFromEditor(selected, selected);
	}

	void setSelectionStateFromEditor(boolean selected, boolean active) {
		if (active && !selected)
			throw new IllegalArgumentException(
					"An active node must also be selected");
		if (this.selected == selected && activeSelected == active)
			return;
		this.selected = selected;
		activeSelected = active;
		invalidateRecording();
	}

	boolean setDragLocation(float x, float y) {
		return setLocationDuringPointerCapture(x, y);
	}

	void inputPresentationChanged(GLNodePort input) {
		Entry entry = entryFor(input);
		if (entry == null)
			return;
        float previousMinimum = computedMinimumHeight;
        syncConnectedLabel(entry);
        updateMetricsAndLayout();
        if (entry.portAnchor != null && getHeight() > 0)
            setSize(getWidth(), Math.max(computedMinimumHeight, getHeight() + computedMinimumHeight - previousMinimum));
        repaintAndRebuildPicking(this);
	}

	private void addEntry(GLContainer content, GLNodePort input,
			GLNodePort output) {
		if (content == null)
			throw new IllegalArgumentException("Node content must not be null");
		if (entries.stream().anyMatch(entry -> entry.content == content))
			throw new IllegalArgumentException("Node content was already added");
		if (input != null && containsPort(input)
				|| output != null && containsPort(output))
			throw new IllegalArgumentException("Node port was already added");
		if (input != null)
			input.attachTo(this);
		if (output != null)
			output.attachTo(this);
		GLLabel connectedLabel = input == null ? null
				: new GLLabel(content.getName());
		if (input == null && output != null && content instanceof GLLabel label)
			label.setAlignment(GLLabel.Alignment.RIGHT);
		if (connectedLabel != null)
            connectedLabel.setVerticalAlignment(GLLabel.VerticalAlignment.CENTER);
        if (content instanceof GLLabel label)
            label.setVerticalAlignment(GLLabel.VerticalAlignment.CENTER);
        Entry entry = new Entry(content, input, output, connectedLabel);
		entries.add(entry);
		addGL(content);
		if (connectedLabel != null)
			addGL(connectedLabel);
		if (input != null)
			addGL(input);
		if (output != null)
			addGL(output);
		updateMetricsAndLayout();
	}

	private boolean containsPort(GLNodePort port) {
		return entries.stream().anyMatch(entry ->
				entry.input == port || entry.output == port);
	}

	private void removePort(GLNodePort port) {
		if (port == null)
			return;
		removeGL(port);
		port.detachFrom(this);
	}

	private void updateMetricsAndLayout() {
		GLNodeStyle style = getGLStyle();
		float widest = style.contentPadding().horizontal();
		float rowsHeight = 0;
		List<Entry> shown = shownEntries();
		for (Entry entry : shown) {
			GLSize preferred = rowPreferredSize(entry);
			widest = Math.max(widest,
					preferred.width() + style.contentPadding().horizontal());
			rowsHeight += preferred.height();
		}
		if (shown.size() > 1)
			rowsHeight += style.rowGap() * (shown.size() - 1);
		computedMinimumWidth = Math.max(style.minimumWidth(), widest);
		computedMinimumHeight = style.titleHeight()
				+ style.contentPadding().top() + rowsHeight
				+ style.contentPadding().bottom();
		setIntrinsicPreferredGLSize(new GLSize(computedMinimumWidth,
				computedMinimumHeight));
		updateVisualOverflow(style);
		layoutEntries();
	}

	private void layoutEntries() {
		List<Entry> shown = shownEntries();
		for (Entry entry : entries) setEntryVisible(entry, shown.contains(entry));
		if (shown.isEmpty()) return;
		GLNodeStyle style = getGLStyle();
		GLInsets padding = style.contentPadding();
		float contentX = padding.left();
		float contentWidth = Math.max(0, getWidth() - padding.horizontal());
		float y = style.titleHeight() + padding.top();
		float diameter = style.portRadius() * 2;
		float growTotal = 0;
		for (Entry entry : shown) growTotal += entry.verticalGrow;
		float extra = Math.max(0, getHeight() - computedMinimumHeight);
		for (Entry entry : shown) {
			GLSize preferred = rowPreferredSize(entry);
			float rowHeight = preferred.height() + (growTotal > 0 ? extra * entry.verticalGrow / growTotal : 0);
			entry.content.setBounds(contentX, y, contentWidth, rowHeight);
			if (entry.connectedLabel != null) {
				syncConnectedLabel(entry);
				entry.connectedLabel.setBounds(contentX, y, contentWidth, rowHeight);
			}
			float center = entry.portAnchor != null && presentedContent(entry) == entry.content
                    ? entry.portAnchor.getY() + entry.portAnchor.getHeight() * .5f : rowHeight * .5f;
            float portY = y + center - diameter * .5f;
			if (entry.input != null)
				entry.input.setBounds(-style.portRadius(), portY,
						diameter, diameter);
			if (entry.output != null)
				entry.output.setBounds(getWidth() - style.portRadius(), portY,
						diameter, diameter);
			y += rowHeight + style.rowGap();
		}
	}

    /** Anchors a compound row to one direct child. When connected, the row collapses
     * to that child's preferred height. Pass null to restore the full-row anchor. */
    public void setContentPortAnchor(GLContainer content, GLContainer anchor) {
        var entry = entries.stream().filter(e -> e.content == content).findFirst().orElseThrow();
        if (anchor != null && anchor.getGLParent() != content)
            throw new IllegalArgumentException("Port anchor must be a direct child of the row");
        entry.portAnchor = anchor;
        updateMetricsAndLayout();
    }

    private GLSize rowPreferredSize(Entry entry) {
        var size = entry.content.getPreferredGLSize();
        return entry.portAnchor != null && presentedContent(entry) != entry.content
                ? new GLSize(size.width(), entry.portAnchor.getPreferredGLSize().height()) : size;
    }

	private List<Entry> shownEntries() {
		return orderedEntries().stream().filter(this::isEntryShown).toList();
	}

	private boolean isEntryShown(Entry entry) {
		if (!entry.explicitVisible && (entry.input == null || entry.input.getConnectionCount() == 0)
				&& (entry.output == null || entry.output.getConnectionCount() == 0)) return false;
		return !contentHidden || entry.alwaysVisible
				|| entry.input != null && entry.input.getConnectionCount() > 0
				|| entry.output != null && entry.output.getConnectionCount() > 0;
	}

	private void setEntryVisible(Entry entry, boolean visible) {
		entry.content.setVisible(visible);
		if (entry.connectedLabel != null) entry.connectedLabel.setVisible(visible);
		if (entry.input != null) entry.input.setVisible(visible && entry.input.isSocketVisible());
		if (entry.output != null) entry.output.setVisible(visible && entry.output.isSocketVisible());
	}

	private void setAlwaysVisible(Entry entry, boolean value) {
		if (entry.alwaysVisible == value) return;
		entry.alwaysVisible = value;
		updateMetricsAndLayout();
	}

    /** Changes row visibility without replacing controls or interrupting pointer capture. Wired ports remain visible. */
    public void setContentVisible(GLContainer content, boolean visible) {
        var entry=entries.stream().filter(e->e.content==content).findFirst().orElseThrow();
        if(entry.explicitVisible==visible) return;
        entry.explicitVisible=visible;
        updateMetricsAndLayout(); invalidateRecording();
    }

    /** Distributes surplus node height among opted-in rows, leaving preferred sizes unchanged. */
    public void setContentVerticalGrow(GLContainer content, float weight) {
        if (!Float.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Non-negative grow weight required");
        var entry = entries.stream().filter(e -> e.content == content).findFirst().orElseThrow();
        if (entry.verticalGrow == weight) return;
        entry.verticalGrow = weight;
        layoutEntries(); invalidateRecording();
    }


	private List<Entry> orderedEntries() {
		if (portOrder == GLNodePortOrder.DECLARATION_ORDER || entries.size() < 2)
			return entries;
		List<Entry> ordered = new ArrayList<>(entries);
		ordered.sort(java.util.Comparator.comparingInt(this::rowOrder));
		return ordered;
	}

	private int rowOrder(Entry entry) {
		boolean input = entry.input != null;
		boolean output = entry.output != null;
		if (portOrder == GLNodePortOrder.OUTPUTS_FIRST) return output ? 0 : 1;
		if (input && output) return 1;
		if (!input && !output) return 2;
		boolean first = portOrder == GLNodePortOrder.INPUTS_FIRST ? input : output;
		return first ? 0 : 3;
	}

	private boolean isPresentedChild(GLContainer child) {
		for (Entry entry : entries) {
			if (child == entry.content)
				return presentedContent(entry) == child;
			if (child == entry.connectedLabel)
				return presentedContent(entry) == child;
		}
		return true;
	}

	private GLContainer presentedContent(Entry entry) {
		if (entry.connectedLabel != null && entry.input != null
				&& entry.input.isContentHiddenByConnection()) {
			syncConnectedLabel(entry);
			return entry.connectedLabel;
		}
		return entry.content;
	}

	private void syncConnectedLabel(Entry entry) {
		if (entry.connectedLabel == null)
			return;
		String name = entry.content.getName();
		if (!entry.connectedLabel.getText().equals(name))
			entry.connectedLabel.setText(name);
	}

	private Entry entryFor(GLNodePort port) {
		for (Entry entry : entries)
			if (entry.input == port || entry.output == port)
				return entry;
		return null;
	}

	private void updateVisualOverflow(GLNodeStyle style) {
		float left = Math.max(style.portRadius(),
				style.shadowSoftness() - style.shadowOffsetX());
		float right = Math.max(style.portRadius(),
				style.shadowSoftness() + style.shadowOffsetX());
		float top = Math.max(style.portRadius(),
				style.shadowSoftness() - style.shadowOffsetY());
		float bottom = Math.max(style.portRadius(),
				style.shadowSoftness() + style.shadowOffsetY());
		setVisualOverflow(new GLInsets(Math.max(0, top), Math.max(0, left),
				Math.max(0, bottom), Math.max(0, right)));
	}

	private void styleChanged() {
		configureTitleEditor(getGLStyle());
		updateMetricsAndLayout();
		for (Entry entry : entries) {
			if (entry.input != null)
				entry.input.invalidateRecording();
			if (entry.output != null)
				entry.output.invalidateRecording();
		}
		invalidateRecording();
	}

	private void configureTitleEditor(GLNodeStyle nodeStyle) {
		GLTextEditorStyle base = getGLTheme().textEditorStyle();
		GLTextEditorStyle editorStyle = base.withFont(nodeStyle.titleFont())
				.withColors(base.backgroundColor(), nodeStyle.titleText(),
						nodeStyle.titleText(), base.selectionColor(),
						nodeStyle.titleText())
				.withGeometry(nodeStyle.cornerRadius(), 0,
						nodeStyle.titleHeight(),
						Math.max(1, getWidth()),
						new GLInsets(0, nodeStyle.contentPadding().left(), 0,
								nodeStyle.contentPadding().right()))
				.withOverflowFadeWidth(0);
		titleEditor.setGLStyle(editorStyle);
		titleEditor.setContainerShape(new GLCornerRadii(
				nodeStyle.cornerRadius(), nodeStyle.cornerRadius(), 0, 0), false);
	}

	private void layoutTitleEditor() {
		GLNodeStyle style = getGLStyle();
		titleEditor.setBounds(0, 0, Math.max(0,getWidth()-titleBadgeWidth()),
				Math.min(style.titleHeight() + style.borderWidth(), getHeight()));
	}

	private void finishTitleEditing(boolean commit) {
		if (!titleEditor.isVisible())
			return;
		String before = titleBeforeEditing;
		if (commit)
			setCustomTitle(before==null&&titleEditor.getText().equals(defaultTitle)?null:titleEditor.getText());
		else
			titleEditor.setText(title);
		titleEditor.setVisible(false);
		titleBeforeEditing = null;
		requestGLFocus(null);
		invalidateRecording();
		if (commit && !java.util.Objects.equals(before,customTitle)) {
			String after = customTitle;
			var manager = getGLHistoryManager();
			if (manager != null)
				manager.record("Edit node title", () -> setCustomTitle(before),
						() -> setCustomTitle(after));
		}
	}

	private void ensureContextMenu() {
		if (contextMenu != null)
			return;
		contextMenu = new GLPopupMenu();
		editTitleMenuItem = contextMenu.addItem(
				getGLLocalizer().text(GLUiKeys.NODE_EDIT_TITLE),
				event -> editTitle());
		contextMenu.addItem(getGLLocalizer().text("glswing.node.resetTitle"),event->resetTitle());
        contextMenu.addSeparator();
		copyMenuItem = contextMenu.addItem(getGLLocalizer().text(GLUiKeys.NODE_COPY),
				event -> invokeEditingAction(GLNodeEditingActions::copy));
		cutMenuItem = contextMenu.addItem(getGLLocalizer().text(GLUiKeys.NODE_CUT),
				event -> invokeEditingAction(GLNodeEditingActions::cut));
		duplicateMenuItem = contextMenu.addItem(
				getGLLocalizer().text(GLUiKeys.NODE_DUPLICATE),
				event -> invokeEditingAction(GLNodeEditingActions::duplicate));
		extractCompositeMenuItem = contextMenu.addItem(
				getGLLocalizer().text(GLUiKeys.NODE_EXTRACT_COMPOSITE),
				event -> invokeEditingAction(GLNodeEditingActions::extractComposite));
		contextMenu.addSeparator();
		hideMenuItem = contextMenu.addItem(getGLLocalizer().text(GLUiKeys.NODE_HIDE),
				event -> invokeEditingAction(GLNodeEditingActions::toggleHidden));
		muteMenuItem = contextMenu.addItem(getGLLocalizer().text(GLUiKeys.NODE_MUTE),
				event -> invokeEditingAction(GLNodeEditingActions::toggleMuted));
		contextMenu.addSeparator();
		deleteNodeMenuItem = contextMenu.addItem(
				getGLLocalizer().text(GLUiKeys.NODE_DELETE),
				event -> deleteFromEditor());
	}

	private void updateContextMenuText() {
		if (editTitleMenuItem != null)
			editTitleMenuItem.setText(
					getGLLocalizer().text(GLUiKeys.NODE_EDIT_TITLE));
		if (deleteNodeMenuItem != null)
			deleteNodeMenuItem.setText(
					getGLLocalizer().text(deleteMenuKey()));
		if (copyMenuItem != null) copyMenuItem.setText(getGLLocalizer().text(GLUiKeys.NODE_COPY));
		if (cutMenuItem != null) cutMenuItem.setText(getGLLocalizer().text(GLUiKeys.NODE_CUT));
		if (duplicateMenuItem != null) duplicateMenuItem.setText(
				getGLLocalizer().text(GLUiKeys.NODE_DUPLICATE));
		if (extractCompositeMenuItem != null) extractCompositeMenuItem.setText(
				getGLLocalizer().text(GLUiKeys.NODE_EXTRACT_COMPOSITE));
		if (hideMenuItem != null) hideMenuItem.setText(getGLLocalizer().text(
				contentHidden ? GLUiKeys.NODE_UNHIDE : GLUiKeys.NODE_HIDE));
		if (muteMenuItem != null) muteMenuItem.setText(getGLLocalizer().text(
				muted ? GLUiKeys.NODE_UNMUTE : GLUiKeys.NODE_MUTE));
		updateContextMenuAccelerators();
	}

	private void updateContextMenuAccelerators() {
		setAccelerator(editTitleMenuItem, GLInputActions.NODE_EDIT_TITLE);
		setAccelerator(copyMenuItem, GLInputActions.NODE_COPY);
		setAccelerator(cutMenuItem, GLInputActions.NODE_CUT);
		setAccelerator(duplicateMenuItem, GLInputActions.NODE_DUPLICATE);
		setAccelerator(extractCompositeMenuItem, GLInputActions.NODE_EXTRACT_COMPOSITE);
		setAccelerator(hideMenuItem, GLInputActions.NODE_TOGGLE_HIDDEN);
		setAccelerator(muteMenuItem, GLInputActions.NODE_TOGGLE_MUTED);
		setAccelerator(deleteNodeMenuItem, GLInputActions.NODE_DELETE);
	}

	private void setAccelerator(GLMenuItem item, String action) {
		if (item == null) return;
		var bindings = getGLInputMap().getKeyBindings(action);
		item.setAcceleratorText(bindings.isEmpty() ? ""
				: bindings.get(0).toDisplayString());
	}

	@Override protected void inputMapChanged() {
		updateContextMenuAccelerators();
	}

	private String deleteMenuKey() {
		return getGLParent() instanceof GLNodeEditor editor
				&& isSelected() && editor.getSelectedNodes().size() > 1
						? GLUiKeys.NODE_DELETE_MULTIPLE
						: GLUiKeys.NODE_DELETE;
	}

	private void deleteFromEditor() {
		if (!(getGLParent() instanceof GLNodeEditor editor))
			return;
		if (editor.getEditingActions() != null) {
			editor.getEditingActions().delete();
			return;
		}
		if (isSelected())
			editor.removeSelectedNodesFromUser();
		else
			editor.removeNodeFromUser(this);
	}

	private void invokeEditingAction(
			java.util.function.Consumer<GLNodeEditingActions> action) {
		if (getGLParent() instanceof GLNodeEditor editor
				&& editor.getEditingActions() != null)
			action.accept(editor.getEditingActions());
	}

	private void showContextMenu(GLMouseEvent event) {
		GLParent host = rootParent();
		if (host == null)
			return;
		GLPopupMenu menu = getContextMenu();
		editTitleMenuItem.setEnabled(titleEditable);
		GLNodeEditingActions actions = getGLParent() instanceof GLNodeEditor editor
				? editor.getEditingActions() : null;
		copyMenuItem.setEnabled(actions != null && actions.canCopy());
		cutMenuItem.setEnabled(actions != null && actions.canCut());
		duplicateMenuItem.setEnabled(actions != null && actions.canDuplicate());
		extractCompositeMenuItem.setEnabled(actions != null
				&& actions.canExtractComposite());
		hideMenuItem.setEnabled(actions != null && actions.canToggleHidden());
		muteMenuItem.setEnabled(actions != null && actions.canToggleMuted());
		deleteNodeMenuItem.setEnabled(actions == null || actions.canDelete());
		menu.show(host, event.getScreenX(), event.getScreenY());
	}

	private GLParent rootParent() {
		GLParent current = getGLParent();
		while (current instanceof GLContainer container
				&& container.getGLParent() != null)
			current = container.getGLParent();
		return current;
	}

	private Gesture resizeGesture(float x, float y) {
		GLNodeStyle style = getGLStyle();
		float handle = style.resizeHandleSize();
		boolean horizontal = resizeMode.allowsHorizontal();
		boolean vertical = resizeMode.allowsVertical();
		boolean left = horizontal && x <= handle;
		boolean right = horizontal && x >= getWidth() - handle;
		boolean top = vertical && y <= handle;
		boolean bottom = vertical && y >= getHeight() - handle;
		if (left && top) return Gesture.RESIZE_TOP_LEFT;
		if (right && top) return Gesture.RESIZE_TOP_RIGHT;
		if (left && bottom) return Gesture.RESIZE_BOTTOM_LEFT;
		if (right && bottom) return Gesture.RESIZE_BOTTOM_RIGHT;
		if (left) return Gesture.RESIZE_LEFT;
		if (right) return Gesture.RESIZE_RIGHT;
		if (top) return Gesture.RESIZE_TOP;
		if (bottom) return Gesture.RESIZE_BOTTOM;
		return Gesture.NONE;
	}

	private void resizeFromGesture(float deltaX, float deltaY) {
		float minimumWidth = Math.max(computedMinimumWidth,
				getMinimumGLSize().width());
		float minimumHeight = Math.max(computedMinimumHeight,
				getMinimumGLSize().height());
		float maximumWidth = getMaximumGLSize().width();
		float maximumHeight = getMaximumGLSize().height();
		float width = startWidth;
		float height = startHeight;
		float x = startX;
		float y = startY;
		if (gesture.left) {
			width = clamp(startWidth - deltaX, minimumWidth, maximumWidth);
			x = startX + startWidth - width;
		} else if (gesture.right) {
			width = clamp(startWidth + deltaX, minimumWidth, maximumWidth);
		}
		if (gesture.top) {
			height = clamp(startHeight - deltaY, minimumHeight, maximumHeight);
			y = startY + startHeight - height;
		} else if (gesture.bottom) {
			height = clamp(startHeight + deltaY, minimumHeight, maximumHeight);
		}
		setBounds(x, y, width, height);
	}

	private void updateCursor(GLMouseEvent event) {
		if (!event.hasLocalPosition() || gesture != Gesture.NONE)
			return;
		pointerController = event.getPointerController();
		if (isCollapseChevronHit(event)) {
			pointerController.setCursor(this, GLPointerCursor.HAND);
			return;
		}
		Gesture resize = resizeGesture(event.getLocalX(), event.getLocalY());
		if (resize != Gesture.NONE) {
			setGestureCursor(resize);
		} else if (event.getLocalY() <= getGLStyle().titleHeight()
				|| dragAnywhere) {
			pointerController.setCursor(this, GLPointerCursor.MOVE);
		} else {
			clearCursor();
		}
	}

	private void drawCollapseChevron(GLContext2D context, float hitX,
			float hitSize, float titleHeight) {
		float size = Math.max(3, getGLTheme().values().getFloat(
				"node.collapseChevronSize", 7));
		float centerX = hitX + hitSize * .5f;
		float centerY = titleHeight * .5f;
		float half = size * .5f;
		var values = getGLTheme().values();
		java.awt.Color color = values.getColor(
				collapseChevronHovered || collapseChevronPressed
						? "node.collapseChevronHover" : "node.collapseChevron",
				getGLStyle().titleText());
		GLStroke previous = context.getStroke();
		try {
			context.setStroke(new GLStroke(Math.max(.5f, values.getFloat(
					"node.collapseChevronWidth", 1.5f)))
					.withCap(GLStroke.Cap.ROUND)
					.withJoin(GLStroke.Join.ROUND)
					.withWidthSpace(GLStroke.WidthSpace.SCREEN));
			context.drawUILine(centerX - half * .45f, centerY - half,
					centerX + half * .45f, centerY, color);
			context.drawUILine(centerX + half * .45f, centerY,
					centerX - half * .45f, centerY + half, color);
		} finally {
			context.setStroke(previous);
		}
	}

	private float collapseChevronHitSize() {
		return Math.max(12, getGLTheme().values().getFloat(
				"node.collapseChevronHitSize", 20));
	}

	private boolean isCollapseChevronHit(GLMouseEvent event) {
		if (!contentHidden || !event.hasLocalPosition()) return false;
		float start = getGLStyle().contentPadding().left();
		return event.getLocalX() >= start
				&& event.getLocalX() <= start + collapseChevronHitSize()
				&& event.getLocalY() >= 0
				&& event.getLocalY() <= getGLStyle().titleHeight();
	}

	private void updateCollapseChevronHover(GLMouseEvent event) {
		setCollapseChevronHovered(isCollapseChevronHit(event));
	}

	private void setCollapseChevronHovered(boolean value) {
		if (collapseChevronHovered == value) return;
		collapseChevronHovered = value;
		invalidateRecording();
	}

	private void expandFromChevron() {
		if (!contentHidden) return;
		if (getGLParent() instanceof GLNodeEditor editor) {
			GLNodeEditingActions actions = editor.getEditingActions();
			if (actions != null && actions.canToggleHidden()) {
				editor.activate(this, 0, false);
				actions.toggleHidden();
				return;
			}
		}
		setContentHidden(false);
	}

	private void setGestureCursor(Gesture gesture) {
		GLPointerCursor cursor;
		if (gesture == Gesture.MOVE)
			cursor = GLPointerCursor.MOVE;
		else if (gesture.left && gesture.top || gesture.right && gesture.bottom)
			cursor = GLPointerCursor.DIAGONAL_NW_SE_RESIZE;
		else if (gesture.right && gesture.top || gesture.left && gesture.bottom)
			cursor = GLPointerCursor.DIAGONAL_NE_SW_RESIZE;
		else if (gesture.left || gesture.right)
			cursor = GLPointerCursor.HORIZONTAL_RESIZE;
		else
			cursor = GLPointerCursor.VERTICAL_RESIZE;
		pointerController.setCursor(this, cursor);
	}

	private void finishGesture(GLMouseEvent event) {
		Gesture finished = gesture;
		gesture = Gesture.NONE;
		if (finished == Gesture.MOVE
				&& getGLParent() instanceof GLNodeEditor editor)
			editor.endNodeMove(this, event != null);
		else if (finished != Gesture.NONE)
			recordBoundsChange(finished == Gesture.MOVE
					? "Move node" : "Resize node");
		if (event != null && hovered)
			updateCursor(event);
		else
		clearCursor();
	}

	private void recordBoundsChange(String name) {
		float beginX = startX;
		float beginY = startY;
		float beginWidth = startWidth;
		float beginHeight = startHeight;
		float endX = getX();
		float endY = getY();
		float endWidth = getWidth();
		float endHeight = getHeight();
		if (beginX == endX && beginY == endY && beginWidth == endWidth
				&& beginHeight == endHeight)
			return;
		var manager = getGLHistoryManager();
		if (manager != null)
			manager.record(name,
					() -> setBounds(beginX, beginY, beginWidth, beginHeight),
					() -> setBounds(endX, endY, endWidth, endHeight));
	}

	private void clearCursor() {
		pointerController.clearCursor(this);
		pointerController = GLPointerController.NONE;
	}

	private static void requireDirection(GLNodePort port,
			GLNodePort.Direction expected) {
		if (port == null)
			throw new IllegalArgumentException("Node port must not be null");
		if (port.getDirection() != expected)
			throw new IllegalArgumentException("Expected a node "
					+ expected.name().toLowerCase() + " port");
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(value, maximum));
	}

	private static String requireTitle(String title) {
		if (title == null)
			throw new IllegalArgumentException("Node title must not be null");
		return title;
	}

	private static final class Entry {
		private final GLContainer content;
		private final GLNodePort input;
		private final GLNodePort output;
		private final GLLabel connectedLabel;
		private boolean alwaysVisible;
		private boolean explicitVisible = true;
		private float verticalGrow;
        private GLContainer portAnchor;

		private Entry(GLContainer content, GLNodePort input,
				GLNodePort output, GLLabel connectedLabel) {
			this.content = content;
			this.input = input;
			this.output = output;
			this.connectedLabel = connectedLabel;
		}
	}

	private enum Gesture {
		NONE(false, false, false, false),
		MOVE(false, false, false, false),
		RESIZE_LEFT(true, false, false, false),
		RESIZE_RIGHT(false, true, false, false),
		RESIZE_TOP(false, false, true, false),
		RESIZE_BOTTOM(false, false, false, true),
		RESIZE_TOP_LEFT(true, false, true, false),
		RESIZE_TOP_RIGHT(false, true, true, false),
		RESIZE_BOTTOM_LEFT(true, false, false, true),
		RESIZE_BOTTOM_RIGHT(false, true, false, true);

		private final boolean left;
		private final boolean right;
		private final boolean top;
		private final boolean bottom;

		Gesture(boolean left, boolean right, boolean top, boolean bottom) {
			this.left = left;
			this.right = right;
			this.top = top;
			this.bottom = bottom;
		}
	}
}
