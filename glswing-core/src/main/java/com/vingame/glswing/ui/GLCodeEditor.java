package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.Timer;

import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLPointerController;
import com.vingame.glswing.input.GLPointerCursor;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLStroke;

/** Multiline source editor with code navigation and lexical or semantic colors. */
public class GLCodeEditor extends GLTextArea {
	private GLCodeLanguage language = GLCodeLanguages.JAVA;
	private volatile List<GLCodeToken> tokens = List.of();
	private volatile List<GLCodeSemanticToken> semanticTokens = List.of();
	private volatile List<GLCodeColor> documentColors = List.of();
	private GLPipettePreview colorPickerPipettePreview = GLPipettePreview.DEFAULT;
	private GLColorModel preferredColorOutputModel;
	private GLColorExpressionFormat colorExpressionFormat =
			GLColorExpressionFormat.DEFAULT;

	/** Configures the eyedropper preview in color pickers opened from this editor. */
	public final void setColorPickerPipettePreview(GLPipettePreview preview) {
		colorPickerPipettePreview = java.util.Objects.requireNonNull(preview);
	}
	public final GLPipettePreview getColorPickerPipettePreview() { return colorPickerPipettePreview; }
	/**
	 * Selects the model emitted by color editing and theme auto-formatting.
	 * {@code null}, the default, preserves the model of each source expression.
	 */
	public final void setPreferredColorOutputModel(GLColorModel value) {
		preferredColorOutputModel = value;
	}
	public final GLColorModel getPreferredColorOutputModel() {
		return preferredColorOutputModel;
	}
	/** Configures numeric color output for the picker and theme formatter. */
	public final void setColorExpressionFormat(GLColorExpressionFormat value) {
		colorExpressionFormat = java.util.Objects.requireNonNull(value);
	}
	public final GLColorExpressionFormat getColorExpressionFormat() {
		return colorExpressionFormat;
	}
	/** Opens the theme-aware color-expression settings dialog. */
	public final GLDialog showColorFormattingDialog() {
		if (!language.supportsColorFormatting())
			throw new IllegalStateException(
					"The active language does not support color formatting");
		return GLColorExpressionFormatDialog.show(this,
				preferredColorOutputModel, colorExpressionFormat, selection -> {
					preferredColorOutputModel = selection.preferredModel();
					colorExpressionFormat = selection.format();
				});
	}
	private volatile List<GLCodeInlayHint> inlayHints = List.of();
	private List<GLCodeInlayHint> signatureInlayHints = List.of();
	private volatile List<GLCodeDiagnostic> diagnostics = List.of();
	private List<GLCodeDiagnostic> coreDiagnostics = List.of();
	private final boolean coreDiagnosticsEnabled;
	private List<GLCodeDiagnostic> providerDiagnostics = List.of();
	private GLCodeDiagnosticProvider diagnosticProvider;
	private GLCodeDiagnosticProvider.Session diagnosticSession;
	private URI documentUri;
	private boolean automaticCodeIntelligence;
	private int documentVersion;
	private final Timer diagnosticTimer;
	private final Timer semanticTokenTimer;
	private final Timer documentColorTimer;
	private final Timer inlayHintTimer;
	private final Timer hoverTimer;
	private final Timer navigationDismissTimer;
	private com.vingame.glswing.input.GLMouseEvent hoverEvent;
	private GLCodeHover hover;
	private int hoverOffset = -1;
	private long hoverRequest;
	private java.util.concurrent.CompletableFuture<?> hoverFuture;
	private final GLPopupMenu completionMenu = new GLPopupMenu();
	private List<GLMenuItem> completionRows = List.of();
	private int selectedCompletion;
	private long completionRequest;
	private java.util.concurrent.CompletableFuture<?> completionFuture;
	private final GLPopupMenu signatureMenu = new GLPopupMenu();
	private long signatureRequest;
	private java.util.concurrent.CompletableFuture<?> signatureFuture;
	private final GLPopupMenu definitionMenu = new GLPopupMenu();
	private long definitionRequest;
	private java.util.concurrent.CompletableFuture<?> definitionFuture;
	private final GLPopupMenu navigationHoverMenu = new GLPopupMenu();
	private long navigationHoverRequest;
	private java.util.concurrent.CompletableFuture<?> navigationHoverFuture;
	private com.vingame.glswing.input.GLMouseEvent lastMouseEvent;
	private int navigationWordStart = -1;
	private int navigationWordEnd = -1;
	private int navigationRequestStart = -1;
	private int navigationRequestEnd = -1;
	private final Object navigationCursorOwner = new Object();
	private final Object colorPreviewCursorOwner = new Object();
	private GLPointerController navigationPointerController = GLPointerController.NONE;
	private long semanticTokenRequest;
	private java.util.concurrent.CompletableFuture<?> semanticTokenFuture;
	private long documentColorRequest;
	private java.util.concurrent.CompletableFuture<?> documentColorFuture;
	private long inlayHintRequest;
	private java.util.concurrent.CompletableFuture<?> inlayHintFuture;
	private boolean inlayHintsVisible = true;
	private GLCodeNavigationHandler navigationHandler = location -> { };
	private java.util.function.Consumer<GLCodeNavigationStatus> navigationStatusListener = value -> { };
	private java.util.function.Consumer<GLCodeCompletionStatus> completionStatusListener = value -> { };
	private java.util.function.Consumer<GLCodeSemanticStatus> semanticStatusListener = value -> { };
	private boolean insertSingleCompletion = true;
	private int indentationSize = 4;
	private Color keywordColor, stringColor, numberColor, commentColor, typeColor;
	private Color navigationLinkColor;
	private Map<String, Color> semanticColors = Map.of();
	private final GLPopupMenu codeMenu = new GLPopupMenu();
	private final GLActionMap codeActions = new GLActionMap();
	private final GLAction formatAction;
	private final GLAction commentAction;
	private final GLAction uncommentAction;
	private final GLAction organizeImportsAction;
	private final GLAction definitionAction;
	private final GLAction inlayHintsAction;
	private final GLAction colorFormattingAction;

	public GLCodeEditor() { this(new GLTextDocument(), true); }
	public GLCodeEditor(String source) { this(new GLTextDocument(source), true); }
	/** Creates an editor over an existing document model. */
	public GLCodeEditor(GLTextDocument document) { this(document, true); }
	/** Creates an editor and configures its language from the document URI. */
	public GLCodeEditor(String source, URI uri) {
		this(new GLTextDocument(source), true);
		configureDocument(uri);
	}
	/** Creates an editor over an existing document and configures its language. */
	public GLCodeEditor(GLTextDocument document, URI uri) {
		this(document, true);
		configureDocument(uri);
	}
	/** Creates an editor, optionally disabling lightweight diagnostics for source viewers. */
	public GLCodeEditor(String source, boolean coreDiagnosticsEnabled) {
		this(new GLTextDocument(source), coreDiagnosticsEnabled);
	}
	/** Creates an editor over an existing model with optional lightweight diagnostics. */
	public GLCodeEditor(GLTextDocument model, boolean coreDiagnosticsEnabled) {
		super(model);
		this.coreDiagnosticsEnabled = coreDiagnosticsEnabled;
		diagnosticTimer = new Timer(300, event -> validateSource());
		diagnosticTimer.setRepeats(false);
		semanticTokenTimer = new Timer(180, event -> requestSemanticTokens());
		semanticTokenTimer.setRepeats(false);
		documentColorTimer = new Timer(180, event -> requestDocumentColors());
		documentColorTimer.setRepeats(false);
		inlayHintTimer = new Timer(220, event -> requestInlayHints());
		inlayHintTimer.setRepeats(false);
		hoverTimer = new Timer(250, event -> requestHover());
		hoverTimer.setRepeats(false);
		navigationDismissTimer = new Timer(500,
				event -> clearNavigationHover(true));
		navigationDismissTimer.setRepeats(false);
		navigationHoverMenu.setOutsideInputBlocked(false);
		navigationHoverMenu.addPopupMenuListener(new GLPopupMenuListener() {
			@Override public void popupMenuClosed(GLPopupMenu menu) {
				navigationDismissTimer.stop();
				clearNavigationHover(false);
			}
			@Override public void popupMenuPointerEntered(GLPopupMenu menu) {
				navigationDismissTimer.stop();
			}
			@Override public void popupMenuPointerExited(GLPopupMenu menu) {
				scheduleNavigationDismiss();
			}
		});
		setLineWrap(false);
		setTabSize(indentationSize);
		applyCodeTheme();
		getDocument().addDocumentListener((document, change) -> {
			documentVersion++;
			closeCompletion();
			clearHover();
			clearNavigationHover(true);
			coreDiagnostics = List.of();
			providerDiagnostics = List.of();
			semanticTokens = retainUnaffectedSemanticTokens(semanticTokens, change);
			documentColors = retainDocumentColors(documentColors, change);
			inlayHints = retainInlayHints(inlayHints, change);
			signatureInlayHints = retainInlayHints(signatureInlayHints, change);
			invalidateRowLayout();
			mergeDiagnostics();
			retokenize();
			diagnosticTimer.restart();
			updateDiagnosticProvider();
			semanticTokenTimer.restart();
			documentColorTimer.restart();
			if (inlayHintsVisible) inlayHintTimer.restart();
		});
		setTextColorProvider(this::syntaxColor);
		formatAction = editableCodeAction(GLInputActions.CODE_FORMAT,
				GLUiKeys.CODE_FORMAT, this::formatCode);
		commentAction = editableCodeAction(GLInputActions.CODE_COMMENT,
				GLUiKeys.CODE_COMMENT, this::commentSelection);
		uncommentAction = editableCodeAction(GLInputActions.CODE_UNCOMMENT,
				GLUiKeys.CODE_UNCOMMENT, this::uncommentSelection);
		organizeImportsAction = editableCodeAction(
				GLInputActions.CODE_ORGANIZE_IMPORTS,
				GLUiKeys.CODE_ORGANIZE_IMPORTS, this::organizeImports);
		definitionAction = GLAction.builder(
				GLInputActions.CODE_GO_TO_DEFINITION)
				.textKey(GLUiKeys.CODE_GO_TO_DEFINITION)
				.onAction(this::goToDefinition).build();
		inlayHintsAction = GLAction.builder(
				GLInputActions.CODE_TOGGLE_INLAY_HINTS)
				.textKey(GLUiKeys.CODE_SHOW_INLAY_HINTS)
				.selectedWhen(this::isInlayHintsVisible)
				.onAction(() -> setInlayHintsVisible(!isInlayHintsVisible())).build();
		colorFormattingAction = GLAction.builder("code.color-formatting")
				.textKey(GLUiKeys.CODE_COLOR_FORMATTING)
				.onAction(this::showColorFormattingDialog).build();
		codeActions.add(formatAction).add(commentAction).add(uncommentAction)
				.add(organizeImportsAction).add(definitionAction).add(inlayHintsAction)
				.add(colorFormattingAction);
		updateCodeMenuText();
		retokenize();
		if (coreDiagnosticsEnabled) validateSource();
	}
	@Override public GLPopupMenu getContextMenu() {
		updateCodeMenuText();
		rebuildCodeMenu();
		return codeMenu;
	}
	private void rebuildCodeMenu() {
		codeMenu.removeAllItems();
		boolean hasGroup = false;
		if (language.supportsFormatting()) {
			codeMenu.addItem(formatAction);
			hasGroup = true;
		}
		if (language.supportsColorFormatting()) {
			if (hasGroup) codeMenu.addSeparator();
			codeMenu.addItem(colorFormattingAction);
			hasGroup = true;
		}
		if (language.supportsLineComments()) {
			if (hasGroup) codeMenu.addSeparator();
			codeMenu.addItem(commentAction);
			codeMenu.addItem(uncommentAction);
			hasGroup = true;
		}
		if (language.supportsOrganizeImports() && diagnosticSession != null) {
			if (hasGroup) codeMenu.addSeparator();
			codeMenu.addItem(organizeImportsAction);
			hasGroup = true;
		}
		if (language.supportsDefinitionNavigation() && diagnosticSession != null) {
			if (hasGroup) codeMenu.addSeparator();
			codeMenu.addItem(definitionAction);
			hasGroup = true;
		}
		if (language.supportsInlayHints() && diagnosticSession != null) {
			if (hasGroup) codeMenu.addSeparator();
			codeMenu.addItem(inlayHintsAction);
		}
	}
	private GLAction editableCodeAction(String id, String textKey, Runnable handler) {
		return GLAction.builder(id).textKey(textKey).enabledWhen(this::isEditable)
				.onAction(handler).build();
	}

