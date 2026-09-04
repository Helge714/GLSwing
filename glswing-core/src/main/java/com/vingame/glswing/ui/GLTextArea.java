package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.Timer;

import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.input.GLEditingHistoryTarget;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.input.GLPointerController;
import com.vingame.glswing.input.GLPointerCursor;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLStroke;

/** Multiline GPU text editor with wrapping, selection, scrolling and local history. */
public class GLTextArea extends GLControl implements GLEditingHistoryTarget {
	private GLTextDocument document;
	private final GLTextDocument.Listener documentListener = this::documentChanged;
	private final GLHistoryManager editingHistory = new GLHistoryManager();
	private final Timer caretTimer;
	private int caret, anchor;
	private float scrollX, scrollY;
	private boolean lineWrap = true;
	private boolean editable = true;
	private GLTextAlignment textAlignment = GLTextAlignment.LEFT;
	private final GLPopupMenu alignmentMenu = new GLPopupMenu();
	private final GLPopupMenu alignmentSubMenu = new GLPopupMenu();
	private final GLPopupMenu formatSubMenu = new GLPopupMenu();
	private final GLActionMap textActions = new GLActionMap();
	private final GLMenuItem alignmentMenuItem;
	private final GLMenuItem formatMenuItem;
	private final GLMenuItem leftItem;
	private final GLMenuItem centerItem;
	private final GLMenuItem rightItem;
	private final GLMenuItem justifiedItem;
	private final GLMenuItem boldItem;
	private final GLMenuItem italicItem;
	private boolean historyEnabled = true;
	private GLTextDocument.Snapshot editBaseline;
	private GLTextStyle typingStyle = GLTextStyle.PLAIN;
	private boolean selecting;
	private boolean draggingScrollBar;
	private float scrollBarDragOffset;
	private int tabSize = 4;
	private boolean applyingHistory;
	private Float verticalNavigationX;
	private GLPointerController pointerController = GLPointerController.NONE;
	private GLTextEditorStyle localStyle;
	private GLTextColorProvider textColorProvider;
	private List<Row> cachedRows;
	private float cachedRowsWidth = Float.NaN;
	private float cachedMaximumRowWidth;
	private GLCornerRadii containerRadii;
	private boolean containerBorderVisible = true;

	public GLTextArea() { this(""); }
	public GLTextArea(String text) { this(new GLTextDocument(text)); }
	public GLTextArea(GLTextDocument document) {
		super(true);
		if (document == null) throw new IllegalArgumentException("Document must not be null");
		caretTimer = new Timer(500, event -> { caretVisible = !caretVisible; invalidateRecording(); });
		caretTimer.setRepeats(true);
		this.document = document;
		document.addDocumentListener(documentListener);
		editBaseline = document.snapshot();
		GLAction leftAction = alignmentAction(GLInputActions.TEXT_ALIGN_LEFT,
				GLUiKeys.TEXT_ALIGN_LEFT, GLTextAlignment.LEFT);
		GLAction centerAction = alignmentAction(GLInputActions.TEXT_ALIGN_CENTER,
				GLUiKeys.TEXT_ALIGN_CENTER, GLTextAlignment.CENTER);
		GLAction rightAction = alignmentAction(GLInputActions.TEXT_ALIGN_RIGHT,
				GLUiKeys.TEXT_ALIGN_RIGHT, GLTextAlignment.RIGHT);
		GLAction justifiedAction = alignmentAction(GLInputActions.TEXT_ALIGN_JUSTIFIED,
				GLUiKeys.TEXT_ALIGN_JUSTIFIED, GLTextAlignment.JUSTIFIED);
		GLAction boldAction = characterStyleAction(GLInputActions.TEXT_TOGGLE_BOLD,
				GLUiKeys.TEXT_FORMAT_BOLD, true);
		GLAction italicAction = characterStyleAction(GLInputActions.TEXT_TOGGLE_ITALIC,
				GLUiKeys.TEXT_FORMAT_ITALIC, false);
		textActions.add(leftAction).add(centerAction).add(rightAction)
				.add(justifiedAction).add(boldAction).add(italicAction);
		leftItem = alignmentSubMenu.addItem(leftAction);
		centerItem = alignmentSubMenu.addItem(centerAction);
		rightItem = alignmentSubMenu.addItem(rightAction);
		justifiedItem = alignmentSubMenu.addItem(justifiedAction);
		boldItem = formatSubMenu.addItem(boldAction);
		italicItem = formatSubMenu.addItem(italicAction);
		alignmentMenuItem = alignmentMenu.addSubMenu("", alignmentSubMenu);
		formatMenuItem = alignmentMenu.addSubMenu("", formatSubMenu);
		updateContextMenuText();
		setIntrinsicPreferredGLSize(new GLSize(420, 240));
	}

