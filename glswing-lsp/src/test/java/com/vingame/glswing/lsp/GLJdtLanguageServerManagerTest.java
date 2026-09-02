package com.vingame.glswing.lsp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;

final class GLJdtLanguageServerManagerTest {
	@TempDir Path temporary;

	@Test void resolvesRegisteredProfilesByLanguageAndExtension() {
		GLLanguageServerRegistry registry = GLLanguageServerRegistry.standard();
		var java = registry.forLanguage("JAVA").orElseThrow();
		assertTrue(java instanceof GLJdtLanguageServerProfile);
		assertTrue(registry.forPath(Path.of("Example.java")).orElseThrow() == java);
		assertTrue(registry.forPath(Path.of("notes.txt")).isEmpty());
		assertTrue(java.initializationOptions().containsKey("settings"));
		assertThrows(IllegalArgumentException.class,
				() -> registry.register(new GLJdtLanguageServerProfile()));
	}

	@Test void buildsCommandForValidatedInstallation() throws Exception {
		Path installation = temporary.resolve("jdtls");
		Files.createDirectories(installation.resolve("plugins"));
		Files.createDirectories(installation.resolve("config_win"));
		Files.createFile(installation.resolve("plugins/org.eclipse.equinox.launcher_1.0.jar"));
		var command = new GLJdtLanguageServerManager().command(installation,
				temporary.resolve("workspace"));
		assertTrue(command.contains("-configuration"));
		assertTrue(command.contains("-data"));
		assertTrue(command.stream().anyMatch(value -> value.startsWith(
				"-Djava.util.logging.config.file=")));
		assertTrue(command.stream().anyMatch(value -> value.endsWith(
				"org.eclipse.equinox.launcher_1.0.jar")));
	}

	@Test void rejectsDirectoryWithoutDistribution() {
		assertThrows(java.io.IOException.class, () ->
				new GLJdtLanguageServerManager().command(temporary, temporary));
	}

	@Test void registersJdtSpecificNotifications() {
		var methods = org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
				.getSupportedMethods(GLJdtLanguageClient.class);
		assertTrue(methods.containsKey("language/status"));
		assertTrue(methods.containsKey("language/eventNotification"));
	}

	@Test void suppressesOnlyKnownSpiFlyRegistrationNoise() {
		assertTrue(GLLanguageServer.isSpiFlyHeader(
				"Aug. 31, 2026 org.apache.aries.spifly.BaseActivator log"));
		assertTrue(GLLanguageServer.isSpiFlyProviderRegistration(
				"INFORMATION: Registered provider ch.qos.logback.classic.spi."
				+ "LogbackServiceProvider of service org.slf4j.spi.SLF4JServiceProvider "
				+ "in bundle ch.qos.logback.classic"));
		org.junit.jupiter.api.Assertions.assertFalse(
				GLLanguageServer.isSpiFlyProviderRegistration(
						"WARNING: Eclipse JDT LS could not initialize a project"));
	}

	@Test void convertsMarkdownHoverAndUtf16Range() {
		String source = "class A { String value; }";
		int start = source.indexOf("value");
		MarkupContent markup = new MarkupContent("markdown", "`String` **value**\n\nField docs");
		Hover hover = new Hover(markup, new Range(new Position(0, start),
				new Position(0, start + "value".length())));
		var converted = GLLanguageServer.convertHover(source, start, hover).orElseThrow();
		assertTrue(converted.contents().contains("String value"));
		assertTrue(converted.contents().contains("Field docs"));
		assertTrue(converted.start() == start && converted.end() == start + 5);
		assertTrue(GLLanguageServer.position("first\nsecond", 8).equals(new Position(1, 2)));
	}

	@Test void convertsSnippetPlaceholdersToPlainInsertionAndCaret() {
		var snippet = GLLanguageServer.plainSnippet("printf(${1:format}, ${2:args})$0");
		assertTrue(snippet.text().equals("printf(format, args)"));
		assertTrue(snippet.caret() == "printf(".length());
		var finalCaret = GLLanguageServer.plainSnippet("value$0");
		assertTrue(finalCaret.text().equals("value") && finalCaret.caret() == 5);
	}

	@Test void choosesConventionalJavaUtilImportWhenJdtReportsAmbiguity() {
		var selections = java.util.List.of(java.util.Map.of("candidates", java.util.List.of(
				java.util.Map.of("fullyQualifiedName", "java.awt.List", "id", "1"),
				java.util.Map.of("fullyQualifiedName", "java.util.List", "id", "2"))));
		var selected = (com.google.gson.JsonArray) GLLanguageServer.chooseImportCandidates(
				java.util.List.of("file:///Example.java", selections));
		assertTrue(selected.size() == 1 && selected.get(0).getAsJsonObject()
				.get("fullyQualifiedName").getAsString().equals("java.util.List"));
	}

	@Test void decodesRelativeSemanticTokensAndModifiers() {
		String source = "class Demo {\n  int value;\n}";
		SemanticTokens response = new SemanticTokens(java.util.List.of(
				0, 6, 4, 0, 1,
				1, 6, 5, 1, 2));
		var tokens = GLLanguageServer.decodeSemanticTokens(source, response,
				java.util.List.of("class", "property"),
				java.util.List.of("declaration", "readonly"));
		assertTrue(tokens.size() == 2);
		assertTrue(source.substring(tokens.get(0).start(), tokens.get(0).end())
				.equals("Demo"));
		assertTrue(tokens.get(0).type().equals("class")
				&& tokens.get(0).modifiers().contains("declaration"));
		assertTrue(source.substring(tokens.get(1).start(), tokens.get(1).end())
				.equals("value"));
		assertTrue(tokens.get(1).type().equals("property")
				&& tokens.get(1).modifiers().contains("readonly"));
	}





}
