package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vingame.glswing.layout.GLInsets;

/** Loads UTF-8 .gltheme resources or files with inheritance and ${key} references. */
public final class GLThemeLoader {
	private static final Pattern REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
	private static final String TYPE = "theme.type";
	private static final String COLORS = "theme.colors";
	private static final String METRICS = "theme.metrics";
	private enum ThemeType { LEGACY, COLORS, METRICS, BUNDLE }

	private GLThemeLoader() {
	}

	public static GLTheme loadResource(String resource) {
		String normalized = normalizeResource(resource);
		try {
			return loadResource(normalized, new ArrayDeque<>());
		} catch (GLThemeLoadException error) {
			throw error;
		} catch (RuntimeException error) {
			throw failure("Cannot load theme resource " + normalized, error);
		}
	}

	public static GLTheme load(Path path) {
		if (path == null)
			throw new IllegalArgumentException("Theme path must not be null");
		Path normalized = path.toAbsolutePath().normalize();
		try {
			return loadPath(normalized, new ArrayDeque<>());
		} catch (GLThemeLoadException error) {
			throw error;
		} catch (RuntimeException error) {
			throw failure("Cannot load theme file " + normalized, error);
		}
	}

	/**
	 * Composes independently selected color and metrics fragments over a base
	 * theme. Either fragment may be {@code null}; supplied files must declare the
	 * matching {@code theme.type}.
	 */
	public static GLTheme loadBundle(GLTheme base, Path colors, Path metrics) {
		if (base == null)
			throw new IllegalArgumentException("Bundle base theme must not be null");
		Deque<String> stack = new ArrayDeque<>();
		Map<String, String> overrides = new LinkedHashMap<>();
		if (colors != null)
			overrides.putAll(loadPathFragment(colors.toAbsolutePath().normalize(),
					ThemeType.COLORS, stack));
		if (metrics != null)
			overrides.putAll(loadPathFragment(metrics.toAbsolutePath().normalize(),
					ThemeType.METRICS, stack));
		return build(base, overrides);
	}

	/** Loads a file or returns the supplied fallback after reporting the error. */
	public static GLTheme loadOrDefault(Path path, GLTheme fallback,
			Consumer<GLThemeLoadException> errorHandler) {
		if (fallback == null)
			throw new IllegalArgumentException("Fallback theme must not be null");
		try {
			return load(path);
		} catch (GLThemeLoadException error) {
			if (errorHandler != null)
				errorHandler.accept(error);
			return fallback;
		}
	}

	/** Loads a file or falls back to {@link GLTheme#DEFAULT}. */
	public static GLTheme loadOrDefault(Path path,
			Consumer<GLThemeLoadException> errorHandler) {
		return loadOrDefault(path, GLTheme.DEFAULT, errorHandler);
	}

	/** Loads a file or silently falls back to {@link GLTheme#DEFAULT}. */
	public static GLTheme loadOrDefault(Path path) {
		return loadOrDefault(path, GLTheme.DEFAULT, null);
	}

	static GLThemeValues loadRootValues(String resource) {
		String normalized = normalizeResource(resource);
		String identity = "resource:" + normalized;
		try (InputStream stream = GLThemeLoader.class
				.getResourceAsStream(normalized)) {
			if (stream == null)
				throw new GLThemeLoadException(
						"Required theme resource not found: " + normalized);
			Map<String, String> raw = read(new InputStreamReader(stream,
					StandardCharsets.UTF_8), identity);
			registerResourceFonts(raw, resource, identity);
			String inherited = raw.remove("theme.extends");
			if (inherited != null && !inherited.isBlank()
					&& !"none".equalsIgnoreCase(inherited.trim()))
				throw new GLThemeLoadException("Root theme " + normalized
						+ " must not extend another theme");
			Map<String, String> resolved = resolveReferences(raw);
			return new GLThemeValues(resolved, raw);
		} catch (GLThemeLoadException error) {
			throw error;
		} catch (IOException error) {
			throw new GLThemeLoadException(
					"Cannot read required theme resource " + normalized, error);
		} catch (RuntimeException error) {
			throw failure("Invalid required theme resource " + normalized, error);
		}
	}

