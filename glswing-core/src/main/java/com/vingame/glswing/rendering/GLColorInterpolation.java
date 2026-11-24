package com.vingame.glswing.rendering;

import java.awt.Color;
import java.util.Locale;

/** Built-in color interpolation strategies usable by gradients and curves. */
public enum GLColorInterpolation implements GLColorInterpolator {
	/** Direct interpolation of gamma-encoded sRGB components. */
	SRGB {
		@Override
		protected Color mix(Color start, Color end, float amount) {
			return color(
					lerp(start.getRed() / 255f, end.getRed() / 255f, amount),
					lerp(start.getGreen() / 255f, end.getGreen() / 255f, amount),
					lerp(start.getBlue() / 255f, end.getBlue() / 255f, amount),
					alpha(start, end, amount));
		}
	},

	/** Interpolation after converting sRGB components to linear light. */
	LINEAR_RGB {
		@Override
		protected Color mix(Color start, Color end, float amount) {
			return color(
					linearToSrgb(lerp(srgbToLinear(start.getRed() / 255f),
							srgbToLinear(end.getRed() / 255f), amount)),
					linearToSrgb(lerp(srgbToLinear(start.getGreen() / 255f),
							srgbToLinear(end.getGreen() / 255f), amount)),
					linearToSrgb(lerp(srgbToLinear(start.getBlue() / 255f),
							srgbToLinear(end.getBlue() / 255f), amount)),
					alpha(start, end, amount));
		}
	},

	/** HSV interpolation using the shortest circular hue distance. */
	HSV_SHORTEST {
		@Override
		protected Color mix(Color start, Color end, float amount) {
			float[] first = Color.RGBtoHSB(start.getRed(), start.getGreen(),
					start.getBlue(), null);
			float[] second = Color.RGBtoHSB(end.getRed(), end.getGreen(),
					end.getBlue(), null);
			float firstHue = first[1] <= 1e-6f ? second[0] : first[0];
			float secondHue = second[1] <= 1e-6f ? firstHue : second[0];
			float hue = wrap(firstHue + shortestHueDelta(firstHue, secondHue)
					* amount);
			int rgb = Color.HSBtoRGB(hue, lerp(first[1], second[1], amount),
					lerp(first[2], second[2], amount));
			return new Color((rgb >>> 16) & 0xff, (rgb >>> 8) & 0xff,
					rgb & 0xff, Math.round(alpha(start, end, amount) * 255));
		}
	},

	/** Perceptually uniform interpolation in Cartesian OKLab coordinates. */
	OKLAB {
		@Override
		protected Color mix(Color start, Color end, float amount) {
			double[] first = toOklab(start);
			double[] second = toOklab(end);
			return fromOklab(lerp(first[0], second[0], amount),
					lerp(first[1], second[1], amount),
					lerp(first[2], second[2], amount),
					alpha(start, end, amount));
		}
	},

	/** OKLCH interpolation retaining chroma along the shortest hue path. */
	OKLCH_SHORTEST {
		@Override
		protected Color mix(Color start, Color end, float amount) {
			double[] first = toOklab(start);
			double[] second = toOklab(end);
			double firstChroma = Math.hypot(first[1], first[2]);
			double secondChroma = Math.hypot(second[1], second[2]);
			double firstHue = Math.atan2(first[2], first[1]) / (Math.PI * 2);
			double secondHue = Math.atan2(second[2], second[1]) / (Math.PI * 2);
			if (firstChroma <= 1e-8)
				firstHue = secondHue;
			if (secondChroma <= 1e-8)
				secondHue = firstHue;
			double hue = firstHue + shortestHueDelta(firstHue, secondHue) * amount;
			double chroma = lerp(firstChroma, secondChroma, amount);
			return fromOklab(lerp(first[0], second[0], amount),
					chroma * Math.cos(hue * Math.PI * 2),
					chroma * Math.sin(hue * Math.PI * 2),
					alpha(start, end, amount));
		}
	};

	@Override
	public final Color interpolate(Color start, Color end, float amount) {
		if (start == null || end == null)
			throw new IllegalArgumentException(
					"Interpolation endpoint colors must not be null");
		if (!Float.isFinite(amount) || amount < 0 || amount > 1)
			throw new IllegalArgumentException(
					"Interpolation amount must be between 0 and 1");
		if (amount == 0)
			return start;
		if (amount == 1)
			return end;
		return mix(start, end, amount);
	}

