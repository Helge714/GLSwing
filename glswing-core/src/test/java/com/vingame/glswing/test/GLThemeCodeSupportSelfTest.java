package com.vingame.glswing.test;

import java.awt.Color;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.vingame.glswing.ui.GLCodeCompletion;
import com.vingame.glswing.ui.GLCodeColor;
import com.vingame.glswing.ui.GLCodeDiagnosticProvider;
import com.vingame.glswing.ui.GLCodeDocument;
import com.vingame.glswing.ui.GLCodeEditor;
import com.vingame.glswing.ui.GLCodeLanguages;
import com.vingame.glswing.ui.GLCodeLocation;
import com.vingame.glswing.ui.GLCodeInlayHint;
import com.vingame.glswing.ui.GLCodeSemanticToken;

/** CPU checks for the built-in schema-aware .gltheme language. */
public final class GLThemeCodeSupportSelfTest {
	private GLThemeCodeSupportSelfTest() { }

	public static void main(String[] args) throws Exception {
		check(GLCodeLanguages.forFileName("application.gltheme").orElseThrow()
				== GLCodeLanguages.GLTHEME, ".gltheme extension was not detected");
		check(GLCodeLanguages.forFileName("Source.java").orElseThrow()
				== GLCodeLanguages.JAVA, "Java extension detection regressed");
		for (String name : List.of("default", "dark", "light")) {
			String resource = "/com/vingame/glswing/themes/" + name + ".gltheme";
			try (var stream = GLThemeCodeSupportSelfTest.class.getResourceAsStream(resource)) {
				check(stream != null, "Missing built-in theme " + resource);
				String builtIn = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
				check(GLCodeLanguages.GLTHEME.diagnose(builtIn).isEmpty(),
						"Built-in theme is rejected by GLTheme language: " + name);
			}
		}

		String source = "theme.type=bundle\n"
				+ "theme.extends=default\n"
				+ "button.background=${palette.accent}\n"
				+ "custom.brand=oklch(0.65, 0.14, 145deg, 75%)\n";
		URI uri = URI.create("file:///project/application.gltheme");
		GLCodeDocument document = new GLCodeDocument(uri, "gltheme", 0, source);
		GLCodeDiagnosticProvider provider =
				GLCodeLanguages.GLTHEME.codeIntelligenceProvider();
		check(provider != null, "GLTheme language has no in-process provider");
		AtomicReference<GLCodeDiagnosticProvider.Result> publication = new AtomicReference<>();
		try (GLCodeDiagnosticProvider.Session session = provider.open(document,
				publication::set)) {
			check(publication.get() != null && publication.get().diagnostics().isEmpty(),
					"Valid GLTheme source produced provider diagnostics");
			List<GLCodeSemanticToken> semantic = session.semanticTokens(document)
					.toCompletableFuture().join();
			check(semantic.stream().anyMatch(token -> "property".equals(token.type()))
					&& semantic.stream().anyMatch(token -> "variable".equals(token.type())),
					"Theme semantic classifications are incomplete");

			GLCodeDocument keyPrefix = new GLCodeDocument(uri, "gltheme", 1,
					"button.ba");
			List<GLCodeCompletion> keys = session.complete(keyPrefix,
					keyPrefix.text().length(), null).toCompletableFuture().join();
			check(keys.stream().anyMatch(item -> "button.background".equals(item.label())),
					"Optional theme-key completion is missing");

			int referenceOffset = source.indexOf("palette.accent") + 3;
			List<GLCodeLocation> definitions = session.definitions(document,
					referenceOffset).toCompletableFuture().join();
			check(definitions.isEmpty(), "Bundled base value resolved to a false local location");
			check(session.hover(document, source.indexOf("button.background") + 2)
					.toCompletableFuture().join().isPresent(),
					"Known theme property has no hover documentation");
			List<GLCodeColor> colors = session.documentColors(document)
					.toCompletableFuture().join();
			check(colors.stream().anyMatch(color -> color.start()
					== source.indexOf("oklch(")),
					"Direct custom color preview is missing");
			check(semantic.stream().anyMatch(token -> "function".equals(token.type())
					&& source.substring(token.start(), token.end()).equals("oklch")),
					"OKLCH function was not semantically highlighted");
			List<GLCodeInlayHint> hints = session.inlayHints(document)
					.toCompletableFuture().join();
			int colorStart = source.indexOf("0.65");
			check(hints.size() == 4 && hints.get(0).offset() == colorStart
					&& hints.stream().map(GLCodeInlayHint::label).toList()
							.equals(List.of("Lightness", "Chroma", "Hue", "Alpha")),
					"GLTheme color functions did not expose parameter inlay hints");
			String incompleteColor = "custom.brand=oklch(0.65, , 145deg, 75%)\n";
			List<GLCodeInlayHint> incompleteHints = session.inlayHints(new GLCodeDocument(
					uri, "gltheme", 1, incompleteColor)).toCompletableFuture().join();
			int emptySlot = incompleteColor.indexOf(',', incompleteColor.indexOf(',') + 1);
			check(incompleteHints.size() == 4
					&& incompleteHints.get(1).label().equals("Chroma")
					&& incompleteHints.get(1).offset() == emptySlot,
					"An empty color argument lost its structurally known inlay hint");
		}

		check(!GLCodeLanguages.GLTHEME.diagnose(
				"fileDialog.hiddenItalic=perhaps\n").isEmpty(),
				"Invalid boolean theme value was accepted");
		check(!GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=colors\nmetrics.cornerRadius=4\n").isEmpty(),
				"Color fragment accepted a metrics property");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"textShadow.offsetSpace=local\nfont.style=bold-italic\n").isEmpty(),
				"Supported theme enum value was rejected");
		check(!GLCodeLanguages.GLTHEME.diagnose("application.brand=${missing\n").isEmpty(),
				"Malformed reference in a custom theme property was accepted");

		Path directory = Files.createTempDirectory("gltheme-code-");
		try {
			Path fragment = directory.resolve("colors.gltheme");
			Files.writeString(fragment, "theme.type=colors\ncustom.brand=#112233\n");
			Path bundle = directory.resolve("bundle.gltheme");
			String bundleSource = "theme.type=bundle\n"
					+ "theme.colors=colors.gltheme\nbutton.background=${custom.brand}\n";
			Files.writeString(bundle, bundleSource);
			GLCodeDocument bundleDocument = new GLCodeDocument(bundle.toUri(),
					"gltheme", 0, bundleSource);
			AtomicReference<GLCodeDiagnosticProvider.Result> bundleResult =
					new AtomicReference<>();
			try (GLCodeDiagnosticProvider.Session session = provider.open(
					bundleDocument, bundleResult::set)) {
				check(bundleResult.get().diagnostics().isEmpty(),
						"Imported custom theme property was not resolved");
				int offset = bundleSource.indexOf("custom.brand") + 2;
				Optional<GLCodeLocation> location = session.definitions(bundleDocument, offset)
						.toCompletableFuture().join().stream().findFirst();
				check(location.isPresent() && location.get().uri().equals(fragment.toUri()),
						"Imported theme-property definition was not resolved");
				GLCodeDocument importedPrefix = new GLCodeDocument(bundle.toUri(),
						"gltheme", 1, bundleSource + "slider.background=${custom.br");
				List<GLCodeCompletion> imported = session.complete(importedPrefix,
						importedPrefix.text().length(), null).toCompletableFuture().join();
				check(imported.stream().anyMatch(item -> "custom.brand".equals(item.label())),
						"Imported custom property completion is missing");
				check(session.documentColors(bundleDocument).toCompletableFuture().join()
						.stream().anyMatch(color -> color.color().equals(new Color(17, 34, 51))),
						"Imported color reference preview is missing");
			}
			String missing = "theme.type=bundle\ntheme.colors=missing.gltheme\n";
			GLCodeDocument missingDocument = new GLCodeDocument(bundle.toUri(),
					"gltheme", 1, missing);
			AtomicReference<GLCodeDiagnosticProvider.Result> missingResult =
					new AtomicReference<>();
			try (GLCodeDiagnosticProvider.Session ignored = provider.open(
					missingDocument, missingResult::set)) {
				check(!missingResult.get().diagnostics().isEmpty(),
						"Missing theme import was not diagnosed");
			}

			GLCodeEditor editor = new GLCodeEditor(bundleSource, bundle.toUri());
			check(editor.getLanguage() == GLCodeLanguages.GLTHEME
					&& editor.getDiagnosticProvider() != null,
					"URI-configured editor did not install GLTheme support");
			editor.clearDiagnosticProvider();
		} finally {
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
					Files.deleteIfExists(path);
			}
		}
		System.out.println("GLTheme code-support self-test passed.");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
