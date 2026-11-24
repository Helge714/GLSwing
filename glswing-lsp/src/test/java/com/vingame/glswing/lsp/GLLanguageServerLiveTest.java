package com.vingame.glswing.lsp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.ui.GLCodeDocument;
import com.vingame.glswing.ui.GLCodeEditor;
import com.vingame.glswing.ui.GLCodeWorkspace;
import com.vingame.glswing.document.GLDocument;
import com.vingame.glswing.document.GLDocumentWorkspace;
import com.vingame.glswing.history.GLHistoryManager;

/** Opt-in probe against the locally installed JDT LS distribution. */
@EnabledIfSystemProperty(named = "glswing.liveLsp", matches = "true")
final class GLLanguageServerLiveTest {
	@Test void resolvesReferencesAndRenameInTheShowcaseFixture() throws Exception {
		Path workspace = Path.of("..").toAbsolutePath().normalize();
		Path sourceFile = workspace.resolve("glswing-examples/src/main/java/"
				+ "com/vingame/glswing/demo/HelloGLSwing.java");
		String source = Files.readString(sourceFile);
		GLJdtLanguageServerProfile profile = new GLJdtLanguageServerProfile();
		Path installation = profile.findInstallation().orElseThrow();
		try (GLLanguageServer server = new GLLanguageServer(
				profile.command(installation, workspace), workspace,
				profile.initializationOptions())) {
			String probeSource = source.replace("Greeting greeting =",
					"List<String> names = null;\n\t\tGreeting greeting =");
			CountDownLatch reconciled = new CountDownLatch(1);
			GLCodeDocument document = new GLCodeDocument(
					sourceFile.toUri(), "java", 0, probeSource);
			try (var session = server.open(document, ignored -> reconciled.countDown())) {
				assertTrue(reconciled.await(30, TimeUnit.SECONDS),
						"JDT LS did not reconcile the showcase fixture");
				int symbol = probeSource.indexOf("Greeting greeting");
				var references = session.references(document, symbol, true)
						.toCompletableFuture().get(15, TimeUnit.SECONDS);
				assertFalse(references.isEmpty(), "JDT LS returned no Greeting references");
				var edits = session.rename(document, symbol, "RenamedGreeting")
						.toCompletableFuture().get(15, TimeUnit.SECONDS);
				assertFalse(edits.isEmpty(), "JDT LS returned no Greeting rename edits");
				int variable = probeSource.indexOf("greeting =");
				var variableEdits = session.rename(document,
						variable + "greeting".length() / 2, "renamedGreeting")
						.toCompletableFuture().get(15, TimeUnit.SECONDS);
				assertTrue(variableEdits.stream().filter(edit ->
						document.uri().equals(edit.uri()))
						.map(com.vingame.glswing.ui.GLCodeWorkspaceEdit::text)
						.mapToLong(text -> text.split("renamedGreeting", -1).length - 1)
						.sum() >= 2,
						"JDT LS rename edits did not contain both local-variable replacements");
				int unresolvedType = probeSource.indexOf("List<String>");
				var actions = session.codeActions(document, unresolvedType,
						unresolvedType + "List".length()).toCompletableFuture()
						.get(15, TimeUnit.SECONDS);
				System.out.println("Live JDT code actions: " + actions.stream()
						.map(com.vingame.glswing.ui.GLCodeAction::title).toList());
				assertFalse(actions.isEmpty(),
						"JDT LS quick fixes were lost while converting code actions");
			}
			GLDocumentWorkspace documents = new GLDocumentWorkspace();
			GLCodeWorkspace workspaceView = new GLCodeWorkspace(documents);
			GLCodeEditor editor = new GLCodeEditor(source, sourceFile.toUri());
			GLDocument<com.vingame.glswing.ui.GLTextDocument> managed = new GLDocument<>(
					editor.getDocument(), "HelloGLSwing.java", sourceFile.toUri(),
					new GLHistoryManager());
			EventQueue.invokeAndWait(() -> {
				workspaceView.addDocument(managed, editor);
				// The showcase attaches JDT only after the editor is already managed.
				editor.setDiagnosticProvider(server, sourceFile.toUri());
				int selectedVariable = source.indexOf("greeting =");
				editor.select(selectedVariable,
						selectedVariable + "greeting".length());
				editor.keyPressed(new GLKeyEvent(new KeyEvent(new Canvas(),
						KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
						KeyEvent.VK_F2, KeyEvent.CHAR_UNDEFINED)));
			});
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
			while (!editor.isInlineRenameActive() && System.nanoTime() < deadline) {
				Thread.sleep(50);
				EventQueue.invokeAndWait(() -> { });
			}
			assertTrue(editor.isInlineRenameActive(),
					"F2 did not start inline rename through GLCodeEditor");
			assertTrue(editor.getCarets().size() >= 2,
					"Inline rename did not include the second greeting occurrence");
			EventQueue.invokeAndWait(() -> {
				editor.clearDiagnosticProvider();
				workspaceView.close();
				documents.close();
			});
		}
	}
}
