package com.vingame.glswing.ui;

import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import javax.swing.Timer;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.document.GLDocument;
import com.vingame.glswing.document.GLDocumentTabController;
import com.vingame.glswing.document.GLDocumentWorkspace;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLIcon;

/**
 * Ready-to-use tabbed code surface with shared navigation history and an
 * integrated Find/Replace bar. Storage and language tooling remain supplied by
 * the application through document resolution and editor configuration.
 */
public final class GLCodeWorkspace extends GLContainer implements AutoCloseable {
	private static final String ICON_ROOT = "com/vingame/glswing/icons/";
	private static final GLIcon SEARCH_PREVIOUS_ICON =
			GLIcon.fromSvgResource(ICON_ROOT + "search_previous.svg");
	private static final GLIcon SEARCH_NEXT_ICON =
			GLIcon.fromSvgResource(ICON_ROOT + "search_next.svg");
	private static final GLIcon SEARCH_CLOSE_ICON =
			GLIcon.fromSvgResource(ICON_ROOT + "search_close.svg");
	private static final GLIcon SEARCH_EXPAND_ICON =
			GLIcon.fromSvgResource(ICON_ROOT + "search_expand.svg");
	private final GLTabbedPane tabs = new GLTabbedPane();
	private final GLCodeNavigationHistory navigationHistory =
			new GLCodeNavigationHistory();
	private final GLDocumentWorkspace documents;
	private final GLDocumentTabController documentTabs;
	private final boolean ownsDocuments;
	private final SearchBar searchBar = new SearchBar();
	private final GLCodeStatusBar statusBar = new GLCodeStatusBar();
	private final Map<URI, GLCodeEditor> editorsByUri = new HashMap<>();
	private final Map<GLCodeEditor, GLDocument<GLTextDocument>> documentsByEditor =
			new IdentityHashMap<>();
	private final Map<URI, CompletableFuture<GLCodeEditor>> pending = new HashMap<>();
	private GLCodeWorkspaceDocumentResolver documentResolver;
	private Consumer<GLCodeEditor> editorConfigurer = editor -> { };
	private Consumer<Throwable> navigationFailureHandler = failure -> { };
	private boolean closed;

	public GLCodeWorkspace() {
		this(new GLDocumentWorkspace(), true);
	}

	/** Uses an application document workspace for undo/redo routing. */
	public GLCodeWorkspace(GLDocumentWorkspace documents) {
		this(documents, false);
	}

	private GLCodeWorkspace(GLDocumentWorkspace documents, boolean ownsDocuments) {
		this.documents = Objects.requireNonNull(documents, "Document workspace");
		this.ownsDocuments = ownsDocuments;
		documentTabs = new GLDocumentTabController(tabs, documents);
		tabs.setTabsClosable(true);
		tabs.setClosedTabHistoryEnabled(true);
		tabs.addSelectionListener((source, oldIndex, newIndex) -> activeEditorChanged());
		tabs.addCloseListener((source, formerIndex, title, content) -> activeEditorChanged());
		searchBar.setVisible(false);
		tabs.setContentHeader(searchBar);
		tabs.setContentFooter(statusBar);
		addGL(tabs);
		setIntrinsicPreferredGLSize(new GLSize(760, 520));
	}

	public GLTabbedPane tabs() { return tabs; }
	public GLDocumentWorkspace documents() { return documents; }
	public GLDocumentTabController documentTabs() { return documentTabs; }
	public GLCodeNavigationHistory navigationHistory() { return navigationHistory; }
	public GLCodeStatusBar statusBar() { return statusBar; }

	public GLCodeWorkspaceDocumentResolver getDocumentResolver() {
		return documentResolver;
	}
	public void setDocumentResolver(GLCodeWorkspaceDocumentResolver value) {
		documentResolver = value;
	}

	/** Configures each subsequently created editor, for example with an LSP. */
	public void setEditorConfigurer(Consumer<GLCodeEditor> value) {
		editorConfigurer = value == null ? editor -> { } : value;
	}

