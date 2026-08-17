package com.vingame.glswing.ui;

import java.text.MessageFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Immutable ResourceBundle-backed text resolver with deterministic root fallback,
 * application bundles, direct overrides and MessageFormat parameters.
 */
public final class GLLocalizer {
	public static final String GLSWING_BUNDLE =
			"com.vingame.glswing.languages.Messages";
	private static final ResourceBundle.Control NO_LOCALE_FALLBACK =
			ResourceBundle.Control.getNoFallbackControl(
					ResourceBundle.Control.FORMAT_PROPERTIES);

	public static final GLLocalizer ENGLISH = forLocale(Locale.ENGLISH);
	public static final GLLocalizer GERMAN = forLocale(Locale.GERMAN);
	public static final GLLocalizer DEFAULT = ENGLISH;

	private final Locale locale;
	private final List<ResourceBundle> bundles;
	private final Map<String, String> overrides;

	private GLLocalizer(Locale locale, List<ResourceBundle> bundles,
			Map<String, String> overrides) {
		this.locale = locale;
		this.bundles = List.copyOf(bundles);
		this.overrides = Map.copyOf(overrides);
	}

	public static GLLocalizer forLocale(Locale locale) {
		if (locale == null)
			throw new IllegalArgumentException("Locale must not be null");
		List<ResourceBundle> library = loadBundles(GLSWING_BUNDLE, locale,
				GLLocalizer.class.getClassLoader());
		return new GLLocalizer(locale, library, Map.of());
	}

	public Locale getLocale() {
		return locale;
	}

	/** Adds an application bundle at a higher priority than GLSwing's bundle. */
	public GLLocalizer withBundle(String baseName) {
		if (baseName == null || baseName.isBlank())
			throw new IllegalArgumentException("Bundle base name must not be blank");
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader == null)
			loader = GLLocalizer.class.getClassLoader();
		List<ResourceBundle> additional = loadBundles(baseName, locale, loader);
		List<ResourceBundle> changed = new ArrayList<>(
				bundles.size() + additional.size());
		changed.addAll(additional);
		changed.addAll(bundles);
		return new GLLocalizer(locale, changed, overrides);
	}

	/**
	 * Adds an already resolved application bundle. This variant is convenient for
	 * applications in a separate named module, which can load their own resources.
	 */
	public GLLocalizer withBundle(ResourceBundle bundle) {
		if (bundle == null)
			throw new IllegalArgumentException("Resource bundle must not be null");
		List<ResourceBundle> changed = new ArrayList<>(bundles.size() + 1);
		changed.add(bundle);
		changed.addAll(bundles);
		return new GLLocalizer(locale, changed, overrides);
	}

	public GLLocalizer with(String key, String value) {
		validateKey(key);
		if (value == null)
			throw new IllegalArgumentException("Localized text must not be null");
		Map<String, String> changed = new LinkedHashMap<>(overrides);
		changed.put(key, value);
		return new GLLocalizer(locale, bundles, changed);
	}

	public GLLocalizer withOverrides(Map<String, String> values) {
		if (values == null)
			throw new IllegalArgumentException("Localized overrides must not be null");
		GLLocalizer result = this;
		for (Map.Entry<String, String> entry : values.entrySet())
			result = result.with(entry.getKey(), entry.getValue());
		return result;
	}

	/** Resolves a key and returns the key itself as final missing-value fallback. */
	public String text(String key, Object... parameters) {
		validateKey(key);
		String pattern = overrides.get(key);
		if (pattern == null)
			for (ResourceBundle bundle : bundles)
				if (bundle.containsKey(key)) {
					pattern = bundle.getString(key);
					break;
				}
		if (pattern == null)
			pattern = key;
		if (parameters == null || parameters.length == 0)
			return pattern;
		return new MessageFormat(pattern, locale).format(parameters);
	}

	private static List<ResourceBundle> loadBundles(String baseName, Locale locale,
			ClassLoader loader) {
		List<ResourceBundle> result = new ArrayList<>();
		for (Locale candidate : NO_LOCALE_FALLBACK.getCandidateLocales(
				baseName, locale)) {
			String bundleName = NO_LOCALE_FALLBACK.toBundleName(baseName, candidate);
			String resourceName = NO_LOCALE_FALLBACK.toResourceName(
					bundleName, "properties");
			try (InputStream stream = openResource(loader, resourceName)) {
				if (stream != null)
					result.add(new PropertyResourceBundle(new InputStreamReader(
							stream, StandardCharsets.UTF_8)));
			} catch (IOException error) {
				throw new IllegalArgumentException(
						"Cannot read resource bundle " + resourceName, error);
			}
		}
		if (result.isEmpty())
			throw new IllegalArgumentException("Resource bundle not found: "
					+ baseName + " for " + locale,
					new MissingResourceException("Bundle not found", baseName, ""));
		return result;
	}

	private static InputStream openResource(ClassLoader loader,
			String resourceName) throws IOException {
		InputStream stream = GLLocalizer.class.getModule()
				.getResourceAsStream(resourceName);
		return stream != null ? stream : loader.getResourceAsStream(resourceName);
	}

	private static void validateKey(String key) {
		if (key == null || key.isBlank())
			throw new IllegalArgumentException("Localization key must not be blank");
	}
}
