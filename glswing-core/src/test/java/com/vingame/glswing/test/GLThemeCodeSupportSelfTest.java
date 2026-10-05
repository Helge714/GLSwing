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
import com.vingame.glswing.ui.GLCodeToken;
import com.vingame.glswing.ui.GLCodePropertySchema.ValueType;
import com.vingame.glswing.ui.GLThemeCodeSupport;

/** CPU checks for the built-in schema-aware .gltheme language. */
public final class GLThemeCodeSupportSelfTest {
	private GLThemeCodeSupportSelfTest() { }

	public static void main(String[] args) throws Exception {
		check(GLCodeLanguages.forFileName("application.gltheme").orElseThrow()
				== GLCodeLanguages.GLTHEME, ".gltheme extension was not detected");
		check(GLCodeLanguages.forFileName("application.glschema").orElseThrow()
				== GLCodeLanguages.GLSCHEMA, ".glschema extension was not detected");
		String schemaSource = """
				schema.name=Application
				application.tint=COLOR, "Application tint"
				application.padding=INSETS, "Content padding", min=0
				application.mode=ENUM, "Layout mode", values=compact|comfortable
				""";
		check(GLCodeLanguages.GLSCHEMA.diagnose(schemaSource).isEmpty(),
				"Valid schema source was rejected");
		check(GLCodeLanguages.GLSCHEMA.diagnose(
				"application.mode=ENUM, \"Layout mode\"").stream()
				.anyMatch(diagnostic -> diagnostic.message().contains("values")),
				"Missing enum choices were not diagnosed");
		try (var stream = GLThemeCodeSupportSelfTest.class.getResourceAsStream(
				"/com/vingame/glswing/themes/default.glschema")) {
			check(stream != null, "Missing built-in theme schema");
			String builtInSchema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			check(GLCodeLanguages.GLSCHEMA.diagnose(builtInSchema).isEmpty(),
					"Built-in schema is rejected by GLSchema language");
			check(!builtInSchema.contains("palette.")
					&& !builtInSchema.contains("metrics."),
					"Reference tokens remained duplicated in default.glschema");
		}
		check(GLCodeLanguages.forFileName("Source.java").orElseThrow()
				== GLCodeLanguages.JAVA, "Java extension detection regressed");
		for (String key : List.of("metrics.padding", "list.cellPadding",
				"tabbedPane.contentPadding", "node.contentPadding"))
			check(GLThemeCodeSupport.schema().property(key) != null
					&& GLThemeCodeSupport.schema().property(key).type() == ValueType.INSETS,
					"Inset property was inferred as the wrong value type: " + key);
		for (String name : List.of("default", "blue", "darkblue", "light")) {
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
			check(publication.get() != null
					&& publication.get().diagnostics().stream().noneMatch(diagnostic ->
							diagnostic.severity() == com.vingame.glswing.ui.GLCodeDiagnostic
									.Severity.ERROR)
					&& publication.get().diagnostics().stream().anyMatch(diagnostic ->
							diagnostic.message().contains("custom.brand")),
					"Undeclared custom property did not remain a warning");
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
			GLCodeDocument annotationPrefix = new GLCodeDocument(uri, "gltheme", 1,
					"@IN");
			check(session.complete(annotationPrefix, annotationPrefix.text().length(), null)
					.toCompletableFuture().join().stream().anyMatch(item ->
							"@INSETS".equals(item.label())
							&& "@INSETS ".equals(item.insertText())),
					"Inline theme-token type completion is missing");
			GLCodeDocument enumAnnotationPrefix = new GLCodeDocument(uri, "gltheme", 1,
					"@EN");
			check(session.complete(enumAnnotationPrefix,
					enumAnnotationPrefix.text().length(), null).toCompletableFuture().join()
					.stream().anyMatch(item -> "@ENUM".equals(item.label())
							&& item.insertText().startsWith("@ENUM<value1, value2>")),
					"Inline enum completion is missing its value list");
			GLCodeDocument derivePrefix = new GLCodeDocument(uri, "gltheme", 1,
					"button.background=der");
			check(session.complete(derivePrefix, derivePrefix.text().length(), null)
					.toCompletableFuture().join().stream().anyMatch(item ->
							item.label().startsWith("derive(")),
					"derive color completion is missing");
			String caretAtReferenceStart = "button.background=${palette.accent}";
			GLCodeDocument referenceStart = new GLCodeDocument(uri, "gltheme", 2,
					caretAtReferenceStart);
			int dollar = caretAtReferenceStart.indexOf('$');
			check(session.complete(referenceStart, dollar, null).toCompletableFuture()
					.join() != null,
					"Completion failed with the caret at a reference boundary");
			check(session.complete(referenceStart, dollar + 1, null).toCompletableFuture()
					.join() != null,
					"Completion failed between '$' and '{'");

			String metricsSource = "theme.type=metrics\nmetrics.cornerRadius=4\n";
			GLCodeDocument metricsDocument = new GLCodeDocument(uri, "gltheme", 1,
					metricsSource);
			List<GLCodeCompletion> metricKeys = session.complete(metricsDocument,
					metricsSource.length(), null).toCompletableFuture().join();
			check(metricKeys.stream().anyMatch(item ->
					"button.cornerRadius".equals(item.label()))
					&& metricKeys.stream().noneMatch(item ->
							"metrics.cornerRadius".equals(item.label()))
					&& metricKeys.stream().noneMatch(item ->
							"palette.accent".equals(item.label()))
					&& metricKeys.stream().noneMatch(item ->
							"codeEditor.keyword".equals(item.label()))
					&& metricKeys.stream().noneMatch(item ->
							"node.titleGradientLight".equals(item.label()))
					&& metricKeys.stream().noneMatch(item ->
							"checkBox.check".equals(item.label()))
					&& metricKeys.stream().noneMatch(item ->
							"menu.accelerator".equals(item.label()))
					&& metricKeys.stream().anyMatch(item ->
							"theme.name".equals(item.label()))
					&& metricKeys.stream().noneMatch(item ->
							"theme.type".equals(item.label())),
					"Metrics-fragment completion is not scoped or repeats declarations: "
							+ metricKeys.stream().map(GLCodeCompletion::label).toList());

			String colorsSource = "theme.type=colors\npalette.accent=#112233\n";
			GLCodeDocument colorsDocument = new GLCodeDocument(uri, "gltheme", 2,
					colorsSource);
			List<GLCodeCompletion> colorKeys = session.complete(colorsDocument,
					colorsSource.length(), null).toCompletableFuture().join();
			check(colorKeys.stream().anyMatch(item ->
					"button.background".equals(item.label()))
					&& colorKeys.stream().anyMatch(item ->
							"theme.name".equals(item.label()))
					&& colorKeys.stream().anyMatch(item ->
							"codeEditor.keyword".equals(item.label()))
					&& colorKeys.stream().noneMatch(item ->
							"palette.accent".equals(item.label()))
					&& colorKeys.stream().noneMatch(item ->
							"button.cornerRadius".equals(item.label())),
					"Color-fragment completion is not scoped or repeats declarations");

			int referenceOffset = source.indexOf("palette.accent") + 3;
			List<GLCodeLocation> definitions = session.definitions(document,
					referenceOffset).toCompletableFuture().join();
			check(definitions.isEmpty(), "Bundled base value resolved to a false local location");
			int presetOffset = source.indexOf("default") + 2;
			List<GLCodeLocation> presetDefinitions = session.definitions(document,
					presetOffset).toCompletableFuture().join();
			check(presetDefinitions.size() == 1
					&& presetDefinitions.get(0).uri().toString().endsWith(
							"/com/vingame/glswing/themes/default.gltheme"),
					"Built-in default theme has no navigable source");
			try (var presetSource = presetDefinitions.get(0).uri().toURL().openStream()) {
				check(new String(presetSource.readAllBytes(), StandardCharsets.UTF_8)
						.contains("theme.name=Default"),
						"Built-in definition URI cannot load its source");
			}
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

			String insetSource = "list.cellPadding=12\n"
					+ "tabbedPane.contentPadding=4, 8\n"
					+ "node.contentPadding=1, 2, 3\n"
					+ "metrics.padding=1, 2, 3, 4\n";
			List<String> insetLabels = session.inlayHints(new GLCodeDocument(
					uri, "gltheme", 2, insetSource)).toCompletableFuture().join()
					.stream().map(GLCodeInlayHint::label).toList();
			check(insetLabels.equals(List.of("All", "Vertical", "Horizontal",
					"Top", "Horizontal", "Bottom", "Top", "Left", "Bottom",
					"Right")), "Inset shorthand did not expose structural inlay hints: "
							+ insetLabels);

			String liveEnumSource =
					"@ENUM<compact, comfortable, system> application.density=";
			GLCodeDocument liveEnum = new GLCodeDocument(uri, "gltheme", 3,
					liveEnumSource);
			session.update(liveEnum);
			check(publication.get() != null && publication.get().version() == 3
					&& publication.get().diagnostics().stream().noneMatch(diagnostic ->
							diagnostic.message().contains("application.density")),
					"A live inline enum remained unknown after the document update");
			List<String> liveEnumValues = session.complete(liveEnum,
					liveEnumSource.length(), null).toCompletableFuture().join().stream()
					.map(GLCodeCompletion::label).toList();
			check(liveEnumValues.equals(List.of("compact", "comfortable", "system")),
					"Live inline enum values were not completed: " + liveEnumValues);
			String liveTypesSource = "@COLOR application.brand=#336699\n"
					+ "@INSETS application.padding=2, 6\n"
					+ "button.background=${application.brand}\n"
					+ "list.cellPadding=${application.padding}\n";
			GLCodeDocument liveTypes = new GLCodeDocument(uri, "gltheme", 4,
					liveTypesSource);
			session.update(liveTypes);
			check(publication.get().diagnostics().stream().noneMatch(diagnostic ->
					diagnostic.message().contains("application.brand")
							|| diagnostic.message().contains("application.padding")),
					"Live non-enum annotations remained unknown after an update");
			check(session.documentColors(liveTypes).toCompletableFuture().join().stream()
					.anyMatch(color -> color.color().equals(new Color(0x33, 0x66, 0x99))),
					"Live @COLOR declaration did not produce a color preview");
			check(session.inlayHints(liveTypes).toCompletableFuture().join().stream()
					.map(GLCodeInlayHint::label).toList()
					.containsAll(List.of("Vertical", "Horizontal")),
					"Live @INSETS declaration did not produce inset hints");
		}

		check(!GLCodeLanguages.GLTHEME.diagnose(
				"fileDialog.hiddenItalic=perhaps\n").isEmpty(),
				"Invalid boolean theme value was accepted");
		check(!GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=colors\nmetrics.cornerRadius=4\n").isEmpty(),
				"Color fragment accepted a metrics property");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=colors\ntheme.name=Brand colors\ncodeEditor.keyword=#cc6c1d\n")
				.isEmpty(), "Color fragment rejected its metadata or editor colors");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=colors\nnodeEditor.gridMinor=#202020\n"
				+ "nodeEditor.gridMajor=#303030\n"
				+ "nodeEditor.connectionColorInterpolation=oklab\n").isEmpty(),
				"Node-editor color configuration was misclassified as metrics");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=colors\n@COLOR palette.applicationBrand=#336699\n").isEmpty(),
				"Inline color token was not added to the document schema");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=colors\n@NUMBER button.cornerRadius=13\n").isEmpty(),
				"An explicitly typed numeric override was rejected in a color fragment");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=metrics\n@NUMBER metrics.animationDuration=180\n").isEmpty(),
				"Inline numeric token was not added to the document schema");
		check(!GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=metrics\n@NUMBER metrics.animationDuration=fast\n").isEmpty(),
				"Inline metric token was not type-checked");
		List<com.vingame.glswing.ui.GLCodeDiagnostic> conflictingAnnotation =
				GLCodeLanguages.GLTHEME.diagnose(
						"@INSETS palette.text=rgb(0, 0, 0)\n"
						+ "@COLOR application.brand=#336699\n");
		check(conflictingAnnotation.stream().anyMatch(diagnostic ->
				diagnostic.severity()
						== com.vingame.glswing.ui.GLCodeDiagnostic.Severity.ERROR
				&& diagnostic.message().contains("palette.text")
				&& diagnostic.message().contains("cannot be declared as insets")),
				"Conflicting annotation on a known property was not diagnosed");
		check(conflictingAnnotation.stream().noneMatch(diagnostic ->
				diagnostic.message().contains("application.brand")),
				"One conflicting annotation discarded a later valid declaration");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=metrics\nmetrics.padding=2, 6\n").isEmpty(),
				"Exact inset declaration was not preserved");
		check(!GLCodeLanguages.GLTHEME.diagnose(
				"@ENUM application.mode=compact\n").isEmpty(),
				"Inline ENUM without a declared value set was accepted");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"@ENUM<compact, comfortable, system> application.mode=comfortable\n")
				.isEmpty(), "Valid inline enum declaration was rejected");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"@ENUM<compact, comfortable> application.mode=system\n").stream()
				.anyMatch(diagnostic -> diagnostic.message().contains(
						"Expected one of compact, comfortable")),
				"Inline enum accepted a value outside its declaration");
		check(!GLCodeLanguages.GLTHEME.diagnose(
				"@ENUM<compact, COMPACT> application.mode=compact\n").isEmpty(),
				"Inline enum accepted duplicate values");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"@INSETS application.cardPadding=2, 6\n"
				+ "list.cellPadding=${application.cardPadding}\n").isEmpty(),
				"Compatible inline inset reference was rejected");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"@INSETS application.cardPadding=2, 6\n"
				+ "button.height=${application.cardPadding}\n").stream()
				.anyMatch(diagnostic -> diagnostic.message().contains("expects number")
						&& diagnostic.message().contains("is insets")),
				"Inline token reference was not type-checked");
		check(!GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=metrics\nnodeEditor.gridMajor=#303030\n").isEmpty(),
				"Metrics fragment accepted a node-editor grid color");
		check(!GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=metrics\ncodeEditor.keyword=#cc6c1d\n").isEmpty(),
				"Metrics fragment accepted a code-editor color");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"theme.type=metrics\nnode.shadowOffsetX=2\nnode.shadowOffsetY=3\n")
				.isEmpty(), "Node shadow offsets were misclassified as colors");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"button.height=${metrics.padding}\n").stream().anyMatch(diagnostic ->
						diagnostic.message().contains("expects number")
						&& diagnostic.message().contains("is insets")),
				"A theme reference connected incompatible value types");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"textShadow.offsetSpace=local\nfont.style=bold-italic\n").isEmpty(),
				"Supported theme enum value was rejected");
		GLCodeEditor colorFormatter = new GLCodeEditor(
				"@COLOR application.opaque=rgba(10, 20, 30, 255)\n"
				+ "@COLOR application.translucent=rgba(10, 20, 30, 128)");
		colorFormatter.setLanguage(GLCodeLanguages.GLTHEME);
		colorFormatter.setColorExpressionFormat(new com.vingame.glswing.ui
				.GLColorExpressionFormat(com.vingame.glswing.ui.GLColorExpressionFormat
						.ChannelNotation.BYTE, 4, true, true));
		colorFormatter.formatCode();
		check(colorFormatter.getText().contains("application.opaque=rgb(10, 20, 30)")
				&& colorFormatter.getText().contains(
						"application.translucent=rgb(10, 20, 30, 128)")
				&& !colorFormatter.getText().contains("rgba("),
				"Theme formatting did not normalize rgba or omit opaque alpha");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"button.background=derive(${palette.accent}, alpha*=50%)\n")
				.isEmpty(), "Valid referenced derive expression was rejected");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"button.background=derive(${palette.accent}, test*=1)\n").stream()
				.anyMatch(diagnostic -> diagnostic.message().contains(
						"Unsupported derive channel")),
				"Invalid referenced derive adjustment was accepted");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"button.background=derive(${palette.accent}, alpha/=2)\n").stream()
				.anyMatch(diagnostic -> diagnostic.severity()
						== com.vingame.glswing.ui.GLCodeDiagnostic.Severity.ERROR),
				"Unsupported derive operator was accepted");
		check(GLCodeLanguages.GLTHEME.diagnose(
				"button.borderWidth=${metrics.borderWidth} + (2 * 3)\n"
				+ "button.padding=12\n").isEmpty(),
				"Metric expressions or inset shorthand were rejected");
		check(!GLCodeLanguages.GLTHEME.diagnose(
				"button.borderWidth=${metrics.borderWidth} + * 2\n").isEmpty(),
				"Malformed metric expression was accepted");
		check(!GLCodeLanguages.GLTHEME.diagnose("application.brand=${missing\n").isEmpty(),
				"Malformed reference in a custom theme property was accepted");
		String inlineComment = "metrics.cornerRadius=4 # Keep corners compact\n"
				+ "palette.accent = #112233 ! Hex colors remain values\n";
		check(GLCodeLanguages.GLTHEME.diagnose(inlineComment).isEmpty(),
				"Valid inline theme comments were rejected");
		List<GLCodeToken> inlineTokens = GLCodeLanguages.GLTHEME.tokenize(inlineComment);
		check(inlineTokens.stream().filter(token ->
				token.type() == GLCodeToken.Type.COMMENT).count() == 2,
				"Inline theme comments were not highlighted as comments");

		Path directory = Files.createTempDirectory("gltheme-code-");
		try {
			Files.writeString(directory.resolve("application.glschema"), """
					custom.brand=COLOR, "Application brand color"
					custom.cellPadding=INSETS, "Application cell padding", min=0
					""");
			Path fragment = directory.resolve("colors.gltheme");
			Files.writeString(fragment, "theme.type=colors\ncustom.brand=#112233\n");
			Path bundle = directory.resolve("bundle.gltheme");
			String bundleSource = "theme.type=bundle\n"
					+ "theme.schema=application.glschema\n"
					+ "theme.colors=colors.gltheme\n"
					+ "custom.cellPadding=2, 6\n"
					+ "button.background=${custom.brand}\n";
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
				check(session.inlayHints(bundleDocument).toCompletableFuture().join()
						.stream().map(GLCodeInlayHint::label).toList()
						.containsAll(List.of("Vertical", "Horizontal")),
						"Custom schema did not provide inset inlay hints");
			}
			String unknownSource = "application.unknownPadding=1, 2, 3, 4\n";
			GLCodeDocument unknownDocument = new GLCodeDocument(
					directory.resolve("unknown.gltheme").toUri(), "gltheme", 0,
					unknownSource);
			AtomicReference<GLCodeDiagnosticProvider.Result> unknownResult =
					new AtomicReference<>();
			try (GLCodeDiagnosticProvider.Session unknownSession = provider.open(
					unknownDocument, unknownResult::set)) {
				check(unknownResult.get().diagnostics().stream().anyMatch(diagnostic ->
						diagnostic.severity() == com.vingame.glswing.ui.GLCodeDiagnostic
								.Severity.WARNING
						&& diagnostic.message().contains("Unknown property")),
						"Unknown theme property did not produce an editor warning");
				check(unknownSession.inlayHints(unknownDocument).toCompletableFuture().join()
						.isEmpty(), "Unknown Padding-like name was heuristically typed");
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
