package com.vingame.glswing.node;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
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
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLMenuItem;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLUiKeys;

/**
 * Reusable visual node with title bar, content rows and optional edge ports.
 * Public convenience methods hide the internal row representation.
 */
public class GLNode extends GLContainer {
	private final List<Entry> entries = new ArrayList<>();
	private final GLTextEditor titleEditor;
	private GLPopupMenu contextMenu;
	private GLMenuItem editTitleMenuItem;
	private GLMenuItem deleteNodeMenuItem;
	private String title;
	private GLNodeStyle localStyle;
	private GLNodeResizeMode resizeMode = GLNodeResizeMode.BOTH;
	private boolean dragAnywhere;
	private boolean titleEditable = true;
	private boolean titleGradientEnabled;
	private boolean selected;
	private boolean activeSelected;
	private boolean hovered;
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

	public GLNode(String title) {
		this.title = requireTitle(title);
		titleEditor = new GLTextEditor(title);
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

	public void setTitle(String title) {
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
		return contextMenu;
	}

	public void editTitle() {
		if (!titleEditable || titleEditor.isVisible())
			return;
		titleEditor.setText(title);
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
		return entries.stream().map(entry -> entry.content).toList();
	}

	public List<GLNodePort> getPorts() {
		return entries.stream()
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

		float border = style.borderWidth();
		float innerWidth = Math.max(0, width - border * 2);
		float titleHeight = Math.min(style.titleHeight(),
				Math.max(0, height - border));
		if (innerWidth > 0 && titleHeight > 0) {
			float innerRadius = Math.max(0, style.cornerRadius() - border);
			if (titleGradientEnabled)
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
					width - style.contentPadding().horizontal());
			String visibleTitle = GLTextFitter.ellipsize(font, title, available);
			float baseline = Math.max(font.getAscent(),
					(style.titleHeight() - font.getLineHeight()) * 0.5f
							+ font.getAscent());
			context.drawUIText(font, visibleTitle, textX, baseline,
					style.titleText());
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
		if (style.borderWidth() > 0)
			context.drawUIRoundRect(0, 0, getWidth(), getHeight(),
					style.cornerRadius(), style.borderWidth(),
					activeSelected ? style.activeSelectedBorder()
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
		if (getGLParent() instanceof GLNodeEditor editor)
			editor.completeNodeClick(this, event.getModifiersEx());
		if (event.getClickCount() >= 2
				&& event.hasLocalPosition()
				&& event.getLocalY() <= getGLStyle().titleHeight()) {
			editTitle();
			event.consume();
		}
	}

	@Override
	protected void themeChanged() {
		if (localStyle == null)
			styleChanged();
	}

	@Override
	protected void localizerChanged() {
		updateContextMenuText();
	}

	@Override
	public void mouseEntered(GLMouseEvent event) {
		hovered = true;
		updateCursor(event);
	}

	@Override
	public void mouseMoved(GLMouseEvent event) {
		updateCursor(event);
	}

	@Override
	public void mouseExited(GLMouseEvent event) {
		hovered = false;
		if (gesture == Gesture.NONE)
			clearCursor();
	}

	@Override
	public void mousePressed(GLMouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON3) {
			activateFromPort(event, true);
			showContextMenu(event);
			event.consume();
			return;
		}
		if (event.getButton() != MouseEvent.BUTTON1 || !event.hasLocalPosition())
			return;
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
		if (event.getButton() == MouseEvent.BUTTON1)
			finishGesture(event);
	}

	@Override
	public void mouseCaptureLost(GLMouseEvent event) {
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

	void inputPresentationChanged(GLNodePort input) {
		Entry entry = entryFor(input);
		if (entry == null)
			return;
		syncConnectedLabel(entry);
		layoutEntries();
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
		GLFont font = getGLFont(style.titleFont());
		float widest = font.measureText(title) + style.contentPadding().horizontal();
		float rowsHeight = 0;
		for (Entry entry : entries) {
			GLSize preferred = entry.content.getPreferredGLSize();
			widest = Math.max(widest,
					preferred.width() + style.contentPadding().horizontal());
			rowsHeight += preferred.height();
		}
		if (entries.size() > 1)
			rowsHeight += style.rowGap() * (entries.size() - 1);
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
		if (entries.isEmpty())
			return;
		GLNodeStyle style = getGLStyle();
		GLInsets padding = style.contentPadding();
		float contentX = padding.left();
		float contentWidth = Math.max(0, getWidth() - padding.horizontal());
		float y = style.titleHeight() + padding.top();
		float diameter = style.portRadius() * 2;
		for (Entry entry : entries) {
			GLSize preferred = entry.content.getPreferredGLSize();
			float rowHeight = preferred.height();
			entry.content.setBounds(contentX, y, contentWidth, rowHeight);
			if (entry.connectedLabel != null) {
				syncConnectedLabel(entry);
				entry.connectedLabel.setBounds(contentX, y, contentWidth, rowHeight);
			}
			float portY = y + (rowHeight - diameter) * 0.5f;
			if (entry.input != null)
				entry.input.setBounds(-style.portRadius(), portY,
						diameter, diameter);
			if (entry.output != null)
				entry.output.setBounds(getWidth() - style.portRadius(), portY,
						diameter, diameter);
			y += rowHeight + style.rowGap();
		}
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
		titleEditor.setBounds(0, 0, getWidth(),
				Math.min(style.titleHeight() + style.borderWidth(), getHeight()));
	}

	private void finishTitleEditing(boolean commit) {
		if (!titleEditor.isVisible())
			return;
		if (commit)
			setTitle(titleEditor.getText());
		else
			titleEditor.setText(title);
		titleEditor.setVisible(false);
		requestGLFocus(null);
		invalidateRecording();
	}

	private void ensureContextMenu() {
		if (contextMenu != null)
			return;
		contextMenu = new GLPopupMenu();
		editTitleMenuItem = contextMenu.addItem(
				getGLLocalizer().text(GLUiKeys.NODE_EDIT_TITLE),
				event -> editTitle());
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
					getGLLocalizer().text(GLUiKeys.NODE_DELETE));
	}

	private void deleteFromEditor() {
		if (!(getGLParent() instanceof GLNodeEditor editor))
			return;
		if (isSelected())
			editor.removeSelectedNodes();
		else
			editor.removeNode(this);
	}

	private void showContextMenu(GLMouseEvent event) {
		GLParent host = rootParent();
		if (host == null)
			return;
		GLPopupMenu menu = getContextMenu();
		editTitleMenuItem.setEnabled(titleEditable);
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
			editor.endNodeMove(this);
		if (event != null && hovered)
			updateCursor(event);
		else
			clearCursor();
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