	private boolean caretVisible;
	public final GLTextDocument getDocument() { return document; }
	public final void setDocument(GLTextDocument value) {
		if (value == null) throw new IllegalArgumentException("Document must not be null");
		if (document != null) document.removeDocumentListener(documentListener);
		document = value;
		document.addDocumentListener(documentListener);
		invalidateRowLayout();
		caret = anchor = Math.min(caret, document.length());
		editingHistory.reset();
		contentChanged();
	}
	public final String getText() { return document.getText(); }
	public final void setText(String text) { document.setText(text); }
	public final int getCaretPosition() { return caret; }
	public final void setCaretPosition(int value) { moveCaret(value, false); }
	public final int getSelectionStart() { return Math.min(anchor, caret); }
	public final int getSelectionEnd() { return Math.max(anchor, caret); }
	public final void select(int start, int end) {
		if (start < 0 || end < start || end > document.length())
			throw new IndexOutOfBoundsException("Text selection: " + start + ".." + end);
		anchor = start; caret = end; contentChanged();
	}
	public final void selectAll() { anchor = 0; caret = document.length(); contentChanged(); }
	public final boolean isLineWrap() { return lineWrap; }
	public final void setLineWrap(boolean value) { if (lineWrap != value) { lineWrap = value; scrollX = 0; invalidateRowLayout(); contentChanged(); } }
	public final int getTabSize() { return tabSize; }
	public final void setTabSize(int value) { if (value < 1) throw new IllegalArgumentException("Tab size must be positive"); tabSize = value; invalidateRowLayout(); contentChanged(); }
	public final boolean isEditable() { return editable; }
	public final void setEditable(boolean value) {
		if (editable == value) return;
		editable = value;
		textActions.refreshEnabled();
		editableChanged();
	}
	/** Called after the editor's mutability changes. */
	protected void editableChanged() { }
	public final GLTextAlignment getTextAlignment() { return textAlignment; }
	public final GLTextColorProvider getTextColorProvider() { return textColorProvider; }
	public final void setTextColorProvider(GLTextColorProvider value) {
		textColorProvider = value;
		invalidateRecording();
	}
	public final void setTextAlignment(GLTextAlignment value) {
		if (value == null) throw new IllegalArgumentException("Text alignment must not be null");
		if (textAlignment != value) { textAlignment = value; contentChanged(); }
	}
	private void changeTextAlignment(GLTextAlignment value) {
		GLTextAlignment before = textAlignment;
		if (before == value) return;
		setTextAlignment(value);
		GLHistoryManager manager = getGLHistoryManager();
		if (manager != null)
			manager.record("Change text alignment",
					() -> setTextAlignment(before), () -> setTextAlignment(value));
	}
	/** The menu opened by a secondary-button press. Applications may customize it. */
	public GLPopupMenu getContextMenu() {
		updateContextMenuText();
		textActions.refresh();
		return alignmentMenu;
	}
	/** Whether a completed focus session is recorded in the enclosing history. */
	public final boolean isHistoryEnabled() { return historyEnabled; }
	public final void setHistoryEnabled(boolean value) { historyEnabled = value; }
	public final float getHorizontalScrollOffset() { return scrollX; }
	public final float getVerticalScrollOffset() { return scrollY; }
	public final boolean isVerticalScrollBarVisible() {
		return rows(font()).size() * font().getLineHeight() > textViewportHeight();
	}
	public final void setScrollOffset(float x, float y) {
		if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0 || y < 0)
			throw new IllegalArgumentException("Scroll offsets must be finite and non-negative");
		scrollX = x; scrollY = y; clampScroll(); invalidateRecording();
	}
	public final GLHistoryManager getEditingHistoryManager() { return editingHistory; }
	@Override public final boolean handleEditingHistoryKeyPressed(
			KeyEvent event, GLInputMap inputMap) {
		if (!isFocused() || !GLHistoryManager.isHistoryShortcut(event, inputMap))
			return false;
		if (inputMap.matches(GLInputActions.HISTORY_UNDO, event)
				&& !editingHistory.canUndo()) return false;
		if (inputMap.matches(GLInputActions.HISTORY_REDO, event)
				&& !editingHistory.canRedo()) return false;
		return editingHistory.handleKeyPressed(event, inputMap);
	}
	public final void setGLStyle(GLTextEditorStyle style) { if (style == null) throw new IllegalArgumentException("Style must not be null"); localStyle = style; invalidateRowLayout(); contentChanged(); }
	public final void clearGLStyle() { localStyle = null; invalidateRowLayout(); contentChanged(); }
	public final GLTextEditorStyle getGLStyle() { return localStyle != null ? localStyle : getGLTheme().textEditorStyle(); }
	/** Overrides the outer editor shape when it is visually attached to a container. */
	public final void setContainerShape(GLCornerRadii radii, boolean borderVisible) {
		if (radii == null)
			throw new IllegalArgumentException("Editor corner radii must not be null");
		if (radii.equals(containerRadii) && containerBorderVisible == borderVisible) return;
		containerRadii = radii;
		containerBorderVisible = borderVisible;
		invalidateRecording();
	}
	protected final GLCornerRadii containerRadii() {
		return containerRadii == null ? GLCornerRadii.all(getGLStyle().cornerRadius())
				: containerRadii;
	}

	@Override protected void record(GLContext2D context) {
		GLTextEditorStyle style = getGLStyle();
		float width = getWidth(), height = getHeight();
		if (width <= 0 || height <= 0) return;
		Color border = isFocused() ? style.focusedBorderColor() : style.borderColor();
		GLCornerRadii radii = containerRadii();
		if (containerBorderVisible && style.borderWidth() > 0)
			context.fillAndDrawUIRoundRect(0, 0, width, height, radii,
					style.borderWidth(), style.backgroundColor(), border,
					GLStroke.WidthSpace.SCREEN);
		else context.fillUIRoundRect(0, 0, width, height, radii,
				style.backgroundColor());
		GLInsets padding = style.padding();
		drawLeadingDecoration(context, font(), padding);
		float leading = leadingTextInset();
		context.beginClip(padding.left() + leading, padding.top(),
				textViewportWidth(), textViewportHeight());
		GLFont font = font();
		List<Row> rows = rows(font);
		float lineHeight = font.getLineHeight();
		int first = Math.max(0, (int) Math.floor(scrollY / lineHeight));
		int last = Math.min(rows.size(), (int) Math.ceil((scrollY + height) / lineHeight) + 1);
		int caretRow = rowIndexForCaret(rows);
		for (int index = first; index < last; index++) {
			Row row = rows.get(index);
			float available = textViewportWidth();
			float x = padding.left() + leading - scrollX + rowOffset(font, row, available);
			float top = padding.top() + index * lineHeight - scrollY;
			float baseline = top + font.getAscent();
			if (isFocused())
				drawSelection(context, font, row, x, top, lineHeight,
						style.selectionColor());
			drawRow(context, font, row, x, baseline, available, style);
			drawTextDecorations(context, font, row.start, row.end, x, baseline,
					available);
			if (isFocused() && caretVisible && index == caretRow) {
				float caretX = x + rowAdvance(font, row, caret, available);
				context.fillRect(caretX, top, 1, lineHeight, style.caretColor());
			}
		}
		context.endClip();
		drawTrailingDecoration(context, trailingDecorationX(),
				getGLStyle().padding().top(), trailingDecorationWidth(),
				textViewportHeight());
		drawVerticalScrollBar(context, rows.size(), lineHeight);
		// Keep the complete editor bounds pickable independently of glyph coverage,
		// transparent decorations and the shape supplied by a surrounding tab.
		context.addUIHitArea(0, 0, width, height);
	}
	protected void drawTextDecorations(GLContext2D context, GLFont font,
			int rowStart, int rowEnd, float x, float baseline, float available) { }
	/** Additional unwrapped width drawn after a visual row. */
	protected float trailingRowDecorationWidth(int rowStart, int rowEnd) { return 0; }

	private void drawSelection(GLContext2D context, GLFont font, Row row,
			float x, float top, float height, Color color) {
		int start = Math.max(row.start, getSelectionStart());
		int end = Math.min(row.end, getSelectionEnd());
		if (end <= start) return;
		float available = textViewportWidth();
		float left = x + rowAdvance(font, row, start, available);
		float right = x + rowAdvance(font, row, end, available);
		context.fillRect(left, top, Math.max(1, right - left), height, color);
	}

	@Override protected void controlMousePressed(GLMouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON3) {
			GLParent host = popupHost();
			if (host != null) getContextMenu().show(host,
					event.getScreenX(), event.getScreenY());
			event.consume();
			return;
		}
		if (event.getButton() != MouseEvent.BUTTON1 || !event.hasLocalPosition()) return;
		if (trailingDecorationWidth() > 0
				&& event.getLocalX() >= trailingDecorationX()
				&& event.getLocalX() < scrollBarX()
				&& trailingDecorationPressed(event.getLocalY())) {
			event.consume();
			return;
		}
		if (isVerticalScrollBarVisible() && event.getLocalX() >= scrollBarX()) {
			beginScrollBarDrag(event.getLocalY());
			event.consume();
			return;
		}
		selecting = true;
		moveCaret(positionAt(event.getLocalX(), event.getLocalY()),
				getGLInputMap().hasModifiers(GLInputActions.TEXT_EXTEND_SELECTION, event.getModifiersEx()));
	}
	@Override protected void controlMouseDragged(GLMouseEvent event) {
		if (draggingScrollBar && event.hasLocalPosition()) {
			dragScrollBar(event.getLocalY());
			return;
		}
		if (selecting && event.hasLocalPosition()) moveCaret(positionAt(event.getLocalX(), event.getLocalY()), true);
	}
	@Override protected void controlMouseMoved(GLMouseEvent event) {
		if (!event.hasLocalPosition()) return;
		pointerController.setCursor(this, event.getLocalX() >= trailingDecorationX()
						? GLPointerCursor.DEFAULT : GLPointerCursor.TEXT);
	}
	@Override protected void controlMouseReleased(GLMouseEvent event) { selecting = false; draggingScrollBar = false; }
	@Override protected void controlMouseClicked(GLMouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON1 && event.completesDoubleClick()
				&& event.hasLocalPosition())
			selectWordAt(positionAt(event.getLocalX(), event.getLocalY()));
	}
	@Override protected void controlMouseEntered(GLMouseEvent event) {
		pointerController = event.getPointerController();
		controlMouseMoved(event);
	}
	@Override protected void controlMouseExited(GLMouseEvent event) { clearCursor(); }
	@Override public void mouseWheelMoved(GLMouseWheelEvent event) {
		boolean horizontal = !lineWrap && getGLInputMap().hasModifiers(
				GLInputActions.SCROLL_HORIZONTAL, event.getModifiersEx());
		float old = horizontal ? scrollX : scrollY;
		float delta = (float) event.getPreciseWheelRotation()
				* font().getLineHeight() * Math.max(1, event.getScrollAmount());
		if (horizontal) scrollX = Math.max(0, scrollX + delta);
		else scrollY = Math.max(0, scrollY + delta);
		clampScroll();
		if (old != (horizontal ? scrollX : scrollY)) { invalidateRecording(); event.consume(); }
	}

	@Override protected void controlKeyPressed(GLKeyEvent event) {
		boolean extend = getGLInputMap().hasModifiers(GLInputActions.TEXT_EXTEND_SELECTION, event.getModifiersEx());
		boolean command = getGLInputMap().hasModifiers(GLInputActions.TEXT_COMMAND, event.getModifiersEx());
		if (GLHistoryManager.isHistoryShortcut(event.getOriginalEvent(), getGLInputMap())) editingHistory.handleKeyPressed(event.getOriginalEvent(), getGLInputMap());
		else if (textActions.handleKeyPressed(event, getGLInputMap(), this)) return;
		else if (getGLInputMap().matches(GLInputActions.TEXT_SELECT_ALL, event)) selectAll();
		else if (getGLInputMap().matches(GLInputActions.TEXT_COPY, event)) copy();
		else if (getGLInputMap().matches(GLInputActions.TEXT_CUT, event)) { if (editable) { copy(); replaceSelection(""); } }
		else if (getGLInputMap().matches(GLInputActions.TEXT_PASTE, event)) { if (editable) paste(); }
		else if (command && event.getKeyCode() == KeyEvent.VK_LEFT) moveCaret(previousWord(caret), extend);
		else if (command && event.getKeyCode() == KeyEvent.VK_RIGHT) moveCaret(nextWord(caret), extend);
		else if (command && event.getKeyCode() == KeyEvent.VK_HOME) moveCaret(0, extend);
		else if (command && event.getKeyCode() == KeyEvent.VK_END) moveCaret(document.length(), extend);
		else if (getGLInputMap().matches(GLInputActions.TEXT_MOVE_LEFT, event)) moveCaret(previousCharacter(caret), extend);
		else if (getGLInputMap().matches(GLInputActions.TEXT_MOVE_RIGHT, event)) moveCaret(nextCharacter(caret), extend);
		else if (getGLInputMap().matches(GLInputActions.NAVIGATE_UP, event)) moveVertical(-1, extend);
		else if (getGLInputMap().matches(GLInputActions.NAVIGATE_DOWN, event)) moveVertical(1, extend);
		else if (event.getKeyCode() == KeyEvent.VK_PAGE_UP) moveVertical(-visibleLineCount(), extend);
		else if (event.getKeyCode() == KeyEvent.VK_PAGE_DOWN) moveVertical(visibleLineCount(), extend);
		else if (getGLInputMap().matches(GLInputActions.TEXT_MOVE_HOME, event)) moveCaret(currentRow().start, extend);
		else if (getGLInputMap().matches(GLInputActions.TEXT_MOVE_END, event)) moveCaret(currentRow().end, extend);
		else if (getGLInputMap().matches(GLInputActions.TEXT_BACKSPACE, event)) { if (editable) backspace(); }
		else if (getGLInputMap().matches(GLInputActions.TEXT_DELETE, event)) { if (editable) deleteForward(); }
		else if (event.getKeyCode() == KeyEvent.VK_ENTER) { if (editable) replaceSelection("\n"); }
		else if (event.getKeyCode() == KeyEvent.VK_TAB) { if (editable) replaceSelection("\t"); }
		else return;
		event.consume();
	}
	@Override protected void controlKeyTyped(GLKeyEvent event) {
		char value = event.getKeyChar();
		if (!editable || value == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(value)
				|| getGLInputMap().hasModifiers(GLInputActions.TEXT_COMMAND, event.getModifiersEx())
				|| (event.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0) return;
		replaceSelection(Character.toString(value)); event.consume();
	}
	@Override protected void controlFocusChanged(boolean focused) {
		caretVisible = focused;
		if (focused) {
			editBaseline = document.snapshot();
			editingHistory.reset();
			caretTimer.start();
		} else {
			caretTimer.stop();
			commitEditingSession();
		}
		invalidateRecording();
	}
	@Override protected void detached(GLParent formerParent) { caretTimer.stop(); clearCursor(); super.detached(formerParent); }
	@Override protected void resized(float width, float height) { invalidateRowLayout(); clampScroll(); contentChanged(); }
	@Override protected void themeChanged() { if (localStyle == null) { invalidateRowLayout(); contentChanged(); } }
	@Override protected void localizerChanged() { updateContextMenuText(); }
	@Override protected void inputMapChanged() { updateContextMenuText(); }

	protected final void replaceSelection(String inserted) {
		int start = getSelectionStart(), end = getSelectionEnd();
		String removed = document.getText(start, end);
		if (removed.equals(inserted)) return;
		GLTextDocument.Snapshot before = document.snapshot();
		int beforeCaret = caret, beforeAnchor = anchor;
		applyingHistory = true;
		try {
			document.replace(start, end, inserted);
			document.setStyle(start, start + inserted.length(), typingStyle);
		} finally { applyingHistory = false; }
		caret = anchor = start + inserted.length();
		int afterCaret = caret;
		GLTextDocument.Snapshot after = document.snapshot();
		editingHistory.record(inserted.isEmpty() ? "Delete text" : "Insert text",
				() -> applyEdit(before, beforeCaret, beforeAnchor),
				() -> applyEdit(after, afterCaret, afterCaret));
		contentChanged();
	}
	protected record TextReplacement(int start, int end, String text) { }
	/** Applies non-overlapping replacements as one local undo/redo operation. */
	protected final void replaceTextAtomically(List<TextReplacement> replacements,
			int nextCaret) {
		if (replacements == null || replacements.isEmpty()) return;
		List<TextReplacement> ordered = replacements.stream()
				.sorted(java.util.Comparator.comparingInt(TextReplacement::start).reversed())
				.toList();
		int previousStart = document.length() + 1;
		for (TextReplacement edit : ordered) {
			if (edit.text() == null || edit.start() < 0 || edit.end() < edit.start()
					|| edit.end() > document.length() || edit.end() > previousStart)
				throw new IllegalArgumentException("Invalid or overlapping text replacements");
			previousStart = edit.start();
		}
		commitEditingSession();
		GLTextDocument.Snapshot before = document.snapshot();
		int beforeCaret = caret, beforeAnchor = anchor;
		applyingHistory = true;
		try {
			for (TextReplacement edit : ordered) {
				document.replace(edit.start(), edit.end(), edit.text());
				document.setStyle(edit.start(), edit.start() + edit.text().length(), typingStyle);
			}
		} finally { applyingHistory = false; }
		caret = anchor = Math.max(0, Math.min(nextCaret, document.length()));
		GLTextDocument.Snapshot after = document.snapshot();
		int afterCaret = caret;
		editingHistory.record("Apply completion",
				() -> applyEdit(before, beforeCaret, beforeAnchor),
				() -> applyEdit(after, afterCaret, afterCaret));
		contentChanged();
	}
	private void applyEdit(GLTextDocument.Snapshot snapshot, int nextCaret, int nextAnchor) {
		applyingHistory = true;
		try { document.restore(snapshot); } finally { applyingHistory = false; }
		caret = Math.min(nextCaret, document.length()); anchor = Math.min(nextAnchor, document.length()); contentChanged();
	}
	private void toggleCharacterStyle(boolean boldAttribute) {
		int start = getSelectionStart(), end = getSelectionEnd();
		if (start == end) {
			typingStyle = boldAttribute
					? typingStyle.withBold(!typingStyle.bold())
					: typingStyle.withItalic(!typingStyle.italic());
			return;
		}
		commitEditingSession();
		boolean allEnabled = true;
		for (GLTextDocument.StyleRun run : document.getStyleRuns(start, end))
			allEnabled &= boldAttribute ? run.style().bold() : run.style().italic();
		GLTextDocument.Snapshot before = document.snapshot();
		applyingHistory = true;
		try {
			for (GLTextDocument.StyleRun run : document.getStyleRuns(start, end)) {
				GLTextStyle next = boldAttribute
						? run.style().withBold(!allEnabled)
						: run.style().withItalic(!allEnabled);
				document.setStyle(run.start(), run.end(), next);
			}
		} finally { applyingHistory = false; }
		GLTextDocument.Snapshot after = document.snapshot();
		int savedCaret = caret, savedAnchor = anchor;
		GLHistoryManager manager = getGLHistoryManager();
		if (manager == null) manager = editingHistory;
		manager.record(boldAttribute ? "Change bold formatting" : "Change italic formatting",
				() -> applyEdit(before, savedCaret, savedAnchor),
				() -> applyEdit(after, savedCaret, savedAnchor));
		editBaseline = after;
		contentChanged();
	}
	private GLAction alignmentAction(String id, String textKey,
			GLTextAlignment alignment) {
		return GLAction.builder(id).textKey(textKey)
				.enabledWhen(this::isEditable)
				.selectedWhen(() -> textAlignment == alignment)
				.onAction(() -> changeTextAlignment(alignment)).build();
	}
	private GLAction characterStyleAction(String id, String textKey,
			boolean boldAttribute) {
		return GLAction.builder(id).textKey(textKey)
				.enabledWhen(this::isEditable)
				.selectedWhen(() -> isCharacterStyleActive(boldAttribute))
				.onAction(() -> toggleCharacterStyle(boldAttribute)).build();
	}
	private boolean isCharacterStyleActive(boolean boldAttribute) {
		int start = getSelectionStart(), end = getSelectionEnd();
		if (start == end)
			return boldAttribute ? typingStyle.bold() : typingStyle.italic();
		boolean found = false;
		for (GLTextDocument.StyleRun run : document.getStyleRuns(start, end)) {
			found = true;
			if (!(boldAttribute ? run.style().bold() : run.style().italic()))
				return false;
		}
		return found;
	}
	protected final void updateContextMenuText() {
		GLLocalizer localizer = getGLLocalizer();
		alignmentMenuItem.setText(localizer.text(GLUiKeys.TEXT_MENU_ALIGNMENT));
		formatMenuItem.setText(localizer.text(GLUiKeys.TEXT_MENU_FORMAT));
		textActions.refresh();
	}
	protected final String acceleratorText(String action) {
		var bindings = getGLInputMap().getKeyBindings(action);
		return bindings.isEmpty() ? "" : bindings.get(0).toDisplayString();
	}
	@Override public final boolean commitEditingHistory() {
		commitEditingSession();
		return true;
	}
	protected final void commitEditingSession() {
		GLTextDocument.Snapshot before = editBaseline;
		GLTextDocument.Snapshot after = document.snapshot();
		editBaseline = after;
		editingHistory.reset();
		if (!historyEnabled || before == null || before.equals(after)) return;
		GLHistoryManager outer = getGLHistoryManager();
		if (outer != null)
			outer.record("Edit text", () -> applyCommittedText(before),
					() -> applyCommittedText(after));
	}
	private void applyCommittedText(GLTextDocument.Snapshot snapshot) {
		applyingHistory = true;
		try { document.restore(snapshot); } finally { applyingHistory = false; }
		editBaseline = snapshot;
		caret = anchor = Math.min(caret, document.length());
		editingHistory.reset();
		contentChanged();
	}
	private void backspace() { if (caret != anchor) replaceSelection(""); else if (caret > 0) { anchor = previousCharacter(caret); replaceSelection(""); } }
	private void deleteForward() { if (caret != anchor) replaceSelection(""); else if (caret < document.length()) { anchor = nextCharacter(caret); replaceSelection(""); } }
	private void moveCaret(int value, boolean extend) { moveCaret(value, extend, false); }
	private void moveCaret(int value, boolean extend, boolean preserveVerticalX) {
		caret = Math.max(0, Math.min(document.length(), value));
		if (!extend) anchor = caret;
		if (!preserveVerticalX) verticalNavigationX = null;
		caretVisible = true; ensureCaretVisible(); invalidateRecording();
	}
	private void moveVertical(int delta, boolean extend) {
		GLFont font = font(); List<Row> visualRows = rows(font);
		int current = rowIndexForCaret(visualRows);
		Row row = visualRows.get(current);
		float available = textViewportWidth();
		if (verticalNavigationX == null)
			verticalNavigationX = rowOffset(font, row, available)
					+ rowAdvance(font, row, caret, available);
		int next = Math.max(0, Math.min(visualRows.size() - 1, current + delta));
		Row nextRow = visualRows.get(next);
		moveCaret(positionInRow(font, nextRow,
				verticalNavigationX - rowOffset(font, nextRow, available), available),
				extend, true);
	}
	private int visibleLineCount() { return Math.max(1, (int) (getHeight() / Math.max(1, font().getLineHeight()))); }
	private int previousWord(int offset) {
		BreakIterator words = BreakIterator.getWordInstance(Locale.getDefault());
		words.setText(document.getText());
		int result = words.preceding(Math.max(0, offset));
		return result == BreakIterator.DONE ? 0 : result;
	}
	protected void selectWordAt(int offset) {
		String text = document.getText();
		if (text.isEmpty()) return;
		int probe = Math.min(offset, text.length() - 1);
		BreakIterator words = BreakIterator.getWordInstance(Locale.getDefault());
		words.setText(text);
		int start = words.preceding(probe + 1);
		int end = words.following(probe);
		if (start == BreakIterator.DONE) start = 0;
		if (end == BreakIterator.DONE) end = text.length();
		if (start < end && Character.isLetterOrDigit(text.codePointAt(start)))
			select(start, end);
	}
	private int nextWord(int offset) {
		BreakIterator words = BreakIterator.getWordInstance(Locale.getDefault());
		words.setText(document.getText());
		int result = words.following(Math.min(offset, document.length()));
		return result == BreakIterator.DONE ? document.length() : result;
	}
	private int previousCharacter(int offset) {
		BreakIterator characters = BreakIterator.getCharacterInstance(Locale.getDefault());
		characters.setText(document.getText());
		int result = characters.preceding(Math.max(0, offset));
		return result == BreakIterator.DONE ? 0 : result;
	}
	private int nextCharacter(int offset) {
		BreakIterator characters = BreakIterator.getCharacterInstance(Locale.getDefault());
		characters.setText(document.getText());
		int result = characters.following(Math.min(offset, document.length()));
		return result == BreakIterator.DONE ? document.length() : result;
	}
	protected final int positionAt(float localX, float localY) {
		GLFont font = font(); List<Row> rows = rows(font); if (rows.isEmpty()) return 0;
		int rowIndex = Math.max(0, Math.min(rows.size() - 1, (int) ((localY - getGLStyle().padding().top() + scrollY) / font.getLineHeight())));
			Row row = rows.get(rowIndex);
		float available = textViewportWidth();
		float target = localX - getGLStyle().padding().left() - leadingTextInset() + scrollX
				- rowOffset(font, row, available);
		BreakIterator characters = BreakIterator.getCharacterInstance(Locale.getDefault());
		characters.setText(document.getText());
		for (int offset = row.start, next = characters.following(offset);
				offset < row.end; offset = next, next = characters.following(next)) {
			float left = rowAdvance(font, row, offset, available);
			float right = rowAdvance(font, row, Math.min(row.end, next), available);
			if (target < (left + right) / 2) return offset;
		} return row.end;
	}
	private int positionInRow(GLFont font, Row row, float target, float available) {
		BreakIterator characters = BreakIterator.getCharacterInstance(Locale.getDefault());
		characters.setText(document.getText());
		for (int offset = row.start, next = characters.following(offset);
				offset < row.end; offset = next, next = characters.following(next)) {
			float left = rowAdvance(font, row, offset, available);
			float right = rowAdvance(font, row, Math.min(row.end, next), available);
			if (target < (left + right) / 2) return offset;
		}
		return row.end;
	}
	private List<Row> rows(GLFont font) {
		float available = textViewportWidth();
		if (cachedRows != null && Float.compare(cachedRowsWidth, available) == 0)
			return cachedRows;
		List<Row> result = new ArrayList<>();
		for (int line = 0; line < document.getLineCount(); line++) {
			int start = document.getLineStart(line), end = document.getLineEnd(line);
			if (!lineWrap || start == end) { result.add(new Row(start, end, false)); continue; }
			int rowStart = start;
			while (rowStart < end) {
				BreakIterator characters = BreakIterator.getCharacterInstance(Locale.getDefault());
				characters.setText(document.getText());
				int rowEnd = characters.following(rowStart), lastFit = rowEnd;
				int wordBreak = -1;
				while (rowEnd != BreakIterator.DONE && rowEnd <= end
						&& measureRange(rowStart, rowEnd) <= available) {
					lastFit = rowEnd;
					int preceding = document.getText().codePointBefore(rowEnd);
					if (Character.isWhitespace(preceding) || preceding == '-')
						wordBreak = rowEnd;
					rowEnd = characters.following(rowEnd);
				}
				int chosen;
				boolean trailingSeparatorWrap = false;
				if (lastFit > rowStart && lastFit < end
						&& Character.isWhitespace(document.getText().codePointAt(lastFit))) {
					// Exactly the first separator after a fitting word may act as the
					// visual wrap delimiter. Later spaces belong to the continuation.
					String text = document.getText();
					int previous = text.codePointBefore(lastFit);
					if (Character.isWhitespace(previous)) {
						chosen = lastFit;
					} else {
						chosen = lastFit + Character.charCount(text.codePointAt(lastFit));
						trailingSeparatorWrap = true;
					}
				} else {
					chosen = wordBreak > rowStart && lastFit < end ? wordBreak : lastFit;
				}
				chosen = Math.max(rowStart + 1, chosen);
				result.add(new Row(rowStart, chosen, chosen < end)); rowStart = chosen;
				if (trailingSeparatorWrap && chosen == end)
					result.add(new Row(end, end, false));
			}
		}
		cachedRowsWidth = available;
		cachedRows = List.copyOf(result);
		cachedMaximumRowWidth = 0;
		if (!lineWrap)
			for (Row row : cachedRows)
				cachedMaximumRowWidth = Math.max(cachedMaximumRowWidth,
						measureRange(row.start, row.end)
								+ trailingRowDecorationWidth(row.start, row.end));
		return cachedRows;
	}
	protected final void invalidateRowLayout() {
		cachedRows = null;
		cachedRowsWidth = Float.NaN;
		cachedMaximumRowWidth = 0;
	}
	private Row currentRow() { List<Row> value = rows(font()); return value.get(rowIndexForCaret(value)); }
	private int rowIndexForCaret(List<Row> value) {
		for (int index = 0; index < value.size(); index++) {
			Row row = value.get(index);
			if (caret >= row.start && caret < row.end)
				return index;
			if (caret == row.end) {
				boolean softContinuation = index + 1 < value.size()
						&& value.get(index + 1).start == row.end;
				if (!softContinuation)
					return index;
			}
		}
		return Math.max(0, value.size() - 1);
	}
	private void ensureCaretVisible() {
		GLFont font = font(); List<Row> rows = rows(font); int index = 0;
		for (; index < rows.size(); index++) if (caret >= rows.get(index).start && caret <= rows.get(index).end) break;
		float top = index * font.getLineHeight(), extent = Math.max(1, getHeight() - getGLStyle().padding().vertical());
		if (top < scrollY) scrollY = top; else if (top + font.getLineHeight() > scrollY + extent) scrollY = top + font.getLineHeight() - extent;
		if (!lineWrap && index < rows.size()) { float x = measureRange(rows.get(index).start, caret); float width = Math.max(1, getWidth() - getGLStyle().padding().horizontal()); if (x < scrollX) scrollX = x; else if (x > scrollX + width) scrollX = x - width; }
		clampScroll();
	}
	private void clampScroll() {
		GLFont font = font(); List<Row> rows = rows(font);
		scrollY = Math.max(0, Math.min(scrollY, Math.max(0,
				rows.size() * font.getLineHeight() - getHeight() + getGLStyle().padding().vertical())));
		if (lineWrap) scrollX = 0;
		else {
			scrollX = Math.max(0, Math.min(scrollX, Math.max(0,
					cachedMaximumRowWidth - getWidth()
							+ getGLStyle().padding().horizontal())));
		}
	}
	private void documentChanged(GLTextDocument source, GLTextDocument.Change change) {
		invalidateRowLayout();
		if (!applyingHistory) {
			caret = translatedOffset(caret, change);
			anchor = translatedOffset(anchor, change);
			editingHistory.reset();
		}
		caret = Math.min(caret, source.length());
		anchor = Math.min(anchor, source.length());
		contentChanged();
	}
	private static int translatedOffset(int offset, GLTextDocument.Change change) {
		int start = change.offset();
		int removedEnd = start + change.removedText().length();
		if (offset <= start) return offset;
		if (offset <= removedEnd) return start + change.insertedText().length();
		return offset + change.insertedText().length() - change.removedText().length();
	}
	private void contentChanged() {
		textActions.refresh();
		ensureCaretVisible();
		invalidateRecording();
		repaintGL();
	}
	private GLFont font() { try { return getGLFont(getGLStyle().font()); } catch (IllegalStateException error) { return getGLStyle().font().measurementFont(); } }
	private float measure(GLFont font, String value) { return font.measureText(expand(value)); }
	private float rowOffset(GLFont font, Row row, float available) {
		float remaining = Math.max(0, available - measureRange(row.start, row.end));
		return switch (textAlignment) {
			case CENTER -> remaining / 2;
			case RIGHT -> remaining;
			default -> 0;
		};
	}
	private float rowAdvance(GLFont font, Row row, int offset, float available) {
		int bounded = Math.max(row.start, Math.min(row.end, offset));
		float advance = measureRange(row.start, bounded);
		if (textAlignment == GLTextAlignment.JUSTIFIED && row.softWrapped) {
			String rowText = document.getText(row.start, row.end);
			int spaces = spaceCount(rowText);
			if (spaces > 0) {
				float extra = Math.max(0, available - measureRange(row.start, row.end)) / spaces;
				advance += extra * spaceCount(document.getText(row.start, bounded));
			}
		}
		return advance;
	}
	private void drawRow(GLContext2D context, GLFont font, Row row, float x,
			float baseline, float available, GLTextEditorStyle style) {
		String text = document.getText(row.start, row.end);
		Color color = isEnabled() ? style.textColor() : style.disabledTextColor();
		boolean justified = textAlignment == GLTextAlignment.JUSTIFIED
				&& row.softWrapped && spaceCount(text) > 0;
		for (GLTextDocument.StyleRun run : document.getStyleRuns(row.start, row.end)) {
			int start = run.start();
			while (start < run.end()) {
				int end = justified ? start : run.end();
				if (justified) {
					while (start < run.end() && document.getText().charAt(start) == ' ') start++;
					if (start >= run.end()) break;
					end = start;
					while (end < run.end() && document.getText().charAt(end) != ' ') end++;
				}
				int colorEnd = end;
				Color partColor = presentedColor(start, color);
				for (int offset = start + 1; offset < end; offset++)
					if (!presentedColor(offset, color).equals(partColor)) {
						colorEnd = offset;
						break;
					}
				String part = expand(document.getText(start, colorEnd));
				float partX = x + rowAdvance(font, row, start, available);
				GLFont partFont = font(run.style());
				context.drawUITextShadow(partFont, part, partX, baseline,
						getGLTheme().textShadow("textEditor"));
				context.drawUIText(partFont, part, partX, baseline, partColor);
				start = colorEnd;
			}
		}
	}
	private Color presentedColor(int offset, Color fallback) {
		return textColorProvider == null ? fallback
				: textColorProvider.colorAt(document, offset, fallback);
	}
	private static int spaceCount(String value) {
		int count = 0;
		for (int index = 0; index < value.length(); index++)
			if (value.charAt(index) == ' ') count++;
		return count;
	}
	private float textViewportWidth() {
		GLInsets padding = getGLStyle().padding();
		float bar = GLScrollBarStyle.fromTheme(getGLTheme()).thickness();
		return Math.max(1, getWidth() - padding.horizontal()
				- leadingTextInset() - trailingDecorationWidth() - bar - 2);
	}
	protected float leadingTextInset() { return 0; }
	protected void drawLeadingDecoration(GLContext2D context, GLFont font,
			GLInsets padding) { }
	protected float trailingDecorationWidth() { return 0; }
	protected final float trailingDecorationX() {
		return scrollBarX() - trailingDecorationWidth();
	}
	protected void drawTrailingDecoration(GLContext2D context, float x, float y,
			float width, float height) { }
	protected boolean trailingDecorationPressed(float localY) { return false; }
	private float textViewportHeight() {
		return Math.max(1, getHeight() - getGLStyle().padding().vertical());
	}
	private float scrollBarX() {
		GLScrollBarStyle style = GLScrollBarStyle.fromTheme(getGLTheme());
		return getWidth() - getGLStyle().padding().right() - style.thickness();
	}
	private void drawVerticalScrollBar(GLContext2D context, int rowCount,
			float lineHeight) {
		float contentHeight = rowCount * lineHeight;
		float viewport = textViewportHeight();
		if (contentHeight <= viewport) return;
		GLScrollBarStyle style = GLScrollBarStyle.fromTheme(getGLTheme());
		float x = scrollBarX();
		float y = getGLStyle().padding().top();
		float width = style.thickness();
		float trackHeight = viewport;
		context.fillUIRoundRect(x, y, width, trackHeight,
				Math.min(style.cornerRadius(), width / 2), style.track());
		float thumbHeight = scrollBarThumbHeight(contentHeight, viewport, style);
		float travel = Math.max(0, trackHeight - thumbHeight);
		float maximum = Math.max(1, contentHeight - viewport);
		float thumbY = y + travel * scrollY / maximum;
		context.fillUIRoundRect(x + style.trackPadding(),
				thumbY + style.trackPadding(),
				Math.max(1, width - style.trackPadding() * 2),
				Math.max(1, thumbHeight - style.trackPadding() * 2),
				Math.min(style.cornerRadius(), width / 2),
				draggingScrollBar ? style.thumbPressed() : style.thumb());
	}
	private float scrollBarThumbHeight(float contentHeight, float viewport,
			GLScrollBarStyle style) {
		return Math.min(viewport, Math.max(style.minimumThumbLength(),
				viewport * viewport / contentHeight));
	}
	private void beginScrollBarDrag(float localY) {
		GLFont font = font();
		float contentHeight = rows(font).size() * font.getLineHeight();
		float viewport = textViewportHeight();
		GLScrollBarStyle style = GLScrollBarStyle.fromTheme(getGLTheme());
		float thumbHeight = scrollBarThumbHeight(contentHeight, viewport, style);
		float travel = Math.max(0, viewport - thumbHeight);
		float thumbTop = getGLStyle().padding().top()
				+ travel * scrollY / Math.max(1, contentHeight - viewport);
		draggingScrollBar = true;
		if (localY >= thumbTop && localY <= thumbTop + thumbHeight)
			scrollBarDragOffset = localY - thumbTop;
		else {
			scrollBarDragOffset = thumbHeight / 2;
			dragScrollBar(localY);
		}
		invalidateRecording();
	}
	private void dragScrollBar(float localY) {
		GLFont font = font();
		float contentHeight = rows(font).size() * font.getLineHeight();
		float viewport = textViewportHeight();
		GLScrollBarStyle style = GLScrollBarStyle.fromTheme(getGLTheme());
		float thumbHeight = scrollBarThumbHeight(contentHeight, viewport, style);
		float travel = Math.max(0, viewport - thumbHeight);
		float position = Math.max(0, Math.min(travel,
				localY - getGLStyle().padding().top() - scrollBarDragOffset));
		scrollY = travel <= 0 ? 0
				: (contentHeight - viewport) * position / travel;
		invalidateRecording();
		repaintGL();
	}
	protected final GLParent popupHost() {
		GLParent host = getGLParent();
		while (host instanceof com.vingame.glswing.component.GLContainer container
				&& container.getGLParent() != null)
			host = container.getGLParent();
		return host;
	}
	/** Returns the caret's lower edge in root screen coordinates. */
	protected final float[] caretScreenPosition() {
		float[] bounds = getLastRenderedScreenBounds();
		if (bounds == null || getWidth() <= 0 || getHeight() <= 0) return null;
		GLFont font = font();
		List<Row> visualRows = rows(font);
		int rowIndex = rowIndexForCaret(visualRows);
		Row row = visualRows.get(rowIndex);
		float available = textViewportWidth();
		float localX = getGLStyle().padding().left() + leadingTextInset() - scrollX
				+ rowOffset(font, row, available) + rowAdvance(font, row, caret, available);
		float localY = getGLStyle().padding().top() + (rowIndex + 1) * font.getLineHeight()
				- scrollY;
		return new float[] { bounds[0] + localX * bounds[2] / getWidth(),
				bounds[1] + localY * bounds[3] / getHeight() };
	}
	private String expand(String value) { return value.replace("\t", " ".repeat(tabSize)); }
	protected final float measureRange(int start, int end) {
		float width = 0;
		for (GLTextDocument.StyleRun run : document.getStyleRuns(start, end))
			width += font(run.style()).measureText(expand(document.getText(run.start(), run.end())));
		return width;
	}
	private GLFont font(GLTextStyle style) {
		GLFontSpec base = getGLStyle().font();
		int fontStyle = (style.bold() ? GLFont.BOLD : GLFont.PLAIN)
				| (style.italic() ? GLFont.ITALIC : GLFont.PLAIN);
		GLFontSpec spec = new GLFontSpec(base.family(), fontStyle, base.size());
		try { return getGLFont(spec); }
		catch (IllegalStateException error) { return spec.measurementFont(); }
	}
	private void copy() { if (caret == anchor) return; try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(document.getText(getSelectionStart(), getSelectionEnd())), null); } catch (RuntimeException ignored) { } }
	private void paste() { try { Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor); if (value instanceof String text) replaceSelection(text.replace("\r\n", "\n").replace('\r', '\n')); } catch (Exception ignored) { } }
	private void clearCursor() { pointerController.clearCursor(this); pointerController = GLPointerController.NONE; }
	private record Row(int start, int end, boolean softWrapped) { }
}