	public void setNavigationFailureHandler(Consumer<Throwable> value) {
		navigationFailureHandler = value == null ? failure -> { } : value;
	}

	/** Opens or activates a typed text document and returns its editor. */
	public GLCodeEditor openDocument(GLDocument<GLTextDocument> document) {
		ensureOpen();
		Objects.requireNonNull(document, "Document");
		URI uri = document.location();
		GLCodeEditor existing = uri == null ? null : editorsByUri.get(uri);
		if (existing != null && activate(existing)) {
			return existing;
		}
		if (uri != null) editorsByUri.remove(uri);
		GLCodeEditor editor = uri == null
				? new GLCodeEditor(document.content())
				: new GLCodeEditor(document.content(), uri);
		editor.setEditable(!document.isReadOnly());
		editorConfigurer.accept(editor);
		return addDocument(document, editor);
	}

	/** Adds an existing editor as the view of an existing managed document. */
	public GLCodeEditor addDocument(GLDocument<GLTextDocument> document,
			GLCodeEditor editor) {
		ensureOpen();
		Objects.requireNonNull(document, "Document");
		Objects.requireNonNull(editor, "Editor");
		if (document.content() != editor.getDocument())
			throw new IllegalArgumentException(
					"Document and editor must use the same text model");
		URI uri = document.location();
		GLCodeEditor existing = uri == null ? null : editorsByUri.get(uri);
		if (existing != null && activate(existing)) return existing;
		if (uri != null) editorsByUri.remove(uri);
		prepare(editor);
		documentTabs.addDocument(document, editor);
		documentsByEditor.put(editor, document);
		if (uri != null) editorsByUri.put(uri, editor);
		tabs.setSelectedIndex(tabs.indexOfComponent(editor));
		activeEditorChanged();
		return editor;
	}

	/** Adds an already configured editor as a managed document tab. */
	public GLCodeEditor addEditor(String title, GLCodeEditor editor) {
		ensureOpen();
		Objects.requireNonNull(editor, "Editor");
		GLDocument<GLTextDocument> document = new GLDocument<>(editor.getDocument(),
				title, editor.getDocumentUri(), new com.vingame.glswing.history.GLHistoryManager());
		document.setReadOnly(!editor.isEditable());
		return addDocument(document, editor);
	}

	/** Resolves, opens, and selects a location requested by code intelligence. */
	public CompletionStage<GLCodeEditor> openLocation(GLCodeLocation location) {
		ensureOpen();
		Objects.requireNonNull(location, "Location");
		GLCodeEditor existing = editorsByUri.get(location.uri());
		if (existing != null && activate(existing)) {
			moveTo(existing, location);
			return CompletableFuture.completedFuture(existing);
		}
		editorsByUri.remove(location.uri());
		if (documentResolver == null) return CompletableFuture.failedFuture(
				new IllegalStateException("No code-workspace document resolver is configured"));
		CompletableFuture<GLCodeEditor> current = pending.get(location.uri());
		if (current != null) return current.thenApply(editor -> {
			activate(editor);
			moveTo(editor, location);
			return editor;
		});
		CompletableFuture<GLCodeEditor> result = new CompletableFuture<>();
		pending.put(location.uri(), result);
		CompletionStage<GLDocument<GLTextDocument>> stage;
		try { stage = documentResolver.resolve(location); }
		catch (Throwable failure) { stage = CompletableFuture.failedFuture(failure); }
		if (stage == null) stage = CompletableFuture.failedFuture(
				new IllegalStateException("Document resolver returned null"));
		stage.whenComplete((document, failure) -> EventQueue.invokeLater(() -> {
			pending.remove(location.uri());
			if (failure != null || document == null) {
				Throwable problem = failure == null
						? new IllegalStateException("Document resolver returned no document")
						: failure;
				navigationFailureHandler.accept(problem);
				result.completeExceptionally(problem);
				return;
			}
			try {
				if (document.location() == null) document.setLocation(location.uri());
				GLCodeEditor editor = openDocument(document);
				moveTo(editor, location);
				result.complete(editor);
			} catch (Throwable problem) {
				navigationFailureHandler.accept(problem);
				result.completeExceptionally(problem);
			}
		}));
		return result;
	}

