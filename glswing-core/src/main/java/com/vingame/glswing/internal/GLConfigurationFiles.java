package com.vingame.glswing.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Internal UTF-8 override-file support shared by public configuration editors. */
public final class GLConfigurationFiles {
	private GLConfigurationFiles() {
	}

	public static Map<String, String> read(Path path, String description) {
		Map<String, String> result = new LinkedHashMap<>();
		try (BufferedReader reader = Files.newBufferedReader(path,
				StandardCharsets.UTF_8)) {
			String line;
			int number = 0;
			while ((line = reader.readLine()) != null) {
				number++;
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
				int separator = trimmed.indexOf('=');
				if (separator <= 0)
					throw new IllegalArgumentException("Invalid line " + number
							+ " in " + description);
				String key = trimmed.substring(0, separator).trim();
				String value = trimmed.substring(separator + 1).trim();
				if (result.put(key, value) != null)
					throw new IllegalArgumentException("Duplicate property " + key
							+ " in " + description);
			}
			return result;
		} catch (IOException error) {
			throw new IllegalArgumentException("Cannot read " + description, error);
		}
	}

	public static void writeAtomically(Path path, String header,
			String extendsKey, String baseReference, Map<String, String> overrides) {
		if (path == null) throw new IllegalArgumentException("Path must not be null");
		Path target = path.toAbsolutePath().normalize();
		Path directory = target.getParent();
		if (directory == null)
			throw new IllegalArgumentException("Configuration path has no parent");
		try {
			Files.createDirectories(directory);
			Path temporary = Files.createTempFile(directory,
					target.getFileName().toString(), ".tmp");
			try {
				StringBuilder text = new StringBuilder("# ").append(header).append('\n');
				if (baseReference != null && !baseReference.isBlank())
					text.append(extendsKey).append('=').append(baseReference).append('\n');
				if (!overrides.isEmpty()) text.append('\n');
				overrides.forEach((key, value) -> {
					validateLine(key, value);
					text.append(key).append('=').append(value).append('\n');
				});
				Files.writeString(temporary, text, StandardCharsets.UTF_8);
				try {
					Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
							StandardCopyOption.REPLACE_EXISTING);
				} catch (AtomicMoveNotSupportedException unsupported) {
					Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				Files.deleteIfExists(temporary);
			}
		} catch (IOException error) {
			throw new IllegalArgumentException("Cannot write configuration " + target,
					error);
		}
	}

	private static void validateLine(String key, String value) {
		if (key == null || key.isBlank() || value == null
				|| key.indexOf('\n') >= 0 || key.indexOf('=') >= 0
				|| value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
			throw new IllegalArgumentException("Configuration entries must fit one line");
	}
}
