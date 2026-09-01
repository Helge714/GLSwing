package com.vingame.glswing.lsp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Ordered application registry for language-server profiles. */
public final class GLLanguageServerRegistry {
	private final List<GLLanguageServerProfile> profiles = new ArrayList<>();

	/** Creates a registry containing the optional built-in Java/JDT profile. */
	public static GLLanguageServerRegistry standard() {
		GLLanguageServerRegistry registry = new GLLanguageServerRegistry();
		registry.register(new GLJdtLanguageServerProfile());
		return registry;
	}
	public void register(GLLanguageServerProfile profile) {
		if (profile == null) throw new IllegalArgumentException("Profile must not be null");
		if (profiles.stream().anyMatch(value -> value.id().equalsIgnoreCase(profile.id())))
			throw new IllegalArgumentException("Language-server profile is already registered: "
					+ profile.id());
		profiles.add(profile);
	}
	public boolean unregister(String id) {
		return id != null && profiles.removeIf(value -> value.id().equalsIgnoreCase(id));
	}
	public List<GLLanguageServerProfile> profiles() { return List.copyOf(profiles); }
	public Optional<GLLanguageServerProfile> forLanguage(String languageId) {
		if (languageId == null) return Optional.empty();
		String normalized = languageId.toLowerCase(Locale.ROOT);
		return profiles.stream().filter(profile -> profile.languageIds().stream()
				.anyMatch(value -> value.equalsIgnoreCase(normalized))).findFirst();
	}
	public Optional<GLLanguageServerProfile> forPath(Path path) {
		if (path == null || path.getFileName() == null) return Optional.empty();
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		String extension = dot < 0 ? "" : name.substring(dot + 1);
		return profiles.stream().filter(profile -> profile.fileExtensions().stream()
				.anyMatch(value -> normalizeExtension(value).equals(extension))).findFirst();
	}
	private static String normalizeExtension(String value) {
		String normalized = value.toLowerCase(Locale.ROOT);
		return normalized.startsWith(".") ? normalized.substring(1) : normalized;
	}
}
