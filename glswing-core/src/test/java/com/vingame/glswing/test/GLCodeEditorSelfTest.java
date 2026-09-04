package com.vingame.glswing.test;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.net.URI;

import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.ui.GLCodeEditor;
import com.vingame.glswing.ui.GLCodeCompletion;
import com.vingame.glswing.ui.GLCodeDiagnostic;
import com.vingame.glswing.ui.GLCodeDiagnosticProvider;
import com.vingame.glswing.ui.GLCodeDocument;
import com.vingame.glswing.ui.GLCodeLanguages;
import com.vingame.glswing.ui.GLCodeLocation;
import com.vingame.glswing.ui.GLCodeSignature;
import com.vingame.glswing.ui.GLCodeToken;
import com.vingame.glswing.ui.GLThemes;

public final class GLCodeEditorSelfTest {
	private GLCodeEditorSelfTest() { }
	public static void main(String[] args) {
		String largeSource = "public final class LibraryType { int value = 42; }\n".repeat(2_500);
		long constructionStarted = System.nanoTime();
		GLCodeEditor sourceViewer = new GLCodeEditor(largeSource, false);
		check(sourceViewer.getText().length() == largeSource.length(),
				"Large read-only source was not preserved");
		check(System.nanoTime() - constructionStarted < 15_000_000_000L,
				"Large source viewer construction performed premature text layout");
		check(new GLCodeSignature("print(String text, int count)", "",
				java.util.List.of("String text", "int count"), 1).displayLabel()
				.contains("[int count]"), "Signature help did not mark its active parameter");
		var tokens = GLCodeLanguages.JAVA.tokenize("public int value = 42; // note");
		check(tokens.stream().anyMatch(token -> token.type() == GLCodeToken.Type.KEYWORD),
				"Java lexer did not recognize a keyword");
		check(tokens.stream().anyMatch(token -> token.type() == GLCodeToken.Type.COMMENT),
				"Java lexer did not recognize a comment");
		GLCodeEditor invalid = new GLCodeEditor("if (ready] {");
		check(!invalid.getDiagnostics().isEmpty(),
				"Code editor did not publish structural language diagnostics");
		OverviewEditor overview = new OverviewEditor("// one\n// two\nif (ready] {");
		overview.setBounds(0, 0, 400, 200);
		int overviewTarget = overview.getDiagnostics().get(0).start();
		overview.jumpFromOverview(199);
		check(overview.getCaretPosition() == overviewTarget,
				"Overview ruler did not navigate to its nearest diagnostic");
		var javaDiagnostics = GLCodeLanguages.JAVA.diagnose(
				"int value() {\n    return 1\n}\nvoid stop() { return 1; }");
		check(javaDiagnostics.stream().anyMatch(value -> value.message().equals("Missing semicolon")),
				"Lightweight Java diagnostics missed a definite semicolon error");
		check(javaDiagnostics.stream().anyMatch(value -> value.message().contains("void method")),
				"Lightweight Java diagnostics missed an invalid void return");
		check(GLCodeLanguages.JAVA.diagnose("int value() { int x = 1; }").stream()
				.anyMatch(value -> value.message().contains("no value return")),
				"Lightweight Java diagnostics missed a simple absent return");
		check(GLCodeLanguages.JAVA.diagnose(
				"final class Example { private Example() { } }").stream()
				.noneMatch(value -> value.message().contains("value return")),
				"Lightweight Java diagnostics treated a constructor as a method");
		var stringReturnDiagnostics = GLCodeLanguages.JAVA.diagnose(
				"String message() { return \"Hello, GLSwing!\"; }");
		check(stringReturnDiagnostics.stream()
				.noneMatch(value -> value.message().contains("return a value")
						|| value.message().contains("no value return")),
				"Lightweight Java diagnostics discarded a string return value: "
						+ stringReturnDiagnostics);
		check(GLCodeLanguages.C.diagnose("int value() {\n    return 1\n}").stream()
				.anyMatch(value -> value.message().equals("Missing semicolon")),
				"C profile missed a definite semicolon error");
		check(GLCodeLanguages.PYTHON.diagnose("if ready\n    pass").stream()
				.anyMatch(value -> value.message().contains("requires ':'")),
				"Python profile missed a block header without a colon");
		check(GLCodeLanguages.RUBY.diagnose("def value\n  1").stream()
				.anyMatch(value -> value.message().contains("missing 'end'")),
				"Ruby profile missed an unclosed block");
		check(GLCodeLanguages.JSON.diagnose("{\"value\": 1,}").stream()
				.anyMatch(value -> value.message().contains("trailing commas")),
				"JSON profile missed a trailing comma");
		check(GLCodeLanguages.PYTHON.tokenize("# note").stream()
				.anyMatch(value -> value.type() == GLCodeToken.Type.COMMENT),
				"Python lexer did not recognize its line-comment syntax");
		check(GLCodeLanguages.HTML.diagnose("<main><p>text</main>").stream()
				.anyMatch(value -> value.message().contains("closing tag")),
				"HTML profile missed a mismatched closing tag");
		check(GLCodeLanguages.CSS.diagnose("a {\n color: red\n width: 2px;\n}").stream()
				.anyMatch(value -> value.message().contains("Missing semicolon")),
				"CSS profile missed a separator between declarations");
		check(GLCodeLanguages.JAVA.diagnose("class A {\n test\n}").stream()
				.anyMatch(value -> value.message().contains("bare identifier")),
				"Java profile accepted a bare standalone identifier");
		check(GLCodeLanguages.JAVA.diagnose("void run() { call();; }").stream()
				.anyMatch(value -> value.severity() == com.vingame.glswing.ui.GLCodeDiagnostic.Severity.WARNING
						&& value.message().contains("Redundant")),
				"Java profile missed a redundant empty statement");
		check(GLCodeLanguages.JAVA.diagnose("for (;;) { call(); }").stream()
				.noneMatch(value -> value.message().contains("semicolon")
						|| value.message().contains("statement")),
				"Java profile treated the separators in an infinite for loop as redundant");
		check(GLCodeLanguages.CSS.diagnose("a { color: red;; }").stream()
				.anyMatch(value -> value.severity() == com.vingame.glswing.ui.GLCodeDiagnostic.Severity.ERROR
						&& value.message().contains("extra semicolon")),
				"CSS profile missed an extra semicolon");
		testExternalDiagnostics();
		GLCodeEditor editor = new GLCodeEditor("    if (ready) {");
		editor.setCaretPosition(editor.getText().length());
		press(editor, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED);
		check(editor.getText().endsWith("\n        "),
				"Code editor did not continue and increase indentation");
		type(editor, '(');
		check(editor.getText().endsWith("        ()")
				&& editor.getCaretPosition() == editor.getText().length() - 1,
				"Code editor did not insert a bracket pair around the caret");
		int pairedLength = editor.getText().length();
		type(editor, ')');
		check(editor.getText().length() == pairedLength
				&& editor.getCaretPosition() == pairedLength,
				"Typing an existing generated closer inserted a duplicate");
		GLCodeEditor block = new GLCodeEditor("    if (ready) ");
		block.setCaretPosition(block.getText().length());
		type(block, '{');
		press(block, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED);
		check(block.getText().equals("    if (ready) {\n        \n    }")
				&& block.getCaretPosition() == "    if (ready) {\n        ".length(),
				"Enter did not expand a generated brace pair into an indented block");
		editor.toggleLineComment();
		check(editor.getText().contains("//"), "Line comment command did not add a prefix");
		check(editor.getContextMenu().getItems().size() == 5
				&& editor.getContextMenu().getItems().stream()
						.noneMatch(item -> item.getSubMenu() != null),
				"Code editor exposed the inherited rich-text context menu");
		editor.select(0, editor.getText().length());
		press(editor, KeyEvent.VK_B, 'B', InputEvent.CTRL_DOWN_MASK);
		check(editor.getDocument().getStyleRuns(0, editor.getText().length())
				.stream().noneMatch(run -> run.style().bold()),
				"Code editor accepted the rich-text bold shortcut");
		editor.uncommentSelection();
		check(!editor.getText().contains("//"),
				"Uncomment did not process the complete document without a selection");
		GLCodeEditor formatting = new GLCodeEditor("if( ready ){\ncall(a,b);\n}");
		formatting.formatCode();
		check(formatting.getText().equals("if (ready) {\n    call(a, b);\n}"),
				"Code formatting did not indent the complete document");
		GLCodeEditor menuFormatting = new GLCodeEditor("if( ready ){");
		menuFormatting.getContextMenu().getItems().get(0).doClick();
		drainEventQueue();
		check(menuFormatting.getText().equals("if (ready) {"),
				"Deferred context-menu formatting did not run");
		formatting.setGLTheme(GLThemes.LIGHT);
		check(formatting.getGLStyle().backgroundColor().equals(
				GLThemes.LIGHT.textEditorStyle().backgroundColor()),
				"Code editor retained dark-theme control colors under the light theme");
		System.out.println("GL code editor self-test passed.");
	}
	@SuppressWarnings("unchecked")
	private static void testExternalDiagnostics() {
		java.util.function.Consumer<GLCodeDiagnosticProvider.Result>[] publish = new java.util.function.Consumer[1];
		GLCodeDocument[] latest = new GLCodeDocument[1];
		boolean[] closed = new boolean[1];
		boolean[] completed = new boolean[1];
		boolean[] navigated = new boolean[1];
		boolean[] semanticRequested = new boolean[1];
		boolean[] semanticAvailable = new boolean[1];
		GLCodeDiagnosticProvider provider = (document, listener) -> {
			latest[0] = document;
			publish[0] = listener;
			return new GLCodeDiagnosticProvider.Session() {
				@Override public void update(GLCodeDocument value) { latest[0] = value; }
				@Override public java.util.concurrent.CompletionStage<java.util.List<GLCodeCompletion>>
						complete(GLCodeDocument value, int offset, Character trigger) {
					completed[0] = offset == value.text().length() && trigger == null;
					return java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of(
							new GLCodeCompletion("changed", "field", "changed", 4, offset,
									"changed".length(), java.util.List.of(
											new com.vingame.glswing.ui.GLCodeTextEdit(
													0, 0, "// generated\n")))));
				}
				@Override public java.util.concurrent.CompletionStage<java.util.List<GLCodeLocation>>
						definitions(GLCodeDocument value, int offset) {
					return java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of(
							new GLCodeLocation(URI.create("file:///project/Target.java"), 2, 4)));
				}
				@Override public java.util.concurrent.CompletionStage<java.util.List<
						com.vingame.glswing.ui.GLCodeSemanticToken>> semanticTokens(
								GLCodeDocument value) {
					semanticRequested[0] = true;
					return java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of(
							new com.vingame.glswing.ui.GLCodeSemanticToken(4, 9, "variable",
									java.util.Set.of("declaration"))));
				}
				@Override public void close() { closed[0] = true; }
			};
		};
		GLCodeEditor editor = new GLCodeEditor("int value;");
		editor.setSemanticStatusListener(status -> semanticAvailable[0] =
				status.state() == com.vingame.glswing.ui.GLCodeSemanticStatus.State.AVAILABLE
				&& status.tokenCount() == 1);
		editor.setDiagnosticProvider(provider, URI.create("file:///project/Test.java"));
		editor.setNavigationHandler(location -> navigated[0] = location.uri().toString()
				.equals("file:///project/Target.java"));
		editor.goToDefinition();
		flushEvents();
		check(navigated[0], "Code editor did not forward a resolved external definition");
		check(semanticRequested[0] && semanticAvailable[0]
				&& editor.getSemanticTokens().size() == 1,
				"Code editor did not retain provider semantic tokens");
		editor.getDocument().insert(editor.getText().length(), " ");
		check(editor.getSemanticTokens().size() == 1
				&& editor.getSemanticTokens().get(0).start() == 4,
				"An edit after a semantic token discarded an unaffected classification");
		editor.getDocument().insert(0, " ");
		check(editor.getSemanticTokens().size() == 1
				&& editor.getSemanticTokens().get(0).start() == 5,
				"An edit before a semantic token did not translate its source range");
		editor.getDocument().insert(7, "x");
		check(editor.getSemanticTokens().isEmpty(),
				"An edit inside a semantic token retained a stale classification");
		int initialVersion = latest[0].version();
		editor.setText("int changed;");
		publish[0].accept(new GLCodeDiagnosticProvider.Result(initialVersion,
				java.util.List.of(new GLCodeDiagnostic(0, 3, GLCodeDiagnostic.Severity.ERROR,
						"stale"))));
		flushEvents();
		editor.setCaretPosition(editor.getText().length());
		press(editor, KeyEvent.VK_SPACE, ' ', InputEvent.CTRL_DOWN_MASK);
		flushEvents();
		check(completed[0], "Code editor completion shortcut did not request proposals at the caret");
		check(editor.getText().equals("// generated\nint changed"),
				"Completion did not apply its primary and additional edits atomically");
		press(editor, KeyEvent.VK_Z, 'Z', InputEvent.CTRL_DOWN_MASK);
		check(editor.getText().equals("int changed;"),
				"Undo did not revert the complete completion transaction");
		check(editor.getDiagnostics().stream().noneMatch(value -> value.message().equals("stale")),
				"Code editor accepted diagnostics for an obsolete document version");
		publish[0].accept(new GLCodeDiagnosticProvider.Result(latest[0].version(),
				java.util.List.of(new GLCodeDiagnostic(0, 3, GLCodeDiagnostic.Severity.WARNING,
						"server warning"))));
		flushEvents();
		check(editor.getDiagnostics().stream().anyMatch(value -> value.message().equals("server warning")),
				"Code editor did not expose current external diagnostics");
		editor.clearDiagnosticProvider();
		check(closed[0] && editor.getDiagnostics().stream()
				.noneMatch(value -> value.message().equals("server warning")),
				"Code editor did not close and clear its external diagnostic session");
	}
	private static void flushEvents() {
		try { EventQueue.invokeAndWait(() -> { }); }
		catch (Exception failure) { throw new AssertionError("Could not flush AWT events", failure); }
	}
	private static void press(GLCodeEditor editor, int code, char value) {
		press(editor, code, value, 0);
	}
	private static void press(GLCodeEditor editor, int code, char value,
			int modifiers) {
		editor.keyPressed(new GLKeyEvent(new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED,
				System.currentTimeMillis(), modifiers, code, value)));
	}
	private static void drainEventQueue() {
		try { EventQueue.invokeAndWait(() -> { }); }
		catch (Exception exception) { throw new AssertionError(exception); }
	}
	private static void type(GLCodeEditor editor, char value) {
		editor.keyTyped(new GLKeyEvent(new KeyEvent(new Canvas(), KeyEvent.KEY_TYPED,
				System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, value)));
	}
	private static final class OverviewEditor extends GLCodeEditor {
		private OverviewEditor(String source) { super(source); }
		private void jumpFromOverview(float y) { trailingDecorationPressed(y); }
	}
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
