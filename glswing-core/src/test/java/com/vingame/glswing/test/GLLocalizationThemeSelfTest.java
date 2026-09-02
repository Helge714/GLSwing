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
				"com.vingame.glswing.test.languages.TestMessages")
				.text("test.menu.language")),
				"Application bundle did not override/extend library texts");
	}

	private static void testColors() {
		check(GLThemeValues.parseColor("#FF804080").equals(
				new Color(255, 128, 64, 128)), "RRGGBBAA parsing failed");
		check(GLThemeValues.parseColor("rgba(1f, 0.5f, 0.5f, 0f)").equals(
				new Color(1f, 0.5f, 0.5f, 0f)), "Normalized rgba parsing failed");
		check(GLThemeValues.parseColor("rgba(100%, 50%, 0%, 25%)").equals(
				new Color(1f, 0.5f, 0f, 0.25f)), "Percentage rgba parsing failed");
		check(GLThemeValues.parseColor("rgba(255, 0.5, 32, 0.25f)").equals(
				new Color(1f, 0.5f, 32 / 255f, 0.25f)),
				"Mixed functional color channels failed");
		check(GLThemeValues.parseColor("rgba(1, 1, 1, 1)").equals(
				new Color(1, 1, 1, 1)),
				"Unsuffixed unit channels were not interpreted as 0..255 integers");
		check(GLThemeValues.parseColor("rgba(1.0, .5, 0.25, 1.0)").equals(
				new Color(1f, 0.5f, 0.25f, 1f)),
				"Decimal normalized channels failed");
		check(GLThemeValues.parseColor("rgb(255, 128, 64, 128)").equals(
				new Color(255, 128, 64, 128)),
				"Optional RGB alpha channel failed");
		check(GLThemeValues.parseColor("hsv(120deg, 100%, 100%, 50%)").equals(
				new Color(0, 255, 0, 128)), "HSV conversion failed");
		check(GLThemeValues.parseColor("hsl(240deg, 100%, 50%)").equals(
				new Color(0, 0, 255)), "HSL conversion failed");
		check(GLThemeValues.parseColor("oklab(1.0, 0, 0, 128)").equals(
				new Color(255, 255, 255, 128)), "OKLab conversion failed");
		checkColorClose(GLThemeValues.parseColor(
				"oklab(0.627955, 0.224863, 0.125846)"),
				new Color(255, 0, 0), 1, "OKLab red conversion failed");
		checkColorClose(GLThemeValues.parseColor(
				"oklch(0.627955, 0.257683, 29.234deg, 0.5)"),
				new Color(255, 0, 0, 128), 1, "OKLCH conversion failed");
		expectColorFailure("rgba(0, 0, 0, 1e-1)");
		expectColorFailure("rgba(256, 0, 0, 255)");
		expectColorFailure("hsv(120deg, 1, 100%)");
		expectColorFailure("oklch(0.5, -0.1, 20deg)");
		check(GLThemeValues.parseColor(
				"derive(rgba(100, 150, 200, 128), alpha*=50%)").equals(
						new Color(100, 150, 200, 64)),
				"Relative derive alpha failed");
		check(GLThemeValues.parseColor(
				"derive(rgb(10, 20, 30), alpha=1)").equals(
						new Color(10, 20, 30, 1)),
				"Unsuffixed derive channel was not interpreted as 0..255");
		check(GLThemeValues.parseColor(
				"derive(rgb(10, 20, 30), alpha=1.0)").equals(
						new Color(10, 20, 30, 255)),
				"Decimal derive channel was not interpreted as normalized");
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

	private static void expectColorFailure(String source) {
		try {
			GLThemeValues.parseColor(source);
			throw new AssertionError("Invalid color was accepted: " + source);
		} catch (IllegalArgumentException expected) { }
	}

	private static void checkColorClose(Color actual, Color expected,
			int tolerance, String message) {
		check(Math.abs(actual.getRed() - expected.getRed()) <= tolerance
				&& Math.abs(actual.getGreen() - expected.getGreen()) <= tolerance
				&& Math.abs(actual.getBlue() - expected.getBlue()) <= tolerance
				&& Math.abs(actual.getAlpha() - expected.getAlpha()) <= tolerance,
				message + ": " + actual);
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
		Path directory = Files.createTempDirectory("glswing-theme-bundle-");
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
					textShadow.color=rgba(0, 0, 0, 160)
					textShadow.offsetX=1.5
					textShadow.offsetY=2
					textShadow.softness=1.25
					textShadow.offsetSpace=screen
					button.textShadow.offsetY=3
					button.textShadow.offsetSpace=local
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
			check(loaded.textShadow("label").color().getAlpha() == 160
					&& loaded.textShadow("label").offsetX() == 1.5f
					&& loaded.textShadow("label").offsetY() == 2
					&& loaded.textShadow("label").softness() == 1.25f
					&& loaded.textShadow("label").offsetSpace()
							== com.vingame.glswing.rendering.GLTextShadow.OffsetSpace.SCREEN
					&& loaded.textShadow("button").offsetY() == 3
					&& loaded.textShadow("button").offsetSpace()
							== com.vingame.glswing.rendering.GLTextShadow.OffsetSpace.LOCAL,
					"Global or control-specific text shadow was not resolved");
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

			Path colors = directory.resolve("dark-colors.gltheme");
			Path metrics = directory.resolve("rounded-metrics.gltheme");
			Path bundle = directory.resolve("bundle.gltheme");
			Files.writeString(colors, """
					theme.type=colors
					palette.accent=#336699
					button.background=#102030
					""", StandardCharsets.UTF_8);
			Files.writeString(metrics, """
					theme.type=metrics
					metrics.cornerRadius=9
					menu.itemCornerRadius=3
					""", StandardCharsets.UTF_8);
			Files.writeString(bundle, """
					theme.type=bundle
					theme.colors=dark-colors.gltheme
					theme.metrics=rounded-metrics.gltheme
					button.cornerRadius=7
					""", StandardCharsets.UTF_8);
			GLTheme combined = GLThemeLoader.load(bundle);
			check(combined.values().getColor("palette.accent", Color.BLACK)
					.equals(new Color(0x33, 0x66, 0x99))
					&& combined.buttonStyle().background()
							.equals(new Color(0x10, 0x20, 0x30)),
					"Bundle did not import its color theme");
			check(combined.menuStyle().itemCornerRadius() == 3
					&& combined.buttonStyle().cornerRadius() == 7,
					"Bundle metric import or local override order is wrong");
			GLTheme selectedFragments = GLThemeLoader.loadBundle(GLThemes.DEFAULT,
					colors, metrics);
			check(selectedFragments.values()
					.getColor("palette.accent", Color.BLACK)
					.equals(new Color(0x33, 0x66, 0x99))
					&& selectedFragments.menuStyle().itemCornerRadius() == 3
					&& selectedFragments.buttonStyle().cornerRadius() == 9,
					"Independently selected color and metrics fragments were not composed");

			Path invalidColors = directory.resolve("invalid-colors.gltheme");
			Files.writeString(invalidColors, """
					theme.type=colors
					metrics.cornerRadius=4
					""", StandardCharsets.UTF_8);
			Files.writeString(bundle, """
					theme.type=bundle
					theme.colors=invalid-colors.gltheme
					""", StandardCharsets.UTF_8);
			try {
				GLThemeLoader.load(bundle);
				throw new AssertionError("Color fragment accepted a metrics key");
			} catch (GLThemeLoadException expected) {
				check(expected.getMessage().contains("not permitted in a color theme"),
						"Fragment type error did not identify the invalid key");
			}

			Path duplicate = directory.resolve("duplicate.gltheme");
			Files.writeString(duplicate, """
					theme.type=bundle
					theme.colors=first.gltheme
					theme.colors=second.gltheme
					""", StandardCharsets.UTF_8);
			try {
				GLThemeLoader.load(duplicate);
				throw new AssertionError("Duplicate theme slot was accepted");
			} catch (GLThemeLoadException expected) {
				check(expected.getMessage().contains("Duplicate theme key"),
						"Duplicate slot error did not identify its cause");
			}

			Path missingFont = directory.resolve("missing-font.gltheme");
			Files.writeString(missingFont, "font.source=fonts/missing.ttf\n",
					StandardCharsets.UTF_8);
			try {
				GLThemeLoader.load(missingFont);
				throw new AssertionError("Missing external font was accepted");
			} catch (GLThemeLoadException expected) {
				check(expected.getMessage().contains("cannot read font"),
						"External-font error did not include useful context");
			}
		} finally {
			Files.deleteIfExists(file);
			Files.deleteIfExists(cycle);
			if (Files.exists(directory)) {
				try (var files = Files.list(directory)) {
					for (Path child : files.toList())
						Files.deleteIfExists(child);
				}
				Files.deleteIfExists(directory);
			}
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