	protected abstract Color mix(Color start, Color end, float amount);

	public static GLColorInterpolation parse(String name) {
		if (name == null || name.isBlank())
			throw new IllegalArgumentException(
					"Color interpolation name must not be blank");
		String normalized = name.trim().toLowerCase(Locale.ROOT)
				.replace("-", "").replace("_", "").replace(" ", "");
		return switch (normalized) {
		case "srgb", "rgb" -> SRGB;
		case "linearrgb", "linear" -> LINEAR_RGB;
		case "hsv", "hsvshortest" -> HSV_SHORTEST;
		case "oklab" -> OKLAB;
		case "oklch", "oklchshortest" -> OKLCH_SHORTEST;
		default -> throw new IllegalArgumentException(
				"Unknown color interpolation: " + name);
		};
	}

	private static double[] toOklab(Color color) {
		double red = srgbToLinear(color.getRed() / 255f);
		double green = srgbToLinear(color.getGreen() / 255f);
		double blue = srgbToLinear(color.getBlue() / 255f);
		double l = 0.4122214708 * red + 0.5363325363 * green
				+ 0.0514459929 * blue;
		double m = 0.2119034982 * red + 0.6806995451 * green
				+ 0.1073969566 * blue;
		double s = 0.0883024619 * red + 0.2817188376 * green
				+ 0.6299787005 * blue;
		double lRoot = Math.cbrt(l);
		double mRoot = Math.cbrt(m);
		double sRoot = Math.cbrt(s);
		return new double[] {
				0.2104542553 * lRoot + 0.7936177850 * mRoot
						- 0.0040720468 * sRoot,
				1.9779984951 * lRoot - 2.4285922050 * mRoot
						+ 0.4505937099 * sRoot,
				0.0259040371 * lRoot + 0.7827717662 * mRoot
						- 0.8086757660 * sRoot };
	}

	private static Color fromOklab(double lightness, double a, double b,
			float alpha) {
		double lRoot = lightness + 0.3963377774 * a + 0.2158037573 * b;
		double mRoot = lightness - 0.1055613458 * a - 0.0638541728 * b;
		double sRoot = lightness - 0.0894841775 * a - 1.2914855480 * b;
		double l = lRoot * lRoot * lRoot;
		double m = mRoot * mRoot * mRoot;
		double s = sRoot * sRoot * sRoot;
		double red = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
		double green = -1.2684380046 * l + 2.6097574011 * m
				- 0.3413193965 * s;
		double blue = -0.0041960863 * l - 0.7034186147 * m
				+ 1.7076147010 * s;
		return color(linearToSrgb(red), linearToSrgb(green),
				linearToSrgb(blue), alpha);
	}

	private static Color color(double red, double green, double blue,
			double alpha) {
		return new Color(channel(red), channel(green), channel(blue),
				channel(alpha));
	}

	private static int channel(double value) {
		return (int) Math.round(Math.max(0, Math.min(1, value)) * 255);
	}

	private static float alpha(Color start, Color end, float amount) {
		return lerp(start.getAlpha() / 255f, end.getAlpha() / 255f, amount);
	}

	private static float srgbToLinear(float value) {
		return value <= 0.04045f ? value / 12.92f
				: (float) Math.pow((value + 0.055f) / 1.055f, 2.4);
	}

	private static double linearToSrgb(double value) {
		return value <= 0.0031308 ? 12.92 * value
				: 1.055 * Math.pow(value, 1 / 2.4) - 0.055;
	}

	private static float lerp(float start, float end, float amount) {
		return start + (end - start) * amount;
	}

	private static double lerp(double start, double end, double amount) {
		return start + (end - start) * amount;
	}

	private static float shortestHueDelta(float start, float end) {
		return (float) shortestHueDelta((double) start, end);
	}

	private static double shortestHueDelta(double start, double end) {
		double delta = (end - start) % 1;
		if (delta > 0.5)
			delta -= 1;
		else if (delta < -0.5)
			delta += 1;
		return delta;
	}

	private static float wrap(float value) {
		float wrapped = value % 1;
		return wrapped < 0 ? wrapped + 1 : wrapped;
	}
}
