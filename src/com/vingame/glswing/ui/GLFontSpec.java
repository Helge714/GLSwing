package com.vingame.glswing.ui;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import com.vingame.glswing.rendering.GLFont;

/** Immutable logical-font key resolved and cached by the owning GLSwing panel. */
public record GLFontSpec(String family, int style, float size) {
	private static final Map<GLFontSpec, WeakReference<GLFont>> MEASUREMENT_FONTS =
			new WeakHashMap<>();

	public static final GLFontSpec DEFAULT =
			new GLFontSpec(GLFont.SANS_SERIF, GLFont.PLAIN, 16);

	public GLFontSpec {
		if (family == null || family.isBlank())
			throw new IllegalArgumentException("Font family must not be blank");
		if (style < GLFont.PLAIN || style > (GLFont.BOLD | GLFont.ITALIC))
			throw new IllegalArgumentException("Font style is invalid");
		if (!Float.isFinite(size) || size <= 0)
			throw new IllegalArgumentException("Font size must be finite and positive");
	}

	public GLFont createFont() {
		return new GLFont(family, style, size);
	}

	/**
	 * Shared CPU-only font used while a control or an entire control subtree is
	 * not attached to a GL surface.
	 */
	public GLFont measurementFont() {
		synchronized (MEASUREMENT_FONTS) {
			WeakReference<GLFont> reference = MEASUREMENT_FONTS.get(this);
			GLFont font = reference == null ? null : reference.get();
			if (font == null) {
				font = createFont();
				MEASUREMENT_FONTS.put(this, new WeakReference<>(font));
			}
			return font;
		}
	}
}