	public GLCodeEditor getSelectedEditor() {
		return tabs.getSelectedComponent() instanceof GLCodeEditor editor ? editor : null;
	}

	public GLDocument<GLTextDocument> getSelectedDocument() {
		return documentsByEditor.get(getSelectedEditor());
	}

	public boolean activate(GLCodeEditor editor) {
		int index = tabs.indexOfComponent(editor);
		if (index < 0 && tabs.reopenTab(editor)) index = tabs.indexOfComponent(editor);
		if (index < 0) return false;
		tabs.setSelectedIndex(index);
		return true;
	}

	public void showFind() { showSearch(getSelectedEditor(), false); }
	public void showReplace() { showSearch(getSelectedEditor(), true); }
	public void closeSearch() { searchBar.close(); }
	public boolean isSearchVisible() { return searchBar.isVisible(); }
	public String getSearchText() { return searchBar.find.getText(); }
	public void setSearchText(String value) { searchBar.find.setText(value); }
	public String getReplacementText() { return searchBar.replacement.getText(); }
	public void setReplacementText(String value) { searchBar.replacement.setText(value); }
	public boolean isSearchMatchCase() { return searchBar.matchCase.isSelected(); }
	public void setSearchMatchCase(boolean value) {
		if (searchBar.matchCase.isSelected() != value) {
			searchBar.matchCase.setSelected(value);
			searchBar.queryChanged();
		}
	}
	public boolean isSearchWholeWord() { return searchBar.wholeWord.isSelected(); }
	public void setSearchWholeWord(boolean value) {
		if (searchBar.wholeWord.isSelected() != value) {
			searchBar.wholeWord.setSelected(value);
			searchBar.queryChanged();
		}
	}
	public boolean isSearchRegularExpression() { return searchBar.regex.isSelected(); }
	public void setSearchRegularExpression(boolean value) {
		if (searchBar.regex.isSelected() != value) {
			searchBar.regex.setSelected(value);
			searchBar.queryChanged();
		}
	}
	public void findNext() { searchBar.next(); }
	public void findPrevious() { searchBar.previous(); }
	public void replaceCurrent() { searchBar.replaceCurrent(); }
	public void replaceAll() { searchBar.replaceAll(); }

	/** Connects an editor to this workspace without adding a tab or document. */
	public void configureEditor(GLCodeEditor editor) {
		ensureOpen();
		prepare(Objects.requireNonNull(editor, "Editor"));
	}

