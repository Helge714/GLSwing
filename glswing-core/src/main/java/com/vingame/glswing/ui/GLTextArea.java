package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.EventQueue;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
	private final CopyOnWriteArrayList<GLTextCaretListener> caretListeners =
			new CopyOnWriteArrayList<>();
	private final List<GLTextCaret> secondaryCarets = new ArrayList<>();
	private Integer columnAnchor;
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
	private final GLAction findAction;
	private final GLAction replaceAction;
	private final GLAction goToLineAction;
	private String searchQuery = "";
	private GLTextSearchOptions searchOptions = GLTextSearchOptions.DEFAULT;
	private String searchPatternError;
	private List<SearchMatch> searchMatches = List.of();
	private int activeSearchMatch = -1;
	private volatile long searchGeneration;
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
	private GLTextSearchPresenter searchPresenter;
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
		findAction = GLAction.builder(GLInputActions.TEXT_FIND)
				.textKey(GLUiKeys.TEXT_FIND).onAction(() -> showSearchDialog(false)).build();
		replaceAction = GLAction.builder(GLInputActions.TEXT_REPLACE)
				.textKey(GLUiKeys.TEXT_REPLACE).enabledWhen(this::isEditable)
				.onAction(() -> showSearchDialog(true)).build();
		goToLineAction = GLAction.builder(GLInputActions.TEXT_GO_TO_LINE)
				.textKey(GLUiKeys.TEXT_GO_TO_LINE)
				.onAction(this::showGoToLineDialog).build();
		textActions.add(leftAction).add(centerAction).add(rightAction)
				.add(justifiedAction).add(boldAction).add(italicAction)
				.add(findAction).add(replaceAction).add(goToLineAction);
		leftItem = alignmentSubMenu.addItem(leftAction);
		centerItem = alignmentSubMenu.addItem(centerAction);
		rightItem = alignmentSubMenu.addItem(rightAction);
		justifiedItem = alignmentSubMenu.addItem(justifiedAction);
		boldItem = formatSubMenu.addItem(boldAction);
		italicItem = formatSubMenu.addItem(italicAction);
		alignmentMenuItem = alignmentMenu.addSubMenu("", alignmentSubMenu);
		formatMenuItem = alignmentMenu.addSubMenu("", formatSubMenu);
		alignmentMenu.addSeparator();
		alignmentMenu.addItem(goToLineAction);
		alignmentMenu.addItem(findAction);
		alignmentMenu.addItem(replaceAction);
		updateContextMenuText();
		setIntrinsicPreferredGLSize(new GLSize(420, 240));
	}

	private boolean caretVisible;
	public final GLTextDocument getDocument() { return document; }
	public final void setDocument(GLTextDocument value) {
		if (value == null) throw new IllegalArgumentException("Document must not be null");
		if (document != null) document.removeDocumentListener(documentListener);
		document = value;
		clearSearch();
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
	public final void addCaretListener(GLTextCaretListener listener) {
		caretListeners.addIfAbsent(Objects.requireNonNull(listener, "Caret listener"));
	}
	public final void removeCaretListener(GLTextCaretListener listener) {
		caretListeners.remove(listener);
	}
	/** Returns the primary caret followed by all secondary carets. */
	public final List<GLTextCaret> getCarets() {
		ArrayList<GLTextCaret> result = new ArrayList<>(secondaryCarets.size() + 1);
		result.add(new GLTextCaret(anchor, caret)); result.addAll(secondaryCarets);
		return List.copyOf(result);
	}
	/** Adds a bare secondary caret unless that position already has one. */
	public final void addCaret(int position) {
		if (position < 0 || position > document.length())
			throw new IndexOutOfBoundsException("Caret position: " + position);
		if (position == caret || secondaryCarets.stream().anyMatch(value ->
				value.position() == position && value.anchor() == position)) return;
		secondaryCarets.add(new GLTextCaret(position, position));
		contentChanged();
	}
	public final void clearSecondaryCarets() {
		if (secondaryCarets.isEmpty()) return;
		secondaryCarets.clear(); contentChanged();
	}
	/** Replaces the complete primary/secondary caret set. */
	public final void setCarets(List<GLTextCaret> values) {
		if (values == null || values.isEmpty())
			throw new IllegalArgumentException("At least one caret is required");
		for (GLTextCaret value : values)
			if (value == null || value.end() > document.length())
				throw new IllegalArgumentException("Caret is outside the document");
		GLTextCaret primary = values.get(0);
		anchor = primary.anchor(); caret = primary.position();
		secondaryCarets.clear(); secondaryCarets.addAll(values.subList(1, values.size()));
		contentChanged();
	}
	/** Replaces every caret selection as one undoable edit. */
	public final void replaceCaretSelections(String text) {
		if (text == null) throw new IllegalArgumentException("Replacement text required");
		if (!editable) return;
		replaceSelection(text);
	}
	/** Creates one rectangular selection per logical line. Columns are UTF-16 offsets. */
	public final void selectColumns(int anchorLine, int anchorColumn,
			int targetLine, int targetColumn) {
		int first = Math.max(0, Math.min(anchorLine, targetLine));
		int last = Math.min(document.getLineCount() - 1, Math.max(anchorLine, targetLine));
		if (first > last || anchorColumn < 0 || targetColumn < 0)
			throw new IllegalArgumentException("Invalid column selection");
		secondaryCarets.clear();
		for (int line = first; line <= last; line++) {
			int lineStart = document.getLineStart(line), lineEnd = document.getLineEnd(line);
			int from = Math.min(lineEnd, lineStart + anchorColumn);
			int to = Math.min(lineEnd, lineStart + targetColumn);
			if (line == first) { anchor = from; caret = to; }
			else secondaryCarets.add(new GLTextCaret(from, to));
		}
		contentChanged();
	}
	/** Returns the number of logical lines in the current document. */
	public final int getLineCount() { return document.getLineCount(); }
	/**
	 * Moves the caret to a one-based logical line and centers its visual row when
	 * the viewport permits it.
	 */
	public void goToLine(int lineNumber) {
		if (lineNumber < 1 || lineNumber > document.getLineCount())
			throw new IllegalArgumentException("Line must be between 1 and "
					+ document.getLineCount() + ": " + lineNumber);
		moveCaret(document.getLineStart(lineNumber - 1), false);
		GLFont font = font();
		List<Row> visualRows = rows(font);
		int row = rowIndexForCaret(visualRows);
		scrollY = row * font.getLineHeight()
				- (textViewportHeight() - font.getLineHeight()) * 0.5f;
		clampScroll();
		invalidateRecording();
		repaintGL();
	}
	public final int getSelectionStart() { return Math.min(anchor, caret); }
	public final int getSelectionEnd() { return Math.max(anchor, caret); }
	public final void select(int start, int end) {
		if (start < 0 || end < start || end > document.length())
			throw new IndexOutOfBoundsException("Text selection: " + start + ".." + end);
		secondaryCarets.clear(); anchor = start; caret = end; contentChanged();
	}
	public final void selectAll() { secondaryCarets.clear(); anchor = 0; caret = document.length(); contentChanged(); }
	/** Returns the selected document text, or an empty string at a bare caret. */
	public final String getSelectedText() {
		return document.getText(getSelectionStart(), getSelectionEnd());
	}
	/** Starts a literal search and activates the first occurrence after the caret. */
	public final int startSearch(String query, boolean matchCase) {
		return startSearch(query, new GLTextSearchOptions(matchCase, false, false));
	}
	/** Starts a configured search and activates the first occurrence after the caret. */
	public final int startSearch(String query, GLTextSearchOptions options) {
		setSearch(query, options);
		activeSearchMatch = firstMatchAtOrAfter(searchMatches, getSelectionStart());
		activateSearchMatch();
		return searchMatches.size();
	}
	/**
	 * Indexes a search away from the UI thread and applies only the newest result.
	 * A newer search, a document change, or {@link #clearSearch()} cancels the
	 * returned stage before stale markers can be installed.
	 */
	public final CompletionStage<Integer> startSearchAsync(String query,
			boolean matchCase) {
		return startSearchAsync(query,
				new GLTextSearchOptions(matchCase, false, false));
	}
	/** Asynchronously indexes a search with the supplied matching policy. */
	public final CompletionStage<Integer> startSearchAsync(String query,
			GLTextSearchOptions options) {
		String requestedQuery = query == null ? "" : query;
		GLTextSearchOptions requestedOptions = Objects.requireNonNull(options,
				"Search options");
		if (requestedQuery.isEmpty()) {
			clearSearch();
			return CompletableFuture.completedFuture(0);
		}
		long generation = ++searchGeneration;
		String source = document.getText();
		int origin = getSelectionStart();
		CompletableFuture<Integer> result = new CompletableFuture<>();
		CompletableFuture.supplyAsync(() -> scanMatches(source,
				requestedQuery, requestedOptions,
				() -> generation != searchGeneration))
				.whenComplete((scan, failure) -> EventQueue.invokeLater(() -> {
					if (generation != searchGeneration) {
						result.cancel(false);
						return;
					}
					if (failure != null) {
						result.completeExceptionally(failure);
						return;
					}
					searchQuery = requestedQuery;
					searchOptions = requestedOptions;
					searchPatternError = scan.error();
					searchMatches = scan.matches();
					activeSearchMatch = firstMatchAtOrAfter(searchMatches, origin);
					activateSearchMatch();
					invalidateRecording();
					repaintGL();
					result.complete(searchMatches.size());
				}));
		return result;
	}
	/** Returns the number of matches in the active search. */
	public final int getSearchMatchCount() { return searchMatches.size(); }
	/** Returns the zero-based active match, or {@code -1} when none is active. */
	public final int getActiveSearchMatchIndex() { return activeSearchMatch; }
	/** Returns the current regular-expression error, or {@code null}. */
	public final String getSearchPatternError() { return searchPatternError; }
	/** Clears the active search and all visual search markers. */
	public final void clearSearch() {
		searchGeneration++;
		searchQuery = "";
		searchOptions = GLTextSearchOptions.DEFAULT;
		searchPatternError = null;
		searchMatches = List.of();
		activeSearchMatch = -1;
		invalidateRecording();
		repaintGL();
	}
	/** Selects the next occurrence, wrapping at the document end. */
	public final boolean findNext(String query, boolean matchCase) {
		return findNext(query, new GLTextSearchOptions(matchCase, false, false));
	}
	public final boolean findNext(String query, GLTextSearchOptions options) {
		return find(query, options, false);
	}
	/** Selects the previous occurrence, wrapping at the document start. */
	public final boolean findPrevious(String query, boolean matchCase) {
		return findPrevious(query, new GLTextSearchOptions(matchCase, false, false));
	}
	public final boolean findPrevious(String query, GLTextSearchOptions options) {
		return find(query, options, true);
	}
	private boolean find(String query, GLTextSearchOptions options,
			boolean backwards) {
		Objects.requireNonNull(options, "Search options");
		boolean changed = !sameSearch(query, options);
		if (changed) setSearch(query, options);
		if (searchMatches.isEmpty()) return false;
		if (changed || activeSearchMatch < 0) {
			int origin = backwards ? getSelectionStart() : getSelectionEnd();
			activeSearchMatch = backwards ? searchMatches.size() - 1 : 0;
			if (backwards) {
				for (int index = searchMatches.size() - 1; index >= 0; index--)
					if (searchMatches.get(index).end() <= origin) {
						activeSearchMatch = index;
						break;
					}
			} else {
				for (int index = 0; index < searchMatches.size(); index++)
					if (searchMatches.get(index).start() >= origin) {
						activeSearchMatch = index;
						break;
					}
			}
		} else activeSearchMatch = Math.floorMod(activeSearchMatch
				+ (backwards ? -1 : 1), searchMatches.size());
		activateSearchMatch();
		return true;
	}
	private boolean sameSearch(String query, GLTextSearchOptions options) {
		return query != null && query.equals(searchQuery)
				&& options.equals(searchOptions);
	}
	private void setSearch(String query, GLTextSearchOptions options) {
		searchGeneration++;
		searchQuery = query == null ? "" : query;
		searchOptions = Objects.requireNonNull(options, "Search options");
		SearchScan scan = scanMatches(document.getText(), searchQuery,
				searchOptions, () -> false);
		searchMatches = scan.matches();
		searchPatternError = scan.error();
		activeSearchMatch = -1;
		invalidateRecording();
		repaintGL();
	}
	private static SearchScan scanMatches(String source, String query,
			GLTextSearchOptions options, BooleanSupplier cancelled) {
		if (query.isEmpty()) return new SearchScan(List.of(), null);
		Pattern pattern;
		try {
			int flags = options.matchCase() ? 0
					: Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
			pattern = Pattern.compile(options.regularExpression()
					? query : Pattern.quote(query), flags);
		} catch (PatternSyntaxException failure) {
			return new SearchScan(List.of(), failure.getDescription());
		}
		List<SearchMatch> result = new ArrayList<>();
		Matcher matcher = pattern.matcher(source);
		while (matcher.find()) {
			if (cancelled.getAsBoolean()) return new SearchScan(List.of(), null);
			if (matcher.start() == matcher.end()) continue;
			if (!options.wholeWord()
					|| wholeWord(source, matcher.start(), matcher.end()))
				result.add(new SearchMatch(matcher.start(), matcher.end()));
		}
		return new SearchScan(List.copyOf(result), null);
	}
	private static boolean wholeWord(String source, int start, int end) {
		return (start == 0 || !isWordCharacter(source.codePointBefore(start)))
				&& (end == source.length()
						|| !isWordCharacter(source.codePointAt(end)));
	}
	private static boolean isWordCharacter(int codePoint) {
		return Character.isLetterOrDigit(codePoint) || codePoint == '_';
	}
	private static int firstMatchAtOrAfter(List<SearchMatch> matches, int origin) {
		if (matches.isEmpty()) return -1;
		for (int index = 0; index < matches.size(); index++)
			if (matches.get(index).start() >= origin) return index;
		return 0;
	}
	private void activateSearchMatch() {
		if (activeSearchMatch < 0 || activeSearchMatch >= searchMatches.size()) return;
		SearchMatch match = searchMatches.get(activeSearchMatch);
		select(match.start(), match.end());
	}
	/** Replaces the selected match, or selects the next one when necessary. */
	public final boolean replaceCurrent(String query, String replacement,
			boolean matchCase) {
		return replaceCurrent(query, replacement,
				new GLTextSearchOptions(matchCase, false, false));
	}
	public final boolean replaceCurrent(String query, String replacement,
			GLTextSearchOptions options) {
		if (!editable || query == null || query.isEmpty() || replacement == null) return false;
		if (!sameSearch(query, options) || activeSearchMatch < 0
				|| getSelectionStart() != searchMatches.get(activeSearchMatch).start()
				|| getSelectionEnd() != searchMatches.get(activeSearchMatch).end())
			return findNext(query, options);
		String resolved = replacementForMatch(getSelectedText(), query,
				replacement, options);
		if (resolved == null) return false;
		replaceSelection(resolved);
		findNext(query, options);
		return true;
	}
	/** Replaces every non-overlapping match as one undoable edit. */
	public final int replaceAll(String query, String replacement, boolean matchCase) {
		return replaceAll(query, replacement,
				new GLTextSearchOptions(matchCase, false, false));
	}
	public final int replaceAll(String query, String replacement,
			GLTextSearchOptions options) {
		if (!editable || query == null || query.isEmpty() || replacement == null) return 0;
		String source = document.getText();
		SearchScan scan = scanMatches(source, query,
				Objects.requireNonNull(options, "Search options"), () -> false);
		if (scan.error() != null) return 0;
		List<TextReplacement> replacements = new ArrayList<>();
		for (SearchMatch match : scan.matches()) {
			String resolved = replacementForMatch(
					source.substring(match.start(), match.end()), query,
					replacement, options);
			if (resolved == null) return 0;
			replacements.add(new TextReplacement(match.start(), match.end(), resolved));
		}
		if (replacements.isEmpty()) return 0;
		TextReplacement last = replacements.get(replacements.size() - 1);
		int nextCaret = last.start() + last.text().length();
		replaceTextAtomically(replacements, nextCaret, "Replace all");
		return replacements.size();
	}
	private static String replacementForMatch(String matched, String query,
			String replacement, GLTextSearchOptions options) {
		if (!options.regularExpression()) return replacement;
		try {
			int flags = options.matchCase() ? 0
					: Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
			Matcher matcher = Pattern.compile(query, flags).matcher(matched);
			return matcher.matches() ? matcher.replaceFirst(replacement) : null;
		} catch (IllegalArgumentException failure) {
			return null;
		}
	}
	private record SearchMatch(int start, int end) { }
	private record SearchScan(List<SearchMatch> matches, String error) { }
	/** Opens the standard modeless Find or Find/Replace dialog for this editor. */
	public final void showSearchDialog(boolean replaceVisible) {
		if (searchPresenter != null) {
			searchPresenter.showSearch(this, replaceVisible);
			return;
		}
		GLParent host = popupHost();
		if (host != null) GLTextSearchDialog.show(host, this, replaceVisible);
	}
	/**
	 * Replaces the standard modeless search dialog with an application presenter.
	 * Passing {@code null} restores the built-in dialog.
	 */
	public final void setSearchPresenter(GLTextSearchPresenter value) {
		searchPresenter = value;
	}
	public final GLTextSearchPresenter getSearchPresenter() { return searchPresenter; }
	/** Opens the standard line-navigation dialog. */
	public final void showGoToLineDialog() {
		GLParent host = popupHost();
		if (host != null) GLTextGoToLineDialog.show(host, this);
	}
	/** Shared Find action for specialized editor context menus. */
	protected final GLAction findAction() { return findAction; }
	/** Shared Replace action for specialized editor context menus. */
	protected final GLAction replaceAction() { return replaceAction; }
	/** Shared line-navigation action for specialized editor context menus. */
	protected final GLAction goToLineAction() { return goToLineAction; }
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
		if (containerBorderVisible && layoutBorderWidth(style.borderWidth()) > 0)
			context.fillAndDrawUIRoundRect(0, 0, width, height, radii,
					layoutBorderWidth(style.borderWidth()), style.backgroundColor(), border,
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
			drawRowBackgroundDecorations(context, font, row.start, row.end, x, top,
					lineHeight, available);
			drawSearchMatches(context, font, row, x, top, lineHeight,
					style.selectionColor());
			if (isFocused())
				drawSelection(context, font, row, x, top, lineHeight,
						style.selectionColor());
			if (isFocused()) drawSecondarySelections(context, font, row, x, top,
					lineHeight, style.selectionColor());
			drawRow(context, font, row, x, top, baseline, available, style);
			drawTextDecorations(context, font, row.start, row.end, x, baseline,
					available);
			if (isFocused() && caretVisible && index == caretRow) {
				float caretX = x + rowAdvance(font, row, caret, available);
				context.fillRect(caretX, top, 1, lineHeight, style.caretColor());
			}
			if (isFocused() && caretVisible)
				for (GLTextCaret extra : secondaryCarets)
					if (extra.position() >= row.start && extra.position() <= row.end) {
						float caretX = x + rowAdvance(font, row, extra.position(), available);
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
	/** Draws editor-specific row backgrounds before selection and source text. */
	protected void drawRowBackgroundDecorations(GLContext2D context, GLFont font,
			int rowStart, int rowEnd, float x, float top, float lineHeight,
			float available) { }
	/** Returns the next visual-only inline decoration in the half-open range. */
	protected int nextInlineDecorationOffset(int startInclusive, int endExclusive) {
		return -1;
	}
	/** Returns the horizontal space occupied by the decoration before this offset. */
	protected float inlineDecorationWidth(GLFont font, int offset) { return 0; }
	/** Draws a visual-only decoration immediately before the character at offset. */
	protected void drawInlineDecoration(GLContext2D context, GLFont font, int offset,
			float x, float top, float baseline, float lineHeight) { }
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
	private void drawSecondarySelections(GLContext2D context, GLFont font, Row row,
			float x, float top, float height, Color color) {
		for (GLTextCaret caret : secondaryCarets) {
			int start = Math.max(row.start, caret.start());
			int end = Math.min(row.end, caret.end());
			if (end <= start) continue;
			float available = textViewportWidth();
			float left = x + rowAdvance(font, row, start, available);
			float right = x + rowAdvance(font, row, end, available);
			context.fillRect(left, top, Math.max(1, right - left), height, color);
		}
	}
	private void drawSearchMatches(GLContext2D context, GLFont font, Row row,
			float x, float top, float height, Color selectionColor) {
		if (searchMatches.isEmpty()) return;
		Color passive = getGLTheme().values().getColor("textEditor.searchMatch",
				withAlpha(selectionColor, Math.max(48, selectionColor.getAlpha() / 2)));
		Color active = getGLTheme().values().getColor("textEditor.searchActiveMatch",
				withAlpha(selectionColor, Math.max(144, selectionColor.getAlpha())));
		float available = textViewportWidth();
		for (int index = 0; index < searchMatches.size(); index++) {
			SearchMatch match = searchMatches.get(index);
			int start = Math.max(row.start, match.start());
			int end = Math.min(row.end, match.end());
			if (end <= start) continue;
			float left = x + rowAdvance(font, row, start, available);
			float right = x + rowAdvance(font, row, end, available);
			context.fillRect(left, top, Math.max(1, right - left), height,
					index == activeSearchMatch ? active : passive);
		}
	}
	private static Color withAlpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.max(0, Math.min(255, alpha)));
	}

	@Override protected void controlMousePressed(GLMouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON3) {
			if (event.hasLocalPosition()) {
				int position = positionAt(event.getLocalX(), event.getLocalY());
				if (position < getSelectionStart() || position > getSelectionEnd()
						|| getSelectionStart() == getSelectionEnd()) {
					clearSecondaryCarets();
					moveCaret(position, false);
				}
			}
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
		int position = positionAt(event.getLocalX(), event.getLocalY());
		if ((event.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0) {
			if ((event.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
				columnAnchor = caret;
				updateColumnSelection(columnAnchor, position);
				selecting = true;
			} else addCaret(position);
			event.consume();
			return;
		}
		clearSecondaryCarets();
		selecting = true;
		moveCaret(position,
				getGLInputMap().hasModifiers(GLInputActions.TEXT_EXTEND_SELECTION, event.getModifiersEx()));
	}
	@Override protected void controlMouseDragged(GLMouseEvent event) {
		if (draggingScrollBar && event.hasLocalPosition()) {
			dragScrollBar(event.getLocalY());
			return;
		}
		if (selecting && event.hasLocalPosition()) {
			int position = positionAt(event.getLocalX(), event.getLocalY());
			if (columnAnchor != null) updateColumnSelection(columnAnchor, position);
			else moveCaret(position, true);
		}
	}
	@Override protected void controlMouseMoved(GLMouseEvent event) {
		if (!event.hasLocalPosition()) return;
		float textStart = getGLStyle().padding().left() + leadingTextInset();
		boolean outsideText = event.getLocalX() < textStart
				|| event.getLocalX() >= trailingDecorationX();
		pointerController.setCursor(this, outsideText
				? GLPointerCursor.DEFAULT : GLPointerCursor.TEXT);
	}
	@Override protected void controlMouseReleased(GLMouseEvent event) { selecting = false; draggingScrollBar = false; columnAnchor = null; }
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
		if (!secondaryCarets.isEmpty()) {
			replaceMultipleSelections(inserted);
			return;
		}
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
	private void replaceMultipleSelections(String inserted) {
		ArrayList<GLTextCaret> ranges = new ArrayList<>(getCarets());
		ranges.sort(java.util.Comparator.comparingInt(GLTextCaret::start));
		ArrayList<TextReplacement> replacements = new ArrayList<>();
		int shift = 0;
		ArrayList<Integer> resultingCarets = new ArrayList<>();
		int previousEnd = -1;
		for (GLTextCaret range : ranges) {
			if (range.start() < previousEnd) continue;
			replacements.add(new TextReplacement(range.start(), range.end(), inserted));
			resultingCarets.add(range.start() + shift + inserted.length());
			shift += inserted.length() - (range.end() - range.start());
			previousEnd = range.end();
		}
		if (replacements.isEmpty()) return;
		replaceTextAtomically(replacements, resultingCarets.get(0), "Edit multiple carets");
		secondaryCarets.clear();
		for (int index = 1; index < resultingCarets.size(); index++) {
			int at = resultingCarets.get(index);
			secondaryCarets.add(new GLTextCaret(at, at));
		}
		contentChanged();
	}
	private void updateColumnSelection(int from, int to) {
		int fromLine = document.getLineOfOffset(from);
		int toLine = document.getLineOfOffset(to);
		selectColumns(fromLine, from - document.getLineStart(fromLine), toLine,
				to - document.getLineStart(toLine));
	}
	protected record TextReplacement(int start, int end, String text) { }
	/** Applies non-overlapping replacements as one local undo/redo operation. */
	protected final void replaceTextAtomically(List<TextReplacement> replacements,
			int nextCaret) {
		replaceTextAtomically(replacements, nextCaret, "Apply completion");
	}
	/** Applies non-overlapping replacements under a caller-supplied history name. */
	protected final void replaceTextAtomically(List<TextReplacement> replacements,
			int nextCaret, String historyName) {
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
		editingHistory.record(historyName,
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
	private void backspace() {
		if (!secondaryCarets.isEmpty()) { expandBareCaretsForDeletion(true); replaceMultipleSelections(""); }
		else if (caret != anchor) replaceSelection("");
		else if (caret > 0) { anchor = previousCharacter(caret); replaceSelection(""); }
	}
	private void deleteForward() {
		if (!secondaryCarets.isEmpty()) { expandBareCaretsForDeletion(false); replaceMultipleSelections(""); }
		else if (caret != anchor) replaceSelection("");
		else if (caret < document.length()) { anchor = nextCharacter(caret); replaceSelection(""); }
	}
	private void expandBareCaretsForDeletion(boolean backward) {
		if (caret == anchor) anchor = backward ? previousCharacter(caret)
				: nextCharacter(caret);
		for (int index = 0; index < secondaryCarets.size(); index++) {
			GLTextCaret value = secondaryCarets.get(index);
			if (value.start() != value.end()) continue;
			int other = backward ? previousCharacter(value.position())
					: nextCharacter(value.position());
			secondaryCarets.set(index, new GLTextCaret(other, value.position()));
		}
	}
	private void moveCaret(int value, boolean extend) { moveCaret(value, extend, false); }
	private void moveCaret(int value, boolean extend, boolean preserveVerticalX) {
		secondaryCarets.clear();
		caret = Math.max(0, Math.min(document.length(), value));
		if (!extend) anchor = caret;
		if (!preserveVerticalX) verticalNavigationX = null;
		caretVisible = true; ensureCaretVisible(); invalidateRecording();
		fireCaretChanged();
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
		List<Row> cached = cachedRows;
		if (cached != null && Float.compare(cachedRowsWidth, available) == 0)
			return cached;
		List<Row> result = new ArrayList<>();
		for (int line = 0; line < document.getLineCount(); line++) {
			if (!isLogicalLineVisible(line)) continue;
			int start = document.getLineStart(line), end = document.getLineEnd(line);
			if (!lineWrap || start == end) { result.add(new Row(start, end, false, line)); continue; }
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
				result.add(new Row(rowStart, chosen, chosen < end, line)); rowStart = chosen;
				if (trailingSeparatorWrap && chosen == end)
					result.add(new Row(end, end, false, line));
			}
		}
		List<Row> built = List.copyOf(result);
		cachedRowsWidth = available;
		cachedRows = built;
		cachedMaximumRowWidth = 0;
		if (!lineWrap)
			for (Row row : built)
				cachedMaximumRowWidth = Math.max(cachedMaximumRowWidth,
						measureRange(row.start, row.end)
								+ trailingRowDecorationWidth(row.start, row.end));
		return built;
	}
	/** Controls whether a logical document line participates in visual layout. */
	protected boolean isLogicalLineVisible(int logicalLine) { return true; }
	/** Returns the logical line represented by a visible row, or {@code -1}. */
	protected final int logicalLineAtVisualRow(int visualRow) {
		List<Row> value = rows(font());
		return visualRow < 0 || visualRow >= value.size() ? -1
				: value.get(visualRow).logicalLine;
	}
	/** Returns the current number of laid-out visual rows. */
	protected final int visualRowCount() { return rows(font()).size(); }
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
		searchGeneration++;
		invalidateRowLayout();
		if (!applyingHistory) {
			caret = translatedOffset(caret, change);
			anchor = translatedOffset(anchor, change);
			editingHistory.reset();
		}
		caret = Math.min(caret, source.length());
		anchor = Math.min(anchor, source.length());
		if (!searchQuery.isEmpty()) {
			SearchScan scan = scanMatches(source.getText(), searchQuery,
					searchOptions, () -> false);
			searchMatches = scan.matches();
			searchPatternError = scan.error();
			activeSearchMatch = -1;
		}
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
		fireCaretChanged();
	}
	private void fireCaretChanged() {
		caretSetChanged();
		for (GLTextCaretListener listener : caretListeners)
			listener.caretChanged(this, caret, getSelectionStart(), getSelectionEnd());
	}
	/** Called after primary or secondary caret state changes. */
	protected void caretSetChanged() { }
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
		if (bounded < row.end)
			advance += inlineDecorationWidth(font, bounded);
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
			float top, float baseline, float available, GLTextEditorStyle style) {
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
				int decoration = nextInlineDecorationOffset(start, end);
				if (decoration == start) {
					float decorationWidth = inlineDecorationWidth(font, start);
					drawInlineDecoration(context, font, start,
							x + rowAdvance(font, row, start, available) - decorationWidth,
							top, baseline, font.getLineHeight());
					decoration = nextInlineDecorationOffset(start + 1, end);
				}
				if (decoration > start) end = Math.min(end, decoration);
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
				Math.min(layoutCornerRadius(style.cornerRadius()), width / 2), style.track());
		float thumbHeight = scrollBarThumbHeight(contentHeight, viewport, style);
		float travel = Math.max(0, trackHeight - thumbHeight);
		float maximum = Math.max(1, contentHeight - viewport);
		float thumbY = y + travel * scrollY / maximum;
		context.fillUIRoundRect(x + style.trackPadding(),
				thumbY + style.trackPadding(),
				Math.max(1, width - style.trackPadding() * 2),
				Math.max(1, thumbHeight - style.trackPadding() * 2),
				Math.min(layoutCornerRadius(style.cornerRadius()), width / 2),
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
		GLFont baseFont = font();
		for (int offset = nextInlineDecorationOffset(start, end); offset >= start
				&& offset < end; offset = nextInlineDecorationOffset(offset + 1, end))
			width += inlineDecorationWidth(baseFont, offset);
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
	private record Row(int start, int end, boolean softWrapped, int logicalLine) { }
}
