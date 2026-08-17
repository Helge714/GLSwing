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
import com.vingame.glswing.node.GLNodeEditorStyle;
import com.vingame.glswing.rendering.GLColorInterpolation;

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
		check(GLThemeValues.parseColor(
				"derive(rgba(100, 150, 200, 128), alpha*=50%)").equals(
						new Color(100, 150, 200, 64)),
				"Relative derive alpha failed");
		check(GLThemeValues.parseColor(
				"derive(rgb(255, 0, 0), hue+=120deg)").equals(
						new Color(0, 255, 0)),
				"Relative derive hue failed");
		check(GLThemeValues.parseColor(
				"derive(rgb(255, 0, 0), saturation=50%, value=50%)").equals(
						new Color(128, 64, 64)),
				"Absolute derive HSV channels failed");
		check(GLThemeValues.parseColor(
				"derive(rgb(120, 80, 40), saturation*=0%, brightness+=10%)")
						.equals(new Color(146, 146, 146)),
				"Combined derive HSV aliases failed");
	}

	private static void testBundledThemes() {
		GLTheme defaults = GLThemes.DEFAULT;
		GLTheme dark = GLThemes.DARK;
		GLTheme light = GLThemes.LIGHT;
		check("Default".equals(defaults.values().get("theme.name", ""))
				&& "Dark".equals(dark.values().get("theme.name", ""))
				&& "Light".equals(light.values().get("theme.name", "")),
				"Bundled themes were not loaded as resources");
		check(dark.buttonStyle().pressedLightColor()
				.equals(dark.values().getColor("button.pressedLight", Color.BLACK))
				&& dark.buttonStyle().pressedDarkColor().equals(
						dark.values().getColor("button.pressedDark", Color.BLACK)),
				"Explicit button gradient tokens did not reach the style");
		check(!dark.buttonStyle().background()
				.equals(light.buttonStyle().background()),
				"Light theme did not override the inherited dark palette");
		check(defaults.labelStyle().font().equals(defaults.sliderStyle().font())
				&& defaults.labelStyle().font().equals(
						defaults.comboBoxStyle().font())
				&& defaults.labelStyle().font().equals(
						defaults.checkBoxStyle().font())
				&& defaults.labelStyle().font().size() == 12,
				"Standard control typography is no longer uniform");
		GLNodeEditorStyle nodeEditor = GLNodeEditorStyle.fromTheme(defaults);
		check(defaults.values().getColor("node.activeSelectedBorder", Color.BLACK)
				.equals(com.vingame.glswing.node.GLNodeStyle.fromTheme(defaults)
						.activeSelectedBorder()),
				"Active node-selection border did not reach its style");
		check(nodeEditor.connectionSnapDistance() == 18
				&& nodeEditor.connectionColorInterpolator()
						== GLColorInterpolation.SRGB
				&& nodeEditor.connectionCurveTolerance() == 0.01f
				&& nodeEditor.connectionHandleFactor() == 0.55f
				&& nodeEditor.minimumConnectionHandleLength() == 50
				&& nodeEditor.connectionWidth() == 3
				&& nodeEditor.connectionBorderWidth() == 1
				&& nodeEditor.marqueeBorderWidth() == 1
				&& nodeEditor.marqueeFill().equals(new Color(
						defaults.values().getColor("palette.selection", Color.BLACK)
								.getRed(),
						defaults.values().getColor("palette.selection", Color.BLACK)
								.getGreen(),
						defaults.values().getColor("palette.selection", Color.BLACK)
								.getBlue(), 96))
				&& nodeEditor.connectionBorder().equals(
						defaults.values().getColor("palette.border", Color.BLACK))
				&& nodeEditor.selectedConnectionBorder().equals(
						defaults.values().getColor("palette.accent", Color.BLACK)),
				"Default node-editor connection tokens did not reach its style");
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
					custom.derived=derive(${custom.brand}, alpha=64)
					custom.derivedFloat=derive(${custom.brand}, alpha=0.25f)
					custom.relative=derive(${custom.brand}, alpha*=50%)
					custom.shifted=derive(${custom.brand}, hue+=120deg, value*=90%)
					palette.accent=${custom.brand}
					button.pressedLight=${custom.brand}
					button.pressedDark=${custom.brand}
					button.cornerRadius=11
					slider.pressed=${palette.accent}
					nodeEditor.connectionCurveTolerance=0.08
					nodeEditor.connectionHandleFactor=0.8
					nodeEditor.connectionColorInterpolation=oklab
					""", StandardCharsets.UTF_8);
			GLTheme loaded = GLThemeLoader.load(file);
			Color brand = new Color(1f, 0.5f, 0.5f, 0.75f);
			check(brand.equals(loaded.values().getColor("custom.brand", Color.BLACK)),
					"Custom theme token was not retained");
			check(new Color(255, 128, 128, 64).equals(
					loaded.values().getColor("custom.derived", Color.BLACK))
					&& new Color(255, 128, 128, 64).equals(
							loaded.values().getColor("custom.derivedFloat", Color.BLACK)),
					"Derived theme alpha did not retain the base RGB color");
			check(loaded.values().getColor("custom.relative", Color.BLACK)
					.getAlpha() == 96,
					"Relative derived alpha did not use the base alpha");
			check(loaded.values().getColor("custom.shifted", Color.BLACK)
					.getAlpha() == brand.getAlpha(),
					"HSV derivation unexpectedly changed alpha");
			check(brand.equals(loaded.buttonStyle().pressedLightColor())
					&& brand.equals(loaded.buttonStyle().pressedDarkColor()),
					"Theme did not reach both button gradient colors");
			check(brand.equals(loaded.sliderStyle().pressedBackground()),
					"Accent did not reach slider pressed color: "
							+ loaded.sliderStyle().pressedBackground());
			check(loaded.buttonStyle().cornerRadius() == 11,
					"Component-specific corner radius was not applied");
			GLNodeEditorStyle nodeEditor = GLNodeEditorStyle.fromTheme(loaded);
			check(nodeEditor.connectionCurveTolerance() == 0.08f
					&& nodeEditor.connectionHandleFactor() == 0.8f
					&& nodeEditor.connectionColorInterpolator()
							== GLColorInterpolation.OKLAB,
					"External node-editor curve tokens did not reach its style");

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
