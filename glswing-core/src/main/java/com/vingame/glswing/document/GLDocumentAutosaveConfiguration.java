package com.vingame.glswing.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/**
 * Immutable autosave scheduling preferences, loadable from a properties file.
 * A zero duration disables that individual schedule; an enabled configuration
 * must retain at least one non-zero schedule.
 *
 * @param enabled whether recovery snapshots are scheduled
 * @param idleDelay quiet period before an idle snapshot, or zero to disable it
 * @param periodicInterval maximum interval between snapshots, or zero to disable it
 */
public record GLDocumentAutosaveConfiguration(boolean enabled,
		Duration idleDelay, Duration periodicInterval) {
	public static final String ENABLED_KEY = "document.autosave.enabled";
	public static final String IDLE_MILLIS_KEY = "document.autosave.idleMillis";
	public static final String PERIODIC_MILLIS_KEY = "document.autosave.periodicMillis";
	public static final GLDocumentAutosaveConfiguration DEFAULT =
			new GLDocumentAutosaveConfiguration(true, Duration.ofSeconds(10),
					Duration.ofMinutes(2));

	public GLDocumentAutosaveConfiguration {
		if (idleDelay == null || periodicInterval == null || idleDelay.isNegative()
				|| periodicInterval.isNegative())
			throw new IllegalArgumentException("Autosave intervals must be non-negative");
		if (enabled && idleDelay.isZero() && periodicInterval.isZero())
			throw new IllegalArgumentException("Enabled autosave requires at least one interval");
	}
	/** Loads known keys while retaining defaults for omitted values. */
	public static GLDocumentAutosaveConfiguration load(Path path) throws IOException {
		if (path == null) throw new IllegalArgumentException("Preference path required");
		Properties values = new Properties();
		try (InputStream input = Files.newInputStream(path)) { values.load(input); }
		return new GLDocumentAutosaveConfiguration(
				booleanValue(values, ENABLED_KEY, DEFAULT.enabled()),
				Duration.ofMillis(longValue(values, IDLE_MILLIS_KEY,
						DEFAULT.idleDelay().toMillis())),
				Duration.ofMillis(longValue(values, PERIODIC_MILLIS_KEY,
						DEFAULT.periodicInterval().toMillis())));
	}
	/** Writes a portable preferences file, creating its parent directory. */
	public void save(Path path) throws IOException {
		if (path == null) throw new IllegalArgumentException("Preference path required");
		Path absolute = path.toAbsolutePath().normalize();
		Path parent = absolute.getParent();
		if (parent != null) Files.createDirectories(parent);
		Properties values = new Properties();
		values.setProperty(ENABLED_KEY, Boolean.toString(enabled));
		values.setProperty(IDLE_MILLIS_KEY, Long.toString(idleDelay.toMillis()));
		values.setProperty(PERIODIC_MILLIS_KEY,
				Long.toString(periodicInterval.toMillis()));
		try (OutputStream output = Files.newOutputStream(absolute)) {
			values.store(output, "GLSwing document autosave");
		}
	}
	private static boolean booleanValue(Properties values, String key, boolean fallback) {
		String value = values.getProperty(key);
		if (value == null) return fallback;
		String normalized = value.strip();
		if ("true".equalsIgnoreCase(normalized)) return true;
		if ("false".equalsIgnoreCase(normalized)) return false;
		throw new IllegalArgumentException(
				"Invalid autosave preference " + key + ": " + value);
	}
	private static long longValue(Properties values, String key, long fallback) {
		String value = values.getProperty(key);
		if (value == null) return fallback;
		try {
			long parsed = Long.parseLong(value.strip());
			if (parsed < 0) throw new NumberFormatException();
			return parsed;
		} catch (NumberFormatException invalid) {
			throw new IllegalArgumentException(
					"Invalid autosave preference " + key + ": " + value, invalid);
		}
	}
}
