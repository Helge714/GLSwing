package com.vingame.glswing.lsp;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Java profile backed by Eclipse JDT Language Server. */
public final class GLJdtLanguageServerProfile implements GLLanguageServerProfile {
	private final GLJdtLanguageServerManager manager;
	public GLJdtLanguageServerProfile() { this(new GLJdtLanguageServerManager()); }
	public GLJdtLanguageServerProfile(GLJdtLanguageServerManager manager) {
		if (manager == null) throw new IllegalArgumentException("Manager must not be null");
		this.manager = manager;
	}
	@Override public String id() { return "eclipse-jdt-ls"; }
	@Override public String displayName() { return "Eclipse JDT Language Server"; }
	@Override public Set<String> languageIds() { return Set.of("java"); }
	@Override public Set<String> fileExtensions() { return Set.of("java"); }
	@Override public Optional<Path> findInstallation() { return manager.findInstallation(); }
	@Override public boolean supportsInstallationSelection() { return true; }
	@Override public void selectInstallation(Path directory) { manager.selectInstallation(directory); }
	@Override public boolean supportsManagedInstallation() { return true; }
	@Override public Path install(Consumer<String> progress)
			throws IOException, InterruptedException { return manager.install(progress); }
	@Override public List<String> command(Path installation, Path workspace) throws IOException {
		return manager.command(installation, workspace);
	}
	@Override public Map<String, ?> initializationOptions() {
		return Map.of("extendedClientCapabilities", Map.of(
				"executeClientCommandSupport", true, "classFileContentsSupport", true),
				"settings", Map.of("java", Map.of(
						"signatureHelp", Map.of("enabled", true),
						"inlayHints", Map.of("parameterNames", Map.of("enabled", "all")))));
	}
	@Override public CompletionStage<String> loadSource(GLLanguageServer server, URI uri) {
		if ("jdt".equalsIgnoreCase(uri.getScheme())) return server.classFileContents(uri);
		return GLLanguageServerProfile.super.loadSource(server, uri);
	}
}
