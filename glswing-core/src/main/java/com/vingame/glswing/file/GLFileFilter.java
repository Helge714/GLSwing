package com.vingame.glswing.file;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Named extension filter; directories always remain navigable. */
public record GLFileFilter(String description, List<String> extensions) {
	public GLFileFilter {
		if (description == null || description.isBlank() || extensions == null)
			throw new IllegalArgumentException("File filter requires a description and extensions");
		extensions = extensions.stream().map(GLFileFilter::normalize).distinct().toList();
	}
	public GLFileFilter(String description, String... extensions) {
		this(description, List.of(extensions));
	}
	public boolean accepts(Path path) {
		if (java.nio.file.Files.isDirectory(path) || extensions.isEmpty()) return true;
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return extensions.stream().anyMatch(value -> name.endsWith("." + value));
	}
	public String swtPattern() {
		return extensions.isEmpty() ? "*.*"
				: extensions.stream().map(value -> "*." + value)
						.collect(java.util.stream.Collectors.joining(";"));
	}
	private static String normalize(String value) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("File extension must not be blank");
		String result = value.strip().toLowerCase(Locale.ROOT);
		while (result.startsWith("*")) result = result.substring(1);
		while (result.startsWith(".")) result = result.substring(1);
		return result;
	}
}