	private static GLTheme loadResource(String resource, Deque<String> stack) {
		String identity = "resource:" + resource;
		enter(identity, stack);
		try (InputStream stream = GLThemeLoader.class.getResourceAsStream(resource)) {
			if (stream == null)
				throw new GLThemeLoadException("Theme resource not found: " + resource);
			Map<String, String> raw = read(new InputStreamReader(stream,
					StandardCharsets.UTF_8), identity);
			registerResourceFonts(raw, resource, identity);
			String inherited = raw.remove("theme.extends");
			ThemeType type = removeType(raw, identity);
			String colors = raw.remove(COLORS);
			String metrics = raw.remove(METRICS);
			validateRoot(type, colors, metrics, identity);
			GLTheme base = resolveResourceBase(inherited, resource, stack);
			Map<String, String> overrides = new LinkedHashMap<>();
			if (colors != null)
				overrides.putAll(loadResourceFragment(resolveResource(resource, colors),
						ThemeType.COLORS, stack));
			if (metrics != null)
				overrides.putAll(loadResourceFragment(resolveResource(resource, metrics),
						ThemeType.METRICS, stack));
			validateFragmentKeys(type, raw, identity);
			overrides.putAll(raw);
			return build(base, overrides);
		} catch (IOException error) {
			throw new GLThemeLoadException("Cannot read theme " + identity, error);
		} finally {
			stack.removeLast();
		}
	}

