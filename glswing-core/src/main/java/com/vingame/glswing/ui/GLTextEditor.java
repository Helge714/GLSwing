package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLEditingHistoryTarget;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLPointerController;
import com.vingame.glswing.input.GLPointerCursor;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRenderParameters;
import com.vingame.glswing.rendering.GLStroke;

/** Reusable single-line GPU text editor with selection and validation. */
public class GLTextEditor extends GLControl implements GLEditingHistoryTarget {
	public static final long DEFAULT_HISTORY_COALESCE_MILLIS = 750;

	public enum ValidationFailureMode {
		/** Leaves invalid text in place until it is corrected or cancelled. */
		KEEP_INVALID,
		/** Restores the last valid text and briefly flashes the invalid border. */
		RESTORE_LAST_VALID
	}

	private final CopyOnWriteArrayList<ActionListener> actionListeners =
			new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<ActionListener> cancelListeners =
			new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<ActionListener> validationFailureListeners =
			new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<ChangeListener> textChangeListeners =
			new CopyOnWriteArrayList<>();
	private final GLRenderParameters backgroundParameters = new GLRenderParameters();
	private final GLRenderParameters textParameters = new GLRenderParameters();
	private final GLRenderParameters selectionParameters = new GLRenderParameters();
	private final GLRenderParameters caretParameters = new GLRenderParameters();
	private final Timer caretTimer;
	private final Timer validationFeedbackTimer;
	private final GLHistoryManager editingHistory = new GLHistoryManager();

	private String text;
	private String editBaseline;
	private String lastValidText;
	private int caret;
	private int anchor;
	private float scrollX;
	private boolean selecting;
	private boolean invalid;
	private boolean ending;
	private boolean commitOnFocusLost = true;
	private Long historyCoalesceMillis;
	private EditGroup activeEditGroup;
	private boolean historyEnabled = true;
	private GLCornerRadii containerRadii;
	private boolean containerBorderVisible = true;
	private Predicate<String> inputFilter = GLTextInputFormats.ANY;
	private Predicate<String> inputVerifier = value -> true;
	private ValidationFailureMode validationFailureMode =
			ValidationFailureMode.KEEP_INVALID;
	private GLPointerController pointerController = GLPointerController.NONE;
	private GLTextEditorStyle localStyle;
	private GLTextEditorStyle appliedStyle;

	public GLTextEditor() {
		this("");
	}

	public GLTextEditor(String text) {
		super(true);
		this.text = requireText(text);
		editBaseline = text;
		lastValidText = text;
		caret = anchor = text.length();
		caretTimer = new Timer(500, event -> {
			caretParameters.setOpacity(
					caretParameters.getOpacity() > 0 ? 0 : 1);
			repaintGL();
		});
		caretTimer.setRepeats(true);
		validationFeedbackTimer = new Timer(450, event -> setInvalid(false));
		validationFeedbackTimer.setRepeats(false);
		caretParameters.setOpacity(0);
		updatePresentation(true);
	}

	public final String getText() {
		return text;
	}

	@Override
	protected String defaultName() {
		return text;
	}

	public final void setText(String text) {
		text = requireText(text);
		if (this.text.equals(text))
			return;
		activeEditGroup = null;
		this.text = text;
		caret = Math.min(caret, text.length());
		anchor = Math.min(anchor, text.length());
		if (inputVerifier.test(text))
			lastValidText = text;
		clearValidationFeedback();
		contentChanged();
	}

	public final int getCaretPosition() {
		return caret;
	}

	public final void setCaretPosition(int position) {
		moveCaret(position, false);
	}

	public final int getSelectionStart() {
		return Math.min(anchor, caret);
	}

	public final int getSelectionEnd() {
		return Math.max(anchor, caret);
	}

	public final void selectAll() {
		activeEditGroup = null;
		anchor = 0;
		caret = text.length();
		ensureCaretVisible();
		showCaret();
		invalidateRecording();
	}

	/** Restricts intermediate edits without replacing final validation. */
	public final void setInputFilter(Predicate<String> filter) {
		if (filter == null)
			throw new IllegalArgumentException("Input filter must not be null");
		inputFilter = filter;
	}

	public final Predicate<String> getInputFilter() {
		return inputFilter;
	}

	public final void setInputVerifier(Predicate<String> verifier) {
		if (verifier == null)
			throw new IllegalArgumentException("Input verifier must not be null");
		inputVerifier = verifier;
		boolean valid = verifier.test(text);
		if (valid)
			lastValidText = text;
		setInvalid(!valid);
	}

