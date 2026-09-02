package com.vingame.glswing.application;

import java.util.Map;
import java.util.function.Function;

import javax.swing.JFrame;

import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLTheme;

/** Resources and host services available while an application surface is built. */
public final class GLApplicationContext {
	private final JFrame window;
	private final GLParent parent;
	private final GLTheme theme;
	private final GLInputMap inputMap;
	private final GLLocalizer localizer;
	private volatile Map<String, Object> results = Map.of();
	private volatile Function<String, Object> resultResolver = results::get;

	GLApplicationContext(JFrame window, GLParent parent, GLTheme theme,
			GLInputMap inputMap, GLLocalizer localizer) {
		this.window = window;
		this.parent = parent;
		this.theme = theme;
		this.inputMap = inputMap;
		this.localizer = localizer;
	}

	void resultResolver(Function<String, Object> resolver) {
		resultResolver = resolver;
	}
	void resultsAvailable(Map<String, Object> results) {
		this.results = Map.copyOf(results);
		resultResolver = this.results::get;
	}

	public JFrame window() { return window; }
	public GLParent parent() { return parent; }
	public GLTheme theme() { return theme; }
	public GLInputMap inputMap() { return inputMap; }
	public GLLocalizer localizer() { return localizer; }

	/** Returns a value produced by a named {@code startup.provide(...)} task. */
	public <T> T require(String key, Class<T> type) {
		if (key == null || key.isBlank() || type == null)
			throw new IllegalArgumentException("Startup result key and type are required");
		Object value = resultResolver.apply(key);
		if (value == null)
			throw new IllegalStateException("Startup result is not available: " + key);
		return type.cast(value);
	}
}