	private void prepare(GLCodeEditor editor) {
		editor.setNavigationHistory(navigationHistory);
		editor.setNavigationHandler(location -> openLocation(location));
		editor.setSearchPresenter(this::showSearch);
		editor.setWorkspaceEditHandler(this::applyWorkspaceEdits);
	}
	private CompletionStage<Boolean> applyWorkspaceEdits(List<GLCodeWorkspaceEdit> edits) {
		if (edits == null || edits.isEmpty()) return CompletableFuture.completedFuture(false);
		Map<URI, java.util.List<GLCodeWorkspaceEdit>> grouped = edits.stream()
				.collect(java.util.stream.Collectors.groupingBy(GLCodeWorkspaceEdit::uri,
						java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		GLCodeEditor previouslyActive = getSelectedEditor();
		EventQueue.invokeLater(() -> applyWorkspaceEditGroup(
				new java.util.ArrayList<>(grouped.entrySet()), 0,
				previouslyActive, result));
		return result;
	}
	private void applyWorkspaceEditGroup(
			java.util.List<Map.Entry<URI, java.util.List<GLCodeWorkspaceEdit>>> groups,
			int index, GLCodeEditor previouslyActive, CompletableFuture<Boolean> result) {
		if (index >= groups.size()) {
			if (previouslyActive != null) activate(previouslyActive);
			result.complete(true);
			return;
		}
		var group = groups.get(index);
		GLCodeEditor open = editorsByUri.get(group.getKey());
		if (open != null) {
			open.applyWorkspaceEditsForDocument(group.getValue());
			applyWorkspaceEditGroup(groups, index + 1, previouslyActive, result);
			return;
		}
		if (documentResolver == null) {
			result.completeExceptionally(new IllegalStateException(
					"No resolver for workspace edit: " + group.getKey()));
			return;
		}
		CompletionStage<GLDocument<GLTextDocument>> stage;
		try { stage = documentResolver.resolve(new GLCodeLocation(group.getKey(), 0, 0)); }
		catch (Throwable failure) { result.completeExceptionally(failure); return; }
		stage.whenComplete((document, failure) -> EventQueue.invokeLater(() -> {
			if (failure != null || document == null) {
				result.completeExceptionally(failure == null
						? new IllegalStateException("Resolver returned no document") : failure);
				return;
			}
			try {
				if (document.location() == null) document.setLocation(group.getKey());
				GLCodeEditor editor = openDocument(document);
				editor.applyWorkspaceEditsForDocument(group.getValue());
				applyWorkspaceEditGroup(groups, index + 1, previouslyActive, result);
			} catch (Throwable problem) { result.completeExceptionally(problem); }
		}));
	}

	private void showSearch(GLTextArea target, boolean replaceVisible) {
		if (!(target instanceof GLCodeEditor editor)) return;
		activate(editor);
		searchBar.open(editor, replaceVisible);
		requestGLFocus(searchBar.find);
	}

	private void activeEditorChanged() {
		GLCodeEditor editor = getSelectedEditor();
		searchBar.changeTarget(editor);
		statusBar.setEditor(editor);
	}

	private static void moveTo(GLCodeEditor editor, GLCodeLocation location) {
		if (location.line() < 0 || location.line() >= editor.getLineCount()) return;
		int start = editor.getDocument().getLineStart(location.line());
		int end = editor.getDocument().getLineEnd(location.line());
		editor.setCaretPosition(Math.min(end, start + location.character()));
	}

	@Override protected void resized(float width, float height) {
		tabs.setBounds(0, 0, width, height);
	}

	@Override protected void record(GLContext2D context) { }

	@Override public void close() {
		if (closed) return;
		closed = true;
		searchBar.close();
		statusBar.setEditor(null);
		documentTabs.close();
		if (ownsDocuments) documents.close();
		pending.clear();
	}

	private void ensureOpen() {
		if (closed) throw new IllegalStateException("Code workspace is closed");
	}

	private final class SearchBar extends GLContainer {
		private static final float FIND_HEIGHT = 42;
		private static final float REPLACE_HEIGHT = 78;
		private static final int LIVE_SEARCH_DELAY_MILLIS = 90;
		private final SearchField find = new SearchField() {
			@Override protected void controlKeyPressed(GLKeyEvent event) {
				boolean previousKey = event.getKeyCode() == KeyEvent.VK_UP
						|| (event.getKeyCode() == KeyEvent.VK_ENTER
								&& (event.getModifiersEx()
										& InputEvent.SHIFT_DOWN_MASK) != 0);
				if (previousKey) {
					previous();
					event.consume();
				} else if (event.getKeyCode() == KeyEvent.VK_DOWN) {
					next();
					event.consume();
				} else super.controlKeyPressed(event);
			}
		};
		private final SearchField replacement = new SearchField();
		private final GLLabel result = new GLLabel("0 / 0");
		private final GLCheckBox matchCase = new GLCheckBox("Aa");
		private final GLCheckBox wholeWord = new GLCheckBox("W");
		private final GLCheckBox regex = new GLCheckBox(".*");
		private final GLButton expand = new GLButton("");
		private final GLButton previous = new GLButton("");
		private final GLButton next = new GLButton("");
		private final GLButton replace = new GLButton("");
		private final GLButton replaceAll = new GLButton("");
		private final GLButton close = new GLButton("");
		private final Timer liveSearchTimer = new Timer(
				LIVE_SEARCH_DELAY_MILLIS, event -> runLiveSearch());
		private GLCodeEditor target;
		private boolean replaceVisible;
		private boolean openedBefore;
		private String submittedQuery;
		private GLTextSearchOptions submittedOptions;
		private int liveSearchGeneration;

		private SearchBar() {
			result.setAlignment(GLLabel.Alignment.CENTER);
			result.setVerticalAlignment(GLLabel.VerticalAlignment.CENTER);
			find.setCommitOnFocusLost(false);
			replacement.setCommitOnFocusLost(false);
			expand.setIcon(SEARCH_EXPAND_ICON);
			previous.setIcon(SEARCH_PREVIOUS_ICON);
			next.setIcon(SEARCH_NEXT_ICON);
			close.setIcon(SEARCH_CLOSE_ICON);
			addGL(expand); addGL(find); addGL(result); addGL(matchCase);
			addGL(wholeWord); addGL(regex);
			addGL(replacement);
			addGL(previous); addGL(next); addGL(replace); addGL(replaceAll); addGL(close);
			find.addActionListener(event -> next());
			find.addCancelListener(event -> close());
			replacement.addActionListener(event -> replaceCurrent());
			replacement.addCancelListener(event -> close());
			find.addTextChangeListener(event -> queryChanged());
			matchCase.addActionListener(event -> queryChanged());
			wholeWord.addActionListener(event -> queryChanged());
			regex.addActionListener(event -> queryChanged());
			expand.addActionListener(event -> setReplaceVisible(!replaceVisible));
			previous.addActionListener(event -> previous());
			next.addActionListener(event -> next());
			replace.addActionListener(event -> replaceCurrent());
			replaceAll.addActionListener(event -> replaceAll());
			close.addActionListener(event -> close());
			liveSearchTimer.setRepeats(false);
			replacement.setVisible(false);
			replace.setVisible(false);
			replaceAll.setVisible(false);
			setIntrinsicPreferredGLSize(new GLSize(760, FIND_HEIGHT));
			updateText();
			refresh();
		}

		private void open(GLCodeEditor editor, boolean withReplace) {
			if (target != editor) changeTarget(editor);
			if (!openedBefore || withReplace) setReplaceVisible(withReplace);
			openedBefore = true;
			String selected = editor == null ? "" : editor.getSelectedText();
			if (!selected.isEmpty()) find.setText(selected);
			setVisible(true);
			tabs.setContentHeader(this);
			refresh();
		}

		private void setReplaceVisible(boolean value) {
			replaceVisible = value;
			replacement.setVisible(value);
			replace.setVisible(value);
			replaceAll.setVisible(value);
			expand.setIcon(value ? SEARCH_NEXT_ICON : SEARCH_EXPAND_ICON);
			setIntrinsicPreferredGLSize(new GLSize(760,
					value ? REPLACE_HEIGHT : FIND_HEIGHT));
			updateExpandToolTip();
			if (isVisible()) tabs.setContentHeader(this);
		}

		private void changeTarget(GLCodeEditor editor) {
			if (target == editor) return;
			cancelScheduledSearch();
			if (target != null) target.clearSearch();
			target = editor;
			submittedQuery = null;
			if (isVisible()) scheduleLiveSearch();
			else refresh();
		}

		private void queryChanged() {
			submittedQuery = null;
			scheduleLiveSearch();
		}

		private void scheduleLiveSearch() {
			liveSearchGeneration++;
			liveSearchTimer.stop();
			if (target == null || find.getText().isEmpty()) {
				if (target != null) target.clearSearch();
				refresh();
				return;
			}
			liveSearchTimer.restart();
		}

		private void runLiveSearch() {
			GLCodeEditor requestedTarget = target;
			String query = find.getText();
			GLTextSearchOptions options = options();
			int generation = liveSearchGeneration;
			if (requestedTarget == null || query.isEmpty()) return;
			requestedTarget.startSearchAsync(query, options).whenComplete((count, failure) -> {
				if (failure != null || generation != liveSearchGeneration
						|| requestedTarget != target) return;
				submittedQuery = query;
				submittedOptions = options;
				refresh();
			});
		}

		private GLTextSearchOptions options() {
			return new GLTextSearchOptions(matchCase.isSelected(),
					wholeWord.isSelected(), regex.isSelected());
		}

		private void cancelScheduledSearch() {
			liveSearchGeneration++;
			liveSearchTimer.stop();
		}

		private void submit(boolean advance) {
			if (target == null) return;
			cancelScheduledSearch();
			String query = find.getText();
			GLTextSearchOptions options = options();
			if (advance && query.equals(submittedQuery)
					&& options.equals(submittedOptions))
				target.findNext(query, options);
			else target.startSearch(query, options);
			submittedQuery = query;
			submittedOptions = options;
			refresh();
		}

		private void previous() {
			if (target == null) return;
			cancelScheduledSearch();
			GLTextSearchOptions options = options();
			target.findPrevious(find.getText(), options);
			submittedQuery = find.getText();
			submittedOptions = options;
			refresh();
		}

		private void next() { submit(true); }

		private void replaceCurrent() {
			if (target == null || !target.isEditable()) return;
			cancelScheduledSearch();
			String query = find.getText();
			GLTextSearchOptions options = options();
			if (!query.equals(submittedQuery) || !options.equals(submittedOptions))
				target.startSearch(query, options);
			target.replaceCurrent(query, replacement.getText(), options);
			submittedQuery = query;
			submittedOptions = options;
			refresh();
		}

		private void replaceAll() {
			if (target == null || !target.isEditable()) return;
			cancelScheduledSearch();
			GLTextSearchOptions options = options();
			target.replaceAll(find.getText(), replacement.getText(), options);
			target.startSearch(find.getText(), options);
			submittedQuery = find.getText();
			submittedOptions = options;
			refresh();
		}

		private void close() {
			cancelScheduledSearch();
			if (target != null) target.clearSearch();
			submittedQuery = null;
			submittedOptions = null;
			setVisible(false);
			tabs.setContentHeader(this);
		}

		private void refresh() {
			int count = target == null ? 0 : target.getSearchMatchCount();
			int active = target == null ? -1 : target.getActiveSearchMatchIndex();
			String error = target == null ? null : target.getSearchPatternError();
			result.setText(error == null
					? (active < 0 ? 0 : active + 1) + " / " + count
					: getGLLocalizer().text("glswing.text.invalidPattern"));
			find.setToolTipText(error == null
					? getGLLocalizer().text("glswing.text.findLabel") : error);
			boolean searchable = target != null && !find.getText().isEmpty()
					&& error == null;
			previous.setEnabled(searchable);
			next.setEnabled(searchable);
			replace.setEnabled(searchable && target.isEditable());
			replaceAll.setEnabled(searchable && target.isEditable());
		}

		@Override protected void resized(float width, float height) {
			float pad = 7, gap = 5, rowHeight = 28, iconWidth = 30;
			float resultWidth = 64, optionWidth = 48;
			float fixedWidth = resultWidth + optionWidth * 3
					+ iconWidth * 4 + gap * 8;
			float fieldWidth = Math.max(40, width - pad * 2 - fixedWidth);
			float x = pad;
			expand.setBounds(x, 7, iconWidth, rowHeight); x += iconWidth + gap;
			find.setBounds(x, 7, fieldWidth, rowHeight); x += fieldWidth + gap;
			result.setBounds(x, 7, resultWidth, rowHeight); x += resultWidth + gap;
			x = layoutOption(matchCase, x, 7, optionWidth, rowHeight, gap);
			x = layoutOption(wholeWord, x, 7, optionWidth, rowHeight, gap);
			x = layoutOption(regex, x, 7, optionWidth, rowHeight, gap);
			previous.setBounds(x, 7, iconWidth, rowHeight); x += iconWidth + gap;
			next.setBounds(x, 7, iconWidth, rowHeight); x += iconWidth + gap;
			close.setBounds(x, 7, iconWidth, rowHeight);

			float fieldX = pad + iconWidth + gap;
			x = fieldX;
			replacement.setBounds(x, 43, fieldWidth, rowHeight);
			x += fieldWidth + gap;
			float available = Math.max(0, width - pad - x);
			float replaceWidth = Math.max(0, (available - gap) * .5f);
			replace.setBounds(x, 43, replaceWidth, rowHeight);
			x += replaceWidth + gap;
			replaceAll.setBounds(x, 43, Math.max(0, width - pad - x), rowHeight);
		}

		private float layoutOption(GLCheckBox option, float x, float y,
				float width, float rowHeight, float gap) {
			float height = Math.min(rowHeight, option.getPreferredGLSize().height());
			option.setBounds(x, y + (rowHeight - height) * .5f, width, height);
			return x + width + gap;
		}

		@Override protected void record(GLContext2D context) {
			GLMenuStyle style = getGLTheme().menuStyle();
			context.fillUIRoundRect(0, 0, getWidth(), getHeight(), 0,
					style.background());
			float separator = Math.max(1, style.borderWidth());
			context.fillUIRoundRect(0, Math.max(0, getHeight() - separator),
					getWidth(), separator, 0, style.borderColor());
		}

		@Override protected void localizerChanged() { updateText(); }

		private void updateText() {
			GLLocalizer text = getGLLocalizer();
			find.setHint(text.text("glswing.text.findLabel"));
			find.setToolTipText(text.text("glswing.text.findLabel"));
			replacement.setHint(text.text("glswing.text.replaceLabel"));
			replacement.setToolTipText(text.text("glswing.text.replaceLabel"));
			matchCase.setText("Aa");
			matchCase.setToolTipText(text.text("glswing.text.matchCase"));
			wholeWord.setText("W");
			wholeWord.setToolTipText(text.text("glswing.text.wholeWord"));
			regex.setText(".*");
			regex.setToolTipText(text.text("glswing.text.regex"));
			updateExpandToolTip();
			previous.setText("");
			previous.setToolTipText(text.text("glswing.text.findPrevious"));
			next.setText("");
			next.setToolTipText(text.text("glswing.text.findNext"));
			replace.setText(text.text("glswing.text.replaceCurrent"));
			replaceAll.setText(text.text("glswing.text.replaceAll"));
			close.setText("");
			close.setToolTipText(text.text("glswing.text.close"));
		}

		private void updateExpandToolTip() {
			expand.setToolTipText(getGLLocalizer().text(replaceVisible
					? "glswing.text.hideReplace" : "glswing.text.showReplace"));
		}

		private class SearchField extends GLTextEditor {
			private String hint = "";
			private void setHint(String value) {
				hint = value == null ? "" : value;
				invalidateRecording();
			}
			@Override protected void record(GLContext2D context) {
				super.record(context);
				if (!getText().isEmpty() || hint.isEmpty() || isFocused()) return;
				var style = getGLStyle();
				var font = getGLFont(style.font());
				float baseline = (getHeight() - font.getLineHeight()) * .5f
						+ font.getAscent();
				context.drawUIText(font, hint, style.padding().left(), baseline,
						style.disabledTextColor());
			}
		}
	}
}