	public final Predicate<String> getInputVerifier() {
		return inputVerifier;
	}

	public final ValidationFailureMode getValidationFailureMode() {
		return validationFailureMode;
	}

	public final void setValidationFailureMode(ValidationFailureMode mode) {
		if (mode == null)
			throw new IllegalArgumentException(
					"Validation failure mode must not be null");
		validationFailureMode = mode;
	}

	public final int getValidationFeedbackDuration() {
		return validationFeedbackTimer.getInitialDelay();
	}

	public final void setValidationFeedbackDuration(int milliseconds) {
		if (milliseconds < 0)
			throw new IllegalArgumentException(
					"Validation feedback duration must not be negative");
		validationFeedbackTimer.setInitialDelay(milliseconds);
		validationFeedbackTimer.setDelay(milliseconds);
	}

	public final String getLastValidText() {
		return lastValidText;
	}

	public final boolean isInvalid() {
		return invalid;
	}

	public final void setInvalid(boolean invalid) {
		if (this.invalid == invalid)
			return;
		this.invalid = invalid;
		updateColors();
	}

	public final boolean isCommitOnFocusLost() {
		return commitOnFocusLost;
	}

	public final void setCommitOnFocusLost(boolean commitOnFocusLost) {
		this.commitOnFocusLost = commitOnFocusLost;
	}

	/** Whether successful commits create undoable document-history entries. */
	public final boolean isHistoryEnabled() {
		return historyEnabled;
	}

	public final void setHistoryEnabled(boolean historyEnabled) {
		this.historyEnabled = historyEnabled;
	}

	public final boolean hasUncommittedChanges() {
		return !text.equals(editBaseline);
	}

	/** Temporary undo/redo history for the current editing session. */
	public final GLHistoryManager getEditingHistoryManager() {
		return editingHistory;
	}

	public final long getHistoryCoalesceMillis() {
		if (historyCoalesceMillis != null) return historyCoalesceMillis;
		double configured = getGLInputMap().getValue(
				GLInputActions.TEXT_HISTORY_COALESCE_MILLIS,
				DEFAULT_HISTORY_COALESCE_MILLIS);
		return Math.max(0, Math.round(configured));
	}

	/** Overrides the input-map cooldown; zero disables edit coalescing. */
	public final void setHistoryCoalesceMillis(long milliseconds) {
		if (milliseconds < 0)
			throw new IllegalArgumentException("History cooldown must not be negative");
		historyCoalesceMillis = milliseconds;
		activeEditGroup = null;
	}

	/** Restores the cooldown inherited from the nearest input map. */
	public final void clearHistoryCoalesceMillis() {
		historyCoalesceMillis = null;
		activeEditGroup = null;
	}

	/** Handles undo/redo inside the active editing session. */
	@Override
	public final boolean handleEditingHistoryKeyPressed(KeyEvent event,
			GLInputMap inputMap) {
		if (!isFocused() || !GLHistoryManager.isHistoryShortcut(event, inputMap))
			return false;
		activeEditGroup = null;
		editingHistory.handleKeyPressed(event, inputMap);
		return true;
	}

	/** Overrides the outer editor shape without changing its reusable style. */
	public final void setContainerShape(GLCornerRadii radii,
			boolean borderVisible) {
		if (radii == null)
			throw new IllegalArgumentException("Editor corner radii must not be null");
		if (radii.equals(containerRadii)
				&& containerBorderVisible == borderVisible)
			return;
		containerRadii = radii;
		containerBorderVisible = borderVisible;
		invalidateRecording();
	}

	public final void addActionListener(ActionListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Action listener must not be null");
		actionListeners.addIfAbsent(listener);
	}

	public final void removeActionListener(ActionListener listener) {
		actionListeners.remove(listener);
	}

	public final void addCancelListener(ActionListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Cancel listener must not be null");
		cancelListeners.addIfAbsent(listener);
	}

	public final void removeCancelListener(ActionListener listener) {
		cancelListeners.remove(listener);
	}

	public final void addValidationFailureListener(ActionListener listener) {
		if (listener == null)
			throw new IllegalArgumentException(
					"Validation failure listener must not be null");
		validationFailureListeners.addIfAbsent(listener);
	}

	public final void removeValidationFailureListener(ActionListener listener) {
		validationFailureListeners.remove(listener);
	}

	/** Notifies whenever the editable text changes, including programmatic changes. */
	public final void addTextChangeListener(ChangeListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Text change listener must not be null");
		textChangeListeners.addIfAbsent(listener);
	}