	private static GLTheme loadPath(Path path, Deque<String> stack) {
		String identity = "file:" + path;
		enter(identity, stack);
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Map<String, String> raw = read(reader, identity);
			registerPathFonts(raw, path, identity);
			String inherited = raw.remove("theme.extends");
			ThemeType type = removeType(raw, identity);
			String colors = raw.remove(COLORS);
			String metrics = raw.remove(METRICS);
			validateRoot(type, colors, metrics, identity);
			GLTheme base = resolvePathBase(inherited, path, stack);
			Map<String, String> overrides = new LinkedHashMap<>();
			if (colors != null)
				overrides.putAll(loadPathFragment(resolvePath(path, colors),
						ThemeType.COLORS, stack));
			if (metrics != null)
				overrides.putAll(loadPathFragment(resolvePath(path, metrics),
						ThemeType.METRICS, stack));
			validateFragmentKeys(type, raw, identity);
			overrides.putAll(raw);
			return build(base, overrides);
		} catch (IOException error) {
			throw new GLThemeLoadException("Cannot read theme " + path, error);
		} finally {
			stack.removeLast();
		}
	}

	private static Map<String, String> loadPathFragment(Path path,
			ThemeType expected, Deque<String> stack) {
		String identity = "file:" + path;
		enter(identity, stack);
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Map<String, String> raw = read(reader, identity);
			registerPathFonts(raw, path, identity);
			validateFragmentHeader(raw, expected, identity);
			validateFragmentKeys(expected, raw, identity);
			return raw;
		} catch (IOException error) {
			throw new GLThemeLoadException("Cannot read theme fragment " + path, error);
		} finally {
			stack.removeLast();
		}
	}

	private static Map<String, String> loadResourceFragment(String resource,
			ThemeType expected, Deque<String> stack) {
		String identity = "resource:" + resource;
		enter(identity, stack);
		try (InputStream stream = GLThemeLoader.class.getResourceAsStream(resource)) {
			if (stream == null)
				throw new GLThemeLoadException(
						"Theme fragment resource not found: " + resource);
			Map<String, String> raw = read(new InputStreamReader(stream,
					StandardCharsets.UTF_8), identity);
			registerResourceFonts(raw, resource, identity);
			validateFragmentHeader(raw, expected, identity);
			validateFragmentKeys(expected, raw, identity);
			return raw;
		} catch (IOException error) {
			throw new GLThemeLoadException("Cannot read theme fragment " + identity,
					error);
		} finally {
			stack.removeLast();
		}
	}

	private static void registerPathFonts(Map<String, String> raw, Path owner,
			String identity) {
		for (Map.Entry<String, String> entry : fontSources(raw).entrySet()) {
			String reference = entry.getValue();
			Path referenced = Path.of(reference);
			Path fontPath = (referenced.isAbsolute() ? referenced
					: owner.resolveSibling(referenced)).toAbsolutePath().normalize();
			try (InputStream stream = Files.newInputStream(fontPath)) {
				registerFont(raw, entry.getKey(), reference, stream, identity);
			} catch (IOException error) {
				throw new GLThemeLoadException(identity + ": cannot read font "
						+ fontPath, error);
			}
		}
	}

	private static void registerResourceFonts(Map<String, String> raw,
			String owner, String identity) {
		for (Map.Entry<String, String> entry : fontSources(raw).entrySet()) {
			String reference = entry.getValue();
			String resource = resolveResource(owner, reference);
			try (InputStream stream = GLThemeLoader.class
					.getResourceAsStream(resource)) {
				if (stream == null)
					throw new GLThemeLoadException(identity
							+ ": font resource not found: " + resource);
				registerFont(raw, entry.getKey(), reference, stream, identity);
			} catch (IOException error) {
				throw new GLThemeLoadException(identity + ": cannot read font resource "
						+ resource, error);
			}
		}
	}

	private static Map<String, String> fontSources(Map<String, String> raw) {
		Map<String, String> sources = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : raw.entrySet())
			if (isFontSourceKey(entry.getKey()))
				sources.put(entry.getKey(), entry.getValue());
		return sources;
	}

	private static boolean isFontSourceKey(String key) {
		String lower = key.toLowerCase();
		int separator = lower.lastIndexOf('.');
		if (separator < 0 || !lower.substring(0, separator).endsWith("font"))
			return false;
		return switch (lower.substring(separator + 1)) {
		case "source", "regular", "bold", "italic", "bolditalic" -> true;
		default -> false;
		};
	}

	private static void registerFont(Map<String, String> raw, String sourceKey,
			String reference, InputStream stream, String identity) {
		try {
			Font font = Font.createFont(fontType(reference), stream);
			GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
			String familyKey = sourceKey.substring(0,
					sourceKey.lastIndexOf('.') + 1) + "family";
			raw.putIfAbsent(familyKey, font.getFamily());
		} catch (FontFormatException | IOException error) {
			throw new GLThemeLoadException(identity + ": invalid font " + reference,
					error);
		}
	}

	private static int fontType(String reference) {
		String lower = reference.toLowerCase();
		if (lower.endsWith(".ttf") || lower.endsWith(".otf"))
			return Font.TRUETYPE_FONT;
		if (lower.endsWith(".pfa") || lower.endsWith(".pfb"))
			return Font.TYPE1_FONT;
		throw new GLThemeLoadException("Unsupported font format: " + reference);
	}

	private static void validateFragmentHeader(Map<String, String> raw,
			ThemeType expected, String identity) {
		String inherited = raw.remove("theme.extends");
		String colors = raw.remove(COLORS);
		String metrics = raw.remove(METRICS);
		ThemeType actual = removeType(raw, identity);
		if (actual != expected)
			throw new GLThemeLoadException(identity + " must declare theme.type="
					+ expected.name().toLowerCase());
		if (inherited != null || colors != null || metrics != null)
			throw new GLThemeLoadException(identity
					+ " is a fragment and must not inherit or import themes");
	}

	private static ThemeType removeType(Map<String, String> raw, String identity) {
		String value = raw.remove(TYPE);
		if (value == null || value.isBlank())
			return ThemeType.LEGACY;
		try {
			return ThemeType.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException error) {
			throw new GLThemeLoadException(identity + ": unknown theme.type " + value,
					error);
		}
	}

	private static void validateRoot(ThemeType type, String colors,
			String metrics, String identity) {
		if ((colors != null || metrics != null) && type != ThemeType.BUNDLE)
			throw new GLThemeLoadException(identity
					+ ": theme.colors and theme.metrics require theme.type=bundle");
		if (type == ThemeType.COLORS || type == ThemeType.METRICS)
			if (colors != null || metrics != null)
				throw new GLThemeLoadException(identity
						+ ": theme fragments cannot import other fragments");
	}

	private static void validateFragmentKeys(ThemeType type,
			Map<String, String> raw, String identity) {
		if (type != ThemeType.COLORS && type != ThemeType.METRICS)
			return;
		for (String key : raw.keySet()) {
			if ("theme.name".equals(key)) continue;
			boolean color = GLThemeKeyClassifier.isColorOrFontKey(key);
			if (type == ThemeType.COLORS && !color)
				throw new GLThemeLoadException(identity + ": " + key
						+ " is not permitted in a color theme");
			if (type == ThemeType.METRICS && color)
				throw new GLThemeLoadException(identity + ": " + key
						+ " is not permitted in a metrics theme");
		}
	}

	private static Path resolvePath(Path owner, String reference) {
		if (reference == null || reference.isBlank())
			throw new GLThemeLoadException("Blank theme fragment reference in " + owner);
		return owner.resolveSibling(reference.trim()).toAbsolutePath().normalize();
	}

	private static String resolveResource(String owner, String reference) {
		if (reference == null || reference.isBlank())
			throw new GLThemeLoadException(
					"Blank theme fragment reference in " + owner);
		String name = reference.trim();
		return normalizeResource(name.startsWith("/") ? name
				: owner.substring(0, owner.lastIndexOf('/') + 1) + name);
	}

	private static GLTheme resolveResourceBase(String inherited,
			String resource, Deque<String> stack) {
		String name = inherited == null ? "default" : inherited.trim();
		return switch (name.toLowerCase()) {
		case "", "default" -> GLTheme.DEFAULT;
		case "dark" -> loadResource(
				"/com/vingame/glswing/themes/dark.gltheme", stack);
		case "light" -> loadResource(
				"/com/vingame/glswing/themes/light.gltheme", stack);
		default -> {
			String resolved = name.startsWith("/") ? name
					: resource.substring(0, resource.lastIndexOf('/') + 1) + name;
			yield loadResource(normalizeResource(resolved), stack);
		}
		};
	}

	private static GLTheme resolvePathBase(String inherited, Path path,
			Deque<String> stack) {
		String name = inherited == null ? "default" : inherited.trim();
		return switch (name.toLowerCase()) {
		case "", "default" -> siblingPreset(path, "default.gltheme",
				GLTheme.DEFAULT, stack);
		case "dark" -> siblingPreset(path, "dark.gltheme",
				GLThemes.DARK, stack);
		case "light" -> siblingPreset(path, "light.gltheme",
				GLThemes.LIGHT, stack);
		default -> loadPath(path.resolveSibling(name).normalize(), stack);
		};
	}

	private static GLTheme siblingPreset(Path source, String fileName,
			GLTheme bundledFallback, Deque<String> stack) {
		Path sibling = source.resolveSibling(fileName).toAbsolutePath().normalize();
		if (!sibling.equals(source) && Files.isRegularFile(sibling))
			return loadPath(sibling, stack);
		return bundledFallback;
	}

	static GLTheme build(GLTheme base, Map<String, String> raw) {
		Map<String, String> merged = new LinkedHashMap<>(base.values().sourceMap());
		merged.putAll(raw);
		Map<String, String> resolved = resolveReferences(merged);
		GLThemeValues values = new GLThemeValues(resolved, merged);

		GLLabelStyle label = label(base.labelStyle(), values);
		GLButtonStyle button = button(base.buttonStyle(), values);
		GLCheckBoxStyle checkBox = checkBox(base.checkBoxStyle(), values);
		GLSliderStyle slider = slider(base.sliderStyle(), values);
		GLTextEditorStyle editor = editor(base.textEditorStyle(), values);
		GLMenuStyle menu = menu(base.menuStyle(), values);
		GLMenuBarStyle menuBar = menuBar(base.menuBarStyle(), values);
		GLComboBoxStyle combo = combo(base.comboBoxStyle(), values);
		return new GLTheme(label, button, checkBox, slider, editor, menu,
				menuBar, combo, values);
	}

	private static GLLabelStyle label(GLLabelStyle base, GLThemeValues values) {
		return new GLLabelStyle(font(base.font(), "label", values),
				color(values, base.textColor(), "label.text", "palette.text"),
				insets(values, base.padding(), "label.padding", "metrics.padding"));
	}

	private static GLButtonStyle button(GLButtonStyle b, GLThemeValues v) {
		return new GLButtonStyle(font(b.font(), "button", v),
				color(v, b.background(), "button.background", "state.normal"),
				color(v, b.hoverBackground(), "button.hover", "state.hover"),
				color(v, b.pressedLightColor(), "button.pressedLight"),
				color(v, b.pressedDarkColor(), "button.pressedDark"),
				color(v, b.disabledBackground(), "button.disabled", "state.disabled"),
				color(v, b.textColor(), "button.text", "palette.text"),
				color(v, b.disabledTextColor(), "button.textDisabled", "palette.textDisabled"),
				color(v, b.borderColor(), "button.border", "palette.border"),
				color(v, b.focusColor(), "button.focus", "palette.accent"),
				number(v, b.cornerRadius(), "button.cornerRadius", "metrics.cornerRadius"),
				number(v, b.borderWidth(), "button.borderWidth", "metrics.borderWidth"),
				number(v, b.height(), "button.height", "metrics.controlHeight"),
				insets(v, b.padding(), "button.padding", "metrics.padding"));
	}

	private static GLCheckBoxStyle checkBox(GLCheckBoxStyle b, GLThemeValues v) {
		return new GLCheckBoxStyle(font(b.font(), "checkBox", v),
				color(v, b.background(), "checkBox.background", "state.normal"),
				color(v, b.hoverBackground(), "checkBox.hover", "state.hover"),
				color(v, b.pressedBackground(), "checkBox.pressed", "state.pressed"),
				color(v, b.disabledBackground(), "checkBox.disabled", "state.disabled"),
				color(v, b.checkColor(), "checkBox.check", "palette.text"),
				color(v, b.disabledCheckColor(), "checkBox.checkDisabled", "palette.textDisabled"),
				color(v, b.textColor(), "checkBox.text", "palette.text"),
				color(v, b.disabledTextColor(), "checkBox.textDisabled", "palette.textDisabled"),
				color(v, b.borderColor(), "checkBox.border", "palette.border"),
				color(v, b.focusBorderColor(), "checkBox.focus", "palette.accent"),
				number(v, b.boxSize(), "checkBox.boxSize"),
				number(v, b.gap(), "checkBox.gap"),
				number(v, b.cornerRadius(), "checkBox.cornerRadius", "metrics.cornerRadius"),
				number(v, b.borderWidth(), "checkBox.borderWidth", "metrics.borderWidth"),
				number(v, b.checkStrokeWidth(), "checkBox.checkStrokeWidth"),
				insets(v, b.padding(), "checkBox.padding", "metrics.padding"));
	}

	private static GLSliderStyle slider(GLSliderStyle b, GLThemeValues v) {
		return new GLSliderStyle(font(b.font(), "slider", v),
				color(v, b.background(), "slider.background", "state.normal"),
				color(v, b.hoverBackground(), "slider.hover", "state.hover"),
				color(v, b.pressedBackground(), "slider.pressed", "state.pressed"),
				color(v, b.disabledBackground(), "slider.disabled", "state.disabled"),
				color(v, b.textColor(), "slider.text", "palette.text"),
				color(v, b.disabledTextColor(), "slider.textDisabled", "palette.textDisabled"),
				color(v, b.borderColor(), "slider.border", "palette.border"),
				color(v, b.progressLightColor(), "slider.progressLight"),
				color(v, b.progressDarkColor(), "slider.progressDark"),
				color(v, b.arrowBackground(), "slider.arrowBackground", "state.normal"),
				color(v, b.arrowHoverBackground(), "slider.arrowHover", "state.hover"),
				color(v, b.arrowPressedBackground(), "slider.arrowPressed", "state.pressed"),
				color(v, b.arrowDisabledBackground(), "slider.arrowDisabled", "state.disabled"),
				color(v, b.arrowColor(), "slider.arrow", "palette.text"),
				color(v, b.disabledArrowColor(), "slider.arrowTextDisabled", "palette.textDisabled"),
				number(v, b.cornerRadius(), "slider.cornerRadius", "metrics.cornerRadius"),
				number(v, b.borderWidth(), "slider.borderWidth", "metrics.borderWidth"),
				number(v, b.height(), "slider.height", "metrics.controlHeight"),
				number(v, b.preferredWidth(), "slider.preferredWidth"),
				number(v, b.arrowButtonWidth(), "slider.arrowButtonWidth"),
				number(v, b.arrowInset(), "slider.arrowInset"),
				number(v, b.arrowStrokeWidth(), "slider.arrowStrokeWidth"),
				insets(v, b.padding(), "slider.padding", "metrics.padding"));
	}

	private static GLTextEditorStyle editor(GLTextEditorStyle b, GLThemeValues v) {
		return new GLTextEditorStyle(font(b.font(), "textEditor", v),
				color(v, b.backgroundColor(), "textEditor.background", "state.normal"),
				color(v, b.textColor(), "textEditor.text", "palette.text"),
				color(v, b.disabledTextColor(), "textEditor.textDisabled", "palette.textDisabled"),
				color(v, b.selectionColor(), "textEditor.selection", "palette.selection", "palette.accent"),
				color(v, b.caretColor(), "textEditor.caret", "palette.text"),
				color(v, b.borderColor(), "textEditor.border", "palette.border"),
				color(v, b.focusedBorderColor(), "textEditor.focus", "palette.accent"),
				color(v, b.invalidBorderColor(), "textEditor.invalid", "palette.invalid"),
				number(v, b.cornerRadius(), "textEditor.cornerRadius", "metrics.cornerRadius"),
				number(v, b.borderWidth(), "textEditor.borderWidth", "metrics.borderWidth"),
				number(v, b.height(), "textEditor.height", "metrics.controlHeight"),
				number(v, b.preferredWidth(), "textEditor.preferredWidth"),
				number(v, b.overflowFadeWidth(), "textEditor.overflowFadeWidth"),
				insets(v, b.padding(), "textEditor.padding", "metrics.padding"));
	}

	private static GLMenuStyle menu(GLMenuStyle b, GLThemeValues v) {
		return new GLMenuStyle(font(b.font(), "menu", v),
				color(v, b.background(), "menu.background", "palette.surface"),
				color(v, b.hoverBackground(), "menu.hover", "state.hover"),
				color(v, b.pressedBackground(), "menu.pressed", "state.pressed"),
				color(v, b.textColor(), "menu.text", "palette.text"),
				color(v, b.disabledTextColor(), "menu.textDisabled", "palette.textDisabled"),
				color(v, b.acceleratorColor(), "menu.accelerator", "palette.textSecondary", "palette.textDisabled"),
				color(v, b.borderColor(), "menu.border", "palette.border"),
				color(v, b.separatorColor(), "menu.separator", "palette.border"),
				color(v, b.arrowColor(), "menu.arrow", "palette.text"),
				number(v, b.cornerRadius(), "menu.cornerRadius", "metrics.cornerRadius"),
				number(v, b.itemCornerRadius(), "menu.itemCornerRadius"),
				number(v, b.borderWidth(), "menu.borderWidth", "metrics.borderWidth"),
				number(v, b.itemHeight(), "menu.itemHeight", "metrics.controlHeight"),
				number(v, b.separatorHeight(), "menu.separatorHeight"),
				number(v, b.minimumWidth(), "menu.minimumWidth"),
				number(v, b.itemHorizontalPadding(), "menu.itemHorizontalPadding"),
				number(v, b.columnGap(), "menu.columnGap"),
				insets(v, b.padding(), "menu.padding", "metrics.padding"));
	}

	private static GLMenuBarStyle menuBar(GLMenuBarStyle b, GLThemeValues v) {
		return new GLMenuBarStyle(font(b.font(), "menuBar", v),
				color(v, b.background(), "menuBar.background", "palette.surface"),
				color(v, b.textColor(), "menuBar.text", "palette.text"),
				color(v, b.disabledTextColor(), "menuBar.textDisabled", "palette.textDisabled"),
				color(v, b.hoverBackground(), "menuBar.hover", "state.hover"),
				color(v, b.pressedBackground(), "menuBar.pressed", "state.pressed"),
				color(v, b.activeBackground(), "menuBar.active", "palette.accent"),
				color(v, b.borderColor(), "menuBar.border", "palette.border"),
				number(v, b.height(), "menuBar.height"),
				number(v, b.itemHorizontalPadding(), "menuBar.itemHorizontalPadding"),
				number(v, b.itemGap(), "menuBar.itemGap"),
				number(v, b.borderWidth(), "menuBar.borderWidth", "metrics.borderWidth"),
				number(v, b.itemCornerRadius(), "menuBar.cornerRadius", "metrics.cornerRadius"));
	}

	private static GLComboBoxStyle combo(GLComboBoxStyle b, GLThemeValues v) {
		return new GLComboBoxStyle(font(b.font(), "comboBox", v),
				color(v, b.background(), "comboBox.background", "state.normal"),
				color(v, b.hoverBackground(), "comboBox.hover", "state.hover"),
				color(v, b.pressedBackground(), "comboBox.pressed", "state.pressed"),
				color(v, b.pressedLightColor(), "comboBox.pressedLight",
						"button.pressedLight"),
				color(v, b.pressedDarkColor(), "comboBox.pressedDark",
						"button.pressedDark"),
				color(v, b.disabledBackground(), "comboBox.disabled", "state.disabled"),
				color(v, b.textColor(), "comboBox.text", "palette.text"),
				color(v, b.disabledTextColor(), "comboBox.textDisabled", "palette.textDisabled"),
				color(v, b.borderColor(), "comboBox.border", "palette.border"),
				color(v, b.arrowColor(), "comboBox.arrow", "palette.text"),
				number(v, b.cornerRadius(), "comboBox.cornerRadius", "metrics.cornerRadius"),
				number(v, b.borderWidth(), "comboBox.borderWidth", "metrics.borderWidth"),
				number(v, b.height(), "comboBox.height", "metrics.controlHeight"),
				number(v, b.minimumWidth(), "comboBox.minimumWidth"),
				number(v, b.arrowAreaWidth(), "comboBox.arrowAreaWidth"),
				insets(v, b.padding(), "comboBox.padding", "metrics.padding"));
	}

	private static GLFontSpec font(GLFontSpec base, String prefix,
			GLThemeValues values) {
		String family = text(values, base.family(), prefix + ".font.family",
				"font.family");
		String styleText = text(values, null, prefix + ".font.style", "font.style");
		int style = styleText == null ? base.style() : switch (styleText.toLowerCase()) {
		case "plain" -> Font.PLAIN;
		case "bold" -> Font.BOLD;
		case "italic" -> Font.ITALIC;
		case "bolditalic", "bold_italic", "bold-italic" -> Font.BOLD | Font.ITALIC;
		default -> throw new IllegalArgumentException("Unknown font style: " + styleText);
		};
		float size = number(values, base.size(), prefix + ".font.size", "font.size");
		return new GLFontSpec(family, style, size);
	}

	private static Color color(GLThemeValues values, Color fallback,
			String... keys) {
		String value = text(values, null, keys);
		return value == null ? fallback : GLThemeValues.parseColor(value);
	}

	private static float number(GLThemeValues values, float fallback,
			String... keys) {
		String value = text(values, null, keys);
		return value == null ? fallback : GLThemeValues.parseFloat(value, keys[0]);
	}

	private static GLInsets insets(GLThemeValues values, GLInsets fallback,
			String... keys) {
		for (String key : keys)
			if (values.contains(key))
				return values.getInsets(key, fallback);
		return fallback;
	}

	private static String text(GLThemeValues values, String fallback,
			String... keys) {
		for (String key : keys)
			if (values.contains(key))
				return values.get(key, fallback);
		return fallback;
	}

	private static Map<String, String> read(Reader reader, String source)
			throws IOException {
		Properties properties = new Properties() {
			private static final long serialVersionUID = 1L;

			@Override
			public synchronized Object put(Object key, Object value) {
				if (containsKey(key))
					throw new IllegalArgumentException(
							"Duplicate theme key " + key + " in " + source);
				return super.put(key, value);
			}
		};
		StringBuilder sourceText = new StringBuilder();
		char[] buffer = new char[4096];
		for (int read; (read = reader.read(buffer)) >= 0; )
			sourceText.append(buffer, 0, read);
		properties.load(new StringReader(GLPropertiesSyntax.removeInlineComments(
				sourceText.toString())));
		Map<String, String> values = new LinkedHashMap<>();
		for (String key : properties.stringPropertyNames()) {
			String value = properties.getProperty(key);
			if (key.isBlank() || value == null)
				throw new IllegalArgumentException("Invalid theme entry in " + source);
			values.put(key.trim(), value.trim());
		}
		return values;
	}

	private static Map<String, String> resolveReferences(Map<String, String> values) {
		Map<String, String> resolved = new LinkedHashMap<>();
		for (String key : values.keySet())
			resolve(key, values, resolved, new ArrayDeque<>());
		return resolved;
	}

	private static String resolve(String key, Map<String, String> values,
			Map<String, String> resolved, Deque<String> stack) {
		String existing = resolved.get(key);
		if (existing != null)
			return existing;
		if (stack.contains(key))
			throw new IllegalArgumentException("Cyclic theme reference: "
					+ String.join(" -> ", stack) + " -> " + key);
		String value = values.get(key);
		if (value == null)
			throw new IllegalArgumentException("Missing theme reference: " + key);
		stack.addLast(key);
		Matcher matcher = REFERENCE.matcher(value);
		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			String referencedKey = matcher.group(1).trim();
			String replacement = resolve(referencedKey, values, resolved, stack);
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		stack.removeLast();
		String finalValue = result.toString();
		resolved.put(key, finalValue);
		return finalValue;
	}

	private static void enter(String identity, Deque<String> stack) {
		if (stack.contains(identity))
			throw new IllegalArgumentException("Cyclic theme inheritance: "
					+ String.join(" -> ", stack) + " -> " + identity);
		stack.addLast(identity);
	}

	private static String normalizeResource(String resource) {
		if (resource == null || resource.isBlank())
			throw new IllegalArgumentException("Theme resource must not be blank");
		return resource.startsWith("/") ? resource : "/" + resource;
	}

	private static GLThemeLoadException failure(String context,
			RuntimeException error) {
		String detail = error.getMessage();
		return new GLThemeLoadException(context
				+ (detail == null || detail.isBlank() ? "" : ": " + detail), error);
	}
}
