package com.vingame.glswing.lsp;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Describes discovery, launch and source loading for one language-server family. */
public interface GLLanguageServerProfile {
	String id();
	String displayName();
	Set<String> languageIds();
	Set<String> fileExtensions();
	default Optional<Path> findInstallation() { return Optional.empty(); }
	default boolean supportsInstallationSelection() { return false; }
	default void selectInstallation(Path directory) {
		throw new UnsupportedOperationException(displayName() + " does not select installations");
	}
	default boolean supportsManagedInstallation() { return false; }
	default Path install(Consumer<String> progress) throws IOException, InterruptedException {
		throw new UnsupportedOperationException(displayName() + " has no managed installer");
	}
	List<String> command(Path installation, Path workspace) throws IOException;
	default Map<String, ?> initializationOptions() { return Map.of(); }
	default GLLanguageServer start(Path installation, Path workspace) throws IOException {
		return new GLLanguageServer(command(installation, workspace), workspace,
				initializationOptions());
	}
	/** Resolves file or server-specific virtual source returned by definition requests. */
	default CompletionStage<String> loadSource(GLLanguageServer server, URI uri) {
		if (!"file".equalsIgnoreCase(uri.getScheme()))
			return CompletableFuture.failedFuture(new IOException(
					"Unsupported source URI for " + displayName() + ": " + uri));
		return CompletableFuture.supplyAsync(() -> {
			try { return Files.readString(Path.of(uri)); }
			catch (IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
		});
	}
}
