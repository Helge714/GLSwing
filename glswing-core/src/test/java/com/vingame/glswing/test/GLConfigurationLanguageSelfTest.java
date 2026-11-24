package com.vingame.glswing.test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.vingame.glswing.ui.GLCodeCompletion;
import com.vingame.glswing.ui.GLCodeDiagnosticProvider;
import com.vingame.glswing.ui.GLCodeDocument;
import com.vingame.glswing.ui.GLCodeLanguages;
import com.vingame.glswing.ui.GLCodeLocation;

/** CPU checks for native input-map and localization editing support. */
public final class GLConfigurationLanguageSelfTest {
	private GLConfigurationLanguageSelfTest() { }

	public static void main(String[] args) throws Exception {
		testInputLanguage();
		testLocalizationLanguage();
		System.out.println("GL configuration-language self-test passed.");
	}

	private static void testInputLanguage() throws Exception {
		check(GLCodeLanguages.forFileName("application.glinput").orElseThrow()
				== GLCodeLanguages.GLINPUT, ".glinput extension was not detected");
		check(GLCodeLanguages.GLINPUT.diagnose(
				"key.application.save=shortcut+S\nvalue.application.scale=2.5\n").isEmpty(),
				"Valid application input entries were rejected");
		check(!GLCodeLanguages.GLINPUT.diagnose(
				"key.application.save=CTRL+NOT_A_KEY\n").isEmpty(),
				"Unknown input key was accepted");
		check(!GLCodeLanguages.GLINPUT.diagnose(
				"value.application.scale=0\n").isEmpty(),
				"Non-positive input preference was accepted");

		Path directory = Files.createTempDirectory("glinput-code-");
		try {
			Path base = directory.resolve("base.glinput");
			Files.writeString(base, "key.application.save=shortcut+S\n",
					StandardCharsets.UTF_8);
			Path child = directory.resolve("child.glinput");
			String source = "input.extends=base.glinput\nkey.hist";
			URI uri = child.toUri();
			GLCodeDocument document = new GLCodeDocument(uri, "glinput", 0, source);
			GLCodeDiagnosticProvider provider =
					GLCodeLanguages.GLINPUT.codeIntelligenceProvider();
			AtomicReference<GLCodeDiagnosticProvider.Result> publication =
					new AtomicReference<>();
			try (GLCodeDiagnosticProvider.Session session = provider.open(document,
					publication::set)) {
				check(publication.get() != null && publication.get().diagnostics().isEmpty(),
						"Valid input inheritance produced diagnostics");
				List<GLCodeCompletion> completions = session.complete(document,
						source.length(), null).toCompletableFuture().join();
				check(completions.stream().anyMatch(item ->
						"key.history.undo".equals(item.label())),
						"Standard input action completion is missing");
				int pathOffset = source.indexOf("base.glinput") + 2;
				List<GLCodeLocation> definitions = session.definitions(document, pathOffset)
						.toCompletableFuture().join();
				check(definitions.size() == 1 && definitions.get(0).uri().equals(base.toUri()),
						"Input parent navigation failed");
			}
		} finally {
			deleteTree(directory);
		}
	}

	private static void testLocalizationLanguage() throws Exception {
		check(GLCodeLanguages.forFileName("Messages_de.properties").orElseThrow()
				== GLCodeLanguages.LOCALIZATION,
				".properties extension was not detected as localization");
		check(!GLCodeLanguages.LOCALIZATION.diagnose("message=Broken {0\n").isEmpty(),
				"Malformed MessageFormat pattern was accepted");

		Path directory = Files.createTempDirectory("localization-code-");
		try {
			Path root = directory.resolve("Messages.properties");
			Files.writeString(root, "greeting=Hello, {0}!\nfarewell=Goodbye\n",
					StandardCharsets.UTF_8);
			Path german = directory.resolve("Messages_de.properties");
			String invalid = "greeting=Hallo, {1}!\nextra=Zusatz\n";
			GLCodeDocument invalidDocument = new GLCodeDocument(german.toUri(),
					"properties", 0, invalid);
			GLCodeDiagnosticProvider provider =
					GLCodeLanguages.LOCALIZATION.codeIntelligenceProvider();
			AtomicReference<GLCodeDiagnosticProvider.Result> publication =
					new AtomicReference<>();
			try (GLCodeDiagnosticProvider.Session ignored = provider.open(invalidDocument,
					publication::set)) {
				List<String> messages = publication.get().diagnostics().stream()
						.map(diagnostic -> diagnostic.message()).toList();
				check(messages.stream().anyMatch(message -> message.startsWith("Missing 1")),
						"Missing localization key was not reported");
				check(messages.stream().anyMatch(message -> message.contains("not declared")),
						"Additional localization key was not reported");
				check(messages.stream().anyMatch(message -> message.contains("arguments differ")),
						"MessageFormat argument mismatch was not reported");
			}

			String valid = "greeting=Hallo, {0}!\nfare";
			GLCodeDocument document = new GLCodeDocument(german.toUri(), "properties", 1,
					valid);
			try (GLCodeDiagnosticProvider.Session session = provider.open(document,
					ignored -> { })) {
				List<GLCodeCompletion> completions = session.complete(document, valid.length(),
						null).toCompletableFuture().join();
				check(completions.stream().anyMatch(item -> "farewell".equals(item.label())),
						"Root localization-key completion is missing");
				List<GLCodeLocation> definitions = session.definitions(document, 2)
						.toCompletableFuture().join();
				check(definitions.size() == 1 && definitions.get(0).uri().equals(root.toUri()),
						"Root localization-key navigation failed");
				check(session.hover(document, 2).toCompletableFuture().join().isPresent(),
						"Root localization message hover is missing");
			}
		} finally {
			deleteTree(directory);
		}
	}

	private static void deleteTree(Path directory) throws Exception {
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
				Files.deleteIfExists(path);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