	public final void removeTextChangeListener(ChangeListener listener) {
		textChangeListeners.remove(listener);
	}

	public final boolean commit() {
		if (ending)
			return !invalid;
		if (!inputVerifier.test(text)) {
			if (validationFailureMode
					== ValidationFailureMode.RESTORE_LAST_VALID) {
				ending = true;
				try {
					text = lastValidText;
					caret = anchor = text.length();
					contentChanged();
					showValidationFailure(true);
					fireValidationFailed();
					resetEditingHistory();
					requestGLFocus(null);
				} finally {
					ending = false;
				}
				return false;
			}
			showValidationFailure(false);
			return false;
		}
		clearValidationFeedback();
		String before = editBaseline;
		String after = text;
		ending = true;
		try {
			ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
					text, System.currentTimeMillis(), 0);
			for (ActionListener listener : actionListeners)
				listener.actionPerformed(event);
			editBaseline = text;
			lastValidText = text;
			if (historyEnabled && !before.equals(after)) {
				var manager = getGLHistoryManager();
				if (manager != null)
					manager.record("Edit text", () -> applyCommittedText(before),
							() -> applyCommittedText(after));
			}
			resetEditingHistory();
			return true;
		} finally {
			ending = false;
		}
	}

	public final void cancel() {
		if (ending)
			return;
		ending = true;
		try {
			text = editBaseline;
			caret = anchor = text.length();
			clearValidationFeedback();
			contentChanged();
			ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
					"cancel", System.currentTimeMillis(), 0);
			for (ActionListener listener : cancelListeners)
				listener.actionPerformed(event);
			resetEditingHistory();
		} finally {
			ending = false;
		}
	}

	public final void setGLStyle(GLTextEditorStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Text editor style must not be null");
		if (localStyle == style)
			return;
		localStyle = style;
		updatePresentation(true);
	}

	public final void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		updatePresentation(true);
	}

	public final GLTextEditorStyle getGLStyle() {
		return localStyle != null ? localStyle : getGLTheme().textEditorStyle();
	}

	@Override
	protected void record(GLContext2D context) {
		GLTextEditorStyle style = getGLStyle();
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;
		GLFont font = getGLFont(style.font());
		GLInsets padding = style.padding();
		GLCornerRadii radii = containerRadii == null
				? GLCornerRadii.all(style.cornerRadius()) : containerRadii;
		context.withParameters(backgroundParameters, () -> {
			if (containerBorderVisible && style.borderWidth() > 0) {
				Color borderColor = invalid ? style.invalidBorderColor()
						: isFocused() ? style.focusedBorderColor()
								: style.borderColor();
				context.fillAndDrawUIRoundRect(0, 0, width, height, radii,
						style.borderWidth(), Color.WHITE, borderColor,
						GLStroke.WidthSpace.SCREEN);
			} else
				context.fillUIRoundRect(0, 0, width, height, radii, Color.WHITE);
		});

		float clipInset = style.borderWidth() > 0
				? style.borderWidth() + 1 : 0;
		context.beginLayer(0, 0, width, height, clipInset);
		float textOrigin = padding.left() - scrollX;
		float baseline = (height - font.getLineHeight()) * 0.5f + font.getAscent();
		if (anchor != caret) {
			float selectionStart = textOrigin
					+ font.measureText(text.substring(0, getSelectionStart()));
			float selectionEnd = textOrigin
					+ font.measureText(text.substring(0, getSelectionEnd()));
			float clippedStart = Math.max(padding.left(), selectionStart);
			float clippedEnd = Math.min(width - padding.right(), selectionEnd);
			if (clippedEnd > clippedStart)
				context.withParameters(selectionParameters, () ->
						context.fillUIRoundRect(clippedStart,
								baseline - font.getAscent(),
								clippedEnd - clippedStart, font.getLineHeight(),
								1, Color.WHITE));
		}

		context.drawUITextShadow(font, text, textOrigin, baseline,
				getGLTheme().textShadow("textEditor"));
		context.withParameters(textParameters, () ->
				context.drawUIText(font, text, textOrigin, baseline, Color.WHITE));
		float caretX = textOrigin
				+ font.measureText(text.substring(0, caret));
		if (caretX >= padding.left() - 1 && caretX <= width - padding.right() + 1)
			context.withParameters(caretParameters, () ->
					context.fillUIRoundRect(caretX, baseline - font.getAscent(),
							1, font.getLineHeight(), 0, Color.WHITE));

		float availableWidth = Math.max(0, width - padding.horizontal());
		float textWidth = font.measureText(text);
		float fadeWidth = Math.min(style.overflowFadeWidth(),
				Math.max(0, width * 0.5f - clipInset));
		if (fadeWidth > 0 && scrollX > 0.01f)
			drawOverflowFade(context, style, clipInset,
					clipInset + fadeWidth, height, true);
		if (fadeWidth > 0 && textWidth - scrollX > availableWidth + 0.01f)
			drawOverflowFade(context, style,
					width - clipInset - fadeWidth,
					width - clipInset, height, false);
		context.endLayer();
	}

	private static void drawOverflowFade(GLContext2D context,
			GLTextEditorStyle style, float x0, float x1, float height,
			boolean leftEdge) {
		Color opaque = style.backgroundColor();
		Color transparent = new Color(opaque.getRed(), opaque.getGreen(),
				opaque.getBlue(), 0);
		if (leftEdge) {
			// The framebuffer scissor rounds its left edge outward. Cover that
			// possible physical-pixel sliver before starting the soft gradient.
			context.fillUIRoundRect(x0 - 2, 0, 2.5f, height, 0, opaque);
		}
		Color inner = leftEdge ? transparent : opaque;
		Color outer = leftEdge ? opaque : transparent;
		context.fillPolygon(new float[] {
				x0, 0, x1, 0, x1, height, x0, height
		}, new Color[] { outer, inner, inner, outer });
	}

	@Override
	protected void resized(float width, float height) {
		ensureCaretVisible();
		invalidateRecording();
	}

	@Override
	protected void controlMouseEntered(GLMouseEvent event) {
		installTextCursor(event.getPointerController());
	}

	@Override
	protected void controlMouseExited(GLMouseEvent event) {
		restoreCursor();
	}

	@Override
	protected void controlMousePressed(GLMouseEvent event) {
		if (!isEnabled() || event.getButton() != MouseEvent.BUTTON1
				|| !event.hasLocalPosition())
			return;
		selecting = true;
		boolean extend = getGLInputMap().hasModifiers(
				GLInputActions.TEXT_EXTEND_SELECTION, event.getModifiersEx());
		moveCaret(positionAt(event.getLocalX()), extend);
	}

	@Override
	protected void controlMouseDragged(GLMouseEvent event) {
		if (selecting && event.hasLocalPosition())
			moveCaret(positionAt(event.getLocalX()), true);
	}

	@Override
	protected void controlMouseReleased(GLMouseEvent event) {
		selecting = false;
	}

	@Override
	protected void controlKeyPressed(GLKeyEvent event) {
		boolean extend = getGLInputMap().hasModifiers(
				GLInputActions.TEXT_EXTEND_SELECTION, event.getModifiersEx());
		if (getGLInputMap().matches(GLInputActions.TEXT_SELECT_ALL, event))
			selectAll();
		else if (getGLInputMap().matches(GLInputActions.TEXT_COPY, event))
			copySelection();
		else if (getGLInputMap().matches(GLInputActions.TEXT_CUT, event))
			cutSelection();
		else if (getGLInputMap().matches(GLInputActions.TEXT_PASTE, event))
			pasteClipboard();
		else if (getGLInputMap().matches(GLInputActions.TEXT_MOVE_LEFT, event))
			moveCaret(caret - 1, extend);
		else if (getGLInputMap().matches(GLInputActions.TEXT_MOVE_RIGHT, event))
			moveCaret(caret + 1, extend);
		else if (getGLInputMap().matches(GLInputActions.TEXT_MOVE_HOME, event))
			moveCaret(0, extend);
		else if (getGLInputMap().matches(GLInputActions.TEXT_MOVE_END, event))
			moveCaret(text.length(), extend);
		else if (getGLInputMap().matches(GLInputActions.TEXT_BACKSPACE, event))
			backspace();
		else if (getGLInputMap().matches(GLInputActions.TEXT_DELETE, event))
			deleteForward();
		else if (getGLInputMap().matches(GLInputActions.TEXT_COMMIT, event))
			commit();
		else if (getGLInputMap().matches(GLInputActions.CANCEL, event))
			cancel();
		else
			return;
			event.consume();
	}

	@Override
	protected void controlKeyTyped(GLKeyEvent event) {
		char character = event.getKeyChar();
		int modifiers = event.getModifiersEx();
		if (character == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(character)
				|| getGLInputMap().hasModifiers(GLInputActions.TEXT_COMMAND, modifiers)
				|| (modifiers & InputEvent.ALT_DOWN_MASK) != 0)
			return;
		replaceSelection(Character.toString(character), EditKind.INSERT);
		event.consume();
	}

	@Override
	protected void controlFocusChanged(boolean focused) {
		if (focused) {
			editBaseline = text;
			resetEditingHistory();
			showCaret();
			caretTimer.start();
		} else {
			caretTimer.stop();
			caretParameters.setOpacity(0);
			if (!ending && commitOnFocusLost && !commit())
				cancel();
		}
		updateColors();
	}

	@Override
	protected void controlStateChanged(GLControlState oldState,
			GLControlState newState) {
		updateColors();
	}

	@Override
	protected void detached(GLParent formerParent) {
		caretTimer.stop();
		validationFeedbackTimer.stop();
		restoreCursor();
		super.detached(formerParent);
	}

	@Override
	protected void themeChanged() {
		if (localStyle == null && !getGLStyle().equals(appliedStyle))
			updatePresentation(true);
	}

	private void moveCaret(int position, boolean extend) {
		activeEditGroup = null;
		position = Math.max(0, Math.min(text.length(), position));
		if (!extend)
			anchor = position;
		caret = position;
		ensureCaretVisible();
		showCaret();
		invalidateRecording();
	}

	private void installTextCursor(GLPointerController controller) {
		restoreCursor();
		pointerController = controller == null
				? GLPointerController.NONE : controller;
		pointerController.setCursor(this, GLPointerCursor.TEXT);
	}

	private void restoreCursor() {
		pointerController.clearCursor(this);
		pointerController = GLPointerController.NONE;
	}

	private int positionAt(float localX) {
		GLFont font = measurementFont(getGLStyle().font());
		float target = localX - getGLStyle().padding().left() + scrollX;
		if (target <= 0)
			return 0;
		for (int index = 1; index <= text.length(); index++) {
			float previous = font.measureText(text.substring(0, index - 1));
			float current = font.measureText(text.substring(0, index));
			if (target < (previous + current) * 0.5f)
				return index - 1;
		}
		return text.length();
	}

	private void backspace() {
		if (anchor != caret)
			replaceSelection("", EditKind.REPLACE);
		else if (caret > 0) {
			TextState before = textState();
			anchor = caret - 1;
			replaceSelection("", EditKind.BACKSPACE, before);
		}
	}

	private void deleteForward() {
		if (anchor != caret)
			replaceSelection("", EditKind.REPLACE);
		else if (caret < text.length()) {
			TextState before = textState();
			anchor = caret + 1;
			replaceSelection("", EditKind.DELETE, before);
		}
	}

	private void replaceSelection(String replacement, EditKind kind) {
		replaceSelection(replacement, kind, textState());
	}

	private void replaceSelection(String replacement, EditKind kind,
			TextState before) {
		int start = getSelectionStart();
		int end = getSelectionEnd();
		String candidate = text.substring(0, start) + replacement
				+ text.substring(end);
		if (!inputFilter.test(candidate)) {
			showValidationFailure(true);
			return;
		}
		text = candidate;
		caret = anchor = start + replacement.length();
		clearValidationFeedback();
		contentChanged();
		recordEditingChange(before, textState(), kind);
	}

	private void recordEditingChange(TextState before, TextState after,
			EditKind kind) {
		if (!isFocused() || before.equals(after) || editingHistory.isApplying()) return;
		long now = System.nanoTime();
		long cooldownNanos = TimeUnit.MILLISECONDS.toNanos(
				getHistoryCoalesceMillis());
		if (activeEditGroup != null && activeEditGroup.kind == kind
				&& kind.coalescible && cooldownNanos > 0
				&& now - activeEditGroup.lastChangeNanos <= cooldownNanos
				&& activeEditGroup.after.equals(before)) {
			activeEditGroup.after = after;
			activeEditGroup.lastChangeNanos = now;
			return;
		}
		EditGroup group = new EditGroup(kind, before, after, now);
		editingHistory.record(kind.label, () -> applyTextState(group.before),
				() -> applyTextState(group.after));
		activeEditGroup = kind.coalescible ? group : null;
	}

	private TextState textState() {
		return new TextState(text, caret, anchor);
	}

	private void applyTextState(TextState state) {
		text = state.text;
		caret = Math.min(state.caret, text.length());
		anchor = Math.min(state.anchor, text.length());
		clearValidationFeedback();
		contentChanged();
	}

	private void applyCommittedText(String committedText) {
		text = committedText;
		editBaseline = committedText;
		lastValidText = committedText;
		caret = anchor = committedText.length();
		clearValidationFeedback();
		resetEditingHistory();
		contentChanged();
	}

	private void resetEditingHistory() {
		activeEditGroup = null;
		editingHistory.reset();
	}

	private record TextState(String text, int caret, int anchor) {
	}

	private enum EditKind {
		INSERT("Insert text", true),
		BACKSPACE("Delete text", true),
		DELETE("Delete text", true),
		REPLACE("Replace text", false),
		CUT("Cut text", false),
		PASTE("Paste text", false);

		private final String label;
		private final boolean coalescible;

		EditKind(String label, boolean coalescible) {
			this.label = label;
			this.coalescible = coalescible;
		}
	}

	private static final class EditGroup {
		private final EditKind kind;
		private final TextState before;
		private TextState after;
		private long lastChangeNanos;

		private EditGroup(EditKind kind, TextState before, TextState after,
				long lastChangeNanos) {
			this.kind = kind;
			this.before = before;
			this.after = after;
			this.lastChangeNanos = lastChangeNanos;
		}
	}

	private void copySelection() {
		if (anchor == caret)
			return;
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
					new StringSelection(text.substring(
							getSelectionStart(), getSelectionEnd())), null);
		} catch (RuntimeException ignored) {
		}
	}

	private void cutSelection() {
		if (anchor == caret)
			return;
		copySelection();
		replaceSelection("", EditKind.CUT);
	}

	private void pasteClipboard() {
		try {
			Object value = Toolkit.getDefaultToolkit().getSystemClipboard()
					.getData(DataFlavor.stringFlavor);
			if (value instanceof String string)
				replaceSelection(string.replace("\r", "").replace("\n", ""),
						EditKind.PASTE);
		} catch (Exception ignored) {
		}
	}

	private void contentChanged() {
		ensureCaretVisible();
		showCaret();
		invalidateRecording();
		ChangeEvent event = new ChangeEvent(this);
		for (ChangeListener listener : textChangeListeners)
			listener.stateChanged(event);
	}

	private void showValidationFailure(boolean transientFeedback) {
		validationFeedbackTimer.stop();
		setInvalid(true);
		showCaret();
		if (transientFeedback) {
			if (validationFeedbackTimer.getInitialDelay() == 0)
				setInvalid(false);
			else
				validationFeedbackTimer.restart();
		}
	}

	private void clearValidationFeedback() {
		validationFeedbackTimer.stop();
		setInvalid(false);
	}

	private void fireValidationFailed() {
		ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
				"validationFailed", System.currentTimeMillis(), 0);
		for (ActionListener listener : validationFailureListeners)
			listener.actionPerformed(event);
	}

	private void ensureCaretVisible() {
		GLTextEditorStyle style = getGLStyle();
		GLFont font = measurementFont(style.font());
		float caretPosition = font.measureText(text.substring(0, caret));
		float available = Math.max(1,
				getWidth() - style.padding().horizontal() - 2);
		if (caretPosition - scrollX > available)
			scrollX = caretPosition - available;
		else if (caretPosition < scrollX)
			scrollX = caretPosition;
		float textWidth = font.measureText(text);
		scrollX = Math.max(0, Math.min(scrollX, Math.max(0, textWidth - available)));
	}

	private void showCaret() {
		caretParameters.setOpacity(isFocused() ? 1 : 0);
		if (isFocused())
			caretTimer.restart();
	}

	private void updatePresentation(boolean geometryChanged) {
		GLTextEditorStyle style = getGLStyle();
		appliedStyle = style;
		setIntrinsicPreferredGLSize(new GLSize(style.preferredWidth(), style.height()));
		updateColors();
		if (geometryChanged)
			invalidateRecording();
	}

	private void updateColors() {
		GLTextEditorStyle style = getGLStyle();
		backgroundParameters.setColor(style.backgroundColor());
		textParameters.setColor(isEnabled()
				? style.textColor() : style.disabledTextColor());
		selectionParameters.setColor(style.selectionColor());
		caretParameters.setColor(style.caretColor());
		invalidateRecording();
		repaintGL();
	}

	private GLFont measurementFont(GLFontSpec specification) {
		try {
			return getGLFont(specification);
		} catch (IllegalStateException unattached) {
			return specification.measurementFont();
		}
	}

	private static String requireText(String text) {
		if (text == null)
			throw new IllegalArgumentException("Editor text must not be null");
		return text;
	}
}