	public final GLCodeLanguage getLanguage() { return language; }
	public final void setLanguage(GLCodeLanguage value) {
		if (value == null) throw new IllegalArgumentException("Code language must not be null");
		language = value; retokenize();
		if (coreDiagnosticsEnabled) validateSource();
		if (automaticCodeIntelligence) installAutomaticCodeIntelligence();
		else reopenDiagnosticProvider();
	}
	public final List<GLCodeDiagnostic> getDiagnostics() { return diagnostics; }
	/** Returns the latest immutable semantic-token snapshot from the provider. */
	public final List<GLCodeSemanticToken> getSemanticTokens() { return semanticTokens; }
	/** Returns the latest immutable source-color snapshot from the provider. */
	public final List<GLCodeColor> getDocumentColors() { return documentColors; }
	/** Returns the latest immutable visual-only hint snapshot. */
	public final List<GLCodeInlayHint> getInlayHints() { return inlayHints; }
	/** Returns whether visual-only parameter and type hints are displayed. */
	public final boolean isInlayHintsVisible() { return inlayHintsVisible; }
	/** Shows or hides parameter and type hints without changing document text. */
	public final void setInlayHintsVisible(boolean value) {
		if (inlayHintsVisible == value) return;
		inlayHintsVisible = value;
		if (value) requestInlayHints();
		else inlayHintTimer.stop();
		codeActions.refresh();
		invalidateRowLayout();
		invalidateRecording();
	}
	public final GLCodeDiagnosticProvider getDiagnosticProvider() { return diagnosticProvider; }
	public final URI getDocumentUri() { return documentUri; }
	/**
	 * Detects a built-in language from the URI and installs its in-process code
	 * intelligence. Unknown extensions retain the currently selected language.
	 */
	public final void configureDocument(URI uri) {
		if (uri == null) throw new IllegalArgumentException("Document URI is required");
		GLCodeLanguages.forUri(uri).ifPresent(value -> language = value);
		documentUri = uri;
		automaticCodeIntelligence = true;
		retokenize();
		if (coreDiagnosticsEnabled) validateSource();
		installAutomaticCodeIntelligence();
	}
	/** Sets the application callback used for definitions in other documents. */
	public final void setNavigationHandler(GLCodeNavigationHandler handler) {
		navigationHandler = handler == null ? location -> { } : handler;
	}
	public final GLCodeNavigationHandler getNavigationHandler() { return navigationHandler; }
	/** Receives request, result and failure states for definition navigation. */
	public final void setNavigationStatusListener(
			java.util.function.Consumer<GLCodeNavigationStatus> listener) {
		navigationStatusListener = listener == null ? value -> { } : listener;
	}
	/** Returns whether an explicitly requested single proposal is inserted immediately. */
	public final boolean isInsertSingleCompletion() { return insertSingleCompletion; }
	/** Controls immediate insertion of a single manual completion proposal. */
	public final void setInsertSingleCompletion(boolean value) {
		insertSingleCompletion = value;
	}
	/** Receives request, result and failure states for code completion. */
	public final void setCompletionStatusListener(
			java.util.function.Consumer<GLCodeCompletionStatus> listener) {
		completionStatusListener = listener == null ? value -> { } : listener;
	}
	/** Receives request, result and failure states for semantic highlighting. */
	public final void setSemanticStatusListener(
			java.util.function.Consumer<GLCodeSemanticStatus> listener) {
		semanticStatusListener = listener == null ? value -> { } : listener;
	}
	/** Replaces automatic code intelligence with an explicit provider for one URI. */
	public final void setDiagnosticProvider(GLCodeDiagnosticProvider provider, URI uri) {
		if ((provider == null) != (uri == null))
			throw new IllegalArgumentException("Provider and document URI must both be set or cleared");
		closeDiagnosticSession();
		automaticCodeIntelligence = false;
		diagnosticProvider = provider;
		documentUri = uri;
		providerDiagnostics = List.of();
		mergeDiagnostics();
		if (provider != null) openDiagnosticSession();
	}
	/** Disconnects the provider without closing a potentially shared provider. */
	public final void clearDiagnosticProvider() { setDiagnosticProvider(null, null); }
	public final int getIndentationSize() { return indentationSize; }
	public final void setIndentationSize(int value) {
		if (value < 1) throw new IllegalArgumentException("Indentation size must be positive");
		indentationSize = value; setTabSize(value);
	}
	public final void toggleLineComment() { commentSelection(); }
	public final void commentSelection() { transformLines(true); }
	public final void uncommentSelection() { transformLines(false); }
	public final void formatCode() {
		Range range = commandRange();
		String[] lines = getDocument().getText(range.start, range.end).split("\\n", -1);
		StringBuilder formatted = new StringBuilder();
		int level = 0;
		for (int index = 0; index < lines.length; index++) {
			String line = normalizeSpacing(lines[index].strip());
			if (line.startsWith("}")) level = Math.max(0, level - 1);
			if (!line.isEmpty()) formatted.append(" ".repeat(level * indentationSize));
			formatted.append(line);
			if (index + 1 < lines.length) formatted.append('\n');
			if (line.endsWith("{") && !line.startsWith("//")) level++;
		}
		String replacement = formatted.toString();
		if (language == GLCodeLanguages.GLTHEME
				&& language instanceof GLPropertiesCodeLanguage properties)
			replacement = properties.formatColorValues(replacement,
					preferredColorOutputModel, colorExpressionFormat);
		applyCommand(range, replacement);
	}
	/** Applies the provider's document-wide organize-imports operation. */
	public final void organizeImports() {
		if (diagnosticSession == null || documentUri == null) return;
		int version = documentVersion;
		diagnosticSession.organizeImports(diagnosticDocument()).whenComplete((edits, failure) ->
				EventQueue.invokeLater(() -> {
					if (failure != null || edits == null || edits.isEmpty()
							|| version != documentVersion) return;
					applyProviderEdits(edits);
				}));
	}
	/** Resolves and navigates to the definition at the current caret. */
	public final void goToDefinition() { requestDefinitions(getCaretPosition()); }
	/** Resolves and navigates to an implementation at the current caret. */
	public final void goToImplementation() {
		if (diagnosticSession == null || documentUri == null) return;
		int offset = definitionOffset(getCaretPosition());
		int version = documentVersion;
		diagnosticSession.implementations(diagnosticDocument(), offset)
				.whenComplete((locations, failure) -> EventQueue.invokeLater(() -> {
					if (failure != null || version != documentVersion
							|| locations == null || locations.isEmpty()) return;
					navigateLocations(locations);
				}));
	}
	/** Opens the first-level incoming and outgoing call hierarchy at the caret. */
	public final void openCallHierarchy() {
		requestCallHierarchy(definitionOffset(getCaretPosition()));
	}
	private void requestDefinitions(int offset) {
		if (diagnosticSession == null || documentUri == null) {
			navigationStatusListener.accept(GLCodeNavigationStatus.failed(
					new IllegalStateException("No language server is connected")));
			return;
		}
		if (offset < 0 || offset > getDocument().length()) return;
		offset = definitionOffset(offset);
		navigationStatusListener.accept(GLCodeNavigationStatus.requesting());
		long request = ++definitionRequest;
		int version = documentVersion;
		var stage = diagnosticSession.definitions(diagnosticDocument(), offset);
		definitionFuture = stage.toCompletableFuture();
		stage.whenComplete((locations, failure) -> EventQueue.invokeLater(() -> {
			if (request != definitionRequest || version != documentVersion) return;
			if (failure != null) {
				navigationStatusListener.accept(GLCodeNavigationStatus.failed(failure));
				return;
			}
			List<GLCodeLocation> targets = locations == null ? List.of() : locations;
			navigationStatusListener.accept(GLCodeNavigationStatus.completed(targets.size()));
			if (targets.size() == 1) navigateTo(targets.get(0));
			else showDefinitions(targets);
		}));
	}
	private int definitionOffset(int offset) {
		String source = getText();
		if (offset > 0 && (offset == source.length()
				|| !Character.isJavaIdentifierPart(source.charAt(offset)))
				&& Character.isJavaIdentifierPart(source.charAt(offset - 1))) return offset - 1;
		return offset;
	}
	private boolean requestNavigationHover(
			com.vingame.glswing.input.GLMouseEvent event) {
		if (diagnosticSession == null || documentUri == null
				|| event == null || !event.hasLocalPosition()
				|| isOverviewRuler(event.getLocalX()) || diagnosticAt(event) != null) return false;
		int offset = definitionOffset(positionAt(event.getLocalX(), event.getLocalY()));
		Range word = identifierRangeAt(offset);
		if (word == null) {
			leaveNavigationTarget();
			return false;
		}
		navigationDismissTimer.stop();
		if (word.start() == navigationWordStart && word.end() == navigationWordEnd
				&& navigationHoverMenu.isShowing()) {
			navigationPointerController = event.getPointerController();
			navigationPointerController.setCursor(navigationCursorOwner, GLPointerCursor.HAND);
			return true;
		}
		if (word.start() == navigationRequestStart
				&& word.end() == navigationRequestEnd) {
			return true;
		}
		clearNavigationHover(true);
		navigationRequestStart = word.start();
		navigationRequestEnd = word.end();
		long request = ++navigationHoverRequest;
		int version = documentVersion;
		GLCodeDocument snapshot = diagnosticDocument();
		var definitions = diagnosticSession.definitions(snapshot, offset).toCompletableFuture()
				.exceptionally(failure -> List.of());
		var implementations = diagnosticSession.implementations(snapshot, offset)
				.toCompletableFuture().exceptionally(failure -> List.of());
		var hierarchy = diagnosticSession.canOpenCallHierarchy(snapshot, offset)
				.toCompletableFuture().exceptionally(failure -> false);
		var combined = java.util.concurrent.CompletableFuture.allOf(
				definitions, implementations, hierarchy);
		navigationHoverFuture = combined;
		combined.whenComplete((ignored, failure) -> EventQueue.invokeLater(() -> {
			if (request != navigationHoverRequest || version != documentVersion) return;
			navigationHoverFuture = null;
			List<GLCodeLocation> definitionTargets = definitions.join();
			List<GLCodeLocation> implementationTargets = implementations.join();
			boolean callHierarchy = hierarchy.join();
			if (definitionTargets.isEmpty() && implementationTargets.isEmpty()
					&& !callHierarchy) {
				return;
			}
			navigationWordStart = word.start();
			navigationWordEnd = word.end();
			navigationPointerController = event.getPointerController();
			navigationPointerController.setCursor(navigationCursorOwner, GLPointerCursor.HAND);
			showNavigationHover(event, definitionTargets,
					implementationTargets, callHierarchy, offset);
			invalidateRecording();
		}));
		return true;
	}
	private Range identifierRangeAt(int offset) {
		String source = getText();
		if (source.isEmpty() || offset < 0 || offset >= source.length()) return null;
		for (GLCodeSemanticToken token : semanticTokens) {
			if (offset < token.start()) break;
			if (offset < token.end()) return new Range(token.start(), token.end());
		}
		if (!Character.isJavaIdentifierPart(source.charAt(offset))) return null;
		int start = offset;
		while (start > 0 && Character.isJavaIdentifierPart(source.charAt(start - 1)))
			start--;
		int end = offset + 1;
		while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end)))
			end++;
		return new Range(start, end);
	}
	private void showNavigationHover(com.vingame.glswing.input.GLMouseEvent event,
			List<GLCodeLocation> definitions, List<GLCodeLocation> implementations,
			boolean callHierarchy, int offset) {
		navigationHoverMenu.close();
		navigationHoverMenu.removeAllItems();
		if (!definitions.isEmpty()) {
			GLMenuItem item = navigationHoverMenu.addItem(
					getGLLocalizer().text(GLUiKeys.CODE_OPEN_DECLARATION), ignored -> {
						clearNavigationHover(false);
						navigateLocations(definitions);
					});
			item.setMnemonicEnabled(false);
		}
		if (!implementations.isEmpty()) {
			GLMenuItem item = navigationHoverMenu.addItem(
					getGLLocalizer().text(GLUiKeys.CODE_OPEN_IMPLEMENTATION), ignored -> {
						clearNavigationHover(false);
						navigateLocations(implementations);
					});
			item.setMnemonicEnabled(false);
		}
		if (callHierarchy) {
			GLMenuItem item = navigationHoverMenu.addItem(
					getGLLocalizer().text(GLUiKeys.CODE_OPEN_CALL_HIERARCHY), ignored -> {
						clearNavigationHover(false);
						requestCallHierarchy(offset);
					});
			item.setMnemonicEnabled(false);
		}
		GLParent host = popupHost();
		if (host != null && !navigationHoverMenu.getItems().isEmpty())
			navigationHoverMenu.show(host, event.getScreenX(), event.getScreenY() + 12);
	}
	private void requestCallHierarchy(int offset) {
		if (diagnosticSession == null || documentUri == null) return;
		int version = documentVersion;
		var stage = diagnosticSession.callHierarchy(diagnosticDocument(), offset);
		stage.whenComplete((hierarchy, failure) -> EventQueue.invokeLater(() -> {
			if (failure != null || version != documentVersion
					|| hierarchy == null || hierarchy.isEmpty()) return;
			showCallHierarchy(hierarchy.get());
		}));
	}
	private void navigateLocations(List<GLCodeLocation> locations) {
		if (locations.size() == 1) navigateTo(locations.get(0));
		else showDefinitions(locations);
	}
	private void showCallHierarchy(GLCodeCallHierarchy hierarchy) {
		definitionMenu.close();
		definitionMenu.removeAllItems();
		addHierarchyHeading(GLUiKeys.CODE_INCOMING_CALLS);
		if (hierarchy.incoming().isEmpty()) addHierarchyHeading(GLUiKeys.CODE_NO_CALLS);
		else for (GLCodeCall call : hierarchy.incoming().stream().limit(20).toList())
			addCallLocation("← " + call.label(), call.location());
		definitionMenu.addSeparator();
		addHierarchyHeading(GLUiKeys.CODE_OUTGOING_CALLS);
		if (hierarchy.outgoing().isEmpty()) addHierarchyHeading(GLUiKeys.CODE_NO_CALLS);
		else for (GLCodeCall call : hierarchy.outgoing().stream().limit(20).toList())
			addCallLocation("→ " + call.label(), call.location());
		float[] caretPosition = caretScreenPosition();
		GLParent host = popupHost();
		if (caretPosition != null && host != null)
			definitionMenu.show(host, caretPosition[0], caretPosition[1]);
	}
	private void addHierarchyHeading(String key) {
		GLMenuItem heading = definitionMenu.addItem(getGLLocalizer().text(key), event -> { });
		heading.setEnabled(false);
		heading.setMnemonicEnabled(false);
	}
	private void addCallLocation(String label, GLCodeLocation location) {
		GLMenuItem row = definitionMenu.addItem(label, event -> navigateTo(location));
		row.setAcceleratorText(location.uri().toString());
		row.setMnemonicEnabled(false);
	}
	private void clearNavigationHover(boolean closeMenu) {
		navigationDismissTimer.stop();
		navigationHoverRequest++;
		if (navigationHoverFuture != null) navigationHoverFuture.cancel(false);
		navigationHoverFuture = null;
		if (closeMenu) navigationHoverMenu.close();
		navigationPointerController.clearCursor(navigationCursorOwner);
		navigationPointerController = GLPointerController.NONE;
		navigationRequestStart = -1;
		navigationRequestEnd = -1;
		if (navigationWordStart >= 0 || navigationWordEnd >= 0) {
			navigationWordStart = -1;
			navigationWordEnd = -1;
			invalidateRecording();
		}
	}
	private void leaveNavigationTarget() {
		if (navigationHoverMenu.isShowing()) scheduleNavigationDismiss();
		else clearNavigationHover(true);
	}
	private void scheduleNavigationDismiss() {
		if (navigationHoverMenu.isShowing()) navigationDismissTimer.restart();
	}
	private void showDefinitions(List<GLCodeLocation> locations) {
		definitionMenu.close();
		definitionMenu.removeAllItems();
		for (GLCodeLocation location : locations.stream().limit(30).toList()) {
			String path = location.uri().getPath();
			String name = path == null || path.isBlank() ? location.uri().toString()
					: path.substring(path.lastIndexOf('/') + 1);
			GLMenuItem row = definitionMenu.addItem(name + ":" + (location.line() + 1),
					event -> navigateTo(location));
			row.setAcceleratorText(location.uri().toString());
			row.setMnemonicEnabled(false);
		}
		if (locations.isEmpty()) return;
		float[] caretPosition = caretScreenPosition();
		GLParent host = popupHost();
		if (caretPosition != null && host != null)
			definitionMenu.show(host, caretPosition[0], caretPosition[1]);
	}
	private void navigateTo(GLCodeLocation location) {
		definitionMenu.close();
		if (documentUri != null && documentUri.equals(location.uri())
				&& location.line() < getDocument().getLineCount()) {
			int start = getDocument().getLineStart(location.line());
			int end = getDocument().getLineEnd(location.line());
			setCaretPosition(Math.min(end, start + location.character()));
			navigationHandler.navigate(location);
			return;
		}
		navigationHandler.navigate(location);
	}
	private void applyProviderEdits(List<GLCodeTextEdit> edits) {
		ArrayList<TextReplacement> replacements = new ArrayList<>();
		int nextCaret = getCaretPosition();
		for (GLCodeTextEdit edit : edits.stream()
				.sorted(java.util.Comparator.comparingInt(GLCodeTextEdit::start)).toList()) {
			replacements.add(new TextReplacement(edit.start(), edit.end(), edit.text()));
			if (edit.end() <= nextCaret)
				nextCaret += edit.text().length() - (edit.end() - edit.start());
			else if (edit.start() < nextCaret)
				nextCaret = edit.start() + edit.text().length();
		}
		replaceTextAtomically(replacements, nextCaret);
	}
	private void transformLines(boolean comment) {
		Range range = lineCommandRange();
		String prefix = language.lineCommentPrefix();
		String[] lines = getDocument().getText(range.start, range.end).split("\\n", -1);
		StringBuilder changed = new StringBuilder();
		for (int index = 0; index < lines.length; index++) {
			String line = lines[index];
			int content = 0;
			while (content < line.length() && Character.isWhitespace(line.charAt(content))) content++;
			if (comment) line = line.substring(0, content) + prefix + line.substring(content);
			else if (line.startsWith(prefix, content))
				line = line.substring(0, content) + line.substring(content + prefix.length());
			changed.append(line);
			if (index + 1 < lines.length) changed.append('\n');
		}
		applyCommand(range, changed.toString());
	}
	private Range commandRange() {
		if (getSelectionStart() == getSelectionEnd())
			return new Range(0, getDocument().length());
		return selectedLineRange();
	}
	private Range lineCommandRange() {
		if (getSelectionStart() == getSelectionEnd()) {
			int line = getDocument().getLineOfOffset(getCaretPosition());
			return new Range(getDocument().getLineStart(line),
					getDocument().getLineEnd(line));
		}
		return selectedLineRange();
	}
	private Range selectedLineRange() {
		int first = getDocument().getLineOfOffset(getSelectionStart());
		int lastOffset = Math.max(getSelectionStart(), getSelectionEnd() - 1);
		int last = getDocument().getLineOfOffset(lastOffset);
		return new Range(getDocument().getLineStart(first), getDocument().getLineEnd(last));
	}
	private void applyCommand(Range range, String replacement) {
		boolean hadSelection = getSelectionStart() != getSelectionEnd();
		int relativeCaret = Math.max(0, getCaretPosition() - range.start);
		select(range.start, range.end);
		replaceSelection(replacement);
		if (hadSelection)
			select(range.start, range.start + replacement.length());
		else
			setCaretPosition(range.start + Math.min(relativeCaret, replacement.length()));
		commitEditingSession();
	}
	private record Range(int start, int end) { }
	private String normalizeSpacing(String line) {
		int comment = lineCommentStart(line);
		String code = comment < 0 ? line : line.substring(0, comment).stripTrailing();
		String suffix = comment < 0 ? "" : (code.isEmpty() ? "" : " ") + line.substring(comment);
		StringBuilder result = new StringBuilder();
		boolean quoted = false;
		char quote = 0;
		for (int index = 0; index < code.length(); index++) {
			char value = code.charAt(index);
			if (quoted) {
				result.append(value);
				if (value == '\\' && index + 1 < code.length()) result.append(code.charAt(++index));
				else if (value == quote) quoted = false;
				continue;
			}
			if (value == '"' || value == '\'') { quoted = true; quote = value; result.append(value); continue; }
			if (Character.isWhitespace(value)) {
				if (!result.isEmpty() && result.charAt(result.length() - 1) != ' ') result.append(' ');
				continue;
			}
			if (value == ',') {
				trimTrailingSpace(result); result.append(", ");
				while (index + 1 < code.length() && Character.isWhitespace(code.charAt(index + 1))) index++;
				continue;
			}
			if (value == '{') {
				trimTrailingSpace(result);
				if (!result.isEmpty()) result.append(' ');
				result.append('{');
				continue;
			}
			result.append(value);
		}
		String normalized = result.toString().stripTrailing();
		normalized = normalized.replaceAll("\\b(if|for|while|switch|catch|synchronized)\\s*\\(", "$1 (");
		normalized = normalized.replaceAll("\\s+\\)", ")").replaceAll("\\(\\s+", "(");
		return normalized + suffix;
	}
	private static void trimTrailingSpace(StringBuilder value) {
		while (!value.isEmpty() && value.charAt(value.length() - 1) == ' ')
			value.setLength(value.length() - 1);
	}
	private static int lineCommentStart(String line) {
		boolean quoted = false; char quote = 0;
		for (int index = 0; index + 1 < line.length(); index++) {
			char value = line.charAt(index);
			if (quoted) {
				if (value == '\\') index++;
				else if (value == quote) quoted = false;
			} else if (value == '"' || value == '\'') { quoted = true; quote = value; }
			else if (value == '/' && line.charAt(index + 1) == '/') return index;
		}
		return -1;
	}

	@Override protected void controlKeyPressed(GLKeyEvent event) {
		if ((event.getKeyCode() == KeyEvent.VK_CONTROL
				|| event.getKeyCode() == KeyEvent.VK_META)
				&& lastMouseEvent != null) {
			hoverTimer.stop();
			GLToolTipManager.leave(this);
			requestNavigationHover(lastMouseEvent);
		}
		if (completionMenu.isShowing()) {
			if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
				closeCompletion(); event.consume(); return;
			}
			if (event.getKeyCode() == KeyEvent.VK_UP || event.getKeyCode() == KeyEvent.VK_DOWN) {
				selectCompletion(selectedCompletion
						+ (event.getKeyCode() == KeyEvent.VK_UP ? -1 : 1));
				event.consume(); return;
			}
			if (event.getKeyCode() == KeyEvent.VK_ENTER || event.getKeyCode() == KeyEvent.VK_TAB) {
				if (!completionRows.isEmpty()) completionRows.get(selectedCompletion).doClick();
				event.consume(); return;
			}
		}
		if (isCompletionShortcut(event)) {
			requestCompletion(null); event.consume(); return;
		}
		if (getGLInputMap().matches(GLInputActions.TEXT_TOGGLE_BOLD, event)
				|| getGLInputMap().matches(GLInputActions.TEXT_TOGGLE_ITALIC, event)) {
			event.consume();
			return;
		}
		if (codeActions.handleKeyPressed(event, getGLInputMap(), this)) return;
		if (event.getKeyCode() == KeyEvent.VK_ENTER && isEditable()) {
			int caret = getCaretPosition();
			int line = getDocument().getLineOfOffset(caret);
			String before = getDocument().getText(getDocument().getLineStart(line), caret);
			int whitespace = 0;
			while (whitespace < before.length() && Character.isWhitespace(before.charAt(whitespace))) whitespace++;
			String outerIndent = before.substring(0, whitespace);
			boolean opensBlock = before.stripTrailing().endsWith("{");
			boolean beforeGeneratedClose = caret < getText().length()
					&& getText().charAt(caret) == '}';
			if (opensBlock && beforeGeneratedClose) {
				String innerIndent = outerIndent + " ".repeat(indentationSize);
				replaceSelection("\n" + innerIndent + "\n" + outerIndent);
				setCaretPosition(caret + 1 + innerIndent.length());
			} else {
				String indent = opensBlock
						? outerIndent + " ".repeat(indentationSize) : outerIndent;
				replaceSelection("\n" + indent);
			}
			event.consume();
			return;
		}
		super.controlKeyPressed(event);
	}
	private boolean isCompletionShortcut(GLKeyEvent event) {
		if (getGLInputMap().matches(GLInputActions.CODE_COMPLETE, event)) return true;
		if (!getGLInputMap().getKeyBindings(GLInputActions.CODE_COMPLETE).isEmpty()
				|| event.getKeyCode() != KeyEvent.VK_SPACE) return false;
		int shortcut = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
				.contains("mac") ? java.awt.event.InputEvent.META_DOWN_MASK
						: java.awt.event.InputEvent.CTRL_DOWN_MASK;
		int relevant = java.awt.event.InputEvent.CTRL_DOWN_MASK
				| java.awt.event.InputEvent.META_DOWN_MASK
				| java.awt.event.InputEvent.ALT_DOWN_MASK
				| java.awt.event.InputEvent.SHIFT_DOWN_MASK
				| java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK;
		return (event.getModifiersEx() & relevant) == shortcut;
	}
	@Override protected void controlKeyTyped(GLKeyEvent event) {
		if (event.getKeyChar() == ' '
				&& ((event.getModifiersEx() & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0
						|| (event.getModifiersEx() & java.awt.event.InputEvent.META_DOWN_MASK) != 0)) {
			requestCompletion(null);
			event.consume();
			return;
		}
		if (isEditable() && getSelectionStart() == getSelectionEnd()) {
			char typed = event.getKeyChar();
			if (")]}'\"".indexOf(typed) >= 0
					&& getCaretPosition() < getText().length()
					&& getText().charAt(getCaretPosition()) == typed) {
				setCaretPosition(getCaretPosition() + 1);
				if (typed == ')') closeSignatureHelp();
				event.consume();
				return;
			}
			char close = switch (typed) {
				case '(' -> ')'; case '[' -> ']'; case '{' -> '}'; case '"' -> '"'; case '\'' -> '\'';
				default -> 0;
			};
			if (close != 0) {
				int caret = getCaretPosition();
				replaceSelection("" + typed + close);
				setCaretPosition(caret + 1);
				if (typed == '(') requestSignatureHelp('(');
				event.consume();
				return;
			}
		}
		super.controlKeyTyped(event);
		if (event.getKeyChar() == '.') requestCompletion('.');
		else if (event.getKeyChar() == ',') requestSignatureHelp(',');
		else if (event.getKeyChar() == ')') closeSignatureHelp();
	}
	@Override protected float leadingTextInset() {
		int digits = Integer.toString(Math.max(1, getDocument().getLineCount())).length();
		return getGLStyle().font().measurementFont().measureText("9".repeat(digits)) + 18;
	}
	@Override protected void selectWordAt(int offset) {
		String source = getText();
		if (source.isEmpty()) return;
		int probe = Math.min(offset, source.length() - 1);
		if (!Character.isJavaIdentifierPart(source.codePointAt(probe))
				&& probe > 0 && Character.isJavaIdentifierPart(source.codePointBefore(probe)))
			probe = source.offsetByCodePoints(probe, -1);
		if (!Character.isJavaIdentifierPart(source.codePointAt(probe))) {
			select(probe, source.offsetByCodePoints(probe, 1));
			return;
		}
		int start = probe;
		while (start > 0) {
			int codePoint = source.codePointBefore(start);
			if (!Character.isJavaIdentifierPart(codePoint)) break;
			start -= Character.charCount(codePoint);
		}
		int end = probe;
		while (end < source.length()) {
			int codePoint = source.codePointAt(end);
			if (!Character.isJavaIdentifierPart(codePoint)) break;
			end += Character.charCount(codePoint);
		}
		select(start, end);
	}
	@Override protected void drawLeadingDecoration(GLContext2D context, GLFont font, GLInsets padding) {
		float width = leadingTextInset() - 6;
		float height = Math.max(0, getHeight() - padding.vertical());
		float bottomLeft = Math.max(0, containerRadii().bottomLeft() - padding.left());
		context.fillUIRoundRect(padding.left(), padding.top(), width, height,
				new com.vingame.glswing.rendering.GLCornerRadii(0, 0, 0, bottomLeft),
				new Color(0, 0, 0, 28));
		float lineHeight = font.getLineHeight();
		int first = Math.max(0, (int) Math.floor(getVerticalScrollOffset() / lineHeight));
		int last = Math.min(getDocument().getLineCount(), first
				+ (int) Math.ceil(getHeight() / lineHeight) + 2);
		for (int line = first; line < last; line++) {
			float baseline = padding.top() + line * lineHeight - getVerticalScrollOffset() + font.getAscent();
			if (baseline >= padding.top() && baseline <= getHeight() - padding.bottom() + lineHeight) {
				GLCodeDiagnostic diagnostic = diagnosticForLine(line);
				if (diagnostic != null)
					context.fillUIRoundRect(padding.left() + 2,
							baseline - font.getAscent() + 2, 3,
							Math.max(3, lineHeight - 4), 1.5f,
							diagnosticColor(diagnostic));
				String number = Integer.toString(line + 1);
				context.drawUIText(font, number,
						padding.left() + width - font.measureText(number) - 5,
						baseline, new Color(145, 145, 145));
			}
		}
	}
	@Override protected void drawTextDecorations(GLContext2D context, GLFont font,
			int rowStart, int rowEnd, float x, float baseline, float available) {
		int linkStart = Math.max(rowStart, navigationWordStart);
		int linkEnd = Math.min(rowEnd, navigationWordEnd);
		if (linkEnd > linkStart) {
			float left = x + measureRange(rowStart, linkStart);
			float right = Math.max(left + 1, x + measureRange(rowStart, linkEnd));
			context.drawUILine(left, baseline + 1, right, baseline + 1,
					navigationLinkColor);
		}
		for (GLCodeDiagnostic diagnostic : diagnostics) {
			int start = Math.max(rowStart, diagnostic.start());
			int end = Math.min(rowEnd, diagnostic.end());
			if (end <= start) continue;
			float left = x + measureRange(rowStart, start);
			float right = Math.max(left + 3, x + measureRange(rowStart, end));
			float y = baseline + 2;
			Color color = diagnosticColor(diagnostic);
			for (float wave = left; wave < right; wave += 4)
				context.drawUILine(wave, y, Math.min(wave + 2, right), y + 1.5f, color);
		}
		for (GLCodeColor color : documentColors) {
			if (color.start() < rowStart || color.end() > rowEnd) continue;
			float previewX = x + measureRange(rowStart, color.end()) + colorPreviewGap();
			float size = colorPreviewSize(font);
			float previewY = baseline - font.getAscent()
					+ (font.getLineHeight() - size) / 2;
			drawColorPreview(context, previewX, previewY, size, color.color());
		}
	}
	private void drawColorPreview(GLContext2D context, float x, float y,
			float size, Color color) {
		GLThemeValues values = getGLTheme().values();
		Color light = values.getColor("codeEditor.colorPreviewCheckerLight",
				new Color(225, 225, 225));
		Color dark = values.getColor("codeEditor.colorPreviewCheckerDark",
				new Color(145, 145, 145));
		float inner = Math.max(2, size - 2), half = inner / 2;
		context.fillUIRoundRect(x + 1, y + 1, half, half, 0, light);
		context.fillUIRoundRect(x + 1 + half, y + 1, inner - half, half, 0, dark);
		context.fillUIRoundRect(x + 1, y + 1 + half, half, inner - half, 0, dark);
		context.fillUIRoundRect(x + 1 + half, y + 1 + half,
				inner - half, inner - half, 0, light);
		context.fillUIRoundRect(x + 1, y + 1, inner, inner, 1, color);
		context.drawUIRoundRect(x, y, size, size, 2, 1,
				values.getColor("codeEditor.colorPreviewBorder", new Color(35, 35, 35)));
	}
	@Override protected float trailingRowDecorationWidth(int rowStart, int rowEnd) {
		for (GLCodeColor color : documentColors)
			if (color.start() >= rowStart && color.end() <= rowEnd)
				return colorPreviewGap()
						+ colorPreviewSize(getGLStyle().font().measurementFont());
		return 0;
	}
	private float colorPreviewSize(GLFont font) {
		return Math.max(5, Math.min(font.getLineHeight() - 3,
				getGLTheme().values().getFloat("codeEditor.colorPreviewSize", 11)));
	}
	private float colorPreviewGap() {
		return Math.max(0, getGLTheme().values()
				.getFloat("codeEditor.colorPreviewGap", 6));
	}
	@Override protected float trailingDecorationWidth() {
		GLThemeValues values = getGLTheme().values();
		return values.getFloat("codeEditor.overviewRulerWidth", 7)
				+ values.getFloat("codeEditor.overviewRulerGap", 3);
	}
	@Override protected void drawTrailingDecoration(GLContext2D context, float x,
			float y, float width, float height) {
		if (diagnostics.isEmpty() || width <= 0 || height <= 0) return;
		GLThemeValues values = getGLTheme().values();
		float rulerWidth = values.getFloat("codeEditor.overviewRulerWidth", 7);
		float markerHeight = Math.max(2,
				values.getFloat("codeEditor.overviewMarkerHeight", 3));
		context.fillUIRoundRect(x, y, rulerWidth, height,
				Math.min(2, rulerWidth / 2),
				values.getColor("codeEditor.overviewRulerBackground",
						new Color(0, 0, 0, 25)));
		for (GLCodeDiagnostic.Severity severity : new GLCodeDiagnostic.Severity[] {
				GLCodeDiagnostic.Severity.WARNING, GLCodeDiagnostic.Severity.ERROR })
			for (GLCodeDiagnostic diagnostic : diagnostics)
				if (diagnostic.severity() == severity) {
					float markerY = overviewMarkerY(diagnostic, y, height, markerHeight);
					context.fillUIRoundRect(x, markerY, rulerWidth, markerHeight,
							Math.min(1.5f, markerHeight / 2), diagnosticColor(diagnostic));
				}
	}
	@Override protected boolean trailingDecorationPressed(float localY) {
		GLCodeDiagnostic diagnostic = overviewDiagnosticAt(localY, false);
		if (diagnostic != null)
			setCaretPosition(Math.min(diagnostic.start(), getDocument().length()));
		return true;
	}
	private float overviewMarkerY(GLCodeDiagnostic diagnostic, float y, float height,
			float markerHeight) {
		int lines = Math.max(1, getDocument().getLineCount());
		int line = getDocument().getLineOfOffset(
				Math.min(diagnostic.start(), getDocument().length()));
		float fraction = lines <= 1 ? 0 : (float) line / (lines - 1);
		return y + fraction * Math.max(0, height - markerHeight);
	}
	private GLCodeDiagnostic overviewDiagnosticAt(float localY, boolean closeOnly) {
		if (diagnostics.isEmpty()) return null;
		float y = getGLStyle().padding().top();
		float height = Math.max(1, getHeight() - getGLStyle().padding().vertical());
		float markerHeight = Math.max(2, getGLTheme().values()
				.getFloat("codeEditor.overviewMarkerHeight", 3));
		GLCodeDiagnostic nearest = null;
		float distance = Float.MAX_VALUE;
		for (GLCodeDiagnostic diagnostic : diagnostics) {
			float center = overviewMarkerY(diagnostic, y, height, markerHeight)
					+ markerHeight / 2;
			float candidate = Math.abs(localY - center);
			if (candidate < distance) { distance = candidate; nearest = diagnostic; }
		}
		return closeOnly && distance > Math.max(4, markerHeight) ? null : nearest;
	}
	@Override protected String toolTipTextAt(com.vingame.glswing.input.GLMouseEvent event) {
		GLCodeColor sourceColor = colorPreviewAt(event);
		if (sourceColor != null) return colorDescription(sourceColor.color());
		GLCodeDiagnostic diagnostic = diagnosticAt(event);
		if (diagnostic != null) return diagnostic.message();
		if (hover != null && event.hasLocalPosition()) {
			int offset = positionAt(event.getLocalX(), event.getLocalY());
			if (offset >= hover.start() && offset < hover.end()) return hover.contents();
		}
		return super.toolTipTextAt(event);
	}
	@Override protected void controlMousePressed(com.vingame.glswing.input.GLMouseEvent event) {
		GLCodeColor sourceColor = colorPreviewAt(event);
		if (event.getButton() == java.awt.event.MouseEvent.BUTTON1 && sourceColor != null) {
			super.controlMousePressed(event);
			select(sourceColor.start(), sourceColor.end());
			if (isEditable() && getLanguage() == GLCodeLanguages.GLTHEME) {
				String before = getText();
				String literal = before.substring(sourceColor.start(), sourceColor.end());
				GLColorModel model = GLColorModel.RGB;
				for (GLColorModel candidate : GLColorModel.values())
					if (literal.toLowerCase(java.util.Locale.ROOT).startsWith(candidate.name().toLowerCase(java.util.Locale.ROOT)+"(")) model=candidate;
				GLColorModel outputModel = preferredColorOutputModel == null
						? model : preferredColorOutputModel;
				GLColorChooser.show(this, sourceColor.color(), model, outputModel,
						colorExpressionFormat, colorPickerPipettePreview, picker -> {
					if (!isEditable() || !before.equals(getText()) || picker.getColor().equals(sourceColor.color())) return;
					select(sourceColor.start(), sourceColor.end());
					String replacement=picker.getColorExpression();
					if(literal.startsWith("#")) {
						java.awt.Color c=picker.getColor();
						replacement=String.format(java.util.Locale.ROOT,"#%02X%02X%02X",c.getRed(),c.getGreen(),c.getBlue());
						if(c.getAlpha()!=255 || literal.length()==9) replacement+=String.format(java.util.Locale.ROOT,"%02X",c.getAlpha());
					}
					commitEditingSession();
					replaceSelection(replacement);
					commitEditingSession();
				});
			}
			event.consume();
			return;
		}
		if (event.hasLocalPosition() && isOverviewRuler(event.getLocalX())) {
			super.controlMousePressed(event);
			return;
		}
		if (event.getButton() == java.awt.event.MouseEvent.BUTTON1 && event.hasLocalPosition()
				&& (event.getModifiersEx() & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0) {
			clearNavigationHover(true);
			requestDefinitions(positionAt(event.getLocalX(), event.getLocalY()));
			event.consume();
			return;
		}
		super.controlMousePressed(event);
	}
	@Override protected void controlMouseMoved(com.vingame.glswing.input.GLMouseEvent event) {
		lastMouseEvent = event;
		if (colorPreviewAt(event) != null) {
			clearHover();
			super.controlMouseMoved(event);
			navigationPointerController = event.getPointerController();
			navigationPointerController.setCursor(colorPreviewCursorOwner,
					GLPointerCursor.HAND);
			return;
		}
		navigationPointerController.clearCursor(colorPreviewCursorOwner);
		if (diagnosticSession != null && event.hasLocalPosition()
				&& navigationModifier(event.getModifiersEx())
				&& !isOverviewRuler(event.getLocalX()) && diagnosticAt(event) == null) {
			hoverTimer.stop();
			GLToolTipManager.leave(this);
			if (requestNavigationHover(event)) return;
		}
		super.controlMouseMoved(event);
		if (navigationModifier(event.getModifiersEx())) leaveNavigationTarget();
		else clearNavigationHover(true);
		if (diagnosticSession == null || !event.hasLocalPosition()
				|| isOverviewRuler(event.getLocalX()) || diagnosticAt(event) != null) {
			hoverTimer.stop(); return;
		}
		int offset = positionAt(event.getLocalX(), event.getLocalY());
		hoverEvent = event;
		if (hover != null && offset >= hover.start() && offset < hover.end()) return;
		if (offset != hoverOffset) {
			hoverOffset = offset;
			hover = null;
			hoverRequest++;
			hoverTimer.restart();
		}
	}
	@Override protected void controlMouseExited(com.vingame.glswing.input.GLMouseEvent event) {
		boolean navigationPopupVisible = navigationHoverMenu.isShowing();
		super.controlMouseExited(event);
		if (navigationPopupVisible)
			navigationPointerController.setCursor(navigationCursorOwner, GLPointerCursor.HAND);
		clearHover();
		navigationPointerController.clearCursor(colorPreviewCursorOwner);
		lastMouseEvent = null;
		if (navigationPopupVisible) scheduleNavigationDismiss();
		else clearNavigationHover(true);
	}
	@Override protected void controlFocusChanged(boolean focused) {
		super.controlFocusChanged(focused);
		if (!focused) clearNavigationHover(true);
	}
	@Override protected void controlKeyReleased(GLKeyEvent event) {
		if (event.getKeyCode() == KeyEvent.VK_CONTROL
				|| event.getKeyCode() == KeyEvent.VK_META)
			clearNavigationHover(true);
		super.controlKeyReleased(event);
	}
	private static boolean navigationModifier(int modifiers) {
		int expected = System.getProperty("os.name", "")
				.toLowerCase(java.util.Locale.ROOT).contains("mac")
						? java.awt.event.InputEvent.META_DOWN_MASK
						: java.awt.event.InputEvent.CTRL_DOWN_MASK;
		return (modifiers & expected) != 0;
	}
	@Override protected Color toolTipTextColorAt(com.vingame.glswing.input.GLMouseEvent event) {
		GLCodeDiagnostic diagnostic = diagnosticAt(event);
		return diagnostic == null ? super.toolTipTextColorAt(event)
				: diagnosticColor(diagnostic);
	}
	private GLCodeDiagnostic diagnosticAt(com.vingame.glswing.input.GLMouseEvent event) {
		if (!event.hasLocalPosition()) return null;
		if (isOverviewRuler(event.getLocalX()))
			return overviewDiagnosticAt(event.getLocalY(), true);
		GLInsets padding = getGLStyle().padding();
		if (event.getLocalX() < padding.left() + leadingTextInset()) {
			int line = (int) Math.floor((event.getLocalY() - padding.top()
					+ getVerticalScrollOffset()) / fontLineHeight());
			return diagnosticForLine(line);
		}
		int offset = positionAt(event.getLocalX(), event.getLocalY());
		for (GLCodeDiagnostic diagnostic : diagnostics)
			if (offset >= diagnostic.start() && offset < diagnostic.end()) return diagnostic;
		return null;
	}
	private boolean isOverviewRuler(float localX) {
		return localX >= trailingDecorationX()
				&& localX < trailingDecorationX() + trailingDecorationWidth();
	}
	private GLCodeColor colorPreviewAt(com.vingame.glswing.input.GLMouseEvent event) {
		if (!event.hasLocalPosition() || documentColors.isEmpty()) return null;
		GLInsets padding = getGLStyle().padding();
		GLFont font = getGLStyle().font().measurementFont();
		float lineHeight = font.getLineHeight();
		int line = (int) Math.floor((event.getLocalY() - padding.top()
				+ getVerticalScrollOffset()) / lineHeight);
		if (line < 0 || line >= getDocument().getLineCount()) return null;
		int lineStart = getDocument().getLineStart(line);
		int lineEnd = getDocument().getLineEnd(line);
		float size = colorPreviewSize(font);
		float y = padding.top() + line * lineHeight - getVerticalScrollOffset()
				+ (lineHeight - size) / 2;
		float textX = padding.left() + leadingTextInset() - getHorizontalScrollOffset();
		for (GLCodeColor color : documentColors) {
			if (color.start() < lineStart || color.end() > lineEnd) continue;
			float x = textX + measureRange(lineStart, color.end()) + colorPreviewGap();
			if (event.getLocalX() >= x && event.getLocalX() <= x + size
					&& event.getLocalY() >= y && event.getLocalY() <= y + size) return color;
		}
		return null;
	}
	private static String colorDescription(Color color) {
		String hex = color.getAlpha() == 255
				? String.format(java.util.Locale.ROOT, "#%02X%02X%02X",
						color.getRed(), color.getGreen(), color.getBlue())
				: String.format(java.util.Locale.ROOT, "#%02X%02X%02X%02X",
						color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
		return "Color preview · " + hex + "\nrgba(" + color.getRed() + ", "
				+ color.getGreen() + ", " + color.getBlue() + ", " + color.getAlpha() + ")";
	}
	private float fontLineHeight() { return getGLStyle().font().measurementFont().getLineHeight(); }
	private GLCodeDiagnostic diagnosticForLine(int line) {
		if (line < 0 || line >= getDocument().getLineCount()) return null;
		int start = getDocument().getLineStart(line);
		int end = getDocument().getLineEnd(line);
		for (GLCodeDiagnostic diagnostic : diagnostics)
			if (diagnostic.start() <= end && diagnostic.end() > start) return diagnostic;
		return null;
	}
	private static Color diagnosticColor(GLCodeDiagnostic diagnostic) {
		return diagnostic.severity() == GLCodeDiagnostic.Severity.ERROR
				? new Color(235, 75, 75) : new Color(235, 175, 55);
	}
	@Override protected void localizerChanged() {
		super.localizerChanged();
		updateCodeMenuText();
	}
	@Override protected void inputMapChanged() {
		super.inputMapChanged();
		updateCodeMenuText();
	}
	@Override protected void editableChanged() {
		codeActions.refreshEnabled();
	}
	@Override protected void themeChanged() {
		applyCodeTheme();
	}
	private void applyCodeTheme() {
		GLTextEditorStyle themed = getGLTheme().textEditorStyle();
		GLThemeValues values = getGLTheme().values();
		Color codeText = values.getColor("codeEditor.text", themed.textColor());
		Color codeBackground = values.getColor(
				"codeEditor.background", themed.backgroundColor());
		setGLStyle(themed.withColors(codeBackground, codeText,
				themed.disabledTextColor(), themed.selectionColor(), themed.caretColor())
				.withFont(new GLFontSpec(GLFont.MONOSPACED,
						GLFont.PLAIN, themed.font().size())));
		keywordColor = values.getColor("codeEditor.keyword", new Color(204, 108, 29));
		stringColor = values.getColor("codeEditor.string", new Color(23, 198, 163));
		numberColor = values.getColor("codeEditor.number", new Color(104, 151, 187));
		commentColor = values.getColor("codeEditor.comment", new Color(128, 128, 128));
		typeColor = values.getColor("codeEditor.type", new Color(18, 144, 195));
		navigationLinkColor = values.getColor("codeEditor.navigationLink",
				new Color(82, 156, 255));
		Map<String, Color> configuredSemanticColors = new java.util.LinkedHashMap<>(
				Map.ofEntries(
				Map.entry("namespace", values.getColor("codeEditor.semantic.namespace",
						new Color(18, 144, 195))),
				Map.entry("type", values.getColor("codeEditor.semantic.type", typeColor)),
				Map.entry("class", values.getColor("codeEditor.semantic.class",
						new Color(18, 144, 195))),
				Map.entry("enum", values.getColor("codeEditor.semantic.enum",
						new Color(204, 129, 186))),
				Map.entry("interface", values.getColor("codeEditor.semantic.interface",
						new Color(128, 242, 246))),
				Map.entry("struct", values.getColor("codeEditor.semantic.struct", typeColor)),
				Map.entry("typeParameter", values.getColor(
						"codeEditor.semantic.typeParameter", new Color(191, 164, 164))),
				Map.entry("parameter", values.getColor("codeEditor.semantic.parameter",
						new Color(121, 171, 255))),
				Map.entry("variable", values.getColor("codeEditor.semantic.variable",
						new Color(243, 236, 121))),
				Map.entry("property", values.getColor("codeEditor.semantic.property",
						new Color(102, 225, 248))),
				Map.entry("enumMember", values.getColor("codeEditor.semantic.enumMember",
						new Color(141, 218, 248))),
				Map.entry("event", values.getColor("codeEditor.semantic.event",
						new Color(102, 225, 248))),
				Map.entry("function", values.getColor("codeEditor.semantic.function",
						new Color(167, 236, 33))),
				Map.entry("method", values.getColor("codeEditor.semantic.method",
						new Color(167, 236, 33))),
				Map.entry("macro", values.getColor("codeEditor.semantic.macro",
						new Color(177, 102, 218))),
				Map.entry("keyword", keywordColor),
				Map.entry("modifier", values.getColor("codeEditor.semantic.modifier",
						keywordColor)),
				Map.entry("comment", commentColor), Map.entry("string", stringColor),
				Map.entry("number", numberColor),
				Map.entry("regexp", values.getColor("codeEditor.semantic.regexp", stringColor)),
				Map.entry("operator", values.getColor("codeEditor.semantic.operator",
						new Color(230, 230, 250))),
				Map.entry("decorator", values.getColor("codeEditor.semantic.decorator",
						new Color(160, 160, 160)))));
		String semanticPrefix = "codeEditor.semantic.";
		for (String key : values.asMap().keySet())
			if (key.startsWith(semanticPrefix) && key.length() > semanticPrefix.length())
				configuredSemanticColors.put(key.substring(semanticPrefix.length()),
						values.getColor(key, themed.textColor()));
		semanticColors = Map.copyOf(configuredSemanticColors);
	}
	private void updateCodeMenuText() {
		codeActions.refresh();
	}
	private void retokenize() { tokens = language.tokenize(getText()); invalidateRecording(); }
	/**
	 * Keeps classifications whose source range is unchanged while a fresh semantic
	 * snapshot is pending. Tokens intersecting the edit are discarded and the
	 * following ranges are translated by the edit delta; the lexer therefore
	 * remains the immediate fallback only around the changed source.
	 */
	private static List<GLCodeSemanticToken> retainUnaffectedSemanticTokens(
			List<GLCodeSemanticToken> current, GLTextDocument.Change change) {
		if (current.isEmpty()) return current;
		int editStart = change.offset();
		int oldEnd = editStart + change.removedText().length();
		int delta = change.insertedText().length() - change.removedText().length();
		ArrayList<GLCodeSemanticToken> retained = new ArrayList<>(current.size());
		for (GLCodeSemanticToken token : current) {
			if (token.end() <= editStart) {
				retained.add(token);
			} else if (token.start() >= oldEnd) {
				int start = token.start() + delta;
				int end = token.end() + delta;
				if (start >= 0 && end > start)
					retained.add(new GLCodeSemanticToken(start, end,
							token.type(), token.modifiers()));
			}
		}
		return List.copyOf(retained);
	}
	/**
	 * Keeps visual hints anchored while a replacement provider snapshot is
	 * pending. Hints after an edit follow its offset delta; only anchors removed
	 * by the edit disappear immediately.
	 */
	private static List<GLCodeInlayHint> retainInlayHints(
			List<GLCodeInlayHint> current, GLTextDocument.Change change) {
		if (current.isEmpty()) return current;
		int editStart = change.offset();
		int oldEnd = editStart + change.removedText().length();
		int delta = change.insertedText().length() - change.removedText().length();
		ArrayList<GLCodeInlayHint> retained = new ArrayList<>(current.size());
		for (GLCodeInlayHint hint : current) {
			if (hint.offset() <= editStart) {
				retained.add(hint);
			} else if (hint.offset() >= oldEnd) {
				int offset = hint.offset() + delta;
				if (offset >= 0)
					retained.add(new GLCodeInlayHint(offset, hint.label(), hint.kind()));
			}
		}
		return List.copyOf(retained);
	}
	/** Keeps the last valid color swatches stable during the provider debounce. */
	private static List<GLCodeColor> retainDocumentColors(
			List<GLCodeColor> current, GLTextDocument.Change change) {
		if (current.isEmpty()) return current;
		int editStart = change.offset();
		int oldEnd = editStart + change.removedText().length();
		int delta = change.insertedText().length() - change.removedText().length();
		boolean insertion = change.removedText().isEmpty();
		ArrayList<GLCodeColor> retained = new ArrayList<>(current.size());
		for (GLCodeColor color : current) {
			int start = color.start(), end = color.end();
			if (insertion && editStart >= start && editStart <= end) {
				end += delta;
			} else if (end <= editStart) {
				// Unchanged range before the edit.
			} else if (start >= oldEnd) {
				start += delta; end += delta;
			} else if (editStart >= start && oldEnd <= end) {
				end += delta;
			} else {
				continue;
			}
			if (start >= 0 && end > start)
				retained.add(new GLCodeColor(start, end, color.color()));
		}
		return List.copyOf(retained);
	}
	private void validateSource() {
		if (!coreDiagnosticsEnabled) return;
		coreDiagnostics = language.diagnose(getText()).stream()
				.filter(value -> value.end() <= getDocument().length()).toList();
		mergeDiagnostics();
	}
	private void reopenDiagnosticProvider() {
		if (diagnosticProvider == null) return;
		closeDiagnosticSession();
		providerDiagnostics = List.of();
		mergeDiagnostics();
		openDiagnosticSession();
	}
	private void installAutomaticCodeIntelligence() {
		closeDiagnosticSession();
		diagnosticProvider = language.codeIntelligenceProvider();
		providerDiagnostics = List.of();
		mergeDiagnostics();
		if (diagnosticProvider != null && documentUri != null) openDiagnosticSession();
	}
	private void openDiagnosticSession() {
		GLCodeDocument snapshot = diagnosticDocument();
		diagnosticSession = diagnosticProvider.open(snapshot, this::acceptProviderDiagnostics);
		requestSemanticTokens();
		requestDocumentColors();
		requestInlayHints();
	}
	private void updateDiagnosticProvider() {
		if (diagnosticSession != null) diagnosticSession.update(diagnosticDocument());
	}
	private GLCodeDocument diagnosticDocument() {
		return new GLCodeDocument(documentUri, language.id(), documentVersion, getText());
	}
	private void acceptProviderDiagnostics(GLCodeDiagnosticProvider.Result result) {
		if (!EventQueue.isDispatchThread()) {
			EventQueue.invokeLater(() -> acceptProviderDiagnostics(result));
			return;
		}
		if (result.version() != documentVersion) return;
		providerDiagnostics = result.diagnostics().stream()
				.filter(value -> value.end() <= getDocument().length()).toList();
		mergeDiagnostics();
		// A diagnostic publication means that the server has reconciled this
		// document version. If an earlier semantic request was empty, retry now
		// instead of leaving lexer highlighting active indefinitely.
		if (semanticTokens.isEmpty() && semanticTokenFuture == null)
			semanticTokenTimer.restart();
	}
	private void closeDiagnosticSession() {
		closeCompletion();
		closeSignatureHelp();
		clearNavigationHover(true);
		semanticTokenTimer.stop();
		semanticTokenRequest++;
		if (semanticTokenFuture != null) semanticTokenFuture.cancel(false);
		semanticTokenFuture = null;
		semanticTokens = List.of();
		documentColorTimer.stop();
		documentColorRequest++;
		if (documentColorFuture != null) documentColorFuture.cancel(false);
		documentColorFuture = null;
		documentColors = List.of();
		inlayHintTimer.stop();
		inlayHintRequest++;
		if (inlayHintFuture != null) inlayHintFuture.cancel(false);
		inlayHintFuture = null;
		inlayHints = List.of();
		signatureInlayHints = List.of();
		invalidateRowLayout();
		definitionRequest++;
		if (definitionFuture != null) definitionFuture.cancel(false);
		definitionFuture = null;
		definitionMenu.close();
		clearHover();
		if (diagnosticSession == null) return;
		diagnosticSession.close();
		diagnosticSession = null;
	}
	private void requestCompletion(Character trigger) {
		if (diagnosticSession == null || documentUri == null || !isEditable()) return;
		completionStatusListener.accept(GLCodeCompletionStatus.requesting());
		long request = ++completionRequest;
		int version = documentVersion;
		int offset = getCaretPosition();
		var stage = diagnosticSession.complete(diagnosticDocument(), offset, trigger);
		completionFuture = stage.toCompletableFuture();
		stage.whenComplete((items, failure) -> EventQueue.invokeLater(() -> {
			if (request != completionRequest || version != documentVersion
					|| offset != getCaretPosition()) return;
			if (failure != null) {
				completionStatusListener.accept(GLCodeCompletionStatus.failed(failure));
				return;
			}
			List<GLCodeCompletion> proposals = items == null ? List.of() : items;
			completionStatusListener.accept(GLCodeCompletionStatus.completed(proposals.size()));
			if (trigger == null && insertSingleCompletion && proposals.size() == 1)
				acceptCompletion(proposals.get(0));
			else
				showCompletions(proposals);
		}));
	}
	/** Opens completion proposals at the current caret position. */
	public final void requestCompletion() { requestCompletion(null); }
	private void showCompletions(List<GLCodeCompletion> items) {
		completionMenu.close();
		completionMenu.removeAllItems();
		ArrayList<GLMenuItem> rows = new ArrayList<>();
		for (GLCodeCompletion item : items.stream().limit(40).toList()) {
			GLMenuItem row = completionMenu.addItem(item.label(), event -> acceptCompletion(item));
			row.setAcceleratorText(item.detail());
			row.setMnemonicEnabled(false);
			rows.add(row);
		}
		completionRows = List.copyOf(rows);
		selectedCompletion = 0;
		if (!completionRows.isEmpty()) completionRows.get(0).setKeyboardSelected(true);
		if (items.isEmpty()) return;
		float[] caretPosition = caretScreenPosition();
		GLParent host = popupHost();
		if (caretPosition != null && host != null)
			completionMenu.show(host, caretPosition[0], caretPosition[1]);
	}
	private void selectCompletion(int index) {
		if (completionRows.isEmpty()) return;
		completionRows.get(selectedCompletion).setKeyboardSelected(false);
		selectedCompletion = Math.floorMod(index, completionRows.size());
		completionRows.get(selectedCompletion).setKeyboardSelected(true);
	}
	private void acceptCompletion(GLCodeCompletion item) {
		if (item.end() > getDocument().length()) return;
		ArrayList<TextReplacement> edits = new ArrayList<>();
		edits.add(new TextReplacement(item.start(), item.end(), item.insertText()));
		for (GLCodeTextEdit edit : item.additionalEdits())
			edits.add(new TextReplacement(edit.start(), edit.end(), edit.text()));
		int shiftedStart = item.start();
		for (GLCodeTextEdit edit : item.additionalEdits())
			if (edit.end() <= item.start())
				shiftedStart += edit.text().length() - (edit.end() - edit.start());
		replaceTextAtomically(edits, shiftedStart + item.caretOffset());
	}
	private void closeCompletion() {
		completionRequest++;
		if (completionFuture != null) completionFuture.cancel(false);
		completionFuture = null;
		completionMenu.close();
		completionRows = List.of();
		selectedCompletion = 0;
	}
	private void requestSignatureHelp(Character trigger) {
		if (diagnosticSession == null || documentUri == null) return;
		long request = ++signatureRequest;
		int version = documentVersion;
		int offset = getCaretPosition();
		var stage = diagnosticSession.signatureHelp(diagnosticDocument(), offset, trigger);
		signatureFuture = stage.toCompletableFuture();
		stage.whenComplete((signatures, failure) -> EventQueue.invokeLater(() -> {
			if (failure != null || request != signatureRequest || version != documentVersion
					|| offset != getCaretPosition()) return;
			List<GLCodeSignature> received = signatures == null ? List.of() : signatures;
			installUnambiguousSignatureHint(received, offset);
			showSignatureHelp(received);
		}));
	}
	private void installUnambiguousSignatureHint(List<GLCodeSignature> signatures,
			int offset) {
		if (!inlayHintsVisible || signatures.isEmpty()) return;
		int parameter = signatures.stream().mapToInt(GLCodeSignature::activeParameter)
				.filter(value -> value >= 0).findFirst().orElse(-1);
		if (parameter < 0) return;
		String common = null;
		for (GLCodeSignature signature : signatures) {
			if (parameter >= signature.parameters().size()) return;
			String name = javaParameterName(signature.parameters().get(parameter));
			if (name == null) return;
			if (common == null) common = name;
			else if (!common.equals(name)) return;
		}
		GLCodeInlayHint hint = new GLCodeInlayHint(offset, common + ":");
		ArrayList<GLCodeInlayHint> provisional = new ArrayList<>(signatureInlayHints);
		provisional.removeIf(value -> value.offset() == offset);
		provisional.add(hint);
		provisional.sort(java.util.Comparator.comparingInt(GLCodeInlayHint::offset));
		signatureInlayHints = List.copyOf(provisional);
		inlayHints = mergeInlayHints(inlayHints, List.of(hint));
		invalidateRowLayout();
		invalidateRecording();
	}
	private static String javaParameterName(String label) {
		if (label == null || label.isBlank()) return null;
		java.util.regex.Matcher identifiers = java.util.regex.Pattern.compile(
				"[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*").matcher(label);
		String last = null;
		int count = 0;
		while (identifiers.find()) { last = identifiers.group(); count++; }
		if (last == null || (count == 1 && Character.isUpperCase(last.codePointAt(0))))
			return null;
		return switch (last) {
		case "byte", "short", "int", "long", "float", "double", "boolean", "char",
				"void", "this", "super" -> null;
		default -> last;
		};
	}
	private void showSignatureHelp(List<GLCodeSignature> signatures) {
		signatureMenu.close();
		signatureMenu.removeAllItems();
		for (GLCodeSignature signature : signatures.stream().limit(8).toList()) {
			GLMenuItem row = signatureMenu.addItem(signature.displayLabel(), event -> { });
			row.setAcceleratorText(signature.documentation());
			row.setMnemonicEnabled(false);
		}
		if (signatures.isEmpty()) return;
		float[] caretPosition = caretScreenPosition();
		GLParent host = popupHost();
		if (caretPosition != null && host != null)
			signatureMenu.show(host, caretPosition[0], caretPosition[1]);
	}
	private void closeSignatureHelp() {
		signatureRequest++;
		if (signatureFuture != null) signatureFuture.cancel(false);
		signatureFuture = null;
		signatureMenu.close();
	}
	private void requestHover() {
		if (diagnosticSession == null || hoverOffset < 0 || documentUri == null) return;
		long request = ++hoverRequest;
		int version = documentVersion;
		int offset = hoverOffset;
		var requestStage = diagnosticSession.hover(diagnosticDocument(), offset);
		hoverFuture = requestStage.toCompletableFuture();
		requestStage.whenComplete((result, failure) ->
				EventQueue.invokeLater(() -> {
					if (failure != null || request != hoverRequest || version != documentVersion
							|| offset != hoverOffset || result == null) return;
					hover = result.filter(value -> value.end() <= getDocument().length()).orElse(null);
					if (hover != null && hoverEvent != null)
						GLToolTipManager.hover(this, hoverEvent, hover.contents(), null);
				}));
	}
	private void clearHover() {
		hoverTimer.stop();
		if (hoverFuture != null) hoverFuture.cancel(false);
		hoverFuture = null;
		hoverRequest++;
		hoverOffset = -1;
		hoverEvent = null;
		hover = null;
		GLToolTipManager.leave(this);
	}
	private void mergeDiagnostics() {
		ArrayList<GLCodeDiagnostic> merged = new ArrayList<>(coreDiagnostics.size()
				+ providerDiagnostics.size());
		merged.addAll(coreDiagnostics);
		merged.addAll(providerDiagnostics);
		merged.sort(java.util.Comparator.comparingInt(GLCodeDiagnostic::start));
		diagnostics = List.copyOf(merged);
		invalidateRecording();
	}
	private void requestSemanticTokens() {
		if (diagnosticSession == null || documentUri == null) return;
		semanticStatusListener.accept(GLCodeSemanticStatus.requesting());
		long request = ++semanticTokenRequest;
		int version = documentVersion;
		if (semanticTokenFuture != null) semanticTokenFuture.cancel(false);
		var stage = diagnosticSession.semanticTokens(diagnosticDocument());
		semanticTokenFuture = stage.toCompletableFuture();
		stage.whenComplete((result, failure) -> EventQueue.invokeLater(() -> {
			if (request != semanticTokenRequest || version != documentVersion) return;
			semanticTokenFuture = null;
			if (failure != null) {
				semanticStatusListener.accept(GLCodeSemanticStatus.failed(failure));
				return;
			}
			List<GLCodeSemanticToken> received = result == null ? List.of() : result;
			semanticTokens = received.stream()
					.filter(value -> value.end() <= getDocument().length())
					.sorted(java.util.Comparator.comparingInt(GLCodeSemanticToken::start))
					.toList();
			semanticStatusListener.accept(
					GLCodeSemanticStatus.completed(semanticTokens.size()));
			invalidateRecording();
		}));
	}
	private void requestDocumentColors() {
		if (diagnosticSession == null || documentUri == null) return;
		long request = ++documentColorRequest;
		int version = documentVersion;
		if (documentColorFuture != null) documentColorFuture.cancel(false);
		var stage = diagnosticSession.documentColors(diagnosticDocument());
		documentColorFuture = stage.toCompletableFuture();
		stage.whenComplete((result, failure) -> EventQueue.invokeLater(() -> {
			if (request != documentColorRequest || version != documentVersion) return;
			documentColorFuture = null;
			if (failure != null) return;
			List<GLCodeColor> received = result == null ? List.of() : result;
			documentColors = received.stream()
					.filter(value -> value.end() <= getDocument().length())
					.sorted(java.util.Comparator.comparingInt(GLCodeColor::start)).toList();
			invalidateRowLayout();
			invalidateRecording();
		}));
	}
	private void requestInlayHints() {
		if (!inlayHintsVisible || diagnosticSession == null || documentUri == null)
			return;
		long request = ++inlayHintRequest;
		int version = documentVersion;
		if (inlayHintFuture != null) inlayHintFuture.cancel(false);
		var stage = diagnosticSession.inlayHints(diagnosticDocument());
		inlayHintFuture = stage.toCompletableFuture();
		stage.whenComplete((result, failure) -> EventQueue.invokeLater(() -> {
			if (request != inlayHintRequest || version != documentVersion) return;
			inlayHintFuture = null;
			if (failure != null) return;
			List<GLCodeInlayHint> received = (result == null ? java.util.stream.Stream
					.<GLCodeInlayHint>empty() : result.stream())
					.filter(value -> value.offset() <= getDocument().length())
					.sorted(java.util.Comparator.comparingInt(GLCodeInlayHint::offset))
					.toList();
			signatureInlayHints = signatureInlayHints.stream()
					.filter(value -> validSignatureHintAnchor(getText(), value.offset()))
					.filter(value -> received.stream().noneMatch(provider ->
							provider.offset() == value.offset()))
					.toList();
			inlayHints = mergeInlayHints(received, signatureInlayHints);
			invalidateRowLayout();
			invalidateRecording();
		}));
	}
	private static boolean validSignatureHintAnchor(String source, int offset) {
		if (offset < 0 || offset > source.length()) return false;
		int previous = offset - 1;
		while (previous >= 0 && Character.isWhitespace(source.charAt(previous))) previous--;
		return previous >= 0 && (source.charAt(previous) == '(' || source.charAt(previous) == ',');
	}
	private static List<GLCodeInlayHint> mergeInlayHints(List<GLCodeInlayHint> primary,
			List<GLCodeInlayHint> additional) {
		ArrayList<GLCodeInlayHint> merged = new ArrayList<>(primary);
		for (GLCodeInlayHint hint : additional)
			if (merged.stream().noneMatch(existing -> existing.offset() == hint.offset()))
				merged.add(hint);
		merged.sort(java.util.Comparator.comparingInt(GLCodeInlayHint::offset));
		return List.copyOf(merged);
	}
	@Override protected int nextInlineDecorationOffset(int startInclusive,
			int endExclusive) {
		if (!inlayHintsVisible || startInclusive >= endExclusive) return -1;
		int index = firstInlayAtOrAfter(startInclusive);
		return index < inlayHints.size() && inlayHints.get(index).offset() < endExclusive
				? inlayHints.get(index).offset() : -1;
	}
	@Override protected float inlineDecorationWidth(GLFont font, int offset) {
		if (!inlayHintsVisible) return 0;
		GLFont hintFont = inlayFont(font);
		float padding = inlayMetric("padding", 4);
		float gap = inlayMetric("gap", 3);
		float width = 0;
		for (int index = firstInlayAtOrAfter(offset); index < inlayHints.size()
				&& inlayHints.get(index).offset() == offset; index++) {
			GLCodeInlayHint hint = inlayHints.get(index);
			width += gap + hintFont.measureText(hint.label()) + padding * 2;
		}
		return width;
	}
	@Override protected void drawInlineDecoration(GLContext2D context, GLFont font,
			int offset, float x, float top, float baseline, float lineHeight) {
		GLFont hintFont = inlayFont(font);
		GLThemeValues values = getGLTheme().values();
		Color background = values.getColor("codeEditor.inlay.background",
				new Color(67, 69, 72));
		Color border = values.getColor("codeEditor.inlay.border",
				new Color(90, 93, 97));
		Color text = values.getColor("codeEditor.inlay.text",
				new Color(187, 191, 196));
		float padding = inlayMetric("padding", 4);
		float gap = inlayMetric("gap", 3);
		float radius = inlayMetric("radius", 3);
		float borderWidth = inlayMetric("borderWidth", 1);
		float boxHeight = Math.min(Math.max(1, lineHeight - 4),
				hintFont.getLineHeight() + 2);
		float boxY = top + (lineHeight - boxHeight) * 0.5f;
		float cursor = x;
		for (int index = firstInlayAtOrAfter(offset); index < inlayHints.size()
				&& inlayHints.get(index).offset() == offset; index++) {
			GLCodeInlayHint hint = inlayHints.get(index);
			float boxWidth = hintFont.measureText(hint.label()) + padding * 2;
			float boxX = cursor + gap;
			if (borderWidth > 0)
				context.fillAndDrawUIRoundRect(boxX, boxY, boxWidth, boxHeight,
						radius, borderWidth, background, border,
						GLStroke.WidthSpace.SCREEN);
			else context.fillUIRoundRect(boxX, boxY, boxWidth, boxHeight,
					radius, background);
			float textBaseline = boxY + (boxHeight - hintFont.getLineHeight()) * 0.5f
					+ hintFont.getAscent();
			context.drawUIText(hintFont, hint.label(), boxX + padding,
					textBaseline, text);
			cursor = boxX + boxWidth;
		}
	}
	private int firstInlayAtOrAfter(int offset) {
		int low = 0, high = inlayHints.size();
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (inlayHints.get(middle).offset() < offset) low = middle + 1;
			else high = middle;
		}
		return low;
	}
	private GLFont inlayFont(GLFont base) {
		float scale = Math.max(0.5f, Math.min(1, inlayMetric("fontScale", 0.78f)));
		return getGLFont(new GLFontSpec(base.getFamily(), GLFont.PLAIN,
				Math.max(6, base.getSize() * scale)));
	}
	private float inlayMetric(String suffix, float fallback) {
		return Math.max(0, getGLTheme().values().getFloat(
				"codeEditor.inlay." + suffix, fallback));
	}
	private Color syntaxColor(GLTextDocument document, int offset, Color fallback) {
		if (offset >= navigationWordStart && offset < navigationWordEnd)
			return navigationLinkColor;
		List<GLCodeSemanticToken> semantic = semanticTokens;
		int semanticLow = 0, semanticHigh = semantic.size() - 1;
		while (semanticLow <= semanticHigh) {
			int middle = (semanticLow + semanticHigh) >>> 1;
			GLCodeSemanticToken token = semantic.get(middle);
			if (offset < token.start()) semanticHigh = middle - 1;
			else if (offset >= token.end()) semanticLow = middle + 1;
			else {
				Color color = semanticColor(token);
				if (color != null) return color;
				break;
			}
		}
		List<GLCodeToken> current = tokens;
		int low = 0, high = current.size() - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			GLCodeToken token = current.get(middle);
			if (offset < token.start()) high = middle - 1;
			else if (offset >= token.end()) low = middle + 1;
			else return switch (token.type()) {
				case KEYWORD -> keywordColor; case STRING -> stringColor;
				case NUMBER -> numberColor; case COMMENT -> commentColor;
				case TYPE -> typeColor; case PLAIN -> fallback;
			};
		}
		return fallback;
	}
	private Color semanticColor(GLCodeSemanticToken token) {
		for (String modifier : token.modifiers().stream().sorted().toList()) {
			Color modified = semanticColors.get(token.type() + "." + modifier);
			if (modified != null) return modified;
			modified = semanticColors.get("*." + modifier);
			if (modified != null) return modified;
		}
		return semanticColors.get(token.type());
	}
}
