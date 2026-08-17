package com.vingame.glswing.test;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLThemeLoader;
import com.vingame.glswing.ui.GLThemeLoadException;
import com.vingame.glswing.ui.GLThemeValues;
import com.vingame.glswing.ui.GLThemes;
import com.vingame.glswing.ui.GLUiKeys;

/** CPU checks for locale fallback, formatting and external theme resolution. */
public final class GLLocalizationThemeSelfTest {
	private GLLocalizationThemeSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		testLocalization();
		testColors();
		testBundledThemes();
		testFileThemeAndReferences();
		testMissingFilePolicy();
		System.out.println("GL localization/theme self-test passed.");
	}

	private static void testLocalization() {
		check("Zurücksetzen".equals(GLLocalizer.GERMAN.text(GLUiKeys.SLIDER_RESET)),
				"German bundle did not override the root text");
		check("Reset".equals(GLLocalizer.forLocale(Locale.FRENCH)
				.text(GLUiKeys.SLIDER_RESET)),
				"Unsupported locale did not fall back to the English root bundle");
		check("3 Elemente ausgewählt".equals(GLLocalizer.GERMAN
				.text("glswing.demo.itemsSelected", 3)),
				"Localized MessageFormat parameter was not substituted");
		check("Neu".equals(GLLocalizer.ENGLISH.with("custom.new", "Neu")
				.text("custom.new")), "Direct localization override was ignored");
		check("missing.key".equals(GLLocalizer.ENGLISH.text("missing.key")),
				"Missing localization key did not fall back to itself");
		check("Sprache".equals(GLLocalizer.GERMAN.withBundle(
				"com.vingame.glswing.demo.languages.Showcase")
				.text("showcase.menu.language")),
				"Application bundle did not override/extend library texts");
	}

	private static void testColors() {
		check(GLThemeValues.parseColor("#FF804080").equals(
				new Color(255, 128, 64, 128)), "RRGGBBAA parsing failed");
		check(GLThemeValues.parseColor("rgba(1f, 0.5f, 0.5f, 0f)").equals(
				new Color(1f, 0.5f, 0.5f, 0f)), "Normalized rgba parsing failed");
		check(GLThemeValues.parseColor("rgba(100%, 50%, 0%, 25%)").equals(
				new Color(1f, 0.5f, 0f, 0.25f)), "Percentage rgba parsing failed");
	}

	private static void testBundledThemes() {
		GLTheme defaults = GLThemes.DEFAULT;
		GLTheme dark = GLThemes.DARK;
		GLTheme light = GLThemes.LIGHT;
		check("Default".equals(defaults.values().get("theme.name", ""))
				&& "Dark".equals(dark.values().get("theme.name", ""))
				&& "Light".equals(light.values().get("theme.name", "")),
				"Bundled themes were not loaded as resources");
		check(dark.buttonStyle().pressedBackground()
				.equals(dark.values().getColor("palette.accent", Color.BLACK)),
				"Semantic pressed token did not reach the button style");
		check(!dark.buttonStyle().background()
				.equals(light.buttonStyle().background()),
				"Light theme did not override the inherited dark palette");
	}

	private static void testMissingFilePolicy() throws Exception {
		Path missing = Files.createTempFile("glswing-missing-theme-", ".gltheme");
		Files.delete(missing);
		try {
			GLThemeLoader.load(missing);
			throw new AssertionError("Strict loading accepted a missing theme file");
		} catch (GLThemeLoadException expected) {
			check(expected.getMessage().contains("Cannot read theme"),
					"Missing theme error did not contain useful context");
		}
		AtomicInteger reported = new AtomicInteger();
		GLTheme fallback = GLThemeLoader.loadOrDefault(missing, GLThemes.LIGHT,
				error -> reported.incrementAndGet());
		check(fallback == GLThemes.LIGHT && reported.get() == 1,
				"Explicit theme fallback or error reporting failed");
	}

	private static void testFileThemeAndReferences() throws Exception {
		Path file = Files.createTempFile("glswing-theme-", ".gltheme");
		Path cycle = Files.createTempFile("glswing-theme-cycle-", ".gltheme");
		try {
			Files.writeString(file, """
					theme.extends=light
					custom.brand=rgba(1f, 0.5f, 0.5f, 0.75f)
					palette.accent=${custom.brand}
					button.cornerRadius=11
					slider.pressed=${palette.accent}
					""", StandardCharsets.UTF_8);
			GLTheme loaded = GLThemeLoader.load(file);
			Color brand = new Color(1f, 0.5f, 0.5f, 0.75f);
			check(brand.equals(loaded.values().getColor("custom.brand", Color.BLACK)),
					"Custom theme token was not retained");
			check(brand.equals(loaded.buttonStyle().pressedBackground()),
					"Accent did not reach button pressed color: "
							+ loaded.buttonStyle().pressedBackground());
			check(brand.equals(loaded.sliderStyle().pressedBackground()),
					"Accent did not reach slider pressed color: "
							+ loaded.sliderStyle().pressedBackground());
			check(loaded.buttonStyle().cornerRadius() == 11,
					"Component-specific corner radius was not applied");

			Files.writeString(cycle, """
					token.a=${token.b}
					token.b=${token.a}
					""", StandardCharsets.UTF_8);
			try {
				GLThemeLoader.load(cycle);
				throw new AssertionError("Cyclic theme reference was accepted");
			} catch (IllegalArgumentException expected) {
				check(expected.getMessage().contains("Cyclic theme reference"),
						"Cycle error did not identify its cause");
			}
		} finally {
			Files.deleteIfExists(file);
			Files.deleteIfExists(cycle);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
